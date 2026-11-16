package com.vekx.vfix.usb

import android.util.Log
import java.io.File

object UsbGadgetManager {
    private const val TAG = "UsbGadgetManager"

    private fun runRoot(cmd: String): Pair<Int, String> {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            Pair(p.exitValue(), (out + "\n" + err).trim())
        } catch (e: Exception) {
            Pair(-1, e.message ?: "error")
        }
    }

    /**
     * Точное монтирование ISO под MTK / Snapdragon контроллер твоего телефона:
     * /config/usb_gadget/g1/functions/mass_storage.usb0/lun.0
     */
    fun mountIsoAsUsbDrive(isoPath: String): Pair<Boolean, String> {
        if (!File(isoPath).exists()) {
            return Pair(false, "Файл не найден: $isoPath")
        }

        val script = """
            G="/config/usb_gadget/g1"
            if [ -d "${'$'}G" ]; then
                # 1. Отключаем USB контроллер
                echo "" > "${'$'}G/UDC"
                sleep 0.3

                # 2. Монтируем файл в точный найденный узел mass_storage.usb0
                MS_DIR="${'$'}G/functions/mass_storage.usb0"
                if [ -d "${'$'}MS_DIR" ]; then
                    echo "$isoPath" > "${'$'}MS_DIR/lun.0/file"
                    echo 1 > "${'$'}MS_DIR/lun.0/ro"
                fi

                # 3. Линкуем в активную конфигурацию
                CONFIG_DIR=${'$'}(ls -d "${'$'}G/configs/"* 2>/dev/null | head -n 1)
                if [ -n "${'$'}CONFIG_DIR" ]; then
                    ln -s "${'$'}MS_DIR" "${'$'}CONFIG_DIR/" 2>/dev/null
                fi

                # 4. Включаем контроллер UDC
                UDC_NAME=${'$'}(ls /sys/class/udc | head -n 1)
                echo "${'$'}UDC_NAME" > "${'$'}G/UDC"
                echo "USB_FLASH_ACTIVE"
                exit 0
            fi

            echo "NO_CONFIGFS"
            exit 1
        """.trimIndent()

        val (code, out) = runRoot(script)
        val ok = code == 0 && out.contains("USB_FLASH_ACTIVE")
        return Pair(ok, out)
    }
}
