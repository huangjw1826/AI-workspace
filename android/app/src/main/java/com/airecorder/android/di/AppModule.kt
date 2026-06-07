package com.airecorder.android.di

import android.content.Context
import com.airecorder.android.BuildConfig
import com.airecorder.android.data.local.AudioCacheManager
import com.airecorder.android.data.local.PreferencesManager
import com.airecorder.android.data.remote.ApiService
import com.airecorder.android.data.remote.AuthInterceptor
import com.airecorder.android.data.remote.TokenProvider
import com.airecorder.android.data.remote.DefaultTokenProvider
import com.airecorder.android.service.ConnectivityMonitor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("http://10.0.2.2:8000/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
    
    @Provides
    @Singleton
    fun provideTokenProvider(preferencesManager: PreferencesManager): TokenProvider =
        DefaultTokenProvider(preferencesManager)
    
    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager =
        PreferencesManager(context)

    @Provides
    @Singleton
    fun provideAudioCacheManager(@ApplicationContext context: Context): AudioCacheManager =
        AudioCacheManager(context)

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLooper(android.os.Looper.getMainLooper())
            .build()
    }

    @Provides
    @Singleton
    fun provideConnectivityMonitor(): ConnectivityMonitor = ConnectivityMonitor()
}
