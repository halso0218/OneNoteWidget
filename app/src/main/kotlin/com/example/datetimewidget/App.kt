package com.example.datetimewidget

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application(), Configuration.Provider {

    // WorkManagerをContentProviderではなくここで設定（起動時のRoom DB初期化コストを排除）
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { AmedasCache.load(applicationContext) }
        }
    }
}
