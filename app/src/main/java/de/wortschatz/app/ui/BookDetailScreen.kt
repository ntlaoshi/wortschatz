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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.Word
import de.wortschatz.app.ui.theme.ExtraColors
import kotlinx.coroutines.delay

/**
 * 单词书详情：书里有哪些词，以及搜索并添加新词。
 * 搜索框为空时展示已加入的词，输入时切换成搜索结果。
 */
@Composable
fun BookDetailScreen(vm: AppViewModel, onBack: () -> Unit) {
    val book = vm.currentBook
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Word>>(emptyList()) }

    LaunchedEffect(Unit) { vm.loadBookWords() }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
        } else {
            delay(180)
            vm.searchForBook(query) { results = it }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ---- 标题
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book?.name ?: "单词书",
                        fontFamily = FontFamily.Default,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "共 ${vm.bookWords.size} 词",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 搜索添加
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        text = "搜索单词并加入本书",
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "清除",
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            val showResults = query.isNotBlank()
            val list = if (showResults) results else vm.bookWords

            Text(
                text = if (showResults) "搜索结果，点 + 加入本书"
                else "书里的词，点右侧 ✕ 移出",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (list.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showResults) "没有匹配的词" else "这本书还是空的，搜索后添加",
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
                    items(list, key = { it.id }) { word ->
                        val inBook = if (showResults) vm.isInCurrentBook(word.id) else true
                        BookWordRow(
                            word = word,
                            inBook = inBook,
                            showResults = showResults,
                            onAdd = {
                                book?.let { b -> vm.addWordsToBook(b.id, listOf(word)) }
                            },
                            onRemove = { vm.removeFromBook(word) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookWordRow(
    word: Word,
    inBook: Boolean,
    showResults: Boolean,
    onAdd: () -> Unit,
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
                    text = word.zh.take(28),
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
            if (showResults) {
                if (inBook) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "已在本书",
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(18.dp),
                        tint = ExtraColors.success.copy(alpha = 0.7f)
                    )
                } else {
                    IconButton(onClick = onAdd) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "加入本书",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Clear,
                        contentDescription = "移出本书",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
