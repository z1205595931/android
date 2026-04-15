// 文件：app/src/main/java/com/example/myproxy/OkHttpDns.kt
package com.example.myproxy

import android.util.Log
import com.alibaba.sdk.android.httpdns.DNSResolver
import okhttp3.Dns
import java.net.InetAddress

class OkHttpDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 1. 优先从HTTPDNS缓存获取IPv4地址
            val ips = DNSResolver.getInstance().getIpv4ByHostFromCache(hostname, true)
            if (!ips.isNullOrEmpty()) {
                val addresses = mutableListOf<InetAddress>()
                for (ip in ips) {
                    try {
                        addresses.add(InetAddress.getByName(ip))
                    } catch (e: Exception) {
                        Log.e("OkHttpDns", "Invalid IP address: $ip", e)
                    }
                }
                if (addresses.isNotEmpty()) {
                    Log.d("OkHttpDns", "HTTPDNS resolved $hostname -> ${addresses.joinToString { it.hostAddress }}")
                    return addresses
                }
            }
            // 2. 降级到系统DNS
            Log.w("OkHttpDns", "HTTPDNS cache miss for $hostname, fallback to system DNS.")
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.e("OkHttpDns", "HTTPDNS lookup error for $hostname", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
