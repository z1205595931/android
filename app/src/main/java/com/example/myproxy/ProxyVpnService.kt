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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2Socks: Tun2Socks? = null

    private val scheduler = Executors.newScheduledThreadPool(1)
    private var switchTask: ScheduledFuture<*>? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_service_channel"
        const val ACTION_IP_UPDATED = "com.example.myproxy.IP_UPDATED"
        const val ACTION_ERROR = "com.example.myproxy.ERROR"
        const val ACTION_VPN_STARTED = "com.example.myproxy.VPN_STARTED"
        const val ACTION_VPN_STOPPED = "com.example.myproxy.VPN_STOPPED"

        @Volatile
        var currentProxy: ProxyInfo? = null
    }

    override fun onCreate() {
        super.onCreate()
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

            val tunInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val tunOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            tun2Socks = Tun2Socks(tunInput, tunOutput, currentProxy!!, this)
            tun2Socks?.start()

            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_VPN_STARTED))
            sendIpUpdateBroadcast(currentProxy!!)

            startScheduledSwitch()

        } catch (e: Exception) {
            Log.e("ProxyVpnService", "VPN启动失败", e)
            sendErrorBroadcast("VPN启动失败: ${e.message}")
            stopSelf()
        }
    }

    private fun startScheduledSwitch() {
        switchTask?.cancel(false)
        switchTask = scheduler.scheduleAtFixedRate({
            try {
                val proxyApi = ProxyApi(this)
                val newProxy = proxyApi.fetchSingleProxy()
                currentProxy = newProxy
                tun2Socks?.updateProxy(newProxy)
                sendIpUpdateBroadcast(newProxy)
            } catch (e: Exception) {
                Log.e("ProxyVpnService", "获取代理失败", e)
                sendErrorBroadcast("获取代理失败: ${e.message}")
            }
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

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IP切换器")
            .setContentText("VPN运行中，每3分钟切换IP")
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
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_VPN_STOPPED))
        super.onDestroy()
    }
}
