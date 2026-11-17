---
name: art-style-fidelity
description: "Neural Deck's pixel/cyber-deck art style must stay extremely close to the original; show style changes or new themes before making them"
metadata:
  node_type: memory
  type: feedback
  originSessionId: b525cabb-5227-46cc-8972-224249ce20df
  modified: 2026-11-16T16:54:49.820Z
---

Keep NEURAL//DECK's visual style extremely similar to the original app. Any mockup or before/after picture of the app must use the real palette and UI elements (res/values/colors.xml, the bg_px_* drawables: square corners, 2dp strokes, hard 3dp offset shadows, monospace labels). Style improvements or new themes are welcome as ideas, but must be shown to the user and approved before any permanent change.

**Why:** In the first architecture report (2026-11-16) the hand-drawn "after" pictures subtly changed colors (e.g. accent-colored rails where the app uses faint 0x443B5284 ancestor rails, a bluer background, rounded corners) and a few small UI elements, and the user noticed and flagged it.

**How to apply:** Prefer real emulator screenshots over drawn mockups. When code changes touch anything visible, compare before/after screenshots. Treat any new UI element (even a small chip or dialog) as something to preview first. See [[visual-learner-delegates-decisions]].
