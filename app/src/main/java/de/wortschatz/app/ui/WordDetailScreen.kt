package de.wortschatz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.Progress
import de.wortschatz.app.data.Word
import java.util.concurrent.TimeUnit

@Composable
fun WordDetailScreen(
    word: Word,
    starred: Boolean,
    onBack: () -> Unit,
    onSpeak: (String) -> Unit,
    onStar: () -> Unit,
    vm: AppViewModel
) {
    var progress by remember(word.id) { mutableStateOf<Progress?>(null) }
    LaunchedEffect(word.id) {
        vm.progressFor(word.id) { progress = it }
    }
    var starState by remember(word.id) { mutableStateOf(starred) }
    LaunchedEffect(starred) { starState = starred }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.weight(1f))
                StarButton(starred = starState, onClick = {
                    onStar()
                    starState = !starState
                })
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelBadge(word.level)
                        Spacer(Modifier.width(6.dp))
                        PosChip(word.posLabel)
                        if (word.source != Word.SRC_BUILTIN) {
                            Spacer(Modifier.width(6.dp))
                            PosChip(
                                label = if (word.source == Word.SRC_ONLINE) "在线收录" else "自定义",
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = word.word,
                            fontFamily = FontFamily.Default,
                            fontSize = 34.sp,
                            lineHeight = 42.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(12.dp))
                        SpeakButton(
                            onClick = { onSpeak(word.word) },
                            size = 44.dp
                        )
                    }

                    word.grammarNote?.let { note ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = note,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = word.zh,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    word.en?.let {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!word.example.isNullOrBlank()) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "例句",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        ExampleBlock(
                            german = word.example,
                            chinese = word.exampleZh,
                            onSpeak = { onSpeak(word.example!!) }
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "学习记录",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressSummary(progress)
                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { onSpeak(word.word) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "朗读单词",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (!word.example.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { onSpeak(word.example!!) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "朗读例句",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSummary(progress: Progress?) {
    if (progress == null || progress.state == 0) {
        Text(
            text = "还没有学过这个词，去背单词里可以加入学习队列。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val now = System.currentTimeMillis()
    val diff = progress.due - now
    val dueText = when {
        diff <= 0 -> "现在可以复习"
        diff < TimeUnit.HOURS.toMillis(1) -> "${(diff / 60000).coerceAtLeast(1)} 分钟后复习"
        diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)} 小时后复习"
        else -> "${diff / TimeUnit.DAYS.toMillis(1)} 天后复习"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        InfoPair("复习次数", "${progress.reps}")
        InfoPair("当前间隔", if (progress.interval == 0) "学习中" else "${progress.interval} 天")
        InfoPair("下次", dueText)
    }
}

@Composable
private fun InfoPair(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
