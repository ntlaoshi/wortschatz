package de.wortschatz.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.data.StudyStats
import de.wortschatz.app.ui.theme.ExtraColors
import java.util.Calendar

@Composable
fun HomeScreen(
    stats: StudyStats,
    levelCounts: Map<String, Int>,
    totalWords: Int,
    activeLevels: Set<String>,
    dailyTarget: Int,
    onStartReview: () -> Unit,
    onStartNew: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenPhotoImport: () -> Unit,
    onOpenTextImport: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Header(stats = stats, onOpenCalendar = onOpenCalendar)
        }
        item {
            TodayCard(
                stats = stats,
                dailyTarget = dailyTarget,
                onStartReview = onStartReview,
                onStartNew = onStartNew,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniStatCard(
                    value = stats.totalLearned.toString(),
                    label = "已学词条",
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    value = stats.reviewsToday.toString(),
                    label = "今日已练",
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    value = stats.starred.toString(),
                    label = "收藏",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            QuickActions(
                onPhotoImport = onOpenPhotoImport,
                onTextImport = onOpenTextImport,
                onBooks = onOpenBooks,
                onRecords = onOpenRecords,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        item {
            SectionTitle(text = "词库进度", modifier = Modifier.padding(top = 8.dp))
        }
        item {
            LevelProgressCard(
                levelCounts = levelCounts,
                totalWords = totalWords,
                activeLevels = activeLevels,
                onClick = onOpenLevels,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * 首页快捷入口。
 *
 * 导入和记录原本都埋在设置页最底部，翻好几屏才能摸到；放到首页，常用操作一眼可见。
 */
@Composable
private fun QuickActions(
    onPhotoImport: () -> Unit,
    onTextImport: () -> Unit,
    onBooks: () -> Unit,
    onRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        Triple("拍照导入", Icons.Rounded.CameraAlt as ImageVector, onPhotoImport),
        Triple("文本导入", Icons.Rounded.Notes as ImageVector, onTextImport),
        Triple("单词书", Icons.Rounded.MenuBook as ImageVector, onBooks),
        Triple("学习记录", Icons.Rounded.History as ImageVector, onRecords)
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { (label, icon, action) ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = action
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(stats: StudyStats, onOpenCalendar: () -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Guten Morgen"
        in 12..17 -> "Guten Tag"
        in 18..22 -> "Guten Abend"
        else -> "Gute Nacht"
    }
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    fontFamily = FontFamily.Default,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "今天也来记几个德语单词吧",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onOpenCalendar
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = "打卡记录",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (stats.streak > 0) "${stats.streak} 天" else "打卡",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayCard(
    stats: StudyStats,
    dailyTarget: Int,
    onStartReview: () -> Unit,
    onStartNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val due = stats.dueToday
    val newPool = stats.newAvailable
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "今日任务",
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TaskFigure(
                        value = due,
                        label = "待复习",
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
                    )
                    TaskFigure(
                        value = newPool,
                        label = "待学习",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "每轮 $dailyTarget 个 · 复习优先安排到期词",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TaskButton(
                        text = "开始复习",
                        enabled = due > 0,
                        filled = true,
                        onClick = onStartReview,
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f)
                    )
                    TaskButton(
                        text = "学新词",
                        enabled = newPool > 0,
                        filled = false,
                        onClick = onStartNew,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskFigure(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            fontFamily = FontFamily.Default,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun TaskButton(
    text: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val fg = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onPrimary
    Surface(
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = when {
            filled && enabled -> onSurface
            filled -> onSurface.copy(alpha = 0.28f)
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        border = if (filled) null else androidx.compose.foundation.BorderStroke(
            1.2.dp,
            onSurface.copy(alpha = if (enabled) 0.55f else 0.22f)
        ),
        onClick = { if (enabled) onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = when {
                        filled && enabled -> fg
                        filled -> fg.copy(alpha = 0.45f)
                        enabled -> onSurface
                        else -> onSurface.copy(alpha = 0.4f)
                    }
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = text,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        filled && enabled -> fg
                        filled -> fg.copy(alpha = 0.45f)
                        enabled -> onSurface
                        else -> onSurface.copy(alpha = 0.4f)
                    }
                )
            }
        }
    }
}

@Composable
private fun MiniStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = FontFamily.Default,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LevelProgressCard(
    levelCounts: Map<String, Int>,
    totalWords: Int,
    activeLevels: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val levels = listOf("A1", "A2", "B1", "B2")
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        ),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "共 $totalWords 个词条",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "调整等级",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            levels.forEach { level ->
                val count = levelCounts[level] ?: 0
                val active = activeLevels.contains(level)
                val ratio = if (totalWords > 0) count.toFloat() / totalWords else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(level)
                    Spacer(Modifier.width(10.dp))
                    LinearProgressIndicator(
                        progress = { if (active) ratio.coerceIn(0.02f, 1f) else 0f },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (active) ExtraColors.levelColor(level)
                        else MaterialTheme.colorScheme.outlineVariant,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (count > 0) "$count" else "—",
                        fontSize = 12.sp,
                        color = if (active) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.outline
                    )
                }
                if (level != "B2") Spacer(Modifier.height(9.dp))
            }
        }
    }
}
