@echo off
echo ==========================================
echo   Environment Diagnostics
echo ==========================================
echo.

echo [1] Checking Java...
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version
echo.

echo [2] Checking ADB...
adb version
echo.

echo [3] Checking connected devices...
adb devices
echo.

echo [4] Checking Gradle...
cd android
call .\gradlew.bat --version
echo.

echo ==========================================
echo   Diagnostics Complete
echo ==========================================
pause
