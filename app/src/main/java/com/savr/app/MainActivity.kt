package com.savr.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.savr.app.ui.components.HudHost
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.savr.app.ads.Ads
import com.savr.app.data.extractUrl
import com.savr.app.ui.screens.MainScaffold
import com.savr.app.ui.screens.Onboarding
import com.savr.app.ui.theme.SavrTheme
import com.savr.app.vm.SettingsVm

class MainActivity : ComponentActivity() {
    private val pending = mutableStateOf<String?>(null)
    private val tab = mutableStateOf<String?>(null) // tab requested by a notification tap

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        Ads.init(this) // consent (UMP) then Mobile Ads SDK
        var loaded = false
        splash.setKeepOnScreenCondition { !loaded } // hold the splash until settings are read: no white flash
        setContent {
            val vm: SettingsVm = viewModel(); val s by vm.settings.collectAsState()
            val settings = s ?: return@setContent // wait for DataStore
            SideEffect { loaded = true }
            val dark = when (settings.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            // Status/nav bar icons follow the in-app theme, not just the system one
            SideEffect { WindowCompat.getInsetsController(window, window.decorView).run {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } }
            SavrTheme(settings.theme) {
                Box(Modifier.fillMaxSize()) {
                    // crossfade from onboarding into the app
                    AnimatedContent(settings.onboarded, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) }, label = "root") { on ->
                        if (!on) Onboarding(onDone = { vm.update { it.copy(onboarded = true) } }) else MainScaffold(settings, vm, pending, tab) }
                    HudHost() // iOS-style banners above everything
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }

    // ACTION_SEND text/plain: URL detection happens here, platform recognition in the extractor sheet
    private fun handle(i: Intent?) {
        i?.getStringExtra(EXTRA_TAB)?.let { tab.value = it }
        if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            pending.value = extractUrl(t) ?: t
        }
    }

    companion object { const val EXTRA_TAB = "tab" }
}
