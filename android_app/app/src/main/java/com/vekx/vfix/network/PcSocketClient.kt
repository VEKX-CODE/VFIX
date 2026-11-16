package com.vekx.vfix.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class PcSocketClient {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    // Адреса прямого кабеля RNDIS / USB модема между телефоном и ПК
    private val directCableHosts = listOf(
        "192.168.42.129", // Стандартный Windows USB-кабель шлюз
        "192.168.42.2",   // Вторичный шлюз
        "192.168.42.1",   // Прямой интерфейс
        "127.0.0.1",      // ADB Loopback
        "10.0.0.1"        // Альтернативный кабель-сегмент
    )

    /**
     * Честное подключение по кабелю с проверкой рукопожатия (Пинг/Понг).
     * Статус будет TRUE только если ПК РЕАЛЬНО ответил на сокете!
     */
    suspend fun connectByCable(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        disconnect()

        for (host in directCableHosts) {
            try {
                val s = Socket()
                // Быстрый таймаут 500мс на каждый адрес прямого провода
                s.connect(InetSocketAddress(host, 8888), 600)
                s.soTimeout = 2000

                val w = PrintWriter(s.getOutputStream(), true)
                val r = BufferedReader(InputStreamReader(s.getInputStream()))

                // Шлем проверочный пинг ПК
                val pingJson = JSONObject().put("action", "ping").toString()
                w.println(pingJson)
                w.flush()

                val response = r.readLine()
                if (response != null && response.contains("pong")) {
                    socket = s
                    writer = w
                    reader = r
                    return@withContext Pair(true, host)
                } else {
                    s.close()
                }
            } catch (ignored: Exception) {}
        }

        Pair(false, "ПК не ответил по кабелю на порту 8888. Запустите VFIX_Agent.exe на ПК!")
    }

    fun isConnected(): Boolean {
        val s = socket
        return s != null && s.isConnected && !s.isClosed
    }

    suspend fun sendRequest(action: String, params: Map<String, Any> = emptyMap()): String? = withContext(Dispatchers.IO) {
        val w = writer ?: return@withContext null
        val r = reader ?: return@withContext null

        try {
            val req = JSONObject().apply {
                put("action", action)
                params.forEach { (k, v) -> put(k, v) }
            }
            w.println(req.toString())
            w.flush()
            r.readLine()
        } catch (e: Exception) {
            disconnect()
            null
        }
    }

    fun disconnect() {
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (ignored: Exception) {}
        writer = null
        reader = null
        socket = null
    }
}
