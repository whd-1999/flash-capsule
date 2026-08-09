package com.flashcapsule.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flashcapsule.FlashCapsuleApp
import com.flashcapsule.capture.firstSpeechResult
import com.flashcapsule.capture.speechIntent
import com.flashcapsule.model.Capsule
import com.flashcapsule.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 边缘拉出的面板：从右缘滑入，显示最近胶囊 + 快速捕获。点空白处关闭。 */
class PanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = FlashCapsuleApp.from(this)
        setContent {
            AppTheme {
                val vm: InboxViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = InboxViewModel.Factory(app.repository, app.settings)
                )
                EdgePanel(
                    vm = vm,
                    leftHanded = app.settings.handleLeft,
                    onDismiss = { finish() },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdgePanel(
    vm: InboxViewModel,
    leftHanded: Boolean,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val capsules by vm.capsules.collectAsState()
    val lang by vm.lang.collectAsState()
    val visible = remember { mutableStateOf(true) }

    val voice = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data.firstSpeechResult()?.let { if (it.isNotBlank()) vm.captureVoice(it) }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (leftHanded) {
            AnimatedVisibility(
                visible = visible.value,
                enter = slideInHorizontally(initialOffsetX = { -it }),
            ) {
                Surface(
                    modifier = Modifier
                        .width(330.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        EdgePanelContent(
                            onOpenApp = onOpenApp,
                            onSpeak = { voice.launch(speechIntent(lang)) },
                            capsules = capsules,
                        )
                    }
                }
            }
            // 右侧遮罩：点它关闭
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0x66000000))
                    .clickable { onDismiss() }
            )
        } else {
            // 右侧遮罩：点它关闭
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0x66000000))
                    .clickable { onDismiss() }
            )
            AnimatedVisibility(
                visible = visible.value,
                enter = slideInHorizontally(initialOffsetX = { it }),
            ) {
                Surface(
                    modifier = Modifier
                        .width(330.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        EdgePanelContent(
                            onOpenApp = onOpenApp,
                            onSpeak = { voice.launch(speechIntent(lang)) },
                            capsules = capsules,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EdgePanelContent(
    onOpenApp: () -> Unit,
    onSpeak: () -> Unit,
    capsules: List<Capsule>,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("闪念胶囊", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenApp) {
            Icon(Icons.Filled.OpenInNew, contentDescription = "打开主界面")
        }
    }
    Spacer(Modifier.width(0.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onSpeak,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("说话")
        }
        FilledTonalButton(
            onClick = onOpenApp,
            modifier = Modifier.weight(1f),
        ) {
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
