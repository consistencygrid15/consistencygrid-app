package com.consistencygridwallpaper.network

import com.consistencygridwallpaper.utils.AppLogger
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ApiClient — Singleton Retrofit client factory.
 *
 * Security: Authentication tokens are injected via the Authorization header
 * using an OkHttp interceptor. Tokens are NEVER placed in URL query parameters.
 */
object ApiClient {
    private const val TAG = "ApiClient"
    private const val DEFAULT_BASE_URL = "https://consistencygrid.com/"

    private var retrofit: Retrofit? = null
    private var lastBaseUrl: String = ""
    private var lastToken: String = ""

    /**
     * Returns a configured [ApiService] instance.
     *
     * @param baseUrl   Optional override for the base URL.
     * @param authToken The user's public token — injected as "Authorization: Bearer <token>".
     *                  NEVER appended as a URL query parameter.
     */
    fun getService(baseUrl: String? = null, authToken: String? = null): ApiService {
        val url = baseUrl ?: DEFAULT_BASE_URL
        val formattedUrl = if (url.endsWith("/")) url else "$url/"
        val safeToken = authToken ?: ""

        // Rebuild if base URL or token changed
        val needsRebuild = retrofit == null
                || lastBaseUrl != formattedUrl
                || lastToken != safeToken

        if (needsRebuild) {
            AppLogger.d(TAG, "Building Retrofit client for: $formattedUrl (token present=${safeToken.isNotBlank()})")

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // ── Security: inject token as Authorization header, never in URL ──
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder().apply {
                        if (safeToken.isNotBlank()) {
                            addHeader("Authorization", "Bearer $safeToken")
                            addHeader("Cookie", "publicToken=$safeToken; native_auth=true")
                        }
                    }.build()
                    chain.proceed(request)
                }
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            lastBaseUrl = formattedUrl
            lastToken = safeToken
        }

        return retrofit?.create(ApiService::class.java)
            ?: throw IllegalStateException("Failed to create ApiClient — Retrofit instance is null")
    }
}
