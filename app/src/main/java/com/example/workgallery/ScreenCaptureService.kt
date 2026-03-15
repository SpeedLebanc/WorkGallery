package com.example.workgallery

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

// Add these imports at the top of your file.
// 在你的文件顶部添加这些导入。
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class ScreenCaptureService : Service() {

    companion object {
        var isReady = false
    }

    private var mediaProjection: MediaProjection? = null
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "ACTION_STOP_SERVICE") {
            stopCaptureService()
            return START_NOT_STICKY
        }

        if (action == "ACTION_SHOW_FLOATING_BALL" && isReady) {
            showFloatingBall()
            return START_STICKY
        }

        if (action == "ACTION_CAPTURE_NOW" && isReady) {
            performSingleCapture {}
            return START_STICKY
        }

        if (!isReady) {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, "CaptureChannel")
                .setContentTitle("WorkGallery 截屏待命中")
                .setContentText("悬浮球已准备就绪")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(1, notification)
            }

            val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
            val resultData: Intent? = intent?.getParcelableExtra("RESULT_DATA")

            if (resultCode == Activity.RESULT_OK && resultData != null) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                isReady = true

                // 如果是从主页面开启的悬浮球，直接显示
                if (intent?.getBooleanExtra("SHOW_FLOATING_BALL", false) == true) {
                    showFloatingBall()
                } else {
                    performSingleCapture {}
                }
            } else {
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ==========================================
    // 🚀 新增：绘制与管理全局悬浮球
    // ==========================================
    private fun showFloatingBall() {
        // Prevent duplicate creation.
        // 避免重复创建。
        if (floatingView != null) return

        // Get WindowManager service.
        // 获取 WindowManager 服务。
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Set layout flags based on Android version.
        // 根据 Android 版本设置布局标志。
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Configure layout parameters.
        // 配置布局参数。
        val params = WindowManager.LayoutParams(
            150, 150,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 500
        }

        // Create the floating button.
        // 创建悬浮按钮。
        val button = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setBackgroundColor(Color.parseColor("#80000000"))
            elevation = 10f
        }
        floatingView = button

        // Variables for touch calculation.
        // 用于触摸计算的变量。
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        // Job for the 3-second long press timer.
        // 用于 3 秒长按计时器的任务。
        var longPressJob: kotlinx.coroutines.Job? = null

        // Set the touch listener with the new logic.
        // 使用新逻辑设置触摸监听器。
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Save initial positions.
                    // 保存初始位置。
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    // Start the 3-second timer.
                    // 启动 3 秒计时器。
                    longPressJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(3000)
                        stopCaptureService()
                    }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    // Cancel timer if moved significantly.
                    // 如果发生明显位移，取消计时器。
                    if (kotlin.math.abs(event.rawX - initialTouchX) > 10 || kotlin.math.abs(event.rawY - initialTouchY) > 10) {
                        longPressJob?.cancel()
                    }

                    // Update layout position.
                    // 更新布局位置。
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Cancel timer on release.
                    // 松开手指时取消计时器。
                    longPressJob?.cancel()

                    // Perform click if it was just a tap.
                    // 如果只是轻触，则执行点击。
                    if (kotlin.math.abs(event.rawX - initialTouchX) < 10 && kotlin.math.abs(event.rawY - initialTouchY) < 10) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        // Keep your original click listener logic.
        // 保留你原有的点击监听器逻辑。
        button.setOnClickListener {
            floatingView?.visibility = View.GONE
            Handler(Looper.getMainLooper()).postDelayed({
                performSingleCapture {
                    floatingView?.visibility = View.VISIBLE
                }
            }, 200)
        }

        // Finally, add the view to the screen.
        // 最后，将视图添加到屏幕上。
        windowManager?.addView(floatingView, params)
    }



    private fun performSingleCapture(onComplete: () -> Unit) {
        // Check if media projection is ready.
        // 检查媒体投影是否已准备好。
        if (mediaProjection == null) {
            onComplete()
            return
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        // Increase the delay from 200ms to 800ms for HyperOS compatibility.
        // 为了兼容澎湃 OS，将延迟从 200 毫秒增加到 800 毫秒。
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Attempt to get the latest image.
                // 尝试获取最新的图像。
                val image: Image? = imageReader.acquireLatestImage()

                if (image != null) {
                    // Image is ready, save it.
                    // 图像已准备就绪，保存它。
                    saveImage(image, width, height)
                    image.close()
                    Toast.makeText(this, "✅ 截屏已保存!", Toast.LENGTH_SHORT).show()
                } else {
                    // Show an error if the buffer was too slow.
                    // 如果缓冲区太慢，显示一个错误提示。
                    Toast.makeText(this, "❌ 截屏失败：屏幕缓冲未准备好", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "❌ 截屏发生异常", Toast.LENGTH_SHORT).show()
            } finally {
                // Always clean up resources.
                // 务必清理资源。
                virtualDisplay?.release()
                imageReader.close()
                // Tell the floating ball to reappear.
                // 告诉悬浮球重新出现。
                onComplete()
            }
        }, 800)
    }

    // ... saveImage 保持原来的不变 ...
    private fun saveImage(image: Image, width: Int, height: Int) {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

        val targetDir = File(getExternalFilesDir(null), "WorkAlbums")
        if (!targetDir.exists()) targetDir.mkdirs()
        val file = File(targetDir, "Screenshot_${System.currentTimeMillis()}.png")

        FileOutputStream(file).use { out -> croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    private fun stopCaptureService() {
        isReady = false
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ... createNotificationChannel 保持原来的不变 ...
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CaptureChannel", "截屏服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}