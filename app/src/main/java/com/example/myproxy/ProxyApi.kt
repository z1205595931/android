package com.example.myproxy

import android.content.Context
import android.net.Network
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

// 代理信息数据类
data class ProxyInfo(
    val ip: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val protocol: String = "socks5"
)

// API 响应最外层
data class ApiResponse(
    val code: Int,
    val msg: String,
    val data: ProxyData
)

// data 字段
data class ProxyData(
    val count: Int,
    @SerializedName("filter_count")
    val filterCount: Int,
    @SerializedName("surplus_quantity")
    val surplusQuantity: Int,
    @SerializedName("proxy_list")
    val proxyList: List<String>
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

            // 如果提供了 VPN 网络，则使用其 SocketFactory，确保流量走 VPN
            network?.let {
                val socketFactory = it.socketFactory
                builder.socketFactory(socketFactory)
                Log.d("ProxyApi", "使用 VPN 网络的 SocketFactory")
            }

            return builder.build()
        }

    /**
     * 从巨量 IP API 获取单个代理
     * @throws IOException 网络异常或业务异常
     */
    @Throws(IOException::class)
    fun fetchSingleProxy(): ProxyInfo {
        // 直接获取用户保存的完整 API 链接（在后台生成好的，包含签名）
        val apiUrl = PreferencesManager.getApiUrl(context)
        if (apiUrl.isBlank()) {
            throw IOException("请先在主界面配置完整的 API 提取链接")
        }

        Log.d("ProxyApi", "请求URL: $apiUrl")

        val request = Request.Builder()
            .url(apiUrl)
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

                // 官方返回格式：auth_info=1 时，元素为 "ip:port:username:password"
                val proxyStr = proxyList[0]
                val proxyInfo = parseProxyString(proxyStr)
                Log.d("ProxyApi", "解析成功: ${proxyInfo.ip}:${proxyInfo.port}")
                return proxyInfo
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("网络请求异常: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    /**
     * 解析代理字符串，支持 "ip:port" 或 "ip:port:username:password"
     */
    private fun parseProxyString(proxyStr: String): ProxyInfo {
        val parts = proxyStr.split(":")
        return when (parts.size) {
            2 -> ProxyInfo(parts[0], parts[1].toInt(), null, null)
            4 -> ProxyInfo(parts[0], parts[1].toInt(), parts[2], parts[3])
            else -> throw IOException("无效的代理格式: $proxyStr")
        }
    }
}
