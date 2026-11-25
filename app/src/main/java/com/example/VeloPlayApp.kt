package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.PlayerPreferences
import com.example.data.repository.MediaRepository

class VeloPlayApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var preferences: PlayerPreferences
        private set
    lateinit var repository: MediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = PlayerPreferences(this)
        repository = MediaRepository(this, database.mediaDao())
    }
}
