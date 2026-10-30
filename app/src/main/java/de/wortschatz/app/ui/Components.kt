package de.wortschatz.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.data.PluralStyle
import de.wortschatz.app.data.Word
import de.wortschatz.app.ui.theme.ExtraColors

/** 等级徽标 A1 / A2 / B1 / B2 */
@Composable
fun LevelBadge(level: String, modifier: Modifier = Modifier) {
    val color = ExtraColors.levelColor(level)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = level.uppercase(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}

/** 词性标签 */
@Composable
fun PosChip(label: String, modifier: Modifier = Modifier) {
    if (label.isBlank()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 圆形发音按钮 */
@Composable
fun SpeakButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    active: Boolean = false,
    contentDescription: String = "朗读"
) {
    val bg by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primaryContainer,
        label = "speakBg"
    )
    val fg by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "speakFg"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

/** 收藏星标按钮 */
@Composable
fun StarButton(starred: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(
            imageVector = if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (starred) "取消收藏" else "加入生词本",
            tint = if (starred) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 列表中的词条行 */
@Composable
fun WordListItem(
    word: Word,
    starred: Boolean,
    pluralStyle: PluralStyle,
    onClick: () -> Unit,
    onSpeak: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = word.word,
                    fontFamily = FontFamily.Default,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                LevelBadge(word.level)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = word.zh,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            word.pluralNote(pluralStyle)?.let { note ->
                Spacer(Modifier.height(3.dp))
                Text(
                    text = note,
                    fontFamily = FontFamily.Default,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) {
            trailing()
        } else {
            SpeakButton(onClick = onSpeak, size = 34.dp)
            StarButton(starred = starred, onClick = onStar)
        }
    }
}

/** 小标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

/** 空状态占位 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon?.let {
            it()
            Spacer(Modifier.height(14.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** 例句块：德语原句 + 中文翻译 */
@Composable
fun ExampleBlock(
    german: String,
    chinese: String?,
    modifier: Modifier = Modifier,
    onSpeak: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = german,
                    fontFamily = FontFamily.Default,
                    fontStyle = FontStyle.Normal,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!chinese.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = chinese,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onSpeak != null) {
                Spacer(Modifier.width(8.dp))
                SpeakButton(onClick = onSpeak, size = 32.dp)
            }
        }
    }
}

/** 数值统计块 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            fontFamily = FontFamily.Default,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
