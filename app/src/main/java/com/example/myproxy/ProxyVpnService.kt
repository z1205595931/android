package com.example.myproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2Socks: Tun2Socks? = null
    private lateinit var proxyApi: ProxyApi

    private val scheduler = Executors.newScheduledThreadPool(1)
    private var switchTask: ScheduledFuture<*>? = null

    override fun onCreate() {
        super.onCreate()
        proxyApi = ProxyApi(this)
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        try {
            val builder = Builder()
                .setSession("IP切换器")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("114.114.114.114")
                .addDnsServer("223.5.5.5")
                .setMtu(1500)

            vpnInterface?.close()
            vpnInterface = builder.establish()

            // ---------- 关键修复：绑定进程到 VPN 网络 ----------
            bindProcessToVpnNetwork()
            // ------------------------------------------------

            val tunInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val tunOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            tun2Socks = Tun2Socks(tunInput, tunOutput, proxyApi, this)
            tun2Socks?.start()

            sendVpnStateBroadcast(true)

            // 立即获取一次代理
            fetchAndUpdateProxy()

            startScheduledSwitch()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProxyVpnService", "VPN启动失败", e)
            sendErrorBroadcast("VPN 启动失败: ${e.message}")
            stopSelf()
        }
    }

    /**
     * 将当前应用进程强制绑定到 VPN 虚拟网络接口，
     * 确保所有网络请求（包括 API 请求）都通过 VPN 通道发出。
     */
    private fun bindProcessToVpnNetwork() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val fd = vpnInterface?.fileDescriptor

            if (fd != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val network = android.net.Network.fromFileDescriptor(fd)
                if (network != null) {
                    val bindResult = connectivityManager.bindProcessToNetwork(network)
                    Log.d("ProxyVpnService", "进程绑定到VPN网络结果: $bindResult")
                } else {
                    Log.w("ProxyVpnService", "无法从文件描述符获取Network对象")
                }
            } else {
                Log.w("ProxyVpnService", "无法绑定进程到VPN网络: fd=$fd, SDK=${Build.VERSION.SDK_INT}")
            }
        } catch (e: Exception) {
            Log.e("ProxyVpnService", "绑定进程到VPN网络失败", e)
        }
    }

    private fun fetchAndUpdateProxy() {
        try {
            val newProxy = proxyApi.fetchSingleProxy()
            tun2Socks?.updateProxy(newProxy)
            sendIpUpdateBroadcast(newProxy)
            Log.d("ProxyVpnService", "获取代理成功: ${newProxy.ip}:${newProxy.port}")
        } catch (e: Throwable) {
            e.printStackTrace()
            Log.e("ProxyVpnService", "获取代理失败", e)
            val errorMsg = e.message ?: e.toString()
            val stackTrace = e.stackTraceToString()
            sendErrorBroadcast("获取代理失败: $errorMsg\n$stackTrace")
        }
    }

    private fun startScheduledSwitch() {
        switchTask?.cancel(false)
        switchTask = scheduler.scheduleAtFixedRate({
            fetchAndUpdateProxy()
        }, 3, 3, TimeUnit.MINUTES)
    }

    private fun sendIpUpdateBroadcast(proxy: ProxyInfo) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ACTION_IP_UPDATED).apply {
                putExtra("ip", proxy.ip)
                putExtra("port", proxy.port)
            }
        )
    }

    private fun sendErrorBroadcast(error: String) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ACTION_ERROR).putExtra("error", error)
        )
    }

    private fun sendVpnStateBroadcast(running: Boolean) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(if (running) ACTION_VPN_STARTED else ACTION_VPN_STOPPED)
        )
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IP切换器")
            .setContentText("VPN正在运行中，每3分钟自动切换IP")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        switchTask?.cancel(true)
        scheduler.shutdown()
        tun2Socks?.stopProcessing()
        tun2Socks?.interrupt()
        vpnInterface?.close()
        sendVpnStateBroadcast(false)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_service_channel"
        const val ACTION_IP_UPDATED = "com.example.myproxy.IP_UPDATED"
        const val ACTION_ERROR = "com.example.myproxy.ERROR"
        const val ACTION_VPN_STARTED = "com.example.myproxy.VPN_STARTED"
        const val ACTION_VPN_STOPPED = "com.example.myproxy.VPN_STOPPED"
    }
}
