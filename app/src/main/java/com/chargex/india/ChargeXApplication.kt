package com.chargex.india

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.chargex.india.storage.CleanupCacheWorker
import com.chargex.india.storage.PreferenceDataSource
import com.chargex.india.storage.UpdateFullDownloadWorker
import com.chargex.india.ui.updateAppLocale
import com.chargex.india.ui.updateNightMode
import org.acra.config.dialog
import org.acra.config.httpSender
import org.acra.config.limiter
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import java.time.Duration

class ChargeXApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        val prefs = PreferenceDataSource(this)
        updateNightMode(prefs)

        // Convert to new AppCompat storage for app language
        val lang = prefs.language
        if (lang != null) {
            updateAppLocale(lang)
            prefs.language = null
        }

        init(applicationContext)
        addDebugInterceptors(applicationContext)

        if (!BuildConfig.DEBUG) {
            val credsResId = resources.getIdentifier("acra_credentials", "string", packageName)
            if (credsResId != 0) {
                initAcra {
                    buildConfigClass = BuildConfig::class.java

                    reportFormat = StringFormat.JSON
                    httpSender {
                        uri = getString(R.string.acra_backend_url)
                        val creds = getString(credsResId).split(":")
                        basicAuthLogin = creds[0]
                        basicAuthPassword = creds[1]
                        httpMethod = HttpSender.Method.POST
                    }

                    dialog {
                        text = getString(R.string.crash_report_text)
                        title = getString(R.string.app_name)
                        commentPrompt = getString(R.string.crash_report_comment_prompt)
                        resIcon = R.drawable.ic_launcher_foreground
                        resTheme = R.style.AppTheme
                        if (BuildConfig.FLAVOR_automotive == "automotive") {
                            reportDialogClass =
                                Class.forName("androidx.car.app.activity.CarAppActivity") as Class<out Activity>?
                        }
                    }

                    limiter {
                        enabled = true
                    }
                }
            }
        }

        val workManager = WorkManager.getInstance(this)
        val cleanupCacheRequest = PeriodicWorkRequestBuilder<CleanupCacheWorker>(Duration.ofDays(1))
            .setConstraints(Constraints.Builder().apply {
                setRequiresBatteryNotLow(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setRequiresDeviceIdle(true)
                }
            }.build()).build()
        workManager.enqueueUniquePeriodicWork(
            "CleanupCacheWorker", ExistingPeriodicWorkPolicy.UPDATE, cleanupCacheRequest
        )

        val updateFullDownloadRequest =
            PeriodicWorkRequestBuilder<UpdateFullDownloadWorker>(Duration.ofDays(7))
                .setConstraints(Constraints.Builder().apply {
                    setRequiresBatteryNotLow(true)
                    setRequiredNetworkType(NetworkType.UNMETERED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        setRequiresDeviceIdle(true)
                    }
                }.build()).build()
        workManager.enqueueUniquePeriodicWork(
            "UpdateOsmWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateFullDownloadRequest
        )
    }

    override val workManagerConfiguration = Configuration.Builder().build()
}