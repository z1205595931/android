package com.example.myproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
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
                .addDnsServer("114.114.114.114")  // 改用国内DNS
                .addDnsServer("223.5.5.5")
                .setMtu(1500)

            vpnInterface?.close()
            vpnInterface = builder.establish()

            val tunInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val tunOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            tun2Socks = Tun2Socks(tunInput, tunOutput, proxyApi, this)
            tun2Socks?.start()

            // 通知界面 VPN 已启动
            (applicationContext as? MainActivity)?.updateVpnState(true)

            // ---------- 立即获取一次代理 IP ----------
            fetchAndUpdateProxy()

            // 启动定时切换
            startScheduledSwitch()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProxyVpnService", "VPN启动失败", e)
            (applicationContext as? MainActivity)?.reportError("VPN 启动失败: ${e.message}")
            stopSelf()
        }
    }

    private fun fetchAndUpdateProxy() {
        try {
            val newProxy = proxyApi.fetchSingleProxy()
            tun2Socks?.updateProxy(newProxy)
            (applicationContext as? MainActivity)?.updateIpInfo(newProxy)
            Log.d("ProxyVpnService", "首次获取代理成功: ${newProxy.ip}:${newProxy.port}")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProxyVpnService", "首次获取代理失败", e)
            (applicationContext as? MainActivity)?.reportError("首次获取代理失败: ${e.message}")
        }
    }

    private fun startScheduledSwitch() {
        switchTask?.cancel(false)
        switchTask = scheduler.scheduleAtFixedRate({
            fetchAndUpdateProxy()
        }, 3, 3, TimeUnit.MINUTES)
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
        (applicationContext as? MainActivity)?.updateVpnState(false)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_service_channel"
    }
}
