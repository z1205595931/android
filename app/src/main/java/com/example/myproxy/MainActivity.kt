package com.example.myproxy

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import android.app.AlertDialog
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var errorText: TextView
    private lateinit var tradeNoEditText: EditText
    private lateinit var apiKeyEditText: EditText
    private lateinit var saveConfigButton: Button
    private lateinit var whitelistHelpButton: Button
    private lateinit var controlButton: Button
    private var isRunning = false

    // 用LiveData替代LocalBroadcastManager，更稳定
    private val ipUpdateLiveData = MutableLiveData<ProxyInfo>()
    private val errorLiveData = MutableLiveData<String>()
    private val vpnStateLiveData = MutableLiveData<Boolean>()

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        ipText = findViewById(R.id.ip_text)
        errorText = findViewById(R.id.error_text)
        tradeNoEditText = findViewById(R.id.trade_no_edittext)
        apiKeyEditText = findViewById(R.id.api_key_edittext)
        saveConfigButton = findViewById(R.id.save_config_button)
        whitelistHelpButton = findViewById(R.id.whitelist_help_button)
        controlButton = findViewById(R.id.control_button)

        // 加载已保存的配置
        val savedTradeNo = PreferencesManager.getTradeNo(this)
        val savedApiKey = PreferencesManager.getApiKey(this)
        tradeNoEditText.setText(savedTradeNo)
        apiKeyEditText.setText(savedApiKey)

        // 观察LiveData，更新UI
        ipUpdateLiveData.observe(this, Observer { proxy ->
            ipText.text = "当前IP: ${proxy.ip}:${proxy.port}"
            errorText.text = "状态: 运行正常"
        })
        errorLiveData.observe(this, Observer { error ->
            errorText.text = "错误: $error"
            ipText.text = "当前IP: 获取失败"
        })
        vpnStateLiveData.observe(this, Observer { running ->
            isRunning = running
            statusText.text = if (running) "VPN运行中" else "VPN未连接"
            controlButton.text = if (running) "断开VPN" else "连接VPN"
        })

        saveConfigButton.setOnClickListener {
            val tradeNo = tradeNoEditText.text.toString().trim()
            val apiKey = apiKeyEditText.text.toString().trim()
            if (tradeNo.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "业务编号和API Key不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PreferencesManager.saveTradeNo(this, tradeNo)
            PreferencesManager.saveApiKey(this, apiKey)
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            errorText.text = "状态: 配置已更新"
            
            // 如果VPN未运行，自动连接
            if (!isRunning) {
                startVpn()
            }
        }

        whitelistHelpButton.setOnClickListener {
            showWhitelistDialog()
        }

        controlButton.setOnClickListener {
            if (isRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }

        // 全局异常捕获
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val errorMsg = e.stackTraceToString()
            android.util.Log.e("VPN_CRASH", errorMsg)
            mainHandler.post {
                errorText.text = "崩溃: ${e.message}"
                Toast.makeText(this, "应用崩溃: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startVpn() {
        errorText.text = "状态: 正在检查 VPN 权限..."
        try {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                Toast.makeText(this, "请授权 VPN 连接", Toast.LENGTH_SHORT).show()
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                errorText.text = "状态: 权限已具备，正在启动 VPN 服务..."
                startVpnService()
            }
        } catch (e: Exception) {
            errorLiveData.postValue("VPN 准备失败 - ${e.message}")
        }
    }

    private fun startVpnService() {
        try {
            val intent = Intent(this, ProxyVpnService::class.java)
            startService(intent)
            vpnStateLiveData.postValue(true)
            Toast.makeText(this, "VPN 服务启动中...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            errorLiveData.postValue("无法启动 VPN 服务 - ${e.message}")
        }
    }

    private fun stopVpn() {
        stopService(Intent(this, ProxyVpnService::class.java))
        vpnStateLiveData.postValue(false)
        ipText.text = "当前IP: 无"
        errorText.text = "状态: VPN 已手动断开"
    }

    private fun showWhitelistDialog() {
        AlertDialog.Builder(this)
            .setTitle("设置白名单")
            .setMessage("请按以下步骤操作：\n1. 在云手机浏览器中访问 ip.sb 获取公网IP\n2. 登录巨量IP官网 -> 产品管理 -> 对应订单 -> 设置白名单\n3. 粘贴刚获取的IP地址并保存\n4. 等待1-3分钟后重试")
            .setPositiveButton("我知道了", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                errorText.text = "状态: 用户已授权，正在启动 VPN 服务..."
                startVpnService()
            } else {
                errorLiveData.postValue("用户未授权 VPN 权限")
                vpnStateLiveData.postValue(false)
            }
        }
    }

    // 供Service调用的静态方法，更新UI状态
    fun updateIpInfo(proxy: ProxyInfo) {
        ipUpdateLiveData.postValue(proxy)
    }

    fun reportError(error: String) {
        errorLiveData.postValue(error)
    }

    fun updateVpnState(running: Boolean) {
        vpnStateLiveData.postValue(running)
    }

    companion object {
        private const val VPN_REQUEST_CODE = 100
    }
}
