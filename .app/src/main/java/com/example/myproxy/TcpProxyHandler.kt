package com.example.myproxy

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class TcpProxyHandler(
    private val sourceIp: ByteArray,
    private val sourcePort: Int,
    private val destIp: ByteArray,
    private val destPort: Int,
    private val data: ByteArray
) {
    fun handle(proxyInfo: ProxyInfo) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(proxyInfo.ip, proxyInfo.port), 10000)

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // SOCKS5 handshake with auth
            output.write(byteArrayOf(0x05, 0x01, 0x02))
            val handshakeResponse = ByteArray(2)
            input.read(handshakeResponse)

            val username = proxyInfo.username ?: ""
            val password = proxyInfo.password ?: ""

            val authRequest = ByteArray(3 + username.length + password.length)
            authRequest[0] = 0x01
            authRequest[1] = username.length.toByte()
            System.arraycopy(username.toByteArray(), 0, authRequest, 2, username.length)
            authRequest[2 + username.length] = password.length.toByte()
            System.arraycopy(password.toByteArray(), 0, authRequest, 3 + username.length, password.length)

            output.write(authRequest)
            val authResponse = ByteArray(2)
            input.read(authResponse)

            // SOCKS5 connect request
            val connectRequest = ByteArray(10)
            connectRequest[0] = 0x05
            connectRequest[1] = 0x01
            connectRequest[2] = 0x00
            connectRequest[3] = 0x01 // IPv4
            System.arraycopy(destIp, 0, connectRequest, 4, 4)
            connectRequest[8] = (destPort shr 8 and 0xFF).toByte()
            connectRequest[9] = (destPort and 0xFF).toByte()

            output.write(connectRequest)
            val connectResponse = ByteArray(10)
            input.read(connectResponse)

            output.write(data)
            output.flush()

            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
