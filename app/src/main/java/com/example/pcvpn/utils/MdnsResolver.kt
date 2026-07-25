package com.example.pcvpn.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

object MdnsResolver {
    private const val TAG = "MdnsResolver"

    fun isIpAddress(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        return Patterns.IP_ADDRESS.matcher(trimmed).matches()
    }

    fun formatHost(inputHost: String): String {
        val trimmed = inputHost.trim()
        if (trimmed.isEmpty()) return ""
        if (isIpAddress(trimmed)) return trimmed
        return trimmed.lowercase()
    }

    /**
     * Разрешает имя хоста (mDNS RFC 6762, LLMNR, NetBIOS или IP) в InetAddress.
     */
    suspend fun resolveHost(context: Context, host: String): InetAddress? = withContext(Dispatchers.IO) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return@withContext null

        if (isIpAddress(trimmed)) {
            try {
                return@withContext InetAddress.getByName(trimmed)
            } catch (e: Exception) {
                return@withContext null
            }
        }

        // 1. Попытка через стандартный системный DNS / NetBIOS
        try {
            val addr = InetAddress.getByName(trimmed)
            if (addr != null && !addr.isLoopbackAddress) {
                return@withContext addr
            }
        } catch (ignored: Exception) {}

        // 2. Нативный mDNS (RFC 6762) отправкой пакета на 224.0.0.251:5353
        val cleanHost = if (trimmed.endsWith(".local", ignoreCase = true)) {
            trimmed.substringBeforeLast(".local")
        } else {
            trimmed
        }

        val mdnsName = "$cleanHost.local"
        val resolvedMdns = queryMdnsMulticast(context, mdnsName)
        if (resolvedMdns != null) {
            return@withContext resolvedMdns
        }

        // 3. Попытка без суффикса .local
        try {
            val addr = InetAddress.getByName(cleanHost)
            if (addr != null && !addr.isLoopbackAddress) {
                return@withContext addr
            }
        } catch (ignored: Exception) {}

        return@withContext null
    }

    private fun queryMdnsMulticast(context: Context, fullDomain: String): InetAddress? {
        var multicastLock: WifiManager.MulticastLock? = null
        var socket: DatagramSocket? = null
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("pcvpn_mdns_query")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            val group = InetAddress.getByName("224.0.0.251")
            val port = 5353

            val queryBytes = buildMdnsQuery(fullDomain)
            val packet = DatagramPacket(queryBytes, queryBytes.size, group, port)

            socket = DatagramSocket()
            socket.soTimeout = 2500
            socket.send(packet)

            val recvBuf = ByteArray(1500)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 2500) {
                try {
                    socket.receive(recvPacket)
                    val senderIp = recvPacket.address
                    if (senderIp != null && !senderIp.isLoopbackAddress) {
                        val parsedIp = parseMdnsAnswer(recvPacket.data, recvPacket.length)
                        if (parsedIp != null) {
                            return parsedIp
                        }
                        if (senderIp is java.net.Inet4Address) {
                            return senderIp
                        }
                    }
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка mDNS запроса $fullDomain: ${e.message}")
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
            try {
                if (multicastLock != null && multicastLock.isHeld) {
                    multicastLock.release()
                }
            } catch (ignored: Exception) {}
        }
        return null
    }

    private fun buildMdnsQuery(domain: String): ByteArray {
        val buf = ByteBuffer.allocate(512)
        buf.putShort(0)
        buf.putShort(0x0000)
        buf.putShort(1)
        buf.putShort(0)
        buf.putShort(0)
        buf.putShort(0)

        val parts = domain.split(".")
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            buf.put(bytes.size.toByte())
            buf.put(bytes)
        }
        buf.put(0.toByte())

        buf.putShort(1) // Type A
        buf.putShort(1) // Class IN

        val result = ByteArray(buf.position())
        buf.flip()
        buf.get(result)
        return result
    }

    private fun parseMdnsAnswer(data: ByteArray, len: Int): InetAddress? {
        try {
            if (len < 12) return null
            val answers = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            if (answers == 0) return null

            var pos = 12
            val questions = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            for (q in 0 until questions) {
                while (pos < len && data[pos] != 0.toByte()) {
                    val labelLen = data[pos].toInt() and 0xFF
                    if ((labelLen and 0xC0) == 0xC0) {
                        pos += 2
                        break
                    }
                    pos += 1 + labelLen
                }
                if (pos < len && data[pos] == 0.toByte()) pos++
                pos += 4
            }

            for (a in 0 until answers) {
                if (pos >= len) break
                while (pos < len && data[pos] != 0.toByte()) {
                    val labelLen = data[pos].toInt() and 0xFF
                    if ((labelLen and 0xC0) == 0xC0) {
                        pos += 2
                        break
                    }
                    pos += 1 + labelLen
                }
                if (pos < len && data[pos] == 0.toByte()) pos++

                if (pos + 10 > len) break
                val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                val rdLen = ((data[pos + 8].toInt() and 0xFF) shl 8) or (data[pos + 9].toInt() and 0xFF)
                pos += 10

                if (type == 1 && rdLen == 4 && pos + 4 <= len) {
                    val ipBytes = ByteArray(4)
                    System.arraycopy(data, pos, ipBytes, 0, 4)
                    return InetAddress.getByAddress(ipBytes)
                }
                pos += rdLen
            }
        } catch (ignored: Exception) {}
        return null
    }
}
