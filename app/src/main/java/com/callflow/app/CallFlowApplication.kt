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
import com.callflow.app.core.contacts.DeviceContactResolver

@HiltAndroidApp
class CallFlowApplication : Application(), Configuration.Provider, DefaultLifecycleObserver {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var followUpNotifications: FollowUpNotificationManager
    @Inject lateinit var contactResolver: DeviceContactResolver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super<Application>.onCreate()
        followUpNotifications.createChannel()
        SyncWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        // Periodic work handles ongoing reconciliation; sync only once per foreground entry.
        SyncWorker.syncNow(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) contactResolver.trimCache()
    }
}
