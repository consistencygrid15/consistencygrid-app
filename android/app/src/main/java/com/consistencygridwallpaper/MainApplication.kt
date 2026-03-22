package com.consistencygridwallpaper

import android.app.Application
import android.webkit.CookieManager

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize CookieManager globally for better persistence
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
    }
}
