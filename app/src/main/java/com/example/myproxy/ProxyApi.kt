package com.example.myproxy

import android.content.Context
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

class ProxyApi(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    @Throws(IOException::class)
    fun fetchSingleProxy(): ProxyInfo {
        // 从 SharedPreferences 获取用户保存的 API 地址
        val apiUrl = PreferencesManager.getApiUrl(context)
        if (apiUrl.isBlank()) {
            throw IOException("API 地址未设置，请在主界面填写")
        }

        val request = Request.Builder()
            .url(apiUrl)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP 请求失败: ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("响应体为空")

            val apiResponse = gson.fromJson(body, ApiResponse::class.java)
            if (apiResponse.code != 200) {
                throw IOException("API 业务错误: ${apiResponse.msg}")
            }

            val proxyList = apiResponse.data.proxyList
            if (proxyList.isEmpty()) {
                throw IOException("API 未返回代理数据")
            }
            return proxyList[0]
        }
    }
}
