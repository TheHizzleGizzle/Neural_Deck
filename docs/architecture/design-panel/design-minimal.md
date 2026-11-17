# NEURAL//DECK deepening — design "minimal"

Constraint: 1–3 entry points per module, maximum leverage per entry point.

## The shape

The UI learns **one module**. `Deck` has one way in, `send(Action)`, and one way out, `attach(Screen)` → `render(State)`. IdeaTree, the Idea Engine, the sessions store and the provider profile store sit behind it. No sheet, no `IdeaCardAdapter`, no view touches a model object, a file, a preference or the network.

| module | entry points | who calls it |
|---|---|---|
| **Deck** (C1) | `send`, `attach` | `MainActivity`, four sheets, `IdeaCardAdapter`, tests |
| **IdeaTree** (C2) | `of`, `rows`, `kin` | Deck, Idea Engine, tests |
| **IdeaEngine** (C3) | `run` | Deck, tests |
| **ProviderProfile** (C4) | `problem`, `chatUrl` | Deck, Idea Engine, UI (read-only), tests |
| **DeckFiles** (sessions store) | `load`, `save` | Deck, tests |

Three ports: `ModelText` (1 method), `ProfileStore` (2), `Deck.Screen` (1).

## 1. Interface

### 1.1 Deck (C1)

```java
package com.example.wtfff.deck;

public final class Deck {
    /** Composition root and tests only. Loads from disk. Never throws. */
    public Deck(DeckFiles files, ProfileStore profiles, IdeaEngine engine,
                String legacyActiveSessionId);

    /** The one way in: something the user did. Never throws. */
    public void send(Action action);

    /** The one way out. Replaces the current screen; null detaches.
        A non-null screen is rendered once before this returns. */
    public void attach(Screen screen);

    public interface Screen { void render(State state); }

    public static final class Action {           // opaque value; fields are package-private
        // start a flight
        public static Action spark(String seed);
        public static Action branch(String cardId, String skillIdOrNull); // null = active skill
        public static Action splice();
        public static Action optimise(String roughPrompt, String framework);
        public static Action ping(ProviderProfile draft);
        // cards
        public static Action star(String cardId);
        public static Action collapse(String cardId);
        public static Action select(String cardIdOrNull);                // toggles; null clears
        public static Action banish(String cardId, String reasonCode, String note);
        // graveyard
        public static Action rule(String entryId);                       // mute <-> enable
        public static Action revive(String entryId);
        public static Action purge(String entryId);
        // sessions (rename and delete act on the active session)
        public static Action open(String sessionId);
        public static Action create(String name);
        public static Action rename(String name);
        public static Action delete();
        // view, skills, vault
        public static Action filter(IdeaTree.Filter filter);
        public static Action skill(String skillId);
        public static Action seed(String text);
        public static Action forge(String name, String badge, String instruction);
        public static Action scrap(String skillId);
        public static Action vault(ProviderProfile profile);             // SAVE in the BYOK sheet
    }

    public static final class State {            // immutable; built fresh per render
        public final String sessionId, sessionTitle, seed;
        public final List<SessionRef> sessions;  // switcher order
        public final List<IdeaTree.Row> rows;    // already filtered
        public final IdeaTree.Filter filter;
        public final Set<String> selected;       // splice selection, insertion order
        public final List<Grave> graveyard;
        public final List<CreativeSkill> skills; // built-ins first, then custom
        public final CreativeSkill skill;        // active
        public final List<ProviderProfile> profiles; // five, chip order
        public final ProviderProfile profile;    // active
        public final String flight;              // loading-bar text; null when idle
        public final boolean labBusy, pingBusy;
        public final List<Cue> cues;             // one-shot; non-empty in exactly one render
        public String markdown();                // export of the active session
    }
    public static final class SessionRef { public final String id, title; public final int cardCount; }
    public static final class Grave {
        public final String id, reasonTag, rule, title;   // title null when nothing was archived
        public final boolean active, revivable;
    }
    public static final class Cue {
        public enum Kind { TOAST, LONG_TOAST, ALERT, OPEN_VAULT, SCROLL_TOP, SEED, LAB, PING }
        public final Kind kind;
        public final String title;               // ALERT: SPARK_FAILED | BRANCH_FAILED | SPLICE_FAILED
        public final String text;
        public final boolean ok;                 // PING
        public final PromptOptimizationResult prompt; // LAB
    }
}
```

**Invariants and ordering**

- **Thread.** Deck is confined to the thread its `replyOn` executor posts to (the UI thread). `send`, `attach` and reply application all happen there; Deck takes no locks. A `send` made from inside `render` runs after that render returns.
- **Nothing mutable crosses the seam.** Everything reachable from `State` has final fields and unmodifiable lists. `IdeaCard`, `BrainstormSession` and `GraveyardEntry` become Deck's private working storage; `GraveyardSheet` has no object left to mutate.
- **One publish per event.** Each `send` and each reply produces at most one `render`.
- **Cues are delivered once.** They queue while no screen is attached and ride the first render after `attach`. All rule-driven strings (toasts, alert codes, loading-bar text) are constants inside Deck, so they are byte-for-byte testable.
- **The dock text stays a view.** `SEED` is raised only when the active session or its saved seed changes; a freshly created Activity copies `state.seed` into the dock on its first render. Typing is never overwritten by an unrelated render.
- **Flights: one per indicator, no queue, no cancel.** Three slots: the card flight (spark/branch/splice, shown by `flight`), the Prompt Lab flight (`labBusy`) and the ping flight (`pingBusy`). An Action that would start a flight in an occupied slot is ignored. Each indicator is bound to exactly one slot, so it cannot lie. No queue, because a queued request would carry context captured before the earlier reply landed (branching twice would repeat itself). No cancel, because nothing on screen can send it; it is a one-case addition the day a button exists.
- **Targeting.** A card flight records `{flightId, sessionId, anchorCardId, selectedIds}` at `send`. The reply is applied to *that* session, found by id, never to "the active session". Deleting that session abandons the flight immediately (bar clears; the late reply fails the `flightId` check and is ignored). If the anchor card was banished mid-flight the reply is dropped. Filter reset, scroll-to-top and selection clearing happen only if the target is still active.
- **Saving.** Deck saves synchronously after any change to persisted data and after every applied reply. A failed save keeps state in memory and retries on the next event and on detach.
- **Lifecycle.** The Deck is process-scoped (held by `DeckApp`), not ViewModel-scoped: a ViewModel dies with a finished Activity while a reply is still in flight, and the relaunch would open a second Deck over the same file. A reply with no screen attached is applied, saved, and shown at the next `attach`.
- **Filter, splice selection, active skill live in Deck**, in memory, unpersisted. Rules write all three (success resets the filter; banish, switch and delete prune the selection; `scrap` can orphan the active skill), `rows` depends on the filter, and all three should survive rotation. Today's stale selection after session delete disappears.

**Flight starters** (strings unchanged):

| Action | refusals, in order | on accept |
|---|---|---|
| `spark` | blank seed → toast. Then the seed is saved. Profile problem → toast + `OPEN_VAULT`. Slot busy → ignored. | `SPARKING_ROOT_NODES // [BADGE]...` |
| `branch` | a non-null skill becomes active first. Profile problem; slot busy. | parent un-collapsed; `BRANCHING [BADGE] -> title` |
| `splice` | fewer than 2 selected → toast. Profile problem; slot busy. | `SPLICING_n_NODES_INTO_HYBRIDS...` |
| `optimise` | blank → toast. | `labBusy`; result arrives as a `LAB` cue |
| `ping` | — | `pingBusy`; result arrives as a `PING` cue |

### 1.2 IdeaTree (C2)

```java
public final class IdeaTree {
    public enum Filter { ALL, STARRED, ROOTS, OUTLINE }

    /** O(n). Snapshots the cards; never writes to them; immutable afterwards. */
    public static IdeaTree of(List<IdeaCard> cards);

    /** O(n) on first call per filter, then cached. */
    public List<Row> rows(Filter filter);

    /** Lineage, siblings, subtree and splice sources of one card. Null if absent. */
    public Kin kin(String cardId);

    public static final class Row {
        public final String id, parentId, title, summary, rationale, badge;
        public final int accent;
        public final List<String> tags;
        public final boolean starred, collapsed;
        public final int depth;        // derived; never read from storage
        public final long rails;       // bit k (k < depth): column k's vertical continues below this row
        public final int descendants;  // whole subtree below, collapsed or not
    }
    public static final class Kin {
        public final Row self;
        public final List<Row> ancestors;  // root first
        public final List<Row> children;   // list order: the siblings of whatever is branched next
        public final List<Row> subtree;    // self, then every descendant in OUTLINE order
        public final List<Row> sources;    // cards this hybrid was spliced from that still exist
    }
}
```

- **Filters.** `ALL`: pre-order, children in list order, descendants of collapsed cards omitted. `OUTLINE`: the same ignoring collapse (export, `kin`). `STARRED` and `ROOTS`: flat — `depth` 0 and `rails` 0 — so a rail can never point at a parent that is off screen.
- **Rails.** `(depth, rails, accent)` fully determines geometry. Bit `depth-1` means "a sibling follows me"; lower bits are inherited from the parent. Depth is not clamped (the mask covers 64 levels; deeper columns draw as non-continuing).
- **Shape rules.** A card whose parent is missing is a root. A parent cycle is broken by promoting the first unreached card to a root. No card is ever dropped.
- **Complexity.** `of` is one hash pass plus one iterative walk. `kin` is O(1) lookup plus the size of the answer (`subtree` is a slice of the outline).
- **Multi-parent splice.** A hybrid keeps one tree parent (the anchor) and gains `splicedFrom`, an additive id list. The tree stays a tree; `Kin.sources` exposes the rest.

### 1.3 Idea Engine (C3)

```java
public final class IdeaEngine {
    public IdeaEngine(ModelText live, ModelText offline, Executor worker, Executor replyOn);

    /** Never throws. sink is called exactly once, on replyOn. */
    public void run(Request request, Consumer<Reply> sink);
}

public final class Request {                 // immutable; context is copied at construction
    public enum Kind { SPARK, BRANCH, SPLICE, OPTIMISE, PING }
    public final Kind kind;
    public final ProviderProfile profile;    // captured on the UI thread
    public static Request spark(ProviderProfile p, String seed, List<String> rules,
                                CreativeSkill skill, IdeaTree tree);
    public static Request branch(ProviderProfile p, String seed, List<String> rules,
                                 CreativeSkill skill, IdeaTree tree, String parentId);
    public static Request splice(ProviderProfile p, String seed, List<String> rules,
                                 IdeaTree tree, List<String> cardIds);   // first = anchor
    public static Request optimise(ProviderProfile p, String roughPrompt,
                                   String framework, List<String> rules);
    public static Request ping(ProviderProfile draft);
}

public final class Reply {
    public final List<IdeaCard> cards;             // SPARK | BRANCH | SPLICE; fully stamped
    public final PromptOptimizationResult prompt;  // OPTIMISE
    public final String text;                      // PING
    public final String error;                     // non-null = failure; the others are null
    public final String offlineBecause;            // OPTIMISE: null = live, "" = no usable profile, else the live error
}

/** The seam for model text. Blocking; throws with a message fit to show. */
public interface ModelText {
    String complete(Request request, String system, String user, float temperature) throws Exception;
}
```

- **The factories take the tree**, not pre-chewed context, so "what the model needs to know" is decided in one place. Branch prompts carry the lineage root→parent and the titles of existing children ("do not repeat"); spark carries existing root titles; splice carries every selected card and stamps `splicedFrom` with all of them.
- **Config is captured, not read.** The engine holds no settings reference. The profile inside the `Request` is the only configuration a request can see.
- **The engine decides the offline fallback.** For `OPTIMISE`, if `profile.problem() != null` or `live` throws, it asks `offline` and marks `offlineBecause`. Card requests never fall back.
- **Error modes.** Every failure is `Reply.error`, with today's strings (`HTTP 401: …`, `Empty choices from provider response.`, `Unexpected AI request error`, `Connection failed`). A prose reply is not an error: it yields the single `AI Synthesis Output` card. A blank reply is `Empty choices from provider response.`

### 1.4 Provider profile (C4)

```java
public final class ProviderProfile {         // immutable
    public final String providerId, baseUrl, model, key, globalContext;
    public final float temperature;
    public ProviderProfile(String providerId, String baseUrl, String model,
                           String key, float temperature, String globalContext);
    public static ProviderProfile defaults(String providerId);
    public String label();                   // OPENROUTER | OPENAI | GROQ | DEEPSEEK | CUSTOM_URL
    public String problem();                 // null = a request may be sent
    public String chatUrl();                 // normalised …/chat/completions
}

public interface ProfileStore {
    ProviderProfile load(String providerIdOrNull);   // null = the active provider
    void commit(ProviderProfile profile);            // stores every field; makes it active
}
```

**The one rule**, private to `ProviderProfile`: an endpoint is *local* when the URL's host, parsed with `java.net.URI`, is exactly `localhost`, `127.0.0.1`, `::1` or `10.0.2.2` — for any provider id. A key is required unless local. Cleartext `http://` is allowed only when local. `problem()` is the single consumer; "configured" means `problem() == null`.

**Draft/commit.** There is no draft type. The draft is the sheet's own fields; `new ProviderProfile(...)` is built from them at PING or SAVE. `load` takes an explicit id, so reading another provider's fields cannot move the active pointer, and `Action.vault` is the only write path.

### 1.5 Sessions store

```java
public final class DeckFiles {               // java.io only
    public DeckFiles(File dir);
    public Contents load();                                   // never throws
    public void save(Contents contents) throws IOException;   // all-or-nothing per file
    public static final class Contents {
        public final List<BrainstormSession> sessions;
        public final String activeSessionId;                  // null = unknown
        public final List<CreativeSkill> customSkills;
        public final String problem;                          // load: a file was unreadable and quarantined
    }
}
```

## 2. Usage

```java
public class MainActivity extends AppCompatActivity implements Deck.Screen {
    private Deck deck;
    private Deck.State state;
    private Deck.Screen sheet;                    // the open bottom sheet, or null

    protected void onCreate(Bundle saved) {
        ...
        deck = DeckApp.deck(this);
        cardAdapter = new IdeaCardAdapter(deck, this);   // star/collapse/select/quick-branch send directly
        btnSparkIdeas.setOnClickListener(v ->
                deck.send(Deck.Action.spark(etSeedPrompt.getText().toString())));
        tabFilterStarred.setOnClickListener(v -> deck.send(Deck.Action.filter(IdeaTree.Filter.STARRED)));
        btnCancelMashup.setOnClickListener(v -> deck.send(Deck.Action.select(null)));
        btnExecuteMashup.setOnClickListener(v -> deck.send(Deck.Action.splice()));
    }
    protected void onStart() { super.onStart(); deck.attach(this); }
    protected void onStop()  { deck.attach(null); super.onStop(); }

    @Override public void render(Deck.State s) {
        state = s;
        tvSessionTitle.setText(s.sessionTitle + " ▾");
        tvGraveCount.setText(String.valueOf(s.graveyard.size()));
        boolean ok = s.profile.problem() == null;
        tvByokStatus.setText(ok ? s.profile.label() : "SET_KEY");
        layoutLoadingBar.setVisibility(s.flight != null ? View.VISIBLE : View.GONE);
        if (s.flight != null) tvLoadingStatus.setText(s.flight);
        cardAdapter.submit(s.rows, s.selected, s.skill);
        if (sheet != null) sheet.render(s);
        for (Deck.Cue c : s.cues) perform(c);
    }
    private void perform(Deck.Cue c) {
        switch (c.kind) {
            case TOAST:      Toast.makeText(this, c.text, Toast.LENGTH_SHORT).show(); break;
            case LONG_TOAST: Toast.makeText(this, c.text, Toast.LENGTH_LONG).show(); break;
            case ALERT:      showErrorDialog(c.title, c.text); break;
            case OPEN_VAULT: openByokSheet(); break;
            case SCROLL_TOP: rvIdeaTree.scrollToPosition(0); break;
            case SEED:       etSeedPrompt.setText(c.text); break;
            default:         break;               // LAB and PING belong to their sheets
        }
    }
    // banish dialog: reasons stay here, the rule goes to Deck
    //   deck.send(Deck.Action.banish(row.id, reasonCodes[which], customNote.getText().toString()));
}
```

Each sheet's `show(...)` returns a `Deck.Screen`; the Activity keeps it in `sheet` until dismissal.

```java
// GraveyardSheet.render(s): rebuild rows from s.graveyard
btnToggle.setOnClickListener(v -> deck.send(Deck.Action.rule(g.id)));
btnRevive.setOnClickListener(v -> deck.send(Deck.Action.revive(g.id)));
btnPurge .setOnClickListener(v -> deck.send(Deck.Action.purge(g.id)));

// SkillsSheet
row.setOnClickListener(v -> {
    deck.send(targetId == null ? Deck.Action.skill(skill.id) : Deck.Action.branch(targetId, skill.id));
    dialog.dismiss();
});
delBtn.setOnClickListener(v -> deck.send(Deck.Action.scrap(skill.id)));
// FORGE button: deck.send(Deck.Action.forge(name, badge, sys));

// PromptLabSheet
btnRun.setOnClickListener(v -> deck.send(Deck.Action.optimise(etRaw.getText().toString(), mode[0])));
//   render(s): btnRun.setEnabled(!s.labBusy);
//              btnRun.setText(s.labBusy ? "REWRITING_VIA_NEURAL_LINK..." : "SYNTHESIZE_UPGRADED_PROMPT");
//              for (Cue c : s.cues) if (c.kind == Cue.Kind.LAB) renderResult(c.prompt, ...);
btnLoadOnly.setOnClickListener(v -> { deck.send(Deck.Action.seed(upgraded()));  dialog.dismiss(); });
btnSparkNow.setOnClickListener(v -> { deck.send(Deck.Action.spark(upgraded())); dialog.dismiss(); });

// ByokSettingsSheet — chip tap reads the snapshot and writes nothing
chipGroq.setOnClickListener(v -> { selected[0] = "groq"; fill(profileOf(state, "groq")); });
btnPing.setOnClickListener(v -> deck.send(Deck.Action.ping(draft())));
btnSave.setOnClickListener(v -> { deck.send(Deck.Action.vault(draft())); dialog.dismiss(); });
```

## 3. Hidden implementation

- **Deck.** Sessions, active id, filter, selection, active skill, cached profiles, three flight slots, pending cues, dirty flag. A dispatch over `Action.kind`. Name normalisation (`trim().toUpperCase().replace(' ', '_')`, default `SESSION_0n`), the starter session, the markdown writer, badge defaulting for forged skills. Banish archives `kin.subtree` whole (additive `archivedSubtree` on the entry); revive restores the subtree with its links, under the original parent if that card still exists, else as a root at index 0. Entries saved before this change revive as today. The tree is rebuilt once per publish.
- **IdeaTree.** id→index map, child lists, one iterative pre-order walk that assigns depth, rails and descendant counts and records the outline order.
- **IdeaEngine.** A pure composer (`Request` → system, user, temperature; splice uses `min(1.2, t + 0.1)`, badge `SPLICE_Xn`, accent `0xFFFF2E93`). A pure parser: strip `<think>…</think>`, strip fences, then try each `{`/`[` candidate with a string-aware bracket matcher and take the first that parses and yields ideas; otherwise the freeform card. Fallback policy; error mapping; card stamping. `OfflineModelText` answers `OPTIMISE` in the same JSON schema the live model is asked for, and writes the active graveyard rules into the prompt text, so the "Bound N Graveyard…" line is true and there is one parse path.
- **`HttpModelText`.** `HttpURLConnection`, timeouts, OpenRouter headers by host, provider error extraction. `java.net` only.
- **DeckFiles.** The JSON codec leaves the model classes. Additive keys: `active` on the session (the active pointer now travels in the same atomic write; the old `active_session_id` pref is read once by `DeckApp` and passed as `legacyActiveSessionId`), `splicedFrom`, `archivedSubtree`. `depth` is still written, from the tree, and never read. Writes go to a temp file, are fsynced, then atomically moved. The last good file from the previous launch is kept as `.bak`. An unreadable file is renamed to `<name>.corrupt-<millis>`, never overwritten; `.bak` is tried next.
- **UI.** `PixelBranchConnectorView.setBranchSpec(depth, rails, accent)`; a `RecyclerView.ItemDecoration` reading the same rows bridges the 10dp row margin so rails meet. No `res/` edit.

## 4. Dependency strategy and adapters

| dependency | category | seam | adapters |
|---|---|---|---|
| IdeaTree | in-process | none | — |
| IdeaEngine (composer, parser, fallback) | in-process | none; Deck holds the class | — |
| model text (third-party LLM) | true external (mock) | `ModelText` | `HttpModelText`, `OfflineModelText`, `ScriptedModelText` (test) |
| sessions and skills files | local-substitutable | none | `DeckFiles` on `getFilesDir()` / on a JUnit `TemporaryFolder` |
| provider settings (SharedPreferences + Keystore) | ports & adapters | `ProfileStore` | `PrefsProfileStore` (uses `KeyVault`, same keys), `MemoryProfileStore` (test) |
| threads | in-process | `java.util.concurrent.Executor` | main-looper poster + thread pool; direct and `ManualExecutor` (test) |
| the screen | ports & adapters | `Deck.Screen` | `MainActivity` and the sheets; `RecordingScreen` (test) |
| clock, UUID | in-process | none | one adapter would be hypothetical; no test asserts on them |

`DeckApp` (Android) is the composition root and holds the one Deck per process.

## 5. Trade-offs

**High leverage.** `send` is where the flight guard, session targeting, saving, publishing and lifecycle queueing are written once; a new gesture costs one factory and one case and inherits all five. Every Deck test has the same shape. `IdeaTree.rows` feeds the list, the rails, the counts and the export from one pass. `Request` factories own prompt context, so "thin context" has one place to be fixed.

**Thin, or given up.**

- **The vocabulary did not shrink.** Two entry points carry 22 Action kinds and 8 Cue kinds. I reduced the number of ways in, not the number of gestures. `Action` is one class with a kind and loose arguments; the compiler protects the factories, not the dispatch.
- **No cancel and no queue.** A hung request holds the card slot deck-wide for up to the 65 s timeout. This is the biggest weakness.
- **One card flight for the whole Deck**, not per session.
- **Deck holds user-facing strings.** That is presentation inside a pure module, accepted so the strings are testable.
- **Deck's State carries two sheets' transient business** (`labBusy`, `pingBusy`, `LAB`/`PING` cues). A result that arrives after its sheet closed is dropped.
- **Full re-render per event.** O(n) row copies and `notifyDataSetChanged`, as today.
- **`PrefsProfileStore`'s key mapping is not JVM-tested** (about 40 lines).
- **Two new strings need a decision**: the alert when a corrupt file is quarantined, and the refusal of non-local `http://`. Both replace silent or accidental behaviour. If "no new strings" is absolute, quarantine stays and the alert goes; the http case falls through to the platform's own message.

## 6. Test plan

JUnit 4, plain JVM. No test names `PromptComposer`, `ReplyParser`, the codec or any Deck field.

| test class | crosses | adapters | defects |
|---|---|---|---|
| `IdeaTreeTest` | `of`, `rows`, `kin` | none | 4, 5, 6 |
| `ProviderProfileTest` | `problem`, `chatUrl` | none | 11 |
| `IdeaEngineTest` | `run` | `ScriptedModelText` as `live`, real `OfflineModelText`, direct executors | 7, 8, 12 |
| `DeckFilesTest` | `load`, `save` | temp dir | 13, saved-data fixtures |
| `DeckTest` | `send`, `attach` | real engine + `ScriptedModelText`, `ManualExecutor`, temp dir, `MemoryProfileStore`, `RecordingScreen` | 1, 2, 3, 9, 10, lifecycle, strings |

1. *Wrong session*: spark in A, `open(B)`, release the reply → cards at index 0 of A, B untouched; a second Deck on the same dir agrees. Same for branch (children directly after the parent).
2. *Loading bar*: a second starter while `flight != null` changes nothing; `flight` equals the first message until its reply, then null.
3. *Banish*: banish a 3-level subtree, revive → all cards back with parent links; `DELETE_ONLY` leaves no entry; banished ids leave `selected`.
4. *Stale depth*: cards built with garbage depth; `STARRED` and `ROOTS` rows have depth 0, rails 0.
5. *Rails*: fixture tree → expected masks; depth 7 is not clamped.
6. *Quadratic*: 50,000-card chain and fan under `@Test(timeout = 2000)`.
7. *Parser*: prose → one freeform card, no error; `<think>{x}</think>` before JSON; braces inside strings; fenced; array root.
8. *Context*: the prompt recorded by the scripted adapter contains every ancestor and existing child title; splice cards carry all `splicedFrom`.
9. *Config*: start a spark, `vault(other)`, release → the adapter saw the old profile.
10. *BYOK*: `ping(draftForGroq)` leaves `state.profile` unchanged; only `vault` changes it.
11. *Endpoint*: table of hosts, including `https://localhost.evil.com` (key required).
12. *Offline*: unusable profile → `live` never called; output contains each active rule; the "Bound N" line appears iff N > 0.
13. *Persistence*: stray `.tmp` ignored; corrupt main → original bytes preserved, `.bak` loaded, `problem` set.

Also: session deleted mid-flight; anchor banished mid-flight; reply while detached is on disk and its cue arrives once; today's file fixtures load.

## 7. Migration order

Each step compiles and behaves; old classes become delegating shells until step 8.

1. Test source set (JUnit 4, `org.json:json`, test-only).
2. `ProviderProfile`, `ProfileStore`, `PrefsProfileStore`. `AiConfig.isConfigured()` and the client's local check both delegate to `problem()`. The BYOK sheet stops writing on chip tap. (10, 11)
3. `IdeaTree`. `BrainstormSession`'s walks delegate; `IdeaCardAdapter` and the connector view take `Row`. (4, 5, 6)
4. `IdeaEngine`, `ModelText` and its adapters, lifted out of `OpenAiCompatibleClient`, which keeps its callbacks and delegates. `PromptLabSheet` loses its fallback. (7, 8, 9, 12)
5. `DeckFiles` behind `BrainstormRepository`. (13)
6. `Deck`, complete and green on the JVM, not yet wired.
7. `MainActivity`, `GraveyardSheet` and `SkillsSheet` switch to `send`/`render` in one change (they share session state). (1, 2, 3)
8. `PromptLabSheet` and `ByokSettingsSheet` switch to `optimise`/`ping`/`vault`. Delete `AiConfig`, `OpenAiCompatibleClient`, `BrainstormRepository`, `LocalPromptHeuristic`, the session walks and `IdeaCard.depth`.
