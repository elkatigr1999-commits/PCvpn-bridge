package com.example.pcvpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pcvpn.utils.AppLogger
import com.example.pcvpn.utils.LogEntry
import com.example.pcvpn.utils.LogLevel

@Composable
fun LogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()

    // Автопрокрутка вниз при поступлении новых логов
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E2E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Заголовок и кнопки действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Логи подключения",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Row {
                        // Кнопка Копировать
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("PCVPN Logs", AppLogger.getAllLogsText())
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Логи скопированы в буфер", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Копировать",
                                tint = Color(0xFF89B4FA)
                            )
                        }

                        // Кнопка Очистить
                        IconButton(onClick = { AppLogger.clear() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Очистить",
                                tint = Color(0xFFF38BA8)
                            )
                        }

                        // Кнопка Закрыть
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Консольный список логов
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF11111B), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "Логи отсутствуют. Нажмите «Подключить» для старта диагностирования.",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(logs) { log ->
                                LogItemRow(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: LogEntry) {
    val levelColor = when (log.level) {
        LogLevel.SUCCESS -> Color(0xFFA6E3A1) // Зеленый
        LogLevel.INFO -> Color(0xFF89B4FA)    // Синий
        LogLevel.WARN -> Color(0xFFF9E2AF)    // Желтый
        LogLevel.ERROR -> Color(0xFFF38BA8)   // Красный
    }

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "${log.timestamp} ",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "[${log.tag}] ",
                color = levelColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = log.message,
                color = Color(0xFFCDD6F4),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
