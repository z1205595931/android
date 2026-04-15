// app/src/main/java/com/example/myproxy/MyApp.kt
package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.HttpDns
import com.alibaba.sdk.android.httpdns.InitConfig

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 替换为你自己的账号和密钥
        val ACCOUNT_ID = "118094"
        val ACCESS_KEY_ID = "6e4e74a8ff8685da1138eab88c6032c3"

        // 使用官方推荐配置进行初始化[reference:1]
        val config = InitConfig.Builder()
            .setContext(this)
            .setSecretKey(secretKey)
            .build()
        HttpDns.init(this, config)

        // 预加载巨量IP API域名
        HttpDns.getService(accountID).setPreResolveHosts(listOf("v2.api.juliangip.com"))
    }
}
