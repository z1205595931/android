package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.HttpDns
import com.alibaba.sdk.android.httpdns.InitConfig

class MyApp : Application() {
    companion object {
        const val ACCOUNT_ID = "118094"
        const val SECRET_KEY = "6e4e74a8ff8685da1138eab88c6032c3"
    }

    override fun onCreate() {
        super.onCreate()
        val config = InitConfig.Builder()
            .setContext(this)
            .setSecretKey(SECRET_KEY)
            .build()
        HttpDns.init(this, config)
        HttpDns.getService(ACCOUNT_ID).setPreResolveHosts(listOf("v2.api.juliangip.com"))
    }
}
