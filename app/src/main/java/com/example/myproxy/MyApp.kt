// 文件：app/src/main/java/com/example/myproxy/MyApp.kt
package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.DNSResolver // 导入正确的包名

class MyApp : Application() {

    companion object {
        // ⚠️ 请替换为阿里云HTTPDNS控制台获取的真实值
        
        const val ACCESS_KEY_SECRET = "b54db43230711ced72b2334a44612b8f"
    }

    override fun onCreate() {
        super.onCreate()

        // 使用官方推荐方式初始化
        DNSResolver.Init(this, ACCOUNT_ID, ACCESS_KEY_ID, SECRET_KEY)
        
        // 预加载巨量IP API域名，加速首次解析
        DNSResolver.getInstance().preLoadDomains(
            DNSResolver.QTYPE_IPV4,
            arrayOf("v2.api.juliangip.com")
        )
    }
}
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
