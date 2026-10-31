package de.wortschatz.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.Word
import de.wortschatz.app.data.WordBook
import de.wortschatz.app.tts.Speaker
import de.wortschatz.app.ui.theme.ExtraColors
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenRecords: () -> Unit,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val levels = AppViewModel.ALL_LEVELS
    var showResetConfirm by remember { mutableStateOf(false) }
    var showBookDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<WordBook?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        vm.importCustomWords(text) { count, bookName ->
            val msg = when {
                count < 0 -> "格式不正确，需要 {\"words\":[...]} 结构"
                count == 0 -> "已新建单词书《$bookName》，词条词库里都有"
                else -> "已导入 $count 个词条，新建单词书《$bookName》"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "设置",
                    fontFamily = FontFamily.Default,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // ---------------- 单词书
        item {
            SettingCard(title = "单词书") {
                Text(
                    text = "内置书按等级划分；自建书是自己挑的词，学习与复习只从当前这本取词",
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                vm.allBooks.forEach { book ->
                    val selected = if (book.builtin)
                        vm.currentBookId == null && vm.levels == book.levels
                    else vm.currentBookId == book.id
                    val count = if (book.builtin)
                        book.levels.sumOf { vm.levelCounts[it] ?: 0 }
                    else vm.bookCounts[book.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { vm.selectBook(book) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { vm.selectBook(book) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.name,
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.Medium
                                else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = WordBook.descriptionOf(book),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$count 词",
                            fontSize = 12.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                        if (!book.builtin) {
                            IconButton(
                                onClick = {
                                    editingBook = book
                                    showBookDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "重命名",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { vm.deleteBook(book.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 选中自建书时，提供加词入口
                val active = vm.currentBook
                if (active != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onOpenBook
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.ListAlt,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "管理《${active.name}》的单词 · ${vm.bookWords.size} 词",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    onClick = {
                        editingBook = null
                        showBookDialog = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "新建单词书",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // ---------------- 学习
        item {
            SettingCard(title = "自定义学习范围") {
                Text(
                    text = "选择要学习的等级，未选中的等级不会出现在学习队列里",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    levels.forEach { level ->
                        val selected = vm.levels.contains(level)
                        FilterChip(
                            selected = selected,
                            onClick = { vm.toggleLevel(level) },
                            label = {
                                Text(
                                    text = level,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExtraColors.levelColor(level)
                                    .copy(alpha = 0.14f),
                                selectedLabelColor = ExtraColors.levelColor(level)
                            )
                        )
                    }
                }
            }
        }

        item {
            SettingCard(title = "每轮词数") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "每轮背 ${vm.sessionSize} 个词",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "含复习与新词",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Slider(
                    value = vm.sessionSize.toFloat(),
                    onValueChange = { vm.updateSessionSize(it.roundToInt()) },
                    valueRange = 5f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "到期复习的词优先排入，不足的部分补新词",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingCard(title = "拼写测试") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "学完一轮后拼写自测",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "看中文写出德语单词，变音字母可用 ae/oe/ue 代替",
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vm.spellTestEnabled,
                        onCheckedChange = { vm.updateSpellTest(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // ---------------- 复数显示
        item {
            SettingCard(title = "名词复数") {
                Text(
                    text = "名词卡片与列表里的复数怎么显示",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "完整形式", true to "只显示词尾").forEach { (suffixOnly, label) ->
                        FilterChip(
                            selected = vm.pluralSuffixOnly == suffixOnly,
                            onClick = { vm.updatePluralSuffixOnly(suffixOnly) },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.14f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 预览直接用真实词条的格式化结果，避免与卡片上的写法不一致
                val sample = remember {
                    Word(
                        word = "der Mann", lemma = "Mann", pos = "n", gender = "m",
                        plural = "die Männer", zh = "男人", en = null, example = null,
                        exampleZh = null, level = "A1", tags = null
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(
                            text = sample.word,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = sample.pluralNote(vm.pluralStyle) ?: "",
                            fontFamily = FontFamily.Default,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "词尾写法与教材一致：¨ 表示变音，-er 表示词尾；复数与单数同形时显示「同形」",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // ---------------- 外观
        item {
            SettingCard(title = "外观") {
                Text(
                    text = "主题",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色")
                    modes.forEach { (value, label) ->
                        FilterChip(
                            selected = vm.themeMode == value,
                            onClick = { vm.updateThemeMode(value) },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.14f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // ---------------- 发音
        item {
            SettingCard(title = "发音") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "进入卡片自动朗读",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "翻到新词时自动播放德语发音",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vm.autoSpeak,
                        onCheckedChange = { vm.updateAutoSpeak(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "语速",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(14.dp))
                    Slider(
                        value = vm.ttsSpeed,
                        onValueChange = { vm.updateTtsSpeed(it) },
                        valueRange = 0.5f..1.3f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = String.format("%.1fx", vm.ttsSpeed),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (vm.speaker.offlineCount > 0) ExtraColors.success
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (vm.speaker.offlineCount > 0)
                                "离线发音已内置 · ${vm.speaker.offlineCount} 词"
                            else "离线发音包缺失",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (vm.speaker.germanAvailable)
                                "不依赖系统语音组件；例句可用系统语音朗读"
                            else "不依赖系统语音组件；例句朗读联网时自动获取",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { vm.speak("Entschuldigung") }
                    ) {
                        Text(
                            text = "试听单词",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { vm.speak("Guten Tag, wie geht es Ihnen?") }
                    ) {
                        Text(
                            text = "试听句子",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---------------- 词库
        item {
            SettingCard(title = "词库") {
                Text(
                    text = "内置 ${vm.totalWords} 个词条",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppViewModel.ALL_LEVELS.forEach { level ->
                        val count = vm.levelCounts[level] ?: 0
                        Box(
                            modifier = Modifier
                                .background(
                                    ExtraColors.levelColor(level).copy(alpha = 0.1f),
                                    RoundedCornerShape(9.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$level  $count",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = ExtraColors.levelColor(level)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "导入词表并新建单词书",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "选一个 JSON 词表，会新建一本同名单词书并把词放进去；" +
                        "格式：{\"name\":\"书名\",\"words\":[{\"w\":\"der Hund\",\"zh\":\"狗\"}]}",
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // ---------------- 学习记录
        item {
            SettingCard(title = "学习记录") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "学习中 ${vm.learnedWords.size} 个 · 已掌握 ${vm.masteredWords.size} 个",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "进列表可以逐个标记掌握或移除记录；已掌握的词不再进入复习",
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    onClick = onOpenRecords
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ListAlt,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "管理已学习 / 已掌握",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    ),
                    onClick = {
                        if (vm.learnedWords.isNotEmpty() || vm.masteredWords.isNotEmpty()) {
                            showResetConfirm = true
                        }
                    }
                ) {
                    Text(
                        text = "清除全部学习记录",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ---------------- 关于
        item {
            SettingCard(title = "关于") {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "德语单词 Wortschatz 1.14",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "离线词库 + 内置德语发音。学习进度基于 SM-2 间隔重复算法，" +
                                "每轮优先复习到期词条，再补充新词，学完可做拼写自测。",
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("清除全部学习记录？", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "已学的 ${vm.stats.totalLearned} 个词会重置为未学，" +
                        "复习计划与掌握程度一并清空。此操作不可撤销，收藏的词不会被删除。",
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    vm.resetAllProgress { n ->
                        Toast.makeText(context, "已清除 $n 个词的学习记录", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showBookDialog) {
        BookEditDialog(
            initial = editingBook,
            onDismiss = { showBookDialog = false },
            onConfirm = { name ->
                val editing = editingBook
                if (editing == null) vm.addBook(name) else vm.updateBook(editing.id, name)
                showBookDialog = false
            }
        )
    }
}

/** 新建 / 重命名单词书。书里的词通过「管理单词」单独添加。 */
@Composable
private fun BookEditDialog(
    initial: WordBook?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "新建单词书" else "重命名单词书",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称", fontSize = 13.sp) },
                    placeholder = { Text("例如：考试重点词", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (initial == null) "建好后点「管理单词」把要背的词加进来"
                    else "只改名字，书里的词不受影响",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}
