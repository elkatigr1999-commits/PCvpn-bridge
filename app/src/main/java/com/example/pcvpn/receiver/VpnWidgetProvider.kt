package com.example.pcvpn.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.widget.RemoteViews
import com.example.pcvpn.MainActivity
import com.example.pcvpn.R
import com.example.pcvpn.data.ProfileManager
import com.example.pcvpn.service.SocksVpnService
import com.example.pcvpn.utils.AppStrings

class VpnWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TOGGLE) {
            val currentState = SocksVpnService.connectionState.value
            val profileManager = ProfileManager(context)

            if (currentState is SocksVpnService.VpnState.Connected || currentState is SocksVpnService.VpnState.Connecting) {
                val disconnectIntent = Intent(context, SocksVpnService::class.java).apply {
                    action = SocksVpnService.ACTION_DISCONNECT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(disconnectIntent)
                } else {
                    context.startService(disconnectIntent)
                }
            } else {
                val selectedProfile = profileManager.getSelectedProfile()
                if (selectedProfile != null && selectedProfile.host.isNotBlank()) {
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent != null) {
                        val mainIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(mainIntent)
                    } else {
                        val connectIntent = Intent(context, SocksVpnService::class.java).apply {
                            action = SocksVpnService.ACTION_CONNECT
                            putExtra(SocksVpnService.EXTRA_HOST, selectedProfile.host)
                            putExtra(SocksVpnService.EXTRA_PORT, selectedProfile.port)
                            putExtra(SocksVpnService.EXTRA_USER, selectedProfile.login)
                            putExtra(SocksVpnService.EXTRA_PASS, selectedProfile.password)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(connectIntent)
                        } else {
                            context.startService(connectIntent)
                        }
                    }
                } else {
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(mainIntent)
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE = "com.example.pcvpn.WIDGET_TOGGLE"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, VpnWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id)
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.vpn_widget)
            val currentState = SocksVpnService.connectionState.value
            val profileManager = ProfileManager(context)
            val lang = profileManager.getAppLanguage()
            val selectedProfile = profileManager.getSelectedProfile()

            val isConnected = currentState is SocksVpnService.VpnState.Connected
            val isConnecting = currentState is SocksVpnService.VpnState.Connecting

            if (isConnected) {
                views.setTextViewText(R.id.widget_status_badge, "ON")
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_on)
                views.setTextViewText(R.id.widget_profile_info, selectedProfile?.name ?: AppStrings.get("connected", lang))
                views.setTextViewText(R.id.widget_toggle_button, AppStrings.get("disconnect", lang))
                views.setInt(R.id.widget_toggle_button, "setBackgroundColor", Color.parseColor("#D32F2F"))
                views.setTextColor(R.id.widget_toggle_button, Color.WHITE)
            } else if (isConnecting) {
                views.setTextViewText(R.id.widget_status_badge, "WAIT")
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_off)
                views.setTextViewText(R.id.widget_profile_info, AppStrings.get("connecting", lang))
                views.setTextViewText(R.id.widget_toggle_button, AppStrings.get("connecting", lang))
                views.setInt(R.id.widget_toggle_button, "setBackgroundColor", Color.parseColor("#E65100"))
                views.setTextColor(R.id.widget_toggle_button, Color.WHITE)
            } else {
                views.setTextViewText(R.id.widget_status_badge, "OFF")
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_off)
                views.setTextViewText(R.id.widget_profile_info, selectedProfile?.name ?: AppStrings.get("readyStatus", lang))
                views.setTextViewText(R.id.widget_toggle_button, AppStrings.get("connect", lang))
                views.setInt(R.id.widget_toggle_button, "setBackgroundColor", Color.parseColor("#BB86FC"))
                views.setTextColor(R.id.widget_toggle_button, Color.BLACK)
            }

            val toggleIntent = Intent(context, VpnWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TOGGLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_button, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
