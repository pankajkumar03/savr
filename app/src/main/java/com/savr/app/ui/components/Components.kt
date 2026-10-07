@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.savr.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savr.app.ui.theme.*
import kotlinx.coroutines.delay

// ============================================================================================================
//  iOS-style component kit. Patterns: inset grouped lists, large-title nav bar, segmented control, switch,
//  alerts / action sheets, HUD banners, App-Store progress ring, press-dimming + spring motion.
// ============================================================================================================

/** iOS "ease-out" curve used by UIKit navigation pushes. */
val IosEase = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
fun <T> iosSpring() = spring<T>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/** iOS press feedback: content dims and shrinks slightly while held, springs back on release. */
fun Modifier.pressable(enabled: Boolean = true, scaleTo: Float = 0.97f, haptic: Boolean = false, onLong: (() -> Unit)? = null, onClick: () -> Unit) = composed {
    val src = remember { MutableInteractionSource() }; val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, iosSpring(), label = "press")
    val a by animateFloatAsState(if (pressed) 0.6f else 1f, tween(if (pressed) 60 else 220), label = "dim")
    val h = LocalHapticFeedback.current
    this.graphicsLayer { scaleX = s; scaleY = s; alpha = a }
        .combinedClickable(src, null, enabled = enabled, onLongClick = onLong?.let { l -> { h.performHapticFeedback(HapticFeedbackType.LongPress); l() } }) {
            if (haptic) h.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
}

/** Grouped cell / card background. */
@Composable
fun Modifier.iosCard(radius: Dp = 12.dp): Modifier = this.clip(RoundedCornerShape(radius)).background(Ios.card)

// ---------------------------------------------------------------- Buttons
enum class BtnStyle { Filled, Tinted, Gray, Plain, Destructive }

@Composable
fun IosButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, style: BtnStyle = BtnStyle.Filled,
              enabled: Boolean = true, loading: Boolean = false, small: Boolean = false) {
    val (bg, fg) = when (style) {
        BtnStyle.Filled -> Ios.tint to Ios.onTint
        BtnStyle.Tinted -> Ios.tintSoft to Ios.tint
        BtnStyle.Gray -> Ios.fill to Ios.tint
        BtnStyle.Plain -> Color.Transparent to Ios.tint
        BtnStyle.Destructive -> Sys.Red.copy(alpha = .14f) to Sys.Red
    }
    Row(modifier.height(if (small) 34.dp else 50.dp).alpha(if (enabled) 1f else .35f)
        .clip(if (small) CircleShape else RoundedCornerShape(12.dp)).background(bg)
        .pressable(enabled && !loading, haptic = true, onClick = onClick).padding(horizontal = if (small) 14.dp else 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(loading, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "btn") { l ->
            if (l) ActivitySpinner(Modifier.size(20.dp), fg)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(if (small) 16.dp else 20.dp)); Spacer(Modifier.width(6.dp)) }
                Text(text, color = fg, style = if (small) IosType.Subhead.copy(fontWeight = FontWeight.SemiBold) else IosType.Headline, maxLines = 1)
            }
        }
    }
}

/** Legacy name kept for call sites: maps onto the iOS button styles. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
                  loading: Boolean = false, outlined: Boolean = false, enabled: Boolean = true) =
    IosButton(text, onClick, modifier, icon, if (outlined) BtnStyle.Tinted else BtnStyle.Filled, enabled, loading)

/** Round icon button (iOS "xmark.circle.fill"-style close, toolbar glyphs). */
@Composable
fun IconCircle(icon: ImageVector, desc: String?, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color = Ios.fill, fg: Color = Ios.secondary, size: Dp = 30.dp) =
    Box(modifier.size(size).clip(CircleShape).background(bg).pressable(scaleTo = .9f, onClick = onClick), Alignment.Center) {
        Icon(icon, desc, tint = fg, modifier = Modifier.size(size * .55f)) }

/** Toolbar text/icon button in the tint colour (nav bar items). */
@Composable
fun BarButton(text: String? = null, icon: ImageVector? = null, desc: String? = null, color: Color = Ios.tint, bold: Boolean = false, onClick: () -> Unit) =
    Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).pressable(scaleTo = 1f, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, desc, tint = color, modifier = Modifier.size(24.dp))
        if (text != null) Text(text, color = color, style = if (bold) IosType.Headline else IosType.Body)
    }

/** iOS back button: chevron + previous screen title. */
@Composable
fun BackButton(label: String = "Back", color: Color = Ios.tint, onClick: () -> Unit) =
    Row(Modifier.heightIn(min = 44.dp).pressable(scaleTo = 1f, onClick = onClick).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, style = IosType.Body)
    }

// ---------------------------------------------------------------- Navigation bar with large title
/** Collapses once the large title (first item) has scrolled under the bar. */
@Composable fun rememberCollapsed(s: LazyListState): State<Boolean> { val px = with(LocalDensity.current) { 36.dp.toPx() }
    return remember(s) { derivedStateOf { s.firstVisibleItemIndex > 0 || s.firstVisibleItemScrollOffset > px } } }
@Composable fun rememberCollapsed(s: LazyGridState): State<Boolean> { val px = with(LocalDensity.current) { 36.dp.toPx() }
    return remember(s) { derivedStateOf { s.firstVisibleItemIndex > 0 || s.firstVisibleItemScrollOffset > px } } }
@Composable fun rememberCollapsed(s: ScrollState): State<Boolean> { val px = with(LocalDensity.current) { 36.dp.toPx() }
    return remember(s) { derivedStateOf { s.value > px } } }

val NavBarHeight = 44.dp
/** Space lists leave at the bottom so the last row clears the floating tab bar. */
val TabBarClearance = 120.dp

/**
 * iOS page: translucent nav bar overlaid on scrolling content. The screen renders [LargeTitle] as its first item;
 * when it scrolls away the bar fades in its background, hairline and the small centered inline title.
 */
@Composable
fun IosPage(title: String, collapsed: Boolean, leading: @Composable RowScope.() -> Unit = {}, actions: @Composable RowScope.() -> Unit = {},
            alwaysInline: Boolean = false, content: @Composable (top: Dp) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + NavBarHeight
    val c = collapsed || alwaysInline
    val barAlpha by animateFloatAsState(if (c) 1f else 0f, tween(180), label = "bar")
    val barColor = Ios.bar
    Box(Modifier.fillMaxSize().background(Ios.bg)) {
        content(top)
        Column(Modifier.fillMaxWidth().drawBehind { drawRect(barColor.copy(alpha = barColor.alpha * barAlpha)) }) {
            Box(Modifier.statusBarsPadding().fillMaxWidth().height(NavBarHeight).padding(horizontal = 8.dp)) {
                Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, content = leading)
                Text(title, Modifier.align(Alignment.Center).graphicsLayer { alpha = barAlpha; translationY = (1 - barAlpha) * 8.dp.toPx() }
                    .widthIn(max = 220.dp), style = IosType.Headline, color = Ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).graphicsLayer { alpha = barAlpha }.background(Ios.separator))
        }
    }
}

/** 34sp bold title that sits at the top of the scrolling content. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp), style = IosType.LargeTitle, color = Ios.label)
}

// ---------------------------------------------------------------- Inset grouped lists (Settings-style)
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier.padding(start = 36.dp, end = 36.dp, top = 22.dp, bottom = 7.dp), style = IosType.Footnote, color = Ios.secondary)

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(start = 36.dp, end = 36.dp, top = 7.dp), style = IosType.Footnote, color = Ios.secondary)

@Composable
fun IosSection(modifier: Modifier = Modifier, header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) = Column(modifier) {
    if (header != null) SectionHeader(header) else Spacer(Modifier.height(18.dp))
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().iosCard(), content = content)
    if (footer != null) SectionFooter(footer)
}

/** Rounded-square coloured icon, like the glyphs in iOS Settings. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 29.dp, brush: Brush? = null) =
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .24f)).then(if (brush != null) Modifier.background(brush) else Modifier.background(color)), Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .62f)) }

@Composable
fun IosRow(title: String, icon: ImageVector? = null, iconColor: Color = Sys.Gray, subtitle: String? = null, value: String? = null,
           chevron: Boolean = false, divider: Boolean = true, destructive: Boolean = false, titleColor: Color? = null,
           trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(scaleTo = 1f, onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { IconBadge(icon, iconColor); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = IosType.Body, color = titleColor ?: if (destructive) Sys.Red else Ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = IosType.Footnote, color = Ios.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) Text(value, style = IosType.Body, color = Ios.secondary, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            trailing?.invoke()
            if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = Ios.tertiary, modifier = Modifier.padding(start = 4.dp).size(22.dp))
        }
        if (divider) Box(Modifier.padding(start = if (icon != null) 59.dp else 16.dp).fillMaxWidth().height(0.5.dp).background(Ios.separator))
    }
}

// ---------------------------------------------------------------- Switch & segmented control
/** UISwitch: 51x31, green track, white thumb with shadow, spring travel. */
@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val h = LocalHapticFeedback.current
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, iosSpring(), label = "thumb")
    val track by animateColorAsState(if (checked) Sys.Green else Ios.fill, tween(200), label = "track")
    Box(Modifier.size(51.dp, 31.dp).clip(CircleShape).background(track)
        .clickable(remember { MutableInteractionSource() }, null) { h.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(!checked) }) {
        Box(Modifier.offset(x = x, y = 2.dp).size(27.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** UISegmentedControl: gray track with a sliding raised thumb. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val h = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(Ios.fill).padding(2.dp)) {
        val w = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(w * selected.coerceAtLeast(0), iosSpring(), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().shadow(2.dp, RoundedCornerShape(7.dp)).background(Ios.thumb, RoundedCornerShape(7.dp)))
        Row(Modifier.fillMaxSize()) { options.forEachIndexed { i, o ->
            Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) {
                if (i != selected) { h.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) } }, Alignment.Center) {
                Text(o, style = IosType.Footnote.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium), color = Ios.label, maxLines = 1)
            } } }
    }
}

// ---------------------------------------------------------------- Text field
/** Rounded filled field (search-field look) with leading glyph and clear button. */
@Composable
fun IosTextField(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, leading: ImageVector? = null,
                 leadingTint: Color = Ios.secondary, isError: Boolean = false, onGo: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val border by animateColorAsState(if (isError) Sys.Red else Color.Transparent, label = "err")
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(Ios.fill).border(1.dp, border, RoundedCornerShape(12.dp))
        .padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            AnimatedContent(leading, transitionSpec = { (scaleIn(iosSpring()) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "lead") { ic ->
                Icon(ic, null, tint = leadingTint, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = IosType.Body, color = Ios.tertiary, maxLines = 1)
            BasicTextField(value, onValue, Modifier.fillMaxWidth(), singleLine = true, textStyle = IosType.Body.copy(color = Ios.label),
                cursorBrush = SolidColor(Ios.tint), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo?.invoke() }))
        }
        AnimatedVisibility(value.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            IconCircle(Icons.Rounded.Close, "Clear", { onValue("") }, Modifier.padding(horizontal = 4.dp), bg = Ios.tertiary, fg = Ios.card, size = 20.dp) }
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------- Chips
@Composable
fun PillChip(label: String, active: Boolean, onClick: () -> Unit = {}, icon: ImageVector? = null) {
    val bg by animateColorAsState(if (active) Ios.tint else Ios.fill, tween(180), label = "c")
    val fg by animateColorAsState(if (active) Color.White else Ios.label, tween(180), label = "f")
    Row(Modifier.clip(CircleShape).background(bg).pressable(haptic = true, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = fg, style = IosType.Subhead.copy(fontWeight = FontWeight.Medium))
    }
}

// ---------------------------------------------------------------- Progress
/** UIActivityIndicatorView: 8 fading spokes. */
@Composable
fun ActivitySpinner(modifier: Modifier = Modifier.size(20.dp), color: Color = Ios.secondary) {
    val t = rememberInfiniteTransition(label = "spin")
    val step by t.animateFloat(0f, 8f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "s")
    Canvas(modifier) {
        val r = size.minDimension / 2; val c = center; val w = r * .22f
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45 - 90).toDouble()); val k = ((i - step.toInt() + 8) % 8) / 8f
            drawLine(color.copy(alpha = 1f - k * .85f), Offset(c.x + (r * .45f * kotlin.math.cos(a)).toFloat(), c.y + (r * .45f * kotlin.math.sin(a)).toFloat()),
                Offset(c.x + (r * .95f * kotlin.math.cos(a)).toFloat(), c.y + (r * .95f * kotlin.math.sin(a)).toFloat()), w, StrokeCap.Round)
        }
    }
}

/** App Store-style circular progress with a glyph in the middle (stop / resume / retry). Spins while indeterminate. */
@Composable
fun ProgressRing(progress: Float, indeterminate: Boolean, icon: ImageVector?, color: Color = Ios.tint, size: Dp = 32.dp, onClick: (() -> Unit)? = null) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "ring")
    val rot by rememberInfiniteTransition(label = "rot").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
    Box(Modifier.size(size).then(if (onClick != null) Modifier.pressable(scaleTo = .85f, haptic = true, onClick = onClick) else Modifier), Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val sw = 2.5.dp.toPx(); val st = Stroke(sw, cap = StrokeCap.Round)
            drawArc(color.copy(alpha = .2f), 0f, 360f, false, style = st)
            if (indeterminate) drawArc(color, rot - 90f, 80f, false, style = st)
            else drawArc(color, -90f, 360f * p, false, style = st)
        }
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(size * .42f))
    }
}

/** Thin rounded UIProgressView bar. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Ios.tint) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "bar")
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Ios.fill)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(CircleShape).background(color)) }
}

/** Redacted placeholder that breathes while content loads. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, radius: Dp = 10.dp) {
    val a by rememberInfiniteTransition(label = "sk").animateFloat(.5f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(RoundedCornerShape(radius)).background(Ios.fill.copy(alpha = Ios.fill.alpha * a * 1.6f)))
}

// ---------------------------------------------------------------- Empty states / headers
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else .85f, iosSpring(), label = "es")
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 24.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = (s - .85f) / .15f },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Ios.tertiary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = IosType.Title2, color = Ios.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, textAlign = TextAlign.Center, style = IosType.Subhead, color = Ios.secondary)
        if (action != null) { Spacer(Modifier.height(18.dp)); action() }
    }
}

/** App Store-style section title with optional "See All". */
@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = IosType.Title2, color = Ios.label)
        if (action != null) BarButton(action, onClick = onAction)
    }

// ---------------------------------------------------------------- Alerts & action sheets
enum class AlertStyle { Default, Cancel, Destructive }
data class AlertAction(val text: String, val style: AlertStyle = AlertStyle.Default, val onClick: () -> Unit)

/** UIAlertController(.alert): 270dp card, centered text, hairline-separated buttons, pops in with a spring. */
@Composable
fun IosAlert(title: String, message: String? = null, actions: List<AlertAction>, onDismiss: () -> Unit, dismissible: Boolean = true) {
    Dialog(onDismiss, DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = false)) {
        var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
        val s by animateFloatAsState(if (shown) 1f else 1.15f, iosSpring(), label = "alert")
        Column(Modifier.width(270.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = ((1.15f - s) / .15f).coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(14.dp)).background(Ios.elevated.copy(alpha = .97f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp, 19.dp, 16.dp, 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = IosType.Headline, color = Ios.label, textAlign = TextAlign.Center)
                if (message != null) { Spacer(Modifier.height(4.dp)); Text(message, style = IosType.Footnote, color = Ios.label, textAlign = TextAlign.Center) }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.separator))
            @Composable fun Btn(a: AlertAction, m: Modifier) = Box(m.height(44.dp).pressable(scaleTo = 1f) { a.onClick() }, Alignment.Center) {
                Text(a.text, color = if (a.style == AlertStyle.Destructive) Sys.Red else Ios.tint,
                    style = if (a.style == AlertStyle.Cancel) IosType.Headline else IosType.Body, maxLines = 1) }
            if (actions.size == 2) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Btn(actions[0], Modifier.weight(1f)); Box(Modifier.fillMaxHeight().width(0.5.dp).background(Ios.separator)); Btn(actions[1], Modifier.weight(1f)) }
            else actions.forEachIndexed { i, a -> if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.separator)); Btn(a, Modifier.fillMaxWidth()) }
        }
    }
}

data class SheetAction(val text: String, val selected: Boolean = false, val destructive: Boolean = false, val subtitle: String? = null, val onClick: () -> Unit)

/** UIAlertController(.actionSheet): grouped options sliding up from the bottom plus a separate Cancel button. */
@Composable
fun IosActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss), Alignment.BottomCenter) {
            AnimatedVisibility(shown, enter = slideInVertically(iosSpring()) { it } + fadeIn()) {
                Column(Modifier.navigationBarsPadding().padding(8.dp).clickable(remember { MutableInteractionSource() }, null) {}) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ios.elevated.copy(alpha = .97f))) {
                        if (title != null) { Text(title, Modifier.fillMaxWidth().padding(14.dp), style = IosType.Footnote.copy(fontWeight = FontWeight.SemiBold), color = Ios.secondary, textAlign = TextAlign.Center)
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.separator)) }
                        actions.forEachIndexed { i, a ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.separator))
                            Row(Modifier.fillMaxWidth().heightIn(min = 57.dp).pressable(scaleTo = 1f, haptic = true) { a.onClick() }.padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(24.dp))
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(a.text, style = IosType.Title3.copy(fontWeight = if (a.selected) FontWeight.SemiBold else FontWeight.Normal), color = if (a.destructive) Sys.Red else Ios.tint)
                                    if (a.subtitle != null) Text(a.subtitle, style = IosType.Footnote, color = Ios.secondary) }
                                Box(Modifier.width(24.dp)) { if (a.selected) Icon(Icons.Rounded.Check, null, tint = Ios.tint, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(57.dp).clip(RoundedCornerShape(14.dp)).background(Ios.elevated).pressable(scaleTo = 1f, onClick = onDismiss), Alignment.Center) {
                        Text("Cancel", style = IosType.Title3, color = Ios.tint) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- HUD banner (replaces Android toasts)
enum class HudKind { Success, Error, Info }
data class HudMsg(val text: String, val kind: HudKind, val id: Long = System.nanoTime())

object Hud {
    val current = mutableStateOf<HudMsg?>(null)
    fun show(text: String, kind: HudKind = guess(text)) { current.value = HudMsg(text, kind) }
    private fun guess(t: String) = when {
        Regex("(?i)could ?n['o]t|failed|isn't|error|empty|not found").containsMatchIn(t) -> HudKind.Error
        Regex("(?i)will start|waiting|removed|cleared").containsMatchIn(t) -> HudKind.Info
        else -> HudKind.Success }
}

/** Drops a capsule from the top (like iOS system banners), springs in, auto-dismisses. */
@Composable
fun HudHost() {
    val msg by Hud.current; val h = LocalHapticFeedback.current
    var visible by remember { mutableStateOf<HudMsg?>(null) }
    LaunchedEffect(msg) { val m = msg ?: return@LaunchedEffect
        visible = m; if (m.kind == HudKind.Error) h.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(1900); if (Hud.current.value == m) { visible = null; Hud.current.value = null } }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), Alignment.TopCenter) {
        AnimatedVisibility(visible != null, enter = slideInVertically(spring(.6f, 400f)) { -it * 2 } + fadeIn(), exit = slideOutVertically(tween(250)) { -it * 2 } + fadeOut()) {
            val m = visible ?: return@AnimatedVisibility
            Row(Modifier.shadow(16.dp, CircleShape, spotColor = Color(0x40000000)).clip(CircleShape).background(Ios.elevated)
                .clickable { visible = null }.padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val (ic, col) = when (m.kind) { HudKind.Success -> Icons.Rounded.CheckCircle to Sys.Green; HudKind.Error -> Icons.Rounded.Error to Sys.Red; HudKind.Info -> Icons.Rounded.Info to Ios.tint }
                Icon(ic, null, tint = col, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Text(m.text, style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = Ios.label, maxLines = 2)
            }
        }
    }
}

// ---------------------------------------------------------------- misc
/** iOS sheet grabber. */
@Composable fun Grabber() = Box(Modifier.padding(top = 6.dp, bottom = 4.dp).size(36.dp, 5.dp).clip(CircleShape).background(Ios.tertiary))

/** Duration / label capsule over thumbnails. */
@Composable fun Capsule(t: String, m: Modifier = Modifier, icon: ImageVector? = null) =
    Row(m.clip(CircleShape).background(Color(0x8C000000)).padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp)) }
        Text(t, color = Color.White, style = IosType.Caption2.copy(fontWeight = FontWeight.SemiBold)) }

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "${bytes shr 10} KB"
    else -> "$bytes B" }

fun formatDuration(s: Int) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
