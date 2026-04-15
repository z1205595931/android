package com.example.myproxy

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.app.AlertDialog
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var errorText: TextView
    private lateinit var apiUrlEditText: EditText
    private lateinit var saveConfigButton: Button
    private lateinit var whitelistHelpButton: Button
    private lateinit var controlButton: Button
    private var isRunning = false

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ProxyVpnService.ACTION_IP_UPDATED -> {
                    val ip = intent.getStringExtra("ip") ?: return
                    val port = intent.getIntExtra("port", 0)
                    updateIpInfo(ProxyInfo(ip, port))
                }
                ProxyVpnService.ACTION_ERROR -> {
                    val error = intent.getStringExtra("error") ?: "未知错误"
                    reportError(error)
                }
                ProxyVpnService.ACTION_VPN_STARTED -> updateVpnState(true)
                ProxyVpnService.ACTION_VPN_STOPPED -> updateVpnState(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        ipText = findViewById(R.id.ip_text)
        errorText = findViewById(R.id.error_text)
        apiUrlEditText = findViewById(R.id.api_url_edittext)
        saveConfigButton = findViewById(R.id.save_config_button)
        whitelistHelpButton = findViewById(R.id.whitelist_help_button)
        controlButton = findViewById(R.id.control_button)

        apiUrlEditText.setText(PreferencesManager.getApiUrl(this))

        saveConfigButton.setOnClickListener {
            val apiUrl = apiUrlEditText.text.toString().trim()
            if (apiUrl.isEmpty()) {
                Toast.makeText(this, "请输入API链接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PreferencesManager.saveApiUrl(this, apiUrl)
            Toast.makeText(this, "API已保存", Toast.LENGTH_SHORT).show()
            errorText.text = "状态: 配置已更新"
        }

        whitelistHelpButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("设置白名单")
                .setMessage("1. 浏览器访问 ip.sb 获取本机IP\n2. 登录巨量IP后台 -> 产品管理 -> 对应订单 -> 白名单\n3. 添加IP后等待1-3分钟生效")
                .setPositiveButton("我知道了", null)
                .show()
        }

        controlButton.setOnClickListener {
            if (isRunning) stopVpn() else startVpn()
        }

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            mainHandler.post {
                errorText.text = "崩溃: ${e.message}"
                Toast.makeText(this, "崩溃: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        val filter = IntentFilter().apply {
            addAction(ProxyVpnService.ACTION_IP_UPDATED)
            addAction(ProxyVpnService.ACTION_ERROR)
            addAction(ProxyVpnService.ACTION_VPN_STARTED)
            addAction(ProxyVpnService.ACTION_VPN_STOPPED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter)
    }

    private fun startVpn() {
        errorText.text = "状态: 正在获取代理IP..."
        controlButton.isEnabled = false

        executor.execute {
            try {
                val proxyApi = ProxyApi(this)
                val proxy = proxyApi.fetchSingleProxy()
                ProxyVpnService.currentProxy = proxy

                mainHandler.post {
                    errorText.text = "状态: 代理获取成功，正在启动VPN..."
                    val intent = VpnService.prepare(this)
                    if (intent != null) {
                        startActivityForResult(intent, VPN_REQUEST_CODE)
                    } else {
                        startVpnService()
                    }
                    controlButton.isEnabled = true
                }
            } catch (e: Exception) {
                mainHandler.post {
                    reportError("获取代理失败: ${e.message}")
                    controlButton.isEnabled = true
                }
            }
        }
    }

    private fun startVpnService() {
        try {
            startService(Intent(this, ProxyVpnService::class.java))
            updateVpnState(true)
            Toast.makeText(this, "VPN启动中...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            reportError("启动服务失败: ${e.message}")
        }
    }

    private fun stopVpn() {
        stopService(Intent(this, ProxyVpnService::class.java))
        updateVpnState(false)
        ipText.text = "当前IP: 无"
        errorText.text = "状态: 已断开"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startVpnService()
            } else {
                reportError("未授权VPN权限")
                updateVpnState(false)
                controlButton.isEnabled = true
            }
        }
    }

    fun updateIpInfo(proxy: ProxyInfo) {
        runOnUiThread {
            ipText.text = "当前IP: ${proxy.ip}:${proxy.port}"
            errorText.text = "状态: 运行正常"
        }
    }

    fun reportError(error: String) {
        runOnUiThread {
            errorText.text = "错误: $error"
            ipText.text = "当前IP: 获取失败"
        }
    }

    fun updateVpnState(running: Boolean) {
        runOnUiThread {
            isRunning = running
            statusText.text = if (running) "VPN运行中" else "VPN未连接"
            controlButton.text = if (running) "断开VPN" else "连接VPN"
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val VPN_REQUEST_CODE = 100
    }
}
