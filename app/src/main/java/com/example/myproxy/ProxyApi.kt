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
  // ... 其他代码保持不变 ...

    private fun startVpn() {
        try {
            val builder = Builder()
                .setSession("IP切换器")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("114.114.114.114")
                .addDnsServer("223.5.5.5")
                .setMtu(1500)

            vpnInterface?.close()
            vpnInterface = builder.establish()

            // ---------- 关键：将VPN接口传递给ProxyApi，用于保护Socket ----------
            proxyApi.setVpnInterface(vpnInterface)
            // -----------------------------------------------------------------

            // 绑定进程到VPN网络（让其他应用流量走VPN）
            bindProcessToVpnNetwork()

            val tunInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val tunOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            tun2Socks = Tun2Socks(tunInput, tunOutput, proxyApi, this)
            tun2Socks?.start()

            sendVpnStateBroadcast(true)
            fetchAndUpdateProxyInBackground()
            startScheduledSwitch()

        } catch (e: Exception) {
            // ... 错误处理 ...
        }
    }

    /**
     * 保护一个Socket，使其流量绕过VPN直接走物理网络
     */
    fun protectSocket(socket: java.net.Socket): Boolean {
        return try {
            protect(socket)
        } catch (e: Exception) {
            Log.e("ProxyVpnService", "protectSocket失败", e)
            false
        }
    }

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
            network?.let {
                builder.socketFactory(it.socketFactory)
            }
            return builder.build()
        }

    @Throws(IOException::class)
    fun fetchSingleProxy(): ProxyInfo {
        // 获取用户保存的API链接
        val originalUrl = PreferencesManager.getApiUrl(context)
        if (originalUrl.isBlank()) {
            throw IOException("请先配置API提取链接")
        }

        // ⚠️ 将域名替换为您ping到的真实IP地址（示例为123.58.243.40，请务必换成您自己ping出的IP）
        val API_IP = "123.6.195.38"  // <--- 这里替换成您ping到的IP
        val apiUrl = originalUrl.replace("v2.api.juliangip.com", API_IP)

        Log.d("ProxyApi", "原始URL: $originalUrl")
        Log.d("ProxyApi", "直连URL: $apiUrl")

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Host", "v2.api.juliangip.com")  // 必须添加Host头，否则服务器不知道访问哪个站点
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
