---
name: deepening-work-in-progress
description: "State of the Neural Deck architecture work and the 2026-11-17 move to Google Antigravity; the repo (AGENTS.md, docs/HANDOFF.md, docs/architecture/FINAL-DESIGN.md) is now the source of truth"
metadata:
  node_type: memory
  type: project
  originSessionId: 6ec907a7-9d26-42bf-9ea1-188ef2b213f3
  modified: 2026-11-17T10:14:13.953Z
---

On 2026-11-16 the user approved the architecture plan ("I trust your judgement"), on the condition that the art style stays identical: see [[art-style-fidelity]].

On 2026-11-17 the user moved the project to Google Antigravity. A side chat put a migration kit **into the repo** `<repo>`, and the main thread wrote `docs/architecture/FINAL-DESIGN.md` there just before its usage limit (03:55 local):
- `AGENTS.md` (canonical agent rules; `CLAUDE.md` imports it), `GLOSSARY.md`, `docs/HANDOFF.md` (state and next steps), `.agents/rules/style-fidelity.md`, `.agents/skills/{build-and-test,style-lock,continue-deepening,sync-from-claude}`.
- Gradle build, test kit and 28 reference screenshots; `tools/gw.cmd` (Gradle with Android Studio's JDK); `tools/sync-from-claude.ps1` (imports newer work from the Claude scratchpad, including `b_<suite>` folders; never deletes or overwrites newer repo files).
- `app/build.gradle.kts` leaves `DefectTest` out of normal runs; `-Pdefects=true` includes it.
- FINAL-DESIGN order: pure modules (provider, tree, engine, storage) first, Deck module and UI last; Phase A changes nothing visible except `sheet_prompt_lab_offline` (defect D14, needs approval); Phase B (rails, flat STARRED, new UI) is preview-only.
- Verified: build and 28/28 screenshot tests green from the repo; a 1/255 cyan shift fails 21 of 28 screens.
- `app/src/main` is still original. No git commits; git has no user.name/email configured. Behaviour tests from TESTPLAN are not written yet.

**Why:** anything left only in Claude's temp scratchpad or memory is invisible to Antigravity.

**How to apply:** if work resumes in Claude Code, read AGENTS.md, docs/HANDOFF.md and FINAL-DESIGN.md first and treat the repo as the source of truth, not the scratchpad. See [[build-environment]].
