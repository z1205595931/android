package com.example.myproxy

import android.util.Log
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class OkHttpDns : Dns {

    private val mDNSResolver = DNSResolver.getInstance()

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 1. 优先从缓存获取
            var ipArray = mDNSResolver.getIpv4ByHostFromCache(hostname, true)
            if (ipArray.isNullOrEmpty()) {
                // 2. 缓存未命中，同步请求云端解析
                ipArray = mDNSResolver.getIPsV4ByHost(hostname)
            }

            if (!ipArray.isNullOrEmpty()) {
                // 3. 将IP字符串转换为 InetAddress 列表
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
            
            // 4. HTTPDNS解析失败，降级使用系统DNS，保证网络请求的可用性
            Log.w("OkHttpDns", "HTTPDNS failed for $hostname, fallback to system DNS.")
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.e("OkHttpDns", "HTTPDNS lookup error for $hostname", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
