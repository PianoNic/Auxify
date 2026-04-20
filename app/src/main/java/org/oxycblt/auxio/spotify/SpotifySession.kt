package org.oxycblt.auxio.spotify

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SpotifySession {

    private var cookies: Map<String, String>? = null
    private var isInitialized = false

    val isLoggedIn: Boolean get() = cookies?.containsKey("sp_dc") == true

    fun initialize(context: Context): Boolean {
        cookies = loadCookies(context)
        isInitialized = cookies != null
        return isInitialized
    }

    fun onLoginComplete(context: Context, newCookies: Map<String, String>) {
        cookies = newCookies
        saveCookies(context, newCookies)
        isInitialized = true
    }

    fun logout(context: Context) {
        cookies = null
        isInitialized = false
        clearCookies(context)
    }

    fun getSpDc(): String? = cookies?.get("sp_dc")

    fun getSpKey(): String? = cookies?.get("sp_key")
}
