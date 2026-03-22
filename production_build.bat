@echo off
echo ==========================================
echo   ConsistencyGrid Wallpaper - PRODUCTION BUILD
echo   Target: 100k Users Release Candidate
echo ==========================================
echo.

REM 1. Setup Environment
echo [1/6] Configuring Production Environment...
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version >NUL 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java 17 not found. Please install JDK 17.
    pause
    exit /b 1
)

REM 2. Clean Project
echo [2/6] Cleaning Project (Ensuring fresh build)...
cd android
call .\gradlew.bat clean >NUL 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] Clean failed, but attempting to proceed...
)

REM 3. Build Release Bundle (optional, but good for verification)
REM For now we stick to Debug for user testing, but this script is ready for Release
echo [3/6] Building Application...
call .\gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo [ERROR] Build Failed! Check logs above.
    pause
    exit /b 1
)

REM 4. Install
echo [4/6] Installing on Device...
call .\gradlew.bat installDebug
if %errorlevel% neq 0 (
    echo [ERROR] Installation Failed! Check connection.
    pause
    exit /b 1
)

REM 5. Optimize ADB
echo [5/6] Optimizing Logcat...
adb logcat -c

REM 6. Launch
echo [6/6] Launching App...
adb shell am force-stop com.consistencygridwallpaper
adb shell am start -n com.consistencygridwallpaper/.MainActivity

echo.
echo ==========================================
echo   Build Successful! 🚀
echo   App should be running on phone.
echo ==========================================
echo.
echo To view logs:
echo adb logcat -s MainActivity:D WebInterface:D WallpaperWorker:D
echo.
pause
