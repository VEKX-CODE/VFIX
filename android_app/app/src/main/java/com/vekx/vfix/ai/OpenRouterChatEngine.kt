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

data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

/**
 * Интерактивный диалоговый клиент OpenRouter AI.
 * Хранит историю общения и логи ПК, позволяя вести живой чат и подавать идеи ИИ.
 */
class OpenRouterChatEngine(
    private var apiKey: String = "",
    private var modelName: String = "nvidia/llama-3.1-nemotron-70b-instruct:free"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<ChatMessage>()
    private var pcLogsCached: String = ""

    init {
        resetConversation()
    }

    fun updateApiKey(key: String) {
        this.apiKey = key
    }

    fun resetConversation() {
        conversationHistory.clear()
        conversationHistory.add(
            ChatMessage(
                role = "system",
                content = """
                    Ты — ведущий инженер по аппаратному и программному ремонту ПК в киберпанк-экосистеме VFIX.
                    Ты находишься в интерактивном диалоге с мастером по ремонту через приложение VFIX.
                    
                    Твоя цель:
                    1. Анализировать системные логи с подключенного ПК (SMART, BSOD, Event Viewer, память, диски).
                    2. Отвечать на гипотезы, идеи и вопросы мастера (например: 'сделать ли прогон диска chkdsk', 'проверить ли RAM', 'прогнать ли DISM').
                    3. Давать четкие технические советы и готовые команды для VFIX.
                    Отвечай профессионально, кратко, по делу и в стиле VFIX.
                """.trimIndent()
            )
        )
    }

    fun setPcDiagnosticsContext(logsJson: String) {
        this.pcLogsCached = logsJson
        conversationHistory.add(
            ChatMessage(
                role = "user",
                content = "Мастер подключил ПК. Вот полные системные логи и SMART:\n$logsJson\n\nПроанализируй состояние ПК и дай краткий первичный диагноз."
            )
        )
    }

    suspend fun sendMessage(userText: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Ошибка: Введите ваш API-ключ OpenRouter в настройках VFIX!"
        }

        conversationHistory.add(ChatMessage("user", userText))

        val jsonBody = JSONObject().apply {
            put("model", modelName)
            val msgs = JSONArray()
            for (msg in conversationHistory) {
                msgs.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put("messages", msgs)
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://vekx.bio/vfix")
            .addHeader("X-Title", "VFIX Interactive Diagnostics")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "Ошибка OpenRouter (${response.code}): $bodyStr"
                }

                val resJson = JSONObject(bodyStr)
                val choices = resJson.getJSONArray("choices")
                if (choices.length() > 0) {
                    val reply = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    conversationHistory.add(ChatMessage("assistant", reply))
                    return@withContext reply
                } else {
                    return@withContext "Нейросеть не сформировала ответ."
                }
            }
        } catch (e: Exception) {
            return@withContext "Ошибка сети: ${e.localizedMessage}"
        }
    }
}
