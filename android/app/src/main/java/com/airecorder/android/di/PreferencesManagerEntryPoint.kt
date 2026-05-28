package com.airecorder.android.di

import com.airecorder.android.data.local.PreferencesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesManagerEntryPoint {
    fun preferencesManager(): PreferencesManager
}
