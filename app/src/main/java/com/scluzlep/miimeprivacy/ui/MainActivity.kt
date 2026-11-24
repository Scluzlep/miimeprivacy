package com.scluzlep.miimeprivacy.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.scluzlep.miimeprivacy.bridge.XposedServiceManager
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.ui.icons.MiImeIcons
import com.scluzlep.miimeprivacy.ui.screens.EmojiTab
import com.scluzlep.miimeprivacy.ui.screens.OverlayTab
import com.scluzlep.miimeprivacy.ui.screens.OverviewTab
import com.scluzlep.miimeprivacy.ui.screens.RulesTab
import com.scluzlep.miimeprivacy.ui.screens.SettingsTab
import com.scluzlep.miimeprivacy.ui.theme.MiImeTheme
import com.scluzlep.miimeprivacy.ui.theme.isDarkTheme
import com.scluzlep.miimeprivacy.ui.viewmodel.MainTab
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiController
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiStateBuilder
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleTargetFilter
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    private lateinit var controller: PrivacyUiController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = PrivacyUiController(applicationContext)
        handleIncomingIntent(intent)

        setContent {
            val config by XposedServiceManager.config.collectAsState()
            val serviceState by XposedServiceManager.serviceState.collectAsState()
            val diagnostics by XposedServiceManager.diagnostics.collectAsState()
            val installedImes by controller.installedImes.collectAsState()
            val selectedTab by controller.selectedTab.collectAsState()
            val selectedRuleFilter by controller.selectedRuleFilter.collectAsState()
            val isScopeRequesting by controller.isScopeRequesting.collectAsState()
            val isHotReloading by controller.isHotReloading.collectAsState()
            val actionFeedback by controller.actionFeedback.collectAsState()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = isDarkTheme(config.themeMode, systemDark)

            LaunchedEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            val hostLifecycle = this@MainActivity.lifecycle
            DisposableEffect(hostLifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        controller.refreshAll()
                    }
                }
                hostLifecycle.addObserver(observer)
                onDispose {
                    hostLifecycle.removeObserver(observer)
                }
            }

            val uiState = remember(
                config,
                serviceState,
                diagnostics,
                installedImes,
                isScopeRequesting,
                isHotReloading,
                actionFeedback
            ) {
                PrivacyUiStateBuilder.build(
                    config = config,
                    serviceState = serviceState,
                    diagnosticsMap = diagnostics,
                    installedMap = installedImes,
                    isScopeRequesting = isScopeRequesting,
                    isHotReloading = isHotReloading,
                    actionFeedback = actionFeedback
                )
            }

            MiImeTheme(
                themeMode = config.themeMode,
                monetEnabled = config.monetEnabled,
                paletteStyle = config.paletteStyle
            ) {
                val scrollBehavior = MiuixScrollBehavior()
                val colors = MiuixTheme.colorScheme

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        SmallTopAppBar(
                            title = "米输净盾",
                            subtitle = when (selectedTab) {
                                MainTab.OVERVIEW -> "运行状态"
                                MainTab.RULES -> "拦截规则"
                                MainTab.OVERLAY -> "悬浮胶囊"
                                MainTab.EMOJI -> "Emoji 补全"
                                MainTab.SETTINGS -> "外观与关于"
                            },
                            scrollBehavior = scrollBehavior,
                            actions = {
                                IconButton(
                                    onClick = { controller.refreshAll() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "刷新",
                                        modifier = Modifier.size(20.dp),
                                        tint = colors.onSurface
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            mode = NavigationBarDisplayMode.IconAndText
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == MainTab.OVERVIEW,
                                onClick = { controller.selectTab(MainTab.OVERVIEW) },
                                icon = Icons.Rounded.Home,
                                label = MainTab.OVERVIEW.title
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.RULES,
                                onClick = { controller.selectTab(MainTab.RULES) },
                                icon = MiImeIcons.ShieldCheck,
                                label = MainTab.RULES.title
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.OVERLAY,
                                onClick = { controller.selectTab(MainTab.OVERLAY) },
                                icon = MiImeIcons.OverlayPill,
                                label = MainTab.OVERLAY.title
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.EMOJI,
                                onClick = { controller.selectTab(MainTab.EMOJI) },
                                icon = MiImeIcons.EmojiFace,
                                label = MainTab.EMOJI.title
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.SETTINGS,
                                onClick = { controller.selectTab(MainTab.SETTINGS) },
                                icon = Icons.Rounded.Settings,
                                label = MainTab.SETTINGS.title
                            )
                        }
                    }
                ) { padding ->
                    when (selectedTab) {
                        MainTab.OVERVIEW -> OverviewTab(
                            state = uiState,
                            scrollBehavior = scrollBehavior,
                            contentPadding = padding,
                            onRequestScope = controller::requestScopeForAll,
                            onHotReload = controller::hotReloadAll,
                            onToggleAllowCloud = controller::setAllowCloudInput,
                            onToggleImeManaged = controller::setImeManaged,
                            onChangeImeCloudOverride = controller::setImeCloudOverride,
                            onChangeImeResolveMode = controller::setImeResolveMode,
                            onClearImeSymbolCache = controller::clearImeSymbolCache,
                            onOpenImeDiagnostics = controller::openDiagnosticsFor
                        )

                        MainTab.RULES -> RulesTab(
                            state = uiState,
                            selectedFilter = selectedRuleFilter,
                            scrollBehavior = scrollBehavior,
                            contentPadding = padding,
                            controller = controller
                        )

                        MainTab.OVERLAY -> OverlayTab(
                            state = uiState,
                            scrollBehavior = scrollBehavior,
                            contentPadding = padding,
                            controller = controller
                        )

                        MainTab.EMOJI -> EmojiTab(
                            scrollBehavior = scrollBehavior,
                            contentPadding = padding
                        )

                        MainTab.SETTINGS -> SettingsTab(
                            state = uiState,
                            scrollBehavior = scrollBehavior,
                            contentPadding = padding,
                            controller = controller
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == ConfigContract.ACTION_OPEN_SETTINGS) {
            controller.refreshAll()
            val targetPkg = intent.getStringExtra(ConfigContract.EXTRA_SOURCE_PACKAGE)
            val target = com.scluzlep.miimeprivacy.model.ImeTarget.fromPackage(targetPkg)
            val requestedTab = intent.getStringExtra(EXTRA_TAB)
            when (requestedTab) {
                MainTab.RULES.name -> {
                    if (target != null) {
                        controller.openDiagnosticsFor(target)
                    } else {
                        controller.selectTab(MainTab.RULES)
                    }
                }
                MainTab.OVERLAY.name -> controller.selectTab(MainTab.OVERLAY)
                MainTab.EMOJI.name -> controller.selectTab(MainTab.EMOJI)
                MainTab.SETTINGS.name -> controller.selectTab(MainTab.SETTINGS)
                else -> {
                    controller.selectRuleFilter(RuleTargetFilter.ALL)
                    controller.selectTab(MainTab.OVERVIEW)
                }
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "extra_tab"
    }
}
