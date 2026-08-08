package com.flashcapsule.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flashcapsule.FlashCapsuleApp
import com.flashcapsule.capture.SpeechCapture
import com.flashcapsule.model.RawCapture
import com.flashcapsule.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 捕获界面（磁贴/助理/主界面长按会带 EXTRA_VOICE=true 进来）。
 * 语音走 SpeechRecognizer 直接收音，不跳系统语音界面。
 */
class CaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = FlashCapsuleApp.from(this)
        val repo = app.repository
        val langCode = app.settings.sttLanguage
        val voiceMode = intent.getBooleanExtra(EXTRA_VOICE, false)

        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                CaptureScreen(
                    voiceMode = voiceMode,
                    langCode = langCode,
                    onSave = { text ->
                        if (text.isNotBlank()) {
                            scope.launch {
                                repo.ingest(
                                    RawCapture(text = text, source = if (voiceMode) "voice" else "app")
                                )
                                finish()
                            }
                        } else finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_VOICE = "voice"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureScreen(
    voiceMode: Boolean,
    langCode: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    val speech = remember { SpeechCapture(context) }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    fun listen(finishOnResult: Boolean) {
        partial = ""
        listening = true
        speech.start(
            langCode = langCode,
            onPartial = { partial = it },
            onFinal = { t ->
                listening = false
                if (finishOnResult) {
                    onSave(t)
                } else if (t.isNotBlank()) {
                    text = if (text.isBlank()) t else "$text $t"
                }
            },
            onError = { msg -> listening = false; toast = msg },
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) listen(voiceMode) else toast = "需要麦克风权限才能语音"
    }

    fun ensureMicThenListen(finishOnResult: Boolean) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) listen(finishOnResult) else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) { if (voiceMode) ensureMicThenListen(true) }
    LaunchedEffect(toast) { if (toast != null) { delay(2500); toast = null } }

    Scaffold(topBar = { TopAppBar(title = { Text("闪念捕获") }) }) { padding ->
        if (voiceMode) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (listening) {
                        CircularProgressIndicator()
                        Spacer(Modifier.padding(top = 16.dp))
                        Text(partial.ifBlank { "聆听中…" }, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.padding(top = 16.dp))
                        Button(onClick = { speech.stop() }) { Text("停止") }
                    } else {
                        Text(partial.ifBlank { "准备就绪" }, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.padding(top = 16.dp))
                        Button(onClick = { ensureMicThenListen(true) }) { Text("重新说话") }
                    }
                    toast?.let {
                        Spacer(Modifier.padding(top = 12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.padding(top = 8.dp))
                    TextButton(onClick = onCancel) { Text("取消") }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = { Text("说点什么，或直接打字…") },
                )
                if (listening) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(partial.ifBlank { "聆听中…" }, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { speech.stop() }) { Text("停止") }
                    }
                }
                toast?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = { ensureMicThenListen(false) }) {
                        Icon(Icons.Filled.Mic, contentDescription = "语音", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("语音")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("取消") }
                    Button(onClick = { onSave(text) }) { Text("存") }
                }
            }
        }
    }
}
