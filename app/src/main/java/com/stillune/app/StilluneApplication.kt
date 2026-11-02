package com.stillune.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.stillune.app.analysis.AnalysisScheduler
import javax.inject.Inject

@HiltAndroidApp
class StilluneApplication : Application() {
    @Inject lateinit var analysis: AnalysisScheduler
    override fun onCreate() { super.onCreate(); analysis.registerIdle() }
}
