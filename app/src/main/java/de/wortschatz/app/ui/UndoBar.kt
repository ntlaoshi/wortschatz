package de.wortschatz.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import kotlinx.coroutines.delay

/**
 * 底部浮动的「已撤销」提示条。
 *
 * 只挂在应用最外层一次，学习页、拼写页、导入页都能看到；
 * 6 秒不动自动消失，避免一直占着屏幕。
 */
@Composable
fun UndoBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    val label = vm.undoLabel
    // 退场动画期间 label 已变 null，需要留住上一条文案，否则会闪空
    var display by remember { mutableStateOf("") }
    LaunchedEffect(label) {
        if (label != null) {
            display = label
            delay(6000)
            if (vm.undoLabel == label) vm.clearUndo()
        }
    }

    // 键盘弹起时把撤销条顶到键盘上方，否则会被挡住
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottom = if (ime > 0.dp) ime + 10.dp else 96.dp

    AnimatedVisibility(
        visible = label != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.padding(bottom = bottom)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = display,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                TextButton(onClick = { vm.undo() }) {
                    Text(
                        text = "撤销",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.inversePrimary
                    )
                }
            }
        }
    }
}
