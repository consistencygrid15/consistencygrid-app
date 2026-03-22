@echo off
setlocal enabledelayedexpansion

echo ==========================================
echo   ConsistencyGrid Wallpaper - Build Tool
echo ==========================================
echo.

REM Set JAVA_HOME to the detected JDK
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [1/5] Environment Setup
echo       JAVA_HOME: %JAVA_HOME%
echo.

REM Check if Java is accessible
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found! Please install JDK 17.
    pause
    exit /b 1
)

echo [2/5] Checking for connected devices...
cd android
call adb devices
echo.

echo [3/5] Cleaning previous build...
call .\gradlew.bat clean
echo.

echo [4/5] Building and installing debug APK...
call .\gradlew.bat installDebug --stacktrace

if %errorlevel% neq 0 (
    echo.
    echo ========================================
    echo [ERROR] Build or installation failed!
    echo ========================================
    echo.
    echo Common fixes:
    echo 1. Make sure your phone is connected via USB
    echo 2. Enable USB Debugging on your phone
    echo 3. Accept the USB debugging prompt on your phone
    echo 4. Try running: adb kill-server ^&^& adb start-server
    echo.
    pause
    exit /b %errorlevel%
)

echo.
echo [5/5] Launching app on device...
call adb shell am start -n com.consistencygridwallpaper/.MainActivity

if %errorlevel% neq 0 (
    echo [WARNING] Could not launch app automatically.
    echo Please open "ConsistencyGrid" manually on your phone.
) else (
    echo.
    echo ========================================
    echo [SUCCESS] App installed and launched!
    echo ========================================
)

echo.
echo To view logs, run: adb logcat -s MainActivity:D WallpaperWorker:D WebInterface:D
echo.
pause
