package com.example.workgallery

// System permissions and core OS components. 系统权限和核心操作系统组件。
import android.Manifest // Used to verify if the user granted microphone access. 用于验证用户是否授予了麦克风访问权限。
import android.annotation.SuppressLint // Used to suppress specific compiler warnings (like the touch listener warning). 用于抑制特定的编译器警告（比如触摸监听器警告）。
import android.content.Context // Represents the global environment of the Android application. 代表 Android 应用程序的全局环境。
import android.content.pm.PackageManager // Used to check permission grant status. 用于检查权限授予状态。
import android.widget.Toast // Used to show quick, temporary text pop-ups to the user. 用于向用户显示快速、临时的文本弹出窗口。

// CameraX libraries for interacting with device camera hardware. CameraX 库，用于与设备相机硬件交互。
import androidx.camera.core.* // Contains core CameraX classes like Camera, Preview, and ImageCapture. 包含核心的 CameraX 类，如 Camera、Preview 和 ImageCapture。
import androidx.camera.lifecycle.ProcessCameraProvider // Manages the camera lifecycle, tying it to the activity/fragment. 管理相机生命周期，将其与活动/片段绑定。
import androidx.camera.video.* // Contains classes for video recording like Recorder, VideoCapture, and Recording. 包含用于视频录制的类，如 Recorder、VideoCapture 和 Recording。
import androidx.camera.view.PreviewView // A custom View specifically designed to display the camera feed. 专门设计用于显示相机画面的自定义视图。

// Jetpack Compose libraries for building the declarative UI. Jetpack Compose 库，用于构建声明式用户界面。
import androidx.compose.foundation.background // Used to draw a background color behind a UI element. 用于在 UI 元素后面绘制背景颜色。
import androidx.compose.foundation.gestures.detectTransformGestures // Used to detect complex touch gestures like pinch-to-zoom. 用于检测复杂的触摸手势，如双指缩放。
import androidx.compose.foundation.layout.* // Contains layout components like Box, Row, Column, and Spacer. 包含诸如 Box、Row、Column 和 Spacer 之类的布局组件。
import androidx.compose.material.icons.Icons // Provides access to Material Design icons. 提供对 Material Design 图标的访问。
import androidx.compose.material.icons.automirrored.filled.ArrowBack // The back arrow icon. 返回箭头图标。
import androidx.compose.material.icons.filled.CameraAlt // The camera icon for taking photos. 用于拍照的相机图标。
import androidx.compose.material.icons.filled.Videocam // The video camera icon for recording. 用于录制的摄像机图标。
import androidx.compose.material3.* // Material 3 UI components (Buttons, Texts, Themes). Material 3 UI 组件（按钮、文本、主题）。
import androidx.compose.runtime.* // State management tools like remember and mutableStateOf. 状态管理工具，如 remember 和 mutableStateOf。
import androidx.compose.ui.Alignment // Used to position elements inside layouts (e.g., Center, BottomCenter). 用于在布局内定位元素（例如，居中、底部居中）。
import androidx.compose.ui.Modifier // Used to modify size, padding, and behavior of UI elements. 用于修改 UI 元素的大小、内边距和行为。
import androidx.compose.ui.graphics.Color // Used to define colors in the UI. 用于在 UI 中定义颜色。
import androidx.compose.ui.input.pointer.pointerInput // Used to handle raw pointer/touch events. 用于处理原始的指针/触摸事件。
import androidx.compose.ui.platform.LocalContext // Used to get the Android Context inside a Compose function. 用于在 Compose 函数内获取 Android 上下文。
import androidx.compose.ui.unit.dp // Represents density-independent pixels for sizing. 代表用于设置大小的与密度无关的像素。
import androidx.compose.ui.viewinterop.AndroidView // Crucial for embedding traditional Android Views (like PreviewView) inside Compose. 对于在 Compose 中嵌入传统 Android 视图（如 PreviewView）至关重要。

// Helper libraries for OS checks and file management. 用于操作系统检查和文件管理的辅助库。
import androidx.core.app.ActivityCompat // Used to safely check permissions across different Android versions. 用于在不同的 Android 版本中安全地检查权限。
import androidx.core.content.ContextCompat // Used to retrieve executors and resources. 用于检索执行器和资源。
import androidx.lifecycle.compose.LocalLifecycleOwner // Provides the lifecycle owner required by CameraX. 提供 CameraX 所需的生命周期所有者。
import java.io.File // Represents a file path in the storage. 代表存储中的文件路径。
import java.text.SimpleDateFormat // Used to format the current date/time into a string. 用于将当前日期/时间格式化为字符串。
import java.util.Locale // Defines geographical or cultural formatting rules. 定义地理或文化格式规则。

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraScreen(
    targetDirectory: File,
    onCloseCamera: () -> Unit,
    onMediaCaptured: () -> Unit
) {
    // Get the environment context. 获取环境上下文。
    val context = LocalContext.current
    // Get the lifecycle owner to bind the camera to it. 获取生命周期所有者以将相机绑定到它。
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize camera resources (only exists when this screen is active). 初始化相机资源 (仅在此界面存在时)。
    // Build the image capture use case for taking high-quality photos. 构建用于拍摄高质量照片的图像捕获用例。
    val imageCapture = remember { ImageCapture.Builder().build() }
    // Build the video recorder with the highest quality setting. 构建具有最高质量设置的视频录制器。
    val recorder = remember { Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build() }
    // Attach the recorder to the video capture use case. 将录制器附加到视频捕获用例。
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    // State holding the active camera instance to control features like zoom. 保存活动相机实例的状态，以控制缩放等功能。
    var camera by remember { mutableStateOf<Camera?>(null) }
    // State holding the active video recording session. 保存活动视频录制会话的状态。
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    // State holding the elapsed recording time in seconds. 保存已过去录制时间（秒）的状态。
    var recordingSeconds by remember { mutableIntStateOf(0) }

    // Recording timer effect. 录制计时器副作用。
    LaunchedEffect(currentRecording) {
        // If a recording is actively running. 如果录制正在进行中。
        if (currentRecording != null) {
            recordingSeconds = 0
            while (true) {
                // Wait for one second. 等待一秒。
                kotlinx.coroutines.delay(1000L)
                // Increment the counter. 增加计数器。
                recordingSeconds++
            }
        } else {
            // Reset counter when recording stops. 录制停止时重置计数器。
            recordingSeconds = 0
        }
    }

    // Main container filling the entire screen. 填满整个屏幕的主容器。
    Box(modifier = Modifier.fillMaxSize()) {

        // Camera preview view using traditional AndroidView. 使用传统 AndroidView 的相机预览视图。
        AndroidView(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                // Detect pinch-to-zoom gestures. 检测双指缩放手势。
                detectTransformGestures { _, _, zoom, _ ->
                    camera?.let {
                        // Calculate the new zoom ratio based on user gesture. 根据用户手势计算新的缩放比例。
                        val currentZoom = it.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        // Apply the zoom to the camera control. 将缩放应用于相机控制。
                        it.cameraControl.setZoomRatio(currentZoom * zoom)
                    }
                }
            },
            factory = { ctx ->
                // Create the physical preview view instance. 创建物理预览视图实例。
                val previewView = PreviewView(ctx)
                // Request the camera provider asynchronously. 异步请求相机提供程序。
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                // Add a listener that triggers when the provider is ready. 添加一个在提供程序准备就绪时触发的监听器。
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    // Build the preview use case and connect it to the view's surface. 构建预览用例并将其连接到视图的表面。
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    try {
                        // Unbind any previous use cases before binding new ones. 在绑定新用例之前，取消绑定任何先前的用例。
                        cameraProvider.unbindAll()
                        // Bind the lifecycle, camera selector (back camera), and use cases (preview, photo, video). 绑定生命周期、相机选择器（后置相机）和用例（预览、照片、视频）。
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, videoCapture
                        )
                    } catch (exc: Exception) {
                        // Catch and print any initialization errors. 捕获并打印任何初始化错误。
                        println("Camera error: ${exc.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx)) // Execute on the main thread. 在主线程上执行。

                // Return the initialized view to Compose. 将初始化后的视图返回给 Compose。
                previewView
            }
        )

        // Recording time display (with red dot blinking). 录制时间显示 (带红点闪烁)。
        if (currentRecording != null) {
            // Format seconds into MM:SS format. 将秒格式化为 MM:SS 格式。
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)

            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                    .background(Color.Black.copy(0.6f), MaterialTheme.shapes.large).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Blink the red dot every alternate second. 每隔一秒闪烁一次红点。
                    if (recordingSeconds % 2 == 0) {
                        Box(modifier = Modifier.size(10.dp).background(Color.Red, androidx.compose.foundation.shape.CircleShape))
                    } else {
                        // Leave empty space when dot is hidden to prevent UI shifting. 当点隐藏时留出空白空间，以防止 UI 移动。
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = formattedTime, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Bottom control buttons. 底部控制按钮。
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close camera button. 关闭相机按钮。
            FloatingActionButton(onClick = onCloseCamera) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(32.dp))

            // Take photo button (larger size). 拍照按钮（尺寸更大）。
            LargeFloatingActionButton(onClick = { takePhoto(context, imageCapture, targetDirectory, onMediaCaptured) }) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Photo", modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.width(32.dp))

            // Record video button. 录制视频按钮。
            FloatingActionButton(
                onClick = {
                    if (currentRecording != null) {
                        // Stop recording if one is active. 如果当前正在录制，则停止录制。
                        currentRecording?.stop()
                        currentRecording = null
                        // Notify that media was captured. 通知媒体已被捕获。
                        onMediaCaptured()
                    } else {
                        // Start a new recording session. 开始一个新的录制会话。
                        currentRecording = captureVideo(context, videoCapture, targetDirectory)
                    }
                }
            ) {
                // Change icon color to red while recording. 录制时将图标颜色更改为红色。
                Icon(Icons.Default.Videocam, contentDescription = "Video", tint = if (currentRecording != null) Color.Red else Color.White)
            }
        }
    }
}

// Private function to handle photo taking. 处理拍照的私有函数。
private fun takePhoto(context: Context, imageCapture: ImageCapture, targetDirectory: File, onComplete: () -> Unit) {
    // Generate a file name based on the current timestamp. 根据当前时间戳生成文件名。
    val file = File(targetDirectory, SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(System.currentTimeMillis()) + ".jpg")

    // Command the ImageCapture use case to take a picture. 命令 ImageCapture 用例拍照。
    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            // Triggered when the photo is successfully saved to storage. 当照片成功保存到存储器时触发。
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Toast.makeText(context, "Photo Saved!", Toast.LENGTH_SHORT).show()
                // Execute the callback function. 执行回调函数。
                onComplete()
            }
            // Triggered if the capture process fails. 如果捕获过程失败则触发。
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(context, "Failed!", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

// Private function to handle video recording. 处理视频录制的私有函数。
private fun captureVideo(context: Context, videoCapture: VideoCapture<Recorder>, targetDirectory: File): Recording {
    // Generate a file name for the MP4 video based on the current timestamp. 根据当前时间戳为 MP4 视频生成文件名。
    val file = File(targetDirectory, SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(System.currentTimeMillis()) + ".mp4")

    // Prepare the recording configuration. 准备录制配置。
    val pendingRecording = videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())

    // Check if the app has permission to record audio via the microphone. 检查应用程序是否有权通过麦克风录制音频。
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
        // Enable audio recording if permission is granted. 如果授予了权限，则启用音频录制。
        pendingRecording.withAudioEnabled()
    }

    // Start the actual recording process. 开始实际的录制过程。
    return pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
        // Listen for the finalize event indicating the video file is completely written. 监听表示视频文件已完全写入的完成事件。
        if (event is VideoRecordEvent.Finalize && !event.hasError()) {
            Toast.makeText(context, "Video Saved!", Toast.LENGTH_SHORT).show()
        }
    }
}