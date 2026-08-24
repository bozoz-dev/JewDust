@echo off
cd /d "%~dp0"
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"

echo Building the JewDust client with Java 21...
call gradlew.bat clean build

echo.
if errorlevel 1 (
    echo The build failed. Leave this window open and send its output to Codex.
    pause
    exit /b 1
)

echo Build complete. The mod JAR is in:
echo %CD%\build\libs
echo.
dir /b "build\libs\*.jar"
pause
