package de.wortschatz.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel

private data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

@Composable
fun WortschatzApp(vm: AppViewModel) {
    if (vm.initializing) {
        Splash()
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showStudy by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }

    if (showCalendar) {
        CheckinCalendarScreen(vm = vm, onBack = { showCalendar = false })
        return
    }

    if (showStudy) {
        StudyScreen(vm = vm, onExit = {
            showStudy = false
            vm.refresh()
        })
        return
    }

    val tabs = listOf(
        TabItem("首页", Icons.Rounded.Home, Icons.Outlined.Home),
        TabItem("查词", Icons.Rounded.Search, Icons.Outlined.Search),
        TabItem("收藏", Icons.Rounded.Star, Icons.Outlined.StarBorder),
        TabItem("设置", Icons.Rounded.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                imageVector = if (tab == index) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text(item.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Crossfade(targetState = tab, label = "tab") { current ->
                when (current) {
                    0 -> HomeScreen(
                        stats = vm.stats,
                        levelCounts = vm.levelCounts,
                        totalWords = vm.totalWords,
                        activeLevels = vm.levels,
                        dailyTarget = vm.sessionSize,
                        onStartReview = {
                            vm.startSession(AppViewModel.SessionMode.REVIEW)
                            showStudy = true
                        },
                        onStartNew = {
                            vm.startSession(AppViewModel.SessionMode.NEW)
                            showStudy = true
                        },
                        onOpenCalendar = { showCalendar = true },
                        onOpenLevels = { tab = 3 }
                    )

                    1 -> SearchScreen(vm = vm)
                    2 -> StarredScreen(vm = vm)
                    else -> SettingsScreen(vm = vm)
                }
            }
        }
    }
}

@Composable
private fun Splash() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = "Wortschatz",
            fontFamily = FontFamily.Default,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "德语单词",
            fontSize = 13.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(30.dp))
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "正在准备离线词库…",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 36.dp)
        )
    }
}
