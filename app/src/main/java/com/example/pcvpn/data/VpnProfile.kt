package com.example.pcvpn.data

import org.json.JSONObject
import java.util.UUID

data class VpnProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 4066,
    val login: String = "",
    val password: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("host", host)
            put("port", port)
            put("login", login)
            put("password", password)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): VpnProfile {
            return VpnProfile(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Профиль"),
                host = json.optString("host", ""),
                port = json.optInt("port", 4066),
                login = json.optString("login", ""),
                password = json.optString("password", "")
            )
        }
    }
}
