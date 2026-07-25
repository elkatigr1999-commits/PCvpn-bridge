package com.example.pcvpn.utils

object AppStrings {
    fun get(key: String, lang: String): String {
        val isEn = lang.lowercase() == "en"
        return when (key) {
            "appTitle" -> "PC VPN"
            "connected" -> if (isEn) "Connected" else "Подключено"
            "connecting" -> if (isEn) "Connecting..." else "Подключение..."
            "error" -> if (isEn) "Error" else "Ошибка"
            "disconnected" -> if (isEn) "Disconnected" else "Отключено"
            "connect" -> if (isEn) "Connect" else "Подключить"
            "disconnect" -> if (isEn) "Disconnect" else "Отключить"
            "addProfile" -> if (isEn) "Add Profile" else "Добавить профиль"
            "editProfile" -> if (isEn) "Edit Profile" else "Редактировать профиль"
            "deleteProfile" -> if (isEn) "Delete Profile" else "Удалить профиль"
            "profileName" -> if (isEn) "Profile Name" else "Название профиля"
            "hostAddress" -> if (isEn) "Computer Name or IP" else "Имя компьютера или IP"
            "port" -> if (isEn) "Port (default 4066)" else "Порт (по умолчанию 4066)"
            "loginOptional" -> if (isEn) "Login (optional)" else "Логин (необязательно)"
            "passwordOptional" -> if (isEn) "Password (optional)" else "Пароль (необязательно)"
            "save" -> if (isEn) "Save" else "Сохранить"
            "cancel" -> if (isEn) "Cancel" else "Отмена"
            "logsTitle" -> if (isEn) "Connection Logs" else "Логи соединения"
            "copy" -> if (isEn) "Copy" else "Копировать"
            "clear" -> if (isEn) "Clear" else "Очистить"
            "close" -> if (isEn) "Close" else "Закрыть"
            "noLogs" -> if (isEn) "No logs yet. Click Connect to start diagnostics." else "Логи отсутствуют. Нажмите «Подключить» для старта."
            "enterHostToast" -> if (isEn) "Please enter computer name or IP address" else "Пожалуйста, введите имя компьютера или IP-адрес"
            "addressPrefix" -> if (isEn) "Address" else "Адрес"
            "readyStatus" -> if (isEn) "Ready to connect (SOCKS5 / HTTP)" else "Готов к подключению SOCKS5 / HTTP"
            "establishingStatus" -> if (isEn) "Establishing connection..." else "Установление соединения..."
            "copiedToast" -> if (isEn) "Logs copied to clipboard" else "Логи скопированы в буфер"
            "permissionDenied" -> if (isEn) "VPN permission was denied" else "Разрешение на VPN было отклонено"
            else -> key
        }
    }
}
