package com.example.pcvpn.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class ProfileManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pcvpn_profiles_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROFILES = "key_profiles"
        private const val KEY_SELECTED_ID = "key_selected_id"
    }

    fun getProfiles(): List<VpnProfile> {
        val jsonStr = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val list = mutableListOf<VpnProfile>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(VpnProfile.fromJson(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveProfiles(profiles: List<VpnProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun addOrUpdateProfile(profile: VpnProfile) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        saveProfiles(current)
        setSelectedProfileId(profile.id)
    }

    fun deleteProfile(id: String) {
        val current = getProfiles().toMutableList()
        current.removeAll { it.id == id }
        saveProfiles(current)
        if (getSelectedProfileId() == id) {
            setSelectedProfileId(current.firstOrNull()?.id ?: "")
        }
    }

    fun getSelectedProfileId(): String {
        return prefs.getString(KEY_SELECTED_ID, "") ?: ""
    }

    fun setSelectedProfileId(id: String) {
        prefs.edit().putString(KEY_SELECTED_ID, id).apply()
    }

    fun getSelectedProfile(): VpnProfile? {
        val profiles = getProfiles()
        val selectedId = getSelectedProfileId()
        return profiles.find { it.id == selectedId } ?: profiles.firstOrNull()
    }

    fun isDarkTheme(): Boolean {
        return prefs.getBoolean("key_dark_theme", false)
    }

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("key_dark_theme", isDark).apply()
    }
}
