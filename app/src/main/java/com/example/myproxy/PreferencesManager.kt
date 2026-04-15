package com.example.myproxy

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREF_NAME = "proxy_prefs"
    private const val KEY_API_URL = "api_url"

    // 默认示例链接（您可以替换为您自己的默认链接，或留空）
    private const val DEFAULT_API_URL = ""

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
