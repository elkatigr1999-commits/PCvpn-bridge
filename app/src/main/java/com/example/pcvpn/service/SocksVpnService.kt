package com.example.pcvpn.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.pcvpn.MainActivity
import com.example.pcvpn.socks.LocalProxyRelay
import com.example.pcvpn.socks.Socks5Tun2Socks
import com.example.pcvpn.socks.UniversalProxyClient
import com.example.pcvpn.utils.AppLogger
import com.example.pcvpn.utils.MdnsResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.FileOutputStream

class SocksVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.pcvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.pcvpn.DISCONNECT"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_USER = "extra_user"
        const val EXTRA_PASS = "extra_pass"

        const val DEFAULT_PORT = 4066

        private val _connectionState = MutableStateFlow<VpnState>(VpnState.Disconnected)
        val connectionState: StateFlow<VpnState> = _connectionState
    }

    sealed class VpnState {
        object Disconnected : VpnState()
        object Connecting : VpnState()
        data class Connected(val host: String, val port: Int, val ip: String) : VpnState()
        data class Error(val message: String) : VpnState()
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var localProxyRelay: LocalProxyRelay? = null
    private var tun2SocksEngine: Socks5Tun2Socks? = null
    private val vpnMutex = Mutex()

    private fun updateState(newState: VpnState) {
        _connectionState.value = newState
        try {
            VpnTileService.updateTile(this)
            com.example.pcvpn.receiver.VpnWidgetProvider.updateAllWidgets(this)
        } catch (ignored: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val rawHost = intent.getStringExtra(EXTRA_HOST) ?: ""
                val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                val user = intent.getStringExtra(EXTRA_USER) ?: ""
                val pass = intent.getStringExtra(EXTRA_PASS) ?: ""

                startForegroundServiceNotification("Подключение к $rawHost:$port...")
                updateState(VpnState.Connecting)

                serviceScope.launch {
                    vpnMutex.withLock {
                        startVpnInternal(rawHost, port, user, pass)
                    }
                }
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    vpnMutex.withLock {
                        stopVpnInternal()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startVpnInternal(rawHost: String, port: Int, user: String, pass: String) {
        stopVpnInternal() // Чисто и полностью останавливаем все старые сокеты

        val host = MdnsResolver.formatHost(rawHost)
        if (host.isEmpty()) {
            updateState(VpnState.Error("Хост или IP-адрес не указан"))
            return
        }

        updateState(VpnState.Connecting)
        UniversalProxyClient.resetCache()
        startForegroundServiceNotification("Подключение к $host:$port...")

        AppLogger.i("VPN", "Старт подключения к $host:$port...")

        try {
            // 1. Разрешение адреса (mDNS или IP)
            val resolvedIp = MdnsResolver.resolveHost(applicationContext, host)
            val targetIpStr = resolvedIp?.hostAddress ?: host
            AppLogger.s("VPN", "Адрес ПК $host разрешен: $targetIpStr")

            // 2. Предварительная проверка связи с ПК
            val universalClient = UniversalProxyClient(host, port, user, pass)
            val testResult = universalClient.testProxyConnection(
                resolvedProxyAddress = resolvedIp,
                protectSocket = { socket -> protect(socket) }
            )

            if (!testResult.success) {
                AppLogger.e("VPN", "Ошибка рукопожатия с ПК: ${testResult.message}")
                updateState(VpnState.Error(testResult.message))
                stopSelf()
                return
            }

            AppLogger.s("VPN", "Прокси ПК доступен (${testResult.proxyType.name})")

            // 3. Запуск локального прокси-реле
            val relay = LocalProxyRelay(
                vpnService = this@SocksVpnService,
                remoteProxyHost = host,
                remoteProxyPort = port,
                username = user,
                password = pass,
                localPort = 10808,
                resolvedProxyAddress = resolvedIp
            )
            localProxyRelay = relay
            relay.start()

            // 4. Создаем виртуальный TUN-интерфейс Android VpnService
            val builder = Builder()
                .addAddress("10.0.0.2", 24)
                .addAddress("fd00:1:2::2", 64)
                .addRoute("0.0.0.0", 1)
                .addRoute("128.0.0.0", 1)
                .addRoute("::", 1)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setSession("PC VPN ($host)")
                .setMtu(1500)

            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val method = ProxyInfo::class.java.getMethod("createDirectProxy", String::class.java, Int::class.javaPrimitiveType)
                    val proxyInfo = method.invoke(null, "127.0.0.1", 10808) as? ProxyInfo
                    if (proxyInfo != null) {
                        builder.setHttpProxy(proxyInfo)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val pfd = builder.establish()
            vpnInterface = pfd

            if (pfd == null) {
                updateState(VpnState.Error("Не удалось создать VPN интерфейс"))
                stopSelf()
                return
            }

            // 5. Запуск движка туннелирования пакетов
            val vpnInput = FileInputStream(pfd.fileDescriptor)
            val vpnOutput = FileOutputStream(pfd.fileDescriptor)

            val engine = Socks5Tun2Socks(
                vpnService = this@SocksVpnService,
                proxyHost = host,
                proxyPort = port,
                username = user,
                password = pass,
                resolvedProxyAddress = resolvedIp
            )
            tun2SocksEngine = engine
            engine.start(vpnInput, vpnOutput)

            updateState(VpnState.Connected(host, port, targetIpStr))
            updateNotification("Подключено к $host:$port (${testResult.proxyType.name})")

        } catch (e: Exception) {
            updateState(VpnState.Error("Ошибка VPN: ${e.localizedMessage}"))
            stopSelf()
        }
    }

    private fun stopVpnInternal() {
        try {
            tun2SocksEngine?.stop()
            localProxyRelay?.stop()
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tun2SocksEngine = null
        localProxyRelay = null
        vpnInterface = null
        updateState(VpnState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundServiceNotification(contentTitle: String) {
        val channelId = "pcvpn_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PCVPN Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("PC VPN")
            .setContentText(contentTitle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun updateNotification(contentTitle: String) {
        startForegroundServiceNotification(contentTitle)
    }

    override fun onDestroy() {
        runBlocking {
            vpnMutex.withLock {
                stopVpnInternal()
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
