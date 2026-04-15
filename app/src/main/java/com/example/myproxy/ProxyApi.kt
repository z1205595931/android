package com.example.myproxy

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
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
    val proxyList: List<String>
)

class ProxyApi(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    @Throws(IOException::class)
    fun fetchSingleProxy(): ProxyInfo {
        val tradeNo = PreferencesManager.getTradeNo(context)
        val apiKey = PreferencesManager.getApiKey(context)
        if (tradeNo.isBlank() || apiKey.isBlank()) {
            throw IOException("请先在主界面配置业务编号和API Key")
        }

        val params = mapOf(
            "trade_no" to tradeNo,
            "num" to "1",
            "pt" to "2",
            "result_type" to "json",
            "filter" to "1",
            "auth_info" to "1"
        )
        val sign = generateSign(params, apiKey)
        
        val url = "http://v2.api.juliangip.com/dynamic/getips?" +
                "trade_no=$tradeNo&num=1&pt=2&result_type=json&filter=1&auth_info=1&sign=$sign"

        Log.d("ProxyApi", "请求URL: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP 请求失败: ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("响应体为空")
            Log.d("ProxyApi", "响应JSON: $body")

            val apiResponse = gson.fromJson(body, ApiResponse::class.java)
            if (apiResponse.code != 200) {
                throw IOException("API 业务错误: ${apiResponse.msg}")
            }

            val proxyList = apiResponse.data.proxyList
            if (proxyList.isEmpty()) {
                throw IOException("API 未返回代理数据，请检查白名单或套餐余量")
            }
            
            val proxyInfo = parseProxyString(proxyList[0])
            Log.d("ProxyApi", "解析成功: ${proxyInfo.ip}:${proxyInfo.port}")
            return proxyInfo
        }
    }

    private fun generateSign(params: Map<String, String>, key: String): String {
        val sortedParams = params.toSortedMap()
        val rawStr = sortedParams.map { "${it.key}=${it.value}" }.joinToString("&")
        val signRaw = "$rawStr&key=$key"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(signRaw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseProxyString(proxyStr: String): ProxyInfo {
        val parts = proxyStr.split(":")
        return when (parts.size) {
            2 -> ProxyInfo(parts[0], parts[1].toInt(), null, null)
            4 -> ProxyInfo(parts[0], parts[1].toInt(), parts[2], parts[3])
            else -> throw IllegalArgumentException("Invalid proxy format: $proxyStr")
        }
    }
}
