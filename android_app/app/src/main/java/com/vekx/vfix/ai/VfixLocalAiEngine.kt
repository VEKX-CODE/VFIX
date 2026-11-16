package com.vekx.vfix.ai

import org.json.JSONArray
import org.json.JSONObject

data class AiResponse(
    val replyText: String,
    val commandToExecute: String? = null,
    val commandArgs: String? = null
)

/**
 * 100% Локальный автономный ИИ-диагност VFIX.
 * Не спамит характеристиками ПК! Отвечает чётко и коротко по поломке.
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
     * Анализирует логи ПК и выдает конкретную сводку поломки БЕЗ спама характеристиками железа.
     */
    fun analyzeDiagnostics(reportJson: String): String {
        return try {
            val root = JSONObject(reportJson)
            val data = root.optJSONObject("data") ?: root

            val sb = StringBuilder()

            // 1. Проверка дисков SMART
            val smart = data.opt("smart_status")
            var diskIssues = false
            var diskDetails = ""

            if (smart is JSONArray && smart.length() > 0) {
                for (i in 0 until smart.length()) {
                    val d = smart.getJSONObject(i)
                    val name = d.optString("FriendlyName", "Диск #$i")
                    val health = d.optString("HealthStatus", "Healthy")
                    val op = d.optString("OperationalStatus", "OK")
                    val isOk = health.equals("Healthy", ignoreCase = true) && op.equals("OK", ignoreCase = true)
                    
                    if (!isOk) {
                        diskIssues = true
                        diskDetails += "⚠️ $name: Проблема с состоянием! Статус: $health ($op)\n"
                    }
                }
            }

            // 2. Синие экраны BSOD
            val bsod = data.optJSONArray("bsod_dumps")
            val hasBsod = bsod != null && bsod.length() > 0

            // 3. Ошибки событий
            val events = data.optJSONArray("event_errors")
            val eventCount = events?.length() ?: 0

            sb.append("📋 [ДИАГНОСТИКА ПК]:\n")

            if (diskIssues) {
                sb.append("❌ АППАРАТНАЯ ПРОБЛЕМА С ДИСКОМ:\n$diskDetails\n")
                sb.append("👉 Рекомендация: Запустите '🔍 Прогон диска C (chkdsk)' для изоляции битых секторов.\n")
            } else if (hasBsod) {
                sb.append("⚠️ КРИТИЧЕСКИЕ СБОИ СИСТЕМЫ (BSOD):\n")
                sb.append("Найдено дампов синего экрана: ${bsod!!.length()}.\n")
                sb.append("👉 Рекомендация: Запустите '🛠 SFC /Scannow' или '📦 Восстановление DISM'.\n")
            } else if (eventCount > 0) {
                sb.append("⚠️ Журнал Windows зафиксировал $eventCount критических ошибок служб.\n")
                sb.append("Диски физически исправны (SMART OK).\n")
                sb.append("👉 Рекомендация: Сделайте быструю проверку системных файлов (SFC) или очистку мусора.\n")
            } else {
                sb.append("✅ Железо и накопители ПК полностью исправны (SMART: OK).\n")
                sb.append("Свежих синих экранов или фатальных сбоев не обнаружено.\n")
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
            q.contains("диск") || q.contains("прогон") || q.contains("chkdsk") || q.contains("сектор") -> {
                AiResponse(
                    replyText = "Запускаю глубокую проверку файловой системы и битых секторов (chkdsk C: /f /r)...",
                    commandToExecute = "cmd_raw",
                    commandArgs = "echo y | chkdsk C: /f /r"
                )
            }

            // SFC / Восстановление целостности файлов
            q.contains("sfc") || q.contains("почини") || q.contains("целостност") || q.contains("файлы") -> {
                AiResponse(
                    replyText = "Запускаю сканирование и замену битых системных файлов Windows (sfc /scannow)...",
                    commandToExecute = "sfc"
                )
            }

            // DISM
            q.contains("dism") || q.contains("образ") -> {
                AiResponse(
                    replyText = "Запускаю восстановление системного хранилища (DISM RestoreHealth)...",
                    commandToExecute = "dism_restore"
                )
            }

            // Очистка Temp
            q.contains("мусор") || q.contains("очист") || q.contains("temp") || q.contains("кэш") -> {
                AiResponse(
                    replyText = "Запускаю очистку временных файлов Temp...",
                    commandToExecute = "clean_temp"
                )
            }

            // BIOS
            q.contains("биос") || q.contains("bios") || q.contains("uefi") -> {
                AiResponse(
                    replyText = "Отправляю ПК на перезагрузку сразу в BIOS/UEFI...",
                    commandToExecute = "reboot_uefi"
                )
            }

            // Перезагрузка
            q.contains("перезагруз") || q.contains("ребут") || q.contains("reboot") -> {
                AiResponse(
                    replyText = "Перезагружаю ПК...",
                    commandToExecute = "reboot"
                )
            }

            // Выключение
            q.contains("выключ") || q.contains("выруб") || q.contains("shutdown") -> {
                AiResponse(
                    replyText = "Выключаю ПК...",
                    commandToExecute = "shutdown"
                )
            }

            // Вопрос о поломке
            q.contains("в чем") || q.contains("поломк") || q.contains("что с компом") || q.contains("диагноз") || q.contains("проблем") -> {
                val diag = lastDiagnosticData?.toString() ?: ""
                if (diag.isNotEmpty()) {
                    AiResponse(replyText = analyzeDiagnostics(diag))
                } else {
                    AiResponse(replyText = "Отчет с ПК пока не получен. Подключите провод к ПК и запустите VFIX.")
                }
            }

            // Дефолтный ответ
            else -> {
                AiResponse(
                    replyText = "Команда '$query' принята. Выберите действие из быстрых кнопок выше или напишите 'в чем поломка'."
                )
            }
        }
    }
}
