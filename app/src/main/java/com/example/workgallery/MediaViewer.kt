package com.example.workgallery

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.Intent
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom


// ==========================================
// 统一媒体查看器 (新增 OCR 与 进阶倍速)
// ==========================================
@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(mediaFiles: List<File>, initialIndex: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // 初始化分页器状态，指定初始页和总页数
    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaFiles.size }

    // 获取当前正在浏览的文件，以便顶部操作栏读取正确的状态 (倍速/OCR)
    val currentFile = mediaFiles[pagerState.currentPage]
    val isVideo = currentFile.extension.lowercase() in setOf("mp4", "avi", "mkv", "mov", "3gp", "webm", "ts")

    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf<String?>(null) }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    var showRemarkDialog by remember { mutableStateOf(false) }
    var remarkTitle by remember { mutableStateOf("") }
    var remarkContent by remember { mutableStateOf("") }

    BackHandler(enabled = true) { onDismiss() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ==========================================
        // 核心滑动区域 (HorizontalPager)
        // ==========================================
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val file = mediaFiles[page]
            val pageIsVideo = file.extension.lowercase() in setOf("mp4", "avi", "mkv", "mov", "3gp", "webm", "ts")

            if (pageIsVideo) {
                // 💡 精准计算：当前渲染的 page 是否刚好是用户正在看的这一页
                val isCurrentPage = (page == pagerState.currentPage)

                // 倍速也只应用给当前页
                val speed = if (isCurrentPage) currentSpeed else 1.0f

                // 传入 isCurrentPage 状态，完美解决黑屏与后台抢资源问题
                EnhancedVideoPlayer(
                    videoFile = file,
                    playbackSpeed = speed,
                    isCurrentPage = isCurrentPage
                )
            } else {
                ZoomableImageView(imageFile = file)
            }
        }

        // ==========================================
        // 悬浮在滑动区域上方的顶部操作栏
        // ==========================================
        Row(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(0.5f), MaterialTheme.shapes.medium)) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }

            // 动态显示：如果滑到了视频页，显示倍速；滑到了图片页，显示 OCR
            if (isVideo) {
                Button(onClick = { showSpeedDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(0.5f))) {
                    Text("${currentSpeed}x")
                }
            } else {
                IconButton(onClick = {
                    val image = InputImage.fromFilePath(context, Uri.fromFile(currentFile))
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                        .addOnSuccessListener { recognizedText = it.text.ifEmpty { "未识别到文字" } }
                }, modifier = Modifier.background(Color.Black.copy(0.5f), MaterialTheme.shapes.medium)) {
                    Icon(Icons.Default.Translate, null, tint = Color.White)
                }
            }
        }

        // OCR 结果展示
        // ==========================================
        // OCR 结果展示对话框 (支持长按复制 & 一键翻译)
        // ==========================================
        recognizedText?.let { text ->
            AlertDialog(
                onDismissRequest = { recognizedText = null },
                title = {
                    // 自定义标题栏结构：左边文字，右边翻译按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("识别结果 (可复制)")

                        // 翻译按钮
                        IconButton(onClick = {
                            try {
                                // 获取手机系统的默认语言代码 (例如：中文是 'zh', 英文是 'en')
                                val targetLang = java.util.Locale.getDefault().language
                                // 构建 Google 翻译的 DeepLink 链接
                                val url = "https://translate.google.com/?sl=auto&tl=$targetLang&text=${Uri.encode(text)}&op=translate"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开翻译", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Translate, contentDescription = "Translate to System Language", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp) // 限制最高高度，防止文字太多撑满全屏
                            .verticalScroll(rememberScrollState())
                            .background(Color.DarkGray.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        // 核心：加上 SelectionContainer 就能让内部的文本支持长按选中和复制了！
                        SelectionContainer {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { recognizedText = null }) {
                        Text("关闭")
                    }
                }
            )
        }

        // 进阶倍速选择对话框
        if (showSpeedDialog) {
            SpeedSelectionDialog(
                currentSpeed = currentSpeed,
                onSpeedSelected = { currentSpeed = it },
                onDismiss = { showSpeedDialog = false }
            )
        }

        // ==========================================
        // 🚀 新增：右下角“更多”悬浮菜单
        // ==========================================
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)) {
            // “更多”按钮 (三个垂直的点)
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.background(Color.Black.copy(0.5f), MaterialTheme.shapes.medium)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
            }

            // 下拉菜单
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("备注 (Remark)") },
                    onClick = {
                        showMoreMenu = false
                        val txtFile = File(currentFile.parent, "${currentFile.nameWithoutExtension}.txt")
                        if (txtFile.exists()) {
                            // Read existing remark if the file exists.
                            // 如果文件存在，则读取现有备注。
                            val lines = txtFile.readLines()
                            remarkTitle = lines.firstOrNull() ?: ""
                            remarkContent = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""
                        } else {
                            // Clear fields for a new remark.
                            // 为新备注清空字段。
                            remarkTitle = ""
                            remarkContent = ""
                        }
                        showRemarkDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("详细信息 (Details)") },
                    onClick = {
                        showMoreMenu = false
                        showDetailsDialog = true
                    }
                )

                // 如果是视频，额外显示“裁剪”选项
                if (isVideo) {
                    DropdownMenuItem(
                        text = { Text("裁剪 (Crop / Trim)") },
                        onClick = {
                            showMoreMenu = false
                            Toast.makeText(context, "视频裁剪属于高级重编码功能，模块已预留！", Toast.LENGTH_SHORT).show()
                            // TODO: 未来在这里接入 Media3 Transformer 的裁剪页面
                        }
                    )
                }
            }
        }

        // ==========================================
        // 🚀 新增：详细信息对话框
        // ==========================================
        if (showDetailsDialog) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val sizeInMb = currentFile.length() / (1024f * 1024f)

            AlertDialog(
                onDismissRequest = { showDetailsDialog = false },
                title = { Text("文件详细信息") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("文件名: ${currentFile.name}", style = MaterialTheme.typography.bodyMedium)
                        Text("类型: ${currentFile.extension.uppercase()}", style = MaterialTheme.typography.bodyMedium)
                        Text("大小: ${String.format("%.2f", sizeInMb)} MB", style = MaterialTheme.typography.bodyMedium)
                        Text("修改时间: ${sdf.format(currentFile.lastModified())}", style = MaterialTheme.typography.bodyMedium)
                        Text("完整路径:\n${currentFile.absolutePath}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDetailsDialog = false }) { Text("确定") }
                }
            )
        }
        if (showRemarkDialog) {
            AlertDialog(
                onDismissRequest = { showRemarkDialog = false },
                title = { Text("媒体备注 (Media Remark)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = remarkTitle,
                            onValueChange = { remarkTitle = it },
                            label = { Text("标题 (Title)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = remarkContent,
                            onValueChange = { remarkContent = it },
                            label = { Text("内容 (Content)") },
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // Save the title and content to the text file.
                            // 将标题和内容保存到文本文件中。
                            val txtFile = File(currentFile.parent, "${currentFile.nameWithoutExtension}.txt")
                            txtFile.writeText("$remarkTitle\n$remarkContent")
                            showRemarkDialog = false
                            Toast.makeText(context, "备注已保存", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("保存 (Save)") }
                },
                dismissButton = {
                    TextButton(onClick = { showRemarkDialog = false }) { Text("取消 (Cancel)") }
                }
            )
        }
    }
}

// ==========================================
// OCR 逻辑实现 (使用 ML Kit)
// ==========================================
private fun runTextRecognition(file: File, context: android.content.Context, callback: (String) -> Unit) {
    val image = InputImage.fromFilePath(context, Uri.fromFile(file))
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            callback(visionText.text.ifEmpty { "No text found 未发现文字" })
        }
        .addOnFailureListener {
            callback("OCR Failed: ${it.message}")
        }
}

// ==========================================
// 进阶倍速对话框 (预设 + 自定义滑动条)
// ==========================================
@Composable
fun SpeedSelectionDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    var customSpeed by remember { mutableFloatStateOf(currentSpeed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed 倍速设置") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 预设按钮组
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                        FilterChip(
                            selected = (currentSpeed == speed),
                            onClick = { onSpeedSelected(speed); onDismiss() },
                            label = { Text("${speed}x") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 自定义滑动条 (0.1 - 2.0, 步长 0.1)
                Text("Custom 自定义: ${String.format("%.1f", customSpeed)}x")
                Slider(
                    value = customSpeed,
                    onValueChange = { customSpeed = (it * 10).roundToInt() / 10f }, // 强制 0.1 步长
                    valueRange = 0.1f..2.0f,
                    steps = 18 // (2.0 - 0.1) / 0.1 - 1 = 18 个断点
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSpeedSelected(customSpeed); onDismiss() }) { Text("Apply 应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel 取消") }
        }
    )
}

// ==========================================
// 视频播放与图片查看器 (保持原有逻辑并加上注解)
// ==========================================
@OptIn(UnstableApi::class)
@Composable
fun EnhancedVideoPlayer(videoFile: File, playbackSpeed: Float, isCurrentPage: Boolean) {
    val context = LocalContext.current

    // 💡 修复 1：加上 videoFile 作为 key，防止 Pager 复用时导致状态错乱
    val exoPlayer = remember(videoFile) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
            prepare()
            // 删除了这里的 playWhenReady = true，把控制权交给下面的状态监听
        }
    }

    // 💡 修复 2：核心焦点控制！只有当前页才播放，非当前页严格暂停
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // 监听倍速变化
    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // 释放资源
    DisposableEffect(videoFile) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ZoomableImageView(imageFile: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val bitmap = remember(imageFile) { BitmapFactory.decodeFile(imageFile.absolutePath) }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    // 🚀 核心重构：弃用无脑的 detectTransformGestures，采用精细化事件分发
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            val newScale = (scale * zoom).coerceIn(1f, 5f)

                            // 判断条件：如果用户正在双指缩放 (zoom != 1f)，或者图片已经是放大状态 (scale > 1f)
                            if (zoom != 1f || scale > 1f) {
                                scale = newScale
                                offset += pan
                                // 【关键】：把事件吃掉 (Consume)，不给 Pager
                                event.changes.forEach { it.consume() }
                            } else {
                                // 图片处于原始大小，且单指滑动 -> 【不吃事件】！把滑动完美让给 HorizontalPager！
                                scale = 1f
                                offset = androidx.compose.ui.geometry.Offset.Zero
                            }
                        }
                    }
                }
        )
    }
}