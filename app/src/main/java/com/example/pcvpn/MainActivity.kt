package com.example.pcvpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.pcvpn.data.ProfileManager
import com.example.pcvpn.data.VpnProfile
import com.example.pcvpn.service.SocksVpnService
import com.example.pcvpn.ui.MainScreen
import com.example.pcvpn.ui.theme.PCVPNTheme

class MainActivity : ComponentActivity() {

    private lateinit var profileManager: ProfileManager
    private var pendingConnectAction: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingConnectAction?.invoke()
            pendingConnectAction = null
        } else {
            Toast.makeText(this, "Разрешение на VPN было отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileManager = ProfileManager(this)

        setContent {
            var isDarkTheme by remember { mutableStateOf(profileManager.isDarkTheme()) }
            var currentLanguage by remember { mutableStateOf(profileManager.getAppLanguage()) }
            var profiles by remember { mutableStateOf(profileManager.getProfiles()) }
            var selectedProfile by remember { mutableStateOf(profileManager.getSelectedProfile()) }

            PCVPNTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vpnState by SocksVpnService.connectionState.collectAsState()

                    MainScreen(
                        vpnState = vpnState,
                        isDarkTheme = isDarkTheme,
                        currentLanguage = currentLanguage,
                        profiles = profiles,
                        selectedProfile = selectedProfile,
                        onToggleTheme = {
                            val nextTheme = !isDarkTheme
                            isDarkTheme = nextTheme
                            profileManager.setDarkTheme(nextTheme)
                        },
                        onToggleLanguage = {
                            val nextLang = if (currentLanguage == "en") "ru" else "en"
                            currentLanguage = nextLang
                            profileManager.setAppLanguage(nextLang)
                        },
                        onSelectProfile = { profile ->
                            profileManager.setSelectedProfileId(profile.id)
                            selectedProfile = profile
                        },
                        onSaveProfile = { profile ->
                            profileManager.addOrUpdateProfile(profile)
                            profiles = profileManager.getProfiles()
                            selectedProfile = profileManager.getSelectedProfile()
                        },
                        onDeleteProfile = { profileId ->
                            profileManager.deleteProfile(profileId)
                            profiles = profileManager.getProfiles()
                            selectedProfile = profileManager.getSelectedProfile()
                        },
                        onConnectClick = { profile ->
                            if (profile.host.isBlank()) {
                                val msg = com.example.pcvpn.utils.AppStrings.get("enterHostToast", currentLanguage)
                                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                                return@MainScreen
                            }
                            requestVpnAndConnect(profile.host, profile.port, profile.login, profile.password)
                        },
                        onDisconnectClick = {
                            disconnectVpn()
                        }
                    )
                }
            }
        }
    }

    private fun requestVpnAndConnect(host: String, port: Int, login: String, pass: String) {
        val connectAction: () -> Unit = {
            val intent = Intent(this, SocksVpnService::class.java).apply {
                action = SocksVpnService.ACTION_CONNECT
                putExtra(SocksVpnService.EXTRA_HOST, host)
                putExtra(SocksVpnService.EXTRA_PORT, port)
                putExtra(SocksVpnService.EXTRA_USER, login)
                putExtra(SocksVpnService.EXTRA_PASS, pass)
            }
            startService(intent)
        }

        val vpnPrepareIntent = VpnService.prepare(this)
        if (vpnPrepareIntent != null) {
            pendingConnectAction = connectAction
            vpnPermissionLauncher.launch(vpnPrepareIntent)
        } else {
            connectAction()
        }
    }

    private fun disconnectVpn() {
        val intent = Intent(this, SocksVpnService::class.java).apply {
            action = SocksVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }
}
