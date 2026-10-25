package me.capcom.smsgateway.modules.gateway.workers

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.map
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.App
import java.util.concurrent.TimeUnit

class PullMessagesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            withContext(Dispatchers.IO) {
                App.instance.gatewayService.getNewMessages(
                    applicationContext
                )
            }
            return Result.success()
        } catch (th: Throwable) {
            th.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        const val NAME = "PullMessagesWorker"

        /**
         * Cooldown for EVENT-triggered pull requests (T7 pull coalescing).
         *
         * Verified cadence source (T1 debugger_diagnosis): every
         * MessageEnqueued/Ping event (EventsReceiver) and every SSE
         * onConnected (SSEForegroundService) used to call [start], whose
         * ExistingPeriodicWorkPolicy.REPLACE on the 15-min periodic work
         * triggers an IMMEDIATE run under WorkManager 2.7.1 - one full
         * 1.3-2.4 MB batch GET per enqueue event at ~1.35 events/s.
         *
         * Policy: leading-edge cooldown - the first trigger dispatches at
         * once (first-pull latency ~0), triggers within the window are
         * coalesced away. 5s matches the server's own push debounce floor
         * (push/service.go defaultDebounce=5s), so client pull cadence never
         * outruns server-side coalescing: <=1 event-triggered pull per 5s
         * (<=0.2/s vs ~1.35 events/s). Worst case for a suppressed trigger:
         * this window. Worst case if the gate state is lost entirely
         * (process death / cancelled work): the 15-min periodic worker.
         * Failed pulls keep WorkManager's existing exponential backoff via
         * Result.retry - a success backs off by this cooldown, a failure
         * backs off by WorkManager backoff.
         */
        const val EVENT_COOLDOWN_MILLIS = 5_000L

        private val eventTriggerGate = PullTriggerGate(EVENT_COOLDOWN_MILLIS) {
            SystemClock.elapsedRealtime()
        }

        fun start(context: Context) {
            val work = PeriodicWorkRequestBuilder<PullMessagesWorker>(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    work
                )
        }

        /**
         * Event-triggered entry point used by EventsReceiver (MessageEnqueued
         * and Ping) and SSEForegroundService.onConnected. Coalesces bursts of
         * events into at most one [start] per [EVENT_COOLDOWN_MILLIS] while
         * the first trigger still pulls promptly. Lifecycle starts
         * (GatewayService.start) keep the direct [start].
         */
        fun startCoalesced(context: Context) {
            if (!eventTriggerGate.tryAcquire()) return
            start(context)
        }

        fun getStateLiveData(context: Context) = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME)
        }
    }
}

/**
 * Leading-edge cooldown gate (T7): [tryAcquire] returns true for the first
 * call, then for any call at least [cooldownMillis] after the last acquired
 * call. Pure JVM logic with an injected [clock] so the coalescing bound is
 * unit-testable without WorkManager or Android timers.
 */
internal class PullTriggerGate(
    private val cooldownMillis: Long,
    private val clock: () -> Long,
) {
    private var lastAcquiredAt: Long? = null

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock()
        val last = lastAcquiredAt ?: run {
            lastAcquiredAt = now
            return true
        }
        if (now - last < cooldownMillis) return false
        lastAcquiredAt = now
        return true
    }
}
