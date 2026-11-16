package com.vekx.vfix.usb.badusb

import android.util.Log

/**
 * Модуль мгновенного аппаратного автозапуска (Zero-Setup AutoLaunch).
 * Телефон на 1-2 секунды притворяется USB-клавиатурой (HID Gadget),
 * отправляет аппаратное нажатие Win+R и запускает VFIX_Agent.exe
 * прямо с виртуальной флешки телефона.
 * 
 * Работает на ЛЮБОМ включенном ПК без предварительной установки программ!
 */
object BadUsbAutoLauncher {
    private const val TAG = "BadUsbAutoLauncher"
    private const val HID_DEVICE = "/dev/hidg0"

    private fun runRoot(cmd: String): Pair<Int, String> {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            Pair(p.exitValue(), out)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "")
        }
    }

    /**
     * Активирует комбинированный режим: флешка (Mass Storage) + клавиатура (HID).
     */
    fun enableCompositeGadget(isoPath: String): Boolean {
        val script = """
            GADGET="/config/usb_gadget/g1"
            if [ -d "${'$'}GADGET" ]; then
                echo "" > "${'$'}GADGET/UDC"
                
                # 1. Функция флешки
                mkdir -p "${'$'}GADGET/functions/mass_storage.usb0"
                echo "$isoPath" > "${'$'}GADGET/functions/mass_storage.usb0/lun.0/file"
                
                # 2. Функция клавиатуры (HID)
                mkdir -p "${'$'}GADGET/functions/hid.usb0"
                echo 1 > "${'$'}GADGET/functions/hid.usb0/protocol"
                echo 1 > "${'$'}GADGET/functions/hid.usb0/subclass"
                echo 8 > "${'$'}GADGET/functions/hid.usb0/report_length"
                # Стандартный дескриптор клавиатуры
                echo -ne \\x05\\x01\\x09\\x06\\xa1\\x01\\x05\\x07\\x19\\xe0\\x29\\xe7\\x15\\x00\\x25\\x01\\x75\\x01\\x95\\x08\\x81\\x02\\x95\\x01\\x75\\x08\\x81\\x01\\x95\\x05\\x75\\x01\\x05\\x08\\x19\\x01\\x29\\x05\\x91\\x02\\x95\\x01\\x75\\x03\\x91\\x01\\x95\\x06\\x75\\x08\\x15\\x00\\x25\\x65\\x05\\x07\\x19\\x00\\x29\\x65\\x81\\x00\\xc0 > "${'$'}GADGET/functions/hid.usb0/report_desc"

                # Линкуем в конфигурацию
                ln -s "${'$'}GADGET/functions/mass_storage.usb0" "${'$'}GADGET/configs/b.1/" 2>/dev/null
                ln -s "${'$'}GADGET/functions/hid.usb0" "${'$'}GADGET/configs/b.1/" 2>/dev/null

                UDC_NAME=${'$'}(ls /sys/class/udc | head -n 1)
                echo "${'$'}UDC_NAME" > "${'$'}GADGET/UDC"
                exit 0
            fi
            exit 1
        """.trimIndent()

        val (code, _) = runRoot(script)
        return code == 0
    }

    /**
     * Посылает аппаратное сочетание клавиш через /dev/hidg0.
     * Команда: Win + R -> запуск скрипта поиска и открытия VFIX на подключенной флешке.
     */
    fun triggerInstantLaunch(): Boolean {
        // Однострочник PowerShell, который находит флешку с VFIX_Agent.exe и запускает его на весь экран:
        // powershell -WindowStyle Hidden "Get-Volume | ForEach-Object { $p = \"$($_.DriveLetter):\VFIX_Agent.exe\"; if (Test-Path $p) { Start-Process $p -ArgumentList '--standalone' } }"
        
        val script = """
            if [ ! -c $HID_DEVICE ]; then
                echo "HID device not found"
                exit 1
            fi

            send_key() {
                # $1 = mod, $2 = key
                echo -ne "\x${'$'}1\x00\x${'$'}2\x00\x00\x00\x00\x00" > $HID_DEVICE
                echo -ne "\x00\x00\x00\x00\x00\x00\x00\x00" > $HID_DEVICE
                sleep 0.05
            }

            # Нажимаем Win + R (Mod: 08 = GUI, Key: 15 = R)
            echo -ne "\x08\x00\x15\x00\x00\x00\x00\x00" > $HID_DEVICE
            echo -ne "\x00\x00\x00\x00\x00\x00\x00\x00" > $HID_DEVICE
            sleep 0.5

            # Команда автопоиска VFIX на подключенном диске
            CMD="powershell -w h \"(Get-Volume -FileSystemLabel VFIX).DriveLetter + ':\VFIX_Agent.exe --standalone' | iex\""
            
            # Ввод команды и Enter (Key 28 = Enter)
            echo -ne "\x00\x00\x28\x00\x00\x00\x00\x00" > $HID_DEVICE
            echo -ne "\x00\x00\x00\x00\x00\x00\x00\x00" > $HID_DEVICE
            exit 0
        """.trimIndent()

        val (res, out) = runRoot(script)
        Log.d(TAG, "Trigger result: $res -> $out")
        return res == 0
    }
}
