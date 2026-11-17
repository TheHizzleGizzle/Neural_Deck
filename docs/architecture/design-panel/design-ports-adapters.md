# Design D: ports and adapters

**Thesis.** Five things in NEURAL//DECK really vary: where model text comes from, where the Deck's saved data lives, where provider settings live, which thread runs what, and what time and id come next. Each gets a port with at least two real adapters. Everything else (Deck, IdeaTree, Idea Engine, composer, parser, endpoint rule) is a concrete class with no Java `interface`, because nothing varies there. Every test in the plan is the production object graph with adapters swapped at those five seams. No test fakes one of our own modules.

```
          MainActivity + 4 sheets  (Views, dialogs, toast literals)
                 | gestures                     ^ DeckState
                 v                              |
   +--------------------------- Deck ----------------------------+
   | sessions, active session, one ticket, filter,               |
   | splice selection, active skill, effect queue                |
   |     IdeaTree (one per session)       IdeaEngine             |
   |                                      composer -> [] -> parser|
   +----+---------------+----------------+----------+--------+---+
  SessionsStore  ProviderSettingsStore  ModelText  Executor x2  Stamps
        |               |                  |           |         |
  File / InMemory  Prefs / InMemory  Http / Offline / Scripted
                                           pool, main / Manual   System / Sequence
```

Packages: `deck`, `tree`, `engine`, `provider` (pure JVM, own their ports); `adapters` (pure JVM: `HttpModelText`, `OfflineModelText`, `FileSessionsStore`, `SystemStamps`); `android` (`PrefsProviderSettingsStore`, `KeyVault`, `MainThreadPoster`, `AndroidPorts`, `DeckHolder`); `ui` (Activity, sheets, adapter, connector view). Test adapters live in `src/test`.

---

## 1. Interface

### 1.1 The five ports

```java
// engine/ : seam 1, the source of model text
public interface ModelText {
    /** Blocking. Called only on the worker. Returns raw assistant text, which may be prose. */
    String complete(ModelCall call) throws ModelTextException;
}
public final class ModelCall {
    public final EngineRequest request;   // immutable; holds the provider settings captured at submit
    public final String system;
    public final String user;
    public final float temperature;       // already adjusted per request kind
}
public final class ModelTextException extends Exception {
    public final EngineError.Code code;
}

// deck/ : seam 2, the Deck's saved data
public interface SessionsStore {
    Loaded load();                                                     // never throws
    void saveSessions(List<BrainstormSession> sessions, String activeId) throws IOException;  // all or nothing
    void saveCustomSkills(List<CreativeSkill> skills) throws IOException;

    enum Health { OK, RESTORED_BACKUP, QUARANTINED, SAVE_FAILED }
    final class Loaded {
        public final List<BrainstormSession> sessions;   // empty on first run
        public final String activeId;                    // "" when unknown
        public final List<CreativeSkill> customSkills;
        public final Health health;
    }
}

// provider/ : seam 3
public interface ProviderSettingsStore {
    ProviderSettings load();
    void save(ProviderSettings settings);
}

// seam 4 is java.util.concurrent.Executor, used in two slots: worker and poster.

// deck/ : seam 5
public interface Stamps { long now(); String newId(); }

public final class DeckPorts {
    public final ModelText live, offline;
    public final SessionsStore sessions;
    public final ProviderSettingsStore providers;
    public final Executor worker, poster;
    public final Stamps stamps;
    public DeckPorts(ModelText live, ModelText offline, SessionsStore sessions,
                     ProviderSettingsStore providers, Executor worker, Executor poster, Stamps stamps);
}
```

### 1.2 Deck (C1; questions 1 and 2)

```java
public final class Deck {
    public Deck(DeckPorts ports);                    // loads; makes the starter Session on first run

    public interface Listener { void onDeck(DeckState state); }
    public DeckState state();                        // immutable; same instance until the next change
    public void attach(Listener l);                  // calls l.onDeck(state()) before returning
    public void detach(Listener l);
    public void acknowledge(long effectId);

    // requests: at most one in flight
    public Outcome spark(String seedText);
    public Outcome branch(String cardId, String skillId);     // skillId null = active Skill
    public Outcome splice();

    // probes: never change a Session, do not occupy the request slot
    public Pending optimisePrompt(String raw, String framework, IdeaEngine.Callback cb);
    public Pending ping(ProviderProfile typed, IdeaEngine.Callback cb);

    // Sessions
    public void switchSession(String sessionId);
    public void createSession(String rawName);
    public Outcome renameSession(String rawName);
    public Outcome deleteActiveSession();
    public void setSeedPrompt(String text);

    // Idea Cards and view
    public void toggleStar(String cardId);
    public void toggleCollapse(String cardId);
    public void toggleSpliceSelection(String cardId);
    public void clearSpliceSelection();
    public boolean banish(String cardId, BanishReason reason, String customRule);  // true = a Graveyard rule was left
    public void setFilter(FilterMode mode);

    // Graveyard
    public void toggleRule(String entryId);
    public void revive(String entryId);
    public void purge(String entryId);

    // Skills
    public void selectSkill(String skillId);
    public Outcome forgeSkill(String name, String badge, String instruction);
    public void deleteSkill(String skillId);

    // BYOK vault
    public ByokDraft editProviders();
    public void commit(ProviderSettings settings);   // the only caller of ProviderSettingsStore.save

    public String exportMarkdown();
}
public enum Outcome { STARTED, DONE, BUSY, EMPTY_SEED, NEEDS_KEY, NEEDS_TWO, INVALID, GONE }
public enum BanishReason { TOO_CLICHE, OUT_OF_SCOPE, WRONG_VIBE, DEAD_END, DELETE_ONLY }
public enum FilterMode { ALL, STARRED, ROOTS }

public final class DeckState {
    public final long revision;                    // strictly increasing
    public final List<SessionLine> sessions;       // picker order
    public final String sessionId, sessionTitle, seedPrompt;   // the active Session
    public final FilterMode filter;
    public final List<TreeRow> rows;               // what the list shows, already derived
    public final List<String> spliceSelection;     // insertion order; first = anchor
    public final List<GraveLine> graveyard;        // newest first
    public final List<CreativeSkill> skills;       // built-ins, then custom (copies)
    public final CreativeSkill activeSkill;
    public final boolean providerReady;            // EndpointRule.usable(selected profile)
    public final String providerLabel;             // "OPENROUTER", "CUSTOM_URL", ...
    public final InFlight inFlight;                // null = idle
    public final Effect effect;                    // null, or the oldest unacknowledged one-shot
    public final SessionsStore.Health storage;

    public static final class SessionLine { public final String id, title; public final int cardCount; public final boolean active; }
    public static final class GraveLine   { public final String id, reasonTag, rule, archivedTitle; public final boolean active; }
    public static final class InFlight    { public final String sessionId, message; }
    public static final class Effect      { public enum Kind { FAILURE, SCROLL_TO_TOP }
                                            public final long id; public final Kind kind; public final String code, message; }
}
```

**Invariants.**

1. *Thread-confined.* Every method runs on the thread behind `ports.poster`. Deck has no locks and holds no `Context`.
2. *Save, then notify.* Each change bumps `revision`, attempts a save, then notifies every listener once. Notifications never nest: a command issued from inside `onDeck` causes one more round after the current one returns.
3. *One ticket.* `inFlight != null` exactly while one request is outstanding. A second `spark/branch/splice` returns `BUSY` and changes nothing. There is no queue. The loading bar is `inFlight`, so it cannot lie.
4. *Replies go to their ticket.* A ticket records `sessionId`, `anchorCardId`, source ids, badge, accent and failure code at submit. The apply step looks the Session up by id; it has no path to "the active session".
5. *Target gone means abandoned.* Deleting the ticket's Session, or banishing a subtree that contains its anchor, cancels the ticket: `inFlight` clears at once, no effect is queued, the late text is discarded. This is the only cancel. A user-facing cancel would need a button, and `res/` is frozen. The 20 s / 45 s timeouts bound the wait as today.
6. *Effects wait for an audience.* `FAILURE` (code `SPARK_FAILED` / `BRANCH_FAILED` / `SPLICE_FAILED` plus the message) and `SCROLL_TO_TOP` queue inside Deck and surface one at a time until acknowledged. A reply that lands with no listener attached is applied and saved; the next `attach` renders it and its effect.
7. *Settings are captured by value* at submit and travel inside the request.

**Ordering.** `spark` trims the text, returns `EMPTY_SEED` if blank, otherwise saves the seed, then checks the key (`NEEDS_KEY`), then the slot (`BUSY`). A reply for the active Session resets `filter` to `ALL`; Spark also queues `SCROLL_TO_TOP`; Splice also clears the selection. A reply for a Session that is no longer active changes only that Session.

**Error modes.** Stale ids (a tap on a row that has just gone) return `GONE` or do nothing; Deck never throws for user input. A failed save keeps memory authoritative, sets `storage = SAVE_FAILED` and retries on the next change.

**Where the UI-facing state lives.** `filter`, the splice selection and the active Skill live in Deck, in memory, unsaved. Rules need all three when no Activity exists (a reply resets the filter, Banish prunes the selection, deleting a custom Skill falls back to the first one), and rows are derived from the filter. They now survive rotation and still reset on process start.

### 1.3 IdeaTree (C2; question 3)

```java
public final class IdeaTree {
    public IdeaTree(List<IdeaCard> cards);            // adopts a Session's list; shape changes go through this object

    // derive: one O(n) pass, cached until the next edit
    public List<TreeRow> rows(FilterMode filter);     // ALL: depth-first, honouring collapse. STARRED, ROOTS: flat
    public List<TreeRow> fullRows();                  // depth-first, ignoring collapse (export)

    // ask: O(1) lookup, O(answer) result
    public boolean has(String cardId);
    public IdeaCard card(String cardId);              // null if absent
    public int depth(String cardId);
    public int subtreeSize(String cardId);            // descendants, excluding the card
    public List<IdeaCard> lineage(String cardId);     // root ... parent
    public List<IdeaCard> siblings(String cardId);    // other children of the same parent
    public List<IdeaCard> children(String cardId);
    public List<IdeaCard> subtree(String cardId);     // the card, then descendants in row order
    public List<IdeaCard> spliceSources(String cardId);   // cards named by splicedFrom that still exist

    // edit: the only code that changes parentId or list position
    public void addRoots(List<IdeaCard> cards);                    // index 0, order kept
    public void addChildren(String parentId, List<IdeaCard> kids); // directly after the parent; un-collapses it
    public List<IdeaCard> removeSubtree(String cardId);
    public void restore(List<IdeaCard> subtree);                   // under the old parent if it exists, else root at index 0
    public void setCollapsed(String cardId, boolean collapsed);
}

public final class TreeRow {
    public final String cardId, title, summary, rationale, skillBadge;
    public final int accent;
    public final List<String> tags;
    public final boolean starred, collapsed;
    public final int depth;            // true depth: never stored, never clamped
    public final int subtreeSize;      // for "[+n]" / "[-n]"
    public final boolean railed;       // the parent row is on screen; false for depth 0 and for flat filters
    public final boolean lastSibling;  // elbow ends here, or continues down to the next sibling
    public boolean railAt(int column); // does an ancestor's rail pass through this row in that column
}
```

A card whose `parentId` is null, unknown, itself, or part of a cycle is a root. Sibling order is card-list order, so new children (inserted directly after the parent) precede older ones, as today. The walk is iterative. Depth, subtree size, `lastSibling` and the rail flags come out of the same pass; the flags are a `long` mask pushed down the walk (`child = mask << 1 | hasLaterSibling`), so the whole derivation is O(n) with no per-row scan. The depth clamp of 4 becomes the connector view's pixel budget (it draws the innermost columns), not a lie in the data.

Multi-parent Splice: tree shape stays single-parent (the anchor), so rails are unaffected. Every selected id is recorded in the additive field `IdeaCard.splicedFrom`. `IdeaCard.depth` loses its readers; the JSON key is still written, from the tree, so older builds load the file.

### 1.4 Idea Engine (C3; question 4)

```java
public final class IdeaEngine {
    public IdeaEngine(ModelText live, ModelText offline, Executor worker, Executor poster);
    public interface Callback { void onResult(EngineResult result); }   // on poster; never after cancel()
    public Pending submit(EngineRequest request, Callback cb);
}
public interface Pending { void cancel(); }

public final class EngineRequest {
    public enum Kind { SPARK, BRANCH, SPLICE, OPTIMISE, PING }
    public final Kind kind;
    public final ProviderSettings settings;     // captured value: profile, temperature, global context
    public final List<String> antiPatterns;     // one line per active Graveyard rule
    public final String seed;                   // Session seed, or the raw prompt for OPTIMISE
    public final CreativeSkill skill;           // SPARK, BRANCH (a copy)
    public final CardBrief target;              // BRANCH: the parent
    public final List<CardBrief> lineage;       // BRANCH: root ... grandparent
    public final List<String> siblingTitles;    // BRANCH: titles already under the parent
    public final List<CardBrief> sources;       // SPLICE: every selected card, anchor first
    public final String framework;              // OPTIMISE
    public static EngineRequest spark(...), branch(...), splice(...), optimise(...), ping(...);
}
public final class CardBrief { public final String title, summary, rationale; }
public final class CardDraft { public final String title, summary, rationale; public final List<String> tags; }

public final class EngineResult {
    public enum Source { LIVE, OFFLINE_NO_PROFILE, OFFLINE_AFTER_ERROR }
    public final Source source;
    public final List<CardDraft> cards;              // SPARK / BRANCH / SPLICE: at least one on success
    public final PromptOptimizationResult upgrade;   // OPTIMISE: always present
    public final String pingLine;                    // PING: "ONLINE // model -> reply"
    public final EngineError error;                  // failure; also the live error behind OFFLINE_AFTER_ERROR
    public boolean ok();
}
public final class EngineError {
    public enum Code { NOT_CONFIGURED, TRANSPORT, PROVIDER_REJECTED, EMPTY_REPLY, UNSUPPORTED }
    public final Code code;
    public final String message;                     // today's dialog text, byte-identical
}
```

The engine returns **drafts**, not cards. It never mints an id, assigns a parent, or touches a live `IdeaCard`; Deck is the one place a card is born (id and time from `Stamps`, badge and accent from the ticket).

**The fallback is the engine's decision, and only for `OPTIMISE`.** If `EndpointRule.usable` is false, it calls `offline` inline and invokes the callback before `submit` returns (no I/O, so no thread hop and no flicker of the busy label). If `live` throws, it calls `offline` and reports `OFFLINE_AFTER_ERROR` with the live error. For the other kinds an unusable profile is `NOT_CONFIGURED` with today's "Missing ..." text, and a blank reply is `EMPTY_REPLY`.

**The parser is total for cards.** It removes `<think>...</think>` (also an unterminated one) and code fences, then scans for the first *balanced*, string-aware JSON value that yields at least one card, accepting an array root or the keys `ideas` / `cards` / `branches`. If none does, it returns the single freeform draft `AI Synthesis Output`. It never throws.

### 1.5 Provider profile (C4; question 5)

```java
public final class ProviderProfile {                // immutable, one per Provider
    public final String providerId, baseUrl, model, apiKey;
    public String label();
    public static ProviderProfile defaults(String providerId);
}
public final class ProviderSettings {               // immutable: the whole BYOK vault
    public final String selectedId;
    public final Map<String, ProviderProfile> profiles;
    public final float temperature;                 // clamped 0.0 to 1.5
    public final String globalContext;
    public ProviderProfile selected();
    public ProviderSettings with(ProviderProfile p);
}
public final class ByokDraft {
    public String selectedId();
    public ProviderProfile shown();                 // saved profile of the selected Provider; fills the fields
    public float temperature();
    public String globalContext();
    public void select(String providerId);          // changes the draft only
    public ProviderProfile typed(String url, String key, String model);   // blank url or model = defaults
    public ProviderSettings result(String url, String key, String model, float temperature, String globalContext);
}
public final class EndpointRule {
    public static boolean isLocal(String baseUrl);          // parsed host: localhost, 127.x, 10.0.2.2, ::1
    public static boolean keyRequired(ProviderProfile p);   // !isLocal
    public static boolean cleartextAllowed(String baseUrl); // http only when isLocal
    public static boolean usable(ProviderProfile p);
    public static String problem(ProviderProfile p);        // null, or today's "Missing ..." text
    public static String chatUrl(String baseUrl);
}
```

`EndpointRule` is the one rule. It parses the host, so `https://localhost.evil.com` is not local, and it ignores the provider id, so any Provider pointed at a local host needs no key. Deck, engine, `HttpModelText` and the BYOK badge all call it.

---

## 2. Usage

```java
public class MainActivity extends AppCompatActivity implements IdeaCardAdapter.Listener, Deck.Listener {
    private Deck deck;
    private String shownSessionId;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        deck = new ViewModelProvider(this).get(DeckHolder.class).deck();
        bindViews();
        deck.attach(this);          // renders now, including whatever landed while nothing was attached
    }
    @Override protected void onDestroy() {
        deck.detach(this);
        if (openSheet != null) openSheet.dismiss();
        super.onDestroy();
    }

    @Override public void onDeck(DeckState s) {
        tvSessionTitle.setText(s.sessionTitle + " ▾");
        tvGraveCount.setText(String.valueOf(s.graveyard.size()));
        tvByokStatus.setText(s.providerReady ? s.providerLabel : "SET_KEY");
        layoutLoadingBar.setVisibility(s.inFlight != null ? View.VISIBLE : View.GONE);
        if (s.inFlight != null) tvLoadingStatus.setText(s.inFlight.message);
        if (!s.sessionId.equals(shownSessionId)) { etSeedPrompt.setText(s.seedPrompt); shownSessionId = s.sessionId; }
        cardAdapter.submit(s.rows, s.activeSkill, s.spliceSelection);
        // filter tabs, splice bar, empty state: same lines as today, read from s
        if (s.effect != null) {
            if (s.effect.kind == DeckState.Effect.Kind.FAILURE) showErrorDialog(s.effect.code, s.effect.message);
            else rvIdeaTree.scrollToPosition(0);
            deck.acknowledge(s.effect.id);
        }
    }

    private void sparkFromDock() {
        switch (deck.spark(etSeedPrompt.getText().toString())) {
            case EMPTY_SEED: toastEnterSeed(); break;                    // today's literal, unmoved
            case NEEDS_KEY:  toastConfigureKey(); openByokSheet(); break;
            default: break;  // STARTED arrives through onDeck; BUSY is ignored, the bar already says what runs
        }
    }
    @Override public void onQuickBranch(TreeRow row)    { handle(deck.branch(row.cardId, null)); }
    @Override public void onToggleStar(TreeRow row)     { deck.toggleStar(row.cardId); }
    @Override public void onToggleCollapse(TreeRow row) { deck.toggleCollapse(row.cardId); }

    // inside the unchanged BANISH_TO_GRAVEYARD dialog:
    //   if (deck.banish(row.cardId, BanishReason.values()[which], note))
    //       toast("Banished! AI will avoid [" + row.title + "]");
}
```

`IdeaCardAdapter.onBindViewHolder` reads only `TreeRow`: `holder.branchConnector.setRow(row)` and `"[-" + row.subtreeSize + "]"`. It has no Session and counts nothing.

```java
// GraveyardSheet.show(context, deck): no longer receives a BrainstormSession
Deck.Listener l = s -> render(container, tvEmpty, s.graveyard);
deck.attach(l);
dialog.setOnDismissListener(d -> deck.detach(l));
btnToggle.setOnClickListener(v -> deck.toggleRule(line.id));
btnRevive.setOnClickListener(v -> { deck.revive(line.id); toast("Node revived to workspace."); });
btnPurge.setOnClickListener(v -> deck.purge(line.id));

// SkillsSheet.show(context, deck, targetRowOrNull, onBranch)
row.setOnClickListener(v -> {
    deck.selectSkill(skill.id);
    if (target != null) onBranch.branch(target.cardId, skill.id);     // the Activity maps the Outcome
    dialog.dismiss();
});
delBtn.setOnClickListener(v -> deck.deleteSkill(skill.id));
// FORGE: if (deck.forgeSkill(name, badge, sys) == Outcome.INVALID) toast("Name and instruction required.");

// PromptLabSheet.show(context, deck, initialPrompt, listener): imports neither the heuristic nor settings
btnRun.setEnabled(false); btnRun.setText("REWRITING_VIA_NEURAL_LINK...");
pending[0] = deck.optimisePrompt(raw, mode[0], r -> {
    btnRun.setEnabled(true); btnRun.setText("SYNTHESIZE_UPGRADED_PROMPT");
    renderResult(r.upgrade, layoutResult, tvClarity, etOptimized, tvUpgrades);
    if (r.source == EngineResult.Source.OFFLINE_NO_PROFILE) toastLocalHeuristic();          // today's literal
    else if (r.source == EngineResult.Source.OFFLINE_AFTER_ERROR) toastLong(FALLBACK_PREFIX + r.error.message);
});
dialog.setOnDismissListener(d -> { if (pending[0] != null) pending[0].cancel(); });

// ByokSettingsSheet.show(context, deck)
ByokDraft draft = deck.editProviders();
chipGroq.setOnClickListener(v -> { draft.select("groq"); fill(draft.shown()); });           // writes nothing
btnPing.setOnClickListener(v -> pending[0] = deck.ping(draft.typed(url(), key(), model()), r -> showPing(r)));
btnSave.setOnClickListener(v -> {
    deck.commit(draft.result(url(), key(), model(), temp(), ctx()));
    toast("BYOK Vault updated [" + draft.shown().label() + "]");
    dialog.dismiss();
});
```

Composition root, the only place adapters are named:

```java
public final class DeckHolder extends AndroidViewModel {
    private final AndroidPorts ports;
    private final Deck deck;
    public DeckHolder(Application app) { super(app); ports = AndroidPorts.create(app); deck = new Deck(ports.deckPorts); }
    public Deck deck() { return deck; }
    @Override protected void onCleared() { ports.shutdownWorker(); }   // an in-flight reply still finishes, applies and saves
}
// AndroidPorts.create: HttpModelText, OfflineModelText, FileSessionsStore(filesDir, legacyActiveIdFromPrefs),
//                      PrefsProviderSettingsStore, a 2-thread pool, MainThreadPoster, SystemStamps
```

---

## 3. Hidden implementation

- **Deck**: session list and id map; one `IdeaTree` per Session; the ticket; the effect queue; a cached `ProviderSettings` (Deck is its only writer); a cached `DeckState`. Also every rule lifted from `MainActivity`: name normalisation (`trim().toUpperCase().replace(' ', '_')`, default `SESSION_0n`), the three loading messages and three failure codes (JVM-tested byte for byte, because they are produced when no Activity may exist), custom Skill defaults, the starter Session, the built-in Skills, Markdown export over `fullRows()`. Banish archives everything `removeSubtree` returned in the additive `GraveyardEntry.archivedSubtree`; Revive hands it to `restore`.
- **IdeaTree**: id index, child lists, the single iterative walk, a dirty flag.
- **IdeaEngine**: the fallback policy, a cancel flag checked on the poster, and two package-private pure modules: `PromptComposer` (the four prompts plus the ping; one renderer for the Graveyard block; Branch gains lineage and "already explored" sibling titles; Splice lists every source; temperature rules `min(1.2, t + 0.1)`, `0.65`, `0.2`) and `ReplyParser`.
- **`HttpModelText`**: today's `executeChatCompletion` moved, with timeouts, headers and error text unchanged. Pure `java.net`. It refuses cleartext to a non-local host through `EndpointRule` before connecting.
- **`OfflineModelText`**: today's heuristic, emitting the same JSON a model would so it flows through the same parser. It appends the Graveyard block through the composer's renderer, which makes the existing "Bound N Graveyard anti-pattern constraint(s)" line true. It supports `OPTIMISE` only.
- **`FileSessionsStore(File dir, String legacyActiveId)`**: pure `java.io` / `java.nio`. Same two files and same shapes. Additive keys only: `"active": true` on one session object, `splicedFrom`, `archivedSubtree`. The old `active_session_id` preference is read once by `AndroidPorts` and used only when no session is marked. Writes go to a temp file, sync, keep the previous file as `.bak`, then rename atomically. An unparseable file is moved to `.corrupt-<time>`; the store then tries `.bak` and reports `RESTORED_BACKUP` or `QUARANTINED`. The starter Session is Deck's job, so no adapter can overwrite user data with it.
- **`PrefsProviderSettingsStore`**: today's `AiConfig` and `KeyVault` with their getters replaced by `load()` and `save()`. Same preference files, same keys.

---

## 4. Dependency strategy and adapters (question 6)

| Seam | What varies | Category | Adapters |
|---|---|---|---|
| `ModelText` | where text comes from | true external (mock) | `HttpModelText` (prod), `OfflineModelText` (prod), `ScriptedModelText` (test: queued replies and errors, records every `ModelCall`) |
| `SessionsStore` | where saved data lives | local-substitutable for the file adapter's own tests; a port for Deck because Deck tests need fault injection | `FileSessionsStore` (prod), `InMemorySessionsStore` (test: copies through JSON, can fail a save, can preset `Health`) |
| `ProviderSettingsStore` | preferences plus keystore, or memory | ports and adapters: platform storage with no JVM stand-in | `PrefsProviderSettingsStore` (prod), `InMemoryProviderSettingsStore` (test: counts saves) |
| `Executor` (worker, poster) | which thread, and when | in-process, platform-bound | 2-thread pool and `MainThreadPoster` (prod); `ManualExecutor` and `Runnable::run` (test) |
| `Stamps` | time and ids | in-process, nondeterministic | `SystemStamps` (prod), `SequenceStamps` (test) |

In-process with no seam: IdeaTree, composer, parser, `EndpointRule`, `ByokDraft`, all Deck rules.

Seams refused, each because it would have one adapter: an `IdeaEngine` interface (Deck tests run the real engine over scripted text), a `Deck` interface, an HTTP transport port under `HttpModelText`, a JSON codec port, clipboard and toast ports (they stay in the Activity).

---

## 5. Trade-offs

**High leverage.**
- One Deck test over `ScriptedModelText` exercises composer, parser, tree insertion, saving and the snapshot. Twelve of the thirteen defects become tests at two interfaces (Deck and IdeaTree) with no Android on the classpath.
- The `ModelText` port is also the observation point for the composer: tests assert on the recorded `ModelCall` and never open the engine.
- `ModelText` has two *production* adapters. That removes the fallback decision from `PromptLabSheet` by construction.
- A contract test run against both `SessionsStore` adapters keeps the in-memory one honest.

**Thin.**
- `Stamps` is the weakest seam. It earns its place only through exact golden-file store tests and readable ids in failures. Without those I would delete it.
- `ModelCall` carries both the rendered prompt and the request so `OfflineModelText` can work from facts. Each adapter ignores part of its input, and the offline one handles one kind of five. That is the price of making the heuristic a peer of HTTP.
- `PrefsProviderSettingsStore`'s key mapping is not under JVM test. It is today's code moved, but constraint 4 rests on review and a manual check there.
- The Deck interface is wide: 27 methods, one per gesture. Ports sharpen the driven side and do nothing for the driving side.
- `DeckPorts` plus about twenty small types is real ceremony for an 18-class app.

**Given up.** A queue. User cancel. Interrupting an abandoned HTTP call (the result is dropped; the socket runs to its timeout). Asynchronous saving (writes stay on the UI thread, as today). A fake engine for faster Deck tests. Automated tests for the Android adapters. A visible notice for `storage` (no new string is allowed; the data is preserved on disk and the fact is in the snapshot).

**Behaviour decisions to confirm.** Revive re-attaches under the original parent when it still exists, and otherwise lands as a root at index 0 with its subtree. STARRED and ROOTS render flat with no rails. The parent un-collapses when children land rather than at submit.

---

## 6. Test plan (question 7) and migration order (question 8)

All tests are JUnit 4 on a plain JVM. `DeckRig` builds the real `Deck` from test adapters at all five ports.

| Test class | Crosses | Adapters |
|---|---|---|
| `IdeaTreeTest` | `IdeaTree` | none |
| `EndpointRuleTest`, `ByokDraftTest` | provider values | none |
| `ReplyParserTest` | the parser's own interface (table of raw replies) | none |
| `IdeaEngineTest` | `IdeaEngine.submit` | `ScriptedModelText`, real `OfflineModelText`, direct executors |
| `HttpModelTextTest` | `ModelText` | the real adapter against the JDK's loopback `HttpServer` |
| `SessionsStoreContract` (abstract), run twice | `SessionsStore` | `FileSessionsStore` on `TemporaryFolder`; `InMemorySessionsStore` |
| `FileSessionsStoreTest` | `SessionsStore` | a checked-in fixture of today's v1 file; truncated and corrupt files |
| `DeckTest` | `Deck` | all five test adapters through `DeckRig` |

```java
@Test public void replyLandsInTheSessionItWasAskedFor() {
    DeckRig rig = new DeckRig().withTwoSessions();          // A active, B idle
    rig.text.reply("{\"ideas\":[{\"title\":\"Child\"}]}");
    assertEquals(Outcome.STARTED, rig.deck.branch(rig.cardInA, null));
    rig.deck.switchSession(rig.sessionB);
    rig.worker.runAll(); rig.poster.runAll();               // the reply arrives now
    assertNull(rig.deck.state().inFlight);
    assertEquals(0, rig.rowsOf(rig.sessionB).size());
    assertEquals(rig.cardInA, rig.parentOf(rig.sessionA, "Child"));
    assertEquals(1, rig.store.saveCount());
}
```

| # | Defect | Made impossible by | Test |
|---|---|---|---|
| 1 | wrong session | the ticket names its Session and anchor; apply has no path to "active" | `DeckTest` (above) |
| 2 | bar lies | the bar is `inFlight`; one ticket; `BUSY` | `DeckTest` |
| 3 | subtree lost | `removeSubtree` result archived whole; `restore` | `DeckTest`, store contract round trip |
| 4 | stale depth | depth is derived per call; flat filters set `railed = false` | `IdeaTreeTest` |
| 5 | rails | `lastSibling`, `railAt`, unclamped depth | `IdeaTreeTest` |
| 6 | quadratic | id index, one pass, `subtreeSize` on the row | `IdeaTreeTest`: 50k-card chain and 50k-wide fan |
| 7 | parser | total parser, think-stripping, balanced scan | `ReplyParserTest`, `IdeaEngineTest` |
| 8 | thin context | request carries lineage, sibling titles, every source; `splicedFrom` | `DeckTest` asserting on `rig.text.calls` |
| 9 | config mid-flight | settings are a value inside the request | `DeckTest`: `commit` between submit and `runAll` |
| 10 | saves on tap | `ByokDraft.select` writes nothing; only `Deck.commit` saves | `DeckTest`: save count stays 0 |
| 11 | two local rules | `EndpointRule` only | `EndpointRuleTest` |
| 12 | offline claim | engine owns the fallback; the offline adapter adds the rules | `IdeaEngineTest` |
| 13 | persistence | atomic rename, quarantine, `Health` | `FileSessionsStoreTest`, `DeckTest` with a failing save |

`DeckTest` also covers lifecycle (nothing attached, reply applied and saved; `attach` then delivers the state and one effect, once), abandonment (Session deleted or anchor banished mid-flight) and the byte-identical loading messages and failure codes.

**Migration.** Each step compiles and behaves as before plus the listed fixes.

0. Add the JVM test source set (JUnit 4 and `org.json:json`, test-only). Check in a fixture of today's sessions file.
1. **IdeaTree.** New `tree` package with tests. `BrainstormSession`'s walks delegate to it; `IdeaCardAdapter` binds `TreeRow`; the connector view takes a row. Fixes 4, 5, 6.
2. **Provider profile.** Values, `EndpointRule`, `ByokDraft`, the port. `AiConfig` gains `load()` / `save()` and becomes the prefs adapter. The BYOK sheet edits a draft. Both old "local" checks call the rule. Fixes 10, 11.
3. **Idea Engine.** `ModelText`; `HttpModelText` cut out of the client; composer, parser, `OfflineModelText`, `IdeaEngine`. `OpenAiCompatibleClient` stays as a thin shim over the engine with its old callbacks, so `MainActivity` is untouched. `PromptLabSheet` stops choosing the fallback. Fixes 7, 8, 9, 12.
4. **Sessions store.** Port, `FileSessionsStore`, contract test. `BrainstormRepository` delegates to it. Fixes 13.
5. **Deck, built and tested, unused by the app.** `Deck`, `DeckState`, `DeckPorts`, `Stamps`, the full `DeckTest`.
6. **Cut-over.** `DeckHolder`; `MainActivity` and the four sheets switch to Deck in one commit; delete the shim, `BrainstormRepository`, `LocalPromptHeuristic` and the walks in `BrainstormSession`. Fixes 1, 2, 3 and the lifecycle rules. This is the one large step. Session ownership cannot be shared between Deck and the Activity, so it cannot be split; step 5 exists to make it safe.
