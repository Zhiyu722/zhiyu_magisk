package com.zhiyu.magisk.ui.glass

import android.os.Build
import android.util.Log
import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Liquid glass" design system for the Magisk app.
 *
 * ### Structure of a glassy screen
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     GlassBackground()                 // 1. colourful animated backdrop, painted behind everything
 *     GlassBackdropHost { source ->     // 2. records the screen content into a graphics layer
 *         Scaffold(
 *             containerColor = Color.Transparent,
 *             topBar = { GlassTopAppBar(...) },   // 3. drawn *outside* the recording, blurs it
 *         ) { padding ->
 *             Box(Modifier.fillMaxSize().then(source)) { content(padding) }
 *         }
 *     }
 * }
 * ```
 * Or simply use [GlassScreen], which wires all of that up.
 *
 * The recorded layer is always read by panes that are drawn **after** it (app bars, the floating
 * navigation bar, dialogs), never by panes inside the recording itself, so the graphics layer is
 * never drawn while it is being recorded. As a consequence a glass pane shows the previous
 * frame's backdrop, which is imperceptible and is how every Android backdrop-blur library
 * behaves.
 *
 * On Android 12+ the backdrop copy is blurred with a real `RenderEffect`; below that the blur is
 * silently skipped and panes fall back to a translucent tinted surface with a bright hairline
 * edge, which still reads as glass.
 */
internal const val GlassLogTag = "GlassScroll"

/** Temporary diagnostic switch; set to false (or delete the log lines) once verified. */
internal const val GlassDebugLog = true

object GlassDefaults {

    /** Corner radius of the large panes (cards, sheets). */
    val shape: Shape = RoundedCornerShape(28.dp)

    /** Corner radius of floating chrome (app bars, navigation bar). */
    val chromeShape: Shape = RoundedCornerShape(28.dp)

    /** Corner radius used for list cards. */
    val cardShape: Shape = RoundedCornerShape(24.dp)

    /** Blur applied to the backdrop copy of a pane. */
    val blur: Dp = 32.dp

    /** Stronger blur for chrome, so scrolled content never shows through as noise. */
    val chromeBlur: Dp = 48.dp

    /** Hairline border width. */
    val border: Dp = 1.dp

    /** Slight magnification of the backdrop copy — the "lens" of liquid glass. */
    const val refraction: Float = 1.02f

    /** No magnification; used for wide chrome where a lens would look wrong. */
    const val noRefraction: Float = 1f
}

/**
 * Runtime window metrics, read from the device at composition time, so every piece of glass
 * adapts to the actual screen: phones, foldables, tablets, landscape and split screen.
 */
@Immutable
data class GlassWindowInfo(
    val widthDp: Int,
    val heightDp: Int,
    val isLandscape: Boolean
) {
    /** Phones in portrait. */
    val isCompactWidth: Boolean get() = widthDp < 600

    /** Tablets / unfolded foldables. */
    val isExpandedWidth: Boolean get() = widthDp >= 840

    /** Side padding for page content, growing with the screen. */
    val pagePadding: Dp
        get() = when {
            widthDp >= 1000 -> 32.dp
            widthDp >= 840 -> 28.dp
            widthDp >= 600 -> 24.dp
            else -> 16.dp
        }

    /** Outer margin of floating chrome (app bar, navigation pill). */
    val chromeMargin: Dp
        get() = when {
            widthDp >= 840 -> 28.dp
            widthDp >= 600 -> 20.dp
            else -> 12.dp
        }

    /** Floating chrome never stretches across a wide screen. */
    val chromeMaxWidth: Dp
        get() = if (isExpandedWidth) 620.dp else Dp.Unspecified
}

/** Real screen size, provided by [GlassScreen]; sensible phone defaults outside of one. */
val LocalGlassWindowInfo = staticCompositionLocalOf {
    GlassWindowInfo(widthDp = 360, heightDp = 800, isLandscape = false)
}

@Composable
fun rememberGlassWindowInfo(): GlassWindowInfo {
    val configuration = LocalConfiguration.current
    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.orientation
    ) {
        GlassWindowInfo(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        )
    }
}

/** True when the composed theme is dark; provided by `MagiskTheme`. */
val LocalGlassDarkTheme = staticCompositionLocalOf { true }

/** The recorded backdrop that glass panes sample from. */
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer)

/** `null` when the current screen has no backdrop source; glass then degrades to a flat tint. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * Lets screens publish their backdrop to floating chrome that lives *outside* the screen,
 * e.g. the floating navigation bar in `MainScreen`.
 */
class GlassBackdropHolder {
    var backdrop: GlassBackdrop? by mutableStateOf(null)

    /** How far the visible screen is scrolled (0..1), for chrome outside that screen. */
    var frost: Float by mutableFloatStateOf(0f)

    /** How far the visible screen's chrome is slid away (0..1), for chrome outside that screen. */
    var chromeHidden: Float by mutableFloatStateOf(0f)
}

val LocalGlassBackdropHolder = staticCompositionLocalOf<GlassBackdropHolder?> { null }

/** Provides a [GlassBackdropHolder] to everything composed inside [content]. */
@Composable
fun GlassBackdropHolderProvider(content: @Composable () -> Unit) {
    val holder = remember { GlassBackdropHolder() }
    CompositionLocalProvider(LocalGlassBackdropHolder provides holder, content = content)
}

/** The backdrop published by the currently visible screen, if any. */
@Composable
fun currentBackdrop(): GlassBackdrop? {
    val holder = LocalGlassBackdropHolder.current
    return holder?.backdrop ?: LocalGlassBackdrop.current
}

/**
 * Tracks how far the screen content has been scrolled, so glass can react to sliding:
 *
 * * [fraction] ramps from 0 to 1 as the content scrolls away — panes use it to frost up, which
 *   keeps their text readable once content starts passing underneath them.
 * * [chromeHidden] ramps from 0 to 1 while scrolling down and back to 0 while scrolling up, so
 *   the floating app bar slides away and returns (the "sliding glass" behaviour).
 */
class GlassScrollState internal constructor(
    private val frostDistance: Float,
    private val hideDistance: Float
) {
    /** 0 = content resting at the top, 1 = content scrolled well past the chrome. */
    var fraction by mutableFloatStateOf(0f)
        internal set

    /** 0 = chrome fully visible, 1 = chrome completely slid away. */
    var chromeHidden by mutableFloatStateOf(0f)
        internal set

    internal val connection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            // Compose reports consumed.y as *negative* while the user swipes up (which scrolls the
            // content down), so the amount that means "scrolled down" is -consumed.y. Using the raw
            // value would clamp the state to 0 forever and the chrome would never move.
            val scrolledDown = -consumed.y
            if (scrolledDown != 0f) {
                // Scrolling down tucks the chrome away, scrolling up brings it back twice as fast
                val step = if (scrolledDown > 0f) {
                    scrolledDown / hideDistance
                } else {
                    scrolledDown / (hideDistance * 0.5f)
                }
                chromeHidden = (chromeHidden + step).coerceIn(0f, 1f)
                fraction = (fraction + scrolledDown / frostDistance).coerceIn(0f, 1f)
                if (GlassDebugLog) {
                    Log.d(GlassLogTag, "scroll down=$scrolledDown frost=$fraction hidden=$chromeHidden")
                }
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberGlassScrollState(): GlassScrollState {
    val density = LocalDensity.current
    return remember(density) {
        GlassScrollState(
            frostDistance = with(density) { 140.dp.toPx() },
            hideDistance = with(density) { 110.dp.toPx() }
        )
    }
}

/**
 * Whether the surrounding screen is the one the user is currently looking at. Inside a pager every
 * page is composed at once, so only the active page may publish its scroll state to the shared
 * [GlassBackdropHolder]; otherwise an off-screen page (which never scrolls) would overwrite it and
 * chrome living outside the screen — the floating navigation bar — would look frozen.
 */
val LocalGlassPageActive = staticCompositionLocalOf { true }

/** Scroll state of the surrounding [GlassScreen]; `null` outside of one. */
val LocalGlassScrollState = staticCompositionLocalOf<GlassScrollState?> { null }

private fun Modifier.recordBackdrop(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record(
        this,
        this.layoutDirection,
        IntSize(size.width.toInt(), size.height.toInt())
    ) {
        this@drawWithContent.drawContent()
    }
    drawLayer(layer)
}

/**
 * Records [content] into a graphics layer, publishes it through [LocalGlassBackdrop] (and the
 * enclosing [GlassBackdropHolder] when there is one) and hands the modifier that performs the
 * recording to [content], which must apply it to the layout node that should be recorded.
 */
@Composable
fun GlassBackdropHost(content: @Composable (source: Modifier) -> Unit) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { GlassBackdrop(layer) }
    val holder = LocalGlassBackdropHolder.current
    if (holder != null) {
        SideEffect { holder.backdrop = backdrop }
    }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        content(Modifier.recordBackdrop(layer))
    }
}

/**
 * Simple variant of [GlassBackdropHost] for screens that have no chrome of their own: [content]
 * is recorded as a whole.
 */
@Composable
fun GlassBackdropSource(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    GlassBackdropHost { source ->
        Box(modifier.fillMaxSize().then(source)) {
            content()
        }
    }
}

/** True when a real backdrop blur can be rendered on this device. */
private val canBlurBackdrop: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The colourful animated backdrop the glass refracts. Paint it behind the screen content.
 *
 * The colours follow the active Material theme (including dynamic colour), so every Magisk
 * colour mode keeps working.
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val dark = LocalGlassDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "glassBackdrop")

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val drift2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(33000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift2"
    )

    val base = if (dark) Color(0xFF070A12) else Color(0xFFF3F6FD)
    val baseBottom = if (dark) Color(0xFF0E1122) else Color(0xFFE6EDFA)

    val primary = scheme.primary
    val tertiary = scheme.tertiary
    val secondary = scheme.secondary
    val blobAlpha = if (dark) 0.45f else 0.40f

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val w = size.width
                val h = size.height

                drawRect(Brush.verticalGradient(listOf(base, baseBottom)))

                fun blob(center: Offset, radius: Float, color: Color, alpha: Float) {
                    if (radius <= 0f) return
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                blob(
                    center = Offset(w * (0.14f + 0.10f * drift), h * (0.10f + 0.05f * drift2)),
                    radius = w * 0.90f,
                    color = primary,
                    alpha = blobAlpha
                )
                blob(
                    center = Offset(w * (0.94f - 0.08f * drift2), h * (0.32f + 0.06f * drift)),
                    radius = w * 0.75f,
                    color = tertiary,
                    alpha = blobAlpha * 0.92f
                )
                blob(
                    center = Offset(w * (0.22f + 0.06f * drift2), h * (0.88f - 0.05f * drift)),
                    radius = w * 0.85f,
                    color = secondary,
                    alpha = blobAlpha * 0.85f
                )
                blob(
                    center = Offset(w * 0.88f, h * 0.98f),
                    radius = w * 0.60f,
                    color = primary,
                    alpha = blobAlpha * 0.55f
                )

                // Soft sheen sweeping the upper part of the screen
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (dark) 0.07f else 0.24f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.5f
                    )
                )
            }
    )
}

/**
 * Cheap glass overlay: translucent gradient tint, bright hairline edge and a corner sheen.
 * Contains no backdrop blur, so it is safe and cheap for list rows and cards — and because the
 * [GlassBackground] behind it is smooth, the result still reads as coloured glass.
 */
fun Modifier.glassTint(
    shape: Shape = GlassDefaults.cardShape,
    borderWidth: Dp = GlassDefaults.border,
    dark: Boolean = true,
    tint: Color? = null
): Modifier = this.drawWithContent {
    drawContent()
    drawGlassDecoration(shape, borderWidth, dark, tint)
}

private fun DrawScope.drawGlassDecoration(
    shape: Shape,
    borderWidth: Dp,
    dark: Boolean,
    tint: Color?,
    body: Color? = null
) {
    val overlay = tint ?: Color.White
    val topAlpha = if (tint != null) 0.22f else if (dark) 0.10f else 0.55f
    val bottomAlpha = if (tint != null) 0.14f else if (dark) 0.03f else 0.26f
    val borderColor = when {
        tint != null -> tint.copy(alpha = if (dark) 0.45f else 0.65f)
        dark -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.72f)
    }
    val outline = shape.createOutline(size, layoutDirection, this)

    if (body != null) {
        // Chrome (app bars, the floating navigation bar) floats above scrolling content, so it
        // gets a denser body: the frosted backdrop stays visible, but text scrolling underneath
        // can no longer collide with the labels.
        drawOutline(outline = outline, color = body)
    } else {
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                colors = listOf(
                    overlay.copy(alpha = topAlpha),
                    overlay.copy(alpha = bottomAlpha)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
    }

    if (borderWidth > 0.dp) {
        drawOutline(
            outline = outline,
            color = borderColor,
            style = Stroke(width = borderWidth.toPx())
        )
        // Brighter top edge — the classic glass highlight
        clipRect(bottom = size.height * 0.4f) {
            drawOutline(
                outline = outline,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.32f else 0.70f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.4f
                ),
                style = Stroke(width = borderWidth.toPx() * 1.4f)
            )
        }
    }

    // Specular sheen in the upper-left corner
    clipRect(right = size.width * 0.85f, bottom = size.height * 0.85f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    overlay.copy(alpha = if (dark) 0.14f else 0.35f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.10f, 0f),
                radius = size.minDimension
            ),
            radius = size.minDimension,
            center = Offset(size.width * 0.10f, 0f)
        )
    }
}

/**
 * A frosted glass pane.
 *
 * @param shape outline of the pane.
 * @param blurRadius strength of the backdrop blur (Android 12+). **Keep the default `0.dp` for
 *   anything composed inside a [GlassBackdropHost] recording** — a pane that samples the layer it
 *   is part of would paint a blurred copy of its own previous frame, which reads as smearing.
 *   Chrome drawn *outside* the recording (app bar, floating navigation bar, bottom sheet) should
 *   pass an explicit radius such as [GlassDefaults.chromeBlur] to get a real backdrop blur.
 * @param refraction slight magnification of the backdrop — the lens of liquid glass. Only has an
 *   effect together with a non-zero [blurRadius].
 * @param tint optional tint colour; defaults to theme-aware white glass.
 * @param body optional opaque-ish body colour. Use it for chrome that floats above scrolling
 *   content (app bar, navigation bar): the frosted backdrop still shows, but the pane stays
 *   readable instead of letting the content underneath collide with its text.
 * @param border whether to draw the bright hairline edge.
 * @param contentPadding padding applied around [content].
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassDefaults.shape,
    blurRadius: Dp = 0.dp,
    refraction: Float = GlassDefaults.refraction,
    tint: Color? = null,
    body: Color? = null,
    border: Boolean = true,
    borderWidth: Dp = GlassDefaults.border,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val dark = LocalGlassDarkTheme.current
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    val blurred = backdrop != null && canBlurBackdrop && blurRadius > 0.dp

    Box(
        modifier = modifier
            .onGloballyPositioned { positionInRoot = it.positionInRoot() }
            .clip(shape)
    ) {
        // 1. Blurred copy of everything behind this pane
        if (blurred) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                    .drawWithContent {
                        val layerTopLeft = backdrop!!.layer.topLeft
                        val dx = layerTopLeft.x - positionInRoot.x
                        val dy = layerTopLeft.y - positionInRoot.y
                        withTransform({
                            translate(dx, dy)
                            if (refraction != 1f) {
                                scale(
                                    scaleX = refraction,
                                    scaleY = refraction,
                                    pivot = center
                                )
                            }
                        }) {
                            drawLayer(backdrop.layer)
                        }
                    }
            )
        }

        // 2. Glass tint and edge — above the blur, below the content
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    drawGlassDecoration(
                        shape = RectangleShape,
                        borderWidth = if (border) borderWidth else 0.dp,
                        dark = dark,
                        tint = tint,
                        body = body
                    )
                }
        )

        // 3. Content (this is what sizes the pane)
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Glass version of a Material 3 `Card`: translucent tinted pane with a bright hairline edge and
 * a corner sheen, over the animated [GlassBackground].
 *
 * Cards deliberately do **not** blur the backdrop: they live inside the recorded screen content,
 * so sampling that layer would paint a blurred ghost of the card itself. The frosted blur is
 * reserved for chrome that floats *above* the content (app bar, navigation bar, sheets).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassDefaults.cardShape,
    blurRadius: Dp = 0.dp,
    tint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = shape,
        blurRadius = blurRadius,
        tint = tint,
        contentPadding = contentPadding,
        content = content
    )
}

/** Height of the app bar content, below the status bar. */
private val GlassAppBarHeight = 64.dp

/**
 * Frosted app bar, rendered as a detached floating pane. Apply the outer padding at the call
 * site (`Modifier.padding(horizontal = 12.dp, vertical = 6.dp)`) to make it float.
 *
 * The bar is laid out by hand instead of using Material 3's `TopAppBar`, because the latter paints
 * its own opaque surface colour on top of the glass. The status bar inset is part of the pane, so
 * the glass runs behind the system bar.
 *
 * It reacts to sliding: while the content scrolls it frosts up (denser body, stronger blur) so
 * labels never collide with the text passing underneath, and it slides away while scrolling down
 * and comes back while scrolling up. [scrollBehavior] is still honoured for its height offset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    // safeDrawing (not statusBars) so notches / punch-holes, landscape gesture areas, split
    // screen and multi-window are all respected on every device.
    val window = LocalGlassWindowInfo.current
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = safe.calculateTopPadding()
    val sideInsetStart = safe.calculateStartPadding(LocalLayoutDirection.current)
    val sideInsetEnd = safe.calculateEndPadding(LocalLayoutDirection.current)
    val scroll = LocalGlassScrollState.current
    val density = LocalDensity.current

    val frost = scroll?.fraction ?: 0f
    val hidden = scroll?.chromeHidden ?: 0f
    val behaviourOffset = scrollBehavior?.state?.heightOffset ?: 0f
    val barHeightPx = with(density) { (GlassAppBarHeight + topInset + 16.dp).toPx() }

    val surface = MaterialTheme.colorScheme.surface
    val body = surface.copy(alpha = 0.40f + 0.42f * frost)
    val blurRadius = GlassDefaults.chromeBlur * (0.55f + 0.45f * frost)

    // The status bar area is left clear (transparent) and the glass is a compact rounded pane
    // below it, so the pane is always exactly as big as the bar content needs — never a box
    // stretched over the status bar.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                IntOffset(0, (behaviourOffset - hidden * barHeightPx).roundToInt())
            }
            .padding(top = topInset),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = window.chromeMaxWidth),
            shape = GlassDefaults.chromeShape,
            blurRadius = blurRadius,
            refraction = GlassDefaults.noRefraction,
            body = body
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // heightIn (not height) so a larger system font scale grows the bar instead of
                    // clipping the title.
                    .heightIn(min = GlassAppBarHeight)
                    .padding(
                        start = window.pagePadding + sideInsetStart,
                        end = 4.dp + sideInsetEnd
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationIcon()
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
                        title()
                    }
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            }
        }
    }
}

/**
 * A [Scaffold] whose content is turned into a glass backdrop, on top of the animated
 * [GlassBackground]. Drop-in replacement for `Scaffold`; pass [topBar] and use [GlassTopAppBar]
 * inside it so the bar blurs the content that scrolls underneath.
 *
 * The content lambda receives the usual `PaddingValues` and is expected to apply them itself
 * (exactly like with `Scaffold`). The content also drives [LocalGlassScrollState], which the glass
 * chrome uses to frost up and slide away while the user scrolls.
 */
@Composable
fun GlassScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollState = rememberGlassScrollState()
    val holder = LocalGlassBackdropHolder.current
    // Every pager page is composed at once, so only the page actually on screen may publish its
    // scroll state — otherwise an off-screen page (which never scrolls) overwrites it and the
    // chrome outside the screen looks frozen.
    val pageActive = LocalGlassPageActive.current
    if (GlassDebugLog) {
        val last = remember { floatArrayOf(-1f) }
        val h = scrollState.chromeHidden
        if (kotlin.math.abs(h - last[0]) > 0.02f) {
            last[0] = h
            Log.d(GlassLogTag, "publish pageActive=$pageActive hidden=$h")
        }
    }
    if (holder != null && pageActive) {
        SideEffect {
            holder.frost = scrollState.fraction
            holder.chromeHidden = scrollState.chromeHidden
        }
    }
    CompositionLocalProvider(
        LocalGlassScrollState provides scrollState,
        LocalGlassWindowInfo provides rememberGlassWindowInfo()
    ) {
        Box(modifier.fillMaxSize()) {
            GlassBackground()
            GlassBackdropHost { source ->
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    topBar = topBar,
                    snackbarHost = snackbarHost,
                    floatingActionButton = floatingActionButton
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollState.connection)
                            .then(source)
                    ) {
                        content(padding)
                    }
                }
            }
        }
    }
}
