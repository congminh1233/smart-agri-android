package com.example.agriiot

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.agriiot.worker.EventPollingWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AgriApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupBackgroundPolling()
    }

    private fun setupBackgroundPolling() {
        // Android WorkManager has a minimum interval of 15 minutes for periodic work
        // to preserve battery life and system resources.
        val workRequest = PeriodicWorkRequestBuilder<EventPollingWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "BackgroundEventPolling",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
