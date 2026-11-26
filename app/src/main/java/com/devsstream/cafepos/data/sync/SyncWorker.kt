package com.devsstream.cafepos.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: OrderSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (sync.run()) {
        SyncOutcome.DONE -> Result.success()
        SyncOutcome.RETRY_LATER -> Result.retry()
    }
}
