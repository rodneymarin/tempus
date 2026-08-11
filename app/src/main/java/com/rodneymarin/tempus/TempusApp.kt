package com.rodneymarin.tempus

import android.app.Application
import android.content.Context
import com.rodneymarin.tempus.data.RoomBuilder
import com.rodneymarin.tempus.data.TempusDatabase
import com.rodneymarin.tempus.data.TrackersRepository
import com.rodneymarin.tempus.ui.theme.ThemePreferenceManager

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
    val themePrefs: ThemePreferenceManager = ThemePreferenceManager(context)
}

fun androidx.lifecycle.viewmodel.CreationExtras.appContainer(): AppContainer =
    (this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TempusApp).container

