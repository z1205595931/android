package com.example.myproxy

import android.content.Context
import android.net.Network
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

// ... (数据类保持不变)

class ProxyApi(private val context: Context, private var network: Network? = null) {

    fun updateVpnNetwork(newNetwork: Network?) {
        this.network = newNetwork
    }

    private val client: OkHttpClient
        get() {
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
            
            // 如果有 VPN 网络，则使用其 SocketFactory
            network?.let {
                val socketFactory = it.socketFactory
                builder.socketFactory(socketFactory)
                Log.d("ProxyApi", "使用 VPN 网络的 SocketFactory")
            }
            
            return builder.build()
        }

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

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP 请求失败: ${response.code()}")
                }
                val body = response.body()?.string() ?: throw IOException("响应体为空")
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
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("网络请求异常: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    // ... 其余方法保持不变 (generateSign, parseProxyString)
}
