package com.example.smartautoclicker

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCaptureNotification()
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2
        )
        reader?.setOnImageAvailableListener({ imageReader ->
            var image: Image? = null
            try {
                image = imageReader.acquireLatestImage()
                if (image != null) {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * metrics.widthPixels
                    val wide = Bitmap.createBitmap(
                        metrics.widthPixels + rowPadding / pixelStride,
                        metrics.heightPixels,
                        Bitmap.Config.ARGB_8888
                    )
                    buffer.rewind()
                    wide.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(wide, 0, 0, metrics.widthPixels, metrics.heightPixels)
                    synchronized(lock) {
                        latestBitmap?.recycle()
                        latestBitmap = cropped
                    }
                    wide.recycle()
                }
            } catch (_: Exception) {
                // Ignore transient frame errors; service remains active.
            } finally {
                image?.close()
            }
        }, null)

        display = projection?.createVirtualDisplay(
            "SmartAutoClickerCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            null
        )
        return START_NOT_STICKY
    }

    private fun startCaptureNotification() {
        val channelId = "smart_clicker_capture"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Screen capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setContentTitle("Smart Auto Clicker Pro")
            .setContentText("التقاط الشاشة يعمل بعد موافقتك")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(12, notification)
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        synchronized(lock) {
            latestBitmap?.recycle()
            latestBitmap = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val lock = Any()
        @Volatile var latestBitmap: Bitmap? = null
            private set

        fun findColor(rule: ClickRule): ColorSearchEngine.Match? =
            synchronized(lock) { latestBitmap?.let { ColorSearchEngine.find(it, rule) } }
    }
}
