package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.DNSResolver

class MyApp : Application() {

    companion object {
        // 从阿里云控制台获取
        private const val ACCOUNT_ID = "118094"
        private const val ACCESS_KEY_ID = "6e4e74a8ff8685da1138eab88c6032c3"
        private const val ACCESS_KEY_SECRET = "b54db43230711ced72b2334a44612b8f"
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化 HTTPDNS SDK，确保尽早执行
        DNSResolver.Init(this, ACCOUNT_ID, ACCESS_KEY_ID, ACCESS_KEY_SECRET)

        // （可选）预加载域名，可加速首次解析。此处以巨量IP API域名为例。
        DNSResolver.getInstance().preLoadDomains(
            DNSResolver.QTYPE_IPV4,
            arrayOf("v2.api.juliangip.com")
        )
    }
}
