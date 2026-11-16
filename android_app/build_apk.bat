@echo off
setlocal

set ANDROID_STUDIO_JBR=C:\Program Files\Android\Android Studio\jbr
set JAVA_HOME=%ANDROID_STUDIO_JBR%
set PATH=%JAVA_HOME%\bin;%PATH%

echo ==============================================================
echo                 СБОРКА VFIX ANDROID APK
echo ==============================================================

cd /d "%~dp0"

if not exist "gradlew.bat" (
    echo [i] Для первичной генерации gradle wrapper откройте проект в Android Studio:
    echo     Путь: c:\code\gemini\vfix\android_app
    echo     И нажмите Build -> Build APK.
    echo.
) else (
    echo Запуск компиляции APK...
    call gradlew.bat assembleDebug
)

pause
