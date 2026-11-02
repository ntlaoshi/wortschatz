package de.wortschatz.app.ui

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel

/**
 * 文本导入：把教材、网页、聊天里的单词列表粘进来，提取德语词 → 匹配词库 → 存成新书。
 *
 * 与拍照导入共用同一套提取与匹配逻辑，区别只在于输入是文本而非图片。
 */
@Composable
fun TextImportScreen(vm: AppViewModel, onBack: () -> Unit, onLookup: (String) -> Unit = {}) {
    // 不接系统返回键的话，按返回会直接退出整个应用
    BackHandler { onBack() }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var checked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // 词库未收录的词：用户自己补的释义，key 为词形
    val meanings = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(vm.importWords) {
        checked = vm.importWords.map { it.id }.toSet()
    }
    // 每次新的识别结果都清空上一轮的补填内容
    LaunchedEffect(vm.importMissed) {
        meanings.clear()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
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
                    text = "文本导入单词",
                    fontFamily = FontFamily.Default,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val clip = clipboard.getText()?.text
                    if (!clip.isNullOrBlank()) text = clip
                }) {
                    Text("粘贴", fontSize = 12.5.sp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "把单词列表粘进来即可，空格、换行、逗号分隔都行；" +
                        "只会导入本地词库已有的词，未收录的会列出来。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(
                        onClick = {
                            keyboard?.hide()
                            vm.analyzeText(text)
                        },
                        enabled = text.isNotBlank() && !vm.importLoading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("提取单词", fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = {
                        text = ""
                        vm.clearImport()
                    }) {
                        Text("清空", fontSize = 12.5.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            ImportResultPane(
                vm = vm,
                checked = checked,
                onCheckedChange = { checked = it },
                modifier = Modifier.weight(1f),
                meanings = meanings,
                onMeaningChange = { term, zh -> meanings[term] = zh },
                onLookup = onLookup
            )

            if (vm.importWords.isNotEmpty() && !vm.importLoading) {
                ImportConfirmBar(
                    vm = vm,
                    checked = checked,
                    defaultName = "文本导入",
                    onDone = onBack,
                    meanings = meanings
                )
            }
        }
    }
}
