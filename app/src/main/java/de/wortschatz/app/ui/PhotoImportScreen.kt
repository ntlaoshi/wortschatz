package de.wortschatz.app.ui

import androidx.activity.compose.BackHandler

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import de.wortschatz.app.AppViewModel
import java.io.File

/**
 * 拍照 / 相册导入单词：OCR 识别 → 匹配本地词库 → 存成一本新单词书。
 *
 * 只导入词库里已有的词条，保证导入后每张卡片都有释义可背；
 * 未收录的词会在结果区逐个列出，方便判断是识别误差还是词库缺口。
 */
@Composable
fun PhotoImportScreen(vm: AppViewModel, onBack: () -> Unit, onLookup: (String) -> Unit = {}) {
    // 不接系统返回键的话，按返回会直接退出整个应用
    BackHandler { onBack() }
    val context = LocalContext.current
    var checked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // 词库未收录的词：用户自己补的释义，key 为词形
    val meanings = remember { mutableStateMapOf<String, String>() }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(vm.importWords) {
        checked = vm.importWords.map { it.id }.toSet()
    }
    // 每次新的识别结果都清空上一轮的补填内容
    LaunchedEffect(vm.importMissed) {
        meanings.clear()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) pendingUri?.let { vm.recognizePhoto(it) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.recognizePhoto(uri)
    }

    fun takePhoto() {
        val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
        val file = File.createTempFile("shot_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUri = uri
        cameraLauncher.launch(uri)
    }

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
                    text = "拍照导入单词",
                    fontFamily = FontFamily.Default,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "拍教材或笔记上的单词表，自动识别并匹配本地词库；只会导入词库里已有的词。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { takePhoto() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("拍照", fontSize = 14.sp)
                    }
                    Button(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("相册", fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            ImportResultPane(
                vm = vm,
                checked = checked,
                onCheckedChange = { checked = it },
                modifier = Modifier.weight(1f),
                meanings = meanings,
                onMeaningChange = { term, zh -> meanings[term] = zh },
                onLookup = onLookup,
                onRemove = { id ->
                    vm.removeImportWord(id)
                    checked = checked - id
                },
                onRemoveMissed = { term ->
                    meanings.remove(term)
                    vm.removeImportMissed(term)
                },
                onClear = {
                    meanings.clear()
                    checked = emptySet()
                    vm.clearImport(undoable = true)
                }
            )

            if (vm.importWords.isNotEmpty() && !vm.importLoading) {
                ImportConfirmBar(
                    vm = vm,
                    checked = checked,
                    defaultName = "拍照导入",
                    onDone = onBack,
                    meanings = meanings
                )
            }
        }
    }
}
