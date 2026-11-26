package com.devsstream.cafepos.di

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import com.devsstream.cafepos.data.local.PosDatabase
import com.devsstream.cafepos.data.remote.ClockSyncingApi
import com.devsstream.cafepos.data.remote.PosApi
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import com.devsstream.cafepos.data.remote.fake.FileServerStore
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.data.sync.WorkManagerSyncScheduler
import com.devsstream.cafepos.payment.CardTerminal
import com.devsstream.cafepos.time.PrefsAnchorStore
import com.devsstream.cafepos.time.ServerClock
import com.devsstream.cafepos.time.readBootCount
import com.devsstream.terminal.LegacyCardTerminal
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun serverClock(@ApplicationContext context: Context) = ServerClock(
        store = PrefsAnchorStore(context),
        elapsedMs = SystemClock::elapsedRealtime,
        wallMs = System::currentTimeMillis,
        bootCount = { readBootCount(context) },
    )

    // The fake server is a singleton so its test hooks (debug screen) and the app
    // talk to the same instance.
    @Provides
    @Singleton
    fun fakeServer(@ApplicationContext context: Context) = FakePosServer(
        store = FileServerStore(File(context.filesDir, "fake-server.json")),
        elapsedMs = SystemClock::elapsedRealtime,
        wallMs = System::currentTimeMillis,
    )

    @Provides
    @Singleton
    fun posApi(server: FakePosServer, clock: ServerClock): PosApi =
        ClockSyncingApi(server, clock, SystemClock::elapsedRealtime)

    // no fallbackToDestructiveMigration: wiping unsynced sales is never ok
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PosDatabase =
        Room.databaseBuilder(context, PosDatabase::class.java, "pos.db").build()

    @Provides
    @Singleton
    fun syncScheduler(@ApplicationContext context: Context): SyncScheduler = WorkManagerSyncScheduler(context)

    @Provides
    @Singleton
    fun cardTerminal() = CardTerminal(LegacyCardTerminal(), log = { Log.w("CardTerminal", it) })
}
