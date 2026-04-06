@echo off
setlocal enabledelayedexpansion

echo ========================================
echo NexAI Icon Generator
echo ========================================
echo.

REM Change to project root directory
cd /d "%~dp0\.."

echo Current directory: %CD%
echo.

REM Check if icon.png exists
if not exist "icon.png" (
    echo Error: icon.png not found in root directory!
    echo Please place icon.png in the project root directory.
    pause
    exit /b 1
)

REM Check if ImageMagick is installed
where magick >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: ImageMagick not found!
    echo.
    echo Please install ImageMagick:
    echo 1. Run the installer: D:\Downloads\ImageMagick-7.1.2-15-Q16-HDRI-x64-dll.exe
    echo 2. During installation, make sure to check "Add application directory to your system path"
    echo 3. After installation, restart your command prompt
    echo 4. Run this script again
    echo.
    echo Alternative: You can also install via winget:
    echo    winget install ImageMagick.ImageMagick
    echo.
    pause
    exit /b 1
)

echo Found icon.png
echo Generating Android launcher icons...
echo.

REM Define icon sizes for each density
set "mdpi=48"
set "hdpi=72"
set "xhdpi=96"
set "xxhdpi=144"
set "xxxhdpi=192"

REM Base path
set "base_path=android\app\src\main\res"

REM Generate icons for each density
for %%d in (mdpi hdpi xhdpi xxhdpi xxxhdpi) do (
    set "density=%%d"
    set "size=!%%d!"
    set "output_dir=%base_path%\mipmap-%%d"
    
    echo Generating mipmap-%%d: !size!x!size!px
    
    REM Create directory if not exists
    if not exist "!output_dir!" mkdir "!output_dir!"
    
    REM Remove old files to avoid conflicts
    if exist "!output_dir!\ic_launcher.png" del "!output_dir!\ic_launcher.png"
    if exist "!output_dir!\ic_launcher.xml" del "!output_dir!\ic_launcher.xml"
    
    REM Generate new icon
    magick convert "icon.png" -resize !size!x!size! "!output_dir!\ic_launcher.png"
    
    if !errorlevel! neq 0 (
        echo Error generating icon for %%d
        exit /b 1
    )
)

echo.
echo ========================================
echo Icons generated successfully!
echo ========================================
echo.
echo Generated icons:
for %%d in (mdpi hdpi xhdpi xxhdpi xxxhdpi) do (
    echo   - mipmap-%%d\ic_launcher.png
)
echo.
echo Note: Old XML vector icons have been removed to avoid conflicts.
echo PNG icons are now used as launcher icons.
echo.
echo You can now run: flutter build apk --release
echo.
pause
exit /b 0
