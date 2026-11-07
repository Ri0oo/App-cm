package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityLog
import com.example.data.model.UploadStatus
import com.example.data.repository.ScreenshotRepository
import com.example.service.CaptureManager
import com.example.service.FloatingCaptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScreenshotRepository(application)

    val logs: StateFlow<List<ActivityLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOverlayActive: StateFlow<Boolean> = CaptureManager.isOverlayActive
    val autoUpload: StateFlow<Boolean> = CaptureManager.autoUpload
    val vibrateOnCapture: StateFlow<Boolean> = CaptureManager.vibrateOnCapture

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _webUrl = MutableStateFlow("https://web-candle-mindset-1.onrender.com/")
    val webUrl: StateFlow<String> = _webUrl.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _pendingWebUploadUri = MutableStateFlow<Uri?>(null)
    val pendingWebUploadUri: StateFlow<Uri?> = _pendingWebUploadUri.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setAutoUpload(enabled: Boolean) {
        CaptureManager.setAutoUpload(enabled)
    }

    fun setVibrate(enabled: Boolean) {
        CaptureManager.setVibrateOnCapture(enabled)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setPendingWebUploadUri(uri: Uri?) {
        _pendingWebUploadUri.value = uri
    }

    fun startFloatingService(resultCode: Int, data: Intent) {
        val context = getApplication<Application>()
        CaptureManager.projectionResultCode = resultCode
        CaptureManager.projectionData = data

        val serviceIntent = Intent(context, FloatingCaptureService::class.java).apply {
            action = FloatingCaptureService.ACTION_START
            putExtra(FloatingCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingCaptureService.EXTRA_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        _statusMessage.value = "Floating screenshot icon activated"
    }

    fun stopFloatingService() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, FloatingCaptureService::class.java).apply {
            action = FloatingCaptureService.ACTION_STOP
        }
        context.startService(serviceIntent)
        _statusMessage.value = "Floating screenshot icon stopped"
    }

    fun captureInAppScreenshot(view: View) {
        viewModelScope.launch {
            _isCapturing.value = true
            try {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                view.draw(canvas)

                val log = repository.saveBitmap(
                    bitmap = bitmap,
                    tag = "In-App Chart",
                    notes = "Captured user activity log in Candle Mindset"
                )

                _statusMessage.value = "Captured screenshot: ${log.fileName}"

                if (autoUpload.value) {
                    uploadScreenshot(log)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Capture failed: ${e.message}"
            } finally {
                _isCapturing.value = false
            }
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch {
            val log = repository.importFromUri(uri)
            if (log != null) {
                _statusMessage.value = "Imported screenshot: ${log.fileName}"
                if (autoUpload.value) {
                    uploadScreenshot(log)
                }
            } else {
                _statusMessage.value = "Failed to import selected image"
            }
        }
    }

    fun uploadScreenshot(log: ActivityLog) {
        viewModelScope.launch {
            _statusMessage.value = "Uploading ${log.fileName} to Candle Mindset..."
            val result = repository.uploadToCandleMindset(log, _webUrl.value)
            if (result.isSuccess) {
                _statusMessage.value = "Uploaded: ${result.getOrNull()}"
            } else {
                _statusMessage.value = "Upload note: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun updateNotes(id: String, notes: String, tag: String) {
        viewModelScope.launch {
            repository.updateNotes(id, notes, tag)
            _statusMessage.value = "Updated activity log details"
        }
    }

    fun deleteLog(log: ActivityLog) {
        viewModelScope.launch {
            repository.deleteLog(log)
            _statusMessage.value = "Deleted log: ${log.fileName}"
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAll()
            _statusMessage.value = "Cleared all activity logs"
        }
    }
}
