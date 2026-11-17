# Handoff — NEURAL//DECK deepening (written 2026-11-16, mid-task)

## What the user asked

1. `/improve-codebase-architecture` report (done: `architecture-review-20261116-1630.html` in this folder).
2. Then: "I trust your judgement" — proceed with the recommended track. Hard requirement: the app's art style must stay extremely close to the original. Style improvements or new themes must be shown first, before any permanent change. The user is a visual learner.

## Decisions taken

- Track: Step 0 (Gradle build + tests) -> C1 Deck -> C2 IdeaTree -> C3 Idea Engine -> C4 Provider profile, with the confirmed bug fixes that live inside those modules.
- New UI (Converge sheet, Taste Profile, Undo affordance, delete-session confirm) and theme ideas: previews only, awaiting approval. Do not build them yet.
- Prove style fidelity with pixel comparison of real renders, not drawn mockups.
- T3 "Agent device access" is off, so the emulator was NOT started. Renders come from Robolectric native graphics on the JVM.
- No commits: the repo has no commits and the user has not asked for any.

## State

- Repo `<repo>`: untouched, identical to the original zip.
- `b\`: copy of the original + Gradle build (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties`, `app\build.gradle.kts`, wrapper 9.9.1). `./gradlew assembleDebug` and `:app:testDebugUnitTest` work with `JAVA_HOME` = Android Studio JBR.
- `b\app\src\test\java\com\example\wtfff\testkit\`: `Snap` (golden PNG compare; record with `-Pstylelock.record=true`, not yet wired into build.gradle.kts as a system property), `Ui` (drive by view id, dialogs, toasts, tree shape), `FakeLlm` (ServerSocket fake endpoint, gated replies, captured prompts), `Seed` (writes saved sessions in the shipped format).
- `b\app\src\test\java\com\example\wtfff\ExploreTest.java`: throwaway. 3 of 4 pass. The failure is a harness bug: `Ui.alert()` throws inside an `await` condition; add a non-throwing check.
- Renders of the ORIGINAL app: `b\app\build\smoke\main.png`, `b\app\build\explore\*.png` (deep tree, four sheets, two dialogs, loading bar).
- Design panel workflow: run id `wf_df7e4abe-43a`, script `%USERPROFILE%\.claude\projects\C--Users-<user>-REPOMAN-Neural-Deck-main\b525cabb-5227-46cc-8972-224249ce20df\workflows\scripts\neural-deck-design-panel-wf_df7e4abe-43a.js`, args `{"dir": "<this folder>\\design"}`. Four designs (`design\design-*.md`) then three judges. Resume with `resumeFromRunId` if it was stopped.

## Observations about the original look (from the real renders)

- Bottom sheets and legacy AlertDialogs render over a white/grey backdrop in the harness (no dim scrim); the sheet content itself is faithful.
- Rails: only the active elbow (accent colour at AA alpha) plus a solid 6dp joint are clearly visible; ancestor rails are the faint 0x443B5284. Gaps between sibling rows and a rail drawn past the last child are visible in `explore\tall_tree.png`. Row action bars overflow to the right at depth >= 2 (BANISH is clipped) — that is original behaviour, not something to "fix" silently.
- Legacy dialogs (banish, session picker, errors) use the stock grey Material dialog, not the pixel style. Restyling them would be a style change: preview first.

## Next steps

1. Fix the harness bug; wire `stylelock.record`; add `inputs.dir("src/test/snapshots")` to the test task.
2. Against the ORIGINAL code in `b\`: record golden screenshots (main ALL/STARRED/ROOTS, splice armed, collapsed, empty deck, deep tree, four sheets, dialogs, loading bar) and write UI-level behaviour tests (star, collapse, filters, banish + reasons, revive, sessions, spark/branch/splice positions, captured prompts, markdown export, error dialogs).
3. Read the winning design + grafts; write the final interface contract; add `GLOSSARY.md` (Deck, Session, Idea Card, Skill, Spark, Branch, Splice, Banish, Graveyard, Revive, Purge).
4. Copy the Gradle build + tests into the repo, implement C1–C4 under green tests. Expected pixel changes: rails only (and STARRED view indent). Everything else must be byte-identical.
5. Show the user real before/after renders and the diff, plus previews of anything that needs approval.

## Side effects already made on the user's machine

- Android SDK Platform 36 installed into `%LOCALAPPDATA%\Android\Sdk\platforms\android-36` (auto, by the first Gradle build).
- Gradle 9.9.1 and dependency caches under `%USERPROFILE%\.gradle`; Robolectric runtime jars under the local Maven cache.
- Four memory files under the project memory folder.
