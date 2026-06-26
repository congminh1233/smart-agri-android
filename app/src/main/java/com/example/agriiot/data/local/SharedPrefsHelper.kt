package com.example.agriiot.data.local

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsHelper @Inject constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("agri_prefs", Context.MODE_PRIVATE)

    fun getLastSeenTimestamp(): String {
        return prefs.getString("last_seen_timestamp", "") ?: ""
    }

    fun setLastSeenTimestamp(timestamp: String) {
        val current = getLastSeenTimestamp()
        if (timestamp > current) {
            prefs.edit().putString("last_seen_timestamp", timestamp).apply()
        }
    }
}
