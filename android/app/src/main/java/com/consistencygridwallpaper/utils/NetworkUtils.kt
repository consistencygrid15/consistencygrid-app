package com.consistencygridwallpaper.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * NetworkUtils - Helper for Network Connectivity
 *
 * Provides a centralized way to check for active internet connection.
 * Used to preventing network calls when offline and providing appropriate user feedback.
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Checks if the device currently has an active internet connection.
     *
     * @param context The application context
     * @return true if connected to a network with internet capability, false otherwise
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking network availability", e)
            false
        }
    }
}
