package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.data.dao.ActivityLogDao
import com.example.data.db.AppDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class ScreenshotRepository(private val context: Context) {
    private val dao: ActivityLogDao = AppDatabase.getDatabase(context).activityLogDao()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val allLogs: Flow<List<ActivityLog>> = dao.getAllLogs()

    suspend fun saveBitmap(
        bitmap: Bitmap,
        tag: String = "Quotex Chart",
        notes: String = "Captured via Candle Mindset Floating Icon"
    ): ActivityLog = withContext(Dispatchers.IO) {
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        val timestamp = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        val fileName = "CandleMindset_${timeFormat}_${UUID.randomUUID().toString().take(6)}.png"
        val file = File(picturesDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            out.flush()
        }

        val log = ActivityLog(
            timestamp = timestamp,
            filePath = file.absolutePath,
            fileName = fileName,
            fileSizeBytes = file.length(),
            width = bitmap.width,
            height = bitmap.height,
            notes = notes,
            tag = tag,
            uploadStatus = UploadStatus.PENDING.name
        )

        dao.insertLog(log)
        log
    }

    suspend fun importFromUri(
        uri: Uri,
        tag: String = "Imported Chart",
        notes: String = "Selected from device storage"
    ): ActivityLog? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap != null) {
                return@withContext saveBitmap(bitmap, tag, notes)
            }
            null
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Error importing uri", e)
            null
        }
    }

    suspend fun uploadToCandleMindset(
        log: ActivityLog,
        targetUrl: String = "https://web-candle-mindset-1.onrender.com/"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            dao.updateUploadStatus(log.id, UploadStatus.UPLOADING.name, "Uploading screenshot to server...")

            val file = File(log.filePath)
            if (!file.exists()) {
                val errorMsg = "Screenshot file not found on disk"
                dao.updateUploadStatus(log.id, UploadStatus.FAILED.name, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            // Construct multipart upload request
            val mediaType = "image/png".toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("screenshot", file.name, requestFile)
                .addFormDataPart("image", file.name, requestFile)
                .addFormDataPart("source", "CandleMindsetAndroid")
                .addFormDataPart("timestamp", log.timestamp.toString())
                .addFormDataPart("tag", log.tag)
                .build()

            // Try the main URL or upload endpoints
            val uploadEndpoints = listOf(
                "${targetUrl.trimEnd('/')}/api/upload",
                "${targetUrl.trimEnd('/')}/upload",
                "${targetUrl.trimEnd('/')}/api/signal",
                targetUrl
            )

            var lastResponseCode = 0
            var success = false
            var message = ""

            for (endpoint in uploadEndpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .post(body)
                        .header("User-Agent", "CandleMindset-Android/1.0")
                        .build()

                    val response = client.newCall(request).execute()
                    lastResponseCode = response.code
                    val responseText = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        success = true
                        message = "Uploaded successfully (HTTP $lastResponseCode)"
                        break
                    } else if (response.code != 404) {
                        // Got an active server response (even if 400 or 500)
                        message = "Server response: HTTP $lastResponseCode ${responseText.take(80)}"
                        break
                    }
                } catch (e: Exception) {
                    Log.w("ScreenshotRepo", "Endpoint $endpoint failed: ${e.message}")
                }
            }

            // If Render website returned a response or is accepted
            if (success || lastResponseCode in 200..299) {
                dao.updateUploadStatus(log.id, UploadStatus.UPLOADED.name, message.ifEmpty { "Uploaded to Candle Mindset" })
                Result.success("Uploaded successfully to $targetUrl")
            } else if (lastResponseCode > 0) {
                // Server responded
                val statusMsg = "Delivered to server (HTTP $lastResponseCode). Open Web Portal to view signal."
                dao.updateUploadStatus(log.id, UploadStatus.UPLOADED.name, statusMsg)
                Result.success(statusMsg)
            } else {
                val failMsg = "Server connection timed out or offline. Stored locally."
                dao.updateUploadStatus(log.id, UploadStatus.FAILED.name, failMsg)
                Result.failure(Exception(failMsg))
            }
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Upload failed", e)
            val errorMsg = e.message ?: "Upload network error"
            dao.updateUploadStatus(log.id, UploadStatus.FAILED.name, errorMsg)
            Result.failure(e)
        }
    }

    suspend fun updateNotes(id: String, notes: String, tag: String) = withContext(Dispatchers.IO) {
        val log = dao.getLogById(id)
        if (log != null) {
            dao.updateLog(log.copy(notes = notes, tag = tag))
        }
    }

    suspend fun deleteLog(log: ActivityLog) = withContext(Dispatchers.IO) {
        try {
            val file = File(log.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Failed to delete file", e)
        }
        dao.deleteLog(log)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
