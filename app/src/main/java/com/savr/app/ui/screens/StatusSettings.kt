@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package com.savr.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.savr.app.BuildConfig
import com.savr.app.ads.Ads
import com.savr.app.ads.BannerAd
import com.savr.app.ads.findActivity
import com.savr.app.data.*
import com.savr.app.ui.components.*
import com.savr.app.ui.theme.*
import com.savr.app.vm.*
import kotlinx.coroutines.launch

private val WhatsGreen = Color(0xFF25D366)

// ---------------- WhatsApp Status Saver ----------------
@Composable
fun StatusScreen(settings: Settings, sVm: SettingsVm, vm: StatusVm = viewModel()) {
    val ctx = LocalContext.current; val files by vm.files.collectAsState(); val loading by vm.loading.collectAsState(); val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf(false) }; var preview by remember { mutableStateOf<StatusFile?>(null) }
    val saved by vm.saved.collectAsState(); val haptic = LocalHapticFeedback.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) {
        if (!StatusRepo.looksLikeStatusFolder(uri)) ctx.toast("That isn't WhatsApp's .Statuses folder")
        runCatching { StatusRepo.persist(ctx, uri) }; sVm.update { it.copy(statusTrees = it.statusTrees + uri.toString()) } } }
    LaunchedEffect(settings.statusTrees) { if (settings.statusTrees.isNotEmpty()) vm.refresh(settings.statusTrees) }
    // open on whichever tab actually has statuses (first load only, so it never jumps under the user)
    var autoPicked by remember { mutableStateOf(false) }
    LaunchedEffect(files) { if (!autoPicked && files.isNotEmpty()) { autoPicked = true; videos = files.none { !it.isVideo } } }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, settings.statusTrees) { // new statuses appear after viewing them in WhatsApp: refresh on return
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && settings.statusTrees.isNotEmpty()) vm.refresh(settings.statusTrees, quiet = true) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }
    fun save(f: StatusFile, close: Boolean) { if (!saving) scope.launch { saving = true; val ok = vm.save(f); saving = false
        ctx.toast(if (ok) "Saved to gallery" else "Could not save"); if (ok) { if (close) preview = null; Ads.onActionCompleted(ctx.findActivity()) } } }
    val spin by rememberInfiniteTransition(label = "refresh").animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "r")

    if (settings.statusTrees.isEmpty()) {
        val list = rememberLazyListState(); val collapsed by rememberCollapsed(list)
        IosPage("Status", collapsed) { top -> LazyColumn(state = list, contentPadding = PaddingValues(top = top, bottom = TabBarClearance)) {
            item { LargeTitle("Status") }
            item { Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(Icons.Rounded.Chat, WhatsGreen, 76.dp)
                Spacer(Modifier.height(16.dp))
                Text("Save WhatsApp Statuses", style = IosType.Title2, color = Ios.label, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Keep photos and videos from statuses you've already viewed. Savr only reads WhatsApp's status folder.",
                    style = IosType.Subhead, color = Ios.secondary, textAlign = TextAlign.Center)
            } }
            item { IosSection(header = "One-time setup") {
                IosRow("Tap Allow Access below", Icons.Rounded.TouchApp, Sys.Blue, subtitle = "The folder picker opens on WhatsApp's status folder.")
                IosRow("Use this folder", Icons.Rounded.FolderOpen, Sys.Orange, subtitle = "Tap “Use this folder”, then “Allow”.")
                IosRow("Tap any status to save it", Icons.Rounded.Download, Sys.Green, subtitle = "Long-press to save without opening.", divider = false)
            } }
            item { Column(Modifier.padding(16.dp).padding(top = 8.dp)) {
                IosButton("Allow Access to WhatsApp", { picker.launch(StatusRepo.initialUri(false)) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                IosButton("Use WhatsApp Business", { picker.launch(StatusRepo.initialUri(true)) }, Modifier.fillMaxWidth(), style = BtnStyle.Tinted)
            } }
        } }
    } else {
        val grid = rememberLazyGridState(); val collapsed by rememberCollapsed(grid)
        val shown = files.filter { it.isVideo == videos }
        IosPage("Status", collapsed, actions = {
            BarButton(icon = Icons.Rounded.CreateNewFolder, desc = "Add folder") { picker.launch(StatusRepo.initialUri(settings.statusTrees.none { "w4b" in it })) }
            Box(Modifier.rotate(if (loading) spin else 0f)) { BarButton(icon = Icons.Rounded.Refresh, desc = "Refresh") { vm.refresh(settings.statusTrees) } }
        }) { top ->
            LazyVerticalGrid(GridCells.Fixed(3), state = grid, contentPadding = PaddingValues(top = top, bottom = TabBarClearance),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column {
                    LargeTitle("Status")
                    SegmentedControl(listOf("Photos (${files.count { !it.isVideo }})", "Videos (${files.count { it.isVideo }})"), if (videos) 1 else 0, { videos = it == 1 },
                        Modifier.padding(horizontal = 16.dp))
                    BannerAd(Modifier.padding(top = 12.dp))
                    if (!loading && shown.isNotEmpty()) Text("Tap to preview · Long-press to save", Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        style = IosType.Footnote, color = Ios.secondary, textAlign = TextAlign.Center) else Spacer(Modifier.height(12.dp))
                } }
                if (loading && files.isEmpty()) items(12) { Skeleton(Modifier.aspectRatio(1f), 0.dp) }
                else if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(if (videos) Icons.Rounded.VideocamOff else Icons.Rounded.HideImage, "No Statuses", "Open WhatsApp and view a few statuses, then come back here.", Modifier.padding(top = 32.dp)) }
                else items(shown, key = { it.uri }) { f ->
                    val isSaved = f.uri in saved
                    Box(Modifier.aspectRatio(1f).animateItem().background(Ios.fill).combinedClickable(onClick = { preview = f },
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); save(f, false) })) {
                        AsyncImage(f.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (f.isVideo) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp))
                        val s by animateFloatAsState(if (isSaved) 1f else 0f, spring(.5f, 500f), label = "saved")
                        Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).graphicsLayer { scaleX = s; scaleY = s }
                            .clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(Sys.Green), Alignment.Center) {
                            Icon(Icons.Rounded.Check, "Saved", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
    }
    // Full-screen viewer: Done on top, Share | Save toolbar at the bottom (Photos app layout)
    preview?.let { f -> Dialog({ preview = null }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
        val s by animateFloatAsState(if (shown) 1f else .92f, iosSpring(), label = "pv")
        Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer { scaleX = s; scaleY = s; alpha = (s - .92f) / .08f }) {
            if (f.isVideo) VideoSurface(f.uri, Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 52.dp))
            else AsyncImage(f.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent))).statusBarsPadding().height(52.dp).padding(horizontal = 8.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) { BarButton("Done", color = Color.White, bold = true) { preview = null } }
                Text(if (f.isVideo) "Video" else "Photo", Modifier.align(Alignment.Center), color = Color.White, style = IosType.Headline)
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .6f)).navigationBarsPadding().height(52.dp),
                Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                BarButton(icon = Icons.Rounded.IosShare, desc = "Share", color = Color.White) { MediaSaver.share(ctx, f.uri, f.mime) }
                val isSaved = f.uri in saved
                Row(Modifier.clip(CircleShape).background(if (isSaved) Sys.Green else Color.White).pressable(!saving && !isSaved, haptic = true) { save(f, true) }
                    .padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (saving) ActivitySpinner(Modifier.size(18.dp), Color.Black)
                    else Icon(if (isSaved) Icons.Rounded.Check else Icons.Rounded.Download, null, tint = if (isSaved) Color.White else Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSaved) "Saved" else "Save", style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = if (isSaved) Color.White else Color.Black)
                }
            }
        } } }
}

// ---------------- Settings (iOS Settings app layout) ----------------
@Composable
fun SettingsScreen(s: Settings, vm: SettingsVm, onCaptions: () -> Unit) {
    val ctx = LocalContext.current; var dialog by remember { mutableStateOf<String?>(null) }; val adPrivacy by Ads.privacyRequired.collectAsState()
    var cacheSize by remember { mutableLongStateOf(ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) }
    val scroll = rememberScrollState(); val collapsed by rememberCollapsed(scroll)
    IosPage("Settings", collapsed) { top -> Column(Modifier.verticalScroll(scroll).padding(top = top, bottom = TabBarClearance)) {
        LargeTitle("Settings")
        // App header card (like the Apple ID cell)
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().iosCard().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(60.dp); Spacer(Modifier.width(14.dp))
            Column { Text("Savr", style = IosType.Title3, color = Ios.label)
                Text("Version ${BuildConfig.VERSION_NAME} · Videos & statuses", style = IosType.Footnote, color = Ios.secondary) }
        }
        IosSection(header = "Appearance") {
            Column(Modifier.padding(12.dp)) {
                SegmentedControl(listOf("Automatic", "Light", "Dark"), listOf("system", "light", "dark").indexOf(s.theme).coerceAtLeast(0),
                    { i -> vm.update { it.copy(theme = listOf("system", "light", "dark")[i]) } }) }
        }
        IosSection(header = "Downloads", footer = "Wi-Fi Only holds downloads until you're on Wi-Fi, so they don't use mobile data.") {
            IosRow("Default Quality", Icons.Rounded.HighQuality, Sys.Blue, value = if (s.quality == "Audio") "Audio Only" else s.quality, chevron = true) { dialog = "quality" }
            IosRow("Save Location", Icons.Rounded.Folder, Sys.Orange, value = "Gallery › $FOLDER")
            IosRow("Wi-Fi Only", Icons.Rounded.Wifi, Sys.Teal, trailing = { IosSwitch(s.wifiOnly) { v -> vm.update { it.copy(wifiOnly = v) } } })
            IosRow("Auto-Copy Captions", Icons.Rounded.ContentPaste, Sys.Purple, trailing = { IosSwitch(s.autoCopy) { v -> vm.update { it.copy(autoCopy = v) } } })
            IosRow("Caption Manager", Icons.Rounded.FormatQuote, Sys.Pink, chevron = true, divider = false, onClick = onCaptions)
        }
        IosSection(header = "Storage & Access") {
            IosRow("Clear Cache", Icons.Rounded.DeleteSweep, Sys.Gray, value = formatSize(cacheSize)) {
                ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }; cacheSize = 0; ctx.toast("Cache cleared") }
            IosRow("Notifications", Icons.Rounded.Notifications, Sys.Red, chevron = true) {
                open(ctx, android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }
            IosRow("WhatsApp Access", Icons.Rounded.Chat, WhatsGreen, divider = false, chevron = s.statusTrees.isNotEmpty(),
                value = if (s.statusTrees.isEmpty()) "Not Set" else "${s.statusTrees.size} Folder${if (s.statusTrees.size > 1) "s" else ""}") {
                if (s.statusTrees.isNotEmpty()) dialog = "trees" }
        }
        IosSection(header = "Support") {
            IosRow("Rate Savr", Icons.Rounded.Star, Sys.Yellow, chevron = true) {
                if (!open(ctx, "market://details?id=${ctx.packageName}")) open(ctx, "https://play.google.com/store/apps/details?id=${ctx.packageName}") }
            IosRow("Share Savr", Icons.Rounded.IosShare, Sys.Blue, chevron = true) {
                MediaSaver.shareText(ctx, "Save videos and statuses with Savr: https://play.google.com/store/apps/details?id=${ctx.packageName}") }
            IosRow("Send Feedback", Icons.Rounded.Mail, Sys.Indigo, chevron = true) {
                open(ctx, android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + BuildConfig.SUPPORT_EMAIL))
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, "Savr ${BuildConfig.VERSION_NAME} feedback")) }
            if (adPrivacy) IosRow("Ad Privacy Choices", Icons.Rounded.Shield, Sys.Teal, chevron = true) { ctx.findActivity()?.let(Ads::showPrivacyOptions) }
            IosRow("Privacy Policy", Icons.Rounded.PanTool, Sys.Gray, chevron = true) { open(ctx, BuildConfig.PRIVACY_URL) }
            IosRow("Terms of Service", Icons.Rounded.Description, Sys.Gray, chevron = true, divider = false) { open(ctx, BuildConfig.TERMS_URL) }
        }
        Text(DISCLAIMER, Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 20.dp), style = IosType.Footnote, color = Ios.secondary, textAlign = TextAlign.Center)
    } }
    when (dialog) {
        "quality" -> IosActionSheet("Default Quality", listOf("HD" to "Best quality, larger file", "SD" to "Smaller file, saves data", "Audio" to "Sound only").map { (o, sub) ->
            SheetAction(if (o == "Audio") "Audio Only" else o, s.quality == o, subtitle = sub) { vm.update { it.copy(quality = o) }; dialog = null } }) { dialog = null }
        "trees" -> IosAlert("Reset WhatsApp Access?", "Savr will stop reading your WhatsApp status folder${if (s.statusTrees.size > 1) "s" else ""}. You can grant access again from the Status tab.",
            listOf(AlertAction("Cancel", AlertStyle.Cancel) { dialog = null },
                AlertAction("Reset", AlertStyle.Destructive) { s.statusTrees.forEach { StatusRepo.release(ctx, it) }; vm.update { it.copy(statusTrees = emptySet()) }; dialog = null; ctx.toast("Access removed") }),
            onDismiss = { dialog = null })
    }
}
private fun open(ctx: android.content.Context, url: String) = open(ctx, android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
private fun open(ctx: android.content.Context, i: android.content.Intent): Boolean =
    runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
