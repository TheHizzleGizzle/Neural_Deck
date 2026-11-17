---
name: style-lock
description: Prove the app still looks exactly like the original, or show a proposed visual change for approval. Use when a screenshot test fails, before any visible change, or when the user asks to see, improve, restyle or redesign a screen. Use this instead of generic redesign or mockup skills (such as Mobbin's redesign-screen) for this app.
---

# Style lock

The 28 reference images in `app\src\test\snapshots\` were rendered from the original, unmodified app. `StyleLockTest`
renders the same screens from the current code and requires every pixel to match. This is the proof the user relies on
that the art style has not drifted.

## When a screenshot test fails

1. Run `tools\gw.cmd :app:testDebugUnitTest --console=plain` and note every test whose message starts `Style lock`.
   Each names the changed region (`x a..b, y c..d`).
2. For each failing `<name>`, gather three images:
   - before: `app\src\test\snapshots\<name>.png`
   - after: `app\build\stylelock\<name>.actual.png`
   - diff: `app\build\stylelock\<name>.diff.png` (changed pixels red, everything else dimmed)
3. Put them side by side in your walkthrough or artifact for the user, with one line per screen on what changed and why.
   Embed the three images; if embedding does not work, list their absolute paths so the user can open them.
4. If the change was **not intended**, it is a bug: fix the code until the test passes again.
5. If it **was intended**, wait for the user to approve that exact change. A baseline commit must exist first
   (`git rev-parse --verify HEAD`), so the old references can be recovered. Then re-record **only the approved
   screens**, one `--tests` filter per test method:

   ```
   tools\gw.cmd :app:testDebugUnitTest "-Pstylelock.record=true" --tests "com.example.wtfff.StyleLockTest.<method>"
   tools\gw.cmd :app:testDebugUnitTest
   ```

   Keep the double quotes around `-Pstylelock.record=true`: PowerShell splits it at the dot otherwise. `<method>` is the
   failing test's method name from the report, not the screen name (`sheet_prompt_lab_offline` is
   `sheetPromptLabOfflineResult`); `docs/HANDOFF.md` lists the names that differ. The second run must be fully green. Then `git status app/src/test` must list only the files the user approved.
   Never re-record without `--tests`: that rewrites all 28 references and every text golden at once and silently
   approves any other drift.

## To propose a visual improvement or a new theme

The user welcomes style ideas but must see them first.

1. Make the change in the working tree without re-recording.
2. Run the tests. The failing screens produce `.actual.png` renders of your proposal: these are the preview.
3. Show before / after / diff for every affected screen and ask for approval.
4. Not approved: revert. Approved: re-record as above.

For a brand-new screen with no reference yet, write a test that renders it with `Snap`; the first run fails with
"No golden" and writes `app\build\stylelock\<name>.actual.png`. Show that image and record only after approval.

## Never

- Edit, replace or delete files in `app\src\test\snapshots\` by hand.
- Re-record to make an unexplained failure go away.
- Introduce colours, corner radii, fonts or shadows that are not already in `app/src/main/res`.
