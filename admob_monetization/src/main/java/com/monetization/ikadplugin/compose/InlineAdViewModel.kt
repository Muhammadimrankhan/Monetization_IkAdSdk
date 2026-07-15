package com.monetization.ikadplugin.compose

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.ViewModel
import com.monetization.ikadplugin.one_time_purchase.ProductsPurchaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shared machinery for [NativeAdViewModel] and [BannerAdViewModel].
 *
 * The ad classes (`AdmobNativeAd`, `AdmobBannerAd`, `AdmobCollapsibleBannerAd`) report nothing back
 * to their callers: they load into the `LinearLayout` they were handed, hide it on failure, and
 * swap the shimmer for the real ad view on success. So instead of load callbacks, this ViewModel
 * watches that frame and derives [AdSlotState] from it.
 *
 * The frame is owned **here**, not by composition. That is what lets a loaded ad survive navigating
 * away and back: the same view — still holding the same ad — is handed straight back to the new
 * composition, with no shimmer and no fresh ad request. A slot that ended up [AdSlotState.Hidden]
 * (failed to fill, or was suppressed) has nothing worth keeping, so re-entering the screen retries.
 */
abstract class InlineAdViewModel internal constructor(
    protected val placementKey: String,
    private val adViewClass: Class<*>,
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow<AdSlotState>(AdSlotState.Idle)
    val state: StateFlow<AdSlotState> = _state.asStateFlow()

    private var watchJob: Job? = null

    private var adFrame: LinearLayout? = null
    private var frameActivity: Activity? = null

    /** Inputs of the last accepted request, so recomposition alone can't trigger a reload. */
    private var lastRequest: Any? = null

    init {
        scope.launch {
            ProductsPurchaseHelper.getInstance().appPurchased.collect { purchased ->
                if (purchased) {
                    watchJob?.cancel()
                    releaseFrame()
                    _state.value = AdSlotState.Hidden
                }
            }
        }
    }

    /**
     * The frame this placement loads into, ready to be attached by an `AndroidView` factory.
     *
     * Detaching happens **only** here. The frame outlives any single composition, so when the
     * screen is re-entered it may still be parented by the previous (now discarded) AndroidView
     * node, and adding it twice would throw.
     */
    internal fun frameToAttach(activity: Activity): LinearLayout =
        frameFor(activity).also { frame ->
            (frame.parent as? ViewGroup)?.removeView(frame)
        }

    /**
     * The frame this placement loads into, reused for as long as [activity] is the same instance.
     * A new Activity (a configuration change) makes the cached view stale — it is bound to a dead
     * context — so it is dropped and the placement starts over.
     *
     * Never touches the frame's parent: this runs from `LaunchedEffect`, which fires *after* the
     * AndroidView factory has already attached the frame. Detaching here would tear the live ad
     * straight back out of the layout.
     */
    private fun frameFor(activity: Activity): LinearLayout {
        val cached = adFrame
        if (cached != null && frameActivity === activity) return cached

        releaseFrame()
        val fresh = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        adFrame = fresh
        frameActivity = activity
        lastRequest = null
        _state.value = AdSlotState.Idle
        return fresh
    }

    /**
     * Called on every entry to the screen. Does nothing when an ad is already showing in the cached
     * frame; requests one otherwise — including after a previous failure.
     *
     * [load] must call into the relevant ad class with the frame from [frameFor].
     */
    protected fun requestLoad(
        requestKey: Any,
        activity: Activity,
        allowed: Boolean,
        load: (LinearLayout) -> Unit
    ) {
        val frame = frameFor(activity)

        if (_state.value == AdSlotState.Loading) return
        if (_state.value == AdSlotState.Loaded &&
            requestKey == lastRequest &&
            containsAdView(frame, adViewClass)
        ) {
            // Already filled and still intact — reuse it as-is. No refresh, no new request.
            return
        }

        lastRequest = requestKey

        if (!allowed) {
            watchJob?.cancel()
            _state.value = AdSlotState.Hidden
            return
        }

        frame.removeAllViews()
        frame.visibility = View.VISIBLE
        _state.value = AdSlotState.Loading
        load(frame)
        watchFrame(frame)
    }

    /** Drops the cached ad so the next entry to the screen requests a fresh one. */
    fun reset() {
        watchJob?.cancel()
        releaseFrame()
        lastRequest = null
        _state.value = AdSlotState.Idle
    }

    private fun watchFrame(frame: LinearLayout) {
        watchJob?.cancel()
        watchJob = scope.launch {
            var waited = 0L
            while (isActive && waited < LOAD_TIMEOUT_MS) {
                delay(POLL_INTERVAL_MS)
                waited += POLL_INTERVAL_MS

                if (frame.visibility == View.GONE) {
                    // The ad class hides the frame when the request is suppressed or fails.
                    _state.value = AdSlotState.Hidden
                    return@launch
                }
                if (containsAdView(frame, adViewClass)) {
                    _state.value = AdSlotState.Loaded
                    return@launch
                }
            }
            // Never filled and never explicitly failed: don't leave a shimmer up forever.
            if (_state.value == AdSlotState.Loading) _state.value = AdSlotState.Hidden
        }
    }

    /** Lets a subclass destroy the ad view it cached (e.g. `AdView.destroy()`) before it is dropped. */
    protected open fun destroyAdView(adView: View) {}

    private fun releaseFrame() {
        adFrame?.let { frame ->
            findAdView(frame, adViewClass)?.let(::destroyAdView)
            (frame.parent as? ViewGroup)?.removeView(frame)
            frame.removeAllViews()
        }
        adFrame = null
        frameActivity = null
    }

    override fun onCleared() {
        watchJob?.cancel()
        releaseFrame()
        scope.cancel()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 150L
        const val LOAD_TIMEOUT_MS = 20_000L
    }
}
