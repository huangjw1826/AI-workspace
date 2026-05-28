package com.airecorder.android.data.remote

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val token = runBlocking { tokenProvider.getToken().first() }
        val serverUrl = runBlocking { tokenProvider.getServerUrl().first() }
        
        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "AI Recorder Android/1.0.0")
        
        if (token.isNotBlank()) {
            requestBuilder.header("X-API-Token", token)
        }
        
        if (serverUrl.isNotBlank()) {
            val newUrl = buildNewUrl(originalRequest.url, serverUrl)
            requestBuilder.url(newUrl)
        }
        
        return chain.proceed(requestBuilder.build())
    }
    
    private fun buildNewUrl(originalUrl: HttpUrl, baseUrl: String): HttpUrl {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        
        val scheme = if (normalizedBaseUrl.startsWith("https://")) "https" else "http"
        val hostAndPort = normalizedBaseUrl.removePrefix("https://").removePrefix("http://")
        val parts = hostAndPort.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: (if (scheme == "https") 443 else 80) else (if (scheme == "https") 443 else 80)
        
        return originalUrl.newBuilder()
            .scheme(scheme)
            .host(host)
            .port(port)
            .build()
    }
}

interface TokenProvider {
    suspend fun getToken(): kotlinx.coroutines.flow.Flow<String>
    suspend fun getServerUrl(): kotlinx.coroutines.flow.Flow<String>
}
