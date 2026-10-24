<#
.SYNOPSIS
    Builds and packages BookstoreBilling as a standalone Windows application image.

.DESCRIPTION
    Uses OpenJDK 21 tools (jlink and jpackage) along with the Maven 'package-windows'
    profile to bundle a zero-dependency offline desktop application containing:
    - Standalone executable (BookstoreBilling.exe)
    - Bundled minimized JDK 21 runtime JRE (no preinstalled Java needed)
    - Application modules, dependencies, and configuration

.PARAMETER DryRun
    Validates prerequisites and prints planned packaging actions without executing builds.

.PARAMETER SkipBuild
    Skips Maven build if target/libs and main jar already exist.

.PARAMETER Clean
    Cleans target/dist and target/runtime prior to packaging.

.EXAMPLE
    .\package.ps1
    Builds the complete standalone package.

.EXAMPLE
    .\package.ps1 -DryRun
    Verifies toolchain and configuration without building.
#>

[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$SkipBuild,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $ScriptDir

$AppName = "BookstoreBilling"
$AppMainClass = "com.example.t.RunFX"
$AppVersion = "1.2.0"
$Vendor = "BookstoreBilling"
$Description = "Bookstore & Stationery Billing POS Application"
$IconFile = Join-Path $ScriptDir "icon.ico"
$TargetDir = Join-Path $ScriptDir "target"
$LibsDir = Join-Path $TargetDir "libs"
$RuntimeDir = Join-Path $TargetDir "runtime"
$DistDir = Join-Path $TargetDir "dist"
$DistAppDir = Join-Path $DistDir $AppName
$OutputExe = Join-Path $DistAppDir "$AppName.exe"

$JlinkModules = @(
    "java.base",
    "java.desktop",
    "java.sql",
    "java.xml",
    "java.logging",
    "java.management",
    "java.naming",
    "java.net.http",
    "java.scripting",
    "jdk.unsupported",
    "jdk.crypto.ec"
) -join ","

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " BookstoreBilling - Standalone Windows Packaging (JDK 21)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Locate and Verify JDK 21 and Tools
$JavaBin = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin")) {
    $JavaBin = "$env:JAVA_HOME\bin"
}

function Find-Tool {
    param([string]$toolName)
    if ($JavaBin -and (Test-Path "$JavaBin\$toolName.exe")) {
        return "$JavaBin\$toolName.exe"
    }
    $cmd = Get-Command $toolName -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    return $null
}

$JpackageCmd = Find-Tool "jpackage"
$JlinkCmd = Find-Tool "jlink"
$JavaCmd = Find-Tool "java"

if (-not $JpackageCmd) {
    Write-Error "ERROR: jpackage tool not found. Please ensure JDK 21 is installed and JAVA_HOME is configured."
    exit 1
}

if (-not $JlinkCmd) {
    Write-Error "ERROR: jlink tool not found. Please ensure JDK 21 is installed and JAVA_HOME is configured."
    exit 1
}

Write-Host "[OK] Tool jpackage: $JpackageCmd" -ForegroundColor Green
Write-Host "[OK] Tool jlink:    $JlinkCmd" -ForegroundColor Green

# 2. Locate Maven Wrapper
$MvnwCmd = Join-Path $ScriptDir "mvnw.cmd"
if (-not (Test-Path $MvnwCmd)) {
    $mvnCmd = Get-Command "mvn" -ErrorAction SilentlyContinue
    if ($mvnCmd) {
        $MvnwCmd = $mvnCmd.Source
    } else {
        Write-Error "ERROR: Maven wrapper (mvnw.cmd) or system mvn not found."
        exit 1
    }
}
Write-Host "[OK] Maven tool:    $MvnwCmd" -ForegroundColor Green

# Handle Clean switch
if ($Clean) {
    Write-Host "`nCleaning distribution and runtime directories..." -ForegroundColor Yellow
    if (Test-Path $DistDir) { Remove-Item -Recurse -Force $DistDir }
    if (Test-Path $RuntimeDir) { Remove-Item -Recurse -Force $RuntimeDir }
}

# Handle Dry-Run switch
if ($DryRun) {
    Write-Host "`n[DRY RUN MODE ENABLED]" -ForegroundColor Magenta
    Write-Host "Application Name:    $AppName"
    Write-Host "Launcher Main Class: $AppMainClass"
    Write-Host "Application Version: $AppVersion"
    Write-Host "Main Icon:           $IconFile (Exists: $(Test-Path $IconFile))"
    Write-Host "Packaging Profile:   package-windows"
    Write-Host "Target Libs Dir:     $LibsDir"
    Write-Host "Custom JRE Dir:      $RuntimeDir"
    Write-Host "Output Dist Dir:     $DistAppDir"
    Write-Host "JLink Modules:       $JlinkModules"
    Write-Host "`nPlanned Execution Steps:"
    Write-Host " 1. $MvnwCmd package -DskipTests -Ppackage-windows"
    Write-Host " 2. $JlinkCmd --no-header-files --no-man-pages --strip-debug --add-modules $JlinkModules --output $RuntimeDir"
    Write-Host " 3. $JpackageCmd --type app-image --name $AppName --dest $DistDir --input $LibsDir --main-jar t-1.2.0.jar --main-class $AppMainClass --runtime-image $RuntimeDir"
    Write-Host "`n[OK] Dry-run validation successful. Environment and parameters are valid." -ForegroundColor Green
    exit 0
}

# 3. Build project JAR and gather dependencies with Maven
if (-not $SkipBuild -or -not (Test-Path $LibsDir)) {
    Write-Host "`n[1/3] Building application and gathering dependencies via Maven..." -ForegroundColor Cyan
    & $MvnwCmd package -DskipTests -Ppackage-windows
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven build failed with exit code $LASTEXITCODE."
        exit $LASTEXITCODE
    }
} else {
    Write-Host "`n[1/3] Skipping Maven build (-SkipBuild specified, using existing target/libs)..." -ForegroundColor Yellow
}

# Clean legacy snapshot jars if target has newer version
Get-ChildItem -Path $LibsDir -Filter "t-1.0-SNAPSHOT.jar" -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

# Locate main application JAR (prefer most recent)
$MainJar = Get-ChildItem -Path $LibsDir -Filter "t-*.jar" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $MainJar) {
    # If not in libs, check target root
    $TargetJar = Get-ChildItem -Path $TargetDir -Filter "t-*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*sources*" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($TargetJar) {
        Copy-Item $TargetJar.FullName -Destination $LibsDir -Force
        $MainJar = Get-ChildItem -Path $LibsDir -Filter $TargetJar.Name | Select-Object -First 1
    }
}

if (-not $MainJar) {
    Write-Error "ERROR: Main application jar not found in $LibsDir or $TargetDir."
    exit 1
}
Write-Host "[OK] Main Application JAR: $($MainJar.Name)" -ForegroundColor Green

# 4. Generate Minimized Custom JDK 21 Runtime JRE using jlink
Write-Host "`n[2/3] Generating minimized JDK 21 runtime JRE via jlink..." -ForegroundColor Cyan
if (Test-Path $RuntimeDir) {
    Remove-Item -Recurse -Force $RuntimeDir
}

$JlinkArgs = @(
    "--no-header-files",
    "--no-man-pages",
    "--strip-debug",
    "--add-modules", $JlinkModules,
    "--output", $RuntimeDir
)

& $JlinkCmd @JlinkArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "jlink failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}
Write-Host "[OK] Bundled runtime created at: $RuntimeDir" -ForegroundColor Green

# 5. Invoke jpackage to generate standalone app-image
Write-Host "`n[3/3] Creating standalone application image via jpackage..." -ForegroundColor Cyan
if (Test-Path $DistAppDir) {
    Remove-Item -Recurse -Force $DistAppDir
}

$JpackageArgs = @(
    "--type", "app-image",
    "--name", $AppName,
    "--dest", $DistDir,
    "--input", $LibsDir,
    "--main-jar", $MainJar.Name,
    "--main-class", $AppMainClass,
    "--runtime-image", $RuntimeDir,
    "--app-version", $AppVersion,
    "--vendor", $Vendor,
    "--description", $Description
)

if (Test-Path $IconFile) {
    $JpackageArgs += @("--icon", $IconFile)
}

& $JpackageCmd @JpackageArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "jpackage failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}

# 6. Verify Output Artifacts
if (-not (Test-Path $OutputExe)) {
    Write-Error "ERROR: Standalone executable was not generated at $OutputExe."
    exit 1
}

$ExeSize = (Get-Item $OutputExe).Length / 1KB
Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " PACKAGING SUCCESSFUL!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Executable:       $OutputExe ($([math]::Round($ExeSize, 1)) KB)" -ForegroundColor White
Write-Host "Bundled Runtime:  $(Join-Path $DistAppDir 'runtime')" -ForegroundColor White
Write-Host "App Dependencies: $(Join-Path $DistAppDir 'app')" -ForegroundColor White
Write-Host "Launch command:   & `"$OutputExe`"" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Green
exit 0
