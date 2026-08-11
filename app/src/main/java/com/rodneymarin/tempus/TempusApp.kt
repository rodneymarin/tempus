package com.rodneymarin.tempus

import android.app.Application
import android.content.Context
import com.rodneymarin.tempus.data.RoomBuilder
import com.rodneymarin.tempus.data.TempusDatabase
import com.rodneymarin.tempus.data.TrackersRepository

class TempusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val database: TempusDatabase = RoomBuilder.build(context)
    val repository: TrackersRepository = TrackersRepository(database)
}
