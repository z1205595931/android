package com.example.myproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import javax.net.SocketFactory

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2Socks: Tun2Socks? = null
    private lateinit var proxyApi: ProxyApi

    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newScheduledThreadPool(1)
    private var switchTask: ScheduledFuture<*>? = null

    // 用于保存 VPN 网络对象，供 OkHttp 使用
    @Volatile
    private var vpnNetwork: Network? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d("ProxyVpnService", "NetworkCallback onAvailable: $network")
            // 尝试将应用进程绑定到这个网络（VPN 网络）
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                // 绑定进程到 VPN 网络
                connectivityManager.bindProcessToNetwork(network)
                vpnNetwork = network
                Log.d("ProxyVpnService", "进程绑定到网络成功: $network")
            } catch (e: Exception) {
                Log.e("ProxyVpnService", "绑定进程到网络失败", e)
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d("ProxyVpnService", "NetworkCallback onLost: $network")
            if (vpnNetwork == network) {
                vpnNetwork = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        proxyApi = ProxyApi(this, vpnNetwork) // 将 network 传递给 ProxyApi
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

            // 注册网络回调，以便在 VPN 网络可用时绑定进程
            registerVpnNetworkCallback()

            val tunInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val tunOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            tun2Socks = Tun2Socks(tunInput, tunOutput, proxyApi, this)
            tun2Socks?.start()

            sendVpnStateBroadcast(true)

            // 稍微延迟后获取代理，等待网络绑定完成
            android.os.Handler(mainLooper).postDelayed({
                fetchAndUpdateProxyInBackground()
            }, 1000)

            startScheduledSwitch()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProxyVpnService", "VPN启动失败", e)
            sendErrorBroadcast("VPN 启动失败: ${e.message}")
            stopSelf()
        }
    }

    private fun registerVpnNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun fetchAndUpdateProxyInBackground() {
        networkExecutor.submit {
            try {
                // 更新 ProxyApi 中的 vpnNetwork（可能在回调中已经设置）
                (proxyApi as? ProxyApi)?.updateVpnNetwork(vpnNetwork)
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
    }

    private fun startScheduledSwitch() {
        switchTask?.cancel(false)
        switchTask = scheduler.scheduleAtFixedRate({
            fetchAndUpdateProxyInBackground()
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
            // ... 原有清理代码 ...
    proxyApi.setVpnInterface(null)
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
