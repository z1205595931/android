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
import java.lang.reflect.Method
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2Socks: Tun2Socks? = null
    private lateinit var proxyApi: ProxyApi

    // 用于执行网络请求的单线程池（避免主线程网络异常）
    private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    // 用于定时切换的调度器
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

            // 绑定进程到VPN网络（可选，通过反射处理）
            bindProcessToVpnNetwork()

            val tunInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val tunOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            tun2Socks = Tun2Socks(tunInput, tunOutput, proxyApi, this)
            tun2Socks?.start()

            sendVpnStateBroadcast(true)

            // 立即在后台线程获取一次代理（避免主线程网络异常）
            fetchAndUpdateProxyInBackground()

            startScheduledSwitch()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProxyVpnService", "VPN启动失败", e)
            sendErrorBroadcast("VPN 启动失败: ${e.message}")
            stopSelf()
        }
    }

    /**
     * 在后台线程执行代理获取与更新
     */
    private fun fetchAndUpdateProxyInBackground() {
        networkExecutor.submit {
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
    }

    /**
     * 启动定时切换任务（已在子线程执行）
     */
    private fun startScheduledSwitch() {
        switchTask?.cancel(false)
        switchTask = scheduler.scheduleAtFixedRate({
            fetchAndUpdateProxyInBackground()
        }, 3, 3, TimeUnit.MINUTES)
    }

    /**
     * 绑定进程到VPN网络（反射调用，兼容低版本编译）
     */
    private fun bindProcessToVpnNetwork() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val fd = vpnInterface?.fileDescriptor ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networkClass = Class.forName("android.net.Network")
                val fromFileDescriptorMethod: Method = networkClass.getMethod("fromFileDescriptor", java.io.FileDescriptor::class.java)
                val network = fromFileDescriptorMethod.invoke(null, fd) as? Any

                if (network != null) {
                    val bindResult = connectivityManager.bindProcessToNetwork(network as android.net.Network)
                    Log.d("ProxyVpnService", "进程绑定到VPN网络结果 (反射): $bindResult")
                } else {
                    Log.w("ProxyVpnService", "反射获取Network对象失败")
                }
            } else {
                Log.w("ProxyVpnService", "Android版本低于M，无法绑定进程到网络")
            }
        } catch (e: Exception) {
            Log.e("ProxyVpnService", "绑定进程到VPN网络失败", e)
        }
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
        networkExecutor.shutdown()
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
