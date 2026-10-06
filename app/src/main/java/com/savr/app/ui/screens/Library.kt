@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package com.savr.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.savr.app.data.*
import com.savr.app.ui.components.*
import com.savr.app.ui.theme.*
import com.savr.app.ads.BannerAd
import com.savr.app.vm.DownloadsVm

// ---------------- Library (downloads) ----------------
@Composable
fun DownloadsScreen(wifiOnly: Boolean, onOpen: (String) -> Unit, onCaptions: () -> Unit, onHome: () -> Unit, vm: DownloadsVm = viewModel()) {
    val all by vm.items.collectAsState(); val ctx = LocalContext.current; val haptic = LocalHapticFeedback.current
    var seg by remember { mutableIntStateOf(0) }; var grid by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf(setOf<String>()) }
    var toDelete by remember { mutableStateOf<List<DownloadEntity>>(emptyList()) }
    fun endSelect() { selecting = false; selected = emptySet() }
    androidx.activity.compose.BackHandler(selecting) { endSelect() }
    val types = listOf("all", "video", "image", "audio"); val items = all.filter { seg == 0 || it.type == types[seg] }
    val inProgress = items.filter { !it.isDone }; val saved = items.filter { it.isDone }
    fun tap(d: DownloadEntity) { if (selecting) selected = selected.toggle(d.id) else if (d.isDone) onOpen(d.id) }
    fun long(d: DownloadEntity) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selecting = true; selected = selected + d.id }
    val list = rememberLazyListState(); val gridState = rememberLazyGridState()
    val collapsed by if (grid) rememberCollapsed(gridState) else rememberCollapsed(list)
    IosPage(if (selecting) (if (selected.isEmpty()) "Select Items" else "${selected.size} Selected") else "Library", collapsed,
        alwaysInline = selecting,
        leading = { AnimatedVisibility(selecting, enter = fadeIn(), exit = fadeOut()) {
            BarButton(if (selected.size == items.size && items.isNotEmpty()) "Deselect All" else "Select All") {
                selected = if (selected.size == items.size) emptySet() else items.map { it.id }.toSet() } } },
        actions = {
            if (selecting) BarButton("Done", bold = true) { endSelect() }
            else {
                BarButton(icon = Icons.Rounded.FormatQuote, desc = "Captions", onClick = onCaptions)
                BarButton(icon = if (grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, desc = "Change layout") { grid = !grid }
                if (all.isNotEmpty()) BarButton("Select") { selecting = true }
            } }) { top ->
        val header: @Composable () -> Unit = {
            Column {
                if (!selecting) LargeTitle("Library") else Spacer(Modifier.height(8.dp))
                SegmentedControl(listOf("All", "Videos", "Photos", "Audio"), seg, { seg = it }, Modifier.padding(horizontal = 16.dp))
                BannerAd(Modifier.padding(top = 12.dp))
            }
        }
        val empty: @Composable () -> Unit = {
            EmptyState(Icons.Rounded.ArrowCircleDown, if (seg == 0) "No Downloads" else "Nothing Here",
                if (seg == 0) "Videos you save appear here and play even when you're offline." else "Try another filter.", Modifier.padding(top = 40.dp)) {
                if (seg == 0) IosButton("Paste a Link", onHome, icon = Icons.Rounded.ContentPaste, style = BtnStyle.Tinted, small = true) }
        }
        if (grid) LazyVerticalGrid(GridCells.Fixed(3), state = gridState, contentPadding = PaddingValues(top = top, bottom = TabBarClearance),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header(); Spacer(Modifier.height(12.dp)) } }
            if (items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { empty() }
            items(items, key = { it.id }) { d -> GridTile(d, selecting, d.id in selected, Modifier.animateItem(), { tap(d) }, { long(d) }) }
        }
        else LazyColumn(state = list, contentPadding = PaddingValues(top = top, bottom = TabBarClearance)) {
            item { header() }
            if (items.isEmpty()) item { empty() }
            listOf("In Progress" to inProgress, "Saved" to saved).forEach { (title, rows) -> if (rows.isNotEmpty()) {
                item(key = "h_$title") { SectionHeader(if (title == "Saved" && inProgress.isEmpty()) "${rows.size} item${if (rows.size > 1) "s" else ""}" else title, Modifier.animateItem()) }
                itemsIndexed(rows, key = { _, d -> d.id }) { i, d ->
                    val shape = RoundedCornerShape(topStart = if (i == 0) 12.dp else 0.dp, topEnd = if (i == 0) 12.dp else 0.dp,
                        bottomStart = if (i == rows.lastIndex) 12.dp else 0.dp, bottomEnd = if (i == rows.lastIndex) 12.dp else 0.dp)
                    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v -> when (v) {
                        SwipeToDismissBoxValue.StartToEnd -> { if (d.isDone) MediaSaver.share(ctx, d.mediaUri, d.mime); false }
                        SwipeToDismissBoxValue.EndToStart -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); toDelete = listOf(d); false }
                        else -> false } }, positionalThreshold = { it * .35f })
                    SwipeToDismissBox(state, modifier = Modifier.padding(horizontal = 16.dp).animateItem().clip(shape), enableDismissFromStartToEnd = d.isDone && !selecting,
                        enableDismissFromEndToStart = !selecting, backgroundContent = {
                            val end = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
                            Row(Modifier.fillMaxSize().background(if (end) Sys.Red else Sys.Blue).padding(horizontal = 22.dp),
                                if (end) Arrangement.End else Arrangement.Start, Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(if (end) Icons.Rounded.Delete else Icons.Rounded.IosShare, null, tint = Color.White)
                                    Text(if (end) "Delete" else "Share", color = Color.White, style = IosType.Caption.copy(fontWeight = FontWeight.SemiBold)) } } }) {
                        DownloadRow(d, wifiOnly, vm, selecting, d.id in selected, i != rows.lastIndex, { tap(d) }, { long(d) })
                    }
                }
            } }
        }
    }
    // Selection toolbar (replaces the tab bar visually while selecting)
    Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
        AnimatedVisibility(selecting, enter = slideInVertically(iosSpring()) { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Row(Modifier.fillMaxWidth().background(Ios.bar).navigationBarsPadding().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val sel = all.filter { it.id in selected }
                BarButton(icon = Icons.Rounded.IosShare, desc = "Share", color = if (sel.size == 1 && sel[0].isDone) Ios.tint else Ios.tertiary) {
                    sel.singleOrNull()?.takeIf { it.isDone }?.let { MediaSaver.share(ctx, it.mediaUri, it.mime) } }
                Text(if (selected.isEmpty()) "Select Items" else "${selected.size} Selected", Modifier.weight(1f), style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = Ios.label, textAlign = TextAlign.Center)
                BarButton(icon = Icons.Rounded.Delete, desc = "Delete", color = if (selected.isEmpty()) Ios.tertiary else Sys.Red) { if (sel.isNotEmpty()) toDelete = sel }
            }
        }
    }
    if (toDelete.isNotEmpty()) { val n = toDelete.size
        IosAlert(if (n == 1) "Delete Download?" else "Delete $n Downloads?", "${if (n == 1) "This item" else "These items"} will also be removed from your gallery. This can't be undone.",
            listOf(AlertAction("Cancel", AlertStyle.Cancel) { toDelete = emptyList() },
                AlertAction("Delete", AlertStyle.Destructive) { toDelete.forEach(vm::remove); toDelete = emptyList(); endSelect(); ctx.toast(if (n == 1) "Deleted" else "$n deleted") }),
            onDismiss = { toDelete = emptyList() }) }
}
private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

/** iOS edit-mode selection circle. */
@Composable
private fun CheckCircle(on: Boolean, onDark: Boolean = false) {
    val s by animateFloatAsState(if (on) 1f else 0f, iosSpring(), label = "chk")
    Box(Modifier.size(24.dp).clip(CircleShape).background(if (onDark) Color.Black.copy(alpha = .25f) else Color.Transparent)
        .border(1.5.dp, if (onDark) Color.White else Ios.tertiary, CircleShape), Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(Sys.Blue), Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun DownloadRow(d: DownloadEntity, wifiOnly: Boolean, vm: DownloadsVm, selecting: Boolean, selected: Boolean, divider: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().background(Ios.card).combinedClickable(remember { MutableInteractionSource() }, LocalIndication.current, onLongClick = onLong, onClick = onClick)) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(selecting, enter = expandHorizontally(iosSpring()) + fadeIn(), exit = shrinkHorizontally(iosSpring()) + fadeOut()) {
                Row { CheckCircle(selected); Spacer(Modifier.width(12.dp)) } }
            MediaThumb(d, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), showDuration = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = Ios.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                if (d.isDone) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(platGlyph(platFromLabel(d.platform)), null, tint = platColor(platFromLabel(d.platform)), modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp))
                    Text(listOfNotNull(d.platform, d.durationSec.takeIf { it > 0 }?.let(::formatDuration),
                        DateUtils.getRelativeTimeSpanString(d.createdAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()).joinToString(" · "),
                        style = IosType.Footnote, color = Ios.secondary, maxLines = 1) }
                else {
                    Text(when (d.status) { "FAILED" -> "Download failed. Tap ↻ to retry"; "PAUSED" -> "Paused · ${(d.progress * 100).toInt()}%"
                        "QUEUED" -> if (wifiOnly) "Waiting for Wi-Fi" else "Waiting…"; else -> if (d.progress == 0f) "Preparing…" else "${(d.progress * 100).toInt()}%" },
                        style = IosType.Footnote, color = if (d.status == "FAILED") Sys.Red else Ios.secondary)
                    Spacer(Modifier.height(6.dp)); ProgressBar(d.progress, color = if (d.status == "FAILED") Sys.Red else if (d.status == "PAUSED") Sys.Gray else Ios.tint)
                }
            }
            Spacer(Modifier.width(10.dp))
            if (!selecting) {
                if (!d.isDone) {
                    val running = d.status == "RUNNING" || d.status == "QUEUED"
                    ProgressRing(d.progress, d.status == "QUEUED" || (d.status == "RUNNING" && d.progress == 0f),
                        when (d.status) { "FAILED" -> Icons.Rounded.Refresh; "PAUSED" -> Icons.Rounded.ArrowDownward; else -> Icons.Rounded.Stop },
                        color = if (d.status == "FAILED") Sys.Red else Ios.tint, size = 34.dp) {
                        if (running) vm.pause(d) else vm.resume(d, wifiOnly) }
                    Spacer(Modifier.width(4.dp))
                    IconCircle(Icons.Rounded.Close, "Cancel", { vm.remove(d); ctx.toast("Download cancelled") }, size = 26.dp)
                } else Icon(Icons.Rounded.ChevronRight, null, tint = Ios.tertiary)
            }
        }
        if (divider) Box(Modifier.padding(start = 88.dp).fillMaxWidth().height(0.5.dp).background(Ios.separator))
    }
}

/** Photos-style square tile. */
@Composable
private fun GridTile(d: DownloadEntity, selecting: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    val s by animateFloatAsState(if (selected) .92f else 1f, iosSpring(), label = "tile")
    Box(modifier.aspectRatio(1f).graphicsLayer { scaleX = s; scaleY = s }.combinedClickable(onLongClick = onLong, onClick = onClick)) {
        MediaThumb(d, Modifier.fillMaxSize())
        if (!d.isDone) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)), Alignment.Center) {
            ProgressRing(d.progress, d.progress == 0f, null, Color.White, 38.dp) }
        if (d.type == "video" && d.isDone && d.durationSec == 0) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp))
        if (selecting) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { CheckCircle(selected, onDark = true) }
    }
}

// ---------------- Player (Photos-style) ----------------
@Composable
fun VideoSurface(uri: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(uri) { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView({ PlayerView(it).apply { this.player = player } }, modifier)
}

@Composable
fun PlayerScreen(id: String, onBack: () -> Unit, vm: DownloadsVm = viewModel()) {
    val e = vm.items.collectAsState().value.find { it.id == id }; val ctx = LocalContext.current; val clip = LocalClipboardManager.current
    var confirm by remember { mutableStateOf(false) }; var chrome by remember { mutableStateOf(true) }
    LightStatusIcons()
    val missing = remember(e?.mediaUri) { e != null && !MediaSaver.exists(ctx, e.mediaUri) } // deleted from the gallery app
    val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, tween(220), label = "chrome")
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            e == null -> {}
            missing -> EmptyStateDark("File Not Found", "It was moved or deleted from your gallery.")
            e.type == "image" -> AsyncImage(e.mediaUri, null, Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { chrome = !chrome }, contentScale = ContentScale.Fit)
            else -> { VideoSurface(e.mediaUri, Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 52.dp))
                if (e.type == "audio") Column(Modifier.align(Alignment.Center).padding(bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF8C9C52), OliveDark))), Alignment.Center) {
                        if (e.thumb.isNotEmpty()) AsyncImage(e.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(80.dp)) }
                    Spacer(Modifier.height(20.dp)); Text(e.title, color = Color.White, style = IosType.Title3, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp)) } }
        }
        // top bar: Done + centered title over a soft gradient
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = chromeAlpha }.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
            .statusBarsPadding().height(52.dp).padding(horizontal = 8.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) { BarButton("Done", color = Color.White, bold = true, onClick = onBack) }
            if (e != null) Text(e.title, Modifier.align(Alignment.Center).widthIn(max = 220.dp), color = Color.White, style = IosType.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // bottom toolbar
        if (e != null) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { alpha = chromeAlpha }.background(Color.Black.copy(alpha = .6f))
            .navigationBarsPadding().height(52.dp), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            val on = !missing
            BarButton(icon = Icons.Rounded.IosShare, desc = "Share", color = if (on) Color.White else Color.Gray) { if (on) MediaSaver.share(ctx, e.mediaUri, e.mime) }
            BarButton(icon = Icons.Rounded.OpenInNew, desc = "Open in another app", color = if (on) Color.White else Color.Gray) { if (on) MediaSaver.openWith(ctx, e.mediaUri, e.mime) }
            BarButton(icon = Icons.Rounded.FormatQuote, desc = "Copy caption", color = if (e.caption.isNotEmpty()) Color.White else Color.Gray) {
                if (e.caption.isNotEmpty()) { clip.setText(AnnotatedString(e.caption)); ctx.toast("Caption copied") } }
            BarButton(icon = Icons.Rounded.Delete, desc = "Delete", color = Color.White) { confirm = true }
        }
    }
    if (confirm && e != null) IosAlert("Delete Download?", "It will also be removed from your gallery.",
        listOf(AlertAction("Cancel", AlertStyle.Cancel) { confirm = false }, AlertAction("Delete", AlertStyle.Destructive) { confirm = false; vm.remove(e); ctx.toast("Deleted"); onBack() }),
        onDismiss = { confirm = false })
}

/** Black full-screen media screens: force light status/nav bar icons (restored on exit) so they stay visible in light theme. */
@Composable
fun LightStatusIcons() {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window ?: return@DisposableEffect onDispose {}
        val c = androidx.core.view.WindowCompat.getInsetsController(window, view)
        val s = c.isAppearanceLightStatusBars; val n = c.isAppearanceLightNavigationBars
        c.isAppearanceLightStatusBars = false; c.isAppearanceLightNavigationBars = false
        onDispose { c.isAppearanceLightStatusBars = s; c.isAppearanceLightNavigationBars = n }
    }
}

@Composable
private fun EmptyStateDark(title: String, msg: String) = Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
    Icon(Icons.Rounded.BrokenImage, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(52.dp)); Spacer(Modifier.height(12.dp))
    Text(title, color = Color.White, style = IosType.Title3); Text(msg, color = Color.White.copy(alpha = .6f), style = IosType.Subhead)
}

// ---------------- Caption manager ----------------
@Composable
fun CaptionsScreen(onBack: () -> Unit, vm: DownloadsVm = viewModel()) {
    val items = vm.items.collectAsState().value.filter { it.caption.isNotEmpty() || it.hashtags.isNotEmpty() }
    val clip = LocalClipboardManager.current; val ctx = LocalContext.current; var editing by remember { mutableStateOf<DownloadEntity?>(null) }
    val list = rememberLazyListState(); val collapsed by rememberCollapsed(list)
    IosPage("Captions", collapsed, leading = { BackButton("Back", onClick = onBack) }) { top ->
        LazyColumn(state = list, contentPadding = PaddingValues(top = top, bottom = 40.dp)) {
            item { LargeTitle("Captions") }
            if (items.isEmpty()) item { EmptyState(Icons.Rounded.FormatQuote, "No Captions", "Captions and hashtags are saved automatically with each download.", Modifier.padding(top = 40.dp)) }
            items.forEach { d -> item(key = d.id) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().animateItem().iosCard(14.dp).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(platGlyph(platFromLabel(d.platform)), null, tint = platColor(platFromLabel(d.platform)), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(d.title, Modifier.weight(1f), style = IosType.Headline, color = Ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (d.caption.isNotEmpty()) Text(d.caption, Modifier.padding(top = 6.dp), style = IosType.Subhead, color = Ios.secondary, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    if (d.hashtags.isNotEmpty()) Text(d.hashtags, Modifier.padding(top = 6.dp), style = IosType.Footnote, color = Ios.tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (d.caption.isNotEmpty()) IosButton("Copy", { clip.setText(AnnotatedString(d.caption)); ctx.toast("Caption copied") }, icon = Icons.Rounded.ContentCopy, style = BtnStyle.Gray, small = true)
                        IosButton("Edit", { editing = d }, icon = Icons.Rounded.Edit, style = BtnStyle.Gray, small = true)
                        if (d.hashtags.isNotEmpty()) IosButton("Tags", { clip.setText(AnnotatedString(d.hashtags)); ctx.toast("Hashtags copied") }, icon = Icons.Rounded.Tag, style = BtnStyle.Gray, small = true)
                    }
                }
            } }
        }
    }
    editing?.let { d -> var t by remember(d.id) { mutableStateOf(d.caption) }
        ModalBottomSheet({ editing = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ios.bg,
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp), dragHandle = { Grabber() }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding()) {
                // iOS sheet nav bar: Cancel | title | Save
                Box(Modifier.fillMaxWidth().height(44.dp)) {
                    Box(Modifier.align(Alignment.CenterStart)) { BarButton("Cancel") { editing = null } }
                    Text("Edit Caption", Modifier.align(Alignment.Center), style = IosType.Headline, color = Ios.label)
                    Box(Modifier.align(Alignment.CenterEnd)) { BarButton("Save", bold = true) { vm.setCaption(d, t); editing = null; ctx.toast("Caption saved") } }
                }
                Spacer(Modifier.height(8.dp))
                BasicTextField(t, { t = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp).iosCard(12.dp).padding(14.dp),
                    textStyle = IosType.Body.copy(color = Ios.label), cursorBrush = SolidColor(Ios.tint))
                Text("${t.length} characters", Modifier.fillMaxWidth().padding(top = 6.dp, end = 4.dp), style = IosType.Footnote, color = Ios.secondary, textAlign = TextAlign.End)
            }
        }
    }
}
