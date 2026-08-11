package com.rodneymarin.tempus

import android.app.Application
import android.content.Context

class TempusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    // Stubs — completed in Phase 1
}
