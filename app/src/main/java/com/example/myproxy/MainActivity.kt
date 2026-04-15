package com.example.myproxy

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var errorText: TextView
    private lateinit var apiEditText: EditText
    private lateinit var saveApiButton: Button
    private lateinit var controlButton: Button
    private var isRunning = false

    private val ipUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ProxyVpnService.ACTION_IP_UPDATED -> {
                    val ip = intent.getStringExtra("ip") ?: "未知"
                    val port = intent.getIntExtra("port", 0)
                    ipText.text = "当前IP: $ip:$port"
                    errorText.text = "状态: 运行正常"
                    isRunning = true
                    updateUi()
                }
                ProxyVpnService.ACTION_ERROR -> {
                    val errorMsg = intent.getStringExtra("error") ?: "未知错误"
                    errorText.text = "错误: $errorMsg"
                    ipText.text = "当前IP: 获取失败"
                    isRunning = false
                    updateUi()
                }
                ProxyVpnService.ACTION_VPN_STARTED -> {
                    errorText.text = "状态: VPN 已连接"
                    isRunning = true
                    updateUi()
                }
                ProxyVpnService.ACTION_VPN_STOPPED -> {
                    errorText.text = "状态: VPN 已断开"
                    ipText.text = "当前IP: 无"
                    isRunning = false
                    updateUi()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        ipText = findViewById(R.id.ip_text)
        errorText = findViewById(R.id.error_text)
        apiEditText = findViewById(R.id.api_edittext)
        saveApiButton = findViewById(R.id.save_api_button)
        controlButton = findViewById(R.id.control_button)

        val savedApi = PreferencesManager.getApiUrl(this)
        apiEditText.setText(savedApi)

        saveApiButton.setOnClickListener {
            val newApi = apiEditText.text.toString().trim()
            if (newApi.isNotEmpty()) {
                PreferencesManager.saveApiUrl(this, newApi)
                Toast.makeText(this, "API 地址已保存", Toast.LENGTH_SHORT).show()
                errorText.text = "状态: API 地址已更新"
            } else {
                Toast.makeText(this, "API 地址不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        controlButton.setOnClickListener {
            if (isRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }

        // 全局异常捕获，确保未捕获异常也显示在界面上
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val errorMsg = e.stackTraceToString()
            android.util.Log.e("VPN_CRASH", errorMsg)
            runOnUiThread {
                errorText.text = "崩溃: ${e.message}"
                Toast.makeText(this, "应用崩溃: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        val filter = IntentFilter().apply {
            addAction(ProxyVpnService.ACTION_IP_UPDATED)
            addAction(ProxyVpnService.ACTION_ERROR)
            addAction(ProxyVpnService.ACTION_VPN_STARTED)
            addAction(ProxyVpnService.ACTION_VPN_STOPPED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(ipUpdateReceiver, filter)

        updateUi()
    }

    private fun startVpn() {
        errorText.text = "状态: 正在检查 VPN 权限..."
        try {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                // 需要用户授权
                Toast.makeText(this, "请授权 VPN 连接", Toast.LENGTH_SHORT).show()
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                // 已有权限，直接启动服务
                errorText.text = "状态: 权限已具备，正在启动 VPN 服务..."
                startVpnService()
            }
        } catch (e: Exception) {
            errorText.text = "错误: VPN 准备失败 - ${e.message}"
            Toast.makeText(this, "VPN 准备失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVpnService() {
        try {
            val intent = Intent(this, ProxyVpnService::class.java)
            startService(intent)
            Toast.makeText(this, "VPN 服务启动中...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            errorText.text = "错误: 无法启动 VPN 服务 - ${e.message}"
            Toast.makeText(this, "服务启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVpn() {
        stopService(Intent(this, ProxyVpnService::class.java))
        isRunning = false
        updateUi()
        errorText.text = "状态: VPN 已手动断开"
        ipText.text = "当前IP: 无"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                errorText.text = "状态: 用户已授权，正在启动 VPN 服务..."
                startVpnService()
            } else {
                errorText.text = "错误: 用户未授权 VPN 权限"
                Toast.makeText(this, "需要 VPN 权限才能使用代理", Toast.LENGTH_LONG).show()
                isRunning = false
                updateUi()
            }
        }
    }

    private fun updateUi() {
        statusText.text = if (isRunning) "VPN运行中" else "VPN未连接"
        controlButton.text = if (isRunning) "断开VPN" else "连接VPN"
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ipUpdateReceiver)
        super.onDestroy()
    }

    companion object {
        private const val VPN_REQUEST_CODE = 100
    }
}
