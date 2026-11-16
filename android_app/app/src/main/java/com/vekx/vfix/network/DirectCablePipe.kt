package com.vekx.vfix.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Бронебойный аппаратный канал связи через USB-кабель с контролем физического состояния порта.
 * При отключении кабеля или запуске приложения — стирает все старые кэшированные файлы.
 */
class DirectCablePipe(
    private val context: Context,
    private val onUsbStateChanged: (Boolean) -> Unit,
    private val onDataReceived: (String) -> Unit
) {

    private val reportFile = File("/data/local/tmp/vfix_report.json")
    private val commandFile = File("/data/local/tmp/vfix_cmd.txt")

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                // Аппаратный статус физического USB-порта
                "android.hardware.usb.action.USB_STATE" -> {
                    val connected = intent.extras?.getBoolean("connected") ?: false
                    onUsbStateChanged(connected)
                    if (!connected) {
                        // Кабель физически выдернули -> СТИРАЕМ ВСЕ СТАРЫЕ ЛОГИ НАЧИСТО
                        wipeOldCaches()
                    }
                }
                // Пришел свежий отчет от ПК
                "com.vekx.vfix.DATA_READY" -> {
                    readReportFromFile()
                }
            }
        }
    }

    init {
        // При старте очищаем старый отчет, чтобы не подсасывались данные предыдущего ПК
        wipeOldCaches()

        val filter = IntentFilter().apply {
            addAction("android.hardware.usb.action.USB_STATE")
            addAction("com.vekx.vfix.DATA_READY")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun wipeOldCaches() {
        try {
            if (reportFile.exists()) {
                reportFile.delete()
            }
            if (commandFile.exists()) {
                commandFile.delete()
            }
            // Гарантированное удаление через root su
            Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f /data/local/tmp/vfix_report.json /data/local/tmp/vfix_cmd.txt"))
        } catch (ignored: Exception) {}
    }

    fun readReportFromFile() {
        try {
            if (reportFile.exists() && reportFile.canRead()) {
                val content = reportFile.readText()
                if (content.isNotBlank()) {
                    onDataReceived(content)
                    return
                }
            }

            // Fallback через root su
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/local/tmp/vfix_report.json"))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            if (out.isNotBlank()) {
                onDataReceived(out)
            }
        } catch (e: Exception) {
            Log.e("DirectCablePipe", "Error reading pipe", e)
        }
    }

    fun sendCommandToPc(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "echo '$cmd' > /data/local/tmp/vfix_cmd.txt && chmod 777 /data/local/tmp/vfix_cmd.txt"))
        } catch (ignored: Exception) {}
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: Exception) {}
    }
}
