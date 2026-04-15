package com.example.myproxy

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var controlButton: Button
    private var isRunning = false

    private val ipUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ip = intent?.getStringExtra("ip") ?: "未知"
            val port = intent?.getIntExtra("port", 0) ?: 0
            ipText.text = "当前IP: $ip:$port"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        ipText = findViewById(R.id.ip_text)
        controlButton = findViewById(R.id.control_button)

        controlButton.setOnClickListener {
            if (isRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            ipUpdateReceiver,
            IntentFilter(ProxyVpnService.ACTION_IP_UPDATED)
        )

        updateUi()
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
        }
    }

    private fun stopVpn() {
        stopService(Intent(this, ProxyVpnService::class.java))
        isRunning = false
        updateUi()
        ipText.text = "当前IP: 无"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, ProxyVpnService::class.java)
            startService(intent)
            isRunning = true
            updateUi()
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
