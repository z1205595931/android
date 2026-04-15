package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.HttpDns
import com.alibaba.sdk.android.httpdns.InitConfig

class MyApp : Application() {
    companion object {
        // ⚠️ 请替换为你在阿里云控制台获取的真实值
        const val ACCOUNT_ID = "118094"
        const val SECRET_KEY = "6e4e74a8ff8685da1138eab88c6032c3"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. 初始化配置（官方推荐方式）
        val config = InitConfig.Builder()
            .setContext(this)               // 设置应用上下文
            .setSecretKey(SECRET_KEY)       // 设置密钥（必须）
            .setEnableCacheIp(true)         // 开启本地缓存（推荐）
            .build()
        
        // 2. 执行初始化（可在用户同意隐私政策前调用）
        HttpDns.init(ACCOUNT_ID, config)    // 注意：第一个参数是 accountId

        // 3. 获取服务实例（建议在用户同意隐私政策后调用）
        val httpdnsService = HttpDns.getService(ACCOUNT_ID)
        
        // 4. 预加载域名，加速首次解析
        httpdnsService.setPreResolveHosts(listOf("v2.api.juliangip.com"))
    }
}
