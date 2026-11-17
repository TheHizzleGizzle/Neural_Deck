# NEURAL//DECK: instructions for coding agents

Read this whole file before doing anything. Google Antigravity loads it automatically.
The full story, current state and next steps are in [docs/HANDOFF.md](docs/HANDOFF.md). Read that next.

## What this is

NEURAL//DECK is a small Android app for AI-assisted brainstorming: Java, Android Views, package `com.example.wtfff`,
minSdk 26, targetSdk 36. You type a seed idea; a model you bring your own key for (any OpenAI-compatible endpoint)
"sparks" idea cards; you branch, splice, star and banish them into a tree. It has a deliberate pixel-art "cyber deck" look.

It was first written in **CodeAssist**, an Android IDE that runs on a phone and builds from `app/module.toml` without
Gradle. A Gradle build was added on 2026-11-16 so the app can be built and tested on this PC. **Both builds must keep working.**

## Rules that matter most

1. **Keep the art style exactly as it is.** The user's top concern. Palette, square corners, 2dp strokes, hard offset
   shadows, monospace caps labels, pixel icons, the rails between cards: do not change any of it, not even subtly.
   Style improvements and new themes are welcome *as proposals*: show before/after images and get approval before
   making them permanent. Before editing any layout, drawable, colour, `ui/` file, `MainActivity` or reference
   screenshot, read [.agents/rules/style-fidelity.md](.agents/rules/style-fidelity.md). For any visual proposal use
   the `style-lock` skill (real renders of the app), never a generic redesign or mockup skill (for example the
   globally installed Mobbin `redesign-screen`): drawn mockups drifted from the real look before, and the user noticed.
2. **Show, don't tell.** The user is a visual learner. When you report work, show the rendered screens (PNG files the
   tests produce) in your walkthrough or artifact, before / after / diff side by side for anything visual.
3. **Decide and proceed.** The user delegates technical decisions ("I trust your judgement"). Pick the recommended option,
   say what you picked and why in a line, and keep going. Stop and ask only about: any visible change to the app,
   deleting or overwriting their data or work, git commits and pushes, and anything that costs money or touches accounts.
4. **A baseline commit comes first.** If `git rev-parse --verify HEAD` fails, the repo has no commits yet: before you
   change or record anything (app code, tests, reference screenshots, text goldens), ask the user to approve a
   baseline commit of the current tree, so every later change can be reviewed and undone. If git has no user name or
   email (`git config user.name` prints nothing), ask the user which to use (never invent one) and set them for this
   repo only. The current git state and the user's answers are recorded in `docs/HANDOFF.md`.
5. **Tests stay green.** Run the suite before saying anything is done. Never delete, skip or loosen a test to make it
   pass, and never re-record reference screenshots unless the user approved that exact visual change.
   `DefectTest` (once it exists) pins confirmed bugs and fails until they are fixed, so normal runs leave it out;
   run it on purpose with `-Pdefects=true` (table below).
6. **Keep it CodeAssist-friendly.** Plain Java only (no Kotlin, no annotation processors). Gradle accepts Java 17, but
   CodeAssist does not: no records, sealed types, pattern matching, `var`, text blocks or switch expressions, in app
   code or tests (lambdas and `java.util.function` are fine). No new runtime libraries without asking. If you change a runtime (`implementation`) dependency in `app/build.gradle.kts`, make the same change
   in `app/module.toml`. Test-only (`testImplementation`) dependencies stay in Gradle only: never add a test library to
   `module.toml`'s `implementation` list.
7. **Saved data must keep loading.** Do not rename `neural_deck_sessions_v1.json`, `neural_deck_custom_skills_v1.json`,
   any SharedPreferences file (`neural_deck_ai_config`, `neural_deck_vault_prefs`, `neural_deck_repo_prefs`) or key,
   the Keystore alias `neural_deck_byok_master_key_v1`, or the `enc_key_<provider>` slots (renaming those loses every
   saved API key). Format changes must be additive.
8. **User-visible text stays byte-identical** (button labels, toasts, dialog titles, loading messages such as
   `SPARKING_ROOT_NODES // [BADGE]...`, error codes such as `SPARK_FAILED`) unless the user approves a change.
9. **Use the project's words.** Domain and architecture terms are defined in [GLOSSARY.md](GLOSSARY.md).
10. **Keep `docs/HANDOFF.md` true.** When you finish a step or the user decides something (git identity, the
    baseline commit, what goes into git, an approval), update its Status table and Next steps in the same change, so
    the next session starts from the facts.

## Build and test (Windows)

`java` is not on PATH and `JAVA_HOME` is not set on this machine. Go through `tools\gw.cmd`; it uses the JDK
that ships inside Android Studio and works from PowerShell or cmd, from any folder.

**Antigravity permissions on this machine.** As of 2026-11-17 the Antigravity App here uses the **Turbo** preset:
the terminal sandbox is off and commands run without an approval prompt. Nothing in the tool will stop a
`git commit`, `git push`, `git config`, `git checkout --`, a `"-Pstylelock.record=true"` run or the sync script, so
the "stop and ask" rules above are yours to keep: ask in chat and wait for the answer before any of them.

If the preset is ever **Default** instead, terminal commands run in a sandbox with no network that hides the rest of
the user profile. Gradle needs the network, `%USERPROFILE%\.gradle` and the Android SDK under `%LOCALAPPDATA%`, so
`tools\gw.cmd` must then run **outside the sandbox** (ask for an unsandboxed run). If Gradle fails with "SDK
location not found", "Could not download", "Could not resolve" or "Access is denied", the sandbox is in the way, not
the code: re-run outside it and change nothing (never edit `local.properties` or the build files because of it).
The same goes for `tools\sync-from-claude.ps1`, which reads `%LOCALAPPDATA%\Temp\claude` and `%USERPROFILE%\.claude`.

**PowerShell and dotted flags.** PowerShell splits an unquoted `-Pstylelock.record=true` at the dot. Put double quotes
around any `-P` or `-D` argument that contains a dot, as in the table below; the quotes work in cmd too.

| Task | Command (from the repo root) |
|---|---|
| Build the debug APK | `tools\gw.cmd assembleDebug` (output: `app\build\outputs\apk\debug\app-debug.apk`) |
| Run every test | `tools\gw.cmd :app:testDebugUnitTest` |
| Run one test class | `tools\gw.cmd :app:testDebugUnitTest --tests com.example.wtfff.StyleLockTest` |
| Run the defect tests (expected to fail until fixed) | `tools\gw.cmd :app:testDebugUnitTest -Pdefects=true --tests com.example.wtfff.DefectTest --continue` |
| Re-record approved screens only | `tools\gw.cmd :app:testDebugUnitTest "-Pstylelock.record=true" --tests "com.example.wtfff.StyleLockTest.<method>"`, then run the full suite without the flag |

`<method>` is the test method name, which is not the screen name: `sheet_prompt_lab_offline` is
`sheetPromptLabOfflineResult`, for example. Take it from the failing test in the report, or find the line
`Snap...("<name>"` in `StyleLockTest.java`. Re-recording without `--tests` rewrites all 28 references and every
text golden at once, silently approving any other drift. Only re-record after a baseline commit exists, and check
that `git status app/src/test` lists only the files the user approved.

- First run takes a few minutes (downloads); later runs about a minute or two.
- **Google Drive for desktop backs up the parent folder `REPOMAN`, this repo included.** Within about a minute it
  marks every folder read-only (new ones too), and Java cannot delete a read-only folder on Windows. That was the real
  cause of `Unable to delete directory '...\app\build\...'` (found 2026-11-17). `tools\gw.cmd` now clears the flag
  on `app\build`, `build` and `.gradle` before every run, so always build through it. If the error still appears, run
  the same command again, or `tools\gw.cmd --stop` and retry; it is not a code problem. Source folders are read-only
  too: editing files inside them works, but if deleting or renaming a folder fails with "Access is denied", clear the
  flag first (PowerShell, repo root; changes no file contents):
  `Get-ChildItem -Recurse -Force -Directory | Where-Object { $_.FullName -notmatch '\\\.git(\\|$)' -and ($_.Attributes -band 'ReadOnly') } | ForEach-Object { $_.Attributes = $_.Attributes -band -bnot [IO.FileAttributes]::ReadOnly }`.
  Files named like `<name> (1).<ext>` that appear under `app\build` are Drive's conflict copies, not project files.
- Test report: `app\build\reports\tests\testDebugUnitTest\index.html`.
- A failed screenshot writes `app\build\stylelock\<name>.actual.png` and `<name>.diff.png` (red pixels = changed).
  The approved originals are `app\src\test\snapshots\<name>.png`.
- Git Bash alternative: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest`.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`, set in the untracked `local.properties`. An emulator image
  (AVD name `Medium_Phone_API_37.0`; `emulator -list-avds` confirms it) exists but has not been used for this project yet.

## How the tests work

Robolectric renders the real app on the JVM, no device needed: the actual layouts, drawables and code draw to a
bitmap, which is compared pixel for pixel with a reference image recorded from the original, unmodified app.
The test kit in `app/src/test/java/com/example/wtfff/testkit/`:

- `Ui` drives the app like a user: views by id, tree rows, dialogs, toasts.
- `Snap` renders a screen and compares it with `app/src/test/snapshots/<name>.png`.
- `Golden` does the same for text (prompts sent to the model, exported Markdown, saved JSON) in `app/src/test/resources/golden/`.
  None are recorded yet; the folder is created when the first behaviour suite records one. Today only the 28
  screenshots are locked.
- `FakeLlm` is a fake OpenAI-compatible server on localhost: queue replies, hold them, read back the exact prompts.
  `com.sun.net.httpserver` is not on the test compile classpath, so for any HTTP test reuse `FakeLlm`
  (a `java.net.ServerSocket` fake).
- `Seed` writes saved sessions in the app's real on-disk format before launch.

`StyleLockTest` covers 28 screens, sheets and dialogs. Write new tests through `Ui` (user-level behaviour) or through a
module's own public interface. Never reach into private fields.

## Code map (`app/src/main/java/com/example/wtfff/`)

| File | Role today |
|---|---|
| `MainActivity.java` (659 lines) | Main screen **and** every deck rule: spark, branch, splice, banish, revive, sessions, saving |
| `ai/OpenAiCompatibleClient.java` (522) | One worker thread, four hand-built prompts, raw HTTP, reply parsing |
| `ai/LocalPromptHeuristic.java` | Offline Prompt Lab rewrite |
| `model/BrainstormSession.java` | A session's cards and graveyard, plus two tree walks that overwrite `IdeaCard.depth` |
| `model/IdeaCard`, `GraveyardEntry`, `CreativeSkill`, `PromptOptimizationResult` | Plain data and JSON (8 built-in skills live in `CreativeSkill`) |
| `storage/BrainstormRepository.java` | Sessions file, custom skills file, active-session preference, starter session |
| `storage/AiConfig.java`, `storage/KeyVault.java` | Provider settings (SharedPreferences) and API keys (Android Keystore) |
| `ui/IdeaCardAdapter.java`, `ui/PixelBranchConnectorView.java` | Card rows and the branch rails between them |
| `ui/PixelGridBackgroundView.java` | The pixel grid drawn behind the main screen |
| `ui/SkillsSheet`, `GraveyardSheet`, `PromptLabSheet`, `ByokSettingsSheet` | The four bottom sheets |
| `app/src/main/res/` | Layouts (`activity_main`, `item_idea_card`, `bottom_sheet_*`), the `bg_px_*` drawables, `ic_px_*` icons, the palette in `values/colors.xml` |

## Where things are

| Path | What |
|---|---|
| `docs/HANDOFF.md` | State, decisions, verified facts, next steps. Start here. |
| `docs/architecture/FINAL-DESIGN.md` | The contract the refactor is built to: module interfaces, order of work, Phase A / Phase B. It wins over the panel designs. |
| `docs/architecture/architecture-review-20261116-1630.html` | Visual architecture review: missing pieces, problems, the four planned refactors (open in a browser) |
| `docs/architecture/design-panel/` | Design brief, four candidate designs, the judges' verdicts |
| `docs/claude-session/` | Raw notes copied from the Claude Code session (history; read, don't edit). One exception: `scratchpad/TESTPLAN.md` is the live behaviour-test plan; you may correct it (say what and why in `docs/HANDOFF.md`) |
| `.agents/skills/` | Step-by-step procedures: `build-and-test`, `style-lock`, `continue-deepening`, `sync-from-claude` |
| `.agents/rules/` | Extra rules loaded when you edit UI files |
| `tools/` | `gw.cmd` (Gradle with the right JDK), `sync-from-claude.ps1` (one-time import of late Claude work) |
