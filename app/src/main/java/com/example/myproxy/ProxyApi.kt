package com.example.myproxy

import android.content.Context
import android.net.Network
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// 代理信息
data class ProxyInfo(
    val ip: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val protocol: String = "socks5"
)

// 巨量IP API响应结构（根据您提供的链接返回格式）
data class ApiResponse(
    val code: Int,
    val msg: String,
    val data: ProxyData
)

data class ProxyData(
    val count: Int,
    @SerializedName("filter_count")
    val filterCount: Int,
    @SerializedName("surplus_quantity")
    val surplusQuantity: Int,
    @SerializedName("proxy_list")
    val proxyList: List<ProxyItem>  // 注意：您的API返回的是对象数组，每个对象包含ip、port、http_user、http_pass
)

data class ProxyItem(
    val ip: String,
    val port: String,  // 注意：API返回的port是字符串，需转换为Int
    @SerializedName("http_user")
    val httpUser: String?,
    @SerializedName("http_pass")
    val httpPass: String?
)

class ProxyApi(private val context: Context, private var network: Network? = null) {

    fun updateVpnNetwork(newNetwork: Network?) {
        this.network = newNetwork
    }

    private val gson = Gson()

    private val client: OkHttpClient
        get() {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)

            // 绑定VPN网络（确保流量走VPN通道）
            network?.let {
                val socketFactory = it.socketFactory
                builder.socketFactory(socketFactory)
            }
            return builder.build()
        }

    @Throws(IOException::class)
    fun fetchSingleProxy(): ProxyInfo {
        val apiUrl = PreferencesManager.getApiUrl(context)
        if (apiUrl.isBlank()) {
            throw IOException("请先在主界面配置API提取链接")
        }

        Log.d("ProxyApi", "请求URL: $apiUrl")

        val request = Request.Builder()
            .url(apiUrl)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code()}")
            }
            val body = response.body()?.string() ?: throw IOException("响应体为空")
            Log.d("ProxyApi", "响应: $body")

            val apiResponse = gson.fromJson(body, ApiResponse::class.java)
            if (apiResponse.code != 200) {
                throw IOException("API错误: ${apiResponse.msg}")
            }

            val proxyList = apiResponse.data.proxyList
            if (proxyList.isEmpty()) {
                throw IOException("未返回代理数据，请检查白名单或套餐余量")
            }

            val item = proxyList[0]
            val port = item.port.toIntOrNull() ?: throw IOException("端口格式错误: ${item.port}")
            
            return ProxyInfo(
                ip = item.ip,
                port = port,
                username = item.httpUser,
                password = item.httpPass,
                protocol = "socks5"
            )
        }
    }
}
