# Handoff: NEURAL//DECK, from Claude Code to Google Antigravity

Written 2026-11-17, when work on this project moved from Claude Code (run inside T3 Code) to Google Antigravity.
Agents: the rules are in [AGENTS.md](../AGENTS.md). This file is the state of the work.

**Open this folder itself (`Neural_Deck-main`, the one that holds `.git` and `AGENTS.md`) as the Antigravity
workspace.** Antigravity never looks in subfolders of the workspace, so opening the parent `REPOMAN` folder, the
`Neural_Deck-main.zip` or the separate `Neural_Deck_Forkz` checkout loads none of these rules. Then open
Customizations and check that `style-fidelity` is listed under Rules and `build-and-test`, `style-lock`,
`continue-deepening` and `sync-from-claude` under Skills. In the IDE it is the `…` menu at the top of the agent side
panel; in the App, the application menu or the project's settings (add `Neural_Deck-main` itself as the project
folder); in the CLI, `/skills`.

**This machine's Antigravity App runs the Turbo preset** (terminal sandbox off, commands run without asking; seen in
its settings on 2026-11-17). So nothing in the tool pauses before a commit, a push or a screenshot re-record: the
"stop and ask" rules in AGENTS.md hold only because the agent follows them. The user may prefer a per-project
override for this repo (Default or Request Review, or ask/deny rules for `git commit`, `git push` and `git config`);
that is their setting to choose.

## In one minute

- The app code in `app/src/main` is still **exactly the original**. Nothing has been refactored yet.
- Added around it: a Gradle build, `tools\gw.cmd` (runs Gradle with Android Studio's JDK), a Robolectric test kit, and
  **28 reference screenshots of the original app**. Every future change is checked against them pixel for pixel.
- **Verified 2026-11-17, from this folder, with `JAVA_HOME` unset:** `tools\gw.cmd assembleDebug :app:testDebugUnitTest`
  gave `BUILD SUCCESSFUL`, **28 of 28 tests passed**, and `app\build\outputs\apk\debug\app-debug.apk` (6.9 MB) was built.
  Re-verified the same day from a full `clean` (`clean assembleDebug :app:testDebugUnitTest`, 1 min 17 s, 28 of 28),
  after `tools\gw.cmd` learned to clear the read-only flag that Google Drive keeps setting (see Known caveats).
- **The screenshot lock is proven sensitive:** shifting the app's cyan by 1/255, invisible to the eye, made 21 of the
  28 screenshot tests fail ([proof](claude-session/proof/)). The references are stable: they matched on two separate
  runs in the Claude working copy and again from this folder.
- The design is written but **not yet reviewed by the user**: [FINAL-DESIGN.md](architecture/FINAL-DESIGN.md), merged
  from four candidate designs and two judges. Build order: the pure modules first (provider, tree, engine, storage), then the Deck module and the UI.
- **Baseline commit: done** (2026-11-17), on top of GitHub's `main`, and pushed. Next: the behaviour tests, then
  Phase A of the final design.

## Status

| Item | Status |
|---|---|
| Gradle build: AGP 9.5.1, Gradle 9.9.1, compileSdk/targetSdk 36, Java 17 source and target (runs on Android Studio's bundled JDK 25) | Done, verified |
| `tools\gw.cmd` launcher | Done, verified |
| Test kit: `Ui`, `Snap`, `Golden`, `FakeLlm`, `Seed` | Done |
| 28 reference screenshots (`StyleLockTest`) | Done, verified, proven sensitive |
| `DefectTest` switch in `app/build.gradle.kts` (left out of normal runs; `-Pdefects=true` runs it) | Done, verified with a temporary failing probe test (excluded normally, ran and failed with the switch; probe deleted) |
| Behaviour test plan: [TESTPLAN.md](claude-session/scratchpad/TESTPLAN.md), five suites | Written. Corrected 2026-11-17 in its "Working in the repo" note and Suite 4: the recording flag is quoted for PowerShell, one agent writes all five suites and reports in this file, and the online optimise prompt is pinned for all four modes (FINAL-DESIGN relies on those goldens) |
| Behaviour tests themselves | **Not written.** The Claude session launched five test writers at 03:47; its usage limit stopped them before they produced any file. When that session resumed (04:30) it checked: nothing had been written, the scratch copies held only the baseline, and it deleted them. It was about to relaunch the writers when the user asked it to hand off instead, so this is the next task (TESTPLAN now has a "Working in the repo" note at the top of *Working setup*) |
| Final design: [FINAL-DESIGN.md](architecture/FINAL-DESIGN.md) | Written 03:54, minutes before the Claude session stopped. Not yet reviewed by the user |
| Phase A refactor (the look, layout and existing strings stay; one screen changes, and behaviour changes where `DefectTest` pins a bug; both need the user's approval, below) | Not started |
| Phase B (connected rails, flat STARRED view, dialog styling, new screens and strings, product ideas) | Previews only, each needs the user's approval |
| Baseline git commit | **Done 2026-11-17, and pushed.** Branch `main`, tracking `origin` = https://github.com/TheHizzleGizzle/Neural_Deck (a **public** repo). The baseline is the first commit after GitHub's `5c58dcb` ("YAY"): it adds the Gradle build, the test kit, the 28 reference screenshots and these docs, and leaves the app code exactly as it is on GitHub. Author: `The Hizzle Gizzle`, the same name and email as the existing GitHub commit, set for this repo only (the user's choice). `docs/claude-session/` is committed, with the Windows account name in one path replaced by `<user>` (the user's choice) |
| Late sync from the Claude session | **Not needed.** The session resumed at 04:30 and stopped at the user's request at about 04:35. A dry run of the sync still lists two files: `app\build.gradle.kts` (byte-identical, only its timestamp is newer) and one Claude memory note, whose content is already in this file. Run the sync only if the user says Claude worked on the project after 2026-11-17 04:35 |

## How we got here

1. **First-pass review** (2026-11-16): an Antigravity (Gemini) agent read every file and listed bugs and gaps.
   Each finding was later re-checked against the source: section 06 of the architecture review.
2. **Architecture review** (Claude):
   [architecture-review-20261116-1630.html](architecture/architecture-review-20261116-1630.html). Open it in a browser.
   It covers what the app is missing, three problems, four deepening refactors (C1 to C4) and three product ideas.
3. **Approval.** The user approved the plan ("I trust your judgement") with one condition: keep the art style
   extremely close to the original, and show any style change or new theme before making it permanent.
   Hand-drawn mockups in the review had drifted in colour and small details, and the user noticed.
4. **Build and test kit.** A Gradle build and a Robolectric harness, so the real screens render on this PC.
5. **Design panel.** Four designers, each with a different constraint, designed the same modules from
   [BRIEF.md](architecture/design-panel/BRIEF.md). Migration risk picked `common-caller` (7.5/10), defect prevention
   picked `minimal` (8/10), and the third judge was cut off by a usage limit ([verdicts.md](architecture/design-panel/verdicts.md)).
6. **Reference screenshots** (2026-11-17): 28 screens recorded from the original code, checked for stability, and
   proven sensitive with the 1/255 cyan shift.
7. **Final design.** `minimal` as the base with the other designs' best mechanisms and both judges' fixes:
   [FINAL-DESIGN.md](architecture/FINAL-DESIGN.md). It reorders the work so the Deck module, which depends on the other
   three, comes last.
8. **Move to Antigravity**: `AGENTS.md`, this file, `GLOSSARY.md`, `.agents/` and `tools/` were added. An independent
   reviewer then tried to pick the project up from the repo alone; its findings were fixed.

## Decisions, and why

| Decision | Why |
|---|---|
| Prove the look with real renders, not drawn mockups | Drawn mockups drifted; the user cares most about the art style |
| Render with Robolectric on the JVM | Fast and deterministic; T3's device access was off, so the emulator was never used |
| Plain Java, no new runtime libraries, keep `app/module.toml` | The app must keep building in CodeAssist on the phone |
| New modules import nothing from `android.*` | Their tests run on a plain JVM |
| Pure modules first, Deck module last | The Deck depends on the other three; building it last avoids writing it twice (FINAL-DESIGN, "Order of work") |
| No ViewModel | It dies with a finished Activity while a reply is in flight (FINAL-DESIGN, section 6) |
| `DefectTest` left out of normal runs | It fails by design until the refactor fixes the bugs it names; "green" must stay reachable |
| Gradle build cache off in this repo | The Claude session turned it on to speed up its many throwaway copies; here it only added a failure mode (Windows file locks while restoring from the cache) |
| Product ideas and other visible changes are Phase B | The user wants to see new UI before it is built |
| Baseline commit on top of GitHub's `main`, not a new unrelated history | This folder was a ZIP download of the public GitHub repo (hence the name `Neural_Deck-main`), and its app code matched `5c58dcb` byte for byte. Building on that commit keeps one history, and a plain push (no force) updates GitHub |

## What the reference screenshots cover

`main_all`, `main_starred`, `main_roots`, `main_splice_one`, `main_splice_two`, `main_collapsed`, `main_star_toggled`,
`main_empty`, `main_loading`, `main_after_spark`, `tree_deep`, `tree_deep_starred`, `sheet_skills`,
`sheet_skills_branch`, `sheet_graveyard`, `sheet_graveyard_muted`, `sheet_graveyard_empty`, `sheet_byok`,
`sheet_byok_ping_online`, `sheet_prompt_lab`, `sheet_prompt_lab_offline`, `dialog_banish`, `dialog_sessions`,
`dialog_sessions_delete`, `dialog_new_session`, `dialog_rename_session`, `dialog_forge_skill`, `dialog_spark_failed`.

All render on a 411 x 914 dp, 420 dpi screen with Android SDK 36. Screens and sheets are 1078 x 2399 px; the
deep-tree screens use a 2600 dp tall screen (1078 x 6825 px); dialogs are cropped to the dialog window, so their
sizes vary. The images are in `app/src/test/snapshots/`. Open a few to see the original look.

The test method names differ from the screen names (re-recording needs the method). Most are the camelCase form;
the exceptions are `main_splice_one` = `mainSpliceOneArmed`, `main_splice_two` = `mainSpliceTwoArmed`,
`main_empty` = `mainEmptyDeck`, `main_loading` = `mainLoadingBar`, `sheet_skills_branch` = `sheetSkillsForBranch`,
`sheet_prompt_lab_offline` = `sheetPromptLabOfflineResult`, `dialog_sessions_delete` = `dialogSessionsWithDelete`.
When in doubt, find `Snap...("<name>"` in `app/src/test/java/com/example/wtfff/StyleLockTest.java`.

## Next steps, in order

0. **Baseline commit: done** (see Status). From here on, every change is a normal commit on `main`. Commit and push
   only with the user's OK (AGENTS.md rule 3). The GitHub repo is **public**: before any push, check that the new
   commits hold no keys, tokens or personal data. This repo has `core.filemode=false`, so a new executable script
   needs `git add --chmod=+x <file>` (`gradlew` is already committed as `100755`).
1. **Late sync: not needed** (see Status). Run the `sync-from-claude` skill only if the user says Claude worked on the
   project after 2026-11-17 04:35.
2. **Behaviour tests.** Write the five suites in [TESTPLAN.md](claude-session/scratchpad/TESTPLAN.md). Follow its hard
   rules, test-kit cheat sheet and suite specs. In its "Working setup", follow the **"Working in the repo"** note at
   the top and the `Golden.text` naming convention; skip the rest of that section (the Claude session's scratch copies
   and lock script). Work in this repo and run one suite with
   `tools\gw.cmd :app:testDebugUnitTest --tests com.example.wtfff.<Suite> --console=plain`.
   `DeckScreenTest`, `AiFlowTest`, `GraveyardSkillsTest` and `ByokPromptLabTest` must pass on the original code.
   `DefectTest` must fail on it, each test naming its bug; run it with `-Pdefects=true`.
   Then prove each of the four passing suites is not vacuous (see the `continue-deepening` skill, step 1).
3. **Phase A** of FINAL-DESIGN: provider, tree, engine and storage modules with their own tests, then the Deck module
   and the UI cut-over, each step fully green. At the end every suite passes, `DefectTest` included.
   **Before starting, ask the user once to approve the fixed behaviours** that `DefectTest` pins (D1 to D19,
   TESTPLAN Suite 5), plus the two manifest changes in FINAL-DESIGN §7. Many fixes change what the user sees in
   some situation; TESTPLAN states each one's expected screen. The clearest: D4 shows an `AI Synthesis Output` card
   instead of the `SPARK_FAILED` alert for a prose reply; D11 and D12 show `OPENAI` instead of `SET_KEY` in the HUD
   for a local or private-network endpoint with no key; D13 refuses plain http to a public host with a
   `SPARK_FAILED` alert (`Cleartext HTTP traffic to … not permitted`). Also visible: D1 (a reply lands in the session
   it was asked from), D2 (the loading bar stays on while requests are queued), D3 (revive brings back the whole
   branch), D10 (tapping a provider chip no longer saves it), D16 (a corrupt sessions file falls back to the last good
   copy), D18 (unsparked dock text is kept per session), D19 (a reply that arrives during rotation reaches the new
   screen). The manifest changes: `usesCleartextTraffic="true"` (so local http endpoints such as Ollama work, with
   the app's own endpoint rule as the only gate) and backup rules that keep the stored API keys out of cloud backup
   and device transfer. Tests on the JVM cannot see either manifest change, so offer to check them on the emulator.
   Show the visible fixes concretely, then note the approval here.
   The one look change allowed in Phase A: `sheet_prompt_lab_offline` loses the untrue line
   `+ Bound 1 Graveyard anti-pattern constraint(s)` (defect D14). Show before / after / diff and re-record that one
   screen only after the user approves, with
   `tools\gw.cmd :app:testDebugUnitTest "-Pstylelock.record=true" --tests "com.example.wtfff.StyleLockTest.sheetPromptLabOfflineResult"`.
   Every other screenshot must stay pixel-identical.
4. **Phase B**, one proposal at a time, each shown as before / after images first: connected rails in the original
   colours, a flat STARRED view, then anything else the user picks.

## Known caveats

- **Robolectric is not a phone.** Sheets and dialogs render without the dimmed backdrop or the status bar. That is fine
  for comparing styles, but it is not a device screenshot.
- **Local `http://` works in tests but not on a phone.** The JVM's networking ignores Android's cleartext rule, so
  `FakeLlm` on `http://127.0.0.1` passes in tests, while the real app on Android 9+ blocks plain-http endpoints such as a
  local Ollama (a confirmed bug). A green test is not proof that local endpoints work on a device.
- **Google Drive for desktop backs up `%USERPROFILE%\REPOMAN`, this repo included** (its log shows it reading the
  repo's Gradle lock files during builds). Seen on 2026-11-17 as `Unable to delete directory '...\app\build\...'`,
  first blamed on Windows indexing or antivirus. The real cause: Drive marks every folder under the backed-up tree
  read-only within about a minute, new folders included (a probe folder in the repo turned read-only within 50 s; one
  in `%LOCALAPPDATA%\Temp` did not), and Java cannot delete a read-only folder on Windows. So `clean` failed every
  time, even after `tools\gw.cmd --stop`, and so did a test run that had to replace its old results folder.
  `tools\gw.cmd` now clears the flag on `app\build`, `build` and `.gradle` before every run; with all 546 folders
  read-only, `clean assembleDebug :app:testDebugUnitTest` then passed first time. The `... (1).xml` / `(1).html`
  files seen under `app\build` were Drive's conflict copies. **Side effects the user should decide on:** Drive also
  uploads `.git`, `.gradle`, every build output and `local.properties` to their Google Drive, and a sync tool writing
  into `.git` while git works can damage the repository. Excluding this repo (or `REPOMAN`) from the Drive backup, or
  moving the repo to a folder Drive does not back up, removes all of this. That is an account setting, so it is the
  user's call.
- `StyleLockTest` starts `FakeLlm` on the fixed port 47123 so the BYOK sheet always shows the same URL. If another
  program holds that port, those tests fail.
- Original quirks the reference images deliberately keep: the action bar clips on cards at depth 2 and deeper, the
  rails break between sibling rows, and the legacy dialogs use the stock grey Material look.
- FINAL-DESIGN.md was written in a hurry just before a usage limit, and its introduction repeats itself. Read it
  critically, and raise anything that looks wrong with the user before building on it.

## If the Claude session is resumed

That session's working copy lives in a temporary folder under `%LOCALAPPDATA%\Temp\claude\` (`scratchpad\b` and
`scratchpad\b_base`; the `scratchpad\b_<suite>` folders for parallel test suites were deleted when it resumed, since
they held nothing new). It stopped at a usage limit at 03:55 on 2026-11-17, resumed at 04:30, wrote this handoff and
stopped at the user's request at about 04:35. If the user says it did more work after that, run the
`sync-from-claude` skill (outside Antigravity's terminal sandbox, see AGENTS.md), or:

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\sync-from-claude.ps1 -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File tools\sync-from-claude.ps1
```

It adds or updates files and never deletes; it never replaces a newer repo file, and never replaces an existing
reference screenshot with one from a suite folder (a newer one in `scratchpad\b` itself would be copied, which is why
the skill needs a baseline commit first); it leaves `app\src\main` alone unless you pass `-IncludeAppSources`.
robocopy compares timestamps, so a listed file can be byte-identical: check with `Get-FileHash` before treating it as
new work. If the temp folder is gone (checked outside the sandbox), this snapshot is final.

## Machine setup this relies on

- Android Studio at `C:\Program Files\Android\Android Studio`; its bundled JDK is what `tools\gw.cmd` uses.
- Android SDK at `%LOCALAPPDATA%\Android\Sdk`, with platform 36 (installed automatically by the first build on 2026-11-16).
- Gradle, dependency and Robolectric downloads are cached under the user folder; a new machine downloads them again.
- `local.properties` (not in git) points Gradle at the SDK. On another machine, Android Studio recreates it, or write
  `sdk.dir=<path to the Android SDK>` with forward slashes.

## Where the history is

| Where | What |
|---|---|
| `docs/claude-session/proof/` | The 1/255 cyan-shift proof: per-screen count of changed pixels, and the diff image for `main_all` |
| `docs/claude-session/scratchpad/TESTPLAN.md` | The behaviour test plan (see next steps); the one live document in this folder |
| `docs/claude-session/scratchpad/CONTRACT.md` | An earlier draft of FINAL-DESIGN.md |
| `docs/claude-session/scratchpad/HANDOFF.md` | The Claude session's own handoff note (2026-11-16, superseded by this file) |
| `docs/claude-session/memory/` | The notes Claude kept about the user and the project |
| `docs/claude-session/workflow-results/` | `wlthu16ja.output` is the raw output of the design panel run; the other three files are empty or unrelated tool output |
| T3 Code thread "Codebase Review and Feedback" | The full conversation, including the first-pass review text. The user can open it; agents cannot |

`docs/claude-session/` holds raw logs from this PC. It is committed and published (the GitHub repo is public): on
2026-11-17 the user chose to commit it with the Windows account name removed. What remains is harmless (the time
zone in `workflow-results/wlthu16ja.output`, Claude session IDs). Keep personal data out of anything added there.
