package com.example.myproxy

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ProxyInfo(
    val ip: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val protocol: String = "socks5"
)

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
    val proxyList: List<ProxyItem>
)

data class ProxyItem(
    val ip: String,
    val port: String,
    @SerializedName("http_user")
    val httpUser: String?,
    @SerializedName("http_pass")
    val httpPass: String?
)

class ProxyApi(private val context: Context) {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun fetchSingleProxy(): ProxyInfo {
        val originalUrl = PreferencesManager.getApiUrl(context)
        if (originalUrl.isBlank()) {
            throw IOException("请先在主界面配置API提取链接")
        }

        // ⚠️ 请替换为您 ping v2.api.juliangip.com 得到的真实 IP
        val API_IP = "123.6.195.38"
        val apiUrl = originalUrl.replace("v2.api.juliangip.com", API_IP)

        Log.d("ProxyApi", "直连URL: $apiUrl")

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Host", "v2.api.juliangip.com")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("响应体为空")
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
