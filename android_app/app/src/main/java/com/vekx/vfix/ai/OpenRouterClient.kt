package com.vekx.vfix.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Интеграция с OpenRouter API для отправки системных логов ПК
 * и получения заключения ИИ-диагноста.
 */
class OpenRouterClient(
    private var apiKey: String = "",
    private var modelName: String = "inclusionai/ling-3.2-flash:free"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun updateConfig(newKey: String, newModel: String) {
        this.apiKey = newKey
        this.modelName = newModel
    }

    suspend fun analyzePcDiagnostics(diagnosticJson: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Ошибка: API ключ OpenRouter не указан в настройках VFIX!"
        }

        val systemPrompt = """
            Ты — ведущий инженер по аппаратному и программному ремонту ПК в экосистеме VFIX.
            Тебе предоставлен полный диагностический отчет с компьютера пользователя:
            (статус S.M.A.R.T. накопителей, последние критические ошибки Event Viewer, наличие дампов BSOD, состояние памяти и дисков).
            
            Твоя задача:
            1. Выдели главную проблему или источник сбоев (почему система зависает, падает в синий экран или тормозит).
            2. Оцени здоровье накопителей (есть ли битые сектора, ошибки CRC).
            3. Дай четкий пошаговый план решения проблемы (какие команды запустить через VFIX, нужно ли менять термопасту/диск, или лучше переустановить Windows).
            Отвечай четко, емко, технически грамотно и структурировано.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("model", modelName)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Вот диагностические логи с ПК:\n$diagnosticJson")
                })
            }
            put("messages", messages)
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://vekx.bio/vfix")
            .addHeader("X-Title", "VFIX Diagnostics")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "Ошибка API (${response.code}): $bodyStr"
                }

                val resJson = JSONObject(bodyStr)
                val choices = resJson.getJSONArray("choices")
                if (choices.length() > 0) {
                    return@withContext choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else {
                    return@withContext "Нейросеть вернула пустой ответ."
                }
            }
        } catch (e: Exception) {
            return@withContext "Сбой сетевого запроса к OpenRouter: ${e.localizedMessage}"
        }
    }
}
