package com.example.myproxy

import android.content.Context
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

class Tun2Socks(
    private val tunInput: FileInputStream,
    private val tunOutput: FileOutputStream,
    private val proxyApi: ProxyApi,
    private val context: Context
) : Thread() {

    @Volatile
    private var running = true

    private val currentProxy = AtomicReference<ProxyInfo>()

    fun updateProxy(newProxy: ProxyInfo) {
        currentProxy.set(newProxy)
        Log.d("Tun2Socks", "代理已更新: ${newProxy.ip}:${newProxy.port}")
    }

    private fun getCurrentProxy(): ProxyInfo? {
        var proxy = currentProxy.get()
        if (proxy == null) {
            proxy = try {
                proxyApi.fetchSingleProxy()
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? MainActivity)?.reportError("初始化代理失败: ${e.message}")
                null
            }
            currentProxy.set(proxy)
        }
        return proxy
    }

    override fun run() {
        val packet = ByteArray(32767)
        while (running) {
            try {
                val length = tunInput.read(packet)
                if (length > 0) {
                    processPacket(packet.copyOf(length))
                }
            } catch (e: Exception) {
                if (running) e.printStackTrace()
            }
        }
    }

    private fun processPacket(packet: ByteArray) {
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return

        val protocol = packet[9].toInt()
        if (protocol != 6) return // TCP only

        val headerLength = (packet[0].toInt() and 0x0F) * 4
        val sourceIp = packet.copyOfRange(12, 16)
        val destIp = packet.copyOfRange(16, 20)
        val sourcePort = ((packet[headerLength].toInt() and 0xFF) shl 8) or (packet[headerLength + 1].toInt() and 0xFF)
        val destPort = ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or (packet[headerLength + 3].toInt() and 0xFF)

        val tcpHeaderLength = ((packet[headerLength + 12].toInt() shr 4) and 0x0F) * 4
        val dataOffset = headerLength + tcpHeaderLength
        val data = packet.copyOfRange(dataOffset, packet.size)

        val proxy = getCurrentProxy() ?: return

        Thread {
            TcpProxyHandler(sourceIp, sourcePort, destIp, destPort, data).handle(proxy)
        }.start()
    }

    fun stopProcessing() {
        running = false
    }
}
