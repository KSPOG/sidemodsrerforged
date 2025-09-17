@echo off
setlocal

set "GRADLE_VERSION=7.6.3"
set "DISTRIBUTION_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR%"=="" set "SCRIPT_DIR=.\"
set "WRAPPER_DIR=%SCRIPT_DIR%.gradle-wrapper"
set "DIST_ZIP=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_HOME=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_LAUNCHER=%GRADLE_HOME%\bin\gradle.bat"

if not exist "%WRAPPER_DIR%" (
    mkdir "%WRAPPER_DIR%"
    if errorlevel 1 (
        echo Failed to create wrapper cache directory "%WRAPPER_DIR%".
        exit /b 1
    )
)

where powershell >nul 2>nul
if errorlevel 1 (
    echo PowerShell is required to bootstrap Gradle automatically.
    echo Download Gradle %GRADLE_VERSION% manually from:
    echo   %DISTRIBUTION_URL%
    exit /b 1
)

if not exist "%DIST_ZIP%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "try {Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%DIST_ZIP%' -UseBasicParsing} catch {Write-Error $_; exit 1}"
    if errorlevel 1 (
        echo Failed to download Gradle distribution.
        exit /b 1
    )
)

if not exist "%GRADLE_LAUNCHER%" (
    echo Extracting Gradle %GRADLE_VERSION%...
    powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "try {Expand-Archive -LiteralPath '%DIST_ZIP%' -DestinationPath '%WRAPPER_DIR%' -Force} catch {Write-Error $_; exit 1}"
    if errorlevel 1 (
        echo Failed to extract Gradle distribution.
        exit /b 1
    )
)

if not exist "%GRADLE_LAUNCHER%" (
    echo Gradle launcher not found at "%GRADLE_LAUNCHER%".
    exit /b 1
)

call "%GRADLE_LAUNCHER%" %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
