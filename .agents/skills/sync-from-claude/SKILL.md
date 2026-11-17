---
name: sync-from-claude
description: One-time import of work the earlier Claude Code session did after the repo snapshot of 2026-11-17 (newer tests, test suites written in parallel folders, reference images, design notes). Use when the user says Claude kept working, or before starting new work if docs/HANDOFF.md says a sync is still pending.
---

# Sync from the Claude Code session

The Claude Code session kept a working copy of this project in a temporary folder outside the repo
(`scratchpad\b`), and wrote test suites in parallel copies (`scratchpad\b_<suite>`, each started from `b_base`).
The repo received a snapshot on 2026-11-17. That session resumed at 04:30 and stopped at the user's request at about
04:35, with nothing new (see `docs/HANDOFF.md`). Only run this skill if the user says it did more work after that.

0. **Preconditions.** A baseline commit must exist: `git rev-parse --verify HEAD` must succeed. If it fails, stop and
   ask the user for the baseline commit (AGENTS.md rule 4); without it, step 3's recovery is impossible. The Claude
   session must have stopped. Run the script **outside Antigravity's terminal sandbox**: it reads
   `%LOCALAPPDATA%\Temp\claude` and `%USERPROFILE%\.claude`, which the sandbox hides.

1. Preview what would change:

   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File tools\sync-from-claude.ps1 -DryRun
   ```

   If it says it cannot find the session folder (from a run outside the sandbox), the temp files are gone and the repo
   snapshot is final. Tell the user and stop. Every line it lists is a file whose timestamp is newer in the Claude
   copy; files that are newer in the repo are never replaced. robocopy compares timestamps only, so a listed file can
   be byte-identical: check with `Get-FileHash` before treating it as new work. As of the handoff, the dry run lists
   `app\build.gradle.kts` (identical) and one memory note, and that is expected.

2. Copy it in:

   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File tools\sync-from-claude.ps1
   ```

   It adds or updates the Gradle files, `app\src\test` (tests and reference images, including suites from the
   `b_<suite>` folders), the design documents and Claude's notes. It never deletes, never replaces an existing
   reference screenshot with one from a suite folder, and never touches `app\src\main` unless asked.
   A line starting `!` needs a human look: two suites changed the same file, or a suite changed app code.

3. If step [3/5] says the Claude copy of the app code differs, the session had started refactoring. Copy that code only
   if its tests pass: run the script again with `-IncludeAppSources`, then run the `build-and-test` skill. If the suite
   is not green, restore `app\src\main` from the last commit (`git checkout -- app/src/main`) and tell the user what was
   half-done.

4. Run the `build-and-test` skill. Suites copied mid-session may be unfinished: finish them per
   `docs/claude-session/scratchpad/TESTPLAN.md`, or remove a suite that does not compile and say so.

5. Read the newest notes in `docs/claude-session/scratchpad/`, update `docs/HANDOFF.md` with anything new, and record
   there that the sync is done, so it is not repeated.
