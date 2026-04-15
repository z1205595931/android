fun updateIpInfo(proxy: ProxyInfo) {
    runOnUiThread {
        ipText.text = "当前IP: ${proxy.ip}:${proxy.port}"
        errorText.text = "状态: 运行正常"
        statusText.text = "VPN运行中"
        isRunning = true
        controlButton.text = "断开VPN"
    }
}

fun reportError(error: String) {
    runOnUiThread {
        errorText.text = "错误: $error"
        ipText.text = "当前IP: 获取失败"
        statusText.text = "VPN未连接"
        isRunning = false
        controlButton.text = "连接VPN"
    }
}

fun updateVpnState(running: Boolean) {
    runOnUiThread {
        isRunning = running
        statusText.text = if (running) "VPN运行中" else "VPN未连接"
        controlButton.text = if (running) "断开VPN" else "连接VPN"
    }
}
