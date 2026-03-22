@echo off
echo ==========================================
echo   Fix JAVA_HOME Environment Variable
echo ==========================================
echo.

set "NEW_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"

echo Current JAVA_HOME: %JAVA_HOME%
echo New JAVA_HOME: %NEW_JAVA_HOME%
echo.

echo This will update your system JAVA_HOME environment variable.
echo You may need to restart your terminal after this.
echo.
pause

REM Set for current session
set "JAVA_HOME=%NEW_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM Set permanently (requires admin rights)
setx JAVA_HOME "%NEW_JAVA_HOME%" /M

if %errorlevel% equ 0 (
    echo.
    echo [SUCCESS] JAVA_HOME updated successfully!
    echo Please restart your terminal for changes to take effect.
) else (
    echo.
    echo [WARNING] Could not set system variable (requires admin rights)
    echo JAVA_HOME is set for this session only.
    echo.
    echo To set permanently, run this script as Administrator.
)

echo.
pause
