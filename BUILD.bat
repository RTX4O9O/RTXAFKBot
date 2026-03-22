@echo off
title FakePlayerPlugin Builder
color 0A
echo.
echo ===============================================
echo    FakePlayerPlugin Build Script
echo ===============================================
echo.
echo [INFO] Starting build process...
echo [INFO] This will compile, obfuscate, and deploy the plugin
echo.

REM Change to the script's directory (project root)
cd /d "%~dp0"

REM Check if Maven is available
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Maven (mvn) not found in PATH
    echo [ERROR] Please install Maven and add it to your PATH
    echo.
    pause
    exit /b 1
)

REM Run the Maven build
echo [INFO] Running: mvn -DskipTests clean package
echo.
mvn -DskipTests clean package

REM Check if build was successful
if %errorlevel% equ 0 (
    echo.
    echo ===============================================
    echo    BUILD SUCCESSFUL!
    echo ===============================================
    echo.
    for %%F in ("target\fpp-*-obfuscated.jar") do (
        echo [SUCCESS] Obfuscated JAR: %%F
        echo [INFO] File size: %%~zF bytes
    )
    echo.
    echo [DEPLOY]  Obfuscated JAR auto-copied to: %%USERPROFILE%%\Desktop\dmc\plugins\fpp.jar
    echo [DEBUG]   Unobfuscated JAR available at: target\fpp-1.4.22.jar
    echo.
    echo [TIP] To deploy to a different server folder, run:
    echo       mvn -DskipTests clean package -Ddeploy.dir=C:\path\to\server\plugins
) else (
    echo.
    echo ===============================================
    echo    BUILD FAILED!
    echo ===============================================
    echo.
    echo [ERROR] Build failed with exit code %errorlevel%
    echo [ERROR] Check the Maven output above for details
)

echo.
echo Press any key to close this window...
pause >nul
