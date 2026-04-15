package com.example.myproxy

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREF_NAME = "proxy_prefs"
    private const val KEY_API_URL = "api_url"

    private const val DEFAULT_API = "http://v2.api.juliangip.com/company/dynamic/getips?auth_type=2&auto_white=1&filter=1&num=1&pt=2&result_type=json2&trade_no=1452972276467480&sign=f228954613992d388e25979e40d99b5e"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveApiUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_API_URL, url).apply()
    }

    fun getApiUrl(context: Context): String {
        return getPrefs(context).getString(KEY_API_URL, DEFAULT_API) ?: DEFAULT_API
    }
}
