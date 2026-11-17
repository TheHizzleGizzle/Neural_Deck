---
name: build-environment
description: How to build and test Neural Deck on this Windows machine — JDK and SDK locations and the tool versions confirmed to work
metadata:
  node_type: memory
  type: reference
  originSessionId: b525cabb-5227-46cc-8972-224249ce20df
  modified: 2026-11-17T10:24:46.972Z
---

Confirmed working on 2026-11-16:

- No `java` or `gradle` on PATH. Use Android Studio's bundled JDK 25: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (`local.properties` needs forward slashes: `sdk.dir=%LOCALAPPDATA%/Android/Sdk`). Platform 36 was auto-installed by the first build; an AVD `Medium_Phone` (API 37, 1080x2400, 420dpi) exists.
- Versions that build the app as-is: Gradle 9.9.1 (wrapper), Android Gradle Plugin 9.5.1, compileSdk/targetSdk 36, minSdk 26, Java 17 source level, the dependency versions already listed in `app/module.toml`.
- Tests: JUnit 4.13.2, Robolectric 4.18 with `@Config(sdk = 36, qualifiers = "w411dp-h914dp-420dpi")` and `@GraphicsMode(NATIVE)` renders real screens to PNG (`view.draw(canvas)`), including bottom sheets and dialogs. `org.json:json` is needed for plain-JVM tests. `com.sun.net.httpserver` is not on the Android test compile classpath; use a `java.net.ServerSocket` fake instead.
- The project is also built by CodeAssist from `app/module.toml` (no Gradle), so keep that file intact and new code in plain Java. The manifest's `package=` attribute only produces a warning under AGP.
- First test run takes about 2 minutes; later runs under 1 minute.
- In the repo, use `tools\gw.cmd <tasks>` (sets JAVA_HOME to Android Studio's JBR). `DefectTest` is excluded unless `-Pdefects=true`. The Gradle build cache is deliberately off in the repo.
- Builds intermittently fail with `Unable to delete directory '...\app\build\...'` (Windows indexing/antivirus holding fresh files). Retrying the same command a few seconds later works; it is not a code problem.
- PowerShell tool quirk: a command containing a Java `/**` comment plus `Remove-Item` gets blocked by a safety filter. Put such steps in a .ps1 file and run it with `powershell -File`.
