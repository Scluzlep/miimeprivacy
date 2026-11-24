package com.scluzlep.miimeprivacy.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scluzlep.miimeprivacy.bridge.XposedServiceManager
import com.scluzlep.miimeprivacy.config.ThemeMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Phase A Miuix baseline Activity placeholder.
 * Replaced by the full multi-tab Miuix control center in Phase B (`feat/miuix-app-ui`).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        XposedServiceManager.init(applicationContext)
        enableEdgeToEdge()

        setContent {
            val config by XposedServiceManager.config.collectAsState()
            val serviceState by XposedServiceManager.serviceState.collectAsState()
            val schemeMode = when (config.themeMode) {
                ThemeMode.SYSTEM -> if (config.monetEnabled) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
                ThemeMode.LIGHT -> if (config.monetEnabled) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
                ThemeMode.DARK -> if (config.monetEnabled) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
            }
            val controller = remember(schemeMode) {
                ThemeController(colorSchemeMode = schemeMode)
            }

            MiuixTheme(controller = controller) {
                Scaffold(
                    topBar = {
                        SmallTopAppBar(title = "米输净盾")
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp)
                        ) {
                            Text(
                                text = if (serviceState.isServiceBound) {
                                    "LSPosed 服务已连接：${serviceState.frameworkName} ${serviceState.frameworkVersion}"
                                } else {
                                    "LSPosed 服务未绑定（等待框架激活）"
                                }
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "允许输入法云输入")
                                Switch(
                                    checked = config.allowCloudInput,
                                    onCheckedChange = { enabled ->
                                        XposedServiceManager.updateConfig {
                                            it.copy(allowCloudInput = enabled)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
