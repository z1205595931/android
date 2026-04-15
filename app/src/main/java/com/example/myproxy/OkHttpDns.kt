package com.example.myproxy

import android.content.Context
import android.util.Log
import com.alibaba.sdk.android.httpdns.HttpDns
import okhttp3.Dns
import java.net.InetAddress

class OkHttpDns(private val context: Context) : Dns {
    private val httpdnsService = HttpDns.getService(context, "你的AccountID")

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 1. 尝试通过HTTPDNS服务异步解析
            val ip = httpdnsService.getIpByHostAsync(hostname)
            if (!ip.isNullOrEmpty()) {
                Log.d("OkHttpDns", "HTTPDNS resolved $hostname -> $ip")
                return listOf(InetAddress.getByName(ip))
            }

            // 2. 如果HTTPDNS解析失败，则回退到系统DNS解析
            Log.w("OkHttpDns", "HTTPDNS failed for $hostname, fallback to system DNS.")
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.e("OkHttpDns", "HTTPDNS lookup error for $hostname", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
