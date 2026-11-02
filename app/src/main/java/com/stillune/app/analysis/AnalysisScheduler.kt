package com.stillune.app.analysis

import android.content.Context
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun enqueue(priority: String? = null, immediate: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<PreAnalysisWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .setInitialDelay(if (immediate) 0 else 20, TimeUnit.SECONDS)
            .setInputData(workDataOf("priority" to priority))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("stillune-preanalysis",
            if (immediate) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }
    fun registerIdle() {
        val idle = PeriodicWorkRequestBuilder<PreAnalysisWorker>(12, TimeUnit.HOURS)
            .setInputData(workDataOf("bulk" to true))
            .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("stillune-idle-analysis", ExistingPeriodicWorkPolicy.UPDATE, idle)
    }
}

@EntryPoint @InstallIn(SingletonComponent::class)
interface AnalysisEntryPoint { fun repository(): AnalysisRepository }

class PreAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, AnalysisEntryPoint::class.java).repository()
        return if (repository.runBatch(inputData.getString("priority"),
            maxTracks = if (inputData.getBoolean("bulk", false)) Int.MAX_VALUE else 3)) Result.success() else Result.retry()
    }
}
