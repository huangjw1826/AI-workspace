package com.airecorder.android

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AIRecorderApplication : Application() {

    override fun onCreate() {
        // 仅在 Debug 构建启用 StrictMode（检测主线程 IO 等）
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        // Hilt 自动处理依赖注入
        // 非关键组件延迟到首次使用时初始化（由 Hilt Lazy 实现）
        super.onCreate()
    }
}
