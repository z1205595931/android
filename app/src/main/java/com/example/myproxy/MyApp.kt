package com.example.myproxy

import android.app.Application
import com.aliyun.ams.httpdns.HTTPDNS
import com.aliyun.ams.httpdns.HTTPDNSResolver

class MyApp : Application() {

    companion object {
      // 从阿里云控制台获取
        private const val ACCOUNT_ID = "118094"
        private const val ACCESS_KEY_ID = "6e4e74a8ff8685da1138eab88c6032c3"
        private const val ACCESS_KEY_SECRET = "b54db43230711ced72b2334a44612b8f"
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化 HTTPDNS SDK
        HTTPDNS.init(this, ACCOUNT_ID, ACCESS_KEY_ID, ACCESS_KEY_SECRET)
        // 预加载域名
        HTTPDNS.getInstance().preLoadDomains(
            HTTPDNS.QTYPE_IPV4,
            arrayOf("v2.api.juliangip.com")
        )
    }
}
        
    
