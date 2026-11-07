package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.example.data.model.ActivityLog
import com.example.data.repository.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object CaptureManager {
    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    private val _autoUpload = MutableStateFlow(true)
    val autoUpload: StateFlow<Boolean> = _autoUpload.asStateFlow()

    private val _vibrateOnCapture = MutableStateFlow(true)
    val vibrateOnCapture: StateFlow<Boolean> = _vibrateOnCapture.asStateFlow()

    private val _latestCapturedLog = MutableStateFlow<ActivityLog?>(null)
    val latestCapturedLog: StateFlow<ActivityLog?> = _latestCapturedLog.asStateFlow()

    private val _captureEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val captureEvents: SharedFlow<String> = _captureEvents.asSharedFlow()

    // Holds MediaProjection permission intent data from Activity
    var projectionResultCode: Int = 0
    var projectionData: Intent? = null

    fun setOverlayActive(active: Boolean) {
        _isOverlayActive.value = active
    }

    fun setAutoUpload(enabled: Boolean) {
        _autoUpload.value = enabled
    }

    fun setVibrateOnCapture(enabled: Boolean) {
        _vibrateOnCapture.value = enabled
    }

    fun onScreenshotCaptured(context: Context, bitmap: Bitmap, tag: String = "Quotex Chart", notes: String = "Instant Floating Capture") {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = ScreenshotRepository(context)
            val log = repo.saveBitmap(bitmap, tag = tag, notes = notes)
            _latestCapturedLog.value = log
            _captureEvents.tryEmit("Captured: ${log.fileName}")

            if (_autoUpload.value) {
                repo.uploadToCandleMindset(log)
            }
        }
    }
}
