@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package com.savr.app.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.savr.app.ads.Ads
import com.savr.app.ads.BannerAd
import com.savr.app.ads.findActivity
import com.savr.app.data.*
import com.savr.app.ui.components.*
import com.savr.app.ui.theme.*
import com.savr.app.vm.*
import kotlinx.coroutines.delay

/** App-wide feedback: an iOS-style banner instead of an Android toast. */
@Suppress("UnusedReceiverParameter")
fun Context.toast(m: String) = Hud.show(m)
const val DISCLAIMER = "Only download content you own or have permission to save. You are responsible for respecting copyright and the terms of each platform."

// ---------------- Brand visuals ----------------
private val InstaBrush = Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)))
/** Glyph + colour for each platform (generic glyphs, no official logos). */
fun platGlyph(p: Plat): ImageVector = when (p) {
    Plat.Instagram -> Icons.Rounded.CameraAlt; Plat.Facebook -> Icons.Rounded.People; Plat.YouTube -> Icons.Rounded.PlayArrow
    Plat.WhatsApp -> Icons.Rounded.Chat; Plat.Web -> Icons.Rounded.Public; Plat.Unknown -> Icons.Rounded.Link }
fun platColor(p: Plat): Color = when (p) {
    Plat.Instagram -> Color(0xFFD62976); Plat.Facebook -> Color(0xFF1877F2); Plat.YouTube -> Color(0xFFFF0000)
    Plat.WhatsApp -> Color(0xFF25D366); else -> Sys.Gray }
fun platBrush(p: Plat): Brush = if (p == Plat.Instagram) InstaBrush else Brush.linearGradient(listOf(platColor(p), platColor(p)))
fun platFromLabel(l: String) = Plat.entries.firstOrNull { it.label == l } ?: Plat.Web

/** The app icon (olive squircle, white arrow-into-tray), reused on onboarding and the Settings header. */
@Composable
fun AppIcon(size: Dp) = Box(Modifier.size(size).clip(RoundedCornerShape(size * .23f))
    .background(Brush.linearGradient(listOf(Color(0xFF8C9C52), OliveDark))), Alignment.Center) {
    Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(size * .58f)) }

// ---------------- Onboarding: iOS "Welcome" page ----------------
@Composable
fun Onboarding(onDone: () -> Unit) {
    val features = listOf(
        Triple(Icons.Rounded.ArrowCircleDown, Sys.Blue, "Save from anywhere" to "Instagram, Facebook and YouTube videos, straight to your gallery."),
        Triple(Icons.Rounded.IosShare, Sys.Green, "Share. Done." to "Tap Share in any app and choose Savr. No copying needed."),
        Triple(Icons.Rounded.Chat, Color(0xFF25D366), "WhatsApp statuses" to "Keep the photos and videos from statuses you've viewed."),
        Triple(Icons.Rounded.Lock, Sys.Orange, "No account needed" to "Open and go. Your library stays on your phone."))
    var explain by remember { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else onDone() }
    var step by remember { mutableIntStateOf(0) } // drives the staggered entrance
    LaunchedEffect(Unit) { repeat(features.size + 2) { delay(110); step++ } }
    val iconScale by animateFloatAsState(if (step > 0) 1f else .6f, spring(.55f, 300f), label = "icon")
    if (explain) IosAlert("“Savr” Would Like to Send You Notifications",
        "We'll show download progress and let you know when a video is saved." + if (Build.VERSION.SDK_INT <= 28) " Savr also needs storage access to save to your gallery." else "",
        listOf(AlertAction("Not Now") { explain = false; onDone() }, AlertAction("Continue", AlertStyle.Cancel) { explain = false
            when { Build.VERSION.SDK_INT <= 28 -> storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                Build.VERSION.SDK_INT >= 33 -> notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> onDone() } }), onDismiss = {})
    Column(Modifier.fillMaxSize().background(Ios.bg).systemBarsPadding().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(56.dp))
            Box(Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale; alpha = (iconScale - .6f) / .4f }) { AppIcon(92.dp) }
            Spacer(Modifier.height(22.dp))
            AnimatedVisibility(step > 0, enter = fadeIn(tween(400)) + slideInVertically(iosSpring()) { it / 3 }) {
                Text("Welcome to Savr", style = IosType.LargeTitle, color = Ios.label, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(40.dp))
            features.forEachIndexed { i, (icon, color, t) ->
                AnimatedVisibility(step > i + 1, enter = fadeIn(tween(350)) + slideInVertically(iosSpring()) { it / 2 }) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(40.dp)); Spacer(Modifier.width(18.dp))
                        Column { Text(t.first, style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = Ios.label)
                            Text(t.second, style = IosType.Subhead, color = Ios.secondary) }
                    }
                }
            }
        }
        AnimatedVisibility(step > features.size, enter = fadeIn() + slideInVertically(iosSpring()) { it }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Only save content you own or have permission to use.", Modifier.padding(bottom = 14.dp), style = IosType.Caption, color = Ios.secondary, textAlign = TextAlign.Center)
                IosButton("Continue", { explain = true }, Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
        }
    }
}

// ---------------- Shell: tab bar + iOS navigation transitions ----------------
private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)
private val TabRoutes = setOf("home", "downloads", "status", "settings")
private const val PushMs = 420

@Composable
fun MainScaffold(settings: Settings, sVm: SettingsVm, pending: MutableState<String?>, tab: MutableState<String?>) {
    val nav = rememberNavController(); val route = nav.currentBackStackEntryAsState().value?.destination?.route
    fun go(r: String) = nav.navigate(r) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
    LaunchedEffect(tab.value) { tab.value?.let { go(it); tab.value = null } }
    val dl: DownloadsVm = viewModel(); val active = dl.items.collectAsState().value.count { !it.isDone && it.status != "FAILED" }
    val tabs = listOf(Tab("home", "Download", Icons.Outlined.ArrowCircleDown, Icons.Rounded.ArrowCircleDown),
        Tab("downloads", "Library", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
        Tab("status", "Status", Icons.Outlined.PhotoLibrary, Icons.Rounded.PhotoLibrary),
        Tab("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings))
    val haptic = LocalHapticFeedback.current
    fun tabSwitch(a: String?, b: String?) = a in TabRoutes && b in TabRoutes
    Box(Modifier.fillMaxSize().background(Ios.bg)) {
        // UINavigationController push/pop: new screen slides in fully from the right, the old one drifts a third left.
        NavHost(nav, "home",
            enterTransition = { if (tabSwitch(initialState.destination.route, targetState.destination.route)) fadeIn(tween(150))
                else slideInHorizontally(tween(PushMs, easing = IosEase)) { it } },
            exitTransition = { if (tabSwitch(initialState.destination.route, targetState.destination.route)) fadeOut(tween(100))
                else slideOutHorizontally(tween(PushMs, easing = IosEase)) { -it / 3 } + fadeOut(tween(PushMs), .7f) },
            popEnterTransition = { if (tabSwitch(initialState.destination.route, targetState.destination.route)) fadeIn(tween(150))
                else slideInHorizontally(tween(PushMs, easing = IosEase)) { -it / 3 } + fadeIn(tween(PushMs), .7f) },
            popExitTransition = { if (tabSwitch(initialState.destination.route, targetState.destination.route)) fadeOut(tween(100))
                else slideOutHorizontally(tween(PushMs, easing = IosEase)) { it } }) {
            composable("home") { HomeScreen({ pending.value = it }, { go("downloads") }, { nav.navigate("player/$it") }, { go("status") }) }
            composable("downloads") { DownloadsScreen(settings.wifiOnly, { nav.navigate("player/$it") }, { nav.navigate("captions") }, { go("home") }) }
            composable("status") { StatusScreen(settings, sVm) }
            composable("settings") { SettingsScreen(settings, sVm) { nav.navigate("captions") } }
            composable("captions") { CaptionsScreen(onBack = { nav.popBackStack() }) }
            composable("player/{id}") { PlayerScreen(it.arguments!!.getString("id")!!, onBack = { nav.popBackStack() }) }
        }
        AnimatedVisibility(route in TabRoutes, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(PushMs, easing = IosEase)) { it }, exit = slideOutVertically(tween(PushMs, easing = IosEase)) { it }) {
            // UITabBar: translucent material, hairline on top, filled glyph + tint for the selected tab
            Column(Modifier.background(Ios.bar)) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.separator))
                Row(Modifier.navigationBarsPadding().height(52.dp)) { tabs.forEach { t ->
                    val on = route == t.route
                    val bounce = remember { Animatable(1f) }
                    LaunchedEffect(on) { if (on) { bounce.snapTo(.82f); bounce.animateTo(1f, spring(.4f, 700f)) } }
                    val c by animateColorAsState(if (on) Ios.tint else Sys.Gray, tween(150), label = "tab")
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) {
                        if (!on) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); go(t.route) },
                        Arrangement.Center, Alignment.CenterHorizontally) {
                        Box {
                            Icon(if (on) t.selectedIcon else t.icon, t.label, tint = c, modifier = Modifier.size(27.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value })
                            if (t.route == "downloads" && active > 0) Box(Modifier.align(Alignment.TopEnd).offset(8.dp, (-4).dp).sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                .clip(CircleShape).background(Sys.Red).padding(horizontal = 5.dp), Alignment.Center) {
                                Text("$active", color = Color.White, style = IosType.Caption2.copy(fontWeight = FontWeight.Bold)) }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(t.label, color = c, style = IosType.Caption2.copy(fontWeight = FontWeight.Medium))
                    }
                } }
            }
        }
    }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // clear focus on close: otherwise Android hands focus back to the link field and the keyboard pops up over the screen
    pending.value?.let { ExtractSheet(it, settings) { pending.value = null; focus.clearFocus(force = true) } }
    if (!settings.disclaimerOk) IosAlert("Before You Start", DISCLAIMER,
        listOf(AlertAction("I Understand", AlertStyle.Cancel) { sVm.update { it.copy(disclaimerOk = true) } }), onDismiss = {}, dismissible = false)
}

// ---------------- Home ----------------
@Composable
fun HomeScreen(onExtract: (String) -> Unit, onSeeAll: () -> Unit, onOpen: (String) -> Unit, onStatus: () -> Unit, vm: DownloadsVm = viewModel()) {
    val items by vm.items.collectAsState(); val recent = items.filter { it.isDone }.take(12); val running = items.filter { !it.isDone && it.status != "FAILED" }
    var text by rememberSaveable { mutableStateOf("") }; var suggestion by remember { mutableStateOf<String?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    val clip = LocalClipboardManager.current; val ctx = LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun submit(raw: String = text) { val u = extractUrl(raw) ?: raw.trim()
        error = when { u.isEmpty() -> "Paste a link first"; !u.startsWith("http") -> "That doesn't look like a link"; else -> null }
        if (error == null) { focus.clearFocus(); onExtract(u) } }
    // Auto-detect a link in the clipboard each time the app regains focus. Android 10+ only allows clipboard reads once the
    // window is focused (ON_RESUME is too early and gets denied), so key off window focus instead of the lifecycle.
    val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) {
        val u = runCatching { clip.getText()?.text }.getOrNull()?.let(::extractUrl)
        suggestion = if (u != null && detectPlatform(u) != Plat.Unknown && u != text) u else null } }
    val active = detectPlatform(text)
    val list = rememberLazyListState(); val collapsed by rememberCollapsed(list)
    IosPage("Download", collapsed) { top -> LazyColumn(state = list, contentPadding = PaddingValues(top = top, bottom = TabBarClearance)) {
        item { LargeTitle("Download") }
        item { Column(Modifier.padding(horizontal = 16.dp)) {
            IosTextField(text, { text = it; error = null }, "Paste a video link", leading = if (text.isBlank()) Icons.Rounded.Link else platGlyph(active),
                leadingTint = if (active == Plat.Unknown) Ios.secondary else platColor(active), isError = error != null, onGo = { submit() },
                trailing = { AnimatedVisibility(text.isEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    IosButton("Paste", { val c = clip.getText()?.text
                        if (c.isNullOrBlank()) ctx.toast("Clipboard is empty") else { text = extractUrl(c) ?: c; suggestion = null; error = null } },
                        style = BtnStyle.Tinted, small = true) } })
            AnimatedVisibility(error != null, enter = expandVertically(iosSpring()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Error, null, tint = Sys.Red, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp))
                    Text(error.orEmpty(), style = IosType.Footnote, color = Sys.Red) } }
            AnimatedVisibility(suggestion != null, enter = expandVertically(iosSpring()) + fadeIn(), exit = shrinkVertically(iosSpring()) + fadeOut()) {
                val s = suggestion.orEmpty(); val p = detectPlatform(s)
                Row(Modifier.padding(top = 12.dp).fillMaxWidth().iosCard(14.dp).pressable { text = s; suggestion = null; submit(s) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(platGlyph(p), platColor(p), 40.dp, platBrush(p)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Link from ${p.label}", style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = Ios.label)
                        Text(s.removePrefix("https://").removePrefix("www."), style = IosType.Footnote, color = Ios.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.width(8.dp))
                    IosButton("Save", { text = s; suggestion = null; submit(s) }, small = true)
                }
            }
            Spacer(Modifier.height(14.dp))
            IosButton("Download", { submit() }, Modifier.fillMaxWidth(), Icons.Rounded.ArrowDownward)
        } }
        if (running.isNotEmpty()) item { val d = running.first()
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().iosCard(14.dp).pressable(onClick = onSeeAll).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(d.progress, d.progress == 0f, Icons.Rounded.ArrowDownward, size = 36.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (running.size == 1) "Downloading" else "Downloading ${running.size} items", style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = Ios.label)
                    Text(d.title, style = IosType.Footnote, color = Ios.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Icon(Icons.Rounded.ChevronRight, null, tint = Ios.tertiary)
            } }
        item { SectionTitle("Supported Apps") }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.SpaceBetween) {
            listOf(Plat.Instagram, Plat.Facebook, Plat.YouTube, Plat.WhatsApp).forEach { p ->
                Column(Modifier.width(76.dp).pressable(haptic = true) { if (p == Plat.WhatsApp) onStatus() else openPlatform(ctx, p) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box { IconBadge(platGlyph(p), platColor(p), 62.dp, platBrush(p))
                        if (active == p) Box(Modifier.align(Alignment.TopEnd).offset(5.dp, (-5).dp).size(20.dp).clip(CircleShape).background(Ios.bg).padding(2.dp).clip(CircleShape).background(Sys.Blue), Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) } }
                    Spacer(Modifier.height(6.dp))
                    Text(p.label, style = IosType.Caption, color = Ios.label, maxLines = 1)
                }
            } } }
        item { Text("Tap an app, copy a link, then come back.", Modifier.fillMaxWidth().padding(top = 10.dp), style = IosType.Footnote, color = Ios.secondary, textAlign = TextAlign.Center) }
        item { BannerAd(Modifier.padding(top = 18.dp)) }
        item { SectionTitle("Recent", if (recent.isNotEmpty()) "See All" else null, onSeeAll) }
        item { if (recent.isEmpty()) Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().iosCard(14.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VideoLibrary, null, tint = Ios.tertiary, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(14.dp))
                Column { Text("No downloads yet", style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = Ios.label)
                    Text("Videos you save will appear here.", style = IosType.Footnote, color = Ios.secondary) } }
            else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(recent, key = { it.id }) { d ->
                Column(Modifier.width(156.dp).animateItem().pressable { onOpen(d.id) }) {
                    MediaThumb(d, Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.height(7.dp))
                    Text(d.title, style = IosType.Footnote.copy(fontWeight = FontWeight.SemiBold), color = Ios.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(d.platform, style = IosType.Caption, color = Ios.secondary)
                } } } }
        item { IosSection(header = "How It Works") {
            IosRow("Copy a link", Icons.Rounded.Link, Sys.Blue, subtitle = "In Instagram, Facebook or YouTube tap Share, then Copy Link.")
            IosRow("Paste it here", Icons.Rounded.ContentPaste, Sys.Orange, subtitle = "Or share straight to Savr from any app.")
            IosRow("Saved to your gallery", Icons.Rounded.PhotoLibrary, Sys.Green, subtitle = "Find it in your gallery under $FOLDER, even offline.", divider = false)
        } }
    } }
}
/** Opens the platform's app if installed (so the user can copy a link), else its website. */
private fun openPlatform(ctx: Context, p: Plat) {
    val (pkg, web) = when (p) { Plat.Instagram -> "com.instagram.android" to "https://instagram.com"; Plat.Facebook -> "com.facebook.katana" to "https://facebook.com"
        else -> "com.google.android.youtube" to "https://youtube.com" }
    val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
        ?: android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(web))
    runCatching { ctx.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { ctx.toast("Couldn't open ${p.label}") }
}

/** Thumbnail with duration capsule; audio gets a music glyph on a tinted fill. */
@Composable
fun MediaThumb(d: DownloadEntity, modifier: Modifier = Modifier, showDuration: Boolean = true) = Box(modifier.background(Ios.fill)) {
    AsyncImage(d.thumbModel, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    if (d.type == "audio") Icon(Icons.Rounded.MusicNote, null, tint = Ios.tint, modifier = Modifier.align(Alignment.Center).size(30.dp))
    if (showDuration && d.durationSec > 0) Capsule(formatDuration(d.durationSec), Modifier.align(Alignment.BottomEnd).padding(5.dp))
}

// ---------------- Extract sheet ----------------
@Composable
fun ExtractSheet(url: String, settings: Settings, onDismiss: () -> Unit) {
    val vm: ExtractVm = viewModel(key = url); val dl: DownloadsVm = viewModel(); val ctx = LocalContext.current; val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(url) { vm.load(url) }
    val st by vm.state.collectAsState()
    LaunchedEffect(st) { (st as? ExtractState.Ready)?.let { if (settings.autoCopy && it.info.caption.isNotEmpty()) { clip.setText(AnnotatedString(it.info.caption)); ctx.toast("Caption copied") } } }
    var added by remember { mutableStateOf(false) }
    LaunchedEffect(added) { if (added) { delay(700); onDismiss(); Ads.onActionCompleted(ctx.findActivity()) } }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ios.bg,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp), dragHandle = { Grabber() }, scrimColor = Color.Black.copy(alpha = .4f)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().animateContentSize(iosSpring())) {
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                Text(when (st) { ExtractState.Loading -> "Loading"; is ExtractState.Failed -> ""; is ExtractState.Ready -> "Save Video" },
                    Modifier.align(Alignment.Center), style = IosType.Headline, color = Ios.label)
                IconCircle(Icons.Rounded.Close, "Close", onDismiss, Modifier.align(Alignment.CenterEnd))
            }
            when (val s = st) {
                ExtractState.Loading -> Column {
                    Skeleton(Modifier.fillMaxWidth().aspectRatio(16f / 9), 14.dp); Spacer(Modifier.height(14.dp))
                    Skeleton(Modifier.fillMaxWidth(.75f).height(18.dp)); Spacer(Modifier.height(8.dp)); Skeleton(Modifier.fillMaxWidth().height(13.dp))
                    Spacer(Modifier.height(18.dp)); Skeleton(Modifier.fillMaxWidth().height(34.dp), 9.dp); Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().height(50.dp), Arrangement.Center, Alignment.CenterVertically) {
                        ActivitySpinner(Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("Fetching from ${detectPlatform(url).label.takeIf { detectPlatform(url) != Plat.Unknown } ?: "the web"}…", style = IosType.Subhead, color = Ios.secondary) }
                }
                is ExtractState.Failed -> Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Sys.Orange.copy(alpha = .15f)), Alignment.Center) {
                        Icon(Icons.Rounded.Warning, null, tint = Sys.Orange, modifier = Modifier.size(36.dp)) }
                    Spacer(Modifier.height(14.dp)); Text(s.kind.title, style = IosType.Title3, color = Ios.label); Spacer(Modifier.height(6.dp))
                    Text(s.kind.message, textAlign = TextAlign.Center, style = IosType.Subhead, color = Ios.secondary, modifier = Modifier.padding(horizontal = 12.dp))
                    Spacer(Modifier.height(22.dp))
                    if (s.kind == ErrorKind.Network || s.kind == ErrorKind.Server) {
                        IosButton("Try Again", { vm.load(url) }, Modifier.fillMaxWidth(), Icons.Rounded.Refresh)
                        IosButton("Cancel", onDismiss, Modifier.fillMaxWidth(), style = BtnStyle.Plain)
                    } else IosButton("OK", onDismiss, Modifier.fillMaxWidth(), style = BtnStyle.Gray)
                }
                is ExtractState.Ready -> { val i = s.info
                    var q by remember(i) { mutableIntStateOf(i.qualities.indexOfFirst { it.label == settings.quality }.coerceAtLeast(0)) }
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(14.dp)).background(Ios.fill)) {
                        if (i.thumbnail.isNotEmpty()) AsyncImage(i.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(when (i.type) { "audio" -> Icons.Rounded.MusicNote; "image" -> Icons.Rounded.Image; else -> Icons.Rounded.Movie }, null,
                            tint = Ios.tertiary, modifier = Modifier.align(Alignment.Center).size(56.dp)) // direct links have no thumbnail
                        Row(Modifier.padding(10.dp).clip(CircleShape).background(platBrush(i.platform)).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(platGlyph(i.platform), null, tint = Color.White, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp))
                            Text(i.platform.label, color = Color.White, style = IosType.Caption.copy(fontWeight = FontWeight.SemiBold)) }
                        if (i.durationSec > 0) Capsule(formatDuration(i.durationSec), Modifier.align(Alignment.BottomEnd).padding(10.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(i.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = IosType.Headline, color = Ios.label)
                    if (i.caption.isNotEmpty() && i.caption != i.title) { var more by remember { mutableStateOf(false) }
                        Text(i.caption, Modifier.padding(top = 4.dp).clickable(remember { MutableInteractionSource() }, null) { more = !more },
                            maxLines = if (more) 12 else 2, overflow = TextOverflow.Ellipsis, style = IosType.Subhead, color = Ios.secondary) }
                    Spacer(Modifier.height(16.dp))
                    if (i.qualities.size > 1) SegmentedControl(i.qualities.map { if (it.label == "Audio") "Audio Only" else it.label }, q, { q = it })
                    val sel = i.qualities[q.coerceIn(0, i.qualities.lastIndex)]
                    Text(listOfNotNull(if (sel.label == "Audio") "Sound only" else if (i.qualities.size > 1) "${sel.label} video" else null,
                        sel.bytes?.takeIf { it > 0 }?.let { "about ${formatSize(it)}" }, if (settings.wifiOnly) "waits for Wi-Fi" else null).joinToString(" · ").ifEmpty { " " },
                        Modifier.fillMaxWidth().padding(top = 6.dp), style = IosType.Footnote, color = Ios.secondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    // Button morphs into a confirmation before the sheet closes
                    val bg by animateColorAsState(if (added) Sys.Green else Ios.tint, tween(250), label = "dl")
                    Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(bg).pressable(!added) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress); dl.start(i, sel, settings.wifiOnly); added = true },
                        Arrangement.Center, Alignment.CenterVertically) {
                        AnimatedContent(added, transitionSpec = { (scaleIn(iosSpring()) + fadeIn()) togetherWith fadeOut() }, label = "dlc") { a ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (a) Icons.Rounded.CheckCircle else Icons.Rounded.ArrowDownward, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                                Text(if (a) (if (settings.wifiOnly) "Queued for Wi-Fi" else "Added to Library") else "Download", style = IosType.Headline, color = Color.White) } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (i.caption.isNotEmpty()) ActionTile(Icons.Rounded.ContentCopy, "Caption", Modifier.weight(1f)) { clip.setText(AnnotatedString(i.caption)); ctx.toast("Caption copied") }
                        if (i.hashtags.isNotEmpty()) ActionTile(Icons.Rounded.Tag, "Hashtags", Modifier.weight(1f)) { clip.setText(AnnotatedString(i.hashtags.asHashtagString())); ctx.toast("Hashtags copied") }
                        ActionTile(Icons.Rounded.IosShare, "Share Link", Modifier.weight(1f)) { MediaSaver.shareText(ctx, i.sourceUrl) }
                    }
                }
            }
        }
    }
}

/** Card button with a glyph above a short label (iOS share-sheet action style). */
@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Column(modifier.height(66.dp).iosCard(12.dp).pressable(haptic = true, onClick = onClick), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Ios.tint, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(5.dp))
        Text(label, style = IosType.Caption.copy(fontWeight = FontWeight.Medium), color = Ios.label)
    }
