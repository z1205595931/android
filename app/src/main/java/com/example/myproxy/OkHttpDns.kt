package com.example.myproxy

import android.util.Log
import okhttp3.Dns
import com.aliyun.ams.httpdns.HTTPDNS
import java.net.InetAddress
import java.net.UnknownHostException

class OkHttpDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 1. 优先从HTTPDNS缓存获取
            var ipArray = HTTPDNS.getInstance().getIpv4ByHostFromCache(hostname, true)
            if (ipArray.isNullOrEmpty()) {
                // 2. 缓存未命中，同步请求云端解析
                ipArray = HTTPDNS.getInstance().getIPsV4ByHost(hostname)
            }

            if (!ipArray.isNullOrEmpty()) {
                val addresses = mutableListOf<InetAddress>()
                for (ip in ipArray) {
                    try {
                        addresses.add(InetAddress.getByName(ip))
                    } catch (e: UnknownHostException) {
                        Log.e("OkHttpDns", "Invalid IP address: $ip", e)
                    }
                }
                if (addresses.isNotEmpty()) {
                    Log.d("OkHttpDns", "HTTPDNS resolved: $hostname -> ${addresses.joinToString { it.hostAddress }}")
                    return addresses
                }
            }

            // 3. 降级到系统DNS
            Log.w("OkHttpDns", "HTTPDNS failed for $hostname, fallback to system DNS.")
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.e("OkHttpDns", "HTTPDNS lookup error for $hostname", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
