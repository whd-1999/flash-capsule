package com.flashcapsule.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.flashcapsule.data.CaptureRepository
import com.flashcapsule.model.Capsule
import com.flashcapsule.model.ColorTag
import com.flashcapsule.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 真·悬浮面板：全屏暗色遮罩 + 从右侧堆叠的独立"胶囊"卡片，盖在当前 App 之上。
 * 一次性对象，dismiss 后需重新 new。
 */
class OverlayPanel(
    private val context: Context,
    private val repo: CaptureRepository,
    private val onVoice: () -> Unit,
    private val onText: () -> Unit,
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
                AppTheme {
                    PanelContent(
                        repo = repo,
                        onVoice = { onVoice(); dismiss() },
                        onText = { onText(); dismiss() },
                        onDismiss = { dismiss() },
                    )
                }
            }
        }

        val (w, h) = fullScreenSize()
        val flags = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val lp = WindowManager.LayoutParams(w, h, overlayType(), flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
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
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
}

private val CardWidth = 300.dp
private val ScrimColor = Color(0x99000000)   // ~60% 黑，较浅
private val CardBg = Color(0xFFF7F5FB)
private val CardText = Color(0xFF1C1B1F)
private val CardSub = Color(0xFF6B6673)
private val DeleteRed = Color(0xFFD32F2F)

@Composable
private fun PanelContent(
    repo: CaptureRepository,
    onVoice: () -> Unit,
    onText: () -> Unit,
    onDismiss: () -> Unit,
) {
    val flow = remember { repo.observeAll() }
    val capsules by flow.collectAsState(initial = emptyList())
    val noRipple = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Capsule?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                .clickable(interactionSource = noRipple, indication = null) { onDismiss() }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .padding(top = 52.dp, end = 12.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "闪念胶囊",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(end = 4.dp, bottom = 10.dp),
            )

            if (capsules.isEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    CapsuleCard(onClick = {}) {
                        Text(
                            "还没有胶囊 —— 点下面「说话/打字」记一条",
                            color = CardSub,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    items(capsules.take(50), key = { it.id }) { c ->
                        CapsuleCard(colorTag = c.colorTag, onClick = { editing = c }) {
                            Text(
                                text = c.text.ifBlank { "(空)" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = CardText,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = fmt(c.createdAt) + " · " + c.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = CardSub,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("说话", Icons.Filled.Mic, onVoice)
                PillButton("打字", Icons.Filled.Keyboard, onText)
            }
        }

        editing?.let { cap ->
            EditSheet(
                capsule = cap,
                onSave = { newText ->
                    scope.launch { repo.updateText(cap.id, newText) }
                    editing = null
                },
                onDelete = {
                    scope.launch { repo.delete(cap.id) }
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun EditSheet(
    capsule: Capsule,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    var text by remember(capsule.id) { mutableStateOf(capsule.text) }
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
                .clickable(interactionSource = noRipple, indication = null) { /* 消费 */ },
            shape = RoundedCornerShape(20.dp),
            color = CardBg,
            contentColor = CardText,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("编辑胶囊", style = MaterialTheme.typography.titleMedium, color = CardText)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
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
                    TextButton(onClick = onDelete) { Text("删除", color = DeleteRed) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消", color = CardSub) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSave(text) }) { Text("保存") }
                }
            }
        }
    }

    LaunchedEffect(capsule.id) {
        focus.requestFocus()
        keyboard?.show()
    }
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
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
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
        shape = RoundedCornerShape(22.dp),
        color = CardBg,
        contentColor = CardText,
        shadowElevation = 4.dp,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (colorTag != null) {
                Box(
                    Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(colorOf(colorTag))
                )
            }
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    }
}

private fun colorOf(tag: ColorTag): Color = when (tag) {
    ColorTag.RED -> Color(0xFFE53935)
    ColorTag.ORANGE -> Color(0xFFFB8C00)
    ColorTag.YELLOW -> Color(0xFFFDD835)
    ColorTag.GREEN -> Color(0xFF43A047)
    ColorTag.BLUE -> Color(0xFF1E88E5)
    ColorTag.PURPLE -> Color(0xFF8E24AA)
    ColorTag.GRAY -> Color(0xFF9E9E9E)
}

private fun fmt(t: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(t))
