package de.wortschatz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.Word

@Composable
fun StarredScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "收藏",
                fontFamily = FontFamily.Default,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${vm.starred.size} 个",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (vm.starred.isEmpty()) {
            EmptyState(
                title = "还没有收藏的词",
                subtitle = "查词时点右侧星标即可收藏，背单词时下滑卡片也能收藏，方便集中回看",
                icon = {
                    Icon(
                        Icons.Rounded.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(vm.starred, key = { it.id }) { word: Word ->
                    WordListItem(
                        word = word,
                        starred = true,
                        onClick = { vm.speakWord(word) },
                        onSpeak = { vm.speakWord(word) },
                        onStar = { vm.toggleStar(word.id) }
                    )
                }
            }
        }
    }
}
