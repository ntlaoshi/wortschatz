package de.wortschatz.app.ui

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.Calendar0
import java.util.Calendar
import java.util.Locale

/**
 * 打卡日历：按月展示哪天练过、哪天断签。
 * 打卡判定依据 daily_log 表里当日 reviews > 0。
 */
@Composable
fun CheckinCalendarScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }

    val todayKey = remember { Calendar0.todayString() }
    val days = vm.checkinDays

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---- 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "打卡记录",
                    fontFamily = FontFamily.Default,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(6.dp))

            // ---- 月份切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${year} 年 ${month + 1} 月",
                        fontFamily = FontFamily.Default,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "本月打卡 ${monthCheckins(year, month, days)} 天",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    if (month == 0) {
                        month = 11
                        year -= 1
                    } else month -= 1
                }) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = "上个月",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    if (month == 11) {
                        month = 0
                        year += 1
                    } else month += 1
                }) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "下个月",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- 星期表头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ---- 日期网格
            val first = remember(year, month) {
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
            }
            val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            // Calendar 里周日是 1，这里换算成周一为第一列
            val leading = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val cells = List(leading) { 0 } + (1..daysInMonth).toList()

            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                checked = day > 0 && days.contains(keyOf(year, month, day)),
                                isToday = day > 0 && keyOf(year, month, day) == todayKey,
                                isFuture = day > 0 && keyOf(year, month, day) > todayKey,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 补满一行，保持列宽一致
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ---- 汇总
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SummaryItem(
                        value = "${vm.stats.streak}",
                        label = "连续天数",
                        modifier = Modifier.weight(1f),
                        highlight = true
                    )
                    SummaryItem(
                        value = "${days.size}",
                        label = "累计打卡",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryItem(
                        value = "${vm.stats.totalLearned}",
                        label = "已学词条",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun keyOf(year: Int, month: Int, day: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)

private fun monthCheckins(year: Int, month: Int, days: Set<String>): Int {
    val prefix = String.format(Locale.US, "%04d-%02d-", year, month + 1)
    return days.count { it.startsWith(prefix) }
}

@Composable
private fun DayCell(
    day: Int,
    checked: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
    ) {
        if (day == 0) return@Box
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(11.dp),
            color = when {
                checked -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surface
            },
            border = androidx.compose.foundation.BorderStroke(
                width = if (isToday) 1.6.dp else 1.dp,
                color = when {
                    isToday -> MaterialTheme.colorScheme.secondary
                    checked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = day.toString(),
                    fontSize = 13.sp,
                    fontWeight = if (checked || isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        checked -> MaterialTheme.colorScheme.onPrimary
                        isFuture -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (highlight) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = value,
                fontFamily = FontFamily.Default,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
