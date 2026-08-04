@echo off
setlocal EnableExtensions

rem ============================================================
rem  FireballPredict quick start (Windows)
rem
rem  Usage:
rem    quickstart.bat           start Minecraft client (auto setup on first run)
rem    quickstart.bat client    same as above
rem    quickstart.bat setup     run the ForgeGradle workspace setup
rem    quickstart.bat build     build the mod jar into build/libs
rem    quickstart.bat check     locate a JDK 8 and print its version
rem
rem  This script contains no machine-specific paths. It looks for a
rem  JDK 8 in this order:
rem    1. org.gradle.java.home from the local gradle.properties
rem    2. the JAVA_HOME environment variable
rem    3. java from PATH
rem    4. common JDK install directories
rem ============================================================

cd /d "%~dp0"

set "TASK=client"
if /i "%~1"=="client" set "TASK=client"
if /i "%~1"=="run"    set "TASK=client"
if /i "%~1"=="setup"  set "TASK=setup"
if /i "%~1"=="build"  set "TASK=build"
if /i "%~1"=="check"  set "TASK=check"

echo.
echo ============================================
echo    FireballPredict quick start
echo ============================================
echo.

rem ---------- 1) local override in gradle.properties ----------
if exist "gradle.properties" (
    for /f "usebackq delims=" %%L in ("gradle.properties") do (
        for /f "tokens=1,* delims==" %%A in ("%%L") do (
            if /i "%%A"=="org.gradle.java.home" (
                set "JDK=%%~B"
                goto :jdk_candidate
            )
        )
    )
)

rem ---------- 2) JAVA_HOME ----------
if defined JAVA_HOME (
    set "JDK=%JAVA_HOME%"
    goto :jdk_candidate
)

rem ---------- 3) java from PATH ----------
for /f "delims=" %%J in ('where java 2^>nul') do (
    set "JAVA_BIN=%%J"
    goto :path_java
)
goto :search_dirs

:path_java
for %%I in ("%JAVA_BIN%") do set "JDK=%%~dpI"
set "JDK=%JDK:~0,-1%"
call :try_jdk "%JDK%"
if defined JDK goto :jdk_candidate

rem ---------- 4) common JDK install directories ----------
:search_dirs
set "JDK="
for %%R in ("%USERPROFILE%\.jdks" "%ProgramFiles%\Java" "%ProgramFiles(x86)%\Java" "%ProgramFiles%\Eclipse Adoptium") do (
    if exist "%%~R" (
        for /d %%D in ("%%~R\*") do (
            call :try_jdk "%%~D"
            if defined JDK goto :jdk_candidate
        )
    )
)

:jdk_candidate
if not defined JDK (
    echo [ERROR] No JDK 8 found.
    echo.
    echo   Install JDK 8 and either:
    echo     - set the JAVA_HOME environment variable, or
    echo     - copy gradle.properties.example to gradle.properties
    echo       and set org.gradle.java.home to your JDK 8 path.
    echo.
    exit /b 1
)

set "CAND=%JDK%"
call :try_jdk "%CAND%"
if not defined JDK (
    echo [ERROR] Not a JDK 8: %CAND%
    exit /b 1
)

echo [OK] Using JDK 8: %JDK%
echo.
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%PATH%"

if /i "%TASK%"=="check" (
    call "%JDK%\bin\java.exe" -version
    echo.
    echo Environment ready. Run "quickstart.bat" to start Minecraft.
    exit /b 0
)

rem ---------- first-time Forge workspace setup ----------
set "GRADLE_HOME_LOCAL=%GRADLE_USER_HOME%"
if not defined GRADLE_HOME_LOCAL set "GRADLE_HOME_LOCAL=%USERPROFILE%\.gradle"
set "FORGE_MARKER=%GRADLE_HOME_LOCAL%\caches\minecraft\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9\userdev\dev.json"

if not exist "%FORGE_MARKER%" goto :first_setup
echo [OK] Forge workspace is already set up.
echo.
goto :task_dispatch

:first_setup
if /i "%TASK%"=="setup" goto :task_dispatch
echo [..] First run: setting up the Forge workspace.
echo      This downloads dependencies and decompiles Minecraft,
echo      and can take a while depending on your connection.
echo.
call gradlew.bat setupDecompWorkspace --no-daemon
if errorlevel 1 (
    echo.
    echo [ERROR] Forge workspace setup failed.
    exit /b 1
)
echo.

:task_dispatch
if /i "%TASK%"=="setup" goto :do_setup
if /i "%TASK%"=="build" goto :do_build
echo Starting Minecraft client...
call gradlew.bat runClient --no-daemon
exit /b %errorlevel%

:do_setup
echo Running Forge workspace setup...
call gradlew.bat setupDecompWorkspace --no-daemon
exit /b %errorlevel%

:do_build
echo Building the mod jar...
call gradlew.bat build --no-daemon
exit /b %errorlevel%

rem ============================================================
rem  :try_jdk <dir> - sets JDK if <dir>\bin\java.exe reports
rem  Java 8 (version string 1.8.x or 8.x); otherwise clears JDK.
rem ============================================================
:try_jdk
set "JDK=%~1"
if not exist "%JDK%\bin\java.exe" (
    set "JDK="
    exit /b 1
)
for /f "tokens=3" %%V in ('"%JDK%\bin\java.exe" -version 2^>^&1') do (
    set "JVER=%%~V"
    goto :version_got
)
:version_got
if "%JVER:~0,3%"=="1.8" exit /b 0
if "%JVER:~0,2%"=="8." exit /b 0
if "%JVER%"=="8" exit /b 0
set "JDK="
exit /b 1
