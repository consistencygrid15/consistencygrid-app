@echo off
echo ==========================================
echo   ConsistencyGrid Wallpaper App Installer
echo ==========================================
echo.
echo 1. Setting up Environment...
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JDK at: %JAVA_HOME%

echo.
echo 2. Checking for connected devices...
cd android
call .\gradlew.bat installDebug
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Build failed or no device found!
    echo Please make sure your phone is connected and USB Debugging is enabled.
    pause
    exit /b %errorlevel%
)

echo.
echo 3. Launching App...
call ..\..\..\platform-tools\adb shell am start -n com.consistencygridwallpaper/.MainActivity 2>nul
if %errorlevel% neq 0 (
    echo [WARNING] Could not launch app automatically. Please open "ConsistencyGrid" on your phone.
    call adb shell am start -n com.consistencygridwallpaper/.MainActivity
)

echo.
echo [SUCCESS] App installed and launched!
pause
