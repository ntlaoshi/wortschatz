package de.wortschatz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.Rating
import de.wortschatz.app.data.Word
import de.wortschatz.app.data.WordWithProgress
import de.wortschatz.app.ui.theme.ExtraColors

@Composable
fun StudyScreen(vm: AppViewModel, onExit: () -> Unit) {
    val current = vm.current

    // 进入每张卡片时自动朗读
    LaunchedEffect(current?.word?.id) {
        val w = current?.word
        if (w != null && vm.autoSpeak) vm.speakWord(w)
    }

    BackHandler { onExit() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // ---- 顶部进度
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "退出学习",
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (vm.sessionTotal > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (vm.sessionDone.toFloat() / vm.sessionTotal).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${vm.sessionDone}/${vm.sessionTotal}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            if (current == null) {
                SessionFinished(
                    done = vm.sessionDone,
                    onExit = onExit,
                    modifier = Modifier.weight(1f)
                )
            } else {
                StudyCard(
                    item = current,
                    revealed = vm.revealed,
                    speaker = { vm.speak(it) },
                    onReveal = { vm.reveal() },
                    modifier = Modifier.weight(1f)
                )
                RatingBar(
                    enabled = vm.revealed,
                    onRate = { vm.rate(it) }
                )
            }
        }
    }
}

@Composable
private fun StudyCard(
    item: WordWithProgress,
    revealed: Boolean,
    speaker: (String) -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val word = item.word
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = !revealed) { onReveal() },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            AnimatedContent(
                targetState = revealed,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 1.02f))
                },
                label = "card",
                modifier = Modifier.fillMaxSize()
            ) { showBack ->
                if (showBack) BackFace(word = word, speaker = speaker)
                else FrontFace(word = word, speaker = speaker, isNew = item.progress.state == 0)
            }
        }
    }
}

@Composable
private fun FrontFace(word: Word, speaker: (String) -> Unit, isNew: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(word.level)
            Spacer(Modifier.width(6.dp))
            PosChip(word.posLabel)
            if (isNew) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "新词",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = word.word,
            fontFamily = FontFamily.Default,
            fontSize = 38.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(22.dp))
        SpeakButton(onClick = { speaker(word.word) }, size = 54.dp)

        Spacer(Modifier.weight(1f))

        Text(
            text = "轻触卡片查看释义",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BackFace(word: Word, speaker: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.word,
                fontFamily = FontFamily.Default,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            SpeakButton(onClick = { speaker(word.word) }, size = 34.dp)
        }

        word.grammarNote?.let {
            Spacer(Modifier.height(5.dp))
            Text(
                text = it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = word.zh,
            fontSize = 21.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        word.en?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!word.example.isNullOrBlank()) {
            Spacer(Modifier.height(20.dp))
            ExampleBlock(
                german = word.example,
                chinese = word.exampleZh,
                onSpeak = { speaker(word.example!!) }
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RatingBar(enabled: Boolean, onRate: (Rating) -> Unit) {
    val items = listOf(
        Triple(Rating.AGAIN, MaterialTheme.colorScheme.error, "忘了"),
        Triple(Rating.HARD, Color(0xFF9A6B00), "模糊"),
        Triple(Rating.GOOD, Color(0xFF2E7D5B), "认识"),
        Triple(Rating.EASY, MaterialTheme.colorScheme.primary, "太简单")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items.forEach { (rating, color, label) ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (enabled) color.copy(alpha = 0.11f) else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (enabled) color.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                onClick = { if (enabled) onRate(rating) },
                enabled = enabled
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) color
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionFinished(done: Int, onExit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = ExtraColors.successContainer,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = ExtraColors.success,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (done > 0) "这一轮完成了" else "暂时没有需要学习的词",
            fontFamily = FontFamily.Default,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (done > 0) "本轮完成 $done 个词条。休息一下，稍后回来复习会更有效。"
            else "可以在设置里调整学习等级，或把每日新词数调大。",
            fontSize = 13.5.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(26.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            onClick = onExit
        ) {
            Text(
                text = "返回首页",
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 12.dp),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
