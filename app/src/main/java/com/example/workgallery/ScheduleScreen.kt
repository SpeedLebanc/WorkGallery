package com.example.workgallery

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBackToMain: () -> Unit = {}) {
    val context = LocalContext.current
    var templates by remember { mutableStateOf(loadTemplatesFromLocal(context)) }
    var activeTemplateId by remember { mutableStateOf<String?>(templates.firstOrNull()?.id) }
    var currentView by remember { mutableStateOf(ScheduleViewState.TABLE_VIEW) }
    var editingTemplate by remember { mutableStateOf<ScheduleTemplate?>(null) }

    var displayWeek by remember { mutableIntStateOf(1) }
    var showWeekInputDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeTemplateId, templates) {
        val tmpl = templates.find { it.id == activeTemplateId }
        if (tmpl != null) {
            val msPerWeek = 7L * 24 * 60 * 60 * 1000
            val weeksPassed = ((System.currentTimeMillis() - tmpl.startDateMillis) / msPerWeek).toInt()
            displayWeek = (weeksPassed + 1).coerceIn(1, 22)
        }
    }

    LaunchedEffect(templates) { withContext(Dispatchers.IO) { saveTemplatesToLocal(context, templates) } }

    BackHandler(enabled = currentView != ScheduleViewState.TABLE_VIEW) { currentView = ScheduleViewState.TABLE_VIEW }

    when (currentView) {
        ScheduleViewState.TABLE_VIEW -> {
            val activeTemplate = templates.find { it.id == activeTemplateId }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            if (activeTemplate != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(activeTemplate.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = { showWeekInputDialog = true }) {
                                        Text("第 $displayWeek 周 ▾", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else {
                                Text("我的课表")
                            }
                        },
                        actions = { IconButton(onClick = { currentView = ScheduleViewState.TEMPLATE_LIST }) { Icon(Icons.Default.List, "标准管理") } }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    if (activeTemplate == null) {
                        Text("请点击右上角创建或选择一个课表标准", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                    } else {
                        RenderedScheduleTable(
                            template = activeTemplate,
                            displayWeek = displayWeek,
                            onWeekChange = { displayWeek = it },
                            onUpdateTemplate = { updatedTmpl: ScheduleTemplate -> templates = templates.map { if (it.id == updatedTmpl.id) updatedTmpl else it } }
                        )
                    }
                }
            }

            if (showWeekInputDialog) {
                var inputStr by remember { mutableStateOf(displayWeek.toString()) }
                AlertDialog(
                    onDismissRequest = { showWeekInputDialog = false }, title = { Text("跳转到指定周") },
                    text = { OutlinedTextField(value = inputStr, onValueChange = { inputStr = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, label = { Text("输入周次 (1-22)") }) },
                    confirmButton = { TextButton(onClick = { val w = inputStr.toIntOrNull(); if (w != null && w in 1..22) { displayWeek = w; showWeekInputDialog = false } else { Toast.makeText(context, "请输入 1 到 22 之间的数字", Toast.LENGTH_SHORT).show() } }) { Text("确定") } },
                    dismissButton = { TextButton(onClick = { showWeekInputDialog = false }) { Text("取消") } }
                )
            }
        }

        ScheduleViewState.TEMPLATE_LIST -> {
            Scaffold(
                topBar = { TopAppBar(title = { Text("标准管理") }, navigationIcon = { IconButton(onClick = { currentView = ScheduleViewState.TABLE_VIEW }) { Icon(Icons.Default.ArrowBack, "返回") } }) },
                floatingActionButton = {
                    FloatingActionButton(onClick = {
                        editingTemplate = ScheduleTemplate(name = "新标准", activeDays = listOf(1, 2, 3, 4, 5), classDuration = 95, breakDuration = 20, startDateMillis = System.currentTimeMillis(), slots = listOf(TimeSlot(startHour = 8, startMinute = 30, endHour = 10, endMinute = 5), TimeSlot(startHour = 10, startMinute = 25, endHour = 12, endMinute = 0), TimeSlot(startHour = 14, startMinute = 0, endHour = 15, endMinute = 35, isCustom = true), TimeSlot(startHour = 15, startMinute = 55, endHour = 17, endMinute = 30), TimeSlot(startHour = 19, startMinute = 0, endHour = 20, endMinute = 30, isCustom = true)), courses = emptyList())
                        currentView = ScheduleViewState.EDITOR
                    }) { Icon(Icons.Default.Add, "新增标准") }
                }
            ) { padding ->
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    items(templates) { tmpl ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { activeTemplateId = tmpl.id; currentView = ScheduleViewState.TABLE_VIEW },
                            colors = CardDefaults.cardColors(containerColor = if (tmpl.id == activeTemplateId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = tmpl.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "共 ${tmpl.slots.size} 个时间段",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = {
                                            val newTmpl = tmpl.copy(
                                                id = UUID.randomUUID().toString(),
                                                name = tmpl.name + " (新学期)",
                                                courses = emptyList(),
                                                startDateMillis = System.currentTimeMillis()
                                            )
                                            templates = templates + newTmpl
                                            activeTemplateId = newTmpl.id
                                            currentView = ScheduleViewState.TABLE_VIEW
                                            Toast.makeText(context, "已为您生成一张基于该表头的空白新课表！", Toast.LENGTH_SHORT).show()
                                        }
                                    ) { Text("复用表头", fontSize = 14.sp) }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = { editingTemplate = tmpl.copy(); currentView = ScheduleViewState.EDITOR }
                                    ) { Text("修改结构", fontSize = 14.sp) }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { templates = templates.filter { it.id != tmpl.id }; if (activeTemplateId == tmpl.id) activeTemplateId = templates.firstOrNull()?.id }
                                    ) {
                                        Icon(Icons.Default.Delete, "删除", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ScheduleViewState.EDITOR -> {
            editingTemplate?.let { tmpl -> TemplateEditorPage(initialTemplate = tmpl, onSave = { updatedTmpl: ScheduleTemplate -> val exists = templates.any { it.id == updatedTmpl.id }; templates = if (exists) templates.map { if (it.id == updatedTmpl.id) updatedTmpl else it } else templates + updatedTmpl; activeTemplateId = updatedTmpl.id; currentView = ScheduleViewState.TABLE_VIEW }, onCancel = { currentView = ScheduleViewState.TEMPLATE_LIST }) }
        }
    }
}

// 表格渲染区
@Composable
fun RenderedScheduleTable(
    template: ScheduleTemplate,
    displayWeek: Int,
    onWeekChange: (Int) -> Unit,
    onUpdateTemplate: (ScheduleTemplate) -> Unit
) {
    val context = LocalContext.current
    val daysMap = mapOf(1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六", 7 to "周日")

    var showCourseDialog by remember { mutableStateOf(false) }
    var showCellOptionsDialog by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var targetDay by remember { mutableIntStateOf(1) }
    var targetSlot by remember { mutableIntStateOf(0) }
    var cellCoursesList by remember { mutableStateOf<List<Course>>(emptyList()) }
    var courseClipboard by remember { mutableStateOf<Course?>(null) }

    val verticalScrollState = rememberScrollState()

    val weekStartCalendar = remember(template.startDateMillis, displayWeek) {
        Calendar.getInstance().apply {
            timeInMillis = template.startDateMillis
            firstDayOfWeek = Calendar.MONDAY
            val currentDayOfWeek = get(Calendar.DAY_OF_WEEK)
            val offsetToMonday = if (currentDayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - currentDayOfWeek
            add(Calendar.DAY_OF_YEAR, offsetToMonday)
            add(Calendar.WEEK_OF_YEAR, displayWeek - 1)
        }
    }
    val sdfDate = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

    // ==========================================
    // 🚀 正确配置的系统日历权限请求 Launcher
    // ==========================================
    val coroutineScope = rememberCoroutineScope()
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isGranted = permissions[Manifest.permission.WRITE_CALENDAR] == true &&
                    permissions[Manifest.permission.READ_CALENDAR] == true
            if (isGranted) {
                // 权限通过后，写入日历
                coroutineScope.launch {
                    ScheduleCalendarManager.exportToCalendar(context, template, 10)
                }
            } else {
                Toast.makeText(context, "需要日历权限才能一键写入课表", Toast.LENGTH_SHORT).show()
            }
        }
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = this.maxHeight
        val headerHeight = 56.dp
        val slotCount = template.slots.size

        val dynamicMinRowHeight = if (slotCount >= 4) {
            val calcHeight = (screenHeight - headerHeight) / slotCount
            if (calcHeight > 80.dp) calcHeight else 80.dp
        } else {
            100.dp
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                if (courseClipboard != null) {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF3E0)).padding(8.dp), contentAlignment = Alignment.Center) {
                        Text("📋 已复制课程，点击空白格子直接粘贴", color = Color(0xFFE65100), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 8.dp, end = 8.dp, start = 0.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(verticalScrollState)) {

                        Row(modifier = Modifier.height(headerHeight).background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 8.dp)) {
                            Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                                Text("时间", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            template.activeDays.forEach { dayId ->
                                val cellCal = weekStartCalendar.clone() as Calendar
                                cellCal.add(Calendar.DAY_OF_YEAR, dayId - 1)
                                val dateStr = sdfDate.format(cellCal.time)

                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(daysMap[dayId] ?: "", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text(dateStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }

                        template.slots.forEachIndexed { slotIndex, slot ->
                            val rowBg = if (slotIndex % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

                            Row(modifier = Modifier.height(IntrinsicSize.Min).defaultMinSize(minHeight = dynamicMinRowHeight).background(rowBg)) {

                                Box(modifier = Modifier.weight(0.8f).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text("${slotIndex + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(String.format("%02d:%02d", slot.startHour, slot.startMinute), fontSize = 11.sp, color = Color.Gray)
                                        Text("-", fontSize = 9.sp, color = Color.LightGray)
                                        Text(String.format("%02d:%02d", slot.endHour, slot.endMinute), fontSize = 11.sp, color = Color.Gray)
                                    }
                                }

                                template.activeDays.forEach { dayId ->
                                    val allCoursesInCell = template.courses.filter { it.dayOfWeek == dayId && it.slotIndex == slotIndex }
                                    val visibleCourses = allCoursesInCell.filter { it.activeWeeks.contains(displayWeek) }

                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                            .background(if (visibleCourses.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                            .clickable {
                                                targetDay = dayId; targetSlot = slotIndex
                                                if (courseClipboard != null) {
                                                    val newCourse = courseClipboard!!.copy(id = UUID.randomUUID().toString(), dayOfWeek = dayId, slotIndex = slotIndex)
                                                    onUpdateTemplate(template.copy(courses = template.courses + newCourse))
                                                    courseClipboard = null
                                                    Toast.makeText(context, "粘贴成功", Toast.LENGTH_SHORT).show()
                                                } else if (allCoursesInCell.isEmpty()) { editingCourse = null; showCourseDialog = true }
                                                else { cellCoursesList = allCoursesInCell; showCellOptionsDialog = true }
                                            }.padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (visibleCourses.isNotEmpty()) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                val isConflict = visibleCourses.size > 1
                                                visibleCourses.forEach { c ->
                                                    Text(
                                                        text = c.name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isConflict) Color.Red else MaterialTheme.colorScheme.onSurface,
                                                        textAlign = TextAlign.Center
                                                    )
                                                    if (c.location.isNotBlank()) {
                                                        Text(
                                                            text = c.location,
                                                            fontSize = 10.sp,
                                                            color = if (isConflict) Color.Red.copy(alpha = 0.7f) else Color.Gray,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                    if (isConflict) Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (displayWeek > 1) {
                Box(
                    modifier = Modifier.align(Alignment.CenterStart).width(36.dp).fillMaxHeight(0.3f)
                        .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .clickable { onWeekChange(displayWeek - 1) },
                    contentAlignment = Alignment.Center
                ) { Text("<", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }

            Column(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(0.4f),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                if (displayWeek < 22) {
                    Box(
                        modifier = Modifier.width(36.dp).weight(1f)
                            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                            .clickable { onWeekChange(displayWeek + 1) },
                        contentAlignment = Alignment.Center
                    ) { Text(">", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }

                // ==========================================
                // 🚀 正确写入日历的铃铛按钮
                // ==========================================
                Box(
                    modifier = Modifier.width(36.dp).height(60.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .clickable {
                            calendarPermissionLauncher.launch(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = "Sync to Calendar", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    if (showCourseDialog) {
        CourseEditDialog(
            initialCourse = editingCourse,
            defaultDay = targetDay,
            defaultSlot = targetSlot,
            onDismiss = { showCourseDialog = false },
            onSave = { course: Course ->
                val updatedCourses = if (editingCourse != null) { template.courses.map { if (it.id == course.id) course else it } } else { template.courses + course }
                onUpdateTemplate(template.copy(courses = updatedCourses))
                showCourseDialog = false
            }
        )
    }

    if (showCellOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showCellOptionsDialog = false }, title = { Text("管理此时间段课程") },
            text = {
                LazyColumn {
                    items(cellCoursesList) { course ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(course.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("周次: ${course.activeWeeks.sorted().joinToString(",")}", fontSize = 12.sp, color = Color.Gray)
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { editingCourse = course; showCellOptionsDialog = false; showCourseDialog = true }) { Text("修改") }
                                    TextButton(onClick = { courseClipboard = course; showCellOptionsDialog = false }) { Text("复制") }
                                    TextButton(onClick = { onUpdateTemplate(template.copy(courses = template.courses.filter { it.id != course.id })); showCellOptionsDialog = false }) { Text("删除", color = Color.Red) }
                                }
                            }
                        }
                    }
                    item { OutlinedButton(onClick = { editingCourse = null; showCellOptionsDialog = false; showCourseDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("➕ 新增课程") } }
                }
            }, confirmButton = { TextButton(onClick = { showCellOptionsDialog = false }) { Text("关闭") } }
        )
    }
}