@echo off
setlocal enabledelayedexpansion

rem ============================================================================
rem  BookstoreBilling - Standalone Windows Packaging Script (JDK 21)
rem ============================================================================

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

set APP_NAME=BookstoreBilling
set APP_MAIN_CLASS=com.example.t.RunFX
set APP_VERSION=1.2.0
set VENDOR=BookstoreBilling
set DESCRIPTION=Bookstore and Stationery Billing POS Application
set ICON_FILE=%SCRIPT_DIR%icon.ico
set TARGET_DIR=%SCRIPT_DIR%target
set LIBS_DIR=%TARGET_DIR%\libs
set RUNTIME_DIR=%TARGET_DIR%\runtime
set DIST_DIR=%TARGET_DIR%\dist
set DIST_APP_DIR=%DIST_DIR%\%APP_NAME%
set OUTPUT_EXE=%DIST_APP_DIR%\%APP_NAME%.exe

set JLINK_MODULES=java.base,java.desktop,java.sql,java.xml,java.logging,java.management,java.naming,java.net.http,java.scripting,jdk.unsupported,jdk.crypto.ec

set DRY_RUN=0
set SKIP_BUILD=0
set CLEAN=0

:parse_args
if "%~1"=="" goto after_args
if /i "%~1"=="/dry-run" set DRY_RUN=1
if /i "%~1"=="--dry-run" set DRY_RUN=1
if /i "%~1"=="/dryrun" set DRY_RUN=1
if /i "%~1"=="-dryrun" set DRY_RUN=1
if /i "%~1"=="-DryRun" set DRY_RUN=1
if /i "%~1"=="/skip-build" set SKIP_BUILD=1
if /i "%~1"=="--skip-build" set SKIP_BUILD=1
if /i "%~1"=="/skipbuild" set SKIP_BUILD=1
if /i "%~1"=="-skipbuild" set SKIP_BUILD=1
if /i "%~1"=="-SkipBuild" set SKIP_BUILD=1
if /i "%~1"=="/clean" set CLEAN=1
if /i "%~1"=="--clean" set CLEAN=1
if /i "%~1"=="-clean" set CLEAN=1
if /i "%~1"=="-Clean" set CLEAN=1
if /i "%~1"=="/?" goto show_help
if /i "%~1"=="/help" goto show_help
if /i "%~1"=="--help" goto show_help
if /i "%~1"=="-help" goto show_help
if /i "%~1"=="-h" goto show_help
shift
goto parse_args

:show_help
echo Usage: package.bat [/dry-run] [/skip-build] [/clean] [/help]
echo.
echo Options:
echo   /dry-run      Validate environment, paths, and toolchain without modifying files.
echo   /skip-build   Skip Maven packaging phase if target\libs already exists.
echo   /clean        Remove target\dist and target\runtime prior to packaging.
echo   /help         Display this help message.
exit /b 0

:after_args

echo ==========================================================
echo  BookstoreBilling - Standalone Windows Packaging (JDK 21)
echo ==========================================================

rem 1. Locate and Verify JDK 21 and Tools (jpackage, jlink)
set JPACKAGE_CMD=
set JLINK_CMD=

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE_CMD=%JAVA_HOME%\bin\jpackage.exe"
    if exist "%JAVA_HOME%\bin\jlink.exe" set "JLINK_CMD=%JAVA_HOME%\bin\jlink.exe"
)

if not defined JPACKAGE_CMD (
    for %%X in (jpackage.exe) do (
        if not "%%~$PATH:X"=="" set "JPACKAGE_CMD=%%~$PATH:X"
    )
)

if not defined JLINK_CMD (
    for %%X in (jlink.exe) do (
        if not "%%~$PATH:X"=="" set "JLINK_CMD=%%~$PATH:X"
    )
)

if not defined JPACKAGE_CMD (
    echo [ERROR] jpackage tool not found. Please ensure JDK 21 is installed and JAVA_HOME is configured.
    exit /b 1
)

if not defined JLINK_CMD (
    echo [ERROR] jlink tool not found. Please ensure JDK 21 is installed and JAVA_HOME is configured.
    exit /b 1
)

echo [OK] Tool jpackage: %JPACKAGE_CMD%
echo [OK] Tool jlink:    %JLINK_CMD%

rem 2. Locate Maven Wrapper
set MVNW_CMD=%SCRIPT_DIR%mvnw.cmd
if not exist "%MVNW_CMD%" (
    for %%X in (mvn.cmd mvn) do (
        if not "%%~$PATH:X"=="" set "MVNW_CMD=%%~$PATH:X"
    )
)

if not exist "%MVNW_CMD%" (
    echo [ERROR] Maven wrapper mvnw.cmd or system mvn not found.
    exit /b 1
)
echo [OK] Maven tool:    %MVNW_CMD%

rem Handle Clean switch
if "%CLEAN%"=="1" (
    echo.
    echo Cleaning distribution and runtime directories...
    if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
    if exist "%RUNTIME_DIR%" rmdir /s /q "%RUNTIME_DIR%"
)

rem Handle Dry-Run switch
if "%DRY_RUN%"=="1" (
    echo.
    echo [DRY RUN MODE ENABLED]
    echo Application Name:    %APP_NAME%
    echo Launcher Main Class: %APP_MAIN_CLASS%
    echo Application Version: %APP_VERSION%
    if exist "%ICON_FILE%" (
        echo Main Icon:           %ICON_FILE% [Found]
    ) else (
        echo Main Icon:           %ICON_FILE% [Missing]
    )
    echo Packaging Profile:   package-windows
    echo Target Libs Dir:     %LIBS_DIR%
    echo Custom JRE Dir:      %RUNTIME_DIR%
    echo Output Dist Dir:     %DIST_APP_DIR%
    echo JLink Modules:       %JLINK_MODULES%
    echo.
    echo Planned Execution Steps:
    echo  1. "%MVNW_CMD%" package -DskipTests -Ppackage-windows
    echo  2. "%JLINK_CMD%" --no-header-files --no-man-pages --strip-debug --add-modules %JLINK_MODULES% --output "%RUNTIME_DIR%"
    echo  3. "%JPACKAGE_CMD%" --type app-image --name %APP_NAME% --dest "%DIST_DIR%" --input "%LIBS_DIR%" --main-jar t-1.2.0.jar --main-class %APP_MAIN_CLASS% --runtime-image "%RUNTIME_DIR%"
    echo.
    echo [OK] Dry-run validation successful. Environment and parameters are valid.
    exit /b 0
)

rem 3. Build project JAR and gather dependencies with Maven
if "%SKIP_BUILD%"=="0" (
    echo.
    echo [1/3] Building application and gathering dependencies via Maven...
    call "%MVNW_CMD%" package -DskipTests -Ppackage-windows
    if errorlevel 1 (
        echo [ERROR] Maven packaging build failed.
        exit /b 1
    )
) else (
    echo.
    echo [1/3] Skipping Maven build: using existing target\libs...
)

rem Locate main application JAR
set MAIN_JAR=
for %%F in ("%LIBS_DIR%\t-*.jar") do (
    set "MAIN_JAR=%%~nxF"
    goto found_jar
)
for %%F in ("%TARGET_DIR%\t-*.jar") do (
    copy /y "%%F" "%LIBS_DIR%\" >nul
    set "MAIN_JAR=%%~nxF"
    goto found_jar
)

:found_jar
if not defined MAIN_JAR (
    echo [ERROR] Main application JAR not found in %LIBS_DIR% or %TARGET_DIR%.
    exit /b 1
)
echo [OK] Main Application JAR: %MAIN_JAR%

rem 4. Generate Minimized Custom JDK 21 Runtime JRE using jlink
echo.
echo [2/3] Generating minimized JDK 21 runtime JRE via jlink...
if exist "%RUNTIME_DIR%" rmdir /s /q "%RUNTIME_DIR%"

"%JLINK_CMD%" --no-header-files --no-man-pages --strip-debug --add-modules %JLINK_MODULES% --output "%RUNTIME_DIR%"
if errorlevel 1 (
    echo [ERROR] jlink execution failed.
    exit /b 1
)
echo [OK] Bundled runtime created at: %RUNTIME_DIR%

rem 5. Invoke jpackage to generate standalone app-image
echo.
echo [3/3] Creating standalone application image via jpackage...
if exist "%DIST_APP_DIR%" rmdir /s /q "%DIST_APP_DIR%"

set ICON_OPT=
if exist "%ICON_FILE%" set "ICON_OPT=--icon "%ICON_FILE%""

"%JPACKAGE_CMD%" --type app-image --name %APP_NAME% --dest "%DIST_DIR%" --input "%LIBS_DIR%" --main-jar "%MAIN_JAR%" --main-class %APP_MAIN_CLASS% --runtime-image "%RUNTIME_DIR%" --app-version %APP_VERSION% --vendor "%VENDOR%" --description "%DESCRIPTION%" %ICON_OPT%
if errorlevel 1 (
    echo [ERROR] jpackage execution failed.
    exit /b 1
)

rem 6. Verify Output Artifacts
if not exist "%OUTPUT_EXE%" (
    echo [ERROR] Standalone executable was not generated at %OUTPUT_EXE%.
    exit /b 1
)

echo.
echo ==========================================================
echo  PACKAGING SUCCESSFUL!
echo ==========================================================
echo Executable:       %OUTPUT_EXE%
echo Bundled Runtime:  %DIST_APP_DIR%\runtime
echo App Dependencies: %DIST_APP_DIR%\app
echo Launch command:   "%OUTPUT_EXE%"
echo ==========================================================
exit /b 0
