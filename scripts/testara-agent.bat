@echo off
REM Testara Agent Windows wrapper
REM Installed by install.ps1 — do not edit manually.
REM The JAR path is baked in at install time.
REM To reinstall: iwr -useb https://github.com/ygrip/testara/releases/latest/download/install.ps1 | iex

setlocal
set JAR=%USERPROFILE%\.testara\testara-agent.jar

if not exist "%JAR%" (
    echo [testara-agent] ERROR: JAR not found at %JAR%
    echo [testara-agent] Run the installer: iwr -useb https://github.com/ygrip/testara/releases/latest/download/install.ps1 ^| iex
    exit /b 1
)

java -jar "%JAR%" %*
endlocal
