package com.devsstream.cafepos.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.devsstream.cafepos.MemoryServerStore
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.inMemoryDb
import com.devsstream.cafepos.data.remote.fake.Failures
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import com.devsstream.cafepos.data.tc06Sale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

// The real WorkManager (test version) with our request: constraint, unique work, worker result.
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb()
    private val repo = OrderRepository(db)
    private val server = FakePosServer(
        MemoryServerStore(),
        elapsedMs = { 10_000_000L },
        wallMs = { dhakaTime(2026, 11, 20, 15, 27, 30).toEpochMilli() },
        random = Random(7),
        latencyMs = 0L..0L,
    ).apply { failures = Failures.OFF }

    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            SyncWorker(appContext, workerParameters, OrderSync(repo, server))
    }

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(factory).setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun syncWork(): WorkInfo =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(SYNC_WORK).get().single()

    @Test
    fun `TC-14 sync waits for a network, then sends the 3 sales in order`() = runBlocking {
        repeat(3) { repo.tc06Sale() }
        WorkManagerSyncScheduler(context).requestSync()

        val waiting = syncWork()
        assertEquals(NetworkType.CONNECTED, waiting.constraints.requiredNetworkType)
        assertEquals(WorkInfo.State.ENQUEUED, waiting.state)
        assertTrue(server.orders().isEmpty())

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(waiting.id)
        val finished = withTimeout(10_000) {
            WorkManager.getInstance(context).getWorkInfoByIdFlow(waiting.id).first { it!!.state.isFinished }
        }

        assertEquals(WorkInfo.State.SUCCEEDED, finished!!.state)
        assertEquals(listOf("T1-20261120-0001", "T1-20261120-0002", "T1-20261120-0003"), server.orders().map { it.receiptNo })
    }

    @Test
    fun `worker asks WorkManager to back off and retry when an upload failed`() = runBlocking {
        repo.tc06Sale()
        server.failures = Failures.NETWORK_DOWN
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build()

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test
    fun `worker succeeds when everything is sent`() = runBlocking {
        repo.tc06Sale()
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, server.orders().size)
    }
}
