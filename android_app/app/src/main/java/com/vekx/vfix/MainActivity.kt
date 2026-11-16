package com.vekx.vfix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vekx.vfix.ai.ChatMessage
import com.vekx.vfix.ai.VfixLocalAiEngine
import com.vekx.vfix.network.DirectCablePipe
import com.vekx.vfix.ui.matrix.RedMatrixRain
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var cablePipe: DirectCablePipe? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VfixMainScreen(
                setupPipe = { onUsbChanged, onData ->
                    cablePipe = DirectCablePipe(this, onUsbChanged, onData)
                },
                onSendCommand = { cmd ->
                    cablePipe?.sendCommandToPc(cmd)
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cablePipe?.unregister()
    }
}

@Composable
fun VfixMainScreen(
    setupPipe: ((Boolean) -> Unit, (String) -> Unit) -> Unit,
    onSendCommand: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isCableConnected by remember { mutableStateOf(false) }
    var hasFreshReport by remember { mutableStateOf(false) }

    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var chatInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Инициализация аппаратного мониторинга кабеля
    LaunchedEffect(Unit) {
        setupPipe(
            // Смена физического состояния USB-порта
            { connected ->
                isCableConnected = connected
                if (!connected) {
                    hasFreshReport = false
                    VfixLocalAiEngine.resetContext()
                    chatMessages.add(
                        ChatMessage(
                            role = "assistant",
                            content = "🔌 [КАБЕЛЬ ОТКЛЮЧЕН]\nСтарый кэш и данные предыдущего ПК удалены. Ожидание подключения к ПК..."
                        )
                    )
                    coroutineScope.launch {
                        if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.size - 1)
                    }
                }
            },
            // Получение свежего отчёта от подключенного ПК
            { jsonReport ->
                hasFreshReport = true
                VfixLocalAiEngine.setDiagnosticsContext(jsonReport)
                val verdict = VfixLocalAiEngine.analyzeDiagnostics(jsonReport)
                chatMessages.add(
                    ChatMessage(
                        role = "assistant",
                        content = "✅ [СВЯЗЬ ПО КАБЕЛЮ УСТАНОВЛЕНА!]\nПолучены свежие логи подключенного ПК:\n\n$verdict"
                    )
                )
                coroutineScope.launch {
                    if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.size - 1)
                }
            }
        )
    }

    val quickOptions = listOf(
        "⚡ В чём поломка?",
        "🔍 Прогон диска C (chkdsk)",
        "🛠 SFC /Scannow (Файлы Windows)",
        "📦 Восстановление образа DISM",
        "🧹 Очистка системного мусора",
        "⚙ Перезагрузка в BIOS/UEFI",
        "🔄 Обычная перезагрузка",
        "🛑 Выключить компьютер"
    )

    fun handleUserAction(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        chatMessages.add(ChatMessage("user", q))

        val aiRes = VfixLocalAiEngine.processUserQuery(q)
        chatMessages.add(ChatMessage("assistant", aiRes.replyText))

        if (aiRes.commandToExecute != null) {
            onSendCommand(aiRes.commandToExecute)
            chatMessages.add(ChatMessage("assistant", "📡 Команда отправлена по кабелю на ПК."))
        }

        coroutineScope.launch {
            if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RedMatrixRain()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "V F I X",
                    color = Color(0xFFFF1A1A),
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 8.sp
                )
                Text(
                    text = "АВТОНОМНЫЙ ДИАГНОСТИЧЕСКИЙ КОМПЛЕКС",
                    color = Color(0xFF882222),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // СТАТУС КАБЕЛЯ (ФИЗИЧЕСКИЙ СТАТУС ПОРТА)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC050000), RoundedCornerShape(6.dp))
                    .border(
                        1.dp,
                        if (isCableConnected && hasFreshReport) Color(0xFFFF2222) else if (isCableConnected) Color(0xFFCC8800) else Color(0xFF440000),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        isCableConnected && hasFreshReport -> "● КАБЕЛЬ: АКТИВЕН (ОТЧЕТ ПОЛУЧЕН)"
                        isCableConnected -> "◐ ПРОВОД ВСТАВЛЕН: ОЖИДАНИЕ VFIX НА ПК"
                        else -> "○ ОЖИДАНИЕ ПРОВОДА USB (ОТКЛЮЧЕН)"
                    },
                    color = when {
                        isCableConnected && hasFreshReport -> Color(0xFFFF2222)
                        isCableConnected -> Color(0xFFFFCC00)
                        else -> Color(0xFF777777)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ЧАТ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xE6050000), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF550000), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                if (chatMessages.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "VFIX СИСТЕМНЫЙ АССИСТЕНТ",
                            color = Color(0xFFFF2222),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Вставьте кабель в сломанный ПК и запустите VFIX.\nЛоги проверятся автоматически.",
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(chatMessages) { msg ->
                            val isUser = msg.role == "user"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                            ) {
                                Text(
                                    text = if (isUser) "ВЫ (МАСТЕР)" else "ДИАГНОСТ VFIX",
                                    color = if (isUser) Color(0xFFFF6666) else Color(0xFFFF2222),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isUser) Color(0xFF2E0000) else Color(0xFF140000),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(1.dp, if (isUser) Color(0xFF660000) else Color(0xFF440000), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = msg.content,
                                        color = if (isUser) Color.White else Color(0xFFFF3333),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ПРОКРУЧИВАЕМЫЕ ВАРИАНТЫ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickOptions.forEach { opt ->
                    Button(
                        onClick = { handleUserAction(opt) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220000)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF660000)),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(opt, color = Color(0xFFFF4444), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ПОЛЕ ВВОДА
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("Команда или вопрос...", fontSize = 11.sp, color = Color(0xFF777777)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF0000),
                        unfocusedBorderColor = Color(0xFF440000),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray
                    )
                )

                Button(
                    onClick = {
                        val t = chatInput
                        chatInput = ""
                        handleUserAction(t)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF880000)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("ВВОД", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
