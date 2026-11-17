# Design brief — deepening NEURAL//DECK

You are designing **interfaces**, not implementing. Do not edit any file in the repo.

## The app

A small Android app (Java, Android Views, ~3,300 lines, 18 classes, zero tests) for AI brainstorming.
Root: `app\src\main\java\com\example\wtfff\`

| file | role today |
|---|---|
| `MainActivity.java` (659 lines) | UI **and** every deck rule: spark, branch, splice, banish, revive, session switch/create/rename/delete, saving |
| `ai/OpenAiCompatibleClient.java` (522) | threading + config reads + 4 hand-built prompts + raw `HttpURLConnection` + reply parsing |
| `ai/LocalPromptHeuristic.java` | offline prompt rewrite, chosen by `PromptLabSheet` itself |
| `model/BrainstormSession.java` | session data + two copy-pasted tree walks that overwrite `IdeaCard.depth` |
| `model/IdeaCard.java`, `GraveyardEntry.java`, `CreativeSkill.java`, `PromptOptimizationResult.java` | public-field data + org.json (de)serialisation |
| `storage/BrainstormRepository.java` | sessions JSON file, custom skills file, active-session pref, starter session |
| `storage/AiConfig.java`, `KeyVault.java` | provider settings in SharedPreferences; getters follow a global "current provider" pointer |
| `ui/IdeaCardAdapter.java`, `PixelBranchConnectorView.java` | card rows and the branch "rails" |
| `ui/GraveyardSheet.java`, `SkillsSheet.java`, `PromptLabSheet.java`, `ByokSettingsSheet.java` | bottom sheets |

Read all of them before designing. Layouts live in `app/src/main/res/layout/`.

## Vocabulary — use exactly

Architecture terms come from `%USERPROFILE%\.claude\plugins\cache\claude-plugins-official\mattpocock-skills\1.3.1\skills\engineering\codebase-design\SKILL.md` and `DEEPENING.md` in the same folder (read both): **module, interface, implementation, depth, deep, shallow, seam, adapter, leverage, locality**. Never say component, service, API, boundary, layer, wrapper.

Domain terms (from the app's own UI): **Session** (one saved brainstorm), **Deck** (the workspace: all sessions plus the active one), **Idea Card** (a node), **Skill** (a "thinking cartridge"), **Spark** (make root cards from the seed prompt), **Branch** (make children of one card with a skill), **Splice** (fuse 2+ selected cards into hybrids), **Banish** (delete a card's subtree, optionally leaving a graveyard rule), **Graveyard** (banished cards + the anti-pattern rules fed to every prompt), **Revive**, **Purge**, **Seed prompt**, **Prompt Lab**, **BYOK vault**, **Provider**.

## What has been decided

Four deepening moves. Your design must cover all four and show how they fit together:

- **C1 Deck module** — owns sessions, the active session, requests in flight, and saving. `MainActivity` sends what the user did and renders a snapshot. Must survive Activity recreation (rotation).
- **C2 IdeaTree module** — the single owner of tree shape. Derives every visible row (depth, rail geometry inputs, subtree size) in one pass; answers lineage / sibling / subtree questions for everyone else. `IdeaCard.depth` stops being a stored fact that render code overwrites.
- **C3 Idea Engine module** — takes a request (spark, branch, splice, optimise prompt + its context) and returns cards or an error. Behind its seam: a pure prompt composer, a pure reply parser, and a swappable source of model text (HTTP in prod, scripted in tests, the offline heuristic).
- **C4 Provider profile** — provider settings read and saved as a value per provider; the BYOK sheet edits a draft that only SAVE commits; one rule decides "is this a local endpoint / is a key required / is cleartext http allowed".

## Hard constraints

1. **Plain Java.** Source level is 17, but the project is also built by CodeAssist (an on-device, non-Gradle IDE): no records, sealed types, pattern matching, `var`, text blocks, annotation processors or Kotlin. Lambdas are fine. minSdk 26.
2. **No new runtime dependencies.** `androidx.lifecycle.ViewModel` is allowed (already transitive through appcompat). JSON is `org.json` (Android built-in; JVM tests get the `org.json:json` artifact).
3. **Zero visual change.** `res/` is not edited. Every user-visible string (toasts, dialog titles and items, loading-bar messages like `SPARKING_ROOT_NODES // [BADGE]...`, error codes like `SPARK_FAILED`) stays byte-identical. Rows show exactly what they show today. The only intended visual fix is rail geometry in `PixelBranchConnectorView` (same colours, stroke and joint; just connected correctly).
4. **Saved data keeps loading.** `neural_deck_sessions_v1.json`, `neural_deck_custom_skills_v1.json` and every SharedPreferences key must still be read. Changes are additive.
5. **Pure-JVM test surface.** Deck, IdeaTree, the engine's composer and parser, the sessions store and the endpoint rule must import nothing from `android.*` and be testable with JUnit 4 on a plain JVM. Android-touching code stays in thin adapters.
6. **Threading.** The UI thread owns Deck state. The engine works off-thread and hands results back through an injected `java.util.concurrent.Executor` (the main-thread poster); tests inject a direct executor.
7. **Lifecycle.** A reply that arrives while no Activity is attached is still applied and saved; the UI catches up when it re-attaches. Nothing shows a dialog on a destroyed Activity.

## Defects the design must make impossible (each becomes a test)

1. **Reply lands in the wrong session.** `MainActivity.java:261-268, 290-300, 333-346` read `activeSession` when the reply arrives. Switch session mid-request and branch children are appended to the *other* session as parentless root cards, then saved.
2. **Loading bar lies.** Nothing blocks a second request; the client has one thread (`OpenAiCompatibleClient.java:35`), so requests queue and the first reply calls `setLoading(false)` while the second still runs.
3. **Banish loses the subtree.** `MainActivity.java:641-648` removes the whole subtree but archives only the root; `428-433` revives it as a flattened root.
4. **Stale depth.** `BrainstormSession.java:83-139, 188-222`: STARRED and ROOTS views draw whatever `depth` the last ALL render wrote; rails point at parents that are not on screen.
5. **Rails don't connect.** `PixelBranchConnectorView.java:37-90` knows only `(depth, accent)`: no "more siblings below" or "which ancestors continue", depth clamped at 4.
6. **Quadratic rebuilds.** Linear `findCardById` per card in the tree walk; `countSubtree` re-scanned per bound row (`IdeaCardAdapter.java:86`).
7. **Parser.** `OpenAiCompatibleClient.java:395-445`: a prose reply throws before the "freeform" fallback card; `473-504` slices first `{` to last `}`, so a `{` inside `<think>…</think>` breaks parsing.
8. **Prompt context is thin.** `branchFromCard` sends seed + parent only: no ancestors, no existing sibling titles (so branching twice repeats itself). `mashupCards` records only the first selected card as parent.
9. **Config read mid-flight.** The client reads live `AiConfig` on the worker thread; changing provider during a request changes the request.
10. **BYOK sheet saves on tap.** `ByokSettingsSheet.java:58` writes `provider_id` when a chip is tapped, before SAVE.
11. **Two "local endpoint" rules disagree.** `AiConfig.java:81` vs `OpenAiCompatibleClient.java:294`.
12. **Offline Prompt Lab claims graveyard rules it never adds** (`LocalPromptHeuristic.java:46-54`), and `PromptLabSheet.java:77-101` decides the fallback itself.
13. **Persistence.** `BrainstormRepository.java:47-55, 209-214`: non-atomic write; a corrupt file is silently replaced by the starter session.

## Behaviour to preserve exactly

- Spark: new root cards go to index 0 of the target session's card list; filter resets to ALL; list scrolls to top. The seed text is saved to the session before the key check.
- Branch: children are inserted directly after the parent in the card list; parent is un-collapsed; filter resets to ALL.
- Splice: needs 2+ selected; anchor is the first selected card; hybrids are parented under the anchor with badge `SPLICE_X<n>` and accent `0xFFFF2E93`; temperature `min(1.2, t + 0.1)`; selection clears on success.
- Banish: five reasons, `DELETE_ONLY` leaves no graveyard entry; new entries go to index 0; removed cards leave the splice selection.
- Graveyard sheet: mute/enable, revive, purge. Sessions: switch, create (name upper-cased, spaces to `_`), rename, delete (only when more than one).
- Built-in skills, their order, badges, colours and output counts; the starter session.
- `filterMode` (ALL / STARRED / ROOTS), the splice selection and the active skill are UI-facing state today; decide where they live and say why.

## Questions your design must answer

1. The Deck interface: entry points, what a state snapshot contains, how the Activity and each sheet use it (note `GraveyardSheet` mutates `session.graveyard` directly today).
2. Request lifecycle: one in flight or a queue? Cancel? What if the target session is deleted mid-flight?
3. IdeaTree: the row model, filter modes, complexity, lineage and sibling queries, multi-parent splice metadata.
4. Engine: request types, the seam for model text, who decides the offline fallback, how config is captured, error modes.
5. Provider profile: the value, draft/commit, the endpoint rule.
6. Dependency category for each dependency (in-process / local-substitutable / ports & adapters / mock) and the adapters at each seam. One adapter is a hypothetical seam; justify every seam with two.
7. Test plan: which tests cross which interface, with which adapters. No test reaches past an interface.
8. Migration order that keeps the app compiling and behaving at every step.

## Output — write to the file path you are given

1. **Interface**: Java signatures for every public type, plus invariants, ordering constraints and error modes in prose.
2. **Usage**: how `MainActivity` and each sheet call it (short Java).
3. **Hidden implementation**: what sits behind each seam.
4. **Dependency strategy and adapters.**
5. **Trade-offs**: where leverage is high, where it is thin, what you gave up.
6. **Test plan** and **migration order**.

Be concrete and opinionated. Around 1,500–2,500 words plus code.
