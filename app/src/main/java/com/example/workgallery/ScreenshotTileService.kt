package com.example.workgallery

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class ScreenshotTileService : TileService() {
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()

        // We construct the intent exactly like we did before.
        // 我们像以前一样构建意图。
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (ScreenCaptureService.isReady) {
                // Send silent capture command if authorized.
                // 如果已授权，发送静默截屏指令。
                putExtra("ACTION_SILENT_CAPTURE_NOW", true)
            } else {
                // Send first-time authorization command.
                // 发送首次授权指令。
                putExtra("ACTION_TAKE_SCREENSHOT", true)
            }
        }

        // Create a secure PendingIntent with immutable flags.
        // 创建一个带有不可变标志的安全 PendingIntent。
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

        // Check the Android API level to use the correct method.
        // 检查 Android API 级别以使用正确的方法。
        if (Build.VERSION.SDK_INT >= 34) {
            // Use the modern approach for Android 14 and above.
            // 对于 Android 14 及更高版本使用现代方法。
            startActivityAndCollapse(pendingIntent)
        } else {
            // Suppress the warning and use the old method for older devices.
            // 抑制警告并为旧设备使用旧方法。
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onStartListening() {
        super.onStartListening()

        // Get the current tile reference.
        // 获取当前磁贴的引用。
        val tile = qsTile

        // Update appearance based on service state.
        // 根据服务状态更新外观。
        if (ScreenCaptureService.isReady) {
            tile.state = android.service.quicksettings.Tile.STATE_ACTIVE
            tile.label = "关闭悬浮球"
        } else {
            tile.state = android.service.quicksettings.Tile.STATE_INACTIVE
            tile.label = "开启悬浮球"
        }

        // Apply the UI update.
        // 应用 UI 更新。
        tile.updateTile()
    }
}