@echo off
rem NEURAL//DECK - run the Gradle wrapper with a usable JDK.
rem JAVA_HOME is not set on this machine and java is not on PATH, so this falls back to
rem the JDK that ships inside Android Studio.
rem
rem Usage, from the repo root, in PowerShell or cmd:
rem   tools\gw.cmd assembleDebug
rem   tools\gw.cmd :app:testDebugUnitTest
setlocal
if defined JAVA_HOME goto run
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
  set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
  goto run
)
if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" (
  set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
  goto run
)
echo JAVA_HOME is not set and Android Studio's bundled JDK was not found. 1>&2
echo Install JDK 17 or newer, or set JAVA_HOME, then try again. 1>&2
exit /b 1

:run
rem Google Drive for desktop backs up the folder this repo lives in and keeps setting the read-only flag on its
rem folders. Java cannot delete a read-only folder on Windows, so Gradle would fail with "Unable to delete
rem directory". Clear the flag on the build-output folders (never on source files) before every run.
for %%D in ("app\build" "build" ".gradle") do (
  if exist "%~dp0..\%%~D" (
    attrib -R "%~dp0..\%%~D" >nul 2>&1
    attrib -R "%~dp0..\%%~D\*" /S /D >nul 2>&1
  )
)
call "%~dp0..\gradlew.bat" -p "%~dp0.." %*
exit /b %ERRORLEVEL%
