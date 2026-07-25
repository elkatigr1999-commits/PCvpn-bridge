package com.example.pcvpn.socks

import android.net.VpnService
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class LocalProxyRelay(
    private val vpnService: VpnService,
    private val remoteProxyHost: String,
    private val remoteProxyPort: Int,
    private val username: String,
    private val password: String,
    val localPort: Int = 10808,
    private val resolvedProxyAddress: InetAddress? = null
) {
    companion object {
        private const val TAG = "LocalProxyRelay"
    }

    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private var threadPool = Executors.newCachedThreadPool()

    fun start() {
        stop() // Закрываем старый сервер перед повторным стартом
        isRunning = true
        threadPool = Executors.newCachedThreadPool()

        threadPool.execute {
            try {
                val s = ServerSocket()
                s.reuseAddress = true // Позволяет немедленно переиспользовать порт 10808 при быстром переключении
                s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), localPort), 100)
                serverSocket = s
                Log.d(TAG, "Локальный прокси-реле запущен на 127.0.0.1:$localPort")

                while (isRunning && !s.isClosed) {
                    val clientSocket = try {
                        s.accept()
                    } catch (e: Exception) {
                        if (!isRunning || s.isClosed) break
                        continue
                    }

                    try {
                        clientSocket.tcpNoDelay = true
                        clientSocket.keepAlive = true
                        threadPool.execute {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        try { clientSocket.close() } catch (ignored: Exception) {}
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Ошибка локального сервера реле: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
        try {
            threadPool.shutdownNow()
        } catch (ignored: Exception) {}
    }

    private fun handleClient(clientSocket: Socket) {
        var remoteSocket: Socket? = null
        try {
            clientSocket.soTimeout = 10000
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val firstByte = clientIn.read()
            if (firstByte == -1) {
                clientSocket.close()
                return
            }

            var targetHost = ""
            var targetPort = 80
            var isSocks5Client = false

            if (firstByte == 0x05) {
                // SOCKS5 Локальный клиент
                isSocks5Client = true
                val authMethodCount = clientIn.read()
                val methods = ByteArray(authMethodCount)
                readFully(clientIn, methods)
                clientOut.write(byteArrayOf(0x05, 0x00))

                val reqHeader = ByteArray(4)
                readFully(clientIn, reqHeader)
                val atyp = reqHeader[3].toInt() and 0xFF

                when (atyp) {
                    0x01 -> { // IPv4
                        val ipBytes = ByteArray(4)
                        readFully(clientIn, ipBytes)
                        targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                    }
                    0x03 -> { // Domain
                        val len = clientIn.read()
                        val domainBytes = ByteArray(len)
                        readFully(clientIn, domainBytes)
                        targetHost = String(domainBytes, StandardCharsets.UTF_8)
                    }
                    0x04 -> { // IPv6
                        val ipBytes = ByteArray(16)
                        readFully(clientIn, ipBytes)
                        targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                    }
                }

                val portBytes = ByteArray(2)
                readFully(clientIn, portBytes)
                targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            } else {
                // HTTP / HTTPS CONNECT Локальный клиент
                isSocks5Client = false
                val requestLineBytes = ByteArray(4096)
                requestLineBytes[0] = firstByte.toByte()
                var readLen = 1
                while (readLen < requestLineBytes.size) {
                    val b = clientIn.read()
                    if (b == -1) break
                    requestLineBytes[readLen++] = b.toByte()
                    if (readLen >= 4 &&
                        requestLineBytes[readLen - 4] == '\r'.code.toByte() &&
                        requestLineBytes[readLen - 3] == '\n'.code.toByte() &&
                        requestLineBytes[readLen - 2] == '\r'.code.toByte() &&
                        requestLineBytes[readLen - 1] == '\n'.code.toByte()
                    ) {
                        break
                    }
                }

                val requestStr = String(requestLineBytes, 0, readLen, StandardCharsets.UTF_8)
                val lines = requestStr.split("\r\n")
                if (lines.isEmpty()) {
                    clientSocket.close()
                    return
                }

                val firstLineParts = lines[0].split(" ")
                if (firstLineParts.size >= 2) {
                    val method = firstLineParts[0]
                    val uri = firstLineParts[1]
                    if (method.equals("CONNECT", ignoreCase = true)) {
                        val hostPort = uri.split(":")
                        targetHost = hostPort[0]
                        targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443
                    } else {
                        val hostHeader = lines.find { it.startsWith("Host:", ignoreCase = true) }
                        if (hostHeader != null) {
                            val hostPort = hostHeader.substring(5).trim().split(":")
                            targetHost = hostPort[0]
                            targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 80 else 80
                        }
                    }
                }
            }

            if (targetHost.isEmpty()) {
                clientSocket.close()
                return
            }

            // Используем заранее разрешенный IP-адрес ПК
            val universalClient = UniversalProxyClient(remoteProxyHost, remoteProxyPort, username, password)
            val connResult = universalClient.connectAndTunnel(
                targetHost = targetHost,
                targetPort = targetPort,
                resolvedProxyAddress = resolvedProxyAddress,
                protectSocket = { socket -> vpnService.protect(socket) }
            )

            if (!connResult.success || connResult.socket == null) {
                if (isSocks5Client) {
                    clientOut.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                } else {
                    clientOut.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                }
                clientOut.flush()
                clientSocket.close()
                return
            }

            remoteSocket = connResult.socket
            remoteSocket.tcpNoDelay = true
            remoteSocket.keepAlive = true

            // 2. Отвечаем клиенту об успешном установлении туннеля
            if (isSocks5Client) {
                val resp = byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 0)
                clientOut.write(resp)
                clientOut.flush()
            } else {
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                clientOut.flush()
            }

            clientSocket.soTimeout = 0
            remoteSocket.soTimeout = 0

            // 3. Двунаправленная трансляция
            val t1 = Thread { pipeStream(clientIn, remoteSocket.getOutputStream()) }
            val t2 = Thread { pipeStream(remoteSocket.getInputStream(), clientOut) }
            t1.start()
            t2.start()
            t1.join()
            t2.join()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка связи с прокси $remoteProxyHost:$remoteProxyPort - ${e.message}")
        } finally {
            try { clientSocket.close() } catch (ignored: Exception) {}
            try { remoteSocket?.close() } catch (ignored: Exception) {}
        }
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(32768)
        try {
            while (isRunning) {
                val r = input.read(buffer)
                if (r <= 0) break
                output.write(buffer, 0, r)
                output.flush()
            }
        } catch (ignored: Exception) {
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
