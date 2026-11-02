package de.wortschatz.app.ui

import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel

/**
 * 文本 / 拍照导入共用的结果面板。
 *
 * 关键是把「少了几个」讲清楚：命中词库多少个、未收录的是哪些词、忽略了多少虚词。
 * 只给一个数字的话，用户无法判断是词库不够还是识别出错。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportResultPane(
    vm: AppViewModel,
    checked: Set<Long>,
    onCheckedChange: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
    meanings: Map<String, String> = emptyMap(),
    onMeaningChange: ((String, String) -> Unit)? = null,
    onLookup: ((String) -> Unit)? = null
) {
    when {
        vm.importLoading -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(10.dp))
                Text("正在识别…", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.outline)
            }
        }

        vm.importError != null -> Box(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = vm.importError ?: "",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        vm.importWords.isEmpty() && vm.importMissed.isEmpty() -> Box(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "还没有结果。拍照或粘贴文本后，这里会列出可导入的单词。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        else -> LazyColumn(modifier = modifier.fillMaxWidth()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "命中词库 ${vm.importWords.size} 个",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "已选 ${checked.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            items(vm.importWords, key = { it.id }) { word ->
                val isChecked = word.id in checked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { on ->
                            onCheckedChange(if (on) checked + word.id else checked - word.id)
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = word.word,
                            fontFamily = FontFamily.Default,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = word.zh,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 原文写法与词库词形不同时标出来，方便逐行核对（Gemuese → das Gemüse）
                        val token = vm.importTokenOf[word.id]
                        if (token != null) {
                            Text(
                                text = "原文 $token",
                                fontFamily = FontFamily.Default,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // ---- 未收录的词：直接列出来，便于判断是词库缺口还是识别错误
            if (vm.importMissed.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "词库未收录 ${vm.importMissed.size} 个",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                        if (onMeaningChange == null) {
                            // 只读模式：小标签
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                vm.importMissed.forEach { term ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                                RoundedCornerShape(7.dp)
                                            )
                                            .then(
                                                if (onLookup != null)
                                                    Modifier.clickable { onLookup.invoke(term) }
                                                else Modifier
                                            )
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = term,
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Default,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        } else {
                            // 可编辑模式：词形 + 释义输入框，填了就会一起入库
                            vm.importMissed.forEach { term ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = term,
                                        modifier = Modifier.width(118.dp),
                                        fontFamily = FontFamily.Default,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = meanings[term] ?: "",
                                        onValueChange = { onMeaningChange.invoke(term, it) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        placeholder = {
                                            Text("补填中文释义（可留空）", fontSize = 11.5.sp)
                                        },
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                        shape = RoundedCornerShape(9.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (onMeaningChange != null)
                                "这些词本地词库没有。填上释义就会和选中的词一起存进新书；留空则跳过。"
                            else if (onLookup != null)
                                "拼写不对多为识别误差；拼写正确却未收录，说明词库缺这个词，点一下可直接去查词。"
                            else
                                "拼写不对多为识别误差；拼写正确却未收录，说明词库缺这个词。",
                            fontSize = 10.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (vm.importIgnored > 0) {
                item {
                    Text(
                        text = "另有 ${vm.importIgnored} 个虚词（der/und/ist 等）已忽略",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/**
 * 底部确认条：只放一个按钮，书名在弹窗里填。
 * 早前把输入框常驻在底部，键盘一弹就把结果列表挤没了。
 */
@Composable
fun ImportConfirmBar(
    vm: AppViewModel,
    checked: Set<Long>,
    defaultName: String,
    onDone: () -> Unit,
    meanings: Map<String, String> = emptyMap()
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var bookName by rememberSaveable { mutableStateOf(defaultName) }
    // 用户补填了释义的词，会作为自定义词条一并入库
    val extras = meanings.filterKeys { it in vm.importMissed }
        .filterValues { it.isNotBlank() }
        .map { (term, zh) -> term to zh }
    val total = checked.size + extras.size

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Button(
            onClick = { showDialog = true },
            enabled = total > 0,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(
                if (extras.isEmpty()) "导入 $total 个词到新单词书"
                else "导入 $total 个词（含 ${extras.size} 个自填）",
                fontSize = 14.5.sp
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("存为单词书", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        text = "将导入 $total 个词" +
                            if (extras.isEmpty()) "" else "，其中 ${extras.size} 个是你补填释义的自定义词",
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bookName,
                        onValueChange = { bookName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("单词书名称", fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    val words = vm.importWords.filter { it.id in checked }
                    vm.importAsBook(bookName, words, extras) { count, name ->
                        Toast.makeText(
                            context,
                            "已导入 $count 个词到《$name》",
                            Toast.LENGTH_SHORT
                        ).show()
                        vm.clearImport()
                        onDone()
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            }
        )
    }
}
