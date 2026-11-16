import os
import sys
import time
import json
import subprocess
import threading
import tkinter as tk
from collections import deque
from PIL import Image, ImageTk

from diagnostic_collector import collect_full_diagnostic_report
from repair_executor import execute_repair_command

class VfixAdbCableBridge:
    def __init__(self, ui_callback):
        self.ui_callback = ui_callback
        self.running = True
        self.connected = False

    def start(self):
        threading.Thread(target=self._cable_loop, daemon=True).start()

    def _cable_loop(self):
        self.ui_callback("log", "[ADB PIPE] Запуск прямого аппаратного канала через кабель...")
        self.ui_callback("status", "[STANDBY] Ожидание подключения смартфона по кабелю...")

        while self.running:
            try:
                # Проверяем наличие телефона на проводе
                res = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=5)
                lines = [l.strip() for l in res.stdout.strip().split("\n") if "\tdevice" in l]

                if lines and not self.connected:
                    self.connected = True
                    device_id = lines[0].split("\t")[0]
                    self.ui_callback("status", f"[CONNECTED] Смартфон ({device_id}) подключен по кабелю!")
                    self.ui_callback("log", f"[HANDSHAKE] Физический USB-канал активен: {device_id}")
                    self.ui_callback("progress", "20|Сбор отчета...")

                    # Сразу запускаем сбор логов ПК и отправку в телефон
                    self._collect_and_push_report()

                elif not lines and self.connected:
                    self.connected = False
                    self.ui_callback("status", "[STANDBY] Смартфон отключен. Ожидание провода...")
                    self.ui_callback("log", "[-] Телефон отключен от USB.")
                    self.ui_callback("progress", "0|Ожидание")

                # Если телефон на связи — проверяем наличие входящих команд с телефона
                if self.connected:
                    self._check_incoming_commands()

            except Exception as e:
                pass

            time.sleep(1.2)

    def _collect_and_push_report(self):
        try:
            self.ui_callback("progress", "40|Сканирование дисков и SMART...")
            self.ui_callback("log", "Сбор S.M.A.R.T. физических накопителей...")
            report = collect_full_diagnostic_report()

            self.ui_callback("progress", "75|Анализ дампов BSOD...")
            self.ui_callback("log", "Проверка критических системных журналов...")

            report_json = json.dumps(report, ensure_ascii=False)
            
            # Сохраняем локально и пушим напрямую в защищенный пайп телефона
            tmp_local = r"c:\code\gemini\vfix\temp_report.json"
            with open(tmp_local, "w", encoding="utf-8") as f:
                f.write(report_json)

            self.ui_callback("progress", "90|Передача по кабелю...")
            subprocess.run(["adb", "push", tmp_local, "/data/local/tmp/vfix_report.json"], capture_output=True, timeout=10)
            subprocess.run(["adb", "shell", "chmod", "777", "/data/local/tmp/vfix_report.json"], capture_output=True, timeout=5)
            
            # Посылаем широковещательный сигнал приложению VFIX
            subprocess.run(["adb", "shell", "am", "broadcast", "-a", "com.vekx.vfix.DATA_READY"], capture_output=True, timeout=5)

            self.ui_callback("progress", "100|Отчет на телефоне")
            self.ui_callback("status", "[READY] Отчет диагностики успешно передан в телефон!")
            self.ui_callback("log", "[SUCCESS] Логи доставлены прямо в приложение VFIX!")

        except Exception as e:
            self.ui_callback("log", f"[ERROR] Ошибка передачи отчета: {e}")

    def _check_incoming_commands(self):
        try:
            # Читаем файл команд с телефона
            res = subprocess.run(["adb", "shell", "cat", "/data/local/tmp/vfix_cmd.txt"], capture_output=True, text=True, timeout=3)
            cmd = res.stdout.strip()
            if cmd and not cmd.startswith("DONE"):
                self.ui_callback("log", f"[PHONE COMMAND] Получена команда с телефона: {cmd}")
                self.ui_callback("status", f"[EXECUTING] Выполнение команды: {cmd}")
                
                # Помечаем команду как прочитанную
                subprocess.run(["adb", "shell", "echo", "DONE", ">", "/data/local/tmp/vfix_cmd.txt"], capture_output=True)

                # Выполняем
                res_exec = execute_repair_command(cmd)
                self.ui_callback("log", f"Результат: {res_exec.get('output', 'OK')}")
                self.ui_callback("status", f"[DONE] Команда {cmd} завершена!")

        except Exception:
            pass

class VfixPcApp:
    def __init__(self):
        self.root = None
        self.log_queue = deque(maxlen=200)
        self.status_text = "[VFIX READY] Инициализация прямого USB-канала..."
        self.progress_value = 0.0
        self.progress_label = "0%"
        self.bridge = None

    def add_log(self, text: str):
        t = time.strftime('%H:%M:%S')
        entry = f"[{t}] {text}"
        self.log_queue.append(entry)
        if self.log_listbox and self.root:
            try:
                self.root.after(0, lambda: self.log_listbox.insert(tk.END, entry))
                self.root.after(0, lambda: self.log_listbox.yview(tk.END))
            except Exception:
                pass

    def set_status(self, text: str):
        self.status_text = text
        if self.status_label and self.root:
            try:
                self.root.after(0, lambda: self.status_label.config(text=text))
            except Exception:
                pass

    def set_progress(self, val: float, label: str = ""):
        self.progress_value = max(0.0, min(100.0, val))
        self.progress_label = label if label else f"{int(self.progress_value)}%"
        if self.root:
            try:
                self.root.after(0, self._render_bar)
            except Exception:
                pass

    def _render_bar(self):
        if not self.progress_canvas:
            return
        w = self.progress_canvas.winfo_width()
        h = self.progress_canvas.winfo_height()
        self.progress_canvas.delete("all")
        self.progress_canvas.create_rectangle(0, 0, w, h, fill="#050000", outline="#440000")
        
        fill_w = int(w * (self.progress_value / 100.0))
        if fill_w > 0:
            self.progress_canvas.create_rectangle(2, 2, fill_w - 2, h - 2, fill="#FF0000", outline="")
            self.progress_canvas.create_line(fill_w - 2, 2, fill_w - 2, h - 2, fill="#FFFFFF", width=2)
            
        if self.progress_text:
            self.progress_text.config(text=f"{self.progress_label} [{int(self.progress_value)}%]")

    def ui_callback(self, kind, msg):
        if kind == "log":
            self.add_log(msg)
        elif kind == "status":
            self.set_status(msg)
        elif kind == "progress":
            try:
                parts = msg.split("|", 1)
                self.set_progress(float(parts[0]), parts[1] if len(parts) > 1 else "")
            except Exception:
                pass

    def run(self):
        self.root = tk.Tk()
        self.root.title("VFIX Direct Cable Station")
        self.root.attributes("-fullscreen", True)
        self.root.attributes("-topmost", True)
        self.root.configure(bg="#000000")
        self.root.bind("<Escape>", lambda e: self.root.destroy())

        w = self.root.winfo_screenwidth()
        h = self.root.winfo_screenheight()

        main_frame = tk.Frame(self.root, bg="#000000")
        main_frame.pack(fill="both", expand=True, padx=40, pady=40)

        left = tk.Frame(main_frame, bg="#000000", width=int(w * 0.45))
        left.pack(side="left", fill="both", expand=False, padx=(0, 30))
        left.pack_propagate(False)

        right = tk.Frame(main_frame, bg="#000000")
        right.pack(side="right", fill="both", expand=True)

        # Логотип
        logo_file = os.path.join(os.path.dirname(__file__), "vfix_logo.jpg")
        if os.path.exists(logo_file):
            try:
                pil_img = Image.open(logo_file).resize((400, 400), Image.Resampling.LANCZOS)
                self.logo_img = ImageTk.PhotoImage(pil_img)
                tk.Label(left, image=self.logo_img, bg="#000000", bd=0).pack(pady=(10, 20))
            except Exception:
                tk.Label(left, text="V F I X", fg="#FF0000", bg="#000000", font=("Consolas", 48, "bold")).pack(pady=40)
        else:
            tk.Label(left, text="V F I X", fg="#FF0000", bg="#000000", font=("Consolas", 48, "bold")).pack(pady=40)

        self.status_label = tk.Label(left, text=self.status_text, fg="#FF2222", bg="#000000", font=("Consolas", 13, "bold"), wraplength=int(w * 0.42))
        self.status_label.pack(pady=10)

        prog_frame = tk.Frame(left, bg="#000000")
        prog_frame.pack(fill="x", padx=10, pady=10)
        tk.Label(prog_frame, text="ШКАЛА ДИАГНОСТИКИ И ВЫПОЛНЕНИЯ:", fg="#888888", bg="#000000", font=("Consolas", 11, "bold")).pack(anchor="w")

        self.progress_canvas = tk.Canvas(prog_frame, height=28, bg="#050000", highlightthickness=0)
        self.progress_canvas.pack(fill="x", pady=6)

        self.progress_text = tk.Label(prog_frame, text=self.progress_label, fg="#FF4444", bg="#000000", font=("Consolas", 12, "bold"))
        self.progress_text.pack(anchor="e")

        # Логи справа
        tk.Label(right, text="СИСТЕМНЫЕ ЛОГИ И ВЫПОЛНЕНИЕ (LIVE LOGS):", fg="#FF0000", bg="#000000", font=("Consolas", 13, "bold")).pack(anchor="w", pady=(10, 8))
        
        box = tk.Frame(right, bg="#000000", bd=2, highlightbackground="#FF0000", highlightthickness=1)
        box.pack(fill="both", expand=True)

        sc = tk.Scrollbar(box, orient="vertical")
        sc.pack(side="right", fill="y")

        self.log_listbox = tk.Listbox(box, bg="#050000", fg="#FF3333", selectbackground="#330000", selectforeground="#FFFFFF", font=("Consolas", 11), bd=0, highlightthickness=0, yscrollcommand=sc.set)
        self.log_listbox.pack(side="left", fill="both", expand=True, padx=5, pady=5)
        sc.config(command=self.log_listbox.yview)

        # Запуск кабельного моста
        self.bridge = VfixAdbCableBridge(self.ui_callback)
        self.bridge.start()

        self.root.after(300, self._render_bar)
        self.root.mainloop()

if __name__ == "__main__":
    app = VfixPcApp()
    app.run()
