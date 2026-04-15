// 文件：app/src/main/java/com/example/myproxy/MyApp.kt
package com.example.myproxy

import android.app.Application
import com.alibaba.sdk.android.httpdns.DNSResolver // 导入正确的包名

class MyApp : Application() {

    companion object {
        // ⚠️ 请替换为阿里云HTTPDNS控制台获取的真实值
        const val ACCOUNT_ID = "118094"
        const val ACCESS_KEY_ID = "6e4e74a8ff8685da1138eab88c6032c3"
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
