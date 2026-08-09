package com.flashcapsule.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.text.Html
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flashcapsule.capture.AudioPlayer
import com.flashcapsule.model.Capsule
import com.flashcapsule.model.CapsuleStatus
import com.flashcapsule.model.ColorTag
import com.flashcapsule.transcribe.TranscriptionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

/** 用指定引擎在浏览器里搜 text。 */
fun openSearchEngine(context: Context, engine: String, text: String) {
    if (text.isBlank()) return
    val base = when (engine) {
        "google" -> "https://www.google.com/search?q="
        "baidu" -> "https://www.baidu.com/s?wd="
        "bing" -> "https://www.bing.com/search?q="
        else -> "https://zh.wikipedia.org/w/index.php?search=" // wiki
    }
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(base + Uri.encode(text)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }
}

/** 搜索面板的四种状态。 */
private sealed interface WebSearchUi {
    data object Idle : WebSearchUi
    data object Loading : WebSearchUi
    data class Hit(val title: String, val snippet: String, val url: String) : WebSearchUi
    data object Empty : WebSearchUi
    data object Error : WebSearchUi
}

/** 抓取搜索摘要（Wikipedia API，零依赖、免费）。IO 线程，失败静默降级为引擎跳转。 */
private suspend fun fetchWebSearch(query: String): WebSearchUi = withContext(Dispatchers.IO) {
    runCatching {
        val api = "https://zh.wikipedia.org/w/api.php?action=query&list=search" +
            "&srsearch=${Uri.encode(query)}&format=json&utf8=1&srlimit=1&srprop=snippet"
        val conn = (URL(api).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 8000
            setRequestProperty("User-Agent", "FlashCapsule/0.11")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val search = JSONObject(body).getJSONObject("query").getJSONArray("search")
        if (search.length() == 0) {
            WebSearchUi.Empty
        } else {
            val first = search.getJSONObject(0)
            val title = first.getString("title")
            val snippet = first.optString("snippet", "")
                .let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
            WebSearchUi.Hit(title, snippet, "https://zh.wikipedia.org/wiki/${Uri.encode(title)}")
        }
    }.getOrElse { WebSearchUi.Error }
}

/** 原版风格的自动网络搜索：胶囊展开即拿文字去搜，底部可切引擎。 */
@Composable
private fun WebSearchPanel(query: String) {
    val context = LocalContext.current
    var ui by remember { mutableStateOf<WebSearchUi>(WebSearchUi.Idle) }
    LaunchedEffect(query) {
        if (query.isBlank()) { ui = WebSearchUi.Idle; return@LaunchedEffect }
        ui = WebSearchUi.Loading
        delay(500) // 防抖：停止输入后再搜，避免逐字请求
        ui = fetchWebSearch(query)
    }
    Column {
        Spacer(Modifier.height(12.dp))
        Text("网络搜索", style = MaterialTheme.typography.labelMedium, color = SheetSub)
        Spacer(Modifier.height(6.dp))
        when (val s = ui) {
            WebSearchUi.Idle, WebSearchUi.Loading ->
                Text("搜索中…", style = MaterialTheme.typography.bodySmall, color = SheetSub)
            is WebSearchUi.Hit -> {
                Text(
                    s.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SheetText,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    s.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = SheetSub,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WebSearchUi.Empty ->
                Text("没搜到词条，试试下方引擎", style = MaterialTheme.typography.bodySmall, color = SheetSub)
            WebSearchUi.Error ->
                Text("网络搜索失败，试试下方引擎", style = MaterialTheme.typography.bodySmall, color = SheetSub)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Google" to "google", "百度" to "baidu", "Bing" to "bing", "维基" to "wiki").forEach { (label, engine) ->
                TextButton(
                    onClick = { openSearchEngine(context, engine, query) },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) { Text(label, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
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

/** 播放控制条：播放/暂停 + 波形 + 进度 Slider（可拖动）+ 时间。 */
@Composable
private fun PlaybackControl(
    playing: Boolean,
    onPlay: () -> Unit,
    waveform: List<Int>,
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // 播放时每 200ms 刷新一次进度
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            position = AudioPlayer.position().toLong()
            if (duration == 0L) duration = AudioPlayer.duration().toLong()
            kotlinx.coroutines.delay(200)
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapsulePlayDot(isPlaying = playing, onClick = onPlay)
            Spacer(Modifier.width(8.dp))
            CapsuleWaveform(
                samples = waveform,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).height(28.dp),
            )
        }
        if (duration > 0) {
            Slider(
                value = position.coerceIn(0, duration).toFloat(),
                onValueChange = { AudioPlayer.seekTo(it.toInt()) },
                valueRange = 0f..duration.toFloat(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(fmtMs(position), style = MaterialTheme.typography.labelSmall, color = SheetSub)
                Text(fmtMs(duration), style = MaterialTheme.typography.labelSmall, color = SheetSub)
            }
        }
    }
}

private fun fmtMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
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

/** 展开的胶囊：顶部 5 分类色标 + 标题/文字/波形/播放 + 底部操作栏。主 App 与侧边面板共用。 */
@Composable
fun CapsuleSheet(
    capsule: Capsule,
    playing: Boolean,
    onPlay: () -> Unit,
    onSetColor: (ColorTag) -> Unit,
    onSaveText: (String) -> Unit,
    onSaveTitle: (String) -> Unit,
    onEnrich: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onShare: (String) -> Unit,
    onCalendar: (String) -> Unit,
    onObsidian: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    var text by remember(capsule.id) { mutableStateOf(capsule.text) }
    var title by remember(capsule.id) { mutableStateOf(capsule.title) }
    var color by remember(capsule.id) { mutableStateOf(capsule.colorTag) }

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
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("自动标题（AI 生成，可改）", color = SheetSub) },
                    textStyle = TextStyle(color = SheetText),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SheetText,
                        unfocusedTextColor = SheetText,
                        cursorColor = SheetText,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
                Spacer(Modifier.height(8.dp))
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
                    PlaybackControl(
                        playing = playing,
                        onPlay = onPlay,
                        waveform = capsule.waveform,
                    )
                }
                if (capsule.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        capsule.tags.forEach { tag ->
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = SheetSub,
                                modifier = Modifier
                                    .background(Color(0x14000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEnrich != null) {
                        IconButton(onClick = { onEnrich() }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 重新生成标题/分类")
                        }
                    }
                    IconButton(onClick = { onShare(text) }) { Icon(Icons.Filled.Share, "分享") }
                    IconButton(onClick = onObsidian) { Icon(Icons.Filled.Description, "落 Obsidian") }
                    IconButton(onClick = { onCalendar(text) }) { Icon(Icons.Filled.Event, "转日历") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除", tint = SheetDelete) }
                }
                // 原版：胶囊下方自动出现网络搜索结果面板
                if (text.isNotBlank()) WebSearchPanel(text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消", color = SheetSub) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSaveText(text); onSaveTitle(title) }) { Text("保存") }
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
