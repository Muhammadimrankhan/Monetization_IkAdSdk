package com.monetization.ikadplugin.ads

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdRequest

/**
 * Exponential backoff for one ad slot (2s, 4s, 8s ... capped at 64s, at most [MAX_ATTEMPTS] in a row),
 * plus the shared freshness rule for cached ads.
 *
 * Only transient failures are retried: a no-fill means no buyer wanted this request, and asking again
 * straight away mostly adds requests that fail too, which lowers the match rate.
 */
internal class AdRetryPolicy {

    private val handler = Handler(Looper.getMainLooper())
    private var attempt = 0
    private var pending: Runnable? = null

    /** Schedules [block] after the next backoff delay. Returns false once the attempts are used up. */
    fun schedule(block: () -> Unit): Boolean {
        if (attempt >= MAX_ATTEMPTS) return false
        cancel()
        attempt++
        val delayMs = (1000L shl attempt).coerceAtMost(MAX_DELAY_MS)
        val runnable = Runnable {
            pending = null
            block()
        }
        pending = runnable
        handler.postDelayed(runnable, delayMs)
        return true
    }

    /** Call after a successful load. */
    fun reset() {
        attempt = 0
        cancel()
    }

    fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val MAX_DELAY_MS = 64_000L

        /**
         * A loaded ad is shown only while younger than this. AdMob ads expire after about an hour;
         * staying under it avoids shows that fail or never count an impression.
         */
        const val MAX_AD_AGE_MS = 50 * 60 * 1000L

        fun now(): Long = SystemClock.elapsedRealtime()

        fun isRetryable(errorCode: Int): Boolean =
            errorCode == AdRequest.ERROR_CODE_INTERNAL_ERROR ||
                errorCode == AdRequest.ERROR_CODE_NETWORK_ERROR
    }
}
