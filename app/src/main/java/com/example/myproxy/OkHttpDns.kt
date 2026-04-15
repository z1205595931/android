package com.example.myproxy

import android.util.Log
import com.alibaba.sdk.android.httpdns.HttpDns
import okhttp3.Dns
import java.net.InetAddress

class OkHttpDns : Dns {

    private val httpdnsService = HttpDns.getService(MyApp.ACCOUNT_ID)

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 通过HTTPDNS同步获取IP（注意：getIpByHostAsync是异步，这里用同步方式）
            val ip = httpdnsService.getIpByHostAsync(hostname)
            if (!ip.isNullOrEmpty()) {
                Log.d("OkHttpDns", "HTTPDNS resolved $hostname -> $ip")
                listOf(InetAddress.getByName(ip))
            } else {
                // 降级到系统DNS
                Log.w("OkHttpDns", "HTTPDNS failed for $hostname, fallback to system DNS.")
                Dns.SYSTEM.lookup(hostname)
            }
        } catch (e: Exception) {
            Log.e("OkHttpDns", "HTTPDNS lookup error for $hostname", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
