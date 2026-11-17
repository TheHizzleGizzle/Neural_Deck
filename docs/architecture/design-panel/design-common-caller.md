# NEURAL//DECK deepening — "gesture in, picture out"

Design constraint: **optimise for the most common caller.** The callers are `MainActivity` and the four bottom sheets. Their default case must be one line; rare cases may cost more.

## 0. Shape

A caller holds three things: a tap, an id it drew on a row, and sometimes typed text. It wants one thing back: what to draw. So:

- Every gesture is **one void verb** on `Deck` that takes ids and typed text. No per-call callback, no result to check.
- Everything that comes back is **one display-ready immutable snapshot** (`DeckState`, state-dependent strings already formatted) pushed to the single attached `Screen`, plus five one-line screen effects.
- **No mutable model object crosses the Deck interface.** `BrainstormSession`, `IdeaCard` and `GraveyardEntry` become invisible to `ui/`, so a sheet cannot write to them.
- Each sheet becomes `show(context, deck)`. All four sheet listener interfaces are deleted, and four of the seven adapter listener methods with them.

I pay with a wide Deck interface (28 methods) that owns state-dependent UI copy. That is deliberate; section 5 says what it costs.

```
MainActivity · sheets · IdeaCardAdapter ─► Deck ─┬► IdeaTree                      in-process
          (android, assignment-only)             ├► IdeaEngine ─► ModelSource ◄─ Http | Offline | Scripted
          DeckHost builds the graph              ├► ProviderVault ─► KeyValues, Secrets ◄─ Prefs, KeyVault | Map
                                                 ├► DeckStore (java.io) ─► KeyValues
                                                 └► Screen ◄─ MainActivity | RecordingScreen
```

## 1. Interface

### 1.1 Deck (C1) — package `deck`, no `android.*`

```java
public final class Deck {
    public Deck(DeckStore store, ProviderVault vault, IdeaEngine engine);

    // screen
    public void attach(Screen screen);     // renders now, then delivers queued effects in order
    public void detach(Screen screen);     // no-op unless `screen` is the attached one
    public DeckState state();              // never null; what the screen last rendered

    // dock and requests: queued, outcome arrives as state + effects
    public void spark(String dockText);
    public void branch(String cardId);                           // with the active skill
    public void pickSkill(String skillId, String cardIdOrNull);  // make active; branch when a card is given
    public void splice();                                        // the armed cards
    public void loadSeed(String seedPrompt);                     // Prompt Lab -> session seed + dock

    // cards
    public void toggleStar(String cardId);
    public void toggleCollapse(String cardId);
    public void toggleArmed(String cardId);                      // splice selection
    public void disarm();
    public void banish(String cardId, BanishReason reason, String note);
    public void filter(Filter filter);

    // graveyard
    public void toggleRule(String entryId);
    public void revive(String entryId);
    public void purge(String entryId);

    // sessions (the active one, except open)
    public void open(String sessionId);
    public void create(String typedName);
    public void rename(String typedName);
    public void delete();

    // skills
    public void forgeSkill(String name, String badge, String instruction);
    public void deleteSkill(String skillId);

    // Prompt Lab, BYOK vault, export
    public boolean optimise(String rawPrompt, String framework, LabReply reply); // false: nothing started
    public ProviderProfile profile(String providerIdOrNull);     // null = committed provider; never writes
    public void saveVault(ProviderProfile draft);
    public void ping(ProviderProfile draft, PingReply reply);
    public String markdown();

    public interface Screen {
        void render(DeckState state);
        void toast(String text, boolean longer);
        void alert(String code, String details);   // SPARK_FAILED | BRANCH_FAILED | SPLICE_FAILED
        void openVault();
        void setDock(String seedPrompt);
        void scrollToTop();
    }
    public interface LabReply  { void done(LabResult result); }
    public interface PingReply { void done(boolean online, String line); }
}

public final class DeckState {             // immutable, lists unmodifiable
    public final String title;             // raw: rename prefill, export subject
    public final String titleLabel;        // "AMBIENT_FOCUS_OS ▾"
    public final String graveCount;        // "1"
    public final String vaultLabel;        // "OPENROUTER" | "SET_KEY"
    public final boolean vaultReady;
    public final Filter filter;
    public final String skillLabel;        // "SKILL: SCAMPER"
    public final String branchLabel;       // "BRANCH: SCAMPER"
    public final String spliceLabel;       // "2 NODES ARMED FOR SPLICE"; null hides the bar
    public final String loading;           // "BRANCHING [SCAMPER] -> Title"; null hides the bar
    public final List<Row> rows;
    public final List<SessionTab> sessions;
    public final boolean canDelete;
    public final List<GraveRow> graveyard;
    public final List<SkillRow> skills;
}
public final class Row {
    public final String id, title, summary, badge;
    public final int accent, descendants;
    public final boolean starred, collapsed, armed;
    public final Rail rail;
    public String collapseLabel();   // "[+3]" collapsed, "[-3]" open, null when descendants == 0
    public String whyLine();         // "// WHY: ..." or null
    public String tagsLine();        // "#A   #B" or null
    public String clipText();        // clipboard form
}
public final class Rail {
    public static final Rail NONE;         // depth 0: draw nothing
    public final int depth;                // true depth, never clamped
    public final int accent;
    public final boolean more;             // a later sibling follows: the drop runs the full row height
    public boolean passes(int column);     // column < depth-1: that ancestor has a later sibling
}
public final class SessionTab { public final String id, label; }          // "● TITLE (4 nodes)"
public final class GraveRow   { public final String id, tagLabel, stateLabel, title /*nullable*/, rule, toggleLabel;
                                public final boolean active, revivable; }
public final class SkillRow   { public final String id, badgeLabel, nameLabel, subtitle;
                                public final int accent; public final boolean current, deletable; }
public final class LabResult  { public final String clarityLabel, prompt, upgradesText, toast /*nullable*/;
                                public final boolean toastLong; }
public enum Filter { ALL, STARRED, ROOTS }
public enum BanishReason { TOO_CLICHE, OUT_OF_SCOPE, WRONG_VIBE, DEAD_END, DELETE_ONLY;
    public static String[] labels(); }     // the five dialog lines, in today's order
```

**Invariants**

1. The Deck belongs to the thread that built it. A call from another thread throws `IllegalStateException`. Nothing is synchronised.
2. Every verb runs validate → mutate → save → render once. A stale or unknown id is a silent no-op. Verbs never throw.
3. Effects raised with no screen attached queue up and are delivered, in order, after the render of the next `attach`. Effects only ever reach an attached screen, so nothing opens on a destroyed Activity.
4. `state.loading != null` exactly when a live request is in flight.
5. A reply is applied to the session and card named on its ticket. No code path consults "the active session" to place cards.
6. `LabReply` and `PingReply` fire at most once, on the Deck's thread. They are sheet-scoped and never saved.

**Ordering.** Verbs are legal before `attach`. Do not call a verb from inside `render`.

**Error modes.** Validation → `toast` with today's strings. Not ready → today's key-missing toast, then `openVault()`. Request failure → `alert(code, message)`. A failed save leaves the previous file intact and is retried on the next change. An unreadable sessions file at start is quarantined and reported with one new toast (the only added string besides the cleartext refusal in 1.4).

**Request lifecycle.** A serial FIFO queue of tickets, head in flight. That is what the app does today by accident (one worker thread); now the loading line is `queue.peek()`'s message, so it cannot lie, and nothing the user tapped is dropped or needs a new "busy" string. No cancel: there is no control for it and none may be added. `delete()` removes that session's tickets; if the head goes, the next starts at once and the late reply finds no ticket and is ignored. A card banished mid-flight: its reply is discarded, because in-flight children belong to the banished subtree.

**Where UI-facing state lives.** `filter`, the armed set and the active skill live in the Deck, unsaved. Deck rules write all three (requests reset the filter, banish disarms, deleting a skill moves the active one), and a snapshot must be one consistent value. They now survive rotation and still reset on process death. The armed set clears whenever the active session changes, including on delete, which today leaves stale ids.

### 1.2 IdeaTree (C2) — package `deck`

```java
public final class IdeaTree {
    public static IdeaTree of(List<IdeaCard> cards);          // O(n); list order = sibling order
    public List<Row> rows(Filter filter, Set<String> armed);  // O(rows)
    public IdeaCard card(String id);                          // O(1), null if absent
    public int depth(String id);                              // O(1)
    public int descendants(String id);                        // O(1)
    public List<IdeaCard> subtree(String id);                 // pre-order, root first
    public List<IdeaCard> lineage(String id);                 // oldest ancestor ... parent; O(depth)
    public List<IdeaCard> children(String idOrNull);          // null = roots
    public List<IdeaCard> siblings(String id);
    public List<IdeaCard> spliceSources(String id);           // surviving cards a hybrid was fused from
    public List<IdeaCard> preOrder();                         // every card in tree order (export)
}
```

A card whose parent is absent, itself, or part of a cycle is a root. The tree never writes to a card; `IdeaCard.depth` is removed from the class (the JSON key is still written, from the tree, so an older build still loads the file). `rows(ALL)` is pre-order with collapsed subtrees hidden. `rows(STARRED)` and `rows(ROOTS)` are flat, in card-list order, every row `Rail.NONE`: **a rail is drawn only when the parent row is on screen above it.** Splice stays a tree: the anchor is `parentId`; the additive `IdeaCard.spliceOf` (JSON `spliceOf`, omitted when empty) lists every fused card.

### 1.3 Idea Engine (C3) — package `engine`

```java
public final class IdeaEngine {
    public IdeaEngine(ModelSource live, Executor worker, Executor callbacks);
    public void cards(EngineRequest request, Reply<List<IdeaCard>> reply);   // SPARK | BRANCH | SPLICE
    public void optimise(EngineRequest request, Reply<Optimised> reply);     // never fails: falls back offline
    public void ping(ProviderProfile profile, Reply<String> reply);
    public interface Reply<T> { void ok(T value); void failed(String message); }
}
public final class EngineRequest {         // immutable; factories deep-copy every card and rule
    public enum Kind { SPARK, BRANCH, SPLICE, OPTIMISE }
    public static EngineRequest spark(ProviderProfile p, String seed, CreativeSkill skill,
                                      List<String> taken, List<GraveyardEntry> rules);
    public static EngineRequest branch(ProviderProfile p, String seed, CreativeSkill skill,
                                       List<IdeaCard> lineage, IdeaCard parent,
                                       List<String> taken, List<GraveyardEntry> rules);
    public static EngineRequest splice(ProviderProfile p, String seed, List<IdeaCard> selected,
                                       List<String> taken, List<GraveyardEntry> rules);
    public static EngineRequest optimise(ProviderProfile p, String rawPrompt, String framework,
                                         List<GraveyardEntry> rules);
}
public final class Optimised {
    public final PromptOptimizationResult result;
    public final String fallback;          // null = live; "" = profile not ready; else the live error
}
public interface ModelSource {             // the seam for model text; blocking; runs on `worker`
    String complete(Prompt prompt) throws IOException;
}
public final class Prompt {
    public final EngineRequest request;    // null for ping
    public final ProviderProfile profile;
    public final String system, user;
    public final float temperature;
}
```

The request is the whole world: the engine reads nothing else, so config cannot change mid-flight. `taken` is the titles of existing siblings (roots for spark), which stops a second branch repeating the first. Returned cards arrive stamped: `parentId`, badge and accent from the skill, or `SPLICE_X<n>` / `0xFFFF2E93` and `spliceOf` for splice. Error messages are today's strings. **The engine decides the offline fallback**, and only for `OPTIMISE`.

### 1.4 Provider profile (C4) — package `vault`

```java
public final class ProviderProfile {       // immutable value, one per provider
    public static final String OPENAI = "openai", OPENROUTER = "openrouter", GROQ = "groq",
                               DEEPSEEK = "deepseek", CUSTOM = "custom";
    public final String providerId, baseUrl, model, key, globalContext;
    public final float temperature;
    public ProviderProfile with(String baseUrl, String key, String model, float temperature, String globalContext);
    public String label();                 // "OPENAI" ... "CUSTOM_URL"
    public EndpointRule endpoint();
    public boolean ready();                // endpoint().problem(key) == null
}
public final class ProviderVault {
    public ProviderVault(KeyValues settings, Secrets secrets);
    public ProviderProfile active();
    public ProviderProfile profile(String providerId);   // read-only
    public void commit(ProviderProfile draft);           // the only writer
}
public final class EndpointRule {
    public static EndpointRule of(String baseUrl);
    public boolean local();                // parsed host is localhost, 127.0.0.1, ::1 or 10.0.2.2
    public boolean keyRequired();          // !local()
    public boolean cleartextAllowed();     // local()
    public String chatUrl();               // slashes trimmed, "/chat/completions" appended once
    public String problem(String key);     // null when usable, else the message to show
}
public interface KeyValues { String get(String k, String fallback);       void put(String k, String v);
                             float getFloat(String k, float fallback);    void putFloat(String k, float v); }
public interface Secrets   { String read(String providerId);              void write(String providerId, String key); }
```

The draft **is** the value: the sheet holds a `ProviderProfile`, a chip tap swaps it for `profile(id)` (a read), and only `commit` writes. `commit` substitutes provider defaults for a blank URL or model and clamps temperature to 0–1.5. The rule parses the host instead of substring-matching, ignores which provider chip is selected, and is the only place that answers local / key required / cleartext.

### 1.5 Store and Android adapters

```java
public final class DeckStore {                                  // package storage, java.io only
    public DeckStore(File dir, KeyValues prefs);
    public Loaded load();                                       // never throws; sessions never empty
    public boolean save(List<BrainstormSession> sessions, String activeId);
    public List<CreativeSkill> customSkills();
    public boolean saveCustomSkills(List<CreativeSkill> skills);
    public static final class Loaded { public final List<BrainstormSession> sessions;
                                       public final String activeId; public final boolean quarantined; }
}
public final class DeckHost { public static Deck of(Context context); }   // ui; one Deck per process
```

`DeckHost` is process-scoped, not a `ViewModel`. A `ViewModel` dies on back-press; a reply landing in the dead one's Deck would then save over a file a newer Deck had changed. One Deck per process survives rotation and makes that lost update impossible.

## 2. Usage

```java
public class MainActivity extends AppCompatActivity implements Deck.Screen, IdeaCardAdapter.Menu {
    private Deck deck;  private DeckState s;

    protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);   // insets + findViewById as today
        deck = DeckHost.of(this);
        rvIdeaTree.setAdapter(cardAdapter = new IdeaCardAdapter(deck, this));
        btnSparkIdeas.setOnClickListener(v -> deck.spark(dock()));
        btnExecuteMashup.setOnClickListener(v -> deck.splice());
        btnCancelMashup.setOnClickListener(v -> deck.disarm());
        tabFilterAll.setOnClickListener(v -> deck.filter(Filter.ALL));          // STARRED, ROOTS alike
        btnGraveyard.setOnClickListener(v -> GraveyardSheet.show(this, deck));
        btnByokStatus.setOnClickListener(v -> ByokSettingsSheet.show(this, deck));
        btnSkillsDeck.setOnClickListener(v -> SkillsSheet.show(this, deck, null));
        btnOptimizePrompt.setOnClickListener(v -> PromptLabSheet.show(this, deck, dock()));
        btnSessionPicker.setOnClickListener(v -> sessionMenu());
    }
    protected void onStart() { super.onStart(); deck.attach(this); }
    protected void onStop()  { deck.detach(this); super.onStop(); }

    public void render(DeckState s) {
        this.s = s;
        tvSessionTitle.setText(s.titleLabel);
        tvGraveCount.setText(s.graveCount);
        tvByokStatus.setText(s.vaultLabel);
        tintKey(s.vaultReady ? 0xFF39FF88 : 0xFFFFB800);
        styleFilterTab(tabFilterAll, s.filter == Filter.ALL);                   // STARRED, ROOTS alike
        tvActiveSkill.setText(s.skillLabel);
        bar(layoutMashupBar, tvMashupCount, s.spliceLabel);
        bar(layoutLoadingBar, tvLoadingStatus, s.loading);
        cardAdapter.submit(s.rows, s.branchLabel);
        layoutEmptyState.setVisibility(s.rows.isEmpty() ? View.VISIBLE : View.GONE);
        rvIdeaTree.setVisibility(s.rows.isEmpty() ? View.GONE : View.VISIBLE);
    }
    public void toast(String t, boolean longer) { Toast.makeText(this, t, longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show(); }
    public void alert(String code, String details) { /* today's dialog; BYOK_SETTINGS -> openVault() */ }
    public void openVault()        { ByokSettingsSheet.show(this, deck); }
    public void setDock(String p)  { etSeedPrompt.setText(p); }
    public void scrollToTop()      { rvIdeaTree.scrollToPosition(0); }

    // IdeaCardAdapter.Menu: the three row actions that need a dialog or the clipboard
    public void copy(Row r)      { clip("Idea Node", r.clipText()); toast("Copied node to clipboard", false); }
    public void pickSkill(Row r) { SkillsSheet.show(this, deck, r); }
    public void banish(Row r) {
        EditText note = createDialogInput("Optional custom anti-pattern rule...", "");
        new AlertDialog.Builder(this).setTitle("BANISH_TO_GRAVEYARD")
            .setItems(BanishReason.labels(), (d, i) -> deck.banish(r.id, BanishReason.values()[i], text(note)))
            .setView(boxed(note)).setNegativeButton("CANCEL", null).show();
    }
    private void sessionMenu() {   // labels = s.sessions' labels + the fixed lines (+ delete if s.canDelete)
        final int n = s.sessions.size();
        dialog("SELECT_DECK_SESSION", labels, i -> {
            if (i < n) deck.open(s.sessions.get(i).id);
            else if (i == n) ask("NEW_BRAINSTORM_SESSION", "", "CREATE", deck::create);
            else if (i == n + 1) ask("RENAME_SESSION", s.title, "SAVE", deck::rename);
            else deck.delete();
        });
    }
}
```

`IdeaCardAdapter.onBindViewHolder` is assignment-only: `setRail(row.rail)`, `setText(row.tagsLine())`, and `v -> deck.toggleStar(row.id)`, `deck.toggleCollapse`, `deck.toggleArmed`, `deck.branch`. No session reference, no `countSubtree` per bind.

```java
// GraveyardSheet.show(Context, Deck) — no Listener; rows are immutable GraveRow values
for (GraveRow g : deck.state().graveyard) {
    toggle(g.toggleLabel).setOnClickListener(v -> { deck.toggleRule(g.id); run(); });
    if (g.revivable) revive.setOnClickListener(v -> { deck.revive(g.id); run(); });   // Deck toasts
    purge.setOnClickListener(v -> { deck.purge(g.id); run(); });
}

// SkillsSheet.show(Context, Deck, Row targetOrNull) — no Listener, no repository
for (SkillRow k : deck.state().skills) {
    if (k.deletable) del.setOnClickListener(v -> { deck.deleteSkill(k.id); run(); });
    row.setOnClickListener(v -> { deck.pickSkill(k.id, target == null ? null : target.id); dialog.dismiss(); });
}
forge.setPositiveButton("FORGE", (d, w) -> { deck.forgeSkill(text(etName), text(etBadge), text(etPrompt)); fill.run(); });

// PromptLabSheet.show(Context, Deck, String dockText) — no config, no client, no fallback branch
btnRun.setOnClickListener(v -> { busy.run(); if (!deck.optimise(text(etRaw), mode[0], lab)) idle.run(); });
// lab = r -> { idle.run(); show r.clarityLabel, r.prompt, r.upgradesText; toast r.toast if non-null }
btnLoadOnly.setOnClickListener(v -> { deck.loadSeed(out()); dialog.dismiss(); });
btnSparkNow.setOnClickListener(v -> { deck.loadSeed(out()); deck.spark(out()); dialog.dismiss(); });

// ByokSettingsSheet.show(Context, Deck) — the draft is a value in the sheet
final ProviderProfile[] draft = { deck.profile(null) };
chipGroq.setOnClickListener(v -> { draft[0] = deck.profile(ProviderProfile.GROQ); fill.run(); });  // a read
btnPing.setOnClickListener(v -> deck.ping(typed(), (ok, line) -> showPing(ok, line)));
btnSave.setOnClickListener(v -> { deck.saveVault(typed()); dialog.dismiss(); });   // Deck toasts + re-renders
```

`typed()` is `draft[0].with(url, key, model, temperature, context)` read from the fields.

## 3. Hidden implementation

**Behind `Deck`.**

- *Tickets.* A ticket records intent only: kind, session id, card id or armed ids, skill, loading line, failure code. The readiness check and the "seed saved first" rule run at the tap. When a ticket reaches the head, the Deck re-finds its session and card, drops it silently if either is gone (or fewer than two armed cards survive), and otherwise freezes an `EngineRequest` from `vault.active()`, the seed, `tree.lineage`, existing child titles and the active rules. One rule: **intent at the tap, the request value at the start.**
- *Apply.* Spark → index 0. Branch → directly after the parent, parent un-collapsed. Splice → after the anchor, anchor un-collapsed, armed set cleared. The filter resets to ALL and `scrollToTop()` fires only when the target is the active session; otherwise the cards are saved and the picture catches up on `open`. `updatedAt` is stamped on the session that changed, not the active one.
- *Banish* archives `tree.subtree(id)` in the entry (additive `archivedSubtree`, root still in `archivedCard`), removes it and disarms removed ids. *Revive* restores the whole subtree under its old parent if that card is alive, else as a root at index 0.
- *Snapshot builder* and `DeckCopy`: every state-dependent string in one package-private class, plus a cached `IdeaTree` per session, dropped on mutation.
- Session-name and skill-forging rules, the effect queue, the owning-thread check.

**Behind `IdeaEngine`.** `PromptComposer` (pure): the four prompts verbatim, plus an ancestor-path section and an "already explored, do not repeat" section; splice temperature `min(1.2, t + 0.1)`. `ReplyParser` (pure, never throws): strips `<think>…</think>` and code fences, takes the first *balanced* JSON value with string and escape awareness, and turns prose into the one freeform card instead of an error. The fallback rule, and error text.

**Behind `ModelSource`.** `HttpModelSource`: `HttpURLConnection`, today's timeouts and headers, `EndpointRule.problem` and the cleartext check before connecting. `OfflineModelSource`: the body of `LocalPromptHeuristic`, emitting the JSON the parser reads. It now appends the active rules to the rewritten prompt, so its "Bound n Graveyard…" upgrade line is true.

**Behind `ProviderVault` and `DeckStore`.** The legacy preference names (`provider_id`, `base_url_<id>`, `model_<id>`, `temperature`, `global_context`, `active_session_id`, `enc_key_<id>`). Save is temp file → fsync → current to `.bak` → rename. Load tries the file, then `.bak`, then moves the damaged file to `.corrupt-<millis>` and builds the starter session. The JSON codec and the starter session live here.

**Behind `PixelBranchConnectorView.setRail`.** Same paints and joint. Pass-through rails come from `passes(i)`, the drop runs full height when `more`, and verticals extend across the row's bottom margin so rows meet.

## 4. Dependency strategy and adapters

| Dependency | Category | Seam | Adapters |
|---|---|---|---|
| `IdeaTree`, composer, parser, `EndpointRule`, `DeckCopy`, tickets | in-process | none | — |
| Sessions and custom-skills files | local-substitutable | none: `DeckStore` on a real directory | app files dir / JUnit temp folder |
| SharedPreferences | ports & adapters | `KeyValues` | `PrefsKeyValues`, `MapKeyValues` |
| Android keystore | ports & adapters | `Secrets` | `KeyVault`, `MapSecrets` |
| The model provider over HTTP | mock | `ModelSource` | `HttpModelSource`, `OfflineModelSource`, `ScriptedModelSource` |
| Threads | ports & adapters | two `Executor`s | thread pool + main-looper poster / `ManualExecutor` + direct |
| The Activity | ports & adapters | `Deck.Screen` | `MainActivity`, `RecordingScreen` |

Every seam has two adapters that exist for a reason. There is deliberately **no** seam between the Deck and `IdeaTree`, `IdeaEngine`, `ProviderVault` or `DeckStore`: each has one implementation, so the Deck takes the concrete class and its tests run the real ones.

## 5. Trade-offs

**Leverage is high** where the callers are. `MainActivity` drops from 659 lines of rules to bindings plus an assignment-only `render`. Sheets lose their listeners and their knowledge of sessions, config, client and repository. Defects 1, 2, 3, 8 and 9 close behind one interface. All state-dependent copy has locality in `DeckCopy`, which turns "byte-identical strings" into a JVM test.

**Leverage is thin** in three places. `toggleStar` and its kin are a field flip; they earn their keep only through invariant 2. `KeyValues` is shallow by nature. `DeckHost` is pure wiring.

**What I gave up.**

- A small interface. 28 verbs is wide. It is flat (ids and strings in, void out, one mental model), but every new gesture is a new verb and usually a new snapshot field.
- A presentation-neutral Deck. It knows that a collapsed card reads `[+3]`. A second screen wanting different wording must reformat or widen the Deck.
- Composition by callers. They cannot reach a model object, so they cannot build an operation the Deck did not foresee. That is the rare case this constraint tells me to tax.
- Per-request results. A caller that wants "did *my* branch succeed" must watch snapshots.
- Cancel, parallel requests, incremental snapshots (each gesture rebuilds rows in O(n)), and a non-global Deck (Activity tests would need a reset hook on `DeckHost`).

## 6. Test plan and migration

Tests cross only these interfaces. No test names `PromptComposer`, `ReplyParser`, tickets or `DeckCopy`.

| Test class | Crosses | Adapters | Covers |
|---|---|---|---|
| `DeckTest` | `Deck` + `Screen` | `RecordingScreen`, `ScriptedModelSource`, `ManualExecutor`, `Map*`, temp dir | 1 switch session mid-request, cards land in the ticket's session on disk · 2 two requests, `loading` never null between them · 3 banish then revive restores the subtree · 8 second branch prompt holds first-round titles and ancestors; hybrids carry every `spliceOf` · 9 `saveVault` mid-flight, the scripted source saw the old profile · delete mid-flight · preserved-behaviour list · golden strings |
| `IdeaTreeTest` | `IdeaTree` | none | 4 STARRED and ROOTS rows are all `Rail.NONE` · 5 `more` / `passes` on a fixture, depth 7 stays 7 · 6 a counting list shows `of` makes two passes and `descendants` none |
| `IdeaEngineTest` | `IdeaEngine` + `ModelSource` | `ScriptedModelSource`, direct executors | 7 prose → one card; `{` inside `<think>`; fenced JSON · 12 not ready or live failure → offline result whose prompt contains every rule it claims · error text |
| `ProviderVaultTest`, `EndpointRuleTest` | `ProviderVault`, `EndpointRule` | `MapKeyValues`, `MapSecrets` | 10 `profile(id)` performs zero writes · 11 one table; `ready()` and the engine's refusal agree on every row |
| `DeckStoreTest` | `DeckStore` | temp dir, `MapKeyValues` | 13 truncated temp file → previous data; damaged file → `.bak` or quarantine, bytes kept · today's JSON fixtures still load |
| `HttpModelSourceTest` | `ModelSource` | JDK loopback `HttpServer` | status and error mapping, headers |

**Migration**, each step compiling and behaving:

0. Add the `test` source set, JUnit 4 and `org.json:json` (tests only).
1. **C4.** `KeyValues`, `Secrets`, `EndpointRule`, `ProviderProfile`, `ProviderVault`. `AiConfig` keeps its signatures and delegates; the client's local check calls the rule. The BYOK sheet moves to draft + `commit`. Manifest gets `usesCleartextTraffic="true"` so the rule is the single gate. Closes 10, 11.
2. **C2.** `IdeaTree`, `Row`, `Rail`. `BrainstormSession`'s walks delegate to the tree; the adapter binds `Row`; the connector takes `Rail`. Remove `IdeaCard.depth`. Closes 4, 5, 6.
3. **C3.** Carve composer, parser, `HttpModelSource` and `OfflineModelSource` out of the client; `OpenAiCompatibleClient` keeps its signatures over `IdeaEngine` and builds requests on the UI thread. Prompt Lab loses its fallback branches. Closes 7, 9, 12.
4. **Store.** `DeckStore`; `BrainstormRepository` delegates. Closes 13.
5. **C1a.** `Deck`, `DeckState`, `DeckHost`. Every rule and field leaves `MainActivity` in one change; it implements `Screen`. Sheets keep their old signatures, fed by one-line shims that call verbs. Closes 1, 2, 3, 8. This is the one large step; steps 1–4 exist to make it mechanical.
6. **C1b.** Sheets one at a time to `show(context, deck…)`: Graveyard, Skills, Prompt Lab, BYOK. The adapter takes the Deck. Delete the listener interfaces.
7. Delete the emptied `AiConfig`, `OpenAiCompatibleClient`, `BrainstormRepository`, `LocalPromptHeuristic`.

## Where each brief question is answered

1 Deck interface, snapshot, sheets → 1.1, 2 · 2 request lifecycle → 1.1 · 3 IdeaTree → 1.2 · 4 engine → 1.3, 3 · 5 provider profile → 1.4 · 6 dependencies → 4 · 7 tests → 6 · 8 migration → 6.
