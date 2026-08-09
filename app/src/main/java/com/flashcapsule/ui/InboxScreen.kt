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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalSplit
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flashcapsule.capture.AudioPlayer
import com.flashcapsule.data.Languages
import com.flashcapsule.model.Capsule
import com.flashcapsule.model.ColorTag
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
    onHandleSideChanged: () -> Unit = {},
    onPickVaultDir: () -> Unit = {},
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
    val apiKey by vm.apiKey.collectAsState()
    val aiError by vm.aiError.collectAsState()
    val handleLeft by vm.handleLeft.collectAsState()
    val filter by vm.filterState.collectAsState()
    val trash by vm.trash.collectAsState()
    var showLangDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showingTrash by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Capsule?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showingTrash) "回收站"
                        else if (versionName != null) "闪念胶囊 v$versionName"
                        else "闪念胶囊"
                    )
                },
                actions = {
                    IconButton(onClick = { showingTrash = !showingTrash }) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = "回收站",
                            tint = if (showingTrash) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!showingTrash) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "点·打字   长按·说话",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    CaptureFab(onTap = onCapture, onLongPress = onVoiceCapture)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (showingTrash) {
                Text(
                    "回收站 · 保留 30 天自动清理",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (trash.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "回收站是空的",
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
                        items(trash, key = { it.id }) { cap ->
                            TrashCard(
                                capsule = cap,
                                onRestore = { vm.restore(cap.id) },
                                onPurge = { vm.permanentDelete(cap.id) },
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索…") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InboxFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { vm.setFilter(f) },
                            label = { Text(f.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
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
                                onToggleDone = { vm.toggleDone(capsule.id) },
                            )
                        }
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

    if (showSettings) {
        SettingsDialog(
            apiKey = apiKey,
            aiError = aiError,
            handleLeft = handleLeft,
            onSave = { vm.setApiKey(it); showSettings = false },
            onToggleHandleSide = {
                vm.toggleHandleSide()
                onHandleSideChanged()
            },
            onPickVaultDir = onPickVaultDir,
            onDismiss = { showSettings = false },
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
            onSaveTitle = { vm.updateTitle(cap.id, it); editing = null },
            onEnrich = { vm.enrich(cap.id) },
            onTogglePin = { vm.togglePin(cap.id) },
            onSetReminder = { t -> vm.setReminder(cap.id, t); scheduleReminder(context, cap, t) },
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
    onToggleDone: () -> Unit,
) {
    val done = capsule.doneAt != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Checkbox(
                checked = done,
                onCheckedChange = { onToggleDone() },
            )
            capsule.colorTag?.let {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(capsuleColorOf(if (done) ColorTag.GRAY else it))
                )
            }
            Column(
                Modifier
                    .padding(12.dp)
                    .graphicsLayer { alpha = if (done) 0.55f else 1f }
            ) {
                val headline = capsule.title.ifBlank { capsule.text }
                if (headline.isNotBlank()) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (capsule.title.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 有标题且与正文不同 → 正文压成两行预览
                    if (capsule.title.isNotBlank() && capsule.text.isNotBlank() && capsule.text != capsule.title) {
                        Text(
                            text = capsule.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
private fun TrashCard(
    capsule: Capsule,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val headline = capsule.title.ifBlank { capsule.text }
                Text(
                    text = if (headline.isNotBlank()) headline else "（空）",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = fmt(capsule.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.Restore, contentDescription = "恢复", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onPurge) {
                Icon(Icons.Filled.DeleteForever, contentDescription = "彻底删除", tint = Color(0xFFD32F2F))
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    apiKey: String,
    aiError: String,
    handleLeft: Boolean,
    onSave: (String) -> Unit,
    onToggleHandleSide: () -> Unit,
    onPickVaultDir: () -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(apiKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                Text(
                    "DeepSeek API Key（可选）：配置后，捕获/转写完成自动生成标题与分类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                )
                if (aiError.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "最近一次 AI 失败：$aiError",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD32F2F),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("把手位置", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !handleLeft,
                        onClick = { if (handleLeft) onToggleHandleSide() },
                        label = { Text("右手") },
                    )
                    FilterChip(
                        selected = handleLeft,
                        onClick = { if (!handleLeft) onToggleHandleSide() },
                        label = { Text("左手") },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Obsidian vault 目录", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onPickVaultDir) {
                    Text("选择目录…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key.trim()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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

/** 调度一次性提醒（AlarmManager → ReminderReceiver）。time=null 取消。 */
private fun scheduleReminder(context: android.content.Context, capsule: Capsule, time: Long?) {
    val am = context.getSystemService(AlarmManager::class.java)
    val intent = Intent(context, com.flashcapsule.capture.ReminderReceiver::class.java)
        .putExtra(com.flashcapsule.capture.ReminderReceiver.EXTRA_ID, capsule.id)
        .putExtra(com.flashcapsule.capture.ReminderReceiver.EXTRA_TITLE, capsule.title.ifBlank { capsule.text }.take(40))
    val pi = PendingIntent.getBroadcast(
        context, capsule.id.hashCode(), intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    if (time == null) {
        am.cancel(pi)
    } else {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
    }
}
