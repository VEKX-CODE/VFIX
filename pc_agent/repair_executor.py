import subprocess
import os
import sys

def execute_repair_command(cmd_key: str, extra_args=None):
    """
    Выполняет команды восстановления системы.
    Поддерживает:
      - 'sfc': sfc /scannow (проверка целостности системных файлов)
      - 'dism_check': DISM /Online /Cleanup-Image /CheckHealth
      - 'dism_restore': DISM /Online /Cleanup-Image /RestoreHealth
      - 'chkdsk_c': chkdsk C: /f /r (планирует проверку диска при ребуте)
      - 'clean_temp': очистка временных файлов %TEMP%
      - 'reboot_uefi': перезагрузка напрямую в UEFI/BIOS
      - 'reboot': обычная перезагрузка
      - 'shutdown': выключение
      - 'cmd_raw': выполнение произвольной команды от админа
    """
    result = {"command": cmd_key, "status": "running", "output": "", "returncode": 0}

    try:
        if cmd_key == "sfc":
            proc = subprocess.run(["sfc", "/scannow"], capture_output=True, text=True, timeout=600)
            result["output"] = proc.stdout
            result["returncode"] = proc.returncode

        elif cmd_key == "dism_restore":
            proc = subprocess.run(["DISM", "/Online", "/Cleanup-Image", "/RestoreHealth"], capture_output=True, text=True, timeout=900)
            result["output"] = proc.stdout
            result["returncode"] = proc.returncode

        elif cmd_key == "dism_check":
            proc = subprocess.run(["DISM", "/Online", "/Cleanup-Image", "/CheckHealth"], capture_output=True, text=True, timeout=120)
            result["output"] = proc.stdout
            result["returncode"] = proc.returncode

        elif cmd_key == "clean_temp":
            cleaned_count = 0
            temp_paths = [os.environ.get("TEMP", ""), os.environ.get("TMP", ""), r"C:\Windows\Temp"]
            for tp in temp_paths:
                if tp and os.path.exists(tp):
                    for root, dirs, files in os.walk(tp):
                        for f in files:
                            try:
                                os.remove(os.path.join(root, f))
                                cleaned_count += 1
                            except Exception:
                                pass
            result["output"] = f"Удалено временных файлов: {cleaned_count}"
            result["returncode"] = 0

        elif cmd_key == "reboot_uefi":
            proc = subprocess.run(["shutdown", "/r", "/fw", "/t", "3"], capture_output=True, text=True)
            result["output"] = "ПК перезагружается в UEFI/BIOS через 3 секунды..."
            result["returncode"] = proc.returncode

        elif cmd_key == "reboot":
            subprocess.run(["shutdown", "/r", "/t", "3"])
            result["output"] = "Перезагрузка через 3 секунды..."

        elif cmd_key == "shutdown":
            subprocess.run(["shutdown", "/s", "/t", "3"])
            result["output"] = "Выключение через 3 секунды..."

        elif cmd_key == "cmd_raw" and extra_args:
            proc = subprocess.run(extra_args, shell=True, capture_output=True, text=True, timeout=180)
            result["output"] = proc.stdout or proc.stderr
            result["returncode"] = proc.returncode

        else:
            result["status"] = "error"
            result["output"] = f"Неизвестная команда: {cmd_key}"
            result["returncode"] = -1

        result["status"] = "completed" if result["returncode"] == 0 else "failed"

    except subprocess.TimeoutExpired:
        result["status"] = "timeout"
        result["output"] = "Превышено время ожидания выполнения команды."
    except Exception as e:
        result["status"] = "error"
        result["output"] = str(e)

    return result
