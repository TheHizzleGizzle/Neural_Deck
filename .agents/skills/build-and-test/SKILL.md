---
name: build-and-test
description: Build the NEURAL//DECK debug APK and run every test (behaviour tests plus pixel-exact screenshot tests) on this Windows machine. Use after any code change, and before saying any work is done.
---

# Build and test

1. From the repo root run:

   ```
   tools\gw.cmd assembleDebug :app:testDebugUnitTest --console=plain
   ```

   `tools\gw.cmd` picks the JDK bundled with Android Studio, because `JAVA_HOME` is not set here.
   The first run downloads dependencies and takes a few minutes. `DefectTest` is left out of this run on purpose.
   Run it **outside Antigravity's terminal sandbox**: Gradle needs the network, `%USERPROFILE%\.gradle` and the
   Android SDK under `%LOCALAPPDATA%`, which the sandbox blocks or hides.

2. **Green** means `BUILD SUCCESSFUL` here. Report the number of tests that ran (open
   `app\build\reports\tests\testDebugUnitTest\index.html`) and the APK path
   `app\build\outputs\apk\debug\app-debug.apk`.

3. **Defect tests**, once `DefectTest` exists. These pin confirmed bugs and are expected to fail until the refactor
   fixes them:

   ```
   tools\gw.cmd :app:testDebugUnitTest -Pdefects=true --tests com.example.wtfff.DefectTest --continue
   ```

   Report which defects still fail and which now pass. Never weaken a defect test to make it pass.
   During Phase A, a step is done when the normal run is green **and** no defect test that passed before now fails.
   When the whole `DefectTest` suite passes, propose to the user removing its exclusion in `app/build.gradle.kts`
   (`module.toml` is unaffected), so the fixed bugs stay pinned in every normal run.

4. **A screenshot test failed** (message starts `Style lock`): something visible changed. Switch to the
   `style-lock` skill. Do not re-record references to make it pass.

5. **A text golden failed** (message starts `Golden text`): a prompt, export or saved-data format changed.
   Treat it like a visual change: show the expected and actual text to the user and get approval before re-recording.
   A message starting `No golden` means a new test with no reference yet: show its output, and record only that test
   (`--tests`) after approval (`style-lock` skill, last section).

6. **`Unable to delete directory '...\app\build\...'`**: Google Drive for desktop backs up this folder and keeps
   marking folders read-only, which Java cannot delete on Windows. `tools\gw.cmd` clears the flag on the build
   folders before each run, so make sure you ran Gradle through it, not `gradlew` directly. If it still happens, run
   the same command again, or run `tools\gw.cmd --stop` first. This is not a code problem; don't change code because
   of it.

7. **"SDK location not found", "Could not download", "Could not resolve" or "Access is denied"**: the terminal
   sandbox is in the way. Re-run outside it. Do not edit `local.properties`, `gradle.properties` or the build files.

8. **Anything else failed**: fix the cause. Never delete, skip or weaken a test to get green.

## Optional: check on the emulator (not yet tried on this machine)

Start the emulator in its own terminal and wait for it to finish booting:

```
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_37.0
```

(`emulator.exe -list-avds` shows the exact AVD name if this one is not found.)

Then install, launch and take a screenshot. Pull the file instead of redirecting output, which corrupts PNGs in
Windows PowerShell:

```
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.example.wtfff/.MainActivity
& $adb shell screencap -p /sdcard/device.png
& $adb pull /sdcard/device.png app\build\device.png
```

Show the user `app\build\device.png` (git ignores `build\`) next to the matching reference in `app\src\test\snapshots\`.
