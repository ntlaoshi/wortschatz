package de.wortschatz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.SpellTest
import de.wortschatz.app.data.Word
import de.wortschatz.app.ui.theme.ExtraColors

/**
 * 一轮学完后的拼写自测：看中文写德语。
 * 变音字母可用 ae/oe/ue 代替，冠词与大小写不计入差异。
 */
@Composable
fun SpellTestScreen(vm: AppViewModel, onExit: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
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
                        contentDescription = "退出拼写测试",
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        if (vm.spellTotal == 0) 0f
                        else vm.spellIndex.toFloat() / vm.spellTotal
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = ExtraColors.levelA2,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${(vm.spellIndex + 1).coerceAtMost(vm.spellTotal)}/${vm.spellTotal}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            }

            if (vm.spellFinished || vm.spellWord == null) {
                SpellResult(vm = vm, onExit = onExit, modifier = Modifier.weight(1f))
            } else {
                SpellQuestion(
                    vm = vm,
                    word = vm.spellWord!!,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SpellQuestion(vm: AppViewModel, word: Word, modifier: Modifier = Modifier) {
    // 提交后自动朗读正确读音，强化记忆
    LaunchedEffect(vm.spellIndex, vm.spellChecked) {
        if (vm.spellChecked) vm.speakWord(word)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "根据释义写出德语",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(word.level)
            Spacer(Modifier.width(6.dp))
            PosChip(word.posLabel)
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = word.zh,
            fontSize = 22.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = "首字母 ${SpellTest.hintOf(word)}",
            fontSize = 12.5.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = vm.spellInput,
            onValueChange = { vm.onSpellInput(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !vm.spellChecked,
            placeholder = { Text("输入德语拼写", fontSize = 14.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.checkSpelling() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                disabledBorderColor = if (vm.spellLastCorrect) ExtraColors.success
                else MaterialTheme.colorScheme.error
            )
        )

        Spacer(Modifier.height(14.dp))

        if (vm.spellChecked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (vm.spellLastCorrect) Icons.Rounded.Check
                    else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (vm.spellLastCorrect) ExtraColors.success
                    else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (vm.spellLastCorrect) "拼写正确"
                    else "正确答案：${SpellTest.answerOf(word)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (vm.spellLastCorrect) ExtraColors.success
                    else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.speakWord(word) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Rounded.VolumeUp,
                        contentDescription = "朗读",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 拼错时顺带把复数一起复习，避免只记住单数
            word.pluralNote(vm.pluralStyle)?.let { note ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note,
                    fontFamily = FontFamily.Default,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.height(18.dp))
        }

        Spacer(Modifier.weight(1f))

        val isLast = vm.spellIndex == vm.spellTotal - 1
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(15.dp),
            color = if (vm.spellInput.isBlank() && !vm.spellChecked)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primary,
            onClick = {
                if (vm.spellChecked) {
                    vm.nextSpelling()
                } else {
                    vm.checkSpelling()
                }
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        vm.spellChecked && isLast -> "查看结果"
                        vm.spellChecked -> "下一题"
                        else -> "检查"
                    },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (vm.spellInput.isBlank() && !vm.spellChecked)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SpellResult(vm: AppViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val total = vm.spellTotal
    val correct = vm.spellCorrect
    val ratio = if (total == 0) 0f else correct.toFloat() / total
    val (title, tip) = when {
        ratio >= 0.9f -> "拼写很稳" to "这轮词掌握得不错，明天按时复习即可。"
        ratio >= 0.6f -> "基本记住了" to "错的几个词会更容易再次忘记，建议回看一遍。"
        else -> "还需要再练" to "别急，错的词已经记录，明天的复习会优先安排它们。"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$correct",
            fontFamily = FontFamily.Default,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = if (ratio >= 0.6f) ExtraColors.success else MaterialTheme.colorScheme.error
        )
        Text(
            text = "/ $total 拼写正确",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            fontFamily = FontFamily.Default,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = tip,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            onClick = onExit
        ) {
            Text(
                text = "完成",
                modifier = Modifier.padding(horizontal = 34.dp, vertical = 12.dp),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
