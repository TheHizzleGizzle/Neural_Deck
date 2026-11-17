# NEURAL//DECK — design "flexible"

Constraint: maximise flexibility. Undo, a cross-session taste profile, a converge/score request, streaming replies and new filters must all arrive without changing an interface.

## 0. Shape

`Deck` has three methods: `dispatch(action)`, `state()`, `subscribe(listener)`. Everything else is a **value handed in**:

| value | says | handed in through | adapters today |
|---|---|---|---|
| `DeckAction` | what the user did | `deck.dispatch(Actions.x(...))` | ~28 factories |
| `Move` | one request kind end to end: where it lands, how it is asked, how the reply is read | `Actions.ask(Ask.of(move))` | SPARK, BRANCH, SPLICE, OPTIMISE, PING |
| `Lens` | one way of viewing the tree | `Actions.lens(lens)` | ALL, STARRED, ROOTS, FULL (export) |
| `ContextSource` | facts every prompt gets | `Deck.Builder.context(...)` | seed, global context, graveyard rules, lineage, siblings |
| `TextSource` | where model text comes from | `new IdeaEngine(sources, ...)` | HTTP, offline heuristic, scripted |

The rule that keeps this from dissolving: **the set of kinds is open; the set of effects is closed.** A reply can do exactly three things to a Deck: add cards at a `Landing`, annotate existing cards (`marks`), or hand text back to whoever asked. A Lens can only choose which cards show and whether they nest. Deck implements those effects once; a new kind is a new value, not a new code path.

| future need | what is added | interfaces changed |
|---|---|---|
| Undo button | UI dispatches `Actions.undo()`, reads `state.undoLabel` | none (journal exists from day one) |
| Taste profile | one `ContextSource` reading stars and banish reasons across all sessions into `Facts["taste"]`; one composer template | none |
| Converge / score | one `Move` whose `parse` fills `Result.marks` (cardId → score); landed into `IdeaCard.meta` | none |
| Streaming | an SSE `TextSource` calling `stream.delta()`; UI reads `RequestLine.partial` | none |
| Tag / search filter | `Actions.lens(Lenses.tag("AUDIO"))` | none |

Packages: `tree`, `engine`, `provider`, `deck` are pure JVM; `app` holds the Android adapters.

## 1. Interface

### 1.1 Deck (C1)

```java
public final class Deck {
    public static Builder with(DocumentStore docs, ProviderStore providers, IdeaEngine engine);
    public static final class Builder {
        public Builder context(ContextSource source);         // appended after the five built-ins
        public Builder clock(Clock clock);                    // default: system
        public Builder ids(IdSource ids);                     // default: UUID
        public Builder legacyActiveSessionId(String idOrNull); // from the old SharedPreferences key
        public Deck open();                                   // loads; never throws
    }
    public DeckState state();
    public void dispatch(DeckAction action);
    public Subscription subscribe(DeckListener listener);     // listener.onDeck(state) at once, then after every transaction
    public void close();                                      // cancels every running request
}
public interface DeckListener { void onDeck(DeckState state); }
public interface Subscription { void close(); }
```

```java
public final class DeckState {                 // immutable, self-consistent
    public final long revision;                // +1 per committed transaction
    public final List<SessionLine> sessions;   // id, title, cardCount, active — switcher order
    public final SessionFace active;           // id, title, seedPrompt, graveyard: List<GraveLine>
    public final Lens lens;
    public final List<TreeRow> rows;           // active session seen through lens
    public final List<String> selection;       // splice selection, tap order
    public final CreativeSkill skill;          // active skill (copy)
    public final List<CreativeSkill> skills;   // built-ins, then custom
    public final ProviderSettings provider;    // provider.ready() is EndpointRule's verdict
    public final List<RequestLine> requests;   // running, plus finished hand-backs not yet dismissed
    public final RequestLine busy;             // loading bar: latest-started running request with a label, else null
    public final List<Notice> notices;         // one-shots awaiting ack, oldest first
    public final String undoLabel, redoLabel;  // null = nothing to undo / redo
    public final String saveProblem;           // null, or why the last write failed
    public RequestLine lane(String lane);      // null when the lane is empty
}
public final class GraveLine {
    public final String id, reasonTag, rule, title;   // title: archived root's, or null
    public final boolean active, revivable;
    public final int archivedCount;                   // whole subtree
}
public final class RequestLine {
    public static final int RUNNING = 0, DONE = 1, FAILED = 2;
    public final String lane, sessionId, label, source;   // source: name of the TextSource answering
    public final int phase;
    public final String partial;                          // text so far from a streaming source, else ""
    public final Result result;                           // DONE
    public final EngineError error;                       // FAILED
}
public final class Notice {
    public static final String TOAST = "TOAST", FAILURE = "FAILURE", NEED_KEY = "NEED_KEY",
                               SCROLL_TOP = "SCROLL_TOP", SEED = "SEED", SHARE = "SHARE";
    public final long id;
    public final String kind, title, body;     // FAILURE: title = "SPARK_FAILED", body = details
}
```

```java
public final class Actions {
    // cards
    public static DeckAction star(String cardId);
    public static DeckAction collapse(String cardId);
    public static DeckAction select(String cardId);                 // toggles splice selection
    public static DeckAction clearSelection();
    public static DeckAction banish(String cardId, String reasonCode, String customRule);
    public static DeckAction lens(Lens lens);
    // graveyard
    public static DeckAction rule(String entryId, boolean active);
    public static DeckAction revive(String entryId);
    public static DeckAction purge(String entryId);
    // sessions
    public static DeckAction openSession(String sessionId);
    public static DeckAction newSession(String rawName);            // Deck upper-cases, spaces to '_'
    public static DeckAction renameSession(String rawName);
    public static DeckAction deleteSession();                       // no-op when only one
    public static DeckAction setSeed(String text);
    // skills
    public static DeckAction useSkill(String skillId);
    public static DeckAction forgeSkill(String name, String badge, String instruction);
    public static DeckAction deleteSkill(String skillId);
    // requests
    public static DeckAction ask(Ask ask);                          // any Move
    public static DeckAction spark(String seedText);                // seed check, setSeed, ask(SPARK)
    public static DeckAction branch(String cardId, String skillIdOrNull);
    public static DeckAction splice();
    public static DeckAction cancel(String lane);
    public static DeckAction dismiss(String lane);                  // drop a finished hand-back
    // provider
    public static DeckAction saveProvider(ProviderSettings settings);
    // housekeeping
    public static DeckAction ack(long noticeId);                    // acknowledges every notice with id <= noticeId
    public static DeckAction export();                              // emits a SHARE notice
    public static DeckAction undo();
    public static DeckAction redo();
    public static DeckAction all(DeckAction... inOrder);            // one transaction, one undo step
}

public final class Ask {                       // what was asked, before any context is gathered
    public static Ask of(Move move);
    public Ask in(String sessionId);           // default: the session active at dispatch
    public Ask on(List<String> cardIds);       // anchors
    public Ask skill(String skillId);          // default: the active skill
    public Ask text(String text);
    public Ask option(String key, String value);
    public Ask using(ProviderProfile profile); // default: the saved current profile (PING passes the unsaved draft)
    public Ask lane(String lane);              // default: the Move decides
}

public interface Move extends Recipe {         // Recipe (compose + parse) is defined by the engine, 1.3
    /** UI thread, at dispatch, before anything changes. Throw Refusal(notice or null) to decline. */
    Plan plan(Ask ask, DeckReader deck) throws Refusal;
}
public final class Plan {
    public final String lane;                  // at most one running request per lane
    public final String label;                 // loading-bar text; null = silent
    public final String failureCode;           // FAILURE notice title; null = error stays in the lane for the asker
    public final boolean needsKey;             // unusable profile: NEED_KEY notice, nothing starts
    public final List<String> anchors;         // cards that must still exist when the reply lands
    public final Landing landing;              // where Result.cards go; null = hand-back only
}
public final class Landing {
    public final String parentId;              // null = roots at index 0; else directly after the parent in list order
    public final List<String> sources;         // provenance stamped on every new card (splice: every selected id)
    public final String badge; public final int accent;
    public final boolean expandParentAtStart, expandParentOnLanding, clearSelection, scrollToTop;
}
public interface ContextSource {               // UI thread, at dispatch; read only
    void contribute(Plan plan, Ask ask, DeckReader deck, Facts.Builder into);
}
public interface DeckReader {                  // every session, read only; valid only during the call
    DeckState state();
    List<String> sessionIds();
    IdeaTree tree(String sessionId);
    List<GraveLine> graveyard(String sessionId);
    String seed(String sessionId);
}
```

**Invariants and ordering.**

1. *One thread.* Every method runs on the thread that called `open()`; any other thread throws `IllegalStateException`.
2. *Run to completion.* A `dispatch` made inside a transaction or a listener callback is queued and runs after it. Listeners see only committed states, in revision order.
3. *Bound at dispatch.* An ask captures session id, anchors, `Facts` and `ProviderProfile` when dispatched. Its reply lands in that session or nowhere; landing never reads "active". The request is cancelled the moment its session is deleted, an anchor leaves the tree (banish, undo) or `close()` runs.
4. *Lanes.* One running request per lane; an ask into a busy lane is dropped with no state change. Default lanes: `sessionId/anchorId` (branch, splice), `sessionId/root` (spark), `lab`, `ping`. No queue.
5. *Truthful bar.* `busy != null` exactly when a labelled request is running.
6. `selection` is always a subset of the active session's card ids; cleared on any change of active session.
7. A transaction that changes saved data writes it before listeners are told. A failed write keeps the in-memory state and sets `saveProblem`.
8. Any landing that adds cards sets `lens = Lenses.ALL`.
9. There is always at least one session, and `active` is never null.

**Error modes.** Unknown card, session, entry or skill id: silent no-op (a stale tap from an older render is normal). Refused precondition: a `TOAST` notice, nothing else changes. Engine error: `FAILURE` notice when `failureCode != null`, else `RequestLine.error` in the lane. Nothing but invariant 1 throws.

**Where UI-facing state lives.** Lens, splice selection and active skill live in Deck, in memory only (cold start still gives ALL / empty / first skill). They cannot live in the Activity: a reply resets the lens and clears the selection while no Activity is attached, banish edits the selection, and deleting a custom skill moves the active skill. The seed text being typed stays in the `EditText`; Deck owns the session's saved seed and emits a `SEED` notice when the dock must adopt it (session switch, create, delete).

### 1.2 IdeaTree (C2)

```java
public final class IdeaTree {                                   // immutable in shape
    public static IdeaTree of(List<IdeaCard> cardsInListOrder); // O(n); never writes to a card
    // O(1)
    public int size();
    public boolean contains(String id);
    public IdeaCard card(String id);
    public String parentOf(String id);                 // null for roots, orphans and cycle-breakers
    public int depthOf(String id);
    public int subtreeSize(String id);                 // descendants, self excluded
    // O(answer)
    public List<String> childrenOf(String idOrNull);   // null = roots; list order
    public List<String> lineageOf(String id);          // root ... parent
    public List<String> siblingsOf(String id);         // same parent, self excluded
    public List<String> subtreeOf(String id);          // pre-order, self first
    public List<String> sourcesOf(String id);          // splice / converge provenance
    public List<String> derivedFrom(String id);        // cards naming id as a source
    // one pass, O(n)
    public List<TreeRow> rows(Lens lens);
    // shape changes: each returns a new tree, O(n)
    public IdeaTree withRoots(List<IdeaCard> cards);                      // index 0
    public IdeaTree withChildren(String parentId, List<IdeaCard> cards);  // directly after the parent
    public Subtree subtree(String rootId);                                // cards pre-order, parent links intact
    public IdeaTree without(String rootId);                               // drops the whole subtree
    public IdeaTree withSubtree(Subtree subtree, String parentIdOrNull);  // null or missing parent: roots at index 0
    public List<IdeaCard> cards();                                        // list order, for saving
}
public interface Lens {
    String name();
    boolean nested();            // false: flat, card-list order, indent 0, no rails
    boolean honoursCollapse();   // true: descendants of a collapsed card are hidden
    boolean shows(IdeaCard card, IdeaTree tree);
}
public final class Lenses { public static final Lens ALL, STARRED, ROOTS, FULL; }

public final class TreeRow {                  // immutable copy of everything a row draws
    public final String cardId, title, summary, rationale, badge;
    public final int accent;
    public final List<String> tags, sources;
    public final Map<String, String> meta;    // open annotations (scores...)
    public final boolean starred, collapsed;
    public final int depth;                   // true depth
    public final int indent;                  // shown depth under this lens; never clamped
    public final long rails;                  // bit i set: column i carries a pass-through rail (i < indent - 1)
    public final boolean lastSibling;         // false: the elbow's vertical continues to the row below
    public final boolean childrenShown;
    public final int subtreeSize;
}
```

Rules: list order is sibling order. A card whose parent is missing, itself, or on a cycle is a root. Under a nested lens a row hangs from its nearest *shown* ancestor, so a rail never refers to a row that is not in the list; STARRED and ROOTS are flat, so they draw no rails at all (defect 4). `rails` and `lastSibling` are computed in the same pre-order pass (child mask = parent mask, plus the parent's own column when the parent is not a last sibling). Multi-parent splice: `parentId` stays the single tree edge (the anchor); `IdeaCard.sources` records every fused card; `sourcesOf` / `derivedFrom` answer both directions.

### 1.3 Idea Engine (C3)

```java
public interface Recipe {
    String kind();
    Prompt compose(Brief brief);                 // pure
    Result parse(Brief brief, String rawText);   // pure; prose degrades to the freeform result, never throws
}
public final class IdeaEngine {
    public IdeaEngine(List<TextSource> sourcesInPreferenceOrder, Executor worker, Executor deliver);
    public Running start(Recipe recipe, Brief brief, Listener listener);   // returns at once
    public interface Listener { void onPartial(String textSoFar); void onResult(Result r); void onError(EngineError e); }
    public interface Running { String source(); void cancel(); }
}
public interface TextSource {
    String name();                               // "live", "local", "script"
    boolean accepts(Brief brief);
    String fetch(ModelCall call, Stream stream) throws IOException;   // blocking, on the worker
    interface Stream { void delta(String chunk); boolean cancelled(); }
}
public final class Brief {                       // immutable; all the engine may know; built on the UI thread
    public final String kind, text;              // text: seed (spark) or raw prompt (optimise)
    public final CreativeSkill skill;            // copy, or null
    public final List<CardFact> anchors;         // title, summary, rationale; ask order
    public final Facts facts;                    // open bag, key -> lines: "seed", "global", "rules", "lineage", "siblings"
    public final Map<String, String> options;    // "framework", future knobs
    public final ProviderProfile profile;        // captured at dispatch
    public final float temperature;              // captured at dispatch
}
public final class Prompt    { public final String system, user; public final float temperature; }
public final class ModelCall { public final Brief brief; public final Prompt prompt; }
public final class Result {
    public final List<CardDraft> cards;                      // title, summary, rationale, tags: no ids, no shape
    public final Map<String, Map<String, String>> marks;     // cardId -> annotations merged into IdeaCard.meta
    public final PromptOptimizationResult rewrite;           // text handed back, or null
    public final String note;                                // e.g. the ping line
    public final String source;                              // which TextSource answered
    public final String fallbackReason;                      // null, or the error that forced a later source
}
public final class EngineError {
    public static final String NOT_CONFIGURED = "NOT_CONFIGURED", ENDPOINT = "ENDPOINT",
                               HTTP = "HTTP", NETWORK = "NETWORK", EMPTY = "EMPTY";
    public final String code, message;           // message is what the dialog shows
}
```

`start` picks the first source that `accepts` the brief; `Running.source()` is known immediately. If that source fails and a later one accepts, the later one answers and `fallbackReason` carries the first error. If none accepts: `NOT_CONFIGURED` with `EndpointRule`'s problem text. Exactly one of `onResult` / `onError` arrives on `deliver`, or nothing after `cancel()`. **The engine decides the offline fallback**: the heuristic is a `TextSource` that accepts only `optimise` briefs and answers in the same JSON the OPTIMISE recipe parses.

### 1.4 Provider profile (C4)

```java
public final class ProviderProfile {             // immutable, one per provider
    public final String providerId, baseUrl, model, apiKey;
    public static ProviderProfile defaults(String providerId);
    public String label();                       // "OPENAI" ... "CUSTOM_URL"
    public EndpointRule.Verdict endpoint();
}
public final class ProviderSettings {            // immutable: everything the BYOK sheet edits
    public final String currentProviderId;
    public final Map<String, ProviderProfile> profiles;
    public final float temperature;
    public final String globalContext;
    public ProviderProfile current();
    public boolean ready();                      // current().endpoint().usable
    public Draft edit();
    public static final class Draft {            // mutable, sheet-local; writes nothing
        public Draft select(String providerId);  // shows that provider's saved profile; drops unsaved edits to the previous one
        public Draft baseUrl(String v); public Draft model(String v); public Draft apiKey(String v);
        public Draft temperature(float v); public Draft globalContext(String v);
        public ProviderProfile selected();
        public ProviderSettings build();         // blank url/model -> defaults; temperature clamped 0..1.5
    }
}
public final class EndpointRule {                // the only code that knows what "local" means
    public static Verdict judge(String baseUrl, String apiKey);
    public static final class Verdict {
        public final String chatUrl;             // normalised .../chat/completions, or null
        public final boolean local;              // parsed host is localhost, 127.x, 10.0.2.2 or ::1
        public final boolean keyRequired;        // !local
        public final boolean cleartextOk;        // http allowed only when local
        public final boolean usable;
        public final String problem;             // null, or today's exact message
    }
}
```

### 1.5 Stores

```java
public interface DocumentStore {                 // named JSON documents
    String read(String name) throws IOException;                  // null when absent
    void write(String name, String content) throws IOException;   // all-or-nothing replace; keeps one .bak
    String readBackup(String name) throws IOException;
    void quarantine(String name);                                 // moves the bytes aside; never deletes
}
public interface ProviderStore { ProviderSettings load(); void save(ProviderSettings settings); }
public interface Clock    { long now(); }
public interface IdSource { String next(); }
```

## 2. Usage

```java
// MainActivity
deck = new ViewModelProvider(this, DeckViewModel.factory(getApplication()))
        .get(DeckViewModel.class).deck;

@Override protected void onStart() { super.onStart(); sub = deck.subscribe(this::render); }
@Override protected void onStop()  { sub.close(); super.onStop(); }

private void render(DeckState s) {
    tvSessionTitle.setText(s.active.title + " ▾");
    tvGraveCount.setText(String.valueOf(s.active.graveyard.size()));
    tvByokStatus.setText(s.provider.ready() ? s.provider.current().label() : "SET_KEY");
    styleFilterTab(tabFilterStarred, s.lens == Lenses.STARRED);
    layoutLoadingBar.setVisibility(s.busy != null ? View.VISIBLE : View.GONE);
    if (s.busy != null) tvLoadingStatus.setText(s.busy.label);
    cardAdapter.submit(s.rows, s.selection, s.skill);
    if (!s.notices.isEmpty()) {
        for (Notice n : s.notices) perform(n);   // toast, error dialog, open BYOK, scroll, set seed, share
        deck.dispatch(Actions.ack(s.notices.get(s.notices.size() - 1).id));
    }
}

btnSparkIdeas.setOnClickListener(v -> deck.dispatch(Actions.spark(etSeedPrompt.getText().toString())));
tabFilterStarred.setOnClickListener(v -> deck.dispatch(Actions.lens(Lenses.STARRED)));
btnExecuteMashup.setOnClickListener(v -> deck.dispatch(Actions.splice()));

// IdeaCardAdapter.Listener now speaks card ids
public void onQuickBranch(String cardId) { deck.dispatch(Actions.branch(cardId, null)); }
public void onToggleStar(String cardId)  { deck.dispatch(Actions.star(cardId)); }
// banish dialog:  deck.dispatch(Actions.banish(cardId, reasonCodes[which], note));
// connector:      holder.branchConnector.setRails(row.indent, row.rails, row.lastSibling, row.accent);
```

```java
// GraveyardSheet.show(context, deck): renders state, mutates nothing
Subscription sub = deck.subscribe(s -> renderEntries(s.active.graveyard));
dialog.setOnDismissListener(d -> sub.close());
btnToggle.setOnClickListener(v -> deck.dispatch(Actions.rule(line.id, !line.active)));
btnRevive.setOnClickListener(v -> deck.dispatch(Actions.revive(line.id)));
btnPurge.setOnClickListener(v -> deck.dispatch(Actions.purge(line.id)));

// SkillsSheet: rows from s.skills
deck.dispatch(Actions.useSkill(skill.id));
if (targetCardId != null) deck.dispatch(Actions.branch(targetCardId, skill.id));
deck.dispatch(Actions.forgeSkill(name, badge, instruction));

// PromptLabSheet: no fallback logic left
deck.dispatch(Actions.ask(Ask.of(Moves.OPTIMISE).text(raw).option("framework", mode)));
// in its subscription:
RequestLine r = s.lane("lab");
if (r != null && r.phase == RequestLine.RUNNING && "live".equals(r.source)) showRewriting();
if (r != null && r.phase == RequestLine.DONE) {
    renderResult(r.result.rewrite);
    if ("local".equals(r.result.source)) toast(r.result.fallbackReason == null
            ? "Upgraded via local heuristic (Add BYOK key for live LLM rewrite)"
            : "API fallback used: " + r.result.fallbackReason);
    deck.dispatch(Actions.dismiss("lab"));
}

// ByokSettingsSheet: a draft; only SAVE commits
ProviderSettings.Draft draft = deck.state().provider.edit();
chipGroq.setOnClickListener(v -> { draft.select("groq"); fillFields(draft.selected()); });
btnPing.setOnClickListener(v -> deck.dispatch(Actions.ask(Ask.of(Moves.PING).using(readFields(draft)))));
btnSave.setOnClickListener(v -> deck.dispatch(Actions.saveProvider(readFields(draft).build())));
```

## 3. Hidden implementation

- **Deck.** Private mutable records (`BrainstormSession` with an `IdeaTree` per session), a run-to-completion queue, the lane table, a notice list, the undo journal. An action is an object with a package-private `run(DeckTxn)`; `DeckTxn` is never public, so tests cannot reach past `dispatch`. After each transaction Deck saves dirty documents, rebuilds `DeckState` once (rows O(n)) and notifies.
- **Ask flow.** `move.plan` (may refuse) → key check via `ProviderSettings.ready()` when `needsKey` → lane check → `expandParentAtStart` → every `ContextSource` contributes → `Brief` frozen → `engine.start`. Landing: session and anchors still present? Stamp ids, times, badge, accent, `parentId`, `sources`; `tree.withRoots` / `withChildren`; merge `marks`; save; lens ALL; `SCROLL_TOP` if asked.
- **Undo.** Before a transaction first touches a session, its JSON is pushed as a checkpoint (20 deep, in memory). Undo restores that session wholesale and applies invariant 3 to running requests. Replies are journalled like any other transaction.
- **Banish / revive.** `tree.subtree(id)` is archived whole in `GraveyardEntry.archivedSubtree` (additive key; `archivedCard` stays the root). Revive uses `withSubtree(subtree, null)`: index 0 as today, shape intact. Old entries revive as one root.
- **Codec.** Pure org.json. Sessions file stays a top-level array. Additive keys: `sources`, `meta` on cards, `archivedSubtree` on entries, plus a new `neural_deck_state_v1.json` (active session id; the legacy pref is the fallback). `depth` is still written, from `depthOf`, but never read. Load order: file → `.bak` → starter session; an unparseable file is quarantined first.
- **Engine.** `ReplyReader` strips `<think>` blocks and code fences, then tries each *balanced* JSON value (string- and escape-aware) until one yields ideas; otherwise the freeform card. Shared prompt frames keep today's wording; BRANCH adds lineage and "already branched" sibling titles; SPLICE keeps `min(1.2, t + 0.1)`. `HttpTextSource` keeps today's body, headers, timeouts and error texts, refuses cleartext unless `cleartextOk`, and polls `stream.cancelled()`. The heuristic appends the active rules to its rewrite before claiming "Bound N Graveyard…".
- **Rails.** The connector view draws from `(indent, rails, lastSibling, accent)` with no clamp; a `RecyclerView.ItemDecoration` reading the same row bridges the 10dp row margin (code only, `res/` untouched).
- **Android glue.** `DeckViewModel` builds the engine (three-thread pool, main-looper `Executor`) and the Deck, and calls `deck.close()` in `onCleared()`.

## 4. Dependency strategy and adapters

| dependency | category | seam | adapters |
|---|---|---|---|
| tree shape, lanes, journal, notices, codec | in-process | none | — |
| request kinds | in-process | `Move` / `Recipe` | SPARK, BRANCH, SPLICE, OPTIMISE, PING |
| tree views | in-process | `Lens` | ALL, STARRED, ROOTS, FULL |
| prompt facts | in-process | `ContextSource` | seed, global, rules, lineage, siblings |
| files | local-substitutable | `DocumentStore` | `FileDocumentStore` (prod; temp dir in tests), `MemoryDocumentStore` |
| SharedPreferences + keystore | ports & adapters | `ProviderStore` | `PrefsProviderStore` (+ `KeyVault`), `MemoryProviderStore` |
| model over HTTP | true external (mock) | `TextSource` | `HttpTextSource`, `HeuristicTextSource`, `ScriptedTextSource` |
| threads | in-process | `java.util.concurrent.Executor` | pool / main-looper poster; `DirectExecutor`, `ManualExecutor` |
| time, ids | in-process | `Clock`, `IdSource` | system / fixed, counting |
| screens | ports & adapters | `DeckListener` | `MainActivity`, four sheets, a recording listener |

Every seam has at least two adapters today. No seam exists for save policy, lane admission or undo: each has one behaviour, so each stays implementation.

## 5. Trade-offs

**High leverage.** `dispatch` + `state`: about thirty gestures and every lifecycle rule behind three methods; a test is "dispatch these, assert that state". `Move`: label, error code, landing, prompt wording and parsing for one kind in one class, so locality per kind is total. `IdeaTree.rows(lens)`: one algorithm serves the adapter, the rails, export and every future filter. `TextSource` chain: offline fallback, streaming and test scripting are one rule.

**Thin.** `DocumentStore` and `ProviderStore` are small on both sides. `Landing` is four booleans encoding three known behaviours: honest, but a flag bag. `Actions` is a wide vocabulary; discovering what the Deck can do means reading a factory, not a class.

**Given up.** Compile-time checking in three places: `Facts` keys, `Ask.options`, and `Result`'s three nullable slots are conventions between a Move and its composer. Notices need an ack; a screen that forgets to ack repeats a toast. Roughly 30 public types for a 3,300-line app. Dropping a second ask into a busy lane is silent, because zero visual change leaves no new string to say why. `Lens`, `Move` and `ContextSource` are open on the strength of futures that may not come; the closed effect set is what stops that being free-form.

## 6. Test plan

No test reaches past an interface; JUnit 4, plain JVM.

| interface crossed | adapters | tests (defect) |
|---|---|---|
| `Deck.dispatch` / `state` | Memory stores, real engine + `ScriptedTextSource`, `ManualExecutor` worker, direct deliver, fixed clock, counting ids | (1) branch in A, `openSession(B)`, release reply: children sit after the parent in A, B unchanged. (2) two lanes running, first lands: `busy` still set, carrying the second label. (3) banish a 4-card subtree: `archivedCount == 4`; revive restores shape. Session deleted or anchor banished mid-flight: reply dropped, no notice. (8) scripted source's captured `ModelCall` holds lineage and sibling titles; hybrids carry every source. (9) `saveProvider` mid-flight: captured profile unchanged. (13) corrupt document: quarantined, backup loaded. No listener attached: reply lands and is saved; a later `subscribe` receives it and any `FAILURE` notice. Golden strings: labels, codes, toasts. Legacy JSON fixtures load. Undo / redo. Every "preserve exactly" bullet. |
| `IdeaTree` | none | (4) STARRED and ROOTS rows have indent 0. (5) rails table for mixed-depth forests, depth 9 unclamped. (6) 50,000 cards: `of` + `rows` inside a linear budget. Orphans, cycles, shape-change round trips. |
| `Recipe.compose` / `parse` per Move | none | (7) prose, fenced, `<think>{…}</think>` then JSON, array root, `cards` / `branches` keys; golden prompts. |
| `IdeaEngine.start` | two scripted sources | (12) fallback order and `fallbackReason`; exactly-once delivery; cancel; partials. |
| `HeuristicTextSource.fetch` | none | (12) rules claimed only when present in the text. |
| `EndpointRule.judge` | none | (11) host table, including `localhost.evil.com`. |
| `ProviderSettings.edit()` | none | (10) `select` then abandon leaves settings equal. |
| `DocumentStore` contract | run against File (temp dir) and Memory | (13) failed write leaves the old content; quarantine keeps bytes. |

Android adapters get one instrumented smoke test: the legacy prefs keys load.

## 7. Migration order

Each step compiles, ships, and behaves as before except for the named fix.

1. Test source set (JUnit 4, `org.json:json`, test-only). `EndpointRule`; `AiConfig.isConfigured` and the client both call it (11).
2. `IdeaTree`, `TreeRow`, `Lenses`. `BrainstormSession`'s walks, counts and `toMarkdown` delegate; the adapter binds rows; the connector takes rails (4, 5, 6).
3. `ReplyReader`, the five recipes' `compose` / `parse`, `TextSource`, `HttpTextSource`, `IdeaEngine`. `OpenAiCompatibleClient` keeps its callbacks but delegates (7, 12 engine side).
4. `ProviderProfile`, `ProviderSettings`, `ProviderStore`; `AiConfig` reads through them; the BYOK sheet edits a `Draft` (10).
5. `DocumentStore` and the codec; `BrainstormRepository` delegates; additive keys start being written (13).
6. Sheets become callback-only: no sheet mutates a session or touches a store.
7. `Deck` + `DeckViewModel` for everything except requests; `MainActivity` renders `DeckState`. The old client's replies land through a temporary session-id-bound action (1, 3).
8. `Move.plan` for the five kinds and `Actions.ask`; the temporary action, `OpenAiCompatibleClient`, `LocalPromptHeuristic`, `AiConfig` and `BrainstormRepository` are deleted (2, 8, 9, rest of 12).
9. Drop `IdeaCard.depth` as a field. Undo is already live behind `Actions.undo()`; it waits only for a button.
