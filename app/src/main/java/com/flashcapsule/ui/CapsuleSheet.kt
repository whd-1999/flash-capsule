package com.flashcapsule.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.flashcapsule.model.Capsule
import com.flashcapsule.model.CapsuleStatus
import com.flashcapsule.model.ColorTag
import com.flashcapsule.transcribe.TranscriptionState

private val SheetBg = Color(0xFFF7F5FB)
private val SheetText = Color(0xFF1C1B1F)
private val SheetSub = Color(0xFF6B6673)
private val SheetDelete = Color(0xFFD32F2F)

fun capsuleColorOf(tag: ColorTag): Color = when (tag) {
    ColorTag.RED -> Color(0xFFE85450)
    ColorTag.ORANGE -> Color(0xFFF5A623)
    ColorTag.YELLOW -> Color(0xFFF5D76E)
    ColorTag.GREEN -> Color(0xFF7ED87E)
    ColorTag.BLUE -> Color(0xFF6695E5)
    ColorTag.PURPLE -> Color(0xFFB57ED8)
    ColorTag.GRAY -> Color(0xFF9E9E9E)
}

private data class Cat(val tag: ColorTag, val icon: ImageVector)

private val Categories = listOf(
    Cat(ColorTag.BLUE, Icons.Filled.Description),   // 便签
    Cat(ColorTag.RED, Icons.Filled.PriorityHigh),   // 重要
    Cat(ColorTag.ORANGE, Icons.Filled.CheckCircle), // 待办
    Cat(ColorTag.GREEN, Icons.Filled.Chat),         // 待发送
    Cat(ColorTag.PURPLE, Icons.Filled.Lightbulb),   // 灵感
)

fun capsuleShareText(context: Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun capsuleAddToCalendar(context: Context, text: String) {
    val i = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(i) }
}

fun capsuleWebSearch(context: Context, text: String) {
    if (text.isBlank()) return
    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }
}

@Composable
fun CapsuleWaveform(samples: List<Int>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val maxA = (samples.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val n = samples.size
        val slot = size.width / n
        val barW = (slot * 0.55f).coerceAtLeast(1.5f)
        val midY = size.height / 2f
        samples.forEachIndexed { i, a ->
            val h = (a / maxA) * size.height * 0.9f
            val x = i * slot + slot / 2f
            drawLine(color, Offset(x, midY - h / 2f), Offset(x, midY + h / 2f), strokeWidth = barW, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun CapsulePlayDot(isPlaying: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 语音胶囊未出字时显示的进度提示：转写中… / 模型下载 x%…。 */
@Composable
fun CapsuleTranscribeHint(capsule: Capsule, textColor: Color = SheetSub) {
    if (capsule.text.isNotBlank() || capsule.status != CapsuleStatus.TRANSCRIBING) return
    val dl by TranscriptionState.modelDownload.collectAsState()
    Text(
        text = if (dl != null) "⬇ 模型下载 $dl%…" else "⏳ 转写中…",
        style = MaterialTheme.typography.bodySmall,
        color = textColor,
    )
}

/** 展开的胶囊：顶部 5 分类色标 + 文字/波形/播放 + 底部操作栏。主 App 与侧边面板共用。 */
@Composable
fun CapsuleSheet(
    capsule: Capsule,
    playing: Boolean,
    onPlay: () -> Unit,
    onSetColor: (ColorTag) -> Unit,
    onSaveText: (String) -> Unit,
    onDelete: () -> Unit,
    onShare: (String) -> Unit,
    onCalendar: (String) -> Unit,
    onSearch: (String) -> Unit,
    onObsidian: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    var text by remember { mutableStateOf(capsule.text) }
    var color by remember { mutableStateOf(capsule.colorTag) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(interactionSource = noRipple, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(340.dp)
                .clickable(interactionSource = noRipple, indication = null) { },
            shape = RoundedCornerShape(20.dp),
            color = SheetBg,
            contentColor = SheetText,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Categories.forEach { c ->
                        CategoryDot(c.tag, c.icon, selected = color == c.tag) {
                            color = c.tag; onSetColor(c.tag)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("（语音胶囊，转写后自动填字）", color = SheetSub) },
                    textStyle = TextStyle(color = SheetText),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SheetText,
                        unfocusedTextColor = SheetText,
                        cursorColor = SheetText,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
                if (capsule.audioPath != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CapsulePlayDot(isPlaying = playing, onClick = onPlay)
                        Spacer(Modifier.width(8.dp))
                        CapsuleWaveform(
                            samples = capsule.waveform,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).height(28.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // 5 个操作图标一行；取消/保存移到第二行，避免 340dp 宽度下一行塞不下被裁掉
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onShare(text) }) { Icon(Icons.Filled.Share, "分享") }
                    IconButton(onClick = onObsidian) { Icon(Icons.Filled.Description, "落 Obsidian") }
                    IconButton(onClick = { onCalendar(text) }) { Icon(Icons.Filled.Event, "转日历") }
                    IconButton(onClick = { onSearch(text) }) { Icon(Icons.Filled.Search, "搜索") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除", tint = SheetDelete) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消", color = SheetSub) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSaveText(text) }) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun CategoryDot(tag: ColorTag, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = capsuleColorOf(tag),
        contentColor = Color.White,
        modifier = Modifier
            .size(42.dp)
            .then(if (selected) Modifier.border(BorderStroke(2.5.dp, SheetText), CircleShape) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
