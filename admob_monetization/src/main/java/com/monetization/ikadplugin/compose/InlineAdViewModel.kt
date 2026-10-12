package com.monetization.ikadplugin.compose

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.monetization.ikadplugin.one_time_purchase.ProductsPurchaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Shared machinery for [NativeAdViewModel] and [BannerAdViewModel].
 *
 * The loaded ad ([T]) is owned **here**, not by composition. That is what lets a loaded ad survive
 * navigating away and back: the same ad is handed straight back to the new composition, with no
 * shimmer and no fresh request. A slot that ended up [AdSlotState.Hidden] (failed to fill, or was
 * suppressed) has nothing worth keeping, so re-entering the screen retries.
 */
abstract class InlineAdViewModel<T : Any> internal constructor(
    protected val placementKey: String,
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow<AdSlotState>(AdSlotState.Idle)
    val state: StateFlow<AdSlotState> = _state.asStateFlow()

    private val _ad = MutableStateFlow<T?>(null)
    internal val ad: StateFlow<T?> = _ad.asStateFlow()

    /** Inputs of the last accepted request, so recomposition alone can't trigger a reload. */
    private var lastRequest: Any? = null

    /** Activity the current ad was created for, when [bindsToActivity]. */
    private var adActivity: WeakReference<Activity>? = null

    /** Identifies the in-flight request; callbacks from any older one are ignored. */
    private var requestToken = 0
    private var pendingFailure: (() -> Unit)? = null
    private var timeoutJob: Job? = null

    /**
     * True when the ad holds a reference to the Activity it was created with (banner `AdView`s do),
     * so a new Activity instance (a configuration change) must drop it and load again.
     */
    protected open val bindsToActivity: Boolean = false

    init {
        scope.launch {
            ProductsPurchaseHelper.getInstance().appPurchased.collect { purchased ->
                if (purchased) {
                    release()
                    _state.value = AdSlotState.Hidden
                }
            }
        }
    }

    /**
     * Called on every entry to the screen. Does nothing while a request is in flight or when an
     * ad for the same inputs is already loaded; requests one otherwise, including after a failure.
     *
     * [load] must call into the relevant ad class and report through the two callbacks it gets.
     */
    protected fun requestLoad(
        requestKey: Any,
        activity: Activity,
        allowed: Boolean,
        load: (onLoaded: (T) -> Unit, onFailed: () -> Unit) -> Unit,
    ) {
        if (bindsToActivity && adActivity?.get() !== activity) {
            release()
            lastRequest = null
            _state.value = AdSlotState.Idle
        }

        if (_state.value == AdSlotState.Loading) return
        if (_state.value == AdSlotState.Loaded && requestKey == lastRequest && _ad.value != null) {
            // Already filled — reuse it as-is. No refresh, no new request.
            return
        }

        release()
        lastRequest = requestKey

        if (!allowed) {
            _state.value = AdSlotState.Hidden
            return
        }

        adActivity = WeakReference(activity)
        _state.value = AdSlotState.Loading
        val token = ++requestToken
        val onFailed: () -> Unit = {
            if (token == requestToken) {
                timeoutJob?.cancel()
                pendingFailure = null
                _state.value = AdSlotState.Hidden
            }
        }
        pendingFailure = onFailed
        // If AdMob never answers, the shimmer must not stay up forever: give up and hide the slot.
        timeoutJob = scope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (token == requestToken && _state.value == AdSlotState.Loading) {
                cancelPendingRequest(onFailed)
                onFailed()
            }
        }
        load(
            { loaded ->
                if (token == requestToken) {
                    timeoutJob?.cancel()
                    pendingFailure = null
                    _ad.value = loaded
                    _state.value = AdSlotState.Loaded
                } else {
                    destroyAd(loaded)
                }
            },
            onFailed,
        )
    }

    /** Drops the cached ad so the next entry to the screen requests a fresh one. */
    fun reset() {
        release()
        lastRequest = null
        _state.value = AdSlotState.Idle
    }

    /** Lets a subclass destroy its ad (e.g. `AdView.destroy()`) before it is dropped. */
    protected open fun destroyAd(ad: T) {}

    /** Lets a subclass tell its ad class to forget the callbacks of an abandoned request. */
    protected open fun cancelPendingRequest(onFailed: () -> Unit) {}

    private fun release() {
        requestToken++
        timeoutJob?.cancel()
        timeoutJob = null
        pendingFailure?.let(::cancelPendingRequest)
        pendingFailure = null
        _ad.value?.let(::destroyAd)
        _ad.value = null
        adActivity = null
    }

    override fun onCleared() {
        release()
        scope.cancel()
        super.onCleared()
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 20_000L
    }
}
