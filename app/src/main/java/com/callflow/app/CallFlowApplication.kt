package com.callflow.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.callflow.app.sync.SyncWorker
import javax.inject.Inject
import com.callflow.app.notifications.FollowUpNotificationManager

@HiltAndroidApp
class CallFlowApplication : Application(), Configuration.Provider, DefaultLifecycleObserver {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var followUpNotifications: FollowUpNotificationManager
    private var lastForegroundSyncAt = 0L

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super<Application>.onCreate()
        followUpNotifications.createChannel()
        SyncWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncAt < 30_000) return
        lastForegroundSyncAt = now
        SyncWorker.syncNow(this)
    }
}
