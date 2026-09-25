package com.zhiyu.magisk.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                beyondViewportPageCount = visibleTabs.size - 1,
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

            AnimatedVisibility(
                visible = isModulesTab && moduleFabAction != null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 104.dp),
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
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * The floating navigation bar, turned into a frosted glass pill.
 *
 * It is composed in the [Scaffold]'s `bottomBar` slot, i.e. *after* the pager content, so it can
 * sample the backdrop published by the currently visible tab — that is what the
 * [GlassBackdropHolderProvider] around the screen is for. The pill tucks itself away while swiping
 * between pages and frosts up as the visible tab is scrolled, so its labels never fight with the
 * content.
 */
@Composable
private fun FloatingNavigationBar(
    pagerState: PagerState,
    visibleTabs: List<Tab>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val shape = GlassDefaults.chromeShape
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The floating bar lives outside the screen it blurs, so it borrows the backdrop
    // published by the currently visible tab.
    val backdrop = currentBackdrop()
    val holder = LocalGlassBackdropHolder.current

    val swiping = pagerState.isScrollInProgress
    val glassWindow = LocalGlassWindowInfo.current
    val navScale by animateFloatAsState(if (swiping) 0.96f else 1f, label = "navScale")
    val frost = holder?.frost ?: 0f
    val slidAway = holder?.chromeHidden ?: 0f
    val body = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f + 0.30f * frost)
    val slideDistancePx = with(LocalDensity.current) { (68.dp + navBarInset + 24.dp).toPx() }

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        GlassSurface(
            modifier = modifier
                .padding(
                    bottom = navBarInset + 12.dp,
                    start = glassWindow.chromeMargin + 8.dp,
                    end = glassWindow.chromeMargin + 8.dp
                )
                // Sliding glass: like the app bar, the pill tucks itself away while the content is
                // scrolled down and slides back in as soon as the user scrolls up again.
                .offset { IntOffset(0, (slidAway * slideDistancePx).roundToInt()) }
                .graphicsLayer {
                    scaleX = navScale
                    scaleY = navScale
                }
                .shadow(elevation = 10.dp, shape = shape, clip = false)
                .fillMaxWidth()
                .widthIn(max = glassWindow.chromeMaxWidth)
                .heightIn(min = 68.dp),
            shape = shape,
            blurRadius = GlassDefaults.chromeBlur * (0.6f + 0.4f * frost),
            refraction = GlassDefaults.noRefraction,
            body = body
        ) {
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
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        modifier = Modifier.weight(1f)
                    )
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
    modifier: Modifier = Modifier
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
            .padding(horizontal = 4.dp, vertical = 6.dp),
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = contentColor,
            )
        }
    }
}
