package com.zhiyu.magisk.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiyu.magisk.R
import com.zhiyu.magisk.arch.VMFactory
import com.zhiyu.magisk.core.Info
import com.zhiyu.magisk.core.model.module.LocalModule
import com.zhiyu.magisk.ui.glass.GlassBackdropHolderProvider
import com.zhiyu.magisk.ui.glass.GlassDefaults
import com.zhiyu.magisk.ui.glass.GlassSurface
import com.zhiyu.magisk.ui.glass.LocalGlassBackdrop
import com.zhiyu.magisk.ui.glass.LocalGlassBackdropHolder
import com.zhiyu.magisk.ui.glass.LocalGlassPageActive
import com.zhiyu.magisk.ui.glass.LocalGlassWindowInfo
import com.zhiyu.magisk.ui.glass.currentBackdrop
import com.zhiyu.magisk.ui.glass.quantiseFrost
import com.zhiyu.magisk.ui.glass.rememberGlassWindowInfo
import com.zhiyu.magisk.ui.home.HomeScreen
import com.zhiyu.magisk.ui.home.HomeViewModel
import com.zhiyu.magisk.ui.install.InstallViewModel
import com.zhiyu.magisk.ui.log.LogScreen
import com.zhiyu.magisk.ui.log.LogViewModel
import com.zhiyu.magisk.ui.module.ModuleScreen
import com.zhiyu.magisk.ui.module.ModuleViewModel
import com.zhiyu.magisk.ui.navigation.CollectNavEvents
import com.zhiyu.magisk.ui.navigation.LocalNavigator
import com.zhiyu.magisk.ui.settings.SettingsScreen
import com.zhiyu.magisk.ui.settings.SettingsViewModel
import com.zhiyu.magisk.ui.superuser.SuperuserScreen
import com.zhiyu.magisk.ui.superuser.SuperuserViewModel
import kotlinx.coroutines.launch
import com.zhiyu.magisk.core.R as CoreR
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.offset

enum class Tab(val titleRes: Int, val iconRes: Int) {
    MODULES(CoreR.string.modules, R.drawable.ic_module),
    SUPERUSER(CoreR.string.superuser, CoreR.drawable.ic_superuser),
    HOME(CoreR.string.section_home, R.drawable.ic_home),
    LOG(CoreR.string.logs, R.drawable.ic_bug),
    SETTINGS(CoreR.string.settings, R.drawable.ic_settings);
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    initialTab: Int = Tab.HOME.ordinal,
    superuserViewModel: SuperuserViewModel? = null,
    onAuthenticate: ((onSuccess: () -> Unit) -> Unit)? = null,
) {
    val navigator = LocalNavigator.current
    val visibleTabs = remember {
        Tab.entries.filter { tab ->
            when (tab) {
                Tab.SUPERUSER -> Info.showSuperUser
                Tab.MODULES -> Info.env.isActive && LocalModule.loaded()
                else -> true
            }
        }
    }
    val initialPage = visibleTabs.indexOf(Tab.entries[initialTab]).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { visibleTabs.size })
    var moduleFabAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isModulesTab = visibleTabs.getOrNull(pagerState.currentPage) == Tab.MODULES

    // Publishes the backdrop and scroll level of the currently visible tab, so the floating
    // navigation bar below — which lives outside every screen — can frost up with it.
    GlassBackdropHolderProvider {
        // Window metrics for the chrome that floats *outside* the pages (the navigation bar and
        // the FAB). Each page's GlassScreen provides its own, but that value dies with the page;
        // the bar needs the same orientation info at MainScreen level to decide its own layout.
        CompositionLocalProvider(LocalGlassWindowInfo provides rememberGlassWindowInfo()) {
        // The pager is full-bleed and the pill is an overlay, so the tab content really does
        // scroll *under* the frosted bar and gets blurred behind it.
        Box(modifier = modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { _ ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Keep only the immediate neighbours composed. Pre-composing *every* page
                // (`pageCount - 1`) costs one full-screen glass backdrop, one animated gradient
                // background and one screen loading its data per tab, all at the same time, which
                // is what made dragging the pager stutter.
                beyondViewportPageCount = 1,
                userScrollEnabled = true,
            ) { page ->
                val isCurrentPage = pagerState.currentPage == page
                // Only the visible page publishes its scroll state, so the floating pill outside
                // the pager follows the page the user is actually looking at.
                CompositionLocalProvider(LocalGlassPageActive provides isCurrentPage) {
                when (visibleTabs[page]) {
                    Tab.HOME -> {
                        val vm: HomeViewModel = viewModel(factory = VMFactory)
                        val installVm: InstallViewModel = viewModel(factory = VMFactory)
                        LaunchedEffect(isCurrentPage) {
                            if (isCurrentPage) vm.startLoading()
                        }
                        CollectNavEvents(vm, navigator)
                        CollectNavEvents(installVm, navigator)
                        HomeScreen(vm, installVm)
                    }
                    Tab.SUPERUSER -> {
                        val activity = LocalActivity.current as? ComponentActivity
                        val vm: SuperuserViewModel = superuserViewModel
                            ?: if (activity != null) {
                                viewModel(viewModelStoreOwner = activity, factory = VMFactory)
                            } else {
                                viewModel(factory = VMFactory)
                            }
                        LaunchedEffect(onAuthenticate) {
                            if (onAuthenticate != null) {
                                vm.authenticate = onAuthenticate
                            }
                        }
                        LaunchedEffect(isCurrentPage) {
                            if (isCurrentPage) vm.startLoading()
                        }
                        SuperuserScreen(vm)
                    }
                    Tab.LOG -> {
                        val vm: LogViewModel = viewModel(factory = VMFactory)
                        LaunchedEffect(isCurrentPage) {
                            if (isCurrentPage) vm.startLoading()
                        }
                        LogScreen(vm)
                    }
                    Tab.MODULES -> {
                        val vm: ModuleViewModel = viewModel(factory = VMFactory)
                        LaunchedEffect(isCurrentPage) {
                            if (isCurrentPage) vm.startLoading()
                        }
                        CollectNavEvents(vm, navigator)
                        ModuleScreen(
                            viewModel = vm,
                            onRegisterFab = { moduleFabAction = it },
                        )
                    }
                    Tab.SETTINGS -> {
                        val vm: SettingsViewModel = viewModel(factory = VMFactory)
                        LaunchedEffect(onAuthenticate) {
                            if (onAuthenticate != null) {
                                vm.authenticate = onAuthenticate
                            }
                        }
                        CollectNavEvents(vm, navigator)
                        SettingsScreen(vm)
                    }
                }
                }
            }
            }

            // In landscape the navigation bar is a vertical rail on the start edge, so the FAB
            // clears it with a smaller bottom inset instead of floating over the content.
            val isLandscape = LocalGlassWindowInfo.current.isLandscape

            AnimatedVisibility(
                visible = isModulesTab && moduleFabAction != null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 20.dp,
                        bottom = if (isLandscape) 24.dp else 104.dp
                    ),
            ) {
                FloatingActionButton(
                    onClick = { moduleFabAction?.invoke() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(CoreR.string.module_action_install_external),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            FloatingNavigationBar(
                pagerState = pagerState,
                visibleTabs = visibleTabs,
                modifier = Modifier.align(
                    if (LocalGlassWindowInfo.current.isLandscape) {
                        Alignment.CenterStart
                    } else {
                        Alignment.BottomCenter
                    }
                )
            )
        }
        }
    }
}

/**
 * How the pager travels when a tab is tapped on the floating bar.
 *
 * Deliberately brisk and *constant* rather than distance-scaled: the pager composes every page it
 * flies past, so stretching a long jump over a proportionally long animation just multiplies the
 * per-frame work for a distance the user did not ask to sit through.
 */
private val PagerSlide = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

/** A flick faster than this (px/s) wins over wherever the pill happened to be dragged. */
private const val PagerFlingVelocity = 800f

/**
 * Lets a drag across the floating bar page through the tabs, the way flicking the pager itself
 * does. In portrait the bar is horizontal, so a horizontal drag pages; in landscape it is a
 * vertical rail, so a vertical drag pages (up = next, down = previous).
 *
 * Handled on the **initial** dispatch pass on purpose. Every icon in the bar is `clickable`, and a
 * clickable child consumes the pointer down before an ancestor ever gets a look at it, so a plain
 * `draggable` on the container never sees the gesture. Watching the initial pass means this node
 * inspects every event on the way *down* the tree, before the icons can take it.
 *
 * Until the finger travels past touch slop nothing is consumed, so a plain tap still reaches the
 * icons and selects a tab. Past the slop the movement is consumed — which cancels the pending tap —
 * and pushed into the pager with `dispatchRawDelta`, avoiding one coroutine per frame so the pages
 * genuinely track the finger. On release the flick velocity picks the page, falling back to
 * whichever page the drag left the pager closest to.
 */
private fun Modifier.pagerDrag(
    pagerState: PagerState,
    pageSize: Float,
    vertical: Boolean = false
): Modifier = pointerInput(pagerState, pageSize, vertical) {
    val slop = viewConfiguration.touchSlop
    while (true) {
        // The gesture loop has to live inside awaitPointerEventScope, but settling the pager is a
        // regular suspend call — so the scope only *decides* the landing page and reports it back.
        val landing = awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // The page the gesture started from. Deriving the destination from this instead of the
            // live currentPage keeps the outcome predictable: raw deltas already move the pager
            // mid-drag, so currentPage can cross into the next page before the finger lifts.
            val startPage = pagerState.currentPage
            val tracker = VelocityTracker()
            var dragging = false
            var travelled = 0f

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                tracker.addPointerInputChange(change)

                val delta = if (vertical) change.positionChange().y else change.positionChange().x
                // Total finger travel decides the landing page, so it has to keep accumulating for
                // the whole gesture — not merely until the drag is first recognised.
                travelled += delta
                // Wait for real movement before stealing the gesture from the icons.
                if (!dragging && abs(travelled) > slop) dragging = true
                if (dragging) {
                    change.consume()
                    // A HorizontalPager scrolls along x regardless of which way the bar faces, so a
                    // vertical rail maps its y deltas onto that x axis.
                    pagerState.dispatchRawDelta(-delta)
                }
            }

            if (!dragging) {
                null
            } else {
                val draggedPages = -travelled / pageSize
                val velocity = if (vertical) {
                    tracker.calculateVelocity().y
                } else {
                    tracker.calculateVelocity().x
                }
                val flicked = when {
                    velocity > PagerFlingVelocity -> -1
                    velocity < -PagerFlingVelocity -> 1
                    else -> 0
                }
                // A flick decides outright; otherwise land on the page the finger dragged towards.
                val step = if (flicked != 0) flicked else draggedPages.roundToInt()
                val settled = (startPage + step).coerceIn(0, pagerState.pageCount - 1)
                settled
            }
        }
        landing?.let { pagerState.animateScrollToPage(it, animationSpec = PagerSlide) }
    }
}

/**
 * The floating navigation bar, turned into a frosted glass pill.
 *
 * It is composed *after* the pager content, so it can sample the backdrop published by the
 * currently visible tab — that is what the [GlassBackdropHolderProvider] around the screen is for.
 * The pill tucks itself away while swiping between pages and frosts up as the visible tab is
 * scrolled, so its labels never fight with the content.
 *
 * Orientation: in portrait it is a bottom-centered pill (horizontal strip, horizontal swipe). In
 * landscape it becomes a vertical rail docked on the start edge, running between the top app bar
 * and the bottom system inset, so a wide screen does not end up with a squat bar across the middle
 * that collides with the content — and the swipe runs vertically (up/down, matching the rail).
 */
@Composable
private fun FloatingNavigationBar(
    pagerState: PagerState,
    visibleTabs: List<Tab>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val shape = GlassDefaults.chromeShape
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val pageWidth = with(density) { windowInfo.containerSize.width.toFloat() }
    val pageHeight = with(density) { windowInfo.containerSize.height.toFloat() }
    // The floating bar lives outside the screen it blurs, so it borrows the backdrop
    // published by the currently visible tab.
    val backdrop = currentBackdrop()
    val holder = LocalGlassBackdropHolder.current

    val glassWindow = LocalGlassWindowInfo.current
    val isLandscape = glassWindow.isLandscape
    // When the rail is vertical, a whole page is one screen height; when the pill is
    // horizontal, it is one screen width.
    val pageSize = if (isLandscape) pageHeight else pageWidth
    // Quantised: the pill would otherwise rebuild its blur and re-lay-out its icons on every
    // scroll frame of the page underneath.
    val frost = quantiseFrost(holder?.frost ?: 0f)
    val body = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f + 0.30f * frost)

    // safeDrawing (not navigationBars) so landscape gesture areas and split screen are respected.
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        GlassSurface(
            modifier = modifier
                .then(
                    if (isLandscape) {
                        // Vertical rail on the start edge. It clears the top app bar and the
                        // bottom system inset, so it never collides with either.
                        Modifier
                            .padding(
                                start = safe.calculateStartPadding(layoutDirection) + glassWindow.chromeMargin + 8.dp,
                                top = safe.calculateTopPadding() + 76.dp,
                                bottom = safe.calculateBottomPadding() + 16.dp,
                                end = 8.dp
                            )
                            .fillMaxHeight()
                            .width(76.dp)
                    } else {
                        // Bottom-centered pill, floating above the navigation bar inset.
                        Modifier
                            .padding(
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
                                start = glassWindow.chromeMargin + 8.dp,
                                end = glassWindow.chromeMargin + 8.dp
                            )
                            .fillMaxWidth()
                            .widthIn(max = glassWindow.chromeMaxWidth)
                            .heightIn(min = 68.dp)
                    }
                )
                .shadow(elevation = 10.dp, shape = shape, clip = false)
                .pagerDrag(pagerState, pageSize, vertical = isLandscape),
            shape = shape,
            blurRadius = GlassDefaults.chromeBlur * (0.6f + 0.4f * frost),
            body = body
        ) {
            if (isLandscape) {
                // Tail the rail to the room it actually has. On a phone in landscape there is
                // ~220dp of height for five tabs, which cannot fit icon+label rows; on a tablet it
                // can. Picking per-entry instead of forcing one layout keeps the targets
                // comfortable on both (apple-design's flexibility rule).
                val showLabels = glassWindow.heightDp >= 480
                Column(
                    modifier = Modifier
                        // fillMaxHeight + widthIn, never fillMaxSize: the rail must stay a rail.
                        .fillMaxHeight()
                        .widthIn(min = 76.dp)
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    visibleTabs.forEachIndexed { index, tab ->
                        FloatingNavItem(
                            icon = ImageVector.vectorResource(tab.iconRes),
                            label = stringResource(tab.titleRes),
                            selected = pagerState.currentPage == index,
                            enabled = true,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index, animationSpec = PagerSlide)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            showLabel = showLabels
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        // fillMaxWidth + heightIn (never fillMaxSize): the pill must stay a pill.
                        // fillMaxSize() inside a heightIn(min) pane expands to the whole screen.
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleTabs.forEachIndexed { index, tab ->
                        FloatingNavItem(
                            icon = ImageVector.vectorResource(tab.iconRes),
                            label = stringResource(tab.titleRes),
                            selected = pagerState.currentPage == index,
                            enabled = true,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index, animationSpec = PagerSlide)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Landscape phones are short and tall, so a vertical rail with five icon+label entries would
    // crush the labels together; the compact rail shows icons only and keeps full-size targets.
    showLabel: Boolean = true
) {
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "navItemColor"
    )

    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(220),
        label = "navItemPill"
    )

    Box(
        modifier = modifier
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            )
            // Compact rails get a tighter vertical footprint so the touch targets still fit.
            .padding(horizontal = 4.dp, vertical = if (showLabel) 6.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft glassy highlight behind the active tab
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(pillColor)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            if (showLabel) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = contentColor,
                )
            }
        }
    }
}
