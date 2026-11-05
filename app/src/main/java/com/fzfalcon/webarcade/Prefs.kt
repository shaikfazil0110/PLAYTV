package com.fzfalcon.webarcade

import android.content.Context

/** Small SharedPreferences wrapper. Native storage survives "clear browser data". */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("playtv", Context.MODE_PRIVATE)

    fun getString(key: String): String = sp.getString(key, "") ?: ""
    fun setString(key: String, value: String) { sp.edit().putString(key, value).apply() }

    fun getBool(key: String, default: Boolean): Boolean = sp.getBoolean(key, default)
    fun setBool(key: String, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
}
