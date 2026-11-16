@echo off
echo ==============================================
echo       СБОРКА VFIX PC AGENT В EXE
echo ==============================================

cd /d "%~dp0"

echo Установка зависимостей...
pip install -r requirements.txt

echo.
echo Компиляция в один файл...
pyinstaller --clean --noconsole --onefile ^
    --name "VFIX_Agent" ^
    --add-data "diagnostic_collector.py;." ^
    --add-data "repair_executor.py;." ^
    --add-data "tcp_server.py;." ^
    --add-data "vfix_overlay.py;." ^
    main_agent.py

echo.
echo ==============================================
echo Сборка завершена! Исполняемый файл находится в:
echo dist\VFIX_Agent.exe
echo ==============================================
pause
