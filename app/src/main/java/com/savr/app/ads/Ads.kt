package com.savr.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.savr.app.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** AdMob: UMP consent -> SDK init -> banners + frequency-capped interstitials. Unit IDs come from BuildConfig (test IDs in debug). */
object Ads {
    private const val INTERSTITIAL_EVERY = 2          // show on every 2nd eligible action (download / status save)
    private const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    private val started = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false); val ready: StateFlow<Boolean> = _ready
    private val _privacyRequired = MutableStateFlow(false); val privacyRequired: StateFlow<Boolean> = _privacyRequired
    private var interstitial: InterstitialAd? = null; private var loading = false
    private var actions = 0; private var lastShown = 0L

    /** Call from Activity.onCreate. Shows the consent form when required (EEA/UK), then starts the SDK. */
    fun init(activity: Activity) {
        val ci = UserMessagingPlatform.getConsentInformation(activity)
        ci.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                _privacyRequired.value = ci.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                if (ci.canRequestAds()) start(activity) }
        }, { if (ci.canRequestAds()) start(activity) }) // consent update failed: fall back to previous session's consent
        if (ci.canRequestAds()) start(activity)
    }

    fun showPrivacyOptions(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) { }

    private fun start(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        val app = activity.applicationContext
        CoroutineScope(Dispatchers.IO).launch { // initialize off the main thread
            MobileAds.initialize(app) {}
            withContext(Dispatchers.Main) { _ready.value = true; loadInterstitial(app) }
        }
    }

    private fun loadInterstitial(ctx: Context) {
        if (loading || interstitial != null || !_ready.value) return
        loading = true
        InterstitialAd.load(ctx, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; loading = false }
            override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null; loading = false }
        })
    }

    /** Call after a completed user action. Shows an interstitial only if one is loaded and the frequency cap allows it. */
    fun onActionCompleted(activity: Activity?) {
        if (activity == null || !_ready.value) return
        actions++
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        if (ad == null) { loadInterstitial(activity.applicationContext); return }
        if (actions < INTERSTITIAL_EVERY || (lastShown != 0L && now - lastShown < INTERSTITIAL_COOLDOWN_MS)) return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadInterstitial(activity.applicationContext) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { loadInterstitial(activity.applicationContext) }
        }
        interstitial = null; actions = 0; lastShown = now
        ad.show(activity)
    }
}

fun Context.findActivity(): Activity? { var c = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

/** Adaptive banner. Nothing before consent/SDK init; a labelled slot while loading (no layout jump); collapses if no fill. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val ready by Ads.ready.collectAsState()
    if (!ready) return
    var failed by remember { mutableStateOf(false) }; var loaded by remember { mutableStateOf(false) }
    if (failed) return
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        val width = maxWidth.value.toInt()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val size = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, width) }
        Box(Modifier.width(size.width.dp).height(size.height.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
            if (!loaded) Text("Ad", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            key(size) { // ad size is fixed per AdView: recreate on rotation / width change
                AndroidView(factory = { c -> AdView(c).apply {
                        adUnitId = BuildConfig.ADMOB_BANNER_ID; setAdSize(size)
                        adListener = object : AdListener() {
                            override fun onAdLoaded() { loaded = true }
                            override fun onAdFailedToLoad(e: LoadAdError) { failed = true } }
                        loadAd(AdRequest.Builder().build()) } },
                    modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() }) }
        }
    }
}
