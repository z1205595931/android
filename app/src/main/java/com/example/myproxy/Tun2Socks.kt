package com.example.myproxy

import android.content.Context
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class Tun2Socks(
    private val tunInput: FileInputStream,
    private val tunOutput: FileOutputStream,
    initialProxy: ProxyInfo,
    private val context: Context
) : Thread() {

    @Volatile
    private var running = true
    private val currentProxy = AtomicReference(initialProxy)
    
    // 使用线程池处理并发连接，避免阻塞主读取线程
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    fun updateProxy(newProxy: ProxyInfo) {
        currentProxy.set(newProxy)
        Log.d("Tun2Socks", "代理已更新: ${newProxy.ip}:${newProxy.port}")
    }

    private fun getCurrentProxy(): ProxyInfo = currentProxy.get()

    override fun run() {
        val packet = ByteArray(32767)
        Log.d("Tun2Socks", "开始监听VPN数据包")
        while (running) {
            try {
                val length = tunInput.read(packet)
                if (length > 0) {
                    val data = packet.copyOf(length)
                    // 将数据包处理提交到线程池，避免阻塞读取
                    executor.submit {
                        try {
                            processPacket(data)
                        } catch (e: Exception) {
                            Log.e("Tun2Socks", "处理数据包异常", e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e("Tun2Socks", "读取VPN数据失败", e)
                }
            }
        }
        Log.d("Tun2Socks", "停止监听")
    }

    private fun processPacket(packet: ByteArray) {
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return // 只处理IPv4

        val protocol = packet[9].toInt()
        if (protocol != 6) return // 只处理TCP

        val headerLength = (packet[0].toInt() and 0x0F) * 4
        val destIp = packet.copyOfRange(16, 20)
        val destPort = ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or (packet[headerLength + 3].toInt() and 0xFF)

        val tcpHeaderLength = ((packet[headerLength + 12].toInt() shr 4) and 0x0F) * 4
        val dataOffset = headerLength + tcpHeaderLength
        val data = packet.copyOfRange(dataOffset, packet.size)

        val proxy = getCurrentProxy()
        
        // 为每个连接创建独立的处理器，并在线程池中执行
        executor.submit {
            try {
                TcpProxyHandler(destIp, destPort, data).handle(proxy)
            } catch (e: Exception) {
                Log.e("Tun2Socks", "TCP代理处理失败", e)
            }
        }
    }

    fun stopProcessing() {
        running = false
        executor.shutdownNow()
        interrupt()
    }
}
