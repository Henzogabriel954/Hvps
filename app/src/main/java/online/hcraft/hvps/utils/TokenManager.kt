package online.hcraft.hvps.utils

import android.content.Context
import android.content.SharedPreferences

object TokenManager {
    private const val PREF_NAME = "hvps_prefs"
    private const val KEY_API_TOKEN = "api_token"
    private lateinit var preferences: SharedPreferences

    fun init(context: Context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        preferences.edit().putString(KEY_API_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return preferences.getString(KEY_API_TOKEN, null)
    }

    fun clearToken() {
        preferences.edit().remove(KEY_API_TOKEN).apply()
    }
}
