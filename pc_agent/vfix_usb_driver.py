import time
import threading
import pythoncom
import wmi
import win32gui
import win32con
from vfix_overlay import VfixLogStreamUI
from tcp_server import VfixTcpServer

TARGET_VID = "VID_2717"

class VfixUsbWatcher:
    def __init__(self):
        self.overlay = None
        self.server = None
        self.is_connected = False
        self.running = True

    def _ui_callback(self, kind, message):
        if not self.overlay:
            return
        if kind == "log":
            self.overlay.add_log(message)
        elif kind == "status":
            self.overlay.set_status(message)
        elif kind == "progress":
            try:
                parts = message.split("|", 1)
                self.overlay.set_progress(float(parts[0]), parts[1] if len(parts) > 1 else "")
            except Exception:
                pass

    def start_overlay(self):
        if not self.overlay or not self.overlay.running:
            self.overlay = VfixLogStreamUI()
            if not self.server:
                self.server = VfixTcpServer(host="0.0.0.0", port=8888, ui_callback=self._ui_callback)
                self.server.start()
            self.overlay.start()

    def stop_overlay(self):
        if self.overlay and self.overlay.running:
            self.overlay.stop()
            self.overlay = None

    def run(self):
        print(f"[*] VFIX USB Драйвер-демон запущен. Отслеживаем подключение {TARGET_VID}...")
        pythoncom.CoInitialize()
        c = wmi.WMI()
        was_connected = False

        while self.running:
            connected = False
            try:
                for device in c.Win32_PnPEntity():
                    dev_id = getattr(device, "DeviceID", "") or ""
                    if TARGET_VID in dev_id.upper():
                        connected = True
                        break
            except Exception:
                pass

            if connected and not was_connected:
                print("[+] Телефон подключен! Мгновенный запуск экрана VFIX...")
                was_connected = True
                self.is_connected = True
                threading.Thread(target=self.start_overlay, daemon=True).start()

            elif not connected and was_connected:
                print("[-] Телефон отключен. Сворачивание экрана VFIX...")
                was_connected = False
                self.is_connected = False
                self.stop_overlay()

            time.sleep(1.0)

if __name__ == "__main__":
    watcher = VfixUsbWatcher()
    watcher.run()
