import json
import subprocess
import psutil
import platform
import os
import winreg

def get_system_summary():
    """Собирает общую информацию о системе и ресурсах."""
    try:
        mem = psutil.virtual_memory()
        cpu_usage = psutil.cpu_percent(interval=0.5)
        boot_time = psutil.boot_time()
        
        disks = []
        for part in psutil.disk_partitions(all=False):
            try:
                usage = psutil.disk_usage(part.mountpoint)
                disks.append({
                    "device": part.device,
                    "mountpoint": part.mountpoint,
                    "fstype": part.fstype,
                    "total_gb": round(usage.total / (1024**3), 2),
                    "free_gb": round(usage.free / (1024**3), 2),
                    "percent_used": usage.percent
                })
            except Exception:
                pass

        return {
            "os": platform.system(),
            "os_release": platform.release(),
            "os_version": platform.version(),
            "arch": platform.machine(),
            "cpu_percent": cpu_usage,
            "ram_total_gb": round(mem.total / (1024**3), 2),
            "ram_used_gb": round(mem.used / (1024**3), 2),
            "ram_percent": mem.percent,
            "disks": disks
        }
    except Exception as e:
        return {"error": str(e)}

def get_smart_status():
    """Получает статус дисков через PowerShell WMI / MSFT_PhysicalDisk."""
    ps_cmd = """
    Get-PhysicalDisk | Select-Object DeviceId, FriendlyName, MediaType, OperationalStatus, HealthStatus, Size | ConvertTo-Json
    """
    try:
        res = subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, text=True, timeout=10)
        if res.returncode == 0 and res.stdout.strip():
            return json.loads(res.stdout)
    except Exception as e:
        return [{"error": f"Failed to query disk health: {e}"}]
    return []

def get_recent_event_errors(limit=25):
    """Считывает последние критические ошибки и предупреждения из Event Viewer (System & Application)."""
    ps_cmd = f"""
    Get-WinEvent -FilterHashtable @{{LogName=@('System','Application'); Level=1,2; StartTime=(Get-Date).AddDays(-3)}} -MaxEvents {limit} -ErrorAction SilentlyContinue | 
    Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message | ConvertTo-Json
    """
    try:
        res = subprocess.run(["powershell", "-NoProfile", "-Command", ps_cmd], capture_output=True, text=True, timeout=15)
        if res.returncode == 0 and res.stdout.strip():
            data = json.loads(res.stdout)
            if isinstance(data, dict):
                data = [data]
            return data
    except Exception as e:
        return [{"error": f"Failed to get events: {e}"}]
    return []

def get_bsod_dumps():
    """Проверяет наличие дампов памяти (BSOD: MEMORY.DMP и Minidump)."""
    dump_folder = r"C:\Windows\Minidump"
    mem_dump = r"C:\Windows\MEMORY.DMP"
    dumps = []

    if os.path.exists(mem_dump):
        dumps.append({
            "type": "KernelDump",
            "path": mem_dump,
            "size_mb": round(os.path.getsize(mem_dump) / (1024**2), 2)
        })

    if os.path.exists(dump_folder):
        try:
            for f in os.listdir(dump_folder):
                if f.endswith(".dmp"):
                    fp = os.path.join(dump_folder, f)
                    dumps.append({
                        "type": "Minidump",
                        "path": fp,
                        "size_kb": round(os.path.getsize(fp) / 1024, 2)
                    })
        except Exception:
            pass

    return dumps

def collect_full_diagnostic_report():
    """Формирует единый диагностический пакет для отправки на Android и в OpenRouter AI."""
    return {
        "status": "success",
        "system_summary": get_system_summary(),
        "smart_status": get_smart_status(),
        "event_errors": get_recent_event_errors(limit=20),
        "bsod_dumps": get_bsod_dumps()
    }

if __name__ == "__main__":
    report = collect_full_diagnostic_report()
    print(json.dumps(report, indent=2, ensure_ascii=False))
