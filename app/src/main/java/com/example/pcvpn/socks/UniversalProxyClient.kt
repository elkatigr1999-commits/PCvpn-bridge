package com.example.pcvpn.socks

import com.example.pcvpn.utils.MdnsResolver
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class UniversalProxyClient(
    private val proxyHost: String,
    private val proxyPort: Int = 4066,
    private val username: String = "",
    private val password: String = ""
) {
    enum class ProxyType { SOCKS5, HTTP, DIRECT }

    companion object {
        @Volatile var cachedProxyType: ProxyType? = null

        fun resetCache() {
            cachedProxyType = null
        }
    }

    data class ProxyConnectionResult(
        val success: Boolean,
        val message: String,
        val socket: Socket? = null,
        val proxyType: ProxyType = ProxyType.SOCKS5
    )

    /**
     * Подключается к прокси-серверу ПК, с мгновенным кэшированием рабочего типа (1 мс отклики)
     */
    fun connectAndTunnel(
        targetHost: String,
        targetPort: Int,
        resolvedProxyAddress: InetAddress? = null,
        protectSocket: ((Socket) -> Boolean)? = null
    ): ProxyConnectionResult {
        val addressToConnect = try {
            resolvedProxyAddress ?: InetAddress.getByName(proxyHost)
        } catch (e: Exception) {
            return ProxyConnectionResult(false, "Не удалось определить адрес хоста ПК ($proxyHost): ${e.localizedMessage}")
        }

        // КЭШИРОВАННЫЙ РЕЖИМ: Если протокол ПК уже определен, подключаемся МГНОВЕННО за 1 мс
        cachedProxyType?.let { mode ->
            try {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                protectSocket?.invoke(socket)
                socket.connect(InetSocketAddress(addressToConnect, proxyPort), 4000)
                socket.soTimeout = 4000

                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val ok = when (mode) {
                    ProxyType.SOCKS5 -> trySocks5Connect(input, output, targetHost, targetPort)
                    ProxyType.HTTP -> tryHttpConnect(input, output, targetHost, targetPort)
                    ProxyType.DIRECT -> true
                }

                if (ok) {
                    socket.soTimeout = 0
                    return ProxyConnectionResult(true, "Успешное подключение ($mode)", socket, mode)
                }
                socket.close()
            } catch (e: Exception) {
                cachedProxyType = null
            }
        }

        // 1. ПОПЫТКА 1: SOCKS5 (Основной протокол)
        try {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.keepAlive = true
            protectSocket?.invoke(socket)

            socket.connect(InetSocketAddress(addressToConnect, proxyPort), 4000)
            socket.soTimeout = 4000

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val socks5Success = trySocks5Connect(input, output, targetHost, targetPort)
            if (socks5Success) {
                socket.soTimeout = 0
                cachedProxyType = ProxyType.SOCKS5
                return ProxyConnectionResult(true, "Успешное SOCKS5 подключение к $proxyHost:$proxyPort", socket, ProxyType.SOCKS5)
            }
            socket.close()
        } catch (ignored: Exception) {}

        // 2. ПОПЫТКА 2: HTTP CONNECT (для Clash / Hiddify / v2rayN / HTTP Custom)
        try {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.keepAlive = true
            protectSocket?.invoke(socket)

            socket.connect(InetSocketAddress(addressToConnect, proxyPort), 4000)
            socket.soTimeout = 4000

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val httpSuccess = tryHttpConnect(input, output, targetHost, targetPort)
            if (httpSuccess) {
                socket.soTimeout = 0
                cachedProxyType = ProxyType.HTTP
                return ProxyConnectionResult(true, "Успешное HTTP CONNECT подключение к $proxyHost:$proxyPort", socket, ProxyType.HTTP)
            }
            socket.close()
        } catch (ignored: Exception) {}

        // 3. ПОПЫТКА 3: Прямой сокет
        if (targetHost == proxyHost || targetHost == addressToConnect.hostAddress) {
            try {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                protectSocket?.invoke(socket)

                socket.connect(InetSocketAddress(addressToConnect, proxyPort), 4000)
                socket.soTimeout = 0
                cachedProxyType = ProxyType.DIRECT
                return ProxyConnectionResult(true, "Прямое подключение к $proxyHost:$proxyPort", socket, ProxyType.DIRECT)
            } catch (e: Exception) {
                return ProxyConnectionResult(false, "Не удалось подключиться к ПК $proxyHost:$proxyPort: ${e.localizedMessage}")
            }
        }

        return ProxyConnectionResult(false, "Прокси ПК $proxyHost:$proxyPort отклонил туннелирование к $targetHost:$targetPort")
    }

    /**
     * Быстрая предполётная проверка доступности порта прокси на ПК
     */
    fun testProxyConnection(
        resolvedProxyAddress: InetAddress? = null,
        protectSocket: ((Socket) -> Boolean)? = null
    ): ProxyConnectionResult {
        return connectAndTunnel("8.8.8.8", 53, resolvedProxyAddress, protectSocket)
    }

    private fun trySocks5Connect(
        input: InputStream,
        output: OutputStream,
        targetHost: String,
        targetPort: Int
    ): Boolean {
        try {
            if (username.isNotEmpty()) {
                output.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                output.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            output.flush()

            val greetingResp = ByteArray(2)
            readFully(input, greetingResp)
            if (greetingResp[0] != 0x05.toByte()) return false

            val method = greetingResp[1]
            if (method == 0x02.toByte() && username.isNotEmpty()) {
                val userBytes = username.toByteArray(StandardCharsets.UTF_8)
                val passBytes = password.toByteArray(StandardCharsets.UTF_8)
                val authBuf = ByteArray(1 + 1 + userBytes.size + 1 + passBytes.size)
                authBuf[0] = 0x01
                authBuf[1] = userBytes.size.toByte()
                System.arraycopy(userBytes, 0, authBuf, 2, userBytes.size)
                val passOff = 2 + userBytes.size
                authBuf[passOff] = passBytes.size.toByte()
                System.arraycopy(passBytes, 0, authBuf, passOff + 1, passBytes.size)

                output.write(authBuf)
                output.flush()

                val authResp = ByteArray(2)
                readFully(input, authResp)
                if (authResp[1] != 0x00.toByte()) return false
            } else if (method != 0x00.toByte()) {
                return false
            }

            val req: ByteArray
            if (MdnsResolver.isIpAddress(targetHost)) {
                val ipBytes = InetAddress.getByName(targetHost).address
                req = ByteArray(4 + 4 + 2)
                req[0] = 0x05
                req[1] = 0x01
                req[2] = 0x00
                req[3] = 0x01
                System.arraycopy(ipBytes, 0, req, 4, 4)
                req[8] = ((targetPort shr 8) and 0xFF).toByte()
                req[9] = (targetPort and 0xFF).toByte()
            } else {
                val hostBytes = targetHost.toByteArray(StandardCharsets.UTF_8)
                req = ByteArray(4 + 1 + hostBytes.size + 2)
                req[0] = 0x05
                req[1] = 0x01
                req[2] = 0x00
                req[3] = 0x03
                req[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
                val portOffset = 5 + hostBytes.size
                req[portOffset] = ((targetPort shr 8) and 0xFF).toByte()
                req[portOffset + 1] = (targetPort and 0xFF).toByte()
            }

            output.write(req)
            output.flush()

            val respHeader = ByteArray(4)
            readFully(input, respHeader)
            if (respHeader[1] != 0x00.toByte()) return false

            val atyp = respHeader[3]
            val skipLen = when (atyp) {
                0x01.toByte() -> 4 + 2
                0x03.toByte() -> {
                    val lenByte = ByteArray(1)
                    readFully(input, lenByte)
                    (lenByte[0].toInt() and 0xFF) + 2
                }
                0x04.toByte() -> 16 + 2
                else -> 6
            }
            val dummy = ByteArray(skipLen)
            readFully(input, dummy)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun tryHttpConnect(
        input: InputStream,
        output: OutputStream,
        targetHost: String,
        targetPort: Int
    ): Boolean {
        try {
            val connectReq = "CONNECT $targetHost:$targetPort HTTP/1.1\r\nHost: $targetHost:$targetPort\r\n\r\n"
            output.write(connectReq.toByteArray(StandardCharsets.UTF_8))
            output.flush()

            val respHeader = ByteArray(12)
            readFully(input, respHeader)
            val respStr = String(respHeader, StandardCharsets.UTF_8)
            if (respStr.contains("200")) {
                val buf = ByteArray(1)
                var last4 = 0
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    last4 = (last4 shl 8) or (buf[0].toInt() and 0xFF)
                    if (last4 == 0x0D0A0D0A) break
                }
                return true
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw java.io.EOFException("Конец потока")
            offset += read
        }
    }
}
