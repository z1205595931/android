package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.HttpDns
import com.alibaba.sdk.android.httpdns.InitConfig

class MyApp : Application() {

    companion object {
        // ⚠️ 请替换为你在阿里云 HTTPDNS 控制台获取的真实值
        const val ACCOUNT_ID = "118094"
        const val SECRET_KEY = "6e4e74a8ff8685da1138eab88c6032c3"
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化配置
        val config = InitConfig.Builder()
            .setContext(this)
            .setSecretKey(SECRET_KEY)
            .build()
        HttpDns.init(this, config)

        // 预加载域名：getService 只需要 AccountID
        val httpdnsService = HttpDns.getService(ACCOUNT_ID)
        httpdnsService.setPreResolveHosts(listOf("v2.api.juliangip.com"))
    }
}
