package com.example.pcvpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pcvpn.data.VpnProfile
import com.example.pcvpn.utils.MdnsResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProfileDialog(
    profileToEdit: VpnProfile? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, host: String, port: Int, login: String, pass: String) -> Unit
) {
    var profileName by remember { mutableStateOf(profileToEdit?.name ?: "") }
    var hostInput by remember { mutableStateOf(profileToEdit?.host ?: "") }
    var portInput by remember { mutableStateOf(profileToEdit?.port?.toString() ?: "4066") }
    var login by remember { mutableStateOf(profileToEdit?.login ?: "") }
    var password by remember { mutableStateOf(profileToEdit?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isIp = remember(hostInput) {
        MdnsResolver.isIpAddress(hostInput)
    }

    val formattedHostPreview = remember(hostInput, portInput) {
        val formatted = MdnsResolver.formatHost(hostInput)
        val port = portInput.toIntOrNull() ?: 4066
        if (formatted.isNotEmpty()) "$formatted:$port" else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (profileToEdit == null) "Новый профиль" else "Редактирование профиля",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Название профиля
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Название профиля") },
                    placeholder = { Text("например: Домашний ПК") },
                    leadingIcon = {
                        Icon(Icons.Default.Bookmark, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // Имя компьютера или IP-адрес
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text("Имя компьютера или IP-адрес") },
                    placeholder = { Text("например, desktop-pc или pcvpn") },
                    supportingText = {
                        if (formattedHostPreview.isNotEmpty()) {
                            val typeLabel = if (isIp) "IP-адрес" else "mDNS адрес"
                            Text(
                                "$typeLabel: $formattedHostPreview",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text("Автоопределение: IP или хост (.local)")
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isIp) Icons.Default.Router else Icons.Default.Computer,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // Порт (цифровая клавиатура, по умолчанию 4066)
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 5) {
                            portInput = input
                        }
                    },
                    label = { Text("Порт (по умолчанию 4066)") },
                    placeholder = { Text("4066") },
                    leadingIcon = {
                        Icon(Icons.Default.Numbers, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Логин (необязательно)
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин (необязательно)") },
                    placeholder = { Text("Введите логин") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // Пароль (необязательно)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль (необязательно)") },
                    placeholder = { Text("Введите пароль") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPort = portInput.toIntOrNull() ?: 4066
                    val finalName = if (profileName.isBlank()) hostInput.ifBlank { "Профиль" } else profileName
                    onSave(finalName, hostInput, parsedPort, login, password)
                },
                enabled = hostInput.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
