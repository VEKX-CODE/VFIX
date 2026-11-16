# VFIX - Комплекс диагностики и восстановления ПК через Android по USB

Проект в экосистеме **VEKX BIO** (стилизован в красно-черных тонах киберпанка).

---

## 📁 Структура проекта

```
c:\code\gemini\vfix\
├── pc_agent\                 # Модуль для ПК (Windows)
│   ├── main_agent.py         # Главный сервис (USB отслеживание + оверлей)
│   ├── vfix_overlay.py       # Полноэкранный UI с фоном из красных логов и логотипом VFIX
│   ├── tcp_server.py         # TCP сокет-сервер (порт 8888) для команд
│   ├── diagnostic_collector.py # Сборщик SMART дисков, Event Viewer, BSOD дампов
│   ├── repair_executor.py    # Исполнитель команд (sfc, dism, reboot UEFI)
│   ├── requirements.txt      # Python зависимости
│   └── build_exe.bat         # Скрипт сборки в автономный VFIX_Agent.exe
│
├── android_app\              # Android приложение (Kotlin + Jetpack Compose)
│   └── app\src\main\java\com\vekx\vfix\
│       ├── MainActivity.kt   # Главный экран с кнопками, настройками и терминалом
│       ├── ui\matrix\        # Кастомный Canvas с падающим красным матричным кодом
│       ├── usb\              # Root-модуль монтирования ISO через Linux USB Gadget (configfs)
│       ├── network\          # Клиент сокета по USB (порт 8888)
│       └── ai\               # Интеграция с OpenRouter API (бесплатные модели ИИ-диагноста)
│
└── docs\                     # Документация и инструкции по настройке WinPE
```

---

## 🚀 Быстрый запуск ПК-агента для тестирования

1. Откройте командную строку в папке `pc_agent`:
   ```bash
   cd c:\code\gemini\vfix\pc_agent
   pip install -r requirements.txt
   ```
2. Запустите в тестовом режиме оверлея (без ожидания кабеля):
   ```bash
   python main_agent.py --standalone
   ```
   *Вы увидите полноэкранный интерфейс: сплошной поток темно-красных системных логов на фоне и горящий логотип VFIX по центру. Для выхода нажмите `Esc`.*

3. Для сборки в один `.exe` файл дважды кликните `build_exe.bat`. Готовый файл появится в `dist\VFIX_Agent.exe`.

---

## 📱 Сборка Android-приложения

1. Откройте папку `c:\code\gemini\vfix\android_app` в **Android Studio**.
2. Подключите смартфон с Root-правами (Magisk / KernelSU / APatch).
3. Соберите и установите APK.
4. В приложении:
   - Введите бесплатный API-ключ OpenRouter (например `sk-or-v1-...`).
   - Укажите путь к образу `.iso` (Windows или WinPE), который лежит на телефоне.
   - Подключите провод к ПК и нажмите **⚡ ДИАГНОСТИКА ИИ**.
