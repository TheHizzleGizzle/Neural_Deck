---
name: continue-deepening
description: Continue the approved refactor of NEURAL//DECK (Provider profile, IdeaTree, Idea Engine, sessions store, then the Deck module and UI cut-over) safely under the tests, following docs/architecture/FINAL-DESIGN.md. Use when the user says to continue, resume, or keep improving the architecture.
---

# Continue the deepening plan

The user approved this plan on 2026-11-16 ("I trust your judgement"), on one condition: the art style stays identical.
The contract is [docs/architecture/FINAL-DESIGN.md](../../../docs/architecture/FINAL-DESIGN.md). It wins over the
panel designs and over the older order given in the architecture review.

## Before writing code

1. Read `docs/HANDOFF.md` (state and next step) and `docs/architecture/FINAL-DESIGN.md` (sections 0 and 1 at least).
2. Check a baseline commit exists: `git rev-parse --verify HEAD` must succeed. If it fails, ask the user to approve a
   baseline commit first. Git has no name or email configured: ask the user for them, never invent them.
3. Run the `build-and-test` skill. Start only from a green suite.

## Order

1. **Behaviour tests first**, if they do not exist yet. Follow
   `docs/claude-session/scratchpad/TESTPLAN.md` for its hard rules, test-kit cheat sheet and the five suite specs:
   `DeckScreenTest`, `AiFlowTest`, `GraveyardSkillsTest`, `ByokPromptLabTest` (these describe the original app and must
   pass on it now) and `DefectTest` (must fail now, with a message naming each bug). In its "Working setup" section,
   follow the **"Working in the repo"** note at the top and the `Golden.text` naming convention; skip the rest (the
   Claude session's scratch copies and lock script). Work in this repo and run one suite with
   `tools\gw.cmd :app:testDebugUnitTest --tests com.example.wtfff.<Suite> --console=plain`
   (add `-Pdefects=true` for `DefectTest`).

   **Then prove each passing suite is not vacuous.** First confirm `git status --porcelain app/src/main` prints
   nothing; if it does not, stop and ask. For at least 4 different behaviours per suite, make a small temporary
   change in one file under `app/src/main` that should break exactly that behaviour, run that suite, and confirm the
   matching test fails. Then restore that file with `git checkout -- <that file>` and confirm
   `git status --porcelain app/src/main` prints nothing again. Never commit these changes. This is the only allowed exception to TESTPLAN's rule against editing
   app code, and it needs the baseline commit. For `DefectTest`, confirm each test fails on its own D-numbered
   assertion, not on a harness error or a timeout.
2. **Ask the user to approve the defect fixes** before Phase A starts: list D1 to D19 (TESTPLAN Suite 5) with the
   behaviour each fix gives, plus the two manifest changes in FINAL-DESIGN §7, and show concretely the ones that
   change what appears on screen (`docs/HANDOFF.md`, next step 3, lists them). Record the approval in
   `docs/HANDOFF.md`.
3. **Phase A**, in the order of FINAL-DESIGN's "Order of work": the pure modules first (provider, tree, engine, sessions
   storage), each with its own plain-JVM tests and no behaviour change; then the Deck module and the UI cut-over.
   One module at a time, each fully green before the next starts. At the end every suite passes, including `DefectTest`.
4. **The one look change in Phase A**: `sheet_prompt_lab_offline` loses the untrue line
   `+ Bound 1 Graveyard anti-pattern constraint(s)` (defect D14). Show the user before / after / diff with the
   `style-lock` skill and re-record that one screen only after approval (its test method is
   `StyleLockTest.sheetPromptLabOfflineResult`). Every other screenshot stays pixel-identical.

After each step (a suite written, a module landed, an approval given), update the Status table and Next steps in
`docs/HANDOFF.md` in the same change (AGENTS.md rule 10).

## Phase B: previews only

Connected rails, a flat STARRED view, restyled dialogs, any new string or screen, and the product ideas (Converge,
Taste Profile, undo, a delete-session confirmation) are Phase B. Show previews with the `style-lock` skill when the
user asks; build nothing without approval.
