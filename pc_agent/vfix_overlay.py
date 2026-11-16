import tkinter as tk
from tkinter import ttk
import time
import random
import threading
import os
from collections import deque
from PIL import Image, ImageTk

class VfixLogStreamUI:
    def __init__(self, on_close_callback=None):
        self.root = None
        self.running = False
        self.log_queue = deque(maxlen=200)
        self.status_text = "[USB LINK INITIALIZING] Ожидание связи с Android..."
        self.progress_value = 0.0 # 0.0 до 100.0
        self.progress_label = "IDLE (0%)"
        self.on_close_callback = on_close_callback
        
        # Ссылки на UI элементы Tkinter
        self.logo_image_tk = None
        self.log_listbox = None
        self.status_label_widget = None
        self.progress_percent_label = None
        self.canvas_progress = None

        # Заполняем начальными логами
        for i in range(10):
            self.log_queue.append(f"[{time.strftime('%H:%M:%S')}] [SYS_INIT] VFIX Daemon layer loaded. Standby.")

    def add_log(self, text: str):
        """Добавить лог в окно логов."""
        timestamp = time.strftime('%H:%M:%S')
        entry = f"[{timestamp}] {text}"
        self.log_queue.append(entry)
        if self.log_listbox and self.root:
            try:
                self.root.after(0, self._insert_log_item, entry)
            except Exception:
                pass

    def _insert_log_item(self, entry):
        if self.log_listbox:
            self.log_listbox.insert(tk.END, entry)
            self.log_listbox.yview(tk.END)

    def set_status(self, text: str):
        self.status_text = text
        if self.status_label_widget and self.root:
            try:
                self.root.after(0, lambda: self.status_label_widget.config(text=text))
            except Exception:
                pass

    def set_progress(self, percent: float, label: str = ""):
        self.progress_value = max(0.0, min(100.0, percent))
        self.progress_label = label if label else f"{int(self.progress_value)}%"
        if self.root:
            try:
                self.root.after(0, self._redraw_progress)
            except Exception:
                pass

    def _redraw_progress(self):
        if not self.canvas_progress:
            return
        w = self.canvas_progress.winfo_width()
        h = self.canvas_progress.winfo_height()
        self.canvas_progress.delete("all")
        
        # Рамка бара
        self.canvas_progress.create_rectangle(0, 0, w, h, fill="#050000", outline="#440000", width=1)
        # Заливка прогресса ярко-красным
        fill_w = int(w * (self.progress_value / 100.0))
        if fill_w > 0:
            self.canvas_progress.create_rectangle(2, 2, fill_w - 2, h - 2, fill="#FF0000", outline="")
            # Неоновое свечение полоски
            self.canvas_progress.create_line(fill_w - 2, 2, fill_w - 2, h - 2, fill="#FFFFFF", width=2)
            
        if self.progress_percent_label:
            self.progress_percent_label.config(text=f"{self.progress_label} [{int(self.progress_value)}%]")

    def start(self):
        if self.running:
            return
        self.running = True
        self.root = tk.Tk()
        self.root.title("VFIX Service System")
        self.root.attributes("-fullscreen", True)
        self.root.attributes("-topmost", True)
        self.root.configure(bg="#000000")

        # Выход по Escape
        self.root.bind("<Escape>", lambda e: self.stop())

        width = self.root.winfo_screenwidth()
        height = self.root.winfo_screenheight()

        # Разметка на два основных блока:
        # Левая часть (~45%): аватарка-логотип VFIX + шкала выполнения
        # Правая часть (~55%): консоль живых логов выполнения

        main_frame = tk.Frame(self.root, bg="#000000")
        main_frame.pack(fill="both", expand=True, padx=40, pady=40)

        left_frame = tk.Frame(main_frame, bg="#000000", width=int(width * 0.45))
        left_frame.pack(side="left", fill="both", expand=False, padx=(0, 30))
        left_frame.pack_propagate(False)

        right_frame = tk.Frame(main_frame, bg="#000000")
        right_frame.pack(side="right", fill="both", expand=True)

        # 1. ЛЕВАЯ ЧАСТЬ: ЛОГОТИП ИЗ ВАШЕГО ФАЙЛА
        logo_path = os.path.join(os.path.dirname(__file__), "vfix_logo.jpg")
        if os.path.exists(logo_path):
            try:
                pil_img = Image.open(logo_path)
                # Масштабируем до приятного размера (например 420x420)
                pil_img = pil_img.resize((420, 420), Image.Resampling.LANCZOS)
                self.logo_image_tk = ImageTk.PhotoImage(pil_img)
                logo_label = tk.Label(left_frame, image=self.logo_image_tk, bg="#000000", bd=0)
                logo_label.pack(pady=(10, 20))
            except Exception as e:
                logo_label = tk.Label(left_frame, text="V F I X", fg="#FF0000", bg="#000000", font=("Consolas", 48, "bold"))
                logo_label.pack(pady=(40, 20))
        else:
            logo_label = tk.Label(left_frame, text="V F I X", fg="#FF0000", bg="#000000", font=("Consolas", 48, "bold"))
            logo_label.pack(pady=(40, 20))

        # Статус
        self.status_label_widget = tk.Label(
            left_frame,
            text=self.status_text,
            fg="#FF2222",
            bg="#000000",
            font=("Consolas", 14, "bold"),
            wraplength=int(width * 0.42)
        )
        self.status_label_widget.pack(pady=(10, 15))

        # Шкала прогресса (Progress Bar)
        progress_container = tk.Frame(left_frame, bg="#000000")
        progress_container.pack(fill="x", padx=10, pady=10)

        prog_title = tk.Label(progress_container, text="ШКАЛА ВЫПОЛНЕНИЯ ОПЕРАЦИЙ:", fg="#888888", bg="#000000", font=("Consolas", 11, "bold"))
        prog_title.pack(anchor="w")

        self.canvas_progress = tk.Canvas(progress_container, height=28, bg="#050000", highlightthickness=0)
        self.canvas_progress.pack(fill="x", pady=(5, 5))

        self.progress_percent_label = tk.Label(progress_container, text=self.progress_label, fg="#FF4444", bg="#000000", font=("Consolas", 12, "bold"))
        self.progress_percent_label.pack(anchor="e")

        # 2. ПРАВАЯ ЧАСТЬ: ЛОГИ ВЫПОЛНЕНИЯ
        log_header = tk.Label(right_frame, text="ЖУРНАЛ СОБЫТИЙ И ВЫПОЛНЕНИЯ (LIVE LOGS):", fg="#FF0000", bg="#000000", font=("Consolas", 13, "bold"))
        log_header.pack(anchor="w", pady=(10, 8))

        log_box_frame = tk.Frame(right_frame, bg="#000000", bd=2, highlightbackground="#FF0000", highlightthickness=1)
        log_box_frame.pack(fill="both", expand=True)

        scrollbar = tk.Scrollbar(log_box_frame, orient="vertical")
        scrollbar.pack(side="right", fill="y")

        self.log_listbox = tk.Listbox(
            log_box_frame,
            bg="#050000",
            fg="#FF3333",
            selectbackground="#330000",
            selectforeground="#FFFFFF",
            font=("Consolas", 11),
            bd=0,
            highlightthickness=0,
            yscrollcommand=scrollbar.set
        )
        self.log_listbox.pack(side="left", fill="both", expand=True, padx=5, pady=5)
        scrollbar.config(command=self.log_listbox.yview)

        # Заполняем логами
        for item in self.log_queue:
            self.log_listbox.insert(tk.END, item)
        self.log_listbox.yview(tk.END)

        # Демо-таймер для плавной анимации прогресса и логов при старте
        self.root.after(500, self._initial_ui_refresh)
        self.root.mainloop()

    def _initial_ui_refresh(self):
        self._redraw_progress()

    def stop(self):
        self.running = False
        if self.root:
            try:
                self.root.after(0, self.root.destroy)
            except Exception:
                pass
            self.root = None
        if self.on_close_callback:
            self.on_close_callback()

if __name__ == "__main__":
    ui = VfixLogStreamUI()
    # Тестовый запуск
    threading.Thread(target=ui.start, daemon=True).start()
    time.sleep(1)
    ui.set_status("[TEST MODE] Ожидание команд...")
    for i in range(1, 101, 10):
        time.sleep(0.4)
        ui.set_progress(i, f"Проверка дисков... {i}%")
        ui.add_log(f"Проверен блок памяти 0x{i*1000:X} -> OK")
    while True:
        time.sleep(1)
