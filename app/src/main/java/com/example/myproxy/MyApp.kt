package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.HttpDns
import com.alibaba.sdk.android.httpdns.InitConfig

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        private const val ACCOUNT_ID = "118094"
        private const val ACCESS_KEY_ID = "6e4e74a8ff8685da1138eab88c6032c3"
        private const val ACCESS_KEY_SECRET = "b54db43230711ced72b2334a44612b8f"
        
        // 初始化 HTTPDNS SDK
        HttpDns.getService(applicationContext, accountID)
        HttpDns.init(applicationContext, InitConfig.Builder()
            .setContext(applicationContext)
            .setSecretKey(secretKey)
            .build()
        )
    }
}
        
    
