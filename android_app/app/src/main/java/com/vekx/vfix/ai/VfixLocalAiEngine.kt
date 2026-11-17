package com.vekx.vfix.ai

import org.json.JSONArray
import org.json.JSONObject

data class AiResponse(
    val replyText: String,
    val commandToExecute: String? = null,
    val commandArgs: String? = null
)

/**
 * 100% Локальный автономный ИИ-диагност VFIX 2.0.
 * Теперь с глубоким анализом железа, аппаратных бэд-блоков, аварийных дампов и системных логов.
 */
object VfixLocalAiEngine {

    private var lastDiagnosticData: JSONObject? = null

    fun resetContext() {
        lastDiagnosticData = null
    }

    fun setDiagnosticsContext(jsonStr: String) {
        try {
            lastDiagnosticData = JSONObject(jsonStr)
        } catch (ignored: Exception) {}
    }

    /**
     * Анализирует расширенный отчёт ПК и выдаёт подробное заключение.
     */
    fun analyzeDiagnostics(reportJson: String): String {
        return try {
            val root = JSONObject(reportJson)
            val data = root.optJSONObject("data") ?: root
            val sb = StringBuilder()

            // 1. АППАРАТНЫЙ ПАСПОРТ ПК
            val hw = data.optJSONObject("hardware_passport")
            if (hw != null) {
                sb.append("🖥️ [АППАРАТНЫЙ ПАСПОРТ СИСТЕМЫ]:\n")
                val mb = hw.optJSONObject("motherboard")
                if (mb != null) {
                    sb.append("  • Плата: ${mb.optString("Motherboard", "Unknown")}\n")
                    sb.append("  • BIOS: ${mb.optString("Bios", "Unknown")}\n")
                }
                val cpu = hw.optJSONObject("cpu")
                if (cpu != null) {
                    sb.append("  • CPU: ${cpu.optString("Name", "Unknown")} (${cpu.optInt("NumberOfCores", 0)}C / ${cpu.optInt("NumberOfLogicalProcessors", 0)}T)\n")
                }
                val gpu = hw.optJSONArray("gpu")
                if (gpu != null && gpu.length() > 0) {
                    val g = gpu.getJSONObject(0)
                    sb.append("  • GPU: ${g.optString("Name", "Unknown")} (${g.optInt("AdapterRAM_GB", 0)} GB)\n")
                }
                val ram = hw.optJSONArray("ram_modules")
                if (ram != null && ram.length() > 0) {
                    var totalRam = 0
                    for (i in 0 until ram.length()) {
                        totalRam += ram.getJSONObject(i).optInt("CapacityGB", 0)
                    }
                    sb.append("  • RAM: ${totalRam} GB (${ram.length()} планки/планок)\n")
                }
                sb.append("\n")
            }

            // 2. ГЛУБОКИЙ АНАЛИЗ ДИСКОВ И БЭД-БЛОКОВ
            val smart = data.opt("smart_status")
            var hasBadBlocks = false
            var diskIssues = false
            val diskDetails = StringBuilder()

            if (smart is JSONArray && smart.length() > 0) {
                diskDetails.append("💾 [НАКОПИТЕЛИ И S.M.A.R.T.]:\n")
                for (i in 0 until smart.length()) {
                    val d = smart.getJSONObject(i)
                    val name = d.optString("FriendlyName", "Диск #$i")
                    val bus = d.optString("BusType", "SATA")
                    val size = d.optDouble("SizeGB", 0.0)
                    val health = d.optString("HealthStatus", "Healthy")
                    val op = d.optString("OperationalStatus", "OK")
                    val isOk = health.equals("Healthy", ignoreCase = true) && op.equals("OK", ignoreCase = true)

                    val events = d.optJSONArray("related_events")
                    var badCount = 0
                    if (events != null && events.length() > 0) {
                        badCount = events.length()
                        hasBadBlocks = true
                    }

                    if (!isOk || badCount > 0) {
                        diskIssues = true
                        diskDetails.append("  ⚠️ $name ($bus, ${size.toInt()} GB): ")
                        if (badCount > 0) {
                            diskDetails.append("ОБНАРУЖЕНО $badCount ОШИБОК БЭД-БЛОКОВ (Event ID 7)! ")
                        }
                        diskDetails.append("Статус: $health ($op)\n")
                    } else {
                        diskDetails.append("  • $name ($bus, ${size.toInt()} GB): ЗДОРОВ (OK)\n")
                    }
                }
                sb.append(diskDetails.toString()).append("\n")
            }

            // 3. АВАРИИ ЯДРА (BSOD) И ПОВРЕЖДЁННЫЕ ДРАЙВЕРЫ
            val bsod = data.optJSONArray("bsod_dumps")
            if (bsod != null && bsod.length() > 0) {
                sb.append("💥 [ДАМПЫ СИНИХ ЭКРАНОВ BSOD]:\n")
                sb.append("  Найдено ${bsod.length()} дампов аварийного сбоя ядра!\n")
                for (i in 0 until bsod.length().coerceAtMost(3)) {
                    val b = bsod.getJSONObject(i)
                    val p = b.optString("path", "")
                    val drivers = b.optJSONArray("suspected_drivers")
                    sb.append("  • Файл: ${p.substringAfterLast("\\")}")
                    if (drivers != null && drivers.length() > 0) {
                        sb.append(" -> Подозреваемые драйверы: ")
                        for (k in 0 until drivers.length()) {
                            sb.append("${drivers.getString(k)} ")
                        }
                    }
                    sb.append("\n")
                }
                sb.append("\n")
            }

            // 4. КРИТИЧЕСКИЕ ОШИБКИ СЛУЖБ
            val events = data.optJSONArray("event_errors")
            val eventCount = events?.length() ?: 0
            if (eventCount > 0) {
                sb.append("📋 [СИСТЕМНЫЙ ЖУРНАЛ СБОЕВ]:\n")
                sb.append("  Зафиксировано $eventCount критических сбоев служб за последние 7 дней.\n\n")
            }

            // 5. ЗАКЛЮЧЕНИЕ И ДЕЙСТВИЯ
            sb.append("🎯 [ЗАКЛЮЧЕНИЕ МАСТЕРА]:\n")
            if (hasBadBlocks) {
                sb.append("❌ КРИТИЧЕСКАЯ ПРОБЛЕМА С ДИСКОМ: Журнал зафиксировал аппаратные бэд-блоки (Bad Blocks)!\n")
                sb.append("👉 Срочно запустите '🔍 Прогон диска C (chkdsk)' для изоляции битых секторов.\n")
            } else if (diskIssues) {
                sb.append("⚠️ Накопитель сообщает об ухудшении здоровья.\n")
                sb.append("👉 Рекомендуется проверка файловой системы и резервное копирование.\n")
            } else if (bsod != null && bsod.length() > 0) {
                sb.append("⚠️ Нарушена стабильность ядра Windows из-за конфликта драйверов.\n")
                sb.append("👉 Запустите '🛠 SFC /Scannow' или '📦 Восстановление DISM'.\n")
            } else {
                sb.append("✅ Физическое железо и память исправны.\n")
                sb.append("👉 Для профилактики можно выполнить очистку мусора или проверку SFC.\n")
            }

            sb.toString()
        } catch (e: Exception) {
            "Ошибка разбора логов: ${e.localizedMessage}"
        }
    }

    /**
     * Парсер вопросов мастера и запуск команд.
     */
    fun processUserQuery(query: String): AiResponse {
        val q = query.lowercase().trim()

        return when {
            // Диск / chkdsk
            q.contains("диск") || q.contains("прогон") || q.contains("chkdsk") || q.contains("сектор") || q.contains("бэд") -> {
                AiResponse(
                    replyText = "Запускаю глубокую проверку файловой системы и изоляцию битых секторов на диске C: (chkdsk /f /r)...",
                    commandToExecute = "cmd_raw",
                    commandArgs = "echo y | chkdsk C: /f /r"
                )
            }

            // SFC / Восстановление целостности файлов
            q.contains("sfc") || q.contains("почини") || q.contains("целостност") || q.contains("файлы") -> {
                AiResponse(
                    replyText = "Запускаю проверку и автоматическую замену битых системных файлов Windows (sfc /scannow)...",
                    commandToExecute = "sfc"
                )
            }

            // DISM
            q.contains("dism") || q.contains("образ") -> {
                AiResponse(
                    replyText = "Запускаю восстановление системного хранилища образов DISM RestoreHealth...",
                    commandToExecute = "dism_restore"
                )
            }

            // Очистка Temp
            q.contains("мусор") || q.contains("очист") || q.contains("temp") || q.contains("кэш") -> {
                AiResponse(
                    replyText = "Запускаю очистку временных системных файлов (%TEMP% и C:\\Windows\\Temp)...",
                    commandToExecute = "clean_temp"
                )
            }

            // BIOS
            q.contains("биос") || q.contains("bios") || q.contains("uefi") -> {
                AiResponse(
                    replyText = "Отправляю команду перезагрузки ПК напрямую в настройки BIOS/UEFI...",
                    commandToExecute = "reboot_uefi"
                )
            }

            // Перезагрузка
            q.contains("перезагруз") || q.contains("ребут") || q.contains("reboot") -> {
                AiResponse(
                    replyText = "Перезагружаю компьютер...",
                    commandToExecute = "reboot"
                )
            }

            // Выключение
            q.contains("выключ") || q.contains("выруб") || q.contains("shutdown") -> {
                AiResponse(
                    replyText = "Выключаю компьютер...",
                    commandToExecute = "shutdown"
                )
            }

            // Вопрос о поломке / подробные логи
            q.contains("в чем") || q.contains("поломк") || q.contains("что с компом") || q.contains("диагноз") || q.contains("лог") || q.contains("желез") -> {
                val diag = lastDiagnosticData?.toString() ?: ""
                if (diag.isNotEmpty()) {
                    AiResponse(replyText = analyzeDiagnostics(diag))
                } else {
                    AiResponse(replyText = "Отчёт с ПК ещё не получен. Подключите провод к ПК и запустите VFIX.")
                }
            }

            // Дефолтный ответ
            else -> {
                AiResponse(
                    replyText = "Команда '$query' принята. Выберите вариант из быстрых кнопок или напишите 'в чём поломка'."
                )
            }
        }
    }
}
