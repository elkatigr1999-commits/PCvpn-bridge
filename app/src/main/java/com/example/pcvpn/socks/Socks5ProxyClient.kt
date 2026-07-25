package com.example.pcvpn.socks

import com.example.pcvpn.utils.MdnsResolver
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class Socks5ProxyClient(
    private val proxyHost: String,
    private val proxyPort: Int = 4066,
    private val username: String,
    private val password: String
) {
    companion object {
        const val SOCKS_VERSION: Byte = 0x05
        const val AUTH_METHOD_PASSWORD: Byte = 0x02
        const val AUTH_METHOD_NO_AUTH: Byte = 0x00
    }

    data class ProxyHandshakeResult(
        val success: Boolean,
        val message: String,
        val socket: Socket? = null
    )

    /**
     * Выполняет рукопожатие SOCKS5 с защитой сокета до подключения.
     */
    fun connectAndAuthenticate(
        resolvedAddress: InetAddress? = null,
        protectSocket: ((Socket) -> Boolean)? = null
    ): ProxyHandshakeResult {
        try {
            val socket = Socket()
            // Важно: защищаем сокет до вызова connect(), чтобы исключить его из маршрутизации VPN
            protectSocket?.invoke(socket)

            val addressToConnect = resolvedAddress ?: InetAddress.getByName(proxyHost)
            socket.connect(InetSocketAddress(addressToConnect, proxyPort), 10000)

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Клиентское приветствие (RFC 1928): Версия 5, методы 0x00 (без аутентификации) и 0x02 (пароль)
            output.write(byteArrayOf(SOCKS_VERSION, 0x02, AUTH_METHOD_NO_AUTH, AUTH_METHOD_PASSWORD))
            output.flush()

            val greetingResponse = ByteArray(2)
            readFully(input, greetingResponse)

            if (greetingResponse[0] != SOCKS_VERSION) {
                socket.close()
                return ProxyHandshakeResult(false, "Неподдерживаемая версия SOCKS: ${greetingResponse[0]}")
            }

            val selectedMethod = greetingResponse[1]
            if (selectedMethod == AUTH_METHOD_PASSWORD) {
                // 2. Аутентификация по логину и паролю (RFC 1929)
                val authResult = performUserPassAuth(input, output)
                if (!authResult.success) {
                    socket.close()
                    return authResult
                }
            } else if (selectedMethod != AUTH_METHOD_NO_AUTH) {
                socket.close()
                return ProxyHandshakeResult(false, "Сервер SOCKS5 отклонил методы аутентификации (код: $selectedMethod)")
            }

            return ProxyHandshakeResult(true, "Успешная аутентификация SOCKS5 ($proxyHost:$proxyPort)", socket)

        } catch (e: Exception) {
            return ProxyHandshakeResult(false, "Ошибка подключения: ${e.localizedMessage}")
        }
    }

    /**
     * Отправляет команду SOCKS5 CONNECT (RFC 1928) с поддержкой ATYP IPv4 (0x01) и Domain (0x03)
     */
    fun sendConnectCommand(socket: Socket, targetHost: String, targetPort: Int): Boolean {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val req: ByteArray
            if (MdnsResolver.isIpAddress(targetHost)) {
                // ATYP = 0x01 (IPv4 4 бинарных байта)
                val ipBytes = InetAddress.getByName(targetHost).address
                req = ByteArray(4 + 4 + 2)
                req[0] = SOCKS_VERSION
                req[1] = 0x01 // CMD_CONNECT
                req[2] = 0x00 // RSV
                req[3] = 0x01 // ATYP_IPV4
                System.arraycopy(ipBytes, 0, req, 4, 4)
                req[8] = ((targetPort shr 8) and 0xFF).toByte()
                req[9] = (targetPort and 0xFF).toByte()
            } else {
                // ATYP = 0x03 (Доменное имя)
                val hostBytes = targetHost.toByteArray(StandardCharsets.UTF_8)
                req = ByteArray(4 + 1 + hostBytes.size + 2)
                req[0] = SOCKS_VERSION
                req[1] = 0x01 // CMD_CONNECT
                req[2] = 0x00 // RSV
                req[3] = 0x03 // ATYP_DOMAIN
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

            if (respHeader[1] != 0x00.toByte()) {
                return false
            }

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

    private fun performUserPassAuth(input: InputStream, output: OutputStream): ProxyHandshakeResult {
        val userBytes = username.toByteArray(StandardCharsets.UTF_8)
        val passBytes = password.toByteArray(StandardCharsets.UTF_8)

        // Подподпротокол аутентификации RFC 1929: [Ver(0x01), ULen, User, PLen, Pass]
        val authBuffer = ByteArray(1 + 1 + userBytes.size + 1 + passBytes.size)
        authBuffer[0] = 0x01
        authBuffer[1] = userBytes.size.toByte()
        System.arraycopy(userBytes, 0, authBuffer, 2, userBytes.size)
        val passOffset = 2 + userBytes.size
        authBuffer[passOffset] = passBytes.size.toByte()
        System.arraycopy(passBytes, 0, authBuffer, passOffset + 1, passBytes.size)

        output.write(authBuffer)
        output.flush()

        val authResponse = ByteArray(2)
        readFully(input, authResponse)

        if (authResponse[1] != 0x00.toByte()) {
            return ProxyHandshakeResult(false, "Ошибка аутентификации SOCKS5 (логин/пароль отклонены, код: ${authResponse[1]})")
        }

        return ProxyHandshakeResult(true, "Аутентификация успешна")
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw java.io.EOFException("Неожиданное закрытие соединения во время рукопожатия SOCKS5")
            offset += read
        }
    }
}
