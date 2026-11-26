package com.devsstream.cafepos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.di.AppScope
import com.devsstream.cafepos.payment.CardPayments
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PosApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sync: SyncScheduler

    @Inject
    lateinit var cards: CardPayments

    @Inject
    @AppScope
    lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // cards that were still waiting when the process died: ask the terminal (TC-17)
        scope.launch { cards.recover() }
        // orders left PENDING last time, e.g. killed before the response (C3)
        sync.requestSync()
    }
}
