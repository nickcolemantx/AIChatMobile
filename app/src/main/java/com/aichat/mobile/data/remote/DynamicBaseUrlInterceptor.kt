package com.aichat.mobile.data.remote

import com.aichat.mobile.data.prefs.AppPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites the scheme + host + port of outgoing requests to the currently configured base URL.
 * Retrofit is built with a placeholder base URL; the user-entered URL takes effect here.
 */
@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val prefs: AppPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configured = runBlocking { prefs.currentBaseUrl() }
        if (configured.isNullOrBlank()) return chain.proceed(request)

        val newBase = configured.toHttpUrlOrNull() ?: return chain.proceed(request)
        val newUrl = request.url.newBuilder()
            .scheme(newBase.scheme)
            .host(newBase.host)
            .port(newBase.port)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
