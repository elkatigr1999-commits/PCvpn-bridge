package com.example.pcvpn.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.pcvpn.MainActivity
import com.example.pcvpn.R
import com.example.pcvpn.data.ProfileManager
import com.example.pcvpn.utils.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@RequiresApi(Build.VERSION_CODES.N)
class VpnTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(SocksVpnService.connectionState.value)
    }

    override fun onClick() {
        super.onClick()
        val currentState = SocksVpnService.connectionState.value
        val profileManager = ProfileManager(this)
        val lang = profileManager.getAppLanguage()
        val tile = qsTile

        if (currentState is SocksVpnService.VpnState.Connected || currentState is SocksVpnService.VpnState.Connecting) {
            // 1. Мгновенная визуальная анимация отключения плитки
            tile?.state = Tile.STATE_INACTIVE
            tile?.label = "PC VPN"
            tile?.subtitle = AppStrings.get("disconnected", lang)
            tile?.updateTile()

            val intent = Intent(this, SocksVpnService::class.java).apply {
                action = SocksVpnService.ACTION_DISCONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            val selectedProfile = profileManager.getSelectedProfile()
            if (selectedProfile != null && selectedProfile.host.isNotBlank()) {
                val prepareIntent = VpnService.prepare(this)
                if (prepareIntent != null) {
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val pendingIntent = PendingIntent.getActivity(
                            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
                        )
                        startActivityAndCollapse(pendingIntent)
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(mainIntent)
                    }
                } else {
                    // 2. Мгновенная визуальная анимация подсветки активной плитки
                    tile?.state = Tile.STATE_ACTIVE
                    tile?.label = "PC VPN"
                    tile?.subtitle = AppStrings.get("connecting", lang)
                    tile?.updateTile()

                    val intent = Intent(this, SocksVpnService::class.java).apply {
                        action = SocksVpnService.ACTION_CONNECT
                        putExtra(SocksVpnService.EXTRA_HOST, selectedProfile.host)
                        putExtra(SocksVpnService.EXTRA_PORT, selectedProfile.port)
                        putExtra(SocksVpnService.EXTRA_USER, selectedProfile.login)
                        putExtra(SocksVpnService.EXTRA_PASS, selectedProfile.password)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            } else {
                // Если профиль не настроен — открываем главное окно
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(mainIntent)
                }
            }
        }
    }

    private fun updateTileState(state: SocksVpnService.VpnState) {
        val tile = qsTile ?: return
        val profileManager = ProfileManager(this)
        val lang = profileManager.getAppLanguage()

        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_vpn_tile)

        when (state) {
            is SocksVpnService.VpnState.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "PC VPN"
                tile.subtitle = AppStrings.get("connected", lang)
            }
            is SocksVpnService.VpnState.Connecting -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "PC VPN"
                tile.subtitle = AppStrings.get("connecting", lang)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "PC VPN"
                tile.subtitle = AppStrings.get("disconnected", lang)
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        fun updateTile(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestListeningState(
                        context,
                        ComponentName(context, VpnTileService::class.java)
                    )
                } catch (ignored: Exception) {}
            }
        }
    }
}
