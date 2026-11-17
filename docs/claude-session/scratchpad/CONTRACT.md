# NEURAL//DECK — implementation contract (Phase A)

The final design, merged from four independent designs and two judges (`design/design-*.md`). Every
implementer works to this file. Where this file and a design disagree, this file wins.

**Phase A changes nothing a user can see.** All 28 style-lock screenshots must stay pixel-identical, with
one exception, `sheet_prompt_lab_offline`, where the untrue line `+ Bound 1 Graveyard anti-pattern
constraint(s)` disappears (defect D14). Every user-visible string stays byte-identical. Rail geometry,
STARRED indentation, dialog styling, new screens and new strings are **Phase B**: previewed to the owner
first, not built here.

Vocabulary: module, interface, implementation, depth, seam, adapter, leverage, locality (see
`codebase-design/SKILL.md`). Domain terms: see `GLOSSARY.md` in the repo.

## 0. Ground rules for implementers

- Java only, source level 17, but no records, sealed types, pattern matching, `var`, text blocks,
  switch expressions, annotation processors or Kotlin (the owner also builds with CodeAssist). Lambdas and
  `java.util.function` are fine (minSdk 26). No new runtime dependencies.
- Pure modules (`tree`, `engine` except `HttpModelText`, `provider` except `PrefsProfileStore`, `storage`
  except `DeviceSessionsStore`, `deck` except `DeckHolder`) import nothing from `android.*` or `androidx.*`.
  Model classes in `model/` already use only `org.json`; keep it that way.
- `res/` is untouched except two new files under `res/xml/` (backup rules). No layout, drawable, colour,
  string or style edits.
- Tests: JUnit 4 on the plain JVM for pure modules (`app/src/test/java/...`, with `org.json:json` already
  on the test classpath); Robolectric only for Android adapters and the existing outside-in suites.
- Build and test only through the lock script:
  `bash <scratchpad>/tools/gradle-run.sh <your-copy> :app:testDebugUnitTest --console=plain`.
- Keep `app/module.toml` untouched; the source roots do not change.

## 1. Module map

| package | public types | Android? |
|---|---|---|
| `provider` | `ProviderProfile`, `ProviderSettings`, `EndpointRule`, `ProfileStore`, `PrefsProfileStore` | only `PrefsProfileStore` |
| `engine` | `IdeaEngine`, `ModelText`, `ModelCall`, `ModelTextException`, `HttpModelText`, `ModelConfig`, `CardRequest`, `CardFacts`, `SkillSpec`, `GraveRule`, `CardDraft`, `CardReply`, `OptimiseRequest`, `OptimiseReply`, `PingRequest`, `PingReply`, `Pending` | none (HttpModelText uses `java.net` only) |
| `tree` | `IdeaTree` (+ `IdeaTree.Row`, `IdeaTree.Filter`) | none |
| `storage` | `SessionsStore`, `SessionFiles`, `DeviceSessionsStore`, `KeyVault` (existing) | `DeviceSessionsStore`, `KeyVault` |
| `deck` | `Deck`, `Action`, `State`, `Cue`, `DeckCopy`, `StarterDeck`, `DeckHolder` | only `DeckHolder` |
| `model` | `BrainstormSession`, `IdeaCard`, `GraveyardEntry`, `CreativeSkill` (existing, trimmed) | none |
| `ui` | sheets, adapter, views (existing, rewired) | yes |

Deleted at the end: `ai/OpenAiCompatibleClient`, `ai/LocalPromptHeuristic`, `storage/AiConfig`,
`storage/BrainstormRepository`, `model/PromptOptimizationResult`, and `BrainstormSession`'s tree walks,
`countSubtree`, `collectSubtree`, `buildVisibleTree`, `toMarkdown`.

Seams and their adapters (each seam has two):

| seam | production adapter | test adapter |
|---|---|---|
| `engine.ModelText` | `HttpModelText` | `ScriptedModelText` (test sources) |
| `provider.ProfileStore` | `PrefsProfileStore` (same pref keys + `KeyVault`) | `MemoryProfileStore` (test sources) |
| `storage.SessionsStore` | `DeviceSessionsStore` (`SessionFiles` + `active_session_id` pref) | `MemorySessionsStore` with save-failure injection (test sources) |
| `java.util.concurrent.Executor` (×3) | single-thread card worker, cached side pool, main-thread poster | direct / `ManualExecutor` (test sources) |
| `deck.Deck.Screen` | `MainActivity` | `RecordingScreen` (test sources) |

## 2. provider

```java
package com.example.wtfff.provider;

public final class EndpointRule {
    /** Lower-cased host of an http(s) URL, IPv6 without brackets; "" when unparseable. java.net.URI only. */
    public static String host(String url);
    /** Local or private network, decided from the literal host only (never DNS):
     *  "localhost"; 127.0.0.0/8; ::1; 10.0.0.0/8 (includes the emulator host 10.0.2.2); 172.16.0.0/12;
     *  192.168.0.0/16; 100.64.0.0/10 (carrier NAT, Tailscale); 169.254.0.0/16; fc00::/7; fe80::/10.
     *  Anything else, including "localhost.evil.com" and "my-pc.local", is not local. */
    public static boolean isLocal(String url);
    /** A key is required unless the endpoint is local. */
    public static boolean keyRequired(String url);
    /** null when a request may be sent. For http:// to a non-local host:
     *  "Cleartext HTTP traffic to <host> not permitted" (Android's own wording). */
    public static String refusal(String url);
    /** Trailing slashes removed; "/chat/completions" appended unless already present. */
    public static String chatUrl(String baseUrl);
}

public final class ProviderProfile {             // immutable; one provider's own fields
    public static final String OPENROUTER = "openrouter", OPENAI = "openai", GROQ = "groq",
            DEEPSEEK = "deepseek", CUSTOM = "custom";
    public final String providerId, baseUrl, model, key;   // never null, trimmed
    public ProviderProfile(String providerId, String baseUrl, String model, String key);
    public static ProviderProfile defaults(String providerId);   // default URL and model, empty key
    public static String defaultBaseUrl(String providerId);       // values exactly as in AiConfig today
    public static String defaultModel(String providerId);
    public static String label(String providerId);                // OPENROUTER, OPENAI, GROQ, DEEPSEEK, CUSTOM_URL
    public String label();
    /** "Configured": URL present and (key present or EndpointRule says no key is needed). Any provider. */
    public boolean ready();
    /** SAVE semantics: an empty URL or model becomes the provider default. */
    public ProviderProfile withBlanksDefaulted();
}

public final class ProviderSettings {            // immutable; vault-wide values (single prefs today)
    public final String selectedId;              // default "openrouter"
    public final float temperature;              // default 0.85f; constructor clamps to [0, 1.5]
    public final String globalContext;           // default ""
    public ProviderSettings(String selectedId, float temperature, String globalContext);
    public static ProviderSettings defaults();
}

public interface ProfileStore {
    ProviderSettings settings();
    /** That provider's saved URL/model/key, or its defaults. Never changes the selected provider. */
    ProviderProfile profile(String providerId);
    /** SAVE in the vault sheet: selects profile.providerId and writes its URL, model and key, plus the
     *  settings' temperature and global context. Other providers' values are untouched. */
    void commit(ProviderProfile profile, ProviderSettings settings);
}

public final class PrefsProfileStore implements ProfileStore {   // Android adapter
    public PrefsProfileStore(android.content.Context context);
}
```

`PrefsProfileStore` reads and writes exactly today's storage: prefs `neural_deck_ai_config` with
`provider_id`, `base_url_<id>`, `model_<id>`, `temperature` (a **float** pref), `global_context`; keys via
the existing `KeyVault` (prefs `neural_deck_vault_prefs`). Saving an empty key removes that provider's slot,
as today.

## 3. engine

```java
package com.example.wtfff.engine;

public final class ModelConfig {                 // captured when the user taps; never re-read
    public final String baseUrl, model, key, globalContext;
    public final float temperature;
    public ModelConfig(String baseUrl, String model, String key, float temperature, String globalContext);
    public static ModelConfig of(ProviderProfile profile, ProviderSettings settings);
}

public final class ModelCall {                   // one chat completion
    public final String chatUrl, model, key, system, user;   // model already defaulted to gpt-4o-mini if blank
    public final float temperature;
}
public interface ModelText {                     // THE seam for model text. Blocking.
    String complete(ModelCall call) throws ModelTextException;   // returns message content ("" allowed)
}
public final class ModelTextException extends Exception { public ModelTextException(String message); }
public final class HttpModelText implements ModelText { public HttpModelText(); }

public final class GraveRule { public final String reasonTag, rule; public GraveRule(String reasonTag, String rule); }
public final class SkillSpec { public final String name, badge, instruction; public final int outputCount; public final int accent;
    public SkillSpec(String name, String badge, String instruction, int outputCount, int accent); }
public final class CardFacts { public final String title, summary, rationale; public CardFacts(String title, String summary, String rationale); }

public final class CardRequest {                 // immutable; built by Deck when the ticket starts
    public enum Kind { SPARK, BRANCH, SPLICE }
    public final Kind kind;
    public final ModelConfig config;
    public static CardRequest spark(ModelConfig c, String seed, SkillSpec skill, List<GraveRule> rules);
    public static CardRequest branch(ModelConfig c, String sessionSeed, SkillSpec skill, List<GraveRule> rules,
                                     CardFacts parent, List<String> lineageTitles /* root first, excludes parent */,
                                     List<String> existingChildTitles);
    public static CardRequest splice(ModelConfig c, String sessionSeed, List<GraveRule> rules,
                                     List<CardFacts> selected /* tap order; first = anchor */);
}
public final class CardDraft {                   // what the model proposed; Deck stamps ids, parents, badge, accent
    public final String title, summary, rationale;
    public final List<String> tags;              // already upper-cased, spaces -> '_', empties dropped
}
public final class CardReply {
    public final List<CardDraft> drafts;         // non-empty on success
    public final String error;                   // non-null on failure; drafts empty
}

public final class OptimiseRequest {
    public OptimiseRequest(ModelConfig configOrNull /* null = no usable provider */, String rawPrompt,
                           String framework, List<GraveRule> rules);
}
public final class OptimiseReply {
    public enum Source { LIVE, OFFLINE_NOT_CONFIGURED, OFFLINE_AFTER_ERROR }
    public final Source source;
    public final String original, framework, optimizedPrompt;
    public final int clarityScore;
    public final List<String> upgrades;
    public final String liveError;               // OFFLINE_AFTER_ERROR only
}
public final class PingRequest { public PingRequest(String baseUrl, String key, String model); }
public final class PingReply { public final boolean ok; public final String text; }  // text exactly as shown
public interface Pending { void cancel(); }      // after cancel() the sink is never called

public final class IdeaEngine {
    public IdeaEngine(ModelText live, Executor cardWorker, Executor sideWorker, Executor replyOn);
    /** One call. sink runs exactly once, on replyOn. Never throws. */
    public void cards(CardRequest request, java.util.function.Consumer<CardReply> sink);
    /** configOrNull == null: offline result delivered synchronously BEFORE this returns (no thread hop).
     *  Otherwise runs on sideWorker; a live failure falls back to the offline result (OFFLINE_AFTER_ERROR). */
    public Pending optimise(OptimiseRequest request, java.util.function.Consumer<OptimiseReply> sink);
    /** Runs on sideWorker (never queued behind card calls). */
    public Pending ping(PingRequest request, java.util.function.Consumer<PingReply> sink);
}
```

**Prompts (package-private `PromptComposer`) are byte-identical to today's** for spark, splice, optimise
and ping, and for the branch system prompt; the golden files under `src/test/resources/golden/aiflow/`
pin them. Only the branch **user** prompt changes, by inserting two optional blocks:

```
Session Root Context: <seed>\n\n                 (only when the seed is non-empty; unchanged)
Lineage (root first):\n- <title>\n...\n           (NEW; only when lineageTitles is non-empty)
Target Parent Card to Branch From:\n- Title: <t>\n- Summary: <s>\n[- Rationale: <r>\n]   (unchanged)
Existing child branches of this card (do not repeat them):\n- <title>\n...   (NEW; only when non-empty)
\nApply the skill [<name>] and return JSON with <n> child branch cards.   (unchanged)
```

Temperatures: spark/branch = config; splice = `t >= 1.2f ? t : Math.min(1.2f, t + 0.1f)` (D17); optimise
0.65; ping 0.2. Splice asks for 3 cards. Spark/branch ask for `skill.outputCount`.

**Checks before any call** (in this order; the message is the reply's error, shown in `*_FAILED` / `LINK_ERROR`):
blank base URL → `Missing Base URL. Configure BYOK endpoint first.`; blank key and
`EndpointRule.keyRequired` → `Missing API Key. Tap [BYOK_KEY] in the top bar to add your key.`;
`EndpointRule.refusal(url)` non-null → that text.

**`HttpModelText`** is today's `executeChatCompletion` minus those checks: same body, headers (OpenRouter
extras when the URL contains `openrouter.ai`), timeouts 20 s/45 s, `HTTP <code>: <message>` /
`HTTP <code> — <first 160 chars>` errors, `Empty choices from provider response.`, `choices[0].message.content`
then `choices[0].text`. An exception with no message becomes `Unexpected AI request error` for card and
optimise calls and `Connection failed` for ping.

**Reply parser (package-private `ReplyParser`), total, never throws:** remove every
`<think>…</think>` block and anything from an unclosed `<think>` to the end; strip a leading code fence
line and trailing fence; then scan each `{` or `[` start in order with a string-aware bracket matcher, and
take the first balanced value that parses (org.json) and yields at least one idea (top-level array, or an
object's `ideas` / `cards` / `branches` array). Item fields: `title` (default `NODE_<i>`), `summary` (alias
`description`), `rationale` (alias `why`), `tags`. If nothing yields an idea, return one draft titled
`AI Synthesis Output`, summary = the raw text trimmed, rationale `Parsed from freeform model output.`
(also for prose: D4). A blank reply is the error `Empty choices from provider response.`

**Offline Prompt Lab (package-private `OfflineModelText`)**: today's `LocalPromptHeuristic` texts exactly,
`clarityScore` 87, framework label `<MODE> (LOCAL_SYNTH)`, **without** the `Bound N Graveyard ...` line
(D14). A live non-JSON optimise reply keeps today's behaviour: raw text, score 88, upgrade
`Structured with <MODE> lens`; a JSON reply without `clarity_score` defaults to 90.

**Ping text:** success `ONLINE // <model as typed> -> <reply trimmed, cut to 90 + "...">`; failure
`LINK_ERROR // <error>`.

## 4. tree

```java
package com.example.wtfff.tree;

public final class IdeaTree {
    public enum Filter { ALL, STARRED, ROOTS }

    /** O(n) snapshot; never writes to the cards; immutable afterwards. */
    public static IdeaTree of(List<IdeaCard> cards);

    public List<Row> rows(Filter filter);        // see rules below
    public List<Row> outline();                  // every card, pre-order, ignoring collapse (export)
    public Row row(String cardId);               // null when absent
    public List<Row> ancestors(String cardId);   // root first, excluding the card; empty for roots
    public List<Row> children(String cardId);    // card-list order
    public List<Row> subtree(String cardId);     // the card, then its descendants in pre-order

    public static final class Row {              // immutable copy of a card plus derived shape
        public final String id, parentId /* null for roots and orphans */, title, summary, rationale, badge;
        public final int accent;
        public final List<String> tags, splicedFrom;
        public final boolean starred, collapsed;
        public final int depth;                  // derived from the tree; never read from storage
        public final int descendants;            // every card below, collapsed or not
    }

    // Shape edits: pure functions; return a new list holding the same card objects; never mutate input.
    public static List<IdeaCard> withFirst(List<IdeaCard> cards, List<IdeaCard> added);           // spark, revive
    public static List<IdeaCard> withAfter(List<IdeaCard> cards, String anchorId, List<IdeaCard> added); // branch, splice; anchor missing -> appended at the end
    public static List<IdeaCard> without(List<IdeaCard> cards, java.util.Collection<String> ids);
}
```

Rules (Phase A must render exactly like today):

- A card is a **root** when `parentId` is null or names no card in the list (orphan). Children keep
  card-list order. A parent cycle is broken by treating the first unreached card (list order) as a root;
  no card is ever dropped.
- `ALL`: roots in list order, each followed by its descendants in pre-order; descendants of a collapsed
  card are omitted. `depth` = number of ancestors.
- `STARRED`: starred cards in card-list order, each with its **true tree depth** (this is what today's
  screen shows; flattening it is a Phase B preview).
- `ROOTS`: roots in list order, depth 0.
- One pass to build: id→card map, child lists, an iterative pre-order walk. No per-card linear lookups,
  no recursion that can overflow on a 50,000-card chain.

## 5. storage

```java
package com.example.wtfff.storage;

public interface SessionsStore {
    enum Health { OK, RESTORED_BACKUP, QUARANTINED }
    final class Loaded {
        public final List<BrainstormSession> sessions;   // possibly empty; Deck adds the starter session
        public final String activeSessionId;             // possibly null or stale
        public final List<CreativeSkill> customSkills;
        public final Health health;
    }
    Loaded load();                                        // never throws
    void saveSessions(List<BrainstormSession> sessions, String activeSessionId) throws java.io.IOException;
    void saveCustomSkills(List<CreativeSkill> customSkills) throws java.io.IOException;
}

public final class SessionFiles {                         // pure java.io; does the file work
    public SessionFiles(java.io.File dir);
    // sessions: neural_deck_sessions_v1.json ; skills: neural_deck_custom_skills_v1.json
}
public final class DeviceSessionsStore implements SessionsStore {   // SessionFiles + prefs neural_deck_repo_prefs/active_session_id
    public DeviceSessionsStore(android.content.Context context);
}
```

File rules (same for both files):

- **Write**: serialise, write `<name>.tmp`, `FileOutputStream.getFD().sync()`, then rename over `<name>`
  (atomic). Never leave the live file missing or half-written. Synchronous, on the calling thread.
- **Load**: live file missing → empty, `OK`. Live file parses → use it, then copy it to `<name>.bak`
  (the last good copy, refreshed once per launch), `OK`. Live file present but unparseable → rename it to
  `<base>.corrupt-<millis>.json` (e.g. `neural_deck_sessions_v1.corrupt-1700000000000.json`), never
  overwrite it; then try `<name>.bak` → `RESTORED_BACKUP`, else empty → `QUARANTINED`.
- The codec stays in the model classes (`toJson`/`fromJson`). Additive keys only:
  `IdeaCard.splicedFrom` (JSON array of ids, omitted when empty), `GraveyardEntry.archivedSubtree`
  (JSON array of the banished card's descendants in pre-order, each a full card object, omitted when empty).
  `depth` is still written (the value at creation) and ignored on read. Every existing key still loads.
- `active_session_id` stays in prefs `neural_deck_repo_prefs` (read and written as today).

## 6. deck

```java
package com.example.wtfff.deck;

public final class Deck {
    public Deck(SessionsStore store, ProfileStore profiles, IdeaEngine engine);
    public interface Screen { void render(State state); }

    public void send(Action action);             // what the user did
    public void attach(Screen screen);           // becomes the screen; rendered at once with queued cues
    public void detach(Screen screen);           // no-op unless that exact screen is attached
    public State state();                        // latest snapshot, without cues
    public ProviderProfile profile(String providerId);   // vault sheet only (chip tap fills fields)
    public engine.Pending optimise(String rawPrompt, String framework, Consumer<engine.OptimiseReply> sink);
    public engine.Pending ping(String baseUrl, String key, String model, Consumer<engine.PingReply> sink);
}
```

`Action` is an abstract class with package-private `abstract void apply(Deck deck)` (or an equivalent
transaction object): each factory and its rule live together, and there is no switch over a kind.
Factories:

`spark(String seedText)`, `branch(String cardId)` (active skill), `branchWith(String cardId, String skillId)`
(skill becomes active first, even if the branch is then refused), `splice()`, `star(id)`, `collapse(id)`,
`select(id)` (toggle), `clearSelection()`, `banish(id, String reasonCode, String note)`, `toggleRule(entryId)`,
`revive(entryId)`, `purge(entryId)`, `open(sessionId)`, `create(String name)`, `rename(String name)`,
`deleteActive()`, `filter(IdeaTree.Filter)`, `skill(skillId)`, `dock(String text)`, `loadSeed(String text)`,
`labSpark(String text)`, `forge(String name, String badge, String instruction)`, `scrap(skillId)`,
`saveVault(ProviderProfile profile, ProviderSettings settings)`.

```java
public final class State {                       // immutable; final fields; unmodifiable lists
    public final String sessionId, sessionTitle;
    public final String dockText;                // the active session's unsparked draft, else its seed
    public final List<SessionLine> sessions;     // switcher order
    public final List<IdeaTree.Row> rows;        // already filtered
    public final IdeaTree.Filter filter;
    public final List<String> selectedIds;       // splice selection, tap order
    public final List<GraveLine> graveyard;      // newest first
    public final List<SkillLine> skills;         // built-ins in order, then custom
    public final SkillLine activeSkill;
    public final ProviderSettings settings;
    public final String providerLabel;           // null when the active provider is not ready (HUD shows SET_KEY)
    public final String loading;                 // loading-bar text, null when idle
    public final SessionsStore.Health storage;
    public final boolean saveFailed;
    public final List<Cue> cues;                 // one-shot; only in the render that delivers them
    public String markdown();                    // export of the active session, byte-identical to today
    public static final class SessionLine { public final String id, title; public final int cardCount; public final boolean active; }
    public static final class GraveLine { public final String id, reasonTag, rule, archivedTitle /* nullable */; public final boolean active, revivable; }
    public static final class SkillLine { public final String id, name, badge, subtitle; public final int accent; public final boolean builtIn; }
}
public final class Cue {
    public enum Kind { TOAST, ALERT, OPEN_VAULT, SCROLL_TOP, SET_DOCK }
    public final Kind kind;
    public final String title;                   // ALERT: SPARK_FAILED | BRANCH_FAILED | SPLICE_FAILED
    public final String text;                    // TOAST text, ALERT message, SET_DOCK text
    public final boolean caretToEnd;             // SET_DOCK
}
```

**Thread and re-entrancy.** Deck is confined to the thread that created it (the UI thread); calls from
another thread throw `IllegalStateException`. A `send` made inside `render` is queued and runs after that
render returns. Each `send` and each applied reply produces exactly one render of the attached screen.

**Lifetime.** One Deck per process, held by `DeckHolder.get(Context)`, which rebuilds when the
application context differs from the one it was built with (Robolectric gives each test a new one).
A ViewModel is not used: it dies with a finished Activity while a reply is in flight, and a relaunch would
open a second Deck over the same files. Replies with no screen attached are applied and saved; their cues
wait and are delivered once, on the next `attach`.

**Card requests: a FIFO queue of tickets, one call at a time** (today's single worker thread, made
truthful). At tap time a ticket captures: kind, target session id, the typed seed (spark), the skill
(spark/branch), the card id (branch), the selected ids (splice), the `ModelConfig` (D9), and its loading
label. When a ticket reaches the head, Deck builds the `CardRequest` from the target session **as it is
then** (lineage, existing children, the session seed, the active graveyard rules), so a second queued
branch sees the first one's children. `loading` = label of the newest ticket still pending; null when the
queue is empty (D2).
- Target session deleted → its waiting tickets are dropped and a running one's reply is ignored.
- Branch parent / splice anchor gone at start or at reply → ticket dropped silently.
- **Reply applied to the ticket's session by id** (D1): spark → new cards first in that session's list;
  branch → right after the parent, parent un-collapsed; splice → right after the anchor, anchor
  un-collapsed, each hybrid stamped `splicedFrom` = the selected ids (D8). New cards: fresh UUID,
  `createdAt` now, badge/accent from the ticket's skill (splice: `SPLICE_X<n>`, `0xFFFF2E93`), depth =
  parent depth + 1 (stored only). Then, **only if that session is active**: filter → ALL; spark → SCROLL_TOP
  cue; splice → selection cleared. Stamp `updatedAt` on the target session; save.
- Failure → ALERT cue `SPARK_FAILED` / `BRANCH_FAILED` / `SPLICE_FAILED` with the error text, whatever
  session is active. Nothing else changes.

**Refusals, in today's order and wording** (`DeckCopy` holds every Deck string):
- spark: blank seed → TOAST `Enter a seed idea or prompt in the bottom terminal.`; else save the seed to
  the session (and clear its draft); then provider not ready → TOAST `Configure your BYOK API key first.`
  + OPEN_VAULT.
- branch / branchWith: provider not ready → same TOAST + OPEN_VAULT.
- splice: fewer than 2 selected → TOAST `Tap [+SPLICE] on at least 2 idea cards to fuse them.`; then
  fewer than 2 of them still exist → nothing; then provider not ready → TOAST + OPEN_VAULT.
- Labels: `SPARKING_ROOT_NODES // [<badge>]...`, `BRANCHING [<badge>] -> <parent title>`,
  `SPLICING_<n>_NODES_INTO_HYBRIDS...`.

**Everything else** (each saves, then renders):
- star / collapse toggle the flag. select toggles membership (tap order kept); clearSelection empties it.
  The selection is in memory only.
- banish: remove the card and its whole subtree from the session and from the selection; unless the code
  is `DELETE_ONLY`, add a `GraveyardEntry` at index 0 holding the card, its descendants
  (`archivedSubtree`), the reason tag and the rule (the note when non-blank, else today's auto rule), and
  TOAST `Banished! AI will avoid [<title>]`.
- revive: remove the entry; put the archived card back first in the list as a root, followed by its
  archived descendants with their parent links (D3); TOAST `Node revived to workspace.` Entries saved
  before this change have no descendants and revive as today.
- toggleRule flips `activeInPrompts`; purge removes the entry.
- open: clear selection, switch, SET_DOCK with that session's dock text (no caret move). create: name
  `trim().toUpperCase().replace(' ', '_')`, empty → `SESSION_0<count+1>`; new session first in the list,
  active, selection cleared, SET_DOCK "". rename: same normalisation; empty ignored. deleteActive: only
  when more than one session; remove it, its tickets and drafts; the first remaining session becomes
  active; SET_DOCK with its dock text.
- dock(text): remember the active session's unsparked draft (memory only; no render needed but allowed;
  never emits SET_DOCK). The Activity sends it before `open` / `create` (D18).
- loadSeed(text) (Prompt Lab LOAD_TO_DOCK): save as the seed, SET_DOCK text with caret to end.
  labSpark(text): save as the seed, SET_DOCK text (no caret move), then `spark(text)`.
- filter, skill: set. forge: badge blank → first 8 chars of the name upper-cased, spaces → `_`; subtitle =
  instruction cut to 67 + `...` over 70; accent `0xFF39FF88`; output count 3; id `custom_<uuid>`; saved;
  appended after the built-ins. scrap: remove and save; if it was the active skill, the first skill
  becomes active.
- saveVault: `profiles.commit(profile.withBlanksDefaulted(), settings)`; later taps capture the new config.
- `optimise`: blank → the sheet shows its own toast (not Deck). Config = active profile if ready, else
  null. `ping`: config from the typed fields; never saved.
- **Saving**: synchronous after every persisted change. On IOException: keep memory, set `saveFailed`,
  retry on the next event. Starter session (`StarterDeck`, today's content exactly) added and saved when
  the store returns no sessions. Active session: the stored id if it exists, else the first session.

## 7. ui

- `MainActivity implements Deck.Screen`. `onCreate`: views and listeners exactly as today, all of which
  only `send(...)`; `deck = DeckHolder.get(this)`; if `savedInstanceState == null`, set the dock to
  `deck.state().dockText` (rotation keeps typed text via the framework, as today). `onStart` → `attach(this)`;
  `onStop` → `detach(this)`; `onDestroy` → dismiss any open sheet/dialog it showed.
- `render(State)`: HUD (`<TITLE> ▾`, grave count, provider label green `0xFF39FF88` or `SET_KEY` amber
  `0xFFFFB800`), filter tabs, `SKILL: <badge>`, mashup bar text, loading bar, rows into the adapter, empty
  state; then perform cues: TOAST (LENGTH_SHORT), ALERT (`android.app.AlertDialog`, buttons
  `BYOK_SETTINGS` → vault sheet and `DISMISS`), OPEN_VAULT, SCROLL_TOP, SET_DOCK. Session switcher,
  rename/create dialogs, banish dialog, copy-card and export stay in the Activity with today's strings;
  their outcomes are `send`s. Export uses `state.markdown()`.
- `IdeaCardAdapter.submit(List<Row> rows, List<String> selectedIds, String activeBadge)`; the listener
  passes `Row`s / ids. Collapse chip uses `row.descendants`. `PixelBranchConnectorView` is **unchanged**
  (`setBranchSpec(row.depth, row.accent)`).
- Sheets take the Deck and read `deck.state()`; they register nothing with it. `GraveyardSheet` and
  `SkillsSheet` send actions and re-render from `deck.state()`. `PromptLabSheet` uses `deck.optimise`,
  cancels its `Pending` on dismiss, and keeps its own toasts: LIVE → none; OFFLINE_NOT_CONFIGURED →
  `Upgraded via local heuristic (Add BYOK key for live LLM rewrite)`; OFFLINE_AFTER_ERROR →
  `API fallback used: <liveError>` (LENGTH_LONG). `ByokSettingsSheet`: chip tap fills URL/model/key from
  `deck.profile(id)` and writes nothing (D10); PING uses `deck.ping` (cancelled on dismiss); SAVE sends
  `saveVault` and keeps its toast `BYOK Vault updated [<LABEL>]`.
- Manifest: `android:usesCleartextTraffic="true"` (EndpointRule becomes the only cleartext gate),
  `android:fullBackupContent="@xml/backup_rules"` and `android:dataExtractionRules="@xml/data_extraction_rules"`
  that exclude `sharedpref/neural_deck_vault_prefs.xml` from cloud backup and device transfer. Nothing else
  changes.

## 8. Tests each module brings

- `EndpointRuleTest`, `ProviderProfileTest` (JVM): host table incl. `localhost.evil.com`, IPv6, ports,
  bad URLs; `ready()` agrees with `refusal()`/`keyRequired()` per row; defaults and labels.
- `PrefsProfileStoreTest` (Robolectric): reads prefs written in today's format (including the float
  `temperature`); commit writes one provider only.
- `IdeaTreeTest` (JVM): filters, depth, descendants, orphans, cycles, lineage/children/subtree, edits,
  a 50,000-card chain and fan built in one pass (count map lookups or assert < 1 s without a timeout thread).
- `ReplyParser` via `IdeaEngineTest` (JVM, `ScriptedModelText`, direct executors): every case in §3,
  prompts equal the goldens, checks before calls, temperatures, offline fallback, cancel.
- `SessionFilesTest` (JVM, temp dir): atomic write, `.tmp` leftovers ignored, `.bak`, quarantine,
  today's fixture files load, additive keys round-trip.
- `DeckTest` (JVM: `MemorySessionsStore`, `MemoryProfileStore`, `ScriptedModelText`, `ManualExecutor`,
  `RecordingScreen`): every rule in §6, including D1, D2, D3, D8, D9, D18, deleted-session tickets,
  replies while detached, re-entrant send, save failure and retry, thread confinement.
- The outside-in suites (`StyleLockTest`, `DeckScreenTest`, `AiFlowTest`, `GraveyardSkillsTest`,
  `ByokPromptLabTest`, `DefectTest`) must all pass at the end. `DefectTest` must have failed before.
