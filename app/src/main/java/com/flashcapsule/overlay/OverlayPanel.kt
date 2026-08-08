package com.flashcapsule.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
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
import com.flashcapsule.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 真·悬浮面板：作为 overlay window 盖在**当前 App 之上**，不切走用户正在用的应用。
 * 一次性对象——dismiss 后需重新 new。自带最小 Lifecycle/SavedState/ViewModelStore 宿主，
 * 以便在非 Activity 环境里承载 Compose。
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

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
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

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
}

@Composable
private fun PanelContent(
    repo: CaptureRepository,
    onVoice: () -> Unit,
    onText: () -> Unit,
    onDismiss: () -> Unit,
) {
    val flow = remember { repo.observeAll() }
    val capsules by flow.collectAsState(initial = emptyList())

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0x66000000))
                .clickable { onDismiss() }
        )
        Surface(
            modifier = Modifier
                .width(330.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("闪念胶囊", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = onVoice, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("说话")
                    }
                    FilledTonalButton(onClick = onText, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Keyboard, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("打字")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (capsules.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有胶囊", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(capsules.take(30), key = { it.id }) { c ->
                            PanelRow(c)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(capsule: Capsule) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = capsule.text.ifBlank { "(空)" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(capsule.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
