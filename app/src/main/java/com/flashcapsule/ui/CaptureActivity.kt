package com.flashcapsule.ui

import android.app.Activity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flashcapsule.FlashCapsuleApp
import com.flashcapsule.capture.firstSpeechResult
import com.flashcapsule.capture.speechIntent
import com.flashcapsule.model.RawCapture
import com.flashcapsule.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 捕获界面。
 * - voice 模式（磁贴/助理/长按）：进来即录，结果直存即退，步骤最少。
 * - text 模式（主界面点按）：完整编辑，附带语音按钮。
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
    var text by remember { mutableStateOf("") }

    val speech = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = if (result.resultCode == Activity.RESULT_OK) {
            result.data.firstSpeechResult()
        } else null
        if (voiceMode) {
            if (!spoken.isNullOrBlank()) onSave(spoken) else onCancel()
        } else if (!spoken.isNullOrBlank()) {
            text = if (text.isBlank()) spoken else "$text $spoken"
        }
    }

    LaunchedEffect(Unit) {
        if (voiceMode) speech.launch(speechIntent(langCode))
    }

    if (voiceMode) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.width(0.dp))
                Text("聆听中…", modifier = Modifier.padding(top = 16.dp))
            }
        }
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("闪念捕获") }) }
    ) { padding ->
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = { speech.launch(speechIntent(langCode)) }) {
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
