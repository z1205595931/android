package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.DNSResolver

class MyApp : Application() {

    companion object {
        const val ACCOUNT_ID = "118094"
        // 注意：SDK 2.3.0 初始化需要 AccessKey ID 和 AccessKey Secret
        const val ACCESS_KEY_ID = "6e4e74a8ff8685da1138eab88c6032c3"
        const val ACCESS_KEY_SECRET = "b54db43230711ced72b2334a44612b8f"
    }

    override fun onCreate() {
        super.onCreate()
        // 使用 DNSResolver 初始化
        DNSResolver.Init(this, ACCOUNT_ID, ACCESS_KEY_ID, ACCESS_KEY_SECRET)

        // 预加载域名
        DNSResolver.getInstance().preLoadDomains(
            DNSResolver.QTYPE_IPV4,
            arrayOf("v2.api.juliangip.com")
        )
    }
}
