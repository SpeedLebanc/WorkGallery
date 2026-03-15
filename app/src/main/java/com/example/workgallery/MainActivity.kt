package com.example.workgallery

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.compose.foundation.lazy.items
import android.graphics.BitmapFactory
import android.widget.Toast

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable

import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput

import android.app.Activity
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    // Manager for screen recording and media projection.
    // 用于录屏和媒体投影的管理类。
    lateinit var mediaProjectionManager: android.media.projection.MediaProjectionManager

    // ==========================================
    // 1. Overlay Permission Launcher.
    // 1. 请求悬浮窗权限拦截器。
    // ==========================================
    // This handles the result of the system settings request for "Draw over other apps".
    // 这处理了系统设置中“在其他应用上层绘制”请求的结果。
    val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (android.provider.Settings.canDrawOverlays(this)) {
            // If the user allows overlays, we immediately move to request screen capture.
            // 如果用户允许悬浮窗，我们立即转为请求录屏权限。
            mediaProjectionManager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } else {
            // Without this permission, the floating capture bubble cannot be displayed.
            // 没有这个权限，就无法显示截屏悬浮球。
            Toast.makeText(this, "必须授予悬浮窗权限才能显示悬浮球！", Toast.LENGTH_LONG).show()
        }
    }

    // ==========================================
    // 2. Screen Capture Permission Launcher.
    // 2. 请求录屏权限拦截器。
    // ==========================================
    // This processes the result of the "Start recording or casting" system dialog.
    // 这处理了系统“开始录制或投射”对话框的结果。
    val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            // We pass the result data to our Service to manage the background capture.
            // 我们将结果数据传递给服务，以管理后台截图逻辑。
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("RESULT_DATA", data)
                putExtra("SHOW_FLOATING_BALL", true) // Tell the service to show the bubble. 告诉服务显示悬浮球。
            }
            // Starting as a foreground service is required for media projection on modern Android.
            // 在现代 Android 系统上，媒体投影需要作为前台服务启动。
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            // Push the app to the background so the user can see what they want to capture.
            // 将应用推向后台，以便用户看到他们想截取的内容。
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "您拒绝了截屏权限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check for files shared from the system gallery (like ACTION_SEND).
        // 检查从系统相册分享过来的文件（例如 ACTION_SEND）。
        handleSharedFiles(this, intent)
        // Check if the intent contains a command for an immediate screenshot.
        // 检查意图是否包含立即截屏的指令。
        checkScreenshotIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    // systemBarsPadding ensures the content doesn't overlap with the status bar.
                    // systemBarsPadding 确保内容不会与状态栏重叠。
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // 🚀 Core Architecture: Horizontal swipe pages (Defaults to Page 1).
                    // 🚀 核心架构：横向滑动页面 (默认停留在第 1 页)。
                    // We use 3 pages: Schedule, Gallery, and Notepad.
                    // 我们使用 3 个页面：日程、图库和记事本。
                    val pagerState = rememberPagerState(initialPage = 1) { 3 }

                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            // Page 0: The timetable/schedule manager. 页面 0：课表/日程管理器。
                            0 -> { ScheduleScreen() }
                            // Page 1: The main media album viewer. 页面 1：主媒体相册查看器。
                            1 -> { MainGalleryScreen() }
                            // Page 2: The text and image note taker. 页面 2：文本与图片笔记记录器。
                            2 -> { NotepadScreen() }
                        }
                    }
                }
            }
        }
    }

    // This handles cases where the activity is already running and receives a new intent.
    // 这处理了 Activity 已经在运行并接收到新意图的情况。
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedFiles(this, intent)
        checkScreenshotIntent(intent)
    }

    // Logic to interpret special commands from Notification Tiles or shortcuts.
    // 解析来自通知磁贴或快捷方式的特殊指令的逻辑。
    private fun checkScreenshotIntent(intent: Intent?) {
        // Silent capture command: take a screenshot without opening the UI.
        // 静默截屏指令：在不打开 UI 的情况下进行截图。
        if (intent?.getBooleanExtra("ACTION_SILENT_CAPTURE_NOW", false) == true) {
            intent.removeExtra("ACTION_SILENT_CAPTURE_NOW")
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = "ACTION_CAPTURE_NOW"
            }
            startService(serviceIntent)
            moveTaskToBack(true)
            return
        }

        // Authorization command: start the permission request flow.
        // 授权指令：开始权限请求流程。
        if (intent?.getBooleanExtra("ACTION_TAKE_SCREENSHOT", false) == true) {
            intent.removeExtra("ACTION_TAKE_SCREENSHOT")
            mediaProjectionManager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    // ==========================================
    // 🚀 Core Helpers: Functions exposed to Compose UI components.
    // 🚀 核心修复：暴露给 Compose UI 组件的辅助函数。
    // ==========================================

    // Triggers the system settings activity for overlay permission.
    // 触发系统设置活动以获取悬浮窗权限。
    fun requestOverlayPermission() {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    // Triggers the standard system dialog for screen recording.
    // 触发标准的系统录屏对话框。
    fun requestScreenCapture() {
        mediaProjectionManager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    // Commands the background service to display the interactive floating ball.
    // 指令后台服务显示交互式悬浮球。
    fun startFloatingBallService() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = "ACTION_SHOW_FLOATING_BALL"
        }
        startService(serviceIntent)
        moveTaskToBack(true)
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainGalleryScreen() {
    val context = LocalContext.current
    val rootDir = remember { File(context.getExternalFilesDir(null), "WorkAlbums").apply { mkdirs() } }
    var currentDirectory by remember { mutableStateOf(rootDir) }

    // UI 导航状态
    var showCamera by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(false) }
    var viewingMedia by remember { mutableStateOf<File?>(null) }

    // 图库交互状态
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newAlbumName by remember { mutableStateOf("") }

    val allFiles = remember(currentDirectory, refreshTrigger) {
        currentDirectory.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            moveFilesToSafFolder(context, selectedFiles, it)
            selectedFiles = emptySet()
            showMoveDialog = false
            refreshTrigger++
        }
    }

    val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "mp4", "avi", "mkv", "mov", "3gp", "webm", "ts")
    val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "3gp", "webm", "ts")

    val displayItems = allFiles.filter { file ->
        file.isDirectory || file.extension.lowercase() in supportedExtensions
    }

    val directories = displayItems.filter { it.isDirectory }
    val mediaFiles = displayItems.filter { !it.isDirectory }


    // ==========================================
    // 🚀 新增：网格双指捏合缩放控制逻辑
    // ==========================================
    val columnLevels = listOf(3, 5, 8, 16) // 4 个缩放挡位
    var currentColumnIndex by remember { mutableIntStateOf(0) } // 默认在第 0 挡 (即 3 列)
    var zoomAccumulator by remember { mutableFloatStateOf(1f) } // 缩放累加器

    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        zoomAccumulator *= zoomChange
        // 当双指【向外张开】(放大)，并且缩放比例超过 1.25倍 -> 图片变大，列数减少
        if (zoomAccumulator > 1.25f) {
            if (currentColumnIndex > 0) {
                currentColumnIndex--
                zoomAccumulator = 1f // 切换后重置累加器，避免连续乱跳
            }
        }
        // 当双指【向内捏合】(缩小)，并且缩放比例小于 0.8倍 -> 图片变小，列数变多
        else if (zoomAccumulator < 0.8f) {
            if (currentColumnIndex < columnLevels.size - 1) {
                currentColumnIndex++
                zoomAccumulator = 1f // 切换后重置
            }
        }
    }
    // 🚀 新增：嗅探当前是否处于“被第三方 App 唤醒的选择器模式”
    val intentAction = (context as? Activity)?.intent?.action
    val isPickerMode = intentAction == Intent.ACTION_GET_CONTENT || intentAction == Intent.ACTION_PICK

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var fileRemarks by remember { mutableStateOf<Map<File, String>>(emptyMap()) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val systemCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                // Refresh the gallery to show the new photo.
                // 刷新图库以显示新照片。
                refreshTrigger++
                Toast.makeText(context, "照片已保存至图库", Toast.LENGTH_SHORT).show()
            } else {
                // Delete the empty file if the user cancelled the camera.
                // 如果用户取消了相机，则删除空文件。
                pendingCameraFile?.delete()
            }
        }
    )
    // 当目录更改时，异步预加载文本备注。
    LaunchedEffect(currentDirectory, refreshTrigger) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val newRemarks = mutableMapOf<File, String>()
            // Read all txt files in the current directory.
            // 读取当前目录中的所有 txt 文件。
            currentDirectory.listFiles()?.forEach { file ->
                if (file.extension.lowercase() in supportedExtensions) {
                    val txtFile = File(file.parent, "${file.nameWithoutExtension}.txt")
                    if (txtFile.exists()) {
                        newRemarks[file] = txtFile.readText()
                    }
                }
            }
            fileRemarks = newRemarks
        }
    }
    // 根据搜索词过滤媒体文件。
    val filteredMediaFiles = mediaFiles.filter { file ->
        if (searchQuery.isBlank()) return@filter true
        val remark = fileRemarks[file] ?: ""
        file.name.contains(searchQuery, ignoreCase = true) || remark.contains(searchQuery, ignoreCase = true)
    }


    // 对 *过滤后* 的文件进行分组，而不是原始列表。
    val groupedMedia = filteredMediaFiles.groupBy { file ->
        java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()).format(file.lastModified())
    }

    // ==========================================
    // 拦截返回键逻辑
    // ==========================================
    BackHandler(enabled = currentDirectory.absolutePath != rootDir.absolutePath) {
        currentDirectory = currentDirectory.parentFile ?: rootDir
    }
    BackHandler(enabled = showCamera) {
        showCamera = false
    }
    BackHandler(enabled = viewingMedia != null) {
        viewingMedia = null
    }

    // ==========================================
    // 界面路由分发
    // ==========================================
    when {
        viewingMedia != null -> {
            val initialIndex = mediaFiles.indexOf(viewingMedia).coerceAtLeast(0)
            MediaViewerScreen(
                mediaFiles = mediaFiles,
                initialIndex = initialIndex,
                onDismiss = { viewingMedia = null }
            )
        }

        showCamera && hasCameraPermission -> {
            CameraScreen(
                targetDirectory = currentDirectory,
                onCloseCamera = { showCamera = false },
                onMediaCaptured = { refreshTrigger++ }
            )
        }

        // 3. 默认主页：图库网格模式
        else -> {
            // 🚀 核心修复：弃用霸道的 transformable，改用智能双指检测
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            do {
                                val event = awaitPointerEvent()
                                // 💡 只有当屏幕上有两个或以上的手指时，才接管事件（进行缩放）
                                if (event.changes.size >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        zoomAccumulator *= zoomChange
                                        // 放大 -> 列数减少
                                        if (zoomAccumulator > 1.25f) {
                                            if (currentColumnIndex > 0) {
                                                currentColumnIndex--
                                                zoomAccumulator = 1f
                                            }
                                        }
                                        // 缩小 -> 列数增加
                                        else if (zoomAccumulator < 0.8f) {
                                            if (currentColumnIndex < columnLevels.size - 1) {
                                                currentColumnIndex++
                                                zoomAccumulator = 1f
                                            }
                                        }
                                        // 吃掉双指的缩放事件，不让系统误判
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                // 💡 如果是单指，什么都不做，让滑动事件完美穿透给外层的 HorizontalPager！
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ==========================================
                    // 🚀 新增：顶部导航栏与右上角“更多”菜单
                    // ==========================================
                    var showTopMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchActive) {
                            // Render the search text field.
                            // 渲染搜索文本框。
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                placeholder = { Text("搜索名字或备注...") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        isSearchActive = false
                                        searchQuery = ""
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                                    }
                                }
                            )
                        } else {
                            // Render the normal folder title.
                            // 渲染普通的文件夹标题。
                            val titleText = if (currentDirectory.name == rootDir.name) "当前：WorkAlbums" else "当前：WA-${currentDirectory.name}"
                            Text(text = titleText, color = Color.Gray, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }

                        // 右侧：更多按钮与下拉菜单
                        Box {
                            IconButton(onClick = { showTopMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                            }

                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("🔍 搜索备注 (Search Remarks)") },
                                    onClick = {
                                        showTopMenu = false
                                        // Trigger the state to show the Search Bar in your UI.
                                        // 触发状态以在你的 UI 中显示搜索栏。
                                        isSearchActive = true
                                    }
                                )
                                // 菜单项 1：调用系统原相机
                                DropdownMenuItem(
                                    text = { Text(" 开启系统原相机") },
                                    onClick = {
                                        showTopMenu = false
                                        // 工程师做法：发送意图拉起手机自带的相机应用
                                        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "无法启动系统相机", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                // 菜单项 2：截屏磁贴设置引导
                                DropdownMenuItem(
                                    text = { Text("添加下拉截屏按钮") },
                                    onClick = {
                                        showTopMenu = false
                                        Toast.makeText(context, "此功能需注册系统级 TileService，底层已预留！", Toast.LENGTH_LONG).show()
                                        // TODO: 引导用户去系统下拉菜单添加我们的快捷截屏磁贴
                                    }
                                )
                                // 菜单项：开启悬浮球
                                DropdownMenuItem(
                                    text = { Text(" 开启悬浮球截屏") },
                                    onClick = {
                                        showTopMenu = false

                                        // 将当前的 context 强转为 MainActivity，这样才能调用刚才写好的辅助函数
                                        val activity = context as? MainActivity
                                        if (activity != null) {
                                            // 1. 检查悬浮窗权限
                                            if (!android.provider.Settings.canDrawOverlays(activity)) {
                                                activity.requestOverlayPermission()
                                            }
                                            // 2. 检查录屏权限（服务是否已准备好）
                                            else if (!ScreenCaptureService.isReady) {
                                                activity.requestScreenCapture()
                                            }
                                            // 3. 都有了，直接呼叫服务亮出悬浮球
                                            else {
                                                activity.startFloatingBallService()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // ==========================================

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnLevels[currentColumnIndex]),
                        modifier = Modifier.weight(1f).padding(4.dp)
                    ) {
                        // 1. Render Folders
                        // 1. 渲染文件夹
                        if (directories.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text("文件夹 Folders", modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }

                            items(directories) { file ->
                                MediaGridItem(
                                    file = file,
                                    selectedFiles = selectedFiles,
                                    videoExtensions = videoExtensions,
                                    isThreeColumnMode = (currentColumnIndex == 0),
                                    onSelect = { isSelected, f ->
                                        // Original directory selection logic.
                                        // 原有的目录选择逻辑。
                                        if (selectedFiles.isNotEmpty()) {
                                            selectedFiles = if (isSelected) selectedFiles - f else selectedFiles + f
                                        } else {
                                            currentDirectory = f
                                        }
                                    },
                                    onLongSelect = { f ->
                                        // Original long press logic.
                                        // 原有的长按逻辑。
                                        selectedFiles = selectedFiles + f
                                    }
                                )
                            }
                        }

                        // 2. Render Grouped Media Files
                        // 2. 渲染分组的媒体文件
                        groupedMedia.forEach { (date, files) ->
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text(date, modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }

                            // This 'files' is provided by the forEach loop above.
                            // 这个 'files' 是由上面的 forEach 循环提供的。
                            items(files) { file ->
                                MediaGridItem(
                                    file = file,
                                    selectedFiles = selectedFiles,
                                    videoExtensions = videoExtensions,
                                    isThreeColumnMode = (currentColumnIndex == 0),
                                    onSelect = { isSelected, f ->
                                        // Original media selection logic.
                                        // 原有的媒体选择逻辑。
                                        if (selectedFiles.isNotEmpty()) {
                                            selectedFiles = if (isSelected) selectedFiles - f else selectedFiles + f
                                        } else {
                                            if (isPickerMode) {
                                                try {
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                                    val resultIntent = Intent().apply { data = uri; flags = Intent.FLAG_GRANT_READ_URI_PERMISSION }
                                                    val activity = context as? android.app.Activity
                                                    activity?.setResult(android.app.Activity.RESULT_OK, resultIntent)
                                                    activity?.finish()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "文件分享失败", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                viewingMedia = f
                                            }
                                        }
                                    },
                                    onLongSelect = { f ->
                                        // Original long press logic.
                                        // 原有的长按逻辑。
                                        selectedFiles = selectedFiles + f
                                    }
                                )
                            }
                        }
                    }
                } // Column 结束

                // ... 下方的悬浮按钮和所有 Dialog 代码保持完全不变 ...

                // 悬浮按钮 (现在大括号正确，能正常使用 align 了)
                Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) {
                    LargeFloatingActionButton(onClick = { showCreateAlbumDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Album", modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    LargeFloatingActionButton(onClick = { showCamera = true }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera", modifier = Modifier.size(36.dp))
                    }
                }

                // 底部操作栏
                // Bottom Action Bar (when items are long-pressed and selected).
// 底部操作栏（当长按并选中项目时）。
                if (selectedFiles.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {

                            // 1. Rename (Only visible if exactly 1 file is selected).
                            // 1. 重命名（仅当恰好选中 1 个文件时可见）。
                            if (selectedFiles.size == 1) {
                                TextButton(onClick = {
                                    newFileName = selectedFiles.first().nameWithoutExtension
                                    showRenameDialog = true
                                }) {
                                    Text("Rename")
                                }
                            }

                            // 2. Move.
                            // 2. 移动。
                            TextButton(onClick = { showMoveDialog = true }) {
                                Text("Move")
                            }

                            // 3. Share (Calls our new secure helper function).
                            // 3. 分享（调用我们新的安全辅助函数）。
                            TextButton(onClick = {
                                shareMediaFiles(context, selectedFiles)
                                // We keep the files selected after sharing, so they don't lose their state.
                                // 分享后我们保持文件被选中，这样它们就不会丢失状态。
                            }) {
                                Text("Share")
                            }

                            // 4. Delete.
                            // 4. 删除。
                            TextButton(onClick = { showDeleteDialog = true }) {
                                Text("Delete", color = Color.Red)
                            }

                            // 5. Cancel.
                            // 5. 取消。
                            TextButton(onClick = { selectedFiles = emptySet() }) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                // 对话框代码
                if (showCreateAlbumDialog) {
                    AlertDialog(onDismissRequest = { showCreateAlbumDialog = false }, title = { Text("New Album") }, text = { OutlinedTextField(value = newAlbumName, onValueChange = { newAlbumName = it }) }, confirmButton = { TextButton(onClick = { if (newAlbumName.isNotBlank()) File(currentDirectory, newAlbumName).mkdirs(); refreshTrigger++; showCreateAlbumDialog = false; newAlbumName = "" }) { Text("Create") } }, dismissButton = { TextButton(onClick = { showCreateAlbumDialog = false }) { Text("Cancel") } })
                }
                if (showDeleteDialog) {
                    AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text("Delete Items?") }, confirmButton = { TextButton(onClick = { selectedFiles.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }; selectedFiles = emptySet(); showDeleteDialog = false; refreshTrigger++ }) { Text("Delete", color = Color.Red) } }, dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } })
                }
                if (showRenameDialog) {
                    AlertDialog(onDismissRequest = { showRenameDialog = false }, title = { Text("Rename") }, text = { OutlinedTextField(value = newFileName, onValueChange = { newFileName = it }) }, confirmButton = { TextButton(onClick = { val old = selectedFiles.first(); old.renameTo(File(old.parent, "$newFileName.${old.extension}")); refreshTrigger++; selectedFiles = emptySet(); showRenameDialog = false }) { Text("Rename") } }, dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } })
                }
                if (showMoveDialog) {
                    AlertDialog(onDismissRequest = { showMoveDialog = false }, title = { Text("Move Options") }, text = {
                        val albums = rootDir.listFiles()?.filter { it.isDirectory && it != currentDirectory } ?: emptyList()
                        LazyColumn {
                            item { Text("📱 Move to Phone Gallery", modifier = Modifier.fillMaxWidth().clickable { moveFilesToSystemGallery(context, selectedFiles); selectedFiles = emptySet(); showMoveDialog = false; refreshTrigger++ }.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
                            item { Text("📁 Move to Arbitrary Folder", modifier = Modifier.fillMaxWidth().clickable { safLauncher.launch(null) }.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
                            items(albums) { album -> Text("📂 ${album.name}", modifier = Modifier.fillMaxWidth().clickable { selectedFiles.forEach { file -> val dest = File(album, file.name); if (!file.renameTo(dest)) { try { file.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }; file.delete() } catch (e: Exception) { Toast.makeText(context, "Move failed", Toast.LENGTH_SHORT).show() } } }; selectedFiles = emptySet(); showMoveDialog = false; refreshTrigger++ }.padding(16.dp)) }
                        }
                    }, confirmButton = {})
                }
            } // Box 结束
        }
    }
}



// ==========================================
// 🚀 新增：被我遗忘的单元格绘制组件！
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    file: File,
    selectedFiles: Set<File>,
    videoExtensions: Set<String>,
    isThreeColumnMode: Boolean = false, // Added parameter to check grid mode. 新增参数以检查网格模式。
    onSelect: (Boolean, File) -> Unit,
    onLongSelect: (File) -> Unit
) {
    val isSelected = selectedFiles.contains(file)
    val isVideo = file.extension.lowercase() in videoExtensions

    // State to hold the title read from the file.
    // 用于保存从文件中读取的标题的状态。
    var remarkTitle by remember(file) { mutableStateOf<String?>(null) }

    // Read the text file asynchronously when in 3-column mode.
    // 在三列模式下异步读取文本文件。
    LaunchedEffect(file, isThreeColumnMode) {
        if (isThreeColumnMode && !file.isDirectory) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val txtFile = File(file.parent, "${file.nameWithoutExtension}.txt")
                if (txtFile.exists()) {
                    // Only read the first line for the title.
                    // 只读取第一行作为标题。
                    remarkTitle = txtFile.useLines { it.firstOrNull() }
                } else {
                    remarkTitle = null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .combinedClickable(
                onClick = { onSelect(isSelected, file) },
                onLongClick = { onLongSelect(file) }
            )
    ) {
        // 1. Render Directory or Media File
        // 1. 渲染目录或媒体文件
        if (file.isDirectory) {
            Box(modifier = Modifier.fillMaxSize().background(Color.LightGray), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(48.dp))
                Text(file.name, modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp))
            }
        } else {
            val bitmap = if (isVideo) getVideoThumbnail(file.absolutePath) else BitmapFactory.decodeFile(file.absolutePath)
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }

            // Video Play Icon overlay
            // 视频播放图标覆盖层
            if (isVideo) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp).align(Alignment.Center).background(Color.Black.copy(0.4f), shape = androidx.compose.foundation.shape.CircleShape))
            }

            // 2. Render Remark Title Overlay (Only in 3-column mode)
            // 2. 渲染备注标题覆盖层 (仅在三列模式下)
            if (isThreeColumnMode && !remarkTitle.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = remarkTitle!!,
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. Render Selection Overlay
        // 3. 渲染选中状态覆盖层
        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Blue.copy(0.3f)))
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }
}

fun shareMediaFiles(context: android.content.Context, files: Set<java.io.File>) {
    // Return immediately if the set is empty.
    // 如果集合为空，则立即返回。
    if (files.isEmpty()) return

    // Create a list to hold the secure URIs.
    // 创建一个列表来保存安全的 URI。
    val uris = java.util.ArrayList<android.net.Uri>()
    files.forEach { file ->
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        uris.add(uri)
    }

    // Configure the intent for single or multiple files.
    // 为单个或多个文件配置意图。
    val intent = if (uris.size == 1) {
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
        }
    }

    // Grant temporary read permission to the receiving app.
    // 授予接收应用临时的读取权限。
    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

    // Launch the chooser dialog.
    // 启动选择器对话框。
    context.startActivity(android.content.Intent.createChooser(intent, "分享给 (Share to)"))
}