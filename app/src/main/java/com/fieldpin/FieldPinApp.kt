package com.fieldpin

import android.app.Application
import com.fieldpin.data.map.OfflineMapManager
import com.fieldpin.data.repo.SyncManager
import com.fieldpin.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FieldPinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@FieldPinApp)
            modules(appModule)
        }.koin
        koin.get<SyncManager>().start()
        koin.get<OfflineMapManager>().start() // saves the offline map on first run
    }
}
