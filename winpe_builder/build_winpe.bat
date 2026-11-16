@echo off
setlocal enabledelayedexpansion

echo ==============================================================
echo        VFIX WINPE BUILDER (СБОРКА ЗАГРУЗОЧНОГО ОБРАЗА)
echo ==============================================================
echo.
echo Этот скрипт собирает загрузочный ISO-образ VFIX_WinPE.iso,
echo который заливается в телефон и позволяет загружать любой ПК!
echo.

:: 1. Проверяем наличие Windows ADK (Deployment and Imaging Tools)
set ADK_PATH=C:\Program Files (x86)\Windows Kits\10\Assessment and Deployment Kit\Deployment Tools
set WINPE_ADK=C:\Program Files (x86)\Windows Kits\10\Assessment and Deployment Kit\Windows Preinstallation Environment

if not exist "%ADK_PATH%" (
    echo [!] Windows ADK не обнаружен по стандартному пути.
    echo     Для автоматической сборки официального WinPE установите:
    echo     1. Windows ADK
    echo     2. WinPE add-on for Windows ADK
    echo.
    echo [i] АЛЬТЕРНАТИВНЫЙ БЫСТРЫЙ СПОСОБ:
    echo     Если у вас есть любой готовый образ WinPE (например Sergei Strelec или Win10XPE):
    echo     1. Откройте ISO в UltraISO или AnyBurn.
    echo     2. Скопируйте папку VFIX с файлом VFIX_Agent.exe в корень диска.
    echo     3. Поместите winpeshl.ini в папку Windows\System32.
    echo     4. Сохраните ISO и закиньте на телефон!
    goto :END
)

echo [+] ADK найден. Подготовка структуры WinPE...
set PE_WORKDIR=C:\vfix_pe_temp
if exist "%PE_WORKDIR%" rd /s /q "%PE_WORKDIR%"

call "%ADK_PATH%\DandISetEnv.bat"
call copype amd64 "%PE_WORKDIR%"

echo [+] Монтирование boot.wim...
Dism /Mount-Image /ImageFile:"%PE_WORKDIR%\media\sources\boot.wim" /index:1 /MountDir:"%PE_WORKDIR%\mount"

echo [+] Внедрение VFIX Agent...
mkdir "%PE_WORKDIR%\mount\VFIX"
copy /y "..\pc_agent\dist\VFIX_Agent.exe" "%PE_WORKDIR%\mount\VFIX\"
copy /y "..\pc_agent\vfix_logo.jpg" "%PE_WORKDIR%\mount\VFIX\"

echo [+] Настройка автозапуска через winpeshl.ini...
copy /y "winpeshl.ini" "%PE_WORKDIR%\mount\Windows\System32\"

echo [+] Размонтирование и сохранение wim...
Dism /Unmount-Image /MountDir:"%PE_WORKDIR%\mount" /commit

echo [+] Создание загрузочного ISO...
MakeWinPEMedia /iso "%PE_WORKDIR%" "..\VFIX_WinPE.iso"

echo.
echo [V] Готово! Образ создан: c:\code\gemini\vfix\VFIX_WinPE.iso
echo Закиньте этот файл в память телефона (например в /sdcard/Download/).

:END
pause
