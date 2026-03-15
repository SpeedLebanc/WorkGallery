package com.example.workgallery

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// 数据模型
// ==========================================
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var tag: String? = null,
    var isPinned: Boolean = false
)

// ==========================================
// 记事本主页面
// ==========================================
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen() {
    val context = LocalContext.current

    // 状态引擎
    var notes by remember { mutableStateOf(loadNotesFromLocal(context)) }
    var tags by remember { mutableStateOf(loadTagsFromLocal(context)) }
    var currentFilterTag by remember { mutableStateOf<String?>(null) }

    // 独立的全屏编辑器状态
    var isEditorOpen by remember { mutableStateOf(false) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorImageUri by remember { mutableStateOf<String?>(null) }

    // 标签管理状态
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newCustomTag by remember { mutableStateOf("") }
    var tagToManage by remember { mutableStateOf<String?>(null) }
    var showRenameTagDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) editorImageUri = uri.toString()
    }

    // 自动保存机制
    LaunchedEffect(notes) { withContext(Dispatchers.IO) { saveNotesToLocal(context, notes) } }
    LaunchedEffect(tags) { withContext(Dispatchers.IO) { saveTagsToLocal(context, tags) } }

    // ==========================================
    // 页面 1：独立的沉浸式全屏编辑器
    // ==========================================
    if (isEditorOpen) {
        val closeAndSave = {
            if (editorText.isNotBlank() || editorImageUri != null) {
                if (editingNoteId != null) {
                    notes = notes.map { if (it.id == editingNoteId) it.copy(text = editorText, imageUri = editorImageUri) else it }
                } else {
                    notes = notes + Note(text = editorText, imageUri = editorImageUri)
                }
            }
            isEditorOpen = false
            editingNoteId = null
            editorText = ""
            editorImageUri = null
        }

        BackHandler { closeAndSave() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (editingNoteId == null) "新建笔记" else "编辑笔记") },
                    navigationIcon = {
                        IconButton(onClick = closeAndSave) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                    },
                    actions = {
                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }) { Icon(Icons.Default.Image, contentDescription = "图片") }
                        IconButton(onClick = closeAndSave) { Icon(Icons.Default.Check, contentDescription = "完成") }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

                // 🚀 核心修复：纯净的图片渲染逻辑，绝不把 UI 嵌套在 try-catch 中
                if (editorImageUri != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(bottom = 16.dp)) {

                        // 1. 在 remember 内处理会抛出异常的流读取（非 UI 逻辑）
                        val editorBitmap = remember(editorImageUri) {
                            try {
                                val inputStream = context.contentResolver.openInputStream(Uri.parse(editorImageUri!!))
                                BitmapFactory.decodeStream(inputStream)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        // 2. 根据处理结果渲染 UI（纯 UI 逻辑）
                        if (editorBitmap != null) {
                            Image(
                                bitmap = editorBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                            )
                        }

                        IconButton(
                            onClick = { editorImageUri = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) { Icon(Icons.Default.Close, contentDescription = "移除", tint = Color.White) }
                    }
                }

                TextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    placeholder = { Text("开始记录你的想法...", color = Color.Gray) },
                    modifier = Modifier.fillMaxSize(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
        return // 退出渲染，显示全屏编辑器
    }

    // ==========================================
    // 页面 2：正常的记事本列表页
    // ==========================================
    val displayedNotes = notes
        .filter { if (currentFilterTag == null) true else it.tag == currentFilterTag }
        .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timestamp })

    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("当前：WorkNotes", color = Color.Gray, style = MaterialTheme.typography.titleMedium)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { isEditorOpen = true }) { Icon(Icons.Default.Add, contentDescription = "新建") }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                LazyRow(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    item {
                        TagChip(tagName = "全部", isSelected = currentFilterTag == null, onClick = { currentFilterTag = null }, onLongClick = {})
                    }
                    items(tags) { tagName ->
                        TagChip(
                            tagName = tagName,
                            isSelected = currentFilterTag == tagName,
                            onClick = { currentFilterTag = if (currentFilterTag == tagName) null else tagName },
                            onLongClick = { tagToManage = tagName }
                        )
                    }
                    item {
                        TagChip(tagName = "+ 自定义", isSelected = false, onClick = { showAddTagDialog = true }, onLongClick = {})
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (displayedNotes.isEmpty()) {
                Text("点击右下角新建笔记", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(displayedNotes, key = { it.id }) { note ->
                        NoteItem(
                            note = note, availableTags = tags,
                            onDelete = { notes = notes.filter { it.id != note.id } },
                            onTogglePin = { notes = notes.map { if (it.id == note.id) it.copy(isPinned = !it.isPinned) else it } },
                            onSetTag = { tag -> notes = notes.map { if (it.id == note.id) it.copy(tag = tag) else it } },
                            onEdit = {
                                editingNoteId = note.id
                                editorText = note.text
                                editorImageUri = note.imageUri
                                isEditorOpen = true
                            }
                        )
                    }
                }
            }
        }

        if (showAddTagDialog) {
            AlertDialog(
                onDismissRequest = { showAddTagDialog = false; newCustomTag = "" },
                title = { Text("新建标签") },
                text = { OutlinedTextField(value = newCustomTag, onValueChange = { newCustomTag = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        if (newCustomTag.isNotBlank() && !tags.contains(newCustomTag)) tags = tags + newCustomTag
                        showAddTagDialog = false; newCustomTag = ""
                    }) { Text("添加") }
                }
            )
        }

        if (tagToManage != null && !showRenameTagDialog) {
            val currentIndex = tags.indexOf(tagToManage)
            AlertDialog(
                onDismissRequest = { tagToManage = null },
                title = { Text("管理标签: $tagToManage") },
                text = {
                    Column {
                        TextButton(onClick = { showRenameTagDialog = true; newCustomTag = tagToManage!! }, modifier = Modifier.fillMaxWidth()) { Text("✏️ 重命名") }
                        if (currentIndex > 0) {
                            TextButton(onClick = {
                                val newList = tags.toMutableList()
                                Collections.swap(newList, currentIndex, currentIndex - 1)
                                tags = newList
                                tagToManage = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("⬅️ 向左移动") }
                        }
                        if (currentIndex < tags.size - 1) {
                            TextButton(onClick = {
                                val newList = tags.toMutableList()
                                Collections.swap(newList, currentIndex, currentIndex + 1)
                                tags = newList
                                tagToManage = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("➡️ 向右移动") }
                        }
                        TextButton(onClick = {
                            tags = tags.filter { it != tagToManage }
                            notes = notes.map { if (it.tag == tagToManage) it.copy(tag = null) else it }
                            tagToManage = null
                            currentFilterTag = null
                        }, modifier = Modifier.fillMaxWidth()) { Text("🗑️ 删除", color = Color.Red) }
                    }
                },
                confirmButton = { TextButton(onClick = { tagToManage = null }) { Text("取消") } }
            )
        }

        if (showRenameTagDialog) {
            AlertDialog(
                onDismissRequest = { showRenameTagDialog = false; newCustomTag = "" },
                title = { Text("重命名标签") },
                text = { OutlinedTextField(value = newCustomTag, onValueChange = { newCustomTag = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        if (newCustomTag.isNotBlank() && !tags.contains(newCustomTag)) {
                            tags = tags.map { if (it == tagToManage) newCustomTag else it }
                            notes = notes.map { if (it.tag == tagToManage) it.copy(tag = newCustomTag) else it }
                            if (currentFilterTag == tagToManage) currentFilterTag = newCustomTag
                        }
                        showRenameTagDialog = false; tagToManage = null; newCustomTag = ""
                    }) { Text("保存") }
                }
            )
        }
    }
}

// ==========================================
// 辅助组件：底栏标签按钮与记事本卡片
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagChip(tagName: String, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bgColor = if (tagName == "+ 自定义" || tagName == "全部") Color.White else if (isSelected) getTagColor(tagName) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (tagName == "+ 自定义" || tagName == "全部") BorderStroke(1.dp, Color.LightGray) else null

    Surface(
        shape = RoundedCornerShape(16.dp), color = bgColor, border = borderColor,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) { Text(tagName, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp, color = Color.DarkGray) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItem(note: Note, availableTags: List<String>, onDelete: () -> Unit, onTogglePin: () -> Unit, onSetTag: (String?) -> Unit, onEdit: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    val tagColor = note.tag?.let { getTagColor(it) } ?: MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { showMenu = true }),
        colors = CardDefaults.cardColors(containerColor = tagColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (note.isPinned) Icon(Icons.Default.PushPin, contentDescription = "置顶", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)

                // 🚀 核心修复：同样保证列表项的解析也是纯净的
                if (note.imageUri != null) {
                    val bitmap = remember(note.imageUri) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(Uri.parse(note.imageUri))
                            BitmapFactory.decodeStream(inputStream)
                        } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.height(80.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).padding(bottom = 8.dp))
                    }
                }
                Text(text = note.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(text = sdf.format(Date(note.timestamp)), color = Color.Gray, fontSize = 12.sp)
                if (note.tag != null) Text(text = note.tag!!, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("📝 修改内容") }, onClick = { onEdit(); showMenu = false })
            DropdownMenuItem(text = { Text(if (note.isPinned) "📌 取消置顶" else "📌 置顶") }, onClick = { onTogglePin(); showMenu = false })
            HorizontalDivider()
            Text("设置标签", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            DropdownMenuItem(text = { Text("无标签") }, onClick = { onSetTag(null); showMenu = false })
            availableTags.forEach { tagName -> DropdownMenuItem(text = { Text(tagName) }, onClick = { onSetTag(tagName); showMenu = false }) }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("🗑️ 删除", color = Color.Red) }, onClick = { onDelete(); showMenu = false })
        }
    }
}

// ==========================================
// 数据持久化引擎
// ==========================================
fun getTagColor(tagName: String): Color {
    return when (tagName) {
        "工作" -> Color(0xFFE3F2FD)
        "生活" -> Color(0xFFF1F8E9)
        "灵感" -> Color(0xFFFFF3E0)
        "重要" -> Color(0xFFFFEBEE)
        else -> {
            val hash = tagName.hashCode()
            Color((hash and 0xFF0000 shr 16) % 128 + 127, (hash and 0x00FF00 shr 8) % 128 + 127, (hash and 0x0000FF) % 128 + 127)
        }
    }
}

fun saveTagsToLocal(context: android.content.Context, tags: List<String>) {
    try {
        val jsonArray = JSONArray()
        tags.forEach { jsonArray.put(it) }
        File(context.filesDir, "work_tags.json").writeText(jsonArray.toString())
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadTagsFromLocal(context: android.content.Context): List<String> {
    val file = File(context.filesDir, "work_tags.json")
    if (!file.exists()) return listOf("工作", "生活", "灵感", "重要")
    try {
        val jsonArray = JSONArray(file.readText())
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) { list.add(jsonArray.getString(i)) }
        return list
    } catch (e: Exception) { return listOf("工作", "生活", "灵感", "重要") }
}

fun saveNotesToLocal(context: android.content.Context, notes: List<Note>) {
    try {
        val jsonArray = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject().apply {
                put("id", note.id)
                put("text", note.text)
                put("imageUri", note.imageUri ?: "")
                put("timestamp", note.timestamp)
                put("tag", note.tag ?: "")
                put("isPinned", note.isPinned)
            }
            jsonArray.put(obj)
        }
        File(context.filesDir, "work_notes.json").writeText(jsonArray.toString())
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadNotesFromLocal(context: android.content.Context): List<Note> {
    val file = File(context.filesDir, "work_notes.json")
    if (!file.exists()) return emptyList()
    val list = mutableListOf<Note>()
    try {
        val jsonArray = JSONArray(file.readText())
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(Note(
                id = obj.getString("id"),
                text = obj.getString("text"),
                imageUri = obj.getString("imageUri").takeIf { it.isNotEmpty() },
                timestamp = obj.getLong("timestamp"),
                tag = obj.getString("tag").takeIf { it.isNotEmpty() },
                isPinned = obj.getBoolean("isPinned")
            ))
        }
    } catch (e: Exception) { e.printStackTrace() }
    return list
}