package com.example.myproxy

import android.util.Log
import com.alibaba.sdk.android.httpdns.DNSResolver
import okhttp3.Dns
import java.net.InetAddress

class OkHttpDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 使用同步非阻塞方式解析，优先返回缓存
            val result = DNSResolver.getInstance().getIpsByHostAsync(hostname)
            if (result != null && result.ips != null && result.ips!!.isNotEmpty()) {
                val addresses = mutableListOf<InetAddress>()
                for (ip in result.ips!!) {
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
            Log.w("OkHttpDns", "HTTPDNS failed for $hostname, fallback to system DNS.")
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.e("OkHttpDns", "HTTPDNS lookup error for $hostname", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
