package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class UploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val notes: String = "",
    val tag: String = "Quotex Chart",
    val uploadStatus: String = UploadStatus.PENDING.name,
    val uploadedUrl: String? = null,
    val serverMessage: String? = null
)
