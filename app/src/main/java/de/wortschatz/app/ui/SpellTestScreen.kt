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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
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
                        else vm.spellPassed.toFloat() / vm.spellTotal
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
                    text = "${vm.spellPassed}/${vm.spellTotal}",
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
    LaunchedEffect(word.id, vm.spellChecked) {
        if (vm.spellChecked) vm.speakWord(word)
    }

    // 键盘常驻：换题时把焦点要回输入框。不主动 requestFocus 的话，
    // 点「下一题」后焦点丢失，键盘就自己收起来了。
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(word.id) {
        runCatching { focusRequester.requestFocus() }
    }
    // 判定后也要把焦点留住：否则输入框失焦，键盘会自己收起来
    LaunchedEffect(vm.spellChecked) {
        if (vm.spellChecked) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---- 顶部操作条：主按钮不再压在底部，键盘弹起时也点得到
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { vm.skipSpelling() },
                enabled = !vm.spellChecked
            ) {
                Text(
                    text = "跳过这个词",
                    fontSize = 12.5.sp,
                    color = if (vm.spellChecked) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            val inputEmpty = vm.spellInput.isBlank() && !vm.spellChecked
            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 112.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (inputEmpty) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary,
                onClick = {
                    if (vm.spellChecked) vm.nextSpelling() else vm.checkSpelling()
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            !vm.spellChecked -> "检查"
                            vm.spellLastCorrect && vm.spellRemaining <= 1 -> "查看结果"
                            vm.spellLastCorrect -> "下一题"
                            else -> "继续测试"
                        },
                        modifier = Modifier.padding(horizontal = 22.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (inputEmpty) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
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

        Spacer(Modifier.height(22.dp))

        OutlinedTextField(
            value = vm.spellInput,
            // 判定后不再锁住输入框：enabled=false 会让它失焦，键盘随之收起
            onValueChange = { if (!vm.spellChecked) vm.onSpellInput(it) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
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

        // 多数手机键盘没有德语变音字母，这里给四个可点的按键；
        // 即便不用，也可以写 ae / oe / ue / ss，判定时两者等价
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ä", "ö", "ü", "ß").forEach { ch ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    enabled = !vm.spellChecked,
                    onClick = { vm.onSpellInput(vm.spellInput + ch) }
                ) {
                    Text(
                        text = ch,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        fontFamily = FontFamily.Default,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (vm.spellChecked)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "打不出变音字母时，也可以写 ae / oe / ue / ss",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.outline
        )

        if (vm.spellChecked) {
            Spacer(Modifier.height(12.dp))
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
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SpellResult(vm: AppViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val total = vm.spellTotal
    val firstTry = vm.spellFirstTry
    val attempts = vm.spellAttempts
    val skipped = vm.spellSkipped
    val passed = vm.spellPassed
    val ratio = if (total == 0) 0f else firstTry.toFloat() / total
    val title = when {
        ratio >= 0.9f -> "拼写很稳"
        ratio >= 0.6f -> "基本记住了"
        else -> "还需要再练"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (skipped == 0 && passed >= total) "全部拼对" else "本轮完成",
            fontFamily = FontFamily.Default,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (skipped == 0 && passed >= total) ExtraColors.success
            else MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "$firstTry / $total 第一次就拼对",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (skipped > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "跳过 $skipped 个，未计入通过",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (attempts > total) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "共尝试 $attempts 次，拼错的词已当场重测到拼对为止",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline
            )
        }
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
            text = "全部拼对后才算通过。错的词已重新测过，下次复习会优先安排。",
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
