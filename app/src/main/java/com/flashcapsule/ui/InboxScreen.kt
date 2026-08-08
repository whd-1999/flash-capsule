package com.flashcapsule.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flashcapsule.capture.firstSpeechResult
import com.flashcapsule.capture.speechIntent
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
    onToggleOverlay: () -> Unit = {},
    overlayOn: Boolean = false,
) {
    val capsules by vm.capsules.collectAsState()
    val query by vm.search.collectAsState()
    val lang by vm.lang.collectAsState()
    var showLangDialog by remember { mutableStateOf(false) }

    val voice = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data.firstSpeechResult()?.let { if (it.isNotBlank()) vm.captureVoice(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("闪念胶囊") },
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
                CaptureFab(
                    onTap = onCapture,
                    onLongPress = { voice.launch(speechIntent(lang)) },
                )
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
                            onDelete = { vm.delete(capsule.id) },
                            onShare = { vm.export("share", capsule.id) },
                            onExportObsidian = { vm.export("obsidian", capsule.id) },
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
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onExportObsidian: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            val stripe = capsule.colorTag?.let(::colorOf)
            if (stripe != null) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(stripe)
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = capsule.text.ifBlank { "(空)" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fmt(capsule.createdAt) + " · " + capsule.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onExportObsidian) {
                        Icon(Icons.Filled.Description, contentDescription = "落 Obsidian")
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
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
