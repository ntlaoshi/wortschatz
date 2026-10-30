package de.wortschatz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.Word
import de.wortschatz.app.ui.theme.ExtraColors

/**
 * 学习记录管理：分别查看已学习与已掌握的词，逐个决定标记掌握、取消掌握或移除记录。
 */
@Composable
fun LearningRecordsScreen(vm: AppViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadRecords() }
    var tab by remember { mutableIntStateOf(0) }

    val words: List<Word> = if (tab == 0) vm.learnedWords else vm.masteredWords

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
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
                    text = "学习记录",
                    fontFamily = FontFamily.Default,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // ---- 切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentChip(
                    text = "已学习 ${vm.learnedWords.size}",
                    selected = tab == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { tab = 0 }
                )
                SegmentChip(
                    text = "已掌握 ${vm.masteredWords.size}",
                    selected = tab == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { tab = 1 }
                )
            }

            Text(
                text = if (tab == 0)
                    "这些词在复习计划中。点 ✓ 标记为已掌握，点垃圾桶移除学习记录（回到未学）"
                else "这些词不会再出现在复习队列。点撤销可放回复习计划",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (words.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tab == 0) "还没有学习中的词" else "还没有标记为已掌握的词",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(words, key = { it.id }) { word ->
                        RecordRow(
                            word = word,
                            mastered = tab == 1,
                            onMaster = { vm.setMastered(word, true) },
                            onUnmaster = { vm.setMastered(word, false) },
                            onRemove = { vm.removeLearningRecord(word) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordRow(
    word: Word,
    mastered: Boolean,
    onMaster: () -> Unit,
    onUnmaster: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.word,
                        fontFamily = FontFamily.Default,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    LevelBadge(word.level)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = word.zh.take(26),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                word.pluralNote?.let { note ->
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = note,
                        fontFamily = FontFamily.Default,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (mastered) {
                IconButton(onClick = onUnmaster) {
                    Icon(
                        Icons.Rounded.Undo,
                        contentDescription = "取消已掌握",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                IconButton(onClick = onMaster) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "标记已掌握",
                        modifier = Modifier.size(20.dp),
                        tint = ExtraColors.success
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "移除学习记录",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
