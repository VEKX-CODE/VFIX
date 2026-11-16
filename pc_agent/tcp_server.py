import socket
import threading
import json
import time
from diagnostic_collector import collect_full_diagnostic_report
from repair_executor import execute_repair_command

class VfixTcpServer:
    def __init__(self, port=8888, ui_callback=None):
        self.port = port
        self.ui_callback = ui_callback
        self.server_socket = None
        self.running = False
        self.clients = []

    def start(self):
        self.running = True
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            # Слушаем все входящие USB-интерфейсы
            self.server_socket.bind(("0.0.0.0", self.port))
            self.server_socket.listen(5)
            self._log(f"[USB HARDWARE LINK] Порт {self.port} открыт. Ожидание провода...")
            threading.Thread(target=self._accept_loop, daemon=True).start()
        except Exception as e:
            self._log(f"[ERROR] Сбой открытия порта {self.port}: {e}")

    def _log(self, msg: str):
        if self.ui_callback:
            self.ui_callback("log", msg)

    def _set_status(self, st: str):
        if self.ui_callback:
            self.ui_callback("status", st)

    def _set_progress(self, percent: float, label: str = ""):
        if self.ui_callback:
            self.ui_callback("progress", f"{percent}|{label}")

    def _accept_loop(self):
        while self.running:
            try:
                client_sock, client_addr = self.server_socket.accept()
                client_ip = client_addr[0]
                self._log(f"[HARDWARE LINK ESTABLISHED] Телефон на проводе: {client_ip}")
                self._set_status(f"[CONNECTED] Android ({client_ip}) подключен по кабелю!")
                self._set_progress(15, "Кабель подключен")
                self.clients.append(client_sock)
                threading.Thread(target=self._handle_client, args=(client_sock, client_addr), daemon=True).start()
            except Exception:
                break

    def _handle_client(self, client_sock, client_addr):
        buffer = ""
        while self.running:
            try:
                data = client_sock.recv(4096).decode("utf-8")
                if not data:
                    break
                buffer += data
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    line = line.strip()
                    if line:
                        self._process_command(client_sock, line)
            except Exception as e:
                self._log(f"[DISCONNECT] Связь по кабелю разорвана: {e}")
                break

        if client_sock in self.clients:
            self.clients.remove(client_sock)
        try:
            client_sock.close()
        except Exception:
            pass
        if not self.clients:
            self._set_status("[USB LINK STANDBY] Ожидание подключения телефона по кабелю...")
            self._set_progress(0, "Ожидание")

    def _process_command(self, client_sock, json_str):
        try:
            req = json.loads(json_str)
            action = req.get("action")
            self._log(f"[COMMAND RECEIVED] {action}")

            if action == "ping":
                # Ответ на пинг для честного подтверждения связи
                self._send_json(client_sock, {"action": "pong", "status": "ok", "time": time.time()})
                self._set_status("[CONNECTED] Связь по проводу активна (Пинг OK)")

            elif action == "get_diagnostics":
                self._set_status("[DIAGNOSTICS] Сбор аппаратных логов ПК...")
                self._set_progress(25, "Опрос дисков S.M.A.R.T...")
                self._log("Сканирование физических дисков и секторов...")
                
                report = collect_full_diagnostic_report()
                
                self._set_progress(75, "Чтение дампов BSOD и Event Viewer...")
                self._log("Сбор ошибок журнала Windows...")
                
                response = {
                    "action": "diagnostics_report",
                    "data": report
                }
                self._send_json(client_sock, response)
                self._set_progress(100, "Отчет передан на телефон")
                self._set_status("[READY] Диагностический пакет передан по кабелю")
                self._log("Логи успешно отправлены в телефон по USB.")

            elif action == "repair":
                cmd = req.get("command")
                extra = req.get("args")
                self._set_status(f"[REPAIR RUNNING] Запуск: {cmd}")
                self._set_progress(40, f"Выполнение {cmd}...")
                self._log(f"Запуск восстановительной операции: {cmd}")
                
                res = execute_repair_command(cmd, extra)
                
                self._set_progress(100, "Завершено")
                response = {
                    "action": "repair_result",
                    "data": res
                }
                self._send_json(client_sock, response)
                self._set_status(f"[COMPLETED] Команда {cmd} выполнена")
                self._log(f"Результат {cmd}: статус={res.get('status')}")

            else:
                self._send_json(client_sock, {"action": "error", "message": f"Unknown action: {action}"})

        except Exception as e:
            self._log(f"[ERROR] Ошибка команды: {e}")
            self._send_json(client_sock, {"action": "error", "message": str(e)})

    def _send_json(self, sock, data_dict):
        payload = json.dumps(data_dict, ensure_ascii=False) + "\n"
        sock.sendall(payload.encode("utf-8"))

    def stop(self):
        self.running = False
        if self.server_socket:
            try:
                self.server_socket.close()
            except Exception:
                pass
