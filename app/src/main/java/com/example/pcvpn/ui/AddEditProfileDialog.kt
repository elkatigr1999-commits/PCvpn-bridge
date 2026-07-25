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
import com.example.pcvpn.utils.AppStrings
import com.example.pcvpn.utils.MdnsResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProfileDialog(
    profileToEdit: VpnProfile? = null,
    currentLanguage: String = "en",
    onDismiss: () -> Unit,
    onSave: (name: String, host: String, port: Int, login: String, pass: String) -> Unit
) {
    val isEn = currentLanguage.lowercase() == "en"

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
                text = if (profileToEdit == null) AppStrings.get("addProfile", currentLanguage) else AppStrings.get("editProfile", currentLanguage),
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
                // Profile Name
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(AppStrings.get("profileName", currentLanguage)) },
                    placeholder = { Text(if (isEn) "e.g. Home PC" else "например: Домашний ПК") },
                    leadingIcon = {
                        Icon(Icons.Default.Bookmark, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // Host or IP
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text(AppStrings.get("hostAddress", currentLanguage)) },
                    placeholder = { Text(if (isEn) "e.g. desktop-pc or 192.168.1.10" else "например: desktop-pc или 192.168.1.10") },
                    supportingText = {
                        if (formattedHostPreview.isNotEmpty()) {
                            val typeLabel = if (isIp) "IP" else "mDNS"
                            Text(
                                "$typeLabel: $formattedHostPreview",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(if (isEn) "Auto-detection: IP or host (.local)" else "Автоопределение: IP или хост (.local)")
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

                // Port (default 4066)
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 5) {
                            portInput = input
                        }
                    },
                    label = { Text(AppStrings.get("port", currentLanguage)) },
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

                // Login (optional)
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text(AppStrings.get("loginOptional", currentLanguage)) },
                    placeholder = { Text(if (isEn) "Enter login" else "Введите логин") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password (optional)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(AppStrings.get("passwordOptional", currentLanguage)) },
                    placeholder = { Text(if (isEn) "Enter password" else "Введите пароль") },
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
                    val defaultProfileName = if (isEn) "Profile" else "Профиль"
                    val finalName = if (profileName.isBlank()) hostInput.ifBlank { defaultProfileName } else profileName
                    onSave(finalName, hostInput, parsedPort, login, password)
                },
                enabled = hostInput.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(AppStrings.get("save", currentLanguage))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.get("cancel", currentLanguage))
            }
        }
    )
}
