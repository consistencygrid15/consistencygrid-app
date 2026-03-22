package com.consistencygridwallpaper.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.fragment.app.Fragment
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.bridge.WebInterface
import com.consistencygridwallpaper.storage.UserPrefs

class PreviewFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var userPrefs: UserPrefs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_preview, container, false)
        
        webView = root.findViewById(R.id.webview_preview)
        val btnSetNow: Button = root.findViewById(R.id.btn_set_now)
        userPrefs = UserPrefs(requireContext())

        setupWebView()
        loadWallpaper()

        btnSetNow.setOnClickListener {
            // Reload and the page logic will trigger Android.saveWallpaper
            loadWallpaper()
        }

        return root
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
        }
        
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                view?.loadUrl(request?.url.toString())
                return true
            }
        }
        
        webView.addJavascriptInterface(WebInterface(requireContext()), "Android")
    }

    private fun loadWallpaper() {
        // Load the full website so user can see dashboard/login
        val domain = "consistencygrid.com"
        val url = "https://$domain" 
        webView.loadUrl(url)
    }
}
