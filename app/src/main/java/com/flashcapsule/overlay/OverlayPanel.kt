package com.flashcapsule.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.provider.CalendarContract
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.flashcapsule.R
import com.flashcapsule.capture.AudioPlayer
import com.flashcapsule.capture.AudioRecorder
import com.flashcapsule.capture.MicPermissionActivity
import com.flashcapsule.data.CaptureRepository
import com.flashcapsule.data.FileStore
import com.flashcapsule.model.Capsule
import com.flashcapsule.model.ColorTag
import com.flashcapsule.model.RawCapture
import com.flashcapsule.ui.CapsulePlayDot
import com.flashcapsule.ui.CapsuleSheet
import com.flashcapsule.ui.CapsuleTranscribeHint
import com.flashcapsule.ui.CapsuleWaveform
import com.flashcapsule.ui.capsuleAddToCalendar
import com.flashcapsule.ui.capsuleColorOf
import com.flashcapsule.ui.capsuleShareText
import com.flashcapsule.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 悬浮面板：全屏遮罩 + 右侧堆叠胶囊。
 * 说话 = 录音（存音频 + 实时波形），胶囊里可 ▶ 回放；打字 = 面板内直接写。
 * （文字转写将在 v0.6 第二步用端上 Whisper 从音频生成。）
 */
class OverlayPanel(
    private val context: Context,
    private val repo: CaptureRepository,
    @Suppress("unused") private val langCode: String,
    private val onDismiss: () -> Unit,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null

    fun show() {
        if (root != null) return
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val themed = ContextThemeWrapper(context, R.style.Theme_FlashCapsule)
        val compose = ComposeView(themed).apply {
            setViewTreeLifecycleOwner(this@OverlayPanel)
            setViewTreeViewModelStoreOwner(this@OverlayPanel)
            setViewTreeSavedStateRegistryOwner(this@OverlayPanel)
            setContent {
                AppTheme { PanelContent(repo = repo, onDismiss = { dismiss() }) }
            }
        }

        val (w, h) = fullScreenSize()
        val flags = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val lp = WindowManager.LayoutParams(w, h, overlayType(), flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        windowManager.addView(compose, lp)
        root = compose
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun dismiss() {
        val v = root ?: return
        root = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        runCatching { windowManager.removeView(v) }
        viewModelStore.clear()
        onDismiss()
    }

    private fun fullScreenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
}

private val CardWidth = 250.dp
private val ScrimColor = Color(0x99000000)
private val CardBg = Color(0xFFF7F5FB)
private val CardText = Color(0xFF1C1B1F)
private val CardSub = Color(0xFF6B6673)
private val DeleteRed = Color(0xFFD32F2F)
private val RecRed = Color(0xFFE53935)

@Composable
private fun PanelContent(repo: CaptureRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val flow = remember { repo.observeAll() }
    val capsules by flow.collectAsState(initial = emptyList())
    val noRipple = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    val recorder = remember { AudioRecorder(context) }
    val fileStore = remember { FileStore(context) }
    var recording by remember { mutableStateOf(false) }
    var wave by remember { mutableStateOf(listOf<Int>()) }
    var recFile by remember { mutableStateOf<java.io.File?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }

    var editing by remember { mutableStateOf<Capsule?>(null) }
    var composingNew by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { recorder.cancel(); AudioPlayer.stop() }
    }

    fun startRec() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            context.startActivity(
                Intent(context, MicPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            toast = "已请求麦克风权限，授权后再点「说话」"
            return
        }
        val f = fileStore.newAudioFile(UUID.randomUUID().toString())
        try {
            recorder.start(f)
            recFile = f
            wave = emptyList()
            recording = true
        } catch (e: Exception) {
            toast = "录音启动失败"
        }
    }

    fun finishRec() {
        val f = recorder.stop()
        recording = false
        if (f != null && wave.isNotEmpty()) {
            val samples = wave
            scope.launch {
                repo.ingest(RawCapture(audioPath = f.absolutePath, waveform = samples, source = "voice"))
            }
        } else {
            toast = "录音太短"
        }
        recFile = null
    }

    fun cancelRec() {
        recorder.cancel(); recording = false; recFile = null; wave = emptyList()
    }

    LaunchedEffect(recording) {
        while (recording) {
            wave = wave + recorder.amplitude()
            delay(80)
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2500); toast = null } }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                .clickable(interactionSource = noRipple, indication = null) { onDismiss() }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(top = 52.dp, end = 12.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "闪念胶囊",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(end = 4.dp),
            )

            if (capsules.isEmpty()) {
                CapsuleCard(onClick = {}) {
                    Text("还没有胶囊 —— 点下面「说话/打字」记一条", color = CardSub, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    items(capsules.take(50), key = { it.id }) { c ->
                        CapsuleCard(colorTag = c.colorTag, onClick = { editing = c }) {
                            if (c.text.isNotBlank()) {
                                Text(
                                    text = c.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CardText,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                CapsuleTranscribeHint(c)
                            }
                            if (c.audioPath != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CapsulePlayDot(isPlaying = playing == c.audioPath) {
                                        AudioPlayer.toggle(c.audioPath) { playing = AudioPlayer.currentPath }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    CapsuleWaveform(
                                        samples = c.waveform,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f).height(26.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = fmt(c.createdAt) + " · " + c.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = CardSub,
                            )
                        }
                    }
                }
            }

            toast?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White) }

            if (recording) {
                RecordingCard(wave = wave, onFinish = { finishRec() }, onCancel = { cancelRec() })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(if (recording) "录音中" else "说话", Icons.Filled.Mic) {
                    if (recording) finishRec() else startRec()
                }
                PillButton("打字", Icons.Filled.Keyboard) { composingNew = true }
            }
        }

        editing?.let { cap ->
            CapsuleSheet(
                capsule = cap,
                playing = playing == cap.audioPath,
                onPlay = { cap.audioPath?.let { p -> AudioPlayer.toggle(p) { playing = AudioPlayer.currentPath } } },
                onSetColor = { tag -> scope.launch { repo.setColor(cap.id, tag) } },
                onSaveText = { t -> scope.launch { repo.updateText(cap.id, t) }; editing = null },
                onDelete = { scope.launch { repo.delete(cap.id) }; editing = null },
                onShare = { t -> capsuleShareText(context, t); editing = null },
                onCalendar = { t -> capsuleAddToCalendar(context, t); editing = null },
                onObsidian = { scope.launch { repo.exportTo("obsidian", cap.id) }; editing = null },
                onDismiss = { editing = null },
            )
        }
        if (composingNew) {
            EditSheet(
                title = "新胶囊",
                initialText = "",
                showDelete = false,
                onSave = { t -> if (t.isNotBlank()) scope.launch { repo.ingest(RawCapture(text = t, source = "app")) }; composingNew = false },
                onDelete = {},
                onDismiss = { composingNew = false },
            )
        }
    }
}

@Composable
private fun RecordingCard(wave: List<Int>, onFinish: () -> Unit, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.width(CardWidth),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        contentColor = CardText,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(RecRed, CircleShape))
            Spacer(Modifier.width(8.dp))
            CapsuleWaveform(
                samples = wave.takeLast(40),
                color = RecRed,
                modifier = Modifier.weight(1f).height(26.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onCancel) { Text("取消", color = CardSub) }
            TextButton(onClick = onFinish) { Text("完成") }
        }
    }
}

@Composable
private fun EditSheet(
    title: String,
    initialText: String,
    showDelete: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    var text by remember { mutableStateOf(initialText) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(interactionSource = noRipple, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(330.dp)
                .clickable(interactionSource = noRipple, indication = null) { },
            shape = RoundedCornerShape(20.dp),
            color = CardBg,
            contentColor = CardText,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = CardText)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = TextStyle(color = CardText),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CardText,
                        unfocusedTextColor = CardText,
                        cursorColor = CardText,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showDelete) TextButton(onClick = onDelete) { Text("删除", color = DeleteRed) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消", color = CardSub) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSave(text) }) { Text("保存") }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
}

@Composable
private fun PillButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

@Composable
private fun CapsuleCard(
    colorTag: ColorTag? = null,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.width(CardWidth),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        contentColor = CardText,
        shadowElevation = 3.dp,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (colorTag != null) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(capsuleColorOf(colorTag)))
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
        }
    }
}

private fun fmt(t: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(t))
