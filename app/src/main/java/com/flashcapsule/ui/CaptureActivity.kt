package com.flashcapsule.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flashcapsule.FlashCapsuleApp
import com.flashcapsule.capture.AudioRecorder
import com.flashcapsule.data.CaptureRepository
import com.flashcapsule.data.FileStore
import com.flashcapsule.model.RawCapture
import com.flashcapsule.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 捕获界面。voiceMode = 录音（存音频 + 波形，对齐原版）；否则文字输入。
 * 磁贴 / 数字助理 / 主界面长按都带 EXTRA_VOICE=true 进来。
 */
class CaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = FlashCapsuleApp.from(this)
        val repo = app.repository
        val voiceMode = intent.getBooleanExtra(EXTRA_VOICE, false)

        setContent {
            AppTheme {
                if (voiceMode) {
                    RecordVoiceScreen(repo = repo, onDone = { finish() })
                } else {
                    TextCaptureScreen(repo = repo, onDone = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_VOICE = "voice"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordVoiceScreen(repo: CaptureRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { AudioRecorder(context) }
    val fileStore = remember { FileStore(context) }

    var recording by remember { mutableStateOf(false) }
    var wave by remember { mutableStateOf(listOf<Int>()) }
    var recFile by remember { mutableStateOf<java.io.File?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    fun start() {
        val f = fileStore.newAudioFile(UUID.randomUUID().toString())
        try {
            recorder.start(f); recFile = f; wave = emptyList(); recording = true
        } catch (e: Exception) { toast = "录音启动失败" }
    }

    fun finishRec() {
        val f = recorder.stop(); recording = false
        if (f != null && wave.isNotEmpty()) {
            val samples = wave
            scope.launch {
                repo.ingest(RawCapture(audioPath = f.absolutePath, waveform = samples, source = "voice"))
                onDone()
            }
        } else onDone()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) start() else { toast = "需要麦克风权限"; } }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) start() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(recording) { while (recording) { wave = wave + recorder.amplitude(); delay(80) } }

    Scaffold(topBar = { TopAppBar(title = { Text("闪念录音") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(0xFFE53935), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(if (recording) "录音中…" else "准备中…", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))
            Waveform(
                samples = wave.takeLast(60),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(60.dp),
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { recorder.cancel(); recording = false; onDone() }) { Text("取消") }
                Button(onClick = { finishRec() }) { Text("完成") }
            }
            toast?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextCaptureScreen(repo: CaptureRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("闪念捕获") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("写点什么…") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("取消") }
                Button(onClick = {
                    if (text.isNotBlank()) {
                        scope.launch { repo.ingest(RawCapture(text = text, source = "app")); onDone() }
                    } else onDone()
                }) { Text("存") }
            }
        }
    }
}

@Composable
private fun Waveform(samples: List<Int>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val maxA = (samples.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val n = samples.size
        val slot = size.width / n
        val barW = (slot * 0.55f).coerceAtLeast(2f)
        val midY = size.height / 2f
        samples.forEachIndexed { i, a ->
            val h = (a / maxA) * size.height * 0.9f
            val x = i * slot + slot / 2f
            drawLine(color, Offset(x, midY - h / 2f), Offset(x, midY + h / 2f), strokeWidth = barW, cap = StrokeCap.Round)
        }
    }
}
