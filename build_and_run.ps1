# ConsistencyGrid Wallpaper - Build and Run Script (PowerShell)
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  ConsistencyGrid Wallpaper - Build Tool" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Set JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "[1/5] Environment Setup" -ForegroundColor Yellow
Write-Host "      JAVA_HOME: $env:JAVA_HOME"
Write-Host ""

# Check Java
Write-Host "[2/5] Checking Java..." -ForegroundColor Yellow
java -version
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Java not found!" -ForegroundColor Red
    pause
    exit 1
}
Write-Host ""

# Check devices
Write-Host "[3/5] Checking for connected devices..." -ForegroundColor Yellow
adb devices
Write-Host ""

# Clean
Write-Host "[4/5] Cleaning previous build..." -ForegroundColor Yellow
Set-Location android
.\gradlew.bat clean
Write-Host ""

# Build and install
Write-Host "[5/5] Building and installing..." -ForegroundColor Yellow
.\gradlew.bat installDebug --stacktrace

if ($LASTEXITCODE -ne 0) {
    Write-Host "" 
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "[ERROR] Build or installation failed!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Common fixes:" -ForegroundColor Yellow
    Write-Host "1. Make sure your phone is connected via USB"
    Write-Host "2. Enable USB Debugging on your phone"
    Write-Host "3. Accept the USB debugging prompt on your phone"
    Write-Host ""
    pause
    exit $LASTEXITCODE
}

# Launch app
Write-Host ""
Write-Host "[6/6] Launching app..." -ForegroundColor Yellow
adb shell am start -n com.consistencygridwallpaper/.MainActivity

if ($LASTEXITCODE -ne 0) {
    Write-Host "[WARNING] Could not launch app automatically." -ForegroundColor Yellow
    Write-Host "Please open 'ConsistencyGrid' manually on your phone."
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "[SUCCESS] App installed and launched!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
}

Write-Host ""
Write-Host "To view logs, run: adb logcat -s MainActivity:D WallpaperWorker:D" -ForegroundColor Cyan
Write-Host ""
pause
