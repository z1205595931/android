package com.example.myproxy

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREF_NAME = "proxy_prefs"
    private const val KEY_TRADE_NO = "trade_no"
    private const val KEY_API_KEY = "api_key"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveTradeNo(context: Context, tradeNo: String) {
        getPrefs(context).edit().putString(KEY_TRADE_NO, tradeNo).apply()
    }

    fun getTradeNo(context: Context): String {
        return getPrefs(context).getString(KEY_TRADE_NO, "") ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }
}
