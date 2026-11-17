# NEURAL//DECK behaviour test plan

Shared brief for every test-writing agent. Read all of it, then do only your suite.

## Why these tests exist

The app (`com.example.wtfff`, Android Views, Java) is about to be restructured internally: a Deck module
takes over session/request logic from `MainActivity`, an IdeaTree module owns tree shape, an Idea Engine
owns prompts/parsing/HTTP, and a Provider profile owns settings. The user's top concern is that **nothing
visible changes**. These tests pin today's behaviour from the **outside**, so they keep passing after the
restructure:

- **Characterization tests** describe what the ORIGINAL app does. They must PASS against the original code.
- **Defect tests** (one suite only) describe the FIXED behaviour for confirmed bugs. They must FAIL against
  the original code, with an assertion message that names the bug, and pass once the bug is fixed.

## Hard rules

1. **Test only through the outside.** A test may use: views found by id (`R.id.*`), text on screen,
   dialogs, bottom sheets, toasts, the clipboard, started intents, files in `getFilesDir()`,
   SharedPreferences, and HTTP traffic captured by `FakeLlm`. It may reference `MainActivity` and `R` and
   nothing else from `com.example.wtfff` (no `BrainstormSession`, `IdeaCard`, `AiConfig`, adapters, sheets,
   etc.). Those classes are about to be rewritten; a test that touches them would break for the wrong reason.
2. **Exact strings.** Where the UI shows text, assert the exact text. Copy strings from the source code,
   then confirm them by running the test.
3. **No sleeps.** Wait only with `Ui.await(...)`. Hold an AI reply "in flight" with
   `FakeLlm.replyIdeasWhenReleased(...)` / `replyContentWhenReleased(...)` and release with `countDown()`.
4. **Deterministic.** No dependence on test order, wall-clock, random ports in asserted text, or network
   access other than `FakeLlm` on 127.0.0.1.
5. **Do not edit app code** under `app/src/main`. Do not edit `StyleLockTest.java`, the snapshots, or the
   other suites' files. Do not edit the shared `testkit/*.java`; put helpers you need as private static
   methods in your own test class. If a testkit class has a real bug that blocks you, fix it minimally and
   describe the exact change in your final report.
6. **Java only**, JUnit 4, Robolectric. Use the same class annotations as `StyleLockTest`:
   `@RunWith(RobolectricTestRunner.class)`, `@Config(sdk = 36, qualifiers = "w411dp-h914dp-420dpi")`,
   `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. Close any `FakeLlm` in `@After`.
7. One test = one behaviour, named for it (`sparkInsertsNewCardsAtTopInReplyOrder`). Short assertion
   messages that say what the user would see.

## Working setup

**Working in the repo (Antigravity, one agent at a time):** skip the scratch copies and the lock script below.
Write the suites directly in `app/src/test/java/com/example/wtfff/` and run them with
`tools\gw.cmd :app:testDebugUnitTest --tests com.example.wtfff.<YourClass>`. `DefectTest` is left out of normal
runs by `app/build.gradle.kts`; run it with `-Pdefects=true --continue --tests com.example.wtfff.DefectTest`.
Record a suite's new text goldens with `"-Pstylelock.record=true" --tests com.example.wtfff.<YourClass>` (that class
only, so no screenshot is re-recorded; keep the double quotes, since PowerShell splits the flag at the dot otherwise),
then run again without the flag. Golden files are named `Golden.text("<suite>/<name>.txt", actual)`. Never touch
`app/src/main` or the snapshots, except for the temporary "not vacuous" check in the `continue-deepening` skill.
Wherever this plan speaks to "every test-writing agent", "your suite" or "your final report": in the repo one agent
writes all five suites, one at a time, and records any testkit change or correction to this plan in
`docs/HANDOFF.md` instead of a final report.

The rest of this section describes the parallel set-up the Claude session used (several agents, one Gradle build at a time):

- Make your own copy of the baseline and work only there:
  `SC=%USERPROFILE%/AppData/Local/Temp/claude/53d57e4524e68b2d/b525cabb-5227-46cc-8972-224249ce20df/scratchpad`
  `rm -rf $SC/b_<suite> && cp -r $SC/b_base $SC/b_<suite>`
- Run Gradle **only** through the lock script (this PC can run one build at a time; others may be waiting):
  `bash $SC/tools/gradle-run.sh $SC/b_<suite> :app:testDebugUnitTest --tests com.example.wtfff.<YourClass> --console=plain`
  First build in a fresh copy takes ~3 minutes. Results: `app/build/test-results/testDebugUnitTest/TEST-*.xml`.
  Keep each run short: run only your class, and batch several fixes per run.
- Golden text files: `Golden.text("<suite>/<name>.txt", actual)`. Record them by adding
  `-Pstylelock.record=true` to the Gradle command (that also re-records screenshots, but your class takes
  none, so only your goldens change). Then run again without it to confirm they compare equal.
- Source to read first: `app/src/main/java/com/example/wtfff/**` (18 files), `app/src/main/res/layout/*.xml`,
  and the testkit: `app/src/test/java/com/example/wtfff/testkit/{Ui,FakeLlm,Seed,Snap,Golden}.java`.

## Testkit cheat sheet

- `Ui.launch()` / `Ui.controller()` (for `recreate()`, `pause()`, `stop()`), `Ui.idle()`, `Ui.await(what, cond)`
- `Ui.click(view)`, `Ui.click(activity, R.id.x)`, `Ui.text(root, id)`, `Ui.shown(activity, id)`
- Rows: `Ui.rowCount(a)`, `Ui.row(a, i)` (scrolls it on screen), `Ui.rowTitles(a)`, `Ui.rowOf(a, title)`,
  `Ui.rowNamed(a, title)`, `Ui.rowIndentDp(a, i)` (0 for a root, `depth*18+6` otherwise, capped at depth 4),
  `Ui.treeShape(a)` → `["Title@indent", ...]`
- Dialogs: `Ui.dialog()`, `Ui.dialogRoot()`, `Ui.alert()`, `Ui.alertTitle()`, `Ui.alertMessage()`,
  `Ui.alertItems()`, `Ui.clickAlertItem(i)`, `Ui.clickAlertButton(DialogInterface.BUTTON_POSITIVE)`,
  `Ui.showingAlertTitle()` (safe inside `await`), `Ui.dialogShowing()`, `Ui.findText(root, text)`,
  `Ui.clickText(root, text)`, `Ui.allTexts(root)`, `Ui.latestToast()`
- `FakeLlm`: `new FakeLlm().install(context)` saves it as the CUSTOM provider (no key; local, so the app
  treats it as configured). `replyIdeas(titles...)`, `replyContent(text)`, `replyHttpError(status, msg)`,
  gated variants; read back `requestCount()`, `systemPrompt(i)`, `userPrompt(i)`, `temperature(i)`,
  `model(i)`, `authorization(i)`, `request(i)` (full JSON body). Unqueued requests get HTTP 500
  "FakeLlm: no reply queued".
- `Seed.sessions(context, activeId, Seed.session(id, title, seed, Seed.card(id, parentId, title, badge, accent)...))`
  writes saved data in the shipped format BEFORE launch. `Seed.savedSessions(context)` reads the file back.
- Context: `RuntimeEnvironment.getApplication()`. Saved files live in its `getFilesDir()`:
  `neural_deck_sessions_v1.json`, `neural_deck_custom_skills_v1.json`. Prefs:
  `neural_deck_ai_config` (`provider_id`, `base_url_<id>`, `model_<id>`, `temperature` (float),
  `global_context`), `neural_deck_repo_prefs` (`active_session_id`), `neural_deck_vault_prefs` (keys).
- Robolectric: `ShadowToast`, `shadowOf(activity).getNextStartedActivity()` (export uses
  `Intent.createChooser`, so the real intent is in `EXTRA_INTENT`), `ClipboardManager` from the context.

## The starter deck (what a fresh install shows)

Session `AMBIENT_FOCUS_OS`, seed "Design a mobile tool that helps deep thinkers enter flow state without
gamified streak guilt.", cards in list order: "Acoustic Bio-Rhythm Cockpit" (root, starred, SEED) >
"Zero-UI Lockscreen Cartridge" (SCAMPER) and "Multiplayer Ghost Co-Working" (ANALOGY); "Context-Snapshot
Parking Lot" (root, 1ST_PRINC). One graveyard entry (TOO_CLICHE, rule "Never suggest punitive daily streaks,
XP leaderboards, or guilt-inducing push notifications.", archived card "Daily Flame Streak Leaderboard").
Active skill on launch: the first built-in, badge `10X_DIVERGE`. HUD shows `SET_KEY` until a provider is
configured.

---

## Suite 1 — `DeckScreenTest` (characterization)

HUD and deck screen, sessions, persistence, copy/export.

- HUD: session title text is `<TITLE> ▾`; graveyard count; `SET_KEY` in amber (`0xFFFFB800`) when not
  configured, provider label (e.g. `CUSTOM_URL` with FakeLlm installed) in green (`0xFF39FF88`);
  skill label `SKILL: 10X_DIVERGE`.
- Filters: ALL shows the tree in pre-order with indents (use `treeShape`); STARRED shows only starred cards
  in card-list order; ROOTS shows only root cards. Tab styling is visual (style lock covers it), but you can
  assert the selected tab's text colour `0xFF00F0FF` vs idle `0xFF8DA4C7`.
- Star toggle: star a card, STARRED now includes it; unstar removes it; state survives a relaunch
  (new activity over the same files).
- Collapse: the collapse chip shows `[-N]` (N = all descendants) and becomes `[+N]` when collapsed; the
  children disappear from ALL; survives relaunch. A leaf shows no collapse chip.
- Splice selection: `[+SPLICE]` → `[✓ SPLICED]`; mashup bar visible with `1 NODE ARMED (PICK 2+)`, then
  `2 NODES ARMED FOR SPLICE`; CANCEL hides it and clears; executing with fewer than 2 shows the toast
  `Tap [+SPLICE] on at least 2 idea cards to fuse them.` Selection is NOT persisted across relaunch.
- Copy card: clipboard text `[<badge>] <title>\n<summary>\nWhy: <rationale>` (no "Why" line when empty) and
  toast `Copied node to clipboard`.
- Export: share intent (ACTION_SEND, text/plain) with subject `<TITLE> // Brainstorm Tree` and text = the
  Markdown export; clipboard holds the same text. Pin the starter deck's export with a golden text file.
- Spark guards: empty dock → toast `Enter a seed idea or prompt in the bottom terminal.`; no key →
  the typed seed is saved to the session file first, then toast `Configure your BYOK API key first.` and
  the BYOK sheet opens.
- Sessions dialog: title `SELECT_DECK_SESSION`; items `● <TITLE> (<n> nodes)` for the active one and
  `  <TITLE> (<n> nodes)` for others, then `+ NEW_BLANK_SESSION`, `✎ RENAME_CURRENT_SESSION`, and
  `✕ DELETE_CURRENT_SESSION` only when more than one session exists.
  - New: dialog `NEW_BRAINSTORM_SESSION`; name upper-cased with spaces → `_`; empty name →
    `SESSION_0<n+1>`; new session goes first in the list, becomes active, dock cleared, empty state shown.
  - Rename: dialog `RENAME_SESSION` prefilled; saved name normalized; empty name ignored.
  - Switch: dock shows that session's saved seed; selection cleared; active id saved
    (`active_session_id`) so a relaunch opens it.
  - Delete: removes the active session and switches to the first remaining one.
- Persistence: everything above that the original saves is in `neural_deck_sessions_v1.json` (check
  parsed structure, not bytes).

## Suite 2 — `AiFlowTest` (characterization, uses FakeLlm)

- Spark: loading bar visible with `SPARKING_ROOT_NODES // [10X_DIVERGE]...` while in flight; new cards go
  to the top in reply order; filter resets to ALL (start from STARRED to prove it); bar hidden after.
  Request: model `fake-model`, temperature 0.85 (compare with tolerance 1e-4), no Authorization header with
  no key. Pin the exact system prompt and user prompt with golden text files
  (`aiflow/spark_system.txt`, `aiflow/spark_user.txt`) — these must stay byte-identical.
- Card fields from the reply: badge = active skill badge, tags shown as `#TAG   #TAG2` upper-cased with
  spaces → `_`, rationale shown as `// WHY: ...`, missing title → `NODE_<i>`, `description`/`why` accepted as
  aliases, top-level array accepted, keys `cards`/`branches` accepted, fenced ```json blocks accepted.
  A JSON object with no ideas array yields one card `AI Synthesis Output` whose summary is the raw text.
- Branch (quick `BRANCH: <badge>` button): loading text `BRANCHING [10X_DIVERGE] -> <parent title>`;
  children inserted directly after the parent (before the parent's older children); a collapsed parent is
  expanded; filter resets to ALL. Pin the branch **system** prompt with a golden. For the branch **user**
  prompt assert only these parts (the restructure adds lineage lines, so do not pin the whole text):
  starts with `Session Root Context: <seed>\n\n`, contains
  `Target Parent Card to Branch From:\n- Title: <t>\n- Summary: <s>\n- Rationale: <r>\n`, ends with
  `\nApply the skill [Diverge 10x] and return JSON with 3 child branch cards.`
- Branch via `[SKILLS ▾]`: sheet title `BRANCH_NODE // PICK_SKILL`, picking `SCAMPER Matrix` makes the
  active skill label `SKILL: SCAMPER` and branches with the SCAMPER instruction (system prompt contains it)
  and badge `SCAMPER`.
- Splice: select 2 cards, execute: loading `SPLICING_2_NODES_INTO_HYBRIDS...`; hybrids (badge
  `SPLICE_X2`) inserted right after the first-selected card; selection cleared and bar hidden; temperature
  0.95 (= 0.85 + 0.1); pin system and user prompts with goldens.
- Errors: HTTP 500 `{"error":{"message":"boom"}}` → alert `SPARK_FAILED` with message `HTTP 500: boom` and
  buttons `BYOK_SETTINGS` (opens the BYOK sheet) and `DISMISS`; also `BRANCH_FAILED`, `SPLICE_FAILED`.
  A non-JSON error body gives `HTTP <code> — <first 160 chars>`. Bar hidden after an error.
- Keys: after saving a key through the BYOK sheet (CUSTOM provider, FakeLlm URL), requests carry
  `Bearer <key>`.
- Global context (saved via the BYOK sheet) appears in the system prompt as
  `\n[USER PERSONA / GLOBAL CONTEXT]\n<ctx>\n`.
- Graveyard rules in prompts: the starter rule appears as `- [TOO_CLICHE] <rule>` under
  `[FORBIDDEN ANTI-PATTERNS // IDEA GRAVEYARD]`; a muted rule does not appear.

## Suite 3 — `GraveyardSkillsTest` (characterization)

- Banish dialog: title `BANISH_TO_GRAVEYARD`, the five items in order (`TOO_CLICHE — Generic or overdone
  trope`, ...), custom-note hint `Optional custom anti-pattern rule...`, button `CANCEL`.
- Banish with a reason: the card and its whole subtree disappear; graveyard count +1; toast
  `Banished! AI will avoid [<title>]`; saved entry has the reason tag and the auto rule
  `Avoid ideas resembling "<title>" (<TAG>): <summary, cut to 107 chars + "..." when over 110>`; a typed
  custom note becomes the rule instead; banished cards leave the splice selection.
- `SILENT_DELETE — Remove without Graveyard rule`: card and subtree removed, no entry, count unchanged.
- Graveyard sheet: entries newest first; `[TAG]`, `  ● GUARDRAIL_ON` / `  ○ MUTED`, archived title, rule,
  buttons `MUTE_RULE`/`ENABLE_RULE`, `REVIVE_NODE` (only with an archived card), `PURGE`. Mute toggles the
  state text and is saved; purge removes the entry and is saved; empty graveyard shows the empty text.
  Revive: the archived card returns at the top of the deck as a root, toast `Node revived to workspace.`,
  entry removed. (Do NOT assert anything about the banished card's children here: today they are lost, and
  that is being fixed — the defect suite covers it.)
- Skills sheet (`SKILL:` chip): title `CREATIVE_SKILLS // CARTRIDGES` (confirm in the layout), the 8
  built-ins in order with `[BADGE]`, `  <name>`, subtitle; selecting one updates `SKILL: <badge>` and the
  quick-branch label `BRANCH: <badge>` on cards.
- Forge custom skill: dialog `FORGE_CUSTOM_SKILL`; empty name or instruction → toast
  `Name and instruction required.`; empty badge → first 8 chars of the name upper-cased, spaces → `_`;
  subtitle = instruction (cut to 67 + "..." over 70); appears last with a `DEL` button; saved in
  `neural_deck_custom_skills_v1.json`; survives relaunch; branching with it sends its instruction, badge
  and 3 cards. `DEL` removes it; deleting the active custom skill falls back to `SKILL: 10X_DIVERGE`.

## Suite 4 — `ByokPromptLabTest` (characterization)

- BYOK sheet opens from the HUD key chip. Fresh install: OPENROUTER chip active (bg/text colour), base URL
  `https://openrouter.ai/api/v1`, model `openai/gpt-4o-mini`, temperature label `0.85`, empty key and context.
  Tapping each chip fills that provider's saved-or-default URL/model/key (OPENAI `https://api.openai.com/v1`
  / `gpt-4o-mini`; GROQ `https://api.groq.com/openai/v1` / `llama-3.3-70b-versatile`; DEEPSEEK
  `https://api.deepseek.com/v1` / `deepseek-chat`; CUSTOM defaults to the OpenAI values). Do NOT assert what
  is saved when a chip is tapped without SAVE (being fixed).
- SAVE (`LOCK_INTO_VAULT`, confirm id `btnSaveByok`): saves provider, URL (empty → default), model
  (empty → default), key, temperature (slider/100), context; toast `BYOK Vault updated [<LABEL>]`; HUD
  updates; sheet closes. Keys are per provider (switching chips shows each provider's own key).
- Temperature label follows the slider (`String.format(Locale.US, "%.2f", p/100f)`).
- PING with FakeLlm (fields as typed, not saved): `PINGING_ENDPOINT // <url> ...` while in flight, then
  `ONLINE // <model> -> <reply trimmed, cut to 90 + "...">`; HTTP error → `LINK_ERROR // HTTP 401: <msg>`;
  empty URL → `LINK_ERROR // Missing Base URL. Configure BYOK endpoint first.`; non-local URL with empty key →
  `LINK_ERROR // Missing API Key. Tap [BYOK_KEY] in the top bar to add your key.`; the ping request's
  system prompt is `You are a concise diagnostic probe. Reply with exactly: LINK_ESTABLISHED`, user
  `Ping neural link.`, temperature 0.2.
- Prompt Lab (`PROMPT_LAB` dock button): raw field prefilled with the dock text; empty raw → toast
  `Enter a rough idea seed first.`; four mode chips (CO-STAR default, 10X_DIVERGENT, SOCRATIC,
  CONSTRAINT_BOX).
  - Offline (no provider configured): result appears at once with the local heuristic text for each mode
    (pin all four optimized texts with goldens), `CLARITY: 87%`, and toast
    `Upgraded via local heuristic (Add BYOK key for live LLM rewrite)`. For the upgrades list assert the two
    mode-specific lines; do NOT assert the graveyard line (being fixed).
  - Online (FakeLlm): button shows `REWRITING_VIA_NEURAL_LINK...` while in flight; request temperature 0.65,
    user `Rough Input Prompt:\n<raw>`, pin the system prompt with a golden for each of the four modes (CO-STAR,
    10X_DIVERGENT, SOCRATIC, CONSTRAINT_BOX: each appends its own framework text, and FINAL-DESIGN relies on these
    goldens to keep the optimise prompts byte-identical); a JSON reply renders
    `optimized_prompt`, `CLARITY: <score>%`, and `+ <upgrade>` lines; a non-JSON reply renders the raw text,
    `CLARITY: 88%`, `+ Structured with <MODE> lens`; an HTTP error renders the local heuristic and toasts
    `API fallback used: <message>`.
  - `LOAD_TO_DOCK` (confirm id `btnApplyPromptOnly`) puts the text in the dock and saves it as the seed;
    `USE_&_SPARK` (`btnApplyAndSpark`) puts it in the dock and sparks with it.

## Suite 5 — `DefectTest` (must FAIL on the original, pass when fixed)

Each test names its defect in the method name and in its first assertion message, e.g.
`assertEquals("D1: branch reply must land in the session it was asked from", ...)`. Before finishing,
run the suite on the original and confirm **every** test fails on an assertion (not on a crash in your own
test code, and not on a timeout unless the bug itself is a hang). The fixed behaviour below is decided:
encode exactly this.

- **D1 reply lands in the session it was asked from.** Two seeded sessions A (active; cards P > older
  child) and B (one card). Branch P with a held reply; switch to B; release. Wait until the saved file shows
  the new children in A. Assert: A's file has 3 new children of P inserted right after P; B's file and B's
  rows are unchanged; B's filter (set STARRED before releasing) is still STARRED; switching back to A shows
  P followed by the new children one level deeper.
- **D2 loading bar is truthful with queued requests.** Tap SPARK twice with both replies held. Wait for the
  first request; release it; wait for its 2 cards. Assert the bar is STILL visible (the second request is
  pending). Wait for the second request, release it, wait for its cards; assert the bar is hidden and both
  replies' cards are present. Requests run one at a time in tap order (the second is sent after the
  first finishes).
- **D3 revive restores the whole branch.** Banish the starter root "Acoustic Bio-Rhythm Cockpit" (2
  children) with TOO_CLICHE, then REVIVE_NODE it: the root is back at the top with both children directly
  under it, indented, in their original order; the saved file keeps their parent ids.
- **D4 prose reply becomes a card.** Reply content "Here are some thoughts without any JSON." → no
  `SPARK_FAILED`; one new top card `AI Synthesis Output` with that summary.
- **D5 a brace inside `<think>` does not break parsing.** Reply
  `<think>Plan: {"draft": true}</think>\n` + ideas JSON with 2 titles → those 2 cards appear.
- **D6 a brace in a prose preamble does not break parsing.** Reply `Sure {as requested}:\n` + ideas JSON →
  the cards appear.
- **D7 branch prompt carries lineage and existing children.** Seed ROOT > CHILD > GRAND, and GRAND already
  has a child `Existing Kid`. Branch GRAND. The user prompt contains `ROOT`'s title, `CHILD`'s title and
  `Existing Kid`. (Wording is the implementer's choice; assert presence only.)
- **D8 splice remembers every parent.** Select two cards X then Y, splice. Every hybrid in the saved file has
  `"splicedFrom": [X.id, Y.id]` (JSON array of card ids in selection order).
- **D9 a queued request keeps the settings it was tapped with.** Spark twice (first held). Open the BYOK
  sheet, change the model to `changed-model`, SAVE. Release the first. Assert the second request's model is
  `fake-model`.
- **D10 tapping a provider chip does not save it.** With FakeLlm installed (CUSTOM), open BYOK, tap the
  GROQ chip, dismiss the sheet without SAVE. Assert pref `provider_id` is still `custom`, the HUD still shows
  `CUSTOM_URL`, and a spark reaches FakeLlm.
- **D11 a local endpoint needs no key on any provider.** In the BYOK sheet pick OPENAI, set the base URL to
  FakeLlm's URL, empty key, SAVE. Assert HUD shows `OPENAI` (not `SET_KEY`) and a spark reaches FakeLlm.
- **D12 a private-network endpoint needs no key.** Same with base URL `http://192.168.1.50:11434/v1`:
  HUD shows `OPENAI`.
- **D13 plain http to a public host is refused before sending.** CUSTOM provider, base URL
  `http://example.invalid/v1`, key `sk-test`, SAVE, SPARK → alert `SPARK_FAILED` whose message is exactly
  `Cleartext HTTP traffic to example.invalid not permitted`.
- **D14 offline Prompt Lab does not claim graveyard rules it did not add.** Fresh install (1 active rule),
  Prompt Lab offline run: no upgrade line mentions `Graveyard`.
- **D15 a corrupt sessions file is kept, not overwritten.** Write `{not json` to
  `neural_deck_sessions_v1.json` before launch. The app shows the starter deck, and `getFilesDir()` holds a
  file named `neural_deck_sessions_v1.corrupt-<digits>.json` containing `{not json`.
- **D16 a corrupt sessions file falls back to the last good copy.** Write a valid seeded sessions array to
  `neural_deck_sessions_v1.json.bak` and `{not json` to the live file. The app shows the `.bak` session.
- **D17 splice never runs cooler than the user's setting.** Save temperature 1.50 (slider 150), splice two
  cards: request temperature 1.5 (today 1.2).
- **D18 unsparked dock text is kept per session.** Type `typed but not sparked` in the dock, switch to a new
  session, switch back: the dock shows `typed but not sparked`.
- **D19 a reply that arrives during rotation reaches the new screen.** Spark with a held reply,
  `controller.recreate()`, release: the recreated activity shows the new cards, and the saved file has them.

## Final report (your last message)

Plain text, at most 200 words: test class path, test count, result on the original (all pass, or for
DefectTest all fail with the reason per test), golden files recorded, any testkit change made (exact diff),
and anything in the spec you found to be wrong about the original (with file:line).
