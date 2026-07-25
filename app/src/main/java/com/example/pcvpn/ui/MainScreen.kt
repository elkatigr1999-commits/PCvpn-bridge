package com.example.pcvpn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pcvpn.data.VpnProfile
import com.example.pcvpn.service.SocksVpnService.VpnState
import com.example.pcvpn.utils.MdnsResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vpnState: VpnState,
    isDarkTheme: Boolean,
    profiles: List<VpnProfile>,
    selectedProfile: VpnProfile?,
    onToggleTheme: () -> Unit,
    onSelectProfile: (VpnProfile) -> Unit,
    onSaveProfile: (profile: VpnProfile) -> Unit,
    onDeleteProfile: (profileId: String) -> Unit,
    onConnectClick: (profile: VpnProfile) -> Unit,
    onDisconnectClick: () -> Unit
) {
    var showAddEditDialog by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<VpnProfile?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    val isConnected = vpnState is VpnState.Connected
    val isConnecting = vpnState is VpnState.Connecting

    if (showLogDialog) {
        LogDialog(onDismiss = { showLogDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PC VPN",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = "Логи соединения",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) "Светлая тема" else "Тёмная тема",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Карточка статуса соединения
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = when (vpnState) {
                        is VpnState.Connected -> if (isDarkTheme) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                        is VpnState.Connecting -> if (isDarkTheme) Color(0xFFE65100) else Color(0xFFFFF8E1)
                        is VpnState.Error -> if (isDarkTheme) Color(0xFFB71C1C) else Color(0xFFFFEBEE)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = when (vpnState) {
                            is VpnState.Connected -> Icons.Default.VpnLock
                            is VpnState.Connecting -> Icons.Default.Sync
                            is VpnState.Error -> Icons.Default.ErrorOutline
                            else -> Icons.Default.Shield
                        },
                        contentDescription = null,
                        tint = when (vpnState) {
                            is VpnState.Connected -> if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
                            is VpnState.Connecting -> if (isDarkTheme) Color(0xFFFFD54F) else Color(0xFFF57F17)
                            is VpnState.Error -> if (isDarkTheme) Color(0xFFE57373) else Color(0xFFC62828)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(36.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (vpnState) {
                                is VpnState.Connected -> "Подключено"
                                is VpnState.Connecting -> "Подключение..."
                                is VpnState.Error -> "Ошибка"
                                else -> "Отключено"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = when (vpnState) {
                                is VpnState.Connected -> if (isDarkTheme) Color(0xFF81C784) else Color(0xFF1B5E20)
                                is VpnState.Connecting -> if (isDarkTheme) Color(0xFFFFD54F) else Color(0xFFE65100)
                                is VpnState.Error -> if (isDarkTheme) Color(0xFFEF5350) else Color(0xFFB71C1C)
                                else -> if (isDarkTheme) Color(0xFFB0BEC5) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        Text(
                            text = when (vpnState) {
                                is VpnState.Connected -> "Адрес: ${vpnState.host}:${vpnState.port} (${vpnState.ip})"
                                is VpnState.Connecting -> "Установление SOCKS5 соединения..."
                                is VpnState.Error -> vpnState.message
                                else -> "Готов к подключению SOCKS5"
                            },
                            fontSize = 13.sp,
                            color = if (vpnState is VpnState.Disconnected) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                        )
                    }

                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = if (isDarkTheme) Color.White else Color(0xFFF57F17)
                        )
                    }
                }
            }



            // 2. Блок выбора и управления профилями
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Профиль подключения",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Кнопка «Плюсик» + для создания нового профиля
                        IconButton(
                            onClick = {
                                profileToEdit = null
                                showAddEditDialog = true
                            },
                            enabled = !isConnected && !isConnecting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить профиль",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Селектор сохраненных профилей
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isConnected && !isConnecting && profiles.isNotEmpty()) {
                                    dropdownExpanded = true
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (selectedProfile != null && MdnsResolver.isIpAddress(selectedProfile.host))
                                            Icons.Default.Router else Icons.Default.Computer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = selectedProfile?.name ?: if (profiles.isEmpty()) "Нет профилей (нажмите +)" else "Выберите профиль",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        if (selectedProfile != null) {
                                            val formattedHost = MdnsResolver.formatHost(selectedProfile.host)
                                            Text(
                                                text = "$formattedHost:${selectedProfile.port}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (profiles.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                            }
                        }

                        // Выпадающее меню со списком профилей
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(profile.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${MdnsResolver.formatHost(profile.host)}:${profile.port}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectProfile(profile)
                                        dropdownExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (MdnsResolver.isIpAddress(profile.host))
                                                Icons.Default.Router else Icons.Default.Computer,
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = {
                                                dropdownExpanded = false
                                                profileToEdit = profile
                                                showAddEditDialog = true
                                            }) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Редактировать",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(onClick = {
                                                onDeleteProfile(profile.id)
                                            }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Удалить",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Дополнительные кнопки редактирования / удаления выбранного профиля
                    if (selectedProfile != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    profileToEdit = selectedProfile
                                    showAddEditDialog = true
                                },
                                enabled = !isConnected && !isConnecting
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Изменить")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextButton(
                                onClick = { onDeleteProfile(selectedProfile.id) },
                                enabled = !isConnected && !isConnecting,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Удалить")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. Главная кнопка «Подключить» / «Отключить»
            Button(
                onClick = {
                    if (isConnected || isConnecting) {
                        onDisconnectClick()
                    } else if (selectedProfile != null) {
                        onConnectClick(selectedProfile)
                    } else {
                        profileToEdit = null
                        showAddEditDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected || isConnecting) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isConnected || isConnecting) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Text(
                        text = if (isConnected || isConnecting) "Отключить"
                        else if (selectedProfile != null) "Подключить (${selectedProfile.name})"
                        else "Добавить профиль и подключить",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Диалог создания / редактирования профиля
    if (showAddEditDialog) {
        AddEditProfileDialog(
            profileToEdit = profileToEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, host, port, login, pass ->
                val newOrUpdated = VpnProfile(
                    id = profileToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    host = host,
                    port = port,
                    login = login,
                    password = pass
                )
                onSaveProfile(newOrUpdated)
                showAddEditDialog = false
            }
        )
    }
}
