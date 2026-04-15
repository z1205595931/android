package com.example.myproxy

import android.util.Log
import com.alibaba.sdk.android.httpdns.HttpDns
import okhttp3.Dns
import java.net.InetAddress

class OkHttpDns(private val context: android.content.Context) : Dns {

    private val httpdnsService = HttpDns.getService(context, MyApp.ACCOUNT_ID)

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val ip = httpdnsService.getIpByHostAsync(hostname)
            if (!ip.isNullOrEmpty()) {
                Log.d("OkHttpDns", "HTTPDNS resolved $hostname -> $ip")
                listOf(InetAddress.getByName(ip))
            } else {
                Log.w("OkHttpDns", "HTTPDNS failed for $hostname, fallback to system DNS.")
                Dns.SYSTEM.lookup(hostname)
            }
        } catch (e: Exception) {
            Log.e("OkHttpDns", "HTTPDNS lookup error for $hostname", e)
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
