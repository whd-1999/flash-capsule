package com.flashcapsule.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flashcapsule.capture.AudioPlayer
import com.flashcapsule.data.Languages
import com.flashcapsule.model.Capsule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    vm: InboxViewModel,
    onCapture: () -> Unit,
    onVoiceCapture: () -> Unit = {},
    onToggleOverlay: () -> Unit = {},
    overlayOn: Boolean = false,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
    }
    val capsules by vm.capsules.collectAsState()
    val query by vm.search.collectAsState()
    val lang by vm.lang.collectAsState()
    var showLangDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Capsule?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (versionName != null) "闪念胶囊 v$versionName" else "闪念胶囊") },
                actions = {
                    IconButton(onClick = onToggleOverlay) {
                        Icon(
                            Icons.Filled.VerticalSplit,
                            contentDescription = "侧边把手",
                            tint = if (overlayOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showLangDialog = true }) {
                        Icon(Icons.Filled.Translate, contentDescription = "语音语言")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "点·打字   长按·说话",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                CaptureFab(onTap = onCapture, onLongPress = onVoiceCapture)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索…") },
                singleLine = true,
            )
            if (capsules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有胶囊\n右下角：点打字 · 长按说话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(capsules, key = { it.id }) { capsule ->
                        CapsuleCard(
                            capsule = capsule,
                            playing = playing == capsule.audioPath,
                            onPlay = {
                                capsule.audioPath?.let { p ->
                                    AudioPlayer.toggle(p) { playing = AudioPlayer.currentPath }
                                }
                            },
                            onClick = { editing = capsule },
                        )
                    }
                }
            }
        }
    }

    if (showLangDialog) {
        LanguageDialog(
            current = lang,
            onPick = { vm.setLang(it); showLangDialog = false },
            onDismiss = { showLangDialog = false },
        )
    }

    editing?.let { cap ->
        CapsuleSheet(
            capsule = cap,
            playing = playing == cap.audioPath,
            onPlay = {
                cap.audioPath?.let { p -> AudioPlayer.toggle(p) { playing = AudioPlayer.currentPath } }
            },
            onSetColor = { vm.setColor(cap.id, it) },
            onSaveText = { vm.updateText(cap.id, it); editing = null },
            onDelete = { vm.delete(cap.id); editing = null },
            onShare = { t -> capsuleShareText(context, t); editing = null },
            onCalendar = { t -> capsuleAddToCalendar(context, t); editing = null },
            onObsidian = { vm.export("obsidian", cap.id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CaptureFab(onTap: () -> Unit, onLongPress: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "捕获：点打字，长按说话",
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun CapsuleCard(
    capsule: Capsule,
    playing: Boolean,
    onPlay: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            capsule.colorTag?.let {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(capsuleColorOf(it))
                )
            }
            Column(Modifier.padding(12.dp)) {
                if (capsule.text.isNotBlank()) {
                    Text(
                        text = capsule.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    CapsuleTranscribeHint(capsule, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (capsule.audioPath != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CapsulePlayDot(isPlaying = playing, onClick = onPlay)
                        Spacer(Modifier.width(8.dp))
                        CapsuleWaveform(
                            samples = capsule.waveform,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).height(26.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = fmt(capsule.createdAt) + " · " + capsule.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LanguageDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("语音语言") },
        text = {
            Column {
                Languages.list.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item.code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = item.code == current, onClick = { onPick(item.code) })
                        Spacer(Modifier.width(8.dp))
                        Text(item.label)
                    }
                }
            }
        },
    )
}

private fun fmt(t: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(t))
