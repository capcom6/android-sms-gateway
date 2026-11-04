package me.capcom.smsgateway.modules.gateway.workers

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.map
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
         * Policy: leading-edge cooldown with a trailing catch-up - the first
         * trigger dispatches at once (first-pull latency ~0), triggers within
         * the window are coalesced and schedule one follow-up pull at the end
         * of the window. A message enqueued just after the leading pull is
         * therefore fetched within one window instead of waiting for the
         * 15-min periodic worker. 5s matches the server's own push debounce
         * floor (push/service.go defaultDebounce=5s), so client pull cadence
         * never outruns server-side coalescing: <=1 event-triggered pull per
         * 5s (<=0.2/s vs ~1.35 events/s). Failed pulls keep WorkManager's
         * existing exponential backoff via Result.retry.
         */
        const val EVENT_COOLDOWN_MILLIS = 5_000L

        private const val FOLLOW_UP_WORK_NAME = "PullMessagesWorkerFollowUp"

        private val eventTriggerGate = PullTriggerGate(EVENT_COOLDOWN_MILLIS) {
            SystemClock.elapsedRealtime()
        }

        // [start]/[stop]/[startCoalesced] are synchronized on the companion
        // object: a running follow-up must either enqueue before stop() (and
        // get cancelled by it) or see stopped and drop, never re-create
        // polling after shutdown. Defaults to false because callers of
        // [startCoalesced] only exist while the gateway is running (and a
        // restarted process may resume services without [start] when
        // autostart is off) - only an explicit [stop] must block pulls.
        @Volatile
        private var stopped = false

        @Synchronized
        fun start(context: Context) {
            stopped = false
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
         * the first trigger still pulls promptly. A trigger inside the window
         * schedules a single trailing pull at the end of the window so a
         * message enqueued after the leading pull is not delayed until the
         * periodic worker. Lifecycle starts (GatewayService.start) keep the
         * direct [start].
         */
        @Synchronized
        fun startCoalesced(context: Context) {
            if (stopped) return

            val remainingMillis = eventTriggerGate.tryAcquire()
            if (remainingMillis == 0L) {
                start(context)
                return
            }
            scheduleFollowUp(context, remainingMillis)
        }

        /**
         * Gate check for [PullMessagesFollowUpWorker]: dispatches the pull and
         * returns 0 when the cooldown elapsed, otherwise returns the remaining
         * delay. Unlike [startCoalesced] it never enqueues another follow-up -
         * the running follow-up must wait and retry itself, because
         * ExistingWorkPolicy.KEEP would discard an enqueue against its own
         * unfinished work name and drop the catch-up pull.
         */
        @Synchronized
        internal fun tryCoalesce(context: Context): Long {
            if (stopped) return 0L

            val remainingMillis = eventTriggerGate.tryAcquire()
            if (remainingMillis == 0L) {
                start(context)
            }
            return remainingMillis
        }

        private fun scheduleFollowUp(context: Context, delayMillis: Long) {
            val followUp = OneTimeWorkRequestBuilder<PullMessagesFollowUpWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(FOLLOW_UP_WORK_NAME, ExistingWorkPolicy.KEEP, followUp)
        }

        fun getStateLiveData(context: Context) = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

        @Synchronized
        fun stop(context: Context) {
            stopped = true
            WorkManager.getInstance(context)
                .cancelUniqueWork(NAME)
            WorkManager.getInstance(context)
                .cancelUniqueWork(FOLLOW_UP_WORK_NAME)
        }
    }
}

/**
 * Trailing pull for a coalesced trigger: waits out the remaining cooldown and
 * dispatches the pull itself, so a message enqueued after the leading pull is
 * fetched within one window instead of waiting for the 15-min periodic worker.
 */
class PullMessagesFollowUpWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        awaitPullDispatch(
            tryPull = { PullMessagesWorker.tryCoalesce(applicationContext) },
            sleep = { delay(it) },
        )
        return Result.success()
    }
}

/**
 * Retry loop for a coalesced follow-up: sleeps for the remaining cooldown and
 * retries until a pull is dispatched (0) or the gateway was stopped. Kept
 * dependency-free so the delayed-follow-up sequence is unit-testable.
 */
internal suspend fun awaitPullDispatch(
    tryPull: () -> Long,
    sleep: suspend (Long) -> Unit,
) {
    while (true) {
        val remainingMillis = tryPull()
        if (remainingMillis <= 0L) return
        sleep(remainingMillis)
    }
}

/**
 * Leading-edge cooldown gate (T7): [tryAcquire] returns 0 when the caller may
 * dispatch now (first call, or at least [cooldownMillis] after the last
 * acquired call), otherwise the remaining cooldown in milliseconds so the
 * caller can schedule a trailing pull. Pure JVM logic with an injected [clock]
 * so the coalescing bound is unit-testable without WorkManager or Android
 * timers.
 */
internal class PullTriggerGate(
    private val cooldownMillis: Long,
    private val clock: () -> Long,
) {
    private var lastAcquiredAt: Long? = null

    @Synchronized
    fun tryAcquire(): Long {
        val now = clock()
        val last = lastAcquiredAt ?: run {
            lastAcquiredAt = now
            return 0L
        }
        val remaining = cooldownMillis - (now - last)
        if (remaining > 0) return remaining
        lastAcquiredAt = now
        return 0L
    }
}
