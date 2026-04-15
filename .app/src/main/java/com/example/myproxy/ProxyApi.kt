package com.example.myproxy

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ProxyInfo(
    val ip: String,
    val port: Int,
    @SerializedName("http_user")
    val username: String? = null,
    @SerializedName("http_pass")
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
    val proxyList: List<ProxyInfo>
)

class ProxyApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    @Throws(IOException::class)
    fun fetchSingleProxy(): ProxyInfo {
        // 替换为你的API地址（确保已添加白名单）
        val apiUrl = "http://v2.api.juliangip.com/company/dynamic/getips?auth_type=2&auto_white=1&filter=1&num=1&pt=2&result_type=json2&trade_no=1452972276467480&sign=f228954613992d388e25979e40d99b5e"

        val request = Request.Builder()
            .url(apiUrl)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("请求失败: ${response.code}")
            val body = response.body?.string() ?: throw IOException("响应体为空")

            val apiResponse = gson.fromJson(body, ApiResponse::class.java)
            if (apiResponse.code != 200) {
                throw IOException("API错误: ${apiResponse.msg}")
            }

            val proxyList = apiResponse.data.proxyList
            if (proxyList.isEmpty()) {
                throw IOException("没有可用代理")
            }
            return proxyList[0]
        }
    }
}
