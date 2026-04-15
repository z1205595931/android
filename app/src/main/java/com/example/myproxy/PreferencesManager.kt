package com.example.myproxy

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREF_NAME = "proxy_prefs"
    private const val KEY_API_URL = "api_url"

    // 默认使用您提供的链接（方便测试）
    private const val DEFAULT_API_URL = "http://v2.api.juliangip.com/company/dynamic/getips?auth_type=2&auto_white=1&filter=1&num=1&pt=2&result_type=json&trade_no=1452972276467480&sign=85ae2c405ff38e16e1f47466b1c42db6"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveApiUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_API_URL, url).apply()
    }

    fun getApiUrl(context: Context): String {
        return getPrefs(context).getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
    }
}
