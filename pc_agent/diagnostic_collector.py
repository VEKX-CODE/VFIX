import json
import subprocess
import psutil
import platform
import os
import glob
import re

def run_ps(cmd: str, timeout: int = 15):
    """Безопасный запуск PowerShell команд с возвратом распарсенного JSON или текста."""
    try:
        proc = subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd],
            capture_output=True,
            text=True,
            timeout=timeout
        )
        out = proc.stdout.strip()
        if proc.returncode == 0 and out:
            try:
                return json.loads(out)
            except Exception:
                return out
    except Exception as e:
        return {"error": str(e)}
    return None

def get_hardware_passport():
    """Сбор паспорта железа: материнка, BIOS, процессор, RAM планки, видеокарта."""
    passport = {
        "motherboard": {},
        "cpu": {},
        "ram_modules": [],
        "gpu": []
    }
    
    # Материнская плата и BIOS
    ps_mb = """
    $mb = Get-CimInstance Win32_BaseBoard | Select-Object Manufacturer, Product, SerialNumber
    $bios = Get-CimInstance Win32_BIOS | Select-Object Manufacturer, SMBIOSBIOSVersion, ReleaseDate
    @{
        Motherboard = "$($mb.Manufacturer) $($mb.Product) (S/N: $($mb.SerialNumber))";
        Bios = "$($bios.Manufacturer) v$($bios.SMBIOSBIOSVersion) [$($bios.ReleaseDate)]"
    } | ConvertTo-Json
    """
    mb_res = run_ps(ps_mb, timeout=8)
    if isinstance(mb_res, dict):
        passport["motherboard"] = mb_res

    # Процессор
    ps_cpu = """
    Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors, MaxClockSpeed, SocketDesignation | ConvertTo-Json
    """
    cpu_res = run_ps(ps_cpu, timeout=8)
    if cpu_res:
        passport["cpu"] = cpu_res if isinstance(cpu_res, dict) else (cpu_res[0] if isinstance(cpu_res, list) else {})

    # Оперативная память (планки в слотах)
    ps_ram = """
    Get-CimInstance Win32_PhysicalMemory | Select-Object DeviceLocator, Manufacturer, PartNumber, Speed, @{N='CapacityGB'; E={[math]::Round($_.Capacity/1GB, 2)}} | ConvertTo-Json
    """
    ram_res = run_ps(ps_ram, timeout=8)
    if ram_res:
        passport["ram_modules"] = [ram_res] if isinstance(ram_res, dict) else ram_res

    # Видеокарты
    ps_gpu = """
    Get-CimInstance Win32_VideoController | Select-Object Name, DriverVersion, Status, @{N='AdapterRAM_GB'; E={[math]::Round($_.AdapterRAM/1GB, 2)}} | ConvertTo-Json
    """
    gpu_res = run_ps(ps_gpu, timeout=8)
    if gpu_res:
        passport["gpu"] = [gpu_res] if isinstance(gpu_res, dict) else gpu_res

    return passport

def get_deep_disk_smart():
    """Глубокий анализ накопителей: модель, состояние здоровья, износ, сбойные сектора."""
    ps_disks = """
    Get-PhysicalDisk | Select-Object DeviceId, FriendlyName, MediaType, BusType, OperationalStatus, HealthStatus,
    @{N='SizeGB'; E={[math]::Round($_.Size/1GB, 2)}},
    AllocatedSize, SpindleSpeed | ConvertTo-Json
    """
    disks = run_ps(ps_disks, timeout=10)
    if not disks:
        return []
    if isinstance(disks, dict):
        disks = [disks]

    # Дополнительная проверка дисковых предупреждений NTFS / Disk в Event Log
    ps_disk_events = """
    Get-WinEvent -FilterHashtable @{LogName='System'; ProviderName=@('disk','ntfs','stornvme'); Level=1,2,3; StartTime=(Get-Date).AddDays(-7)} -MaxEvents 10 -ErrorAction SilentlyContinue |
    Select-Object TimeCreated, ProviderName, Id, Message | ConvertTo-Json
    """
    disk_events = run_ps(ps_disk_events, timeout=8)
    if disk_events:
        if isinstance(disk_events, dict):
            disk_events = [disk_events]
        for d in disks:
            d["related_events"] = disk_events

    return disks

def get_bsod_details():
    """Анализ дампов синих экранов BSOD с поиском причины и виновного драйвера."""
    dumps = []
    dump_files = []
    
    mem_dump = r"C:\Windows\MEMORY.DMP"
    if os.path.exists(mem_dump):
        dump_files.append((mem_dump, "FullKernelDump"))
        
    for f in glob.glob(r"C:\Windows\Minidump\*.dmp"):
        dump_files.append((f, "Minidump"))

    for path, dtype in dump_files[:5]:
        size_kb = round(os.path.getsize(path) / 1024, 2)
        dump_info = {
            "type": dtype,
            "path": path,
            "size_kb": size_kb,
            "file_time": os.path.getmtime(path),
            "bugcheck_code": "Unknown",
            "blamed_module": "Unknown"
        }
        
        # Пробуем вытащить BugCheck из файла minidump по сигнатуре
        try:
            with open(path, "rb") as bf:
                header = bf.read(4096)
                # Поиск текстовых строк драйверов в заголовке дампа
                drivers = re.findall(rb'[a-zA-Z0-9_\-]+\.sys', header)
                if drivers:
                    dump_info["suspected_drivers"] = list(set([d.decode('ascii', errors='ignore') for d in drivers[:4]]))
        except Exception:
            pass

        dumps.append(dump_info)

    # Заодно считываем записи Event ID 1001 (Windows Error Reporting / BugCheck)
    ps_bugcheck = """
    Get-WinEvent -FilterHashtable @{LogName='System'; Id=1001; StartTime=(Get-Date).AddDays(-14)} -MaxEvents 5 -ErrorAction SilentlyContinue |
    Select-Object TimeCreated, Message | ConvertTo-Json
    """
    bc_events = run_ps(ps_bugcheck, timeout=8)
    if bc_events:
        if isinstance(bc_events, dict):
            bc_events = [bc_events]
        for d in dumps:
            d["event_log_crash"] = bc_events

    return dumps

def get_critical_system_events(limit=40):
    """Сбор критических ошибок системных журналов за последнюю неделю."""
    ps_cmd = f"""
    Get-WinEvent -FilterHashtable @{{LogName=@('System','Application'); Level=1,2; StartTime=(Get-Date).AddDays(-7)}} -MaxEvents {limit} -ErrorAction SilentlyContinue |
    Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, @{{N='Message'; E={{$_.Message.Trim()}}}} | ConvertTo-Json
    """
    res = run_ps(ps_cmd, timeout=12)
    if not res:
        return []
    if isinstance(res, dict):
        return [res]
    return res

def collect_full_diagnostic_report():
    """Формирует глубокий и всесторонний отчёт о состоянии компьютера."""
    try:
        mem = psutil.virtual_memory()
        cpu_usage = psutil.cpu_percent(interval=0.3)
        
        partitions = []
        for part in psutil.disk_partitions(all=False):
            try:
                usage = psutil.disk_usage(part.mountpoint)
                partitions.append({
                    "drive": part.device,
                    "mount": part.mountpoint,
                    "fs": part.fstype,
                    "total_gb": round(usage.total / (1024**3), 2),
                    "free_gb": round(usage.free / (1024**3), 2),
                    "percent_used": usage.percent
                })
            except Exception:
                pass

        summary = {
            "os": f"{platform.system()} {platform.release()} (Build {platform.version()})",
            "arch": platform.machine(),
            "cpu_load_percent": cpu_usage,
            "ram_total_gb": round(mem.total / (1024**3), 2),
            "ram_used_gb": round(mem.used / (1024**3), 2),
            "ram_free_gb": round(mem.available / (1024**3), 2),
            "ram_percent": mem.percent,
            "partitions": partitions
        }
    except Exception as e:
        summary = {"error": str(e)}

    return {
        "status": "success",
        "system_summary": summary,
        "hardware_passport": get_hardware_passport(),
        "smart_status": get_deep_disk_smart(),
        "bsod_dumps": get_bsod_details(),
        "event_errors": get_critical_system_events(limit=30)
    }

if __name__ == "__main__":
    rep = collect_full_diagnostic_report()
    print(json.dumps(rep, indent=2, ensure_ascii=False))
