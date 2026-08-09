package com.flashcapsule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 胶囊附件（对齐原版 1.0：图片/文件，≤14 个/条）。uri 存 app 私有目录的 file://。 */
@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val capsuleId: String,
    val uri: String,
    val mime: String?,
    val sizeBytes: Long,
    val createdAt: Long,
)
