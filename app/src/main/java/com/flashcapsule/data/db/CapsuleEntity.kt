package com.flashcapsule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flashcapsule.model.Capsule
import com.flashcapsule.model.CapsuleStatus
import com.flashcapsule.model.ColorTag

@Entity(tableName = "capsules")
data class CapsuleEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val text: String,
    val audioPath: String?,
    val status: String,
    val colorTag: String?,
    val tags: String,
    val source: String,
    val reminderAt: Long?,
    val pinned: Boolean,
    val waveform: String = "",
)

fun CapsuleEntity.toModel(): Capsule = Capsule(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    text = text,
    audioPath = audioPath,
    status = runCatching { CapsuleStatus.valueOf(status) }.getOrDefault(CapsuleStatus.CAPTURED),
    colorTag = colorTag?.let { runCatching { ColorTag.valueOf(it) }.getOrNull() },
    tags = if (tags.isBlank()) emptyList() else tags.split(","),
    source = source,
    reminderAt = reminderAt,
    pinned = pinned,
    waveform = if (waveform.isBlank()) emptyList()
    else waveform.split(",").mapNotNull { it.toIntOrNull() },
)

fun Capsule.toEntity(): CapsuleEntity = CapsuleEntity(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    text = text,
    audioPath = audioPath,
    status = status.name,
    colorTag = colorTag?.name,
    tags = tags.joinToString(","),
    source = source,
    reminderAt = reminderAt,
    pinned = pinned,
    waveform = waveform.joinToString(","),
)
