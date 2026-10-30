package de.wortschatz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun StudyScreen(vm: AppViewModel, onExit: () -> Unit) {
    // 一轮学完后进入拼写自测（设置中可关闭）
    if (vm.spellActive) {
        val quit = {
            vm.exitSpelling()
            onExit()
        }
        BackHandler { quit() }
        SpellTestScreen(vm = vm, onExit = quit)
        return
    }

    val current = vm.current

    if (vm.sessionLoading) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(26.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        return
    }

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
                    starred = vm.isStarred(current.word.id),
                    speaker = { vm.speak(it) },
                    onReveal = { vm.reveal() },
                    onRate = { vm.rate(it) },
                    onStar = { vm.toggleStar(current.word.id) },
                    onMastered = { vm.masterCurrent() },
                    modifier = Modifier.weight(1f)
                )
                SwipeHint()
                RatingBar(
                    enabled = true,
                    onRate = { vm.rate(it) }
                )
            }
        }
    }
}

/** 滑动手势方向与其对应的操作 */
private enum class SwipeDir(
    val rating: Rating?,
    val label: String,
    val color: Color
) {
    LEFT(Rating.GOOD, "认识", Color(0xFF2E7D5B)),
    RIGHT(Rating.AGAIN, "忘了", Color(0xFFC0392B)),
    UP(Rating.HARD, "模糊", Color(0xFF9A6B00)),
    DOWN(null, "收藏", Color(0xFFB8860B))
}

@Composable
private fun StudyCard(
    item: WordWithProgress,
    revealed: Boolean,
    starred: Boolean,
    speaker: (String) -> Unit,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    onStar: () -> Unit,
    onMastered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val word = item.word
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }

    val threshold = with(density) { 92.dp.toPx() }
    val flyDistance = with(density) { 1400.dp.toPx() }

    // 换卡时复位位置，避免沿用上一张的偏移
    LaunchedEffect(word.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        busy = false
    }

    fun settle() {
        scope.launch {
            launch { offsetX.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 420f)) }
            offsetY.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 420f))
        }
    }

    fun commit(dir: SwipeDir) {
        if (busy) return
        busy = true
        scope.launch {
            val spec = tween<Float>(durationMillis = 230, easing = FastOutLinearInEasing)
            when (dir) {
                SwipeDir.LEFT -> offsetX.animateTo(-flyDistance, spec)
                SwipeDir.RIGHT -> offsetX.animateTo(flyDistance, spec)
                SwipeDir.UP -> offsetY.animateTo(-flyDistance, spec)
                SwipeDir.DOWN -> offsetY.animateTo(flyDistance, spec)
            }
            // 飞出后再提交，保证动画期间卡片内容不变
            val rating = dir.rating
            if (rating != null) {
                onRate(rating)
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            } else {
                onStar()
                offsetY.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 520f))
            }
            busy = false
        }
    }

    val dir = when {
        abs(offsetX.value) < 6f && abs(offsetY.value) < 6f -> null
        abs(offsetX.value) > abs(offsetY.value) ->
            if (offsetX.value < 0) SwipeDir.LEFT else SwipeDir.RIGHT
        else -> if (offsetY.value < 0) SwipeDir.UP else SwipeDir.DOWN
    }
    val dragDistance = maxOf(abs(offsetX.value), abs(offsetY.value))
    val badgeAlpha = (dragDistance / threshold).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = (offsetX.value / 58f).coerceIn(-13f, 13f)
                    // 拖动时轻微放大，强化「拿起来」的手感
                    val lift = (dragDistance / (threshold * 3)).coerceIn(0f, 0.035f)
                    scaleX = 1f + lift
                    scaleY = 1f + lift
                }
                .pointerInput(revealed, busy) {
                    if (busy) return@pointerInput
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + drag.x)
                                offsetY.snapTo(offsetY.value + drag.y)
                            }
                        },
                        onDragEnd = {
                            val dx = offsetX.value
                            val dy = offsetY.value
                            val target = when {
                                abs(dx) > abs(dy) && abs(dx) > threshold ->
                                    if (dx < 0) SwipeDir.LEFT else SwipeDir.RIGHT
                                abs(dy) > threshold ->
                                    if (dy < 0) SwipeDir.UP else SwipeDir.DOWN
                                else -> null
                            }
                            if (target == null) {
                                settle()
                            } else {
                                commit(target)
                            }
                        },
                        onDragCancel = { settle() }
                    )
                }
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
                else FrontFace(
                    word = word,
                    speaker = speaker,
                    isNew = item.progress.state == 0,
                    starred = starred
                )
            }
        }

        // 已掌握：点一下这个词就永久退出复习队列。按钮跟随卡片位移，手感一致。
        if (dragDistance < threshold * 0.6f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                    }
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ExtraColors.successContainer,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, ExtraColors.success.copy(alpha = 0.32f)
                    ),
                    onClick = { if (!busy) onMastered() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = ExtraColors.success
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "已掌握",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = ExtraColors.success
                        )
                    }
                }
            }
        }

        // 拖动方向提示：印章式徽章，随拖动距离淡入
        if (dir != null && badgeAlpha > 0.12f) {
            val isDown = dir == SwipeDir.DOWN
            val label = if (isDown && starred) "已收藏" else dir.label
            Box(
                modifier = Modifier
                    .align(
                        when (dir) {
                            SwipeDir.LEFT -> Alignment.CenterEnd
                            SwipeDir.RIGHT -> Alignment.CenterStart
                            SwipeDir.UP -> Alignment.BottomCenter
                            SwipeDir.DOWN -> Alignment.TopCenter
                        }
                    )
                    .padding(26.dp)
                    .graphicsLayer {
                        alpha = badgeAlpha
                        rotationZ = if (dir == SwipeDir.LEFT) 10f
                        else if (dir == SwipeDir.RIGHT) -10f else 0f
                        scaleX = 0.85f + badgeAlpha * 0.15f
                        scaleY = 0.85f + badgeAlpha * 0.15f
                    }
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = dir.color.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp, dir.color.copy(alpha = 0.75f)
                    )
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = dir.color
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeHint() {
    Text(
        text = "左滑认识 · 右滑忘了 · 上滑模糊 · 下滑收藏",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        textAlign = TextAlign.Center,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun FrontFace(
    word: Word,
    speaker: (String) -> Unit,
    isNew: Boolean,
    starred: Boolean
) {
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
            if (starred) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ExtraColors.starContainer
                ) {
                    Text(
                        text = "已收藏",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExtraColors.star,
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
        Triple(Rating.GOOD, Color(0xFF2E7D5B), "认识")
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
