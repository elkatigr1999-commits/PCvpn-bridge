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
import com.example.pcvpn.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vpnState: VpnState,
    isDarkTheme: Boolean,
    currentLanguage: String = "en",
    profiles: List<VpnProfile>,
    selectedProfile: VpnProfile?,
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
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
        LogDialog(currentLanguage = currentLanguage, onDismiss = { showLogDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        AppStrings.get("appTitle", currentLanguage),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    // Переключатель языка EN / RU
                    TextButton(onClick = onToggleLanguage) {
                        Text(
                            text = if (currentLanguage == "en") "EN" else "RU",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = AppStrings.get("logsTitle", currentLanguage),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
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
                                is VpnState.Connected -> AppStrings.get("connected", currentLanguage)
                                is VpnState.Connecting -> AppStrings.get("connecting", currentLanguage)
                                is VpnState.Error -> AppStrings.get("error", currentLanguage)
                                else -> AppStrings.get("disconnected", currentLanguage)
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
                                is VpnState.Connected -> "${AppStrings.get("addressPrefix", currentLanguage)}: ${vpnState.host}:${vpnState.port} (${vpnState.ip})"
                                is VpnState.Connecting -> AppStrings.get("establishingStatus", currentLanguage)
                                is VpnState.Error -> vpnState.message
                                else -> AppStrings.get("readyStatus", currentLanguage)
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
                            text = if (currentLanguage == "en") "VPN Profiles" else "Профили VPN",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        IconButton(onClick = {
                            profileToEdit = null
                            showAddEditDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = AppStrings.get("addProfile", currentLanguage),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedProfile?.name ?: AppStrings.get("addProfile", currentLanguage),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                    if (selectedProfile != null) {
                                        Text(
                                            text = "${selectedProfile.host}:${selectedProfile.port}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }

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
                                            Text("${profile.host}:${profile.port}", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    },
                                    onClick = {
                                        onSelectProfile(profile)
                                        dropdownExpanded = false
                                    },
                                    trailingIcon = {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    dropdownExpanded = false
                                                    profileToEdit = profile
                                                    showAddEditDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = AppStrings.get("editProfile", currentLanguage),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    dropdownExpanded = false
                                                    onDeleteProfile(profile.id)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = AppStrings.get("deleteProfile", currentLanguage),
                                                    tint = Color.Red,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text("+ ${AppStrings.get("addProfile", currentLanguage)}", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    dropdownExpanded = false
                                    profileToEdit = null
                                    showAddEditDialog = true
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. Главная кнопка подключения / отключения
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
                        text = if (isConnected || isConnecting) AppStrings.get("disconnect", currentLanguage)
                        else if (selectedProfile != null) "${AppStrings.get("connect", currentLanguage)} (${selectedProfile.name})"
                        else "${AppStrings.get("addProfile", currentLanguage)} & ${AppStrings.get("connect", currentLanguage)}",
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
            currentLanguage = currentLanguage,
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
