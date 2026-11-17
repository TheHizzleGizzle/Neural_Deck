---
trigger: glob
globs: app/src/main/res/**,app/src/main/java/com/example/wtfff/ui/**,app/src/main/java/com/example/wtfff/MainActivity.java,app/src/test/snapshots/**
description: The app's pixel cyber-deck art style must not drift. Loaded when editing layouts, drawables, colours, UI code or reference screenshots.
---

# Art style fidelity

The user wants NEURAL//DECK to look exactly like the original. An earlier set of mockups subtly changed colours and
small UI details, and the user noticed. Treat every pixel as approved design.

## The style, as built

- **Palette** (`app/src/main/res/values/colors.xml`), use these resources, never new hex values:
  backgrounds `cyber_void` #080A12, `cyber_recessed` #05070D, `cyber_panel` #0F1424, `cyber_cartridge` #151D33,
  `cyber_cartridge_selected` #1C2847; borders `cyber_border_dim` #273659, `cyber_border_bright` #3B5284;
  `cyber_shadow` #030408; accents `neon_cyan` #00F0FF, `neon_magenta` #FF2E93, `neon_lime` #39FF88,
  `neon_amber` #FFB800, `neon_crimson` #FF3864, `neon_violet` #A855F7; text `text_holo` #ECF6FF,
  `text_secondary` #8DA4C7, `text_muted` #516587, `text_on_neon` #050811. The existing `*_dim` variants
  (`neon_cyan_dim` #3300F0FF and the like) and `primary` / `on_primary` are part of the palette too. Hex literals that
  already exist in the code (the rail colours in `PixelBranchConnectorView`, the HUD colours in `MainActivity`, the
  sheets' text colours, and the literals inside the `bg_px_*` drawables) are part of the original look: keep them,
  don't add new ones.
- **Shapes** (`res/drawable/bg_px_*.xml`): square corners everywhere (0dp, bottom sheets included); 2dp strokes,
  1dp on idle chips, the graveyard badge and the cartridge's shadow outline; hard offset shadows with no blur (3dp on
  cartridges, 2dp on buttons).
- **Type**: monospace, bold, ALL_CAPS_WITH_UNDERSCORES for labels and buttons (`SPARK_IDEAS`, `PROMPT_LAB`,
  `[+SPLICE]`, `[SKILLS ▾]`); body text in the default sans.
- **Icons**: 16x16-grid pixel vectors (`ic_px_*.xml`), tinted with palette colours.
- **Rails** (`ui/PixelBranchConnectorView.java`): 18dp per depth level, faint ancestor rails `0x443B5284`,
  the active elbow in the card's accent colour at alpha `0xAA`, a square solder joint in the full accent colour.
- **Legacy dialogs** (banish, sessions, errors, rename, forge skill) use the stock grey Material dialog. That is the
  original look. Restyling them would be a visual change: propose it, don't do it.
- Known original quirks (also not to be "fixed" silently): rows at depth 2+ clip the action bar on the right;
  rails break in the gaps between sibling rows.

## Before you change anything visible

1. Make the change, then run `tools\gw.cmd :app:testDebugUnitTest`. Screens that changed fail and write
   `app\build\stylelock\<name>.actual.png` and `<name>.diff.png`.
2. Show the user `app\src\test\snapshots\<name>.png` (before), the `.actual.png` (after) and the `.diff.png`
   side by side, and say what changed and why.
3. Only after the user approves, and only once a baseline commit exists: re-record just the approved screens with
   `"-Pstylelock.record=true" --tests "com.example.wtfff.StyleLockTest.<method>"` (keep the quotes; `<method>` is the
   test method, not the screen name; see the `style-lock` skill), re-run the full suite, and confirm it
   is green and that `git status app/src/test` lists only the approved files.
4. If not approved, revert the change.

Never edit or replace files in `app/src/test/snapshots/` by hand.
