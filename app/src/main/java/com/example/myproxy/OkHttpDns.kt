// app/src/main/java/com/example/myproxy/OkHttpDns.kt
package com.example.myproxy

import android.util.Log
import com.alibaba.sdk.android.httpdns.HttpDns
import okhttp3.Dns
import java.net.InetAddress

class OkHttpDns : Dns {
    // 替换为你的 AccountID
    private val httpdnsService = HttpDns.getService(MyApp.ACCOUNT_ID)

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 1. 优先从缓存获取
            val ip = httpdnsService.getIpByHostAsync(hostname)
            if (!ip.isNullOrEmpty()) {
                Log.d("OkHttpDns", "HTTPDNS resolved $hostname -> $ip")
                return listOf(InetAddress.getByName(ip))
            }
            // 2. 降级到系统DNS
            Log.w("OkHttpDns", "HTTPDNS failed for $hostname, fallback to system DNS.")
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.e("OkHttpDns", "HTTPDNS lookup error for $hostname", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
