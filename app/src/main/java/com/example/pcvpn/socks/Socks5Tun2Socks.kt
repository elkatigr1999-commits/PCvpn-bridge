package com.example.pcvpn.socks

import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class Socks5Tun2Socks(
    private val vpnService: VpnService,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val username: String,
    private val password: String,
    private val resolvedProxyAddress: InetAddress? = null
) {
    companion object {
        private const val TAG = "Socks5Tun2Socks"
        private const val MAX_TCP_PAYLOAD_SIZE = 1460 // MTU 1500 - 20 (IP) - 20 (TCP)
    }

    private var isRunning = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeTcpSessions = ConcurrentHashMap<String, TcpSession>()

    // Таблица Fake-IP
    private val fakeIpToDomain = ConcurrentHashMap<String, String>()
    private val domainToFakeIp = ConcurrentHashMap<String, String>()
    private val fakeIpCounter = AtomicInteger(1)

    private enum class SessionState { CONNECTING, CONNECTED, CLOSED }

    private class TcpSession(
        val key: String,
        val srcIp: String,
        val srcPort: Int,
        val dstIp: String,
        val targetHost: String,
        val dstPort: Int,
        var clientSeq: Long
    ) {
        var mySeq: Long = 1000L
        var socket: Socket? = null
        var socksInput: InputStream? = null
        var socksOutput: OutputStream? = null
        @Volatile var state: SessionState = SessionState.CONNECTING
        val pendingPayloads = ConcurrentLinkedQueue<ByteArray>()
    }

    fun start(vpnInput: FileInputStream, vpnOutput: FileOutputStream) {
        isRunning = true
        coroutineScope.launch {
            val buffer = ByteArray(32768)
            while (isRunning && coroutineScope.isActive) {
                try {
                    val len = vpnInput.read(buffer)
                    if (len <= 0) continue
                    processPacket(buffer, len, vpnOutput)
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        coroutineScope.cancel()
        activeTcpSessions.values.forEach { session ->
            closeSession(session.key)
        }
        activeTcpSessions.clear()
        fakeIpToDomain.clear()
        domainToFakeIp.clear()
    }

    private fun closeSession(key: String) {
        activeTcpSessions.remove(key)?.let { session ->
            session.state = SessionState.CLOSED
            try { session.socket?.close() } catch (ignored: Exception) {}
        }
    }

    private fun getOrCreateFakeIp(domain: String): String {
        domainToFakeIp[domain]?.let { return it }
        val id = fakeIpCounter.getAndIncrement()
        val b2 = (id shr 8) and 0xFF
        val b1 = id and 0xFF
        val fakeIp = "10.254.$b2.$b1"
        fakeIpToDomain[fakeIp] = domain
        domainToFakeIp[domain] = fakeIp
        return fakeIp
    }

    private fun getTargetHost(ipStr: String): String {
        return fakeIpToDomain[ipStr] ?: ipStr
    }

    private fun processPacket(packet: ByteArray, len: Int, vpnOutput: FileOutputStream) {
        if (len < 20) return
        val version = (packet[0].toInt() and 0xFF) shr 4

        // Мгновенный сброс IPv6 пакетов (0 мс переключение на IPv4)
        if (version == 6) {
            if (len >= 60 && (packet[6].toInt() and 0xFF) == 6) {
                handleIpv6TcpRst(packet, len, vpnOutput)
            }
            return
        }

        if (version != 4) return // Работаем с IPv4

        val ihl = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF

        val srcIp = getIpString(packet, 12)
        val dstIp = getIpString(packet, 16)

        // Исключаем зацикливание: игнорируем трафик к самому прокси-серверу ПК
        val proxyIpStr = resolvedProxyAddress?.hostAddress
        if (dstIp == proxyHost || (proxyIpStr != null && dstIp == proxyIpStr)) {
            return
        }

        if (protocol == 17 && len >= ihl + 8) {
            // UDP
            val srcPort = getShort(packet, ihl)
            val dstPort = getShort(packet, ihl + 2)
            if (dstPort == 53) {
                // DNS Запрос
                handleDnsQuery(packet, ihl + 8, len - ihl - 8, srcIp, srcPort, dstIp, dstPort, vpnOutput)
            } else {
                // Отправляем ICMP Port Unreachable для не-DNS UDP (QUIC 443 / WebRTC),
                // чтобы видеоплееры (YouTube / Chrome) мгновенно переключались на TCP/TLS видеопоток
                handleUdpPortUnreachable(packet, len, vpnOutput)
            }
        } else if (protocol == 6 && len >= ihl + 20) {
            // TCP
            val srcPort = getShort(packet, ihl)
            val dstPort = getShort(packet, ihl + 2)
            val tcpHeaderLen = ((packet[ihl + 12].toInt() and 0xFF) shr 4) * 4
            val flags = packet[ihl + 13].toInt() and 0xFF

            val seqNum = getInt(packet, ihl + 4)

            val payloadOffset = ihl + tcpHeaderLen
            val payloadLen = len - payloadOffset

            val sessionKey = "$srcPort->$dstIp:$dstPort"

            val isSyn = (flags and 0x02) != 0
            val isFin = (flags and 0x01) != 0
            val isRst = (flags and 0x04) != 0
            val isAck = (flags and 0x10) != 0

            if (isSyn) {
                val targetHost = getTargetHost(dstIp)
                handleTcpSyn(sessionKey, srcIp, srcPort, dstIp, targetHost, dstPort, seqNum, vpnOutput)
            } else if (isFin) {
                val session = activeTcpSessions[sessionKey]
                val mySeq = session?.mySeq ?: 1000L
                sendTcpAck(vpnOutput, dstIp, dstPort, srcIp, srcPort, mySeq, seqNum + 1)
                closeSession(sessionKey)
            } else if (isRst) {
                closeSession(sessionKey)
            } else if (isAck && payloadLen > 0) {
                val session = activeTcpSessions[sessionKey]
                if (session != null && session.state != SessionState.CLOSED) {
                    val payload = packet.copyOfRange(payloadOffset, len)
                    session.clientSeq += payloadLen

                    if (session.state == SessionState.CONNECTED) {
                        // Строго последовательная синхронная отправка данных без нарушений порядка TCP пакетов
                        synchronized(session) {
                            try {
                                session.socksOutput?.write(payload)
                                session.socksOutput?.flush()
                                sendTcpAck(vpnOutput, dstIp, dstPort, srcIp, srcPort, session.mySeq, session.clientSeq)
                            } catch (e: Exception) {
                                closeSession(sessionKey)
                            }
                        }
                    } else if (session.state == SessionState.CONNECTING) {
                        session.pendingPayloads.add(payload)
                        sendTcpAck(vpnOutput, dstIp, dstPort, srcIp, srcPort, session.mySeq, session.clientSeq)
                    }
                }
            }
        }
    }

    private fun handleUdpPortUnreachable(packet: ByteArray, len: Int, vpnOutput: FileOutputStream) {
        try {
            if (len < 28) return
            val origIhl = (packet[0].toInt() and 0x0F) * 4
            if (len < origIhl + 8) return

            val origSrcIp = getIpString(packet, 12)
            val origDstIp = getIpString(packet, 16)

            val payloadSize = Math.min(len, origIhl + 8)
            val icmpPayloadLen = 8 + payloadSize
            val totalLen = 20 + icmpPayloadLen

            val icmpPacket = ByteArray(totalLen)
            icmpPacket[0] = 0x45 // IPv4
            icmpPacket[2] = ((totalLen shr 8) and 0xFF).toByte()
            icmpPacket[3] = (totalLen and 0xFF).toByte()
            icmpPacket[8] = 64 // TTL
            icmpPacket[9] = 1  // ICMP

            setIpBytes(icmpPacket, 12, origDstIp)
            setIpBytes(icmpPacket, 16, origSrcIp)

            val ipChecksum = calculateChecksum(icmpPacket, 0, 20)
            icmpPacket[10] = ((ipChecksum shr 8) and 0xFF).toByte()
            icmpPacket[11] = (ipChecksum and 0xFF).toByte()

            icmpPacket[20] = 3
            icmpPacket[21] = 3
            icmpPacket[22] = 0
            icmpPacket[23] = 0

            System.arraycopy(packet, 0, icmpPacket, 28, payloadSize)

            val icmpChecksum = calculateChecksum(icmpPacket, 20, icmpPayloadLen)
            icmpPacket[22] = ((icmpChecksum shr 8) and 0xFF).toByte()
            icmpPacket[23] = (icmpChecksum and 0xFF).toByte()

            synchronized(vpnOutput) {
                vpnOutput.write(icmpPacket)
                vpnOutput.flush()
            }
        } catch (ignored: Exception) {}
    }

    private fun handleIpv6TcpRst(packet: ByteArray, len: Int, vpnOutput: FileOutputStream) {
        try {
            if (len < 60) return
            val srcPort = getShort(packet, 40)
            val dstPort = getShort(packet, 42)
            val seqNum = getInt(packet, 44)

            val rst = ByteArray(60)
            rst[0] = 0x60
            rst[4] = 0x00
            rst[5] = 20
            rst[6] = 6
            rst[7] = 64

            System.arraycopy(packet, 24, rst, 8, 16)
            System.arraycopy(packet, 8, rst, 24, 16)

            setShort(rst, 40, dstPort)
            setShort(rst, 42, srcPort)
            setInt(rst, 44, 0)
            setInt(rst, 48, seqNum + 1)
            rst[52] = (5 shl 4).toByte()
            rst[53] = 0x14.toByte()
            setShort(rst, 54, 0)

            synchronized(vpnOutput) {
                vpnOutput.write(rst)
                vpnOutput.flush()
            }
        } catch (ignored: Exception) {}
    }

    private fun handleTcpSyn(
        sessionKey: String,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        targetHost: String,
        dstPort: Int,
        clientSeq: Long,
        vpnOutput: FileOutputStream
    ) {
        if (activeTcpSessions.containsKey(sessionKey)) return

        val session = TcpSession(sessionKey, srcIp, srcPort, dstIp, targetHost, dstPort, clientSeq + 1)
        activeTcpSessions[sessionKey] = session

        coroutineScope.launch {
            try {
                val proxyAddr = resolvedProxyAddress ?: InetAddress.getByName(proxyHost)

                val universalClient = UniversalProxyClient(proxyHost, proxyPort, username, password)
                val connResult = universalClient.connectAndTunnel(
                    targetHost = targetHost,
                    targetPort = dstPort,
                    resolvedProxyAddress = proxyAddr,
                    protectSocket = { socket -> vpnService.protect(socket) }
                )

                if (!connResult.success || connResult.socket == null) {
                    com.example.pcvpn.utils.AppLogger.e("Tunnel", "Сбой подключения -> $targetHost:$dstPort (${connResult.message})")
                    closeSession(sessionKey)
                    return@launch
                }

                com.example.pcvpn.utils.AppLogger.s("Tunnel", "Туннель открыт -> $targetHost:$dstPort")

                val socket = connResult.socket
                session.socket = socket
                session.socksInput = socket.getInputStream()
                session.socksOutput = socket.getOutputStream()
                session.state = SessionState.CONNECTED

                // 1. Отправляем SYN-ACK в TUN
                sendTcpSynAck(vpnOutput, dstIp, dstPort, srcIp, srcPort, session.mySeq, session.clientSeq)
                session.mySeq += 1

                // 2. Отправляем накопившиеся данные в прокси строго по порядку
                synchronized(session) {
                    while (session.pendingPayloads.isNotEmpty()) {
                        val pendingData = session.pendingPayloads.poll() ?: break
                        session.socksOutput?.write(pendingData)
                    }
                    session.socksOutput?.flush()
                }

                // 3. Читаем из прокси назад в TUN с нарезанными блоками по MSS 1460 байт (для YouTube 4K / стриминга)
                val buffer = ByteArray(65536)
                val input = session.socksInput!!

                while (isRunning && session.state == SessionState.CONNECTED) {
                    val readBytes = input.read(buffer)
                    if (readBytes <= 0) break

                    var offset = 0
                    while (offset < readBytes) {
                        val chunkSize = Math.min(MAX_TCP_PAYLOAD_SIZE, readBytes - offset)
                        val chunk = buffer.copyOfRange(offset, offset + chunkSize)
                        sendTcpData(vpnOutput, dstIp, dstPort, srcIp, srcPort, session.mySeq, session.clientSeq, chunk)
                        session.mySeq += chunkSize
                        offset += chunkSize
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка TCP туннеля $sessionKey: ${e.message}")
            } finally {
                closeSession(sessionKey)
            }
        }
    }

    private fun handleDnsQuery(
        packet: ByteArray,
        dnsOffset: Int,
        dnsLen: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        vpnOutput: FileOutputStream
    ) {
        coroutineScope.launch {
            val domain = parseDnsQuestion(packet, dnsOffset + 12)
            if (domain != null) {
                com.example.pcvpn.utils.AppLogger.i("DNS", "Запрос -> $domain")
                // 1. Сначала пробуем прямой защищенный DNS-запрос к 8.8.8.8:53 в обход VPN
                val realInet = resolveDnsDirectProtected(domain)
                if (realInet != null) {
                    com.example.pcvpn.utils.AppLogger.s("DNS", "Real-IP -> $domain = ${realInet.hostAddress}")
                    val dnsResp = buildDnsResponse(packet, dnsOffset, dnsLen, realInet)
                    if (dnsResp != null) {
                        sendUdpPacket(vpnOutput, dstIp, dstPort, srcIp, srcPort, dnsResp)
                        return@launch
                    }
                }

                // 2. Если защищенный DNS не ответил, задействуем Fake-IP
                val fakeIp = getOrCreateFakeIp(domain)
                com.example.pcvpn.utils.AppLogger.w("DNS", "Fake-IP -> $domain = $fakeIp")
                val fakeInet = InetAddress.getByName(fakeIp)
                val dnsResponse = buildDnsResponse(packet, dnsOffset, dnsLen, fakeInet)
                if (dnsResponse != null) {
                    sendUdpPacket(vpnOutput, dstIp, dstPort, srcIp, srcPort, dnsResponse)
                }
            }
        }
    }

    private fun resolveDnsDirectProtected(domain: String): InetAddress? {
        var socket: DatagramSocket? = null
        try {
            val s = DatagramSocket()
            vpnService.protect(s)
            s.soTimeout = 1200
            socket = s

            val dnsGroup = InetAddress.getByName("8.8.8.8")
            val queryBytes = buildDnsQueryPayload(domain)
            val outPacket = DatagramPacket(queryBytes, queryBytes.size, dnsGroup, 53)
            s.send(outPacket)

            val recvBuf = ByteArray(1024)
            val inPacket = DatagramPacket(recvBuf, recvBuf.size)
            s.receive(inPacket)

            return parseDnsResponsePayload(inPacket.data, inPacket.length)
        } catch (e: Exception) {
            // Ошибка не выводятся, так как есть фоллбэк на Fake-IP
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
        }
        return null
    }

    private fun buildDnsQueryPayload(domain: String): ByteArray {
        val buf = ByteBuffer.allocate(512)
        buf.putShort(0x1234.toShort()) // Transaction ID
        buf.putShort(0x0100.toShort()) // Flags: Standard Query
        buf.putShort(1.toShort())      // Questions = 1
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        val parts = domain.split(".")
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            buf.put(bytes.size.toByte())
            buf.put(bytes)
        }
        buf.put(0.toByte())

        buf.putShort(1.toShort()) // Type A
        buf.putShort(1.toShort()) // Class IN

        val len = buf.position()
        val result = ByteArray(len)
        buf.flip()
        buf.get(result)
        return result
    }

    private fun parseDnsResponsePayload(data: ByteArray, len: Int): InetAddress? {
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

    private fun parseDnsQuestion(packet: ByteArray, offset: Int): String? {
        var pos = offset
        val sb = StringBuilder()
        while (pos < packet.size) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) break
            pos++
            if (pos + len > packet.size) return null
            if (sb.isNotEmpty()) sb.append(".")
            sb.append(String(packet, pos, len, Charsets.US_ASCII))
            pos += len
        }
        return if (sb.isNotEmpty()) sb.toString() else null
    }

    private fun buildDnsResponse(
        reqPacket: ByteArray,
        dnsOffset: Int,
        dnsLen: Int,
        resolvedIp: InetAddress
    ): ByteArray? {
        val ipBytes = resolvedIp.address
        if (ipBytes.size != 4) return null

        val reqDns = reqPacket.copyOfRange(dnsOffset, dnsOffset + dnsLen)
        val resp = ByteArray(dnsLen + 16)
        System.arraycopy(reqDns, 0, resp, 0, dnsLen)

        resp[2] = 0x81.toByte()
        resp[3] = 0x80.toByte()
        resp[6] = 0x00
        resp[7] = 0x01

        var pos = dnsLen
        resp[pos++] = 0xc0.toByte()
        resp[pos++] = 0x0c.toByte()

        resp[pos++] = 0x00
        resp[pos++] = 0x01
        resp[pos++] = 0x00
        resp[pos++] = 0x01
        resp[pos++] = 0x00
        resp[pos++] = 0x00
        resp[pos++] = 0x00
        resp[pos++] = 0x3c.toByte()
        resp[pos++] = 0x00
        resp[pos++] = 0x04
        System.arraycopy(ipBytes, 0, resp, pos, 4)
        pos += 4

        return resp.copyOfRange(0, pos)
    }

    private fun sendTcpSynAck(
        vpnOutput: FileOutputStream,
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        seq: Long, ack: Long
    ) {
        val tcpPacket = createTcpPacket(srcIp, srcPort, dstIp, dstPort, seq, ack, 0x12, null)
        synchronized(vpnOutput) {
            vpnOutput.write(tcpPacket)
            vpnOutput.flush()
        }
    }

    private fun sendTcpAck(
        vpnOutput: FileOutputStream,
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        seq: Long, ack: Long
    ) {
        val tcpPacket = createTcpPacket(srcIp, srcPort, dstIp, dstPort, seq, ack, 0x10, null)
        synchronized(vpnOutput) {
            vpnOutput.write(tcpPacket)
            vpnOutput.flush()
        }
    }

    private fun sendTcpData(
        vpnOutput: FileOutputStream,
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        seq: Long, ack: Long,
        payload: ByteArray
    ) {
        val tcpPacket = createTcpPacket(srcIp, srcPort, dstIp, dstPort, seq, ack, 0x18, payload)
        synchronized(vpnOutput) {
            vpnOutput.write(tcpPacket)
            vpnOutput.flush()
        }
    }

    private fun sendUdpPacket(
        vpnOutput: FileOutputStream,
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        payload: ByteArray
    ) {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val packet = ByteArray(totalLen)

        packet[0] = 0x45
        packet[2] = ((totalLen shr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[8] = 64
        packet[9] = 17
        setIpBytes(packet, 12, srcIp)
        setIpBytes(packet, 16, dstIp)

        val ipChecksum = calculateChecksum(packet, 0, 20)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        setShort(packet, 20, srcPort)
        setShort(packet, 22, dstPort)
        setShort(packet, 24, udpLen)
        System.arraycopy(payload, 0, packet, 28, payload.size)

        synchronized(vpnOutput) {
            vpnOutput.write(packet)
            vpnOutput.flush()
        }
    }

    private fun createTcpPacket(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        seq: Long, ack: Long,
        flags: Int,
        payload: ByteArray?
    ): ByteArray {
        val payloadLen = payload?.size ?: 0
        val tcpLen = 20 + payloadLen
        val totalLen = 20 + tcpLen
        val packet = ByteArray(totalLen)

        packet[0] = 0x45
        packet[2] = ((totalLen shr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[8] = 64
        packet[9] = 6
        setIpBytes(packet, 12, srcIp)
        setIpBytes(packet, 16, dstIp)

        val ipChecksum = calculateChecksum(packet, 0, 20)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        setShort(packet, 20, srcPort)
        setShort(packet, 22, dstPort)
        setInt(packet, 24, seq)
        setInt(packet, 28, ack)
        packet[32] = (5 shl 4).toByte()
        packet[33] = flags.toByte()
        setShort(packet, 34, 65535)

        if (payload != null) {
            System.arraycopy(payload, 0, packet, 40, payloadLen)
        }

        packet[36] = 0
        packet[37] = 0
        val tcpChecksum = calculateTcpChecksum(packet, 0, 20, tcpLen)
        packet[36] = ((tcpChecksum shr 8) and 0xFF).toByte()
        packet[37] = (tcpChecksum and 0xFF).toByte()

        return packet
    }

    private fun calculateTcpChecksum(
        packet: ByteArray,
        ipOff: Int,
        tcpOff: Int,
        tcpLen: Int
    ): Int {
        var sum = 0L

        for (i in 0..3) {
            val word = ((packet[ipOff + 12 + i * 2].toInt() and 0xFF) shl 8) or (packet[ipOff + 12 + i * 2 + 1].toInt() and 0xFF)
            sum += word
        }
        sum += 6
        sum += tcpLen

        var i = 0
        while (i < tcpLen - 1) {
            val word = ((packet[tcpOff + i].toInt() and 0xFF) shl 8) or (packet[tcpOff + i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < tcpLen) {
            sum += ((packet[tcpOff + i].toInt() and 0xFF) shl 8)
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        val checksum = sum.inv().toInt() and 0xFFFF
        return if (checksum == 0) 0xFFFF else checksum
    }

    private fun calculateChecksum(data: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < off + len) {
            sum += ((data[i].toInt() and 0xFF) shl 8)
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun getShort(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun setShort(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v shr 8) and 0xFF).toByte()
        b[off + 1] = (v and 0xFF).toByte()
    }

    private fun getInt(b: ByteArray, off: Int): Long =
        (((b[off].toLong() and 0xFF) shl 24) or
                ((b[off + 1].toLong() and 0xFF) shl 16) or
                ((b[off + 2].toLong() and 0xFF) shl 8) or
                (b[off + 3].toLong() and 0xFF))

    private fun setInt(b: ByteArray, off: Int, v: Long) {
        b[off] = ((v shr 24) and 0xFF).toByte()
        b[off + 1] = ((v shr 16) and 0xFF).toByte()
        b[off + 2] = ((v shr 8) and 0xFF).toByte()
        b[off + 3] = (v and 0xFF).toByte()
    }

    private fun getIpString(b: ByteArray, off: Int): String =
        "${b[off].toInt() and 0xFF}.${b[off + 1].toInt() and 0xFF}.${b[off + 2].toInt() and 0xFF}.${b[off + 3].toInt() and 0xFF}"

    private fun setIpBytes(b: ByteArray, off: Int, ipStr: String) {
        val parts = ipStr.split(".")
        if (parts.size == 4) {
            for (i in 0..3) {
                b[off + i] = parts[i].toInt().toByte()
            }
        }
    }
}
