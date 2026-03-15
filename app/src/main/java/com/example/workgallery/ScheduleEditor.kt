package com.example.workgallery

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun showTimePicker(context: Context, initHour: Int, initMinute: Int, onTimeSelected: (Int, Int) -> Unit) {
    TimePickerDialog(context, { _, hour, minute -> onTimeSelected(hour, minute) }, initHour, initMinute, true).show()
}

// =========================================================================
// 表头标准编辑器 (TemplateEditorPage)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorPage(initialTemplate: ScheduleTemplate, onSave: (ScheduleTemplate) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var templateName by remember { mutableStateOf(initialTemplate.name) }
    var activeDays by remember { mutableStateOf(initialTemplate.activeDays.toSet()) }
    var classDurStr by remember { mutableStateOf(initialTemplate.classDuration.toString()) }
    var breakDurStr by remember { mutableStateOf(initialTemplate.breakDuration.toString()) }
    var slots by remember { mutableStateOf(initialTemplate.slots) }
    var startDateMillis by remember { mutableLongStateOf(initialTemplate.startDateMillis) }

    val daysOfWeek = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val triggerRecalculate = {
        val cDur = classDurStr.toIntOrNull() ?: 95
        val bDur = breakDurStr.toIntOrNull() ?: 20
        if (slots.isNotEmpty()) {
            val newSlots = mutableListOf<TimeSlot>()
            var currentStartH = slots[0].startHour
            var currentStartM = slots[0].startMinute

            for (slot in slots) {
                if (slot.isCustom) {
                    currentStartH = slot.startHour
                    currentStartM = slot.startMinute
                }
                val startTotalMins = currentStartH * 60 + currentStartM
                val endTotalMins = startTotalMins + cDur
                newSlots.add(slot.copy(startHour = currentStartH, startMinute = currentStartM, endHour = endTotalMins / 60, endMinute = endTotalMins % 60))
                val nextStartTotalMins = endTotalMins + bDur
                currentStartH = nextStartTotalMins / 60
                currentStartM = nextStartTotalMins % 60
            }
            slots = newSlots
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑标准", fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "取消") } },
                actions = {
                    TextButton(onClick = {
                        val cDur = classDurStr.toIntOrNull() ?: 95
                        val bDur = breakDurStr.toIntOrNull() ?: 20
                        onSave(ScheduleTemplate(initialTemplate.id, templateName, activeDays.toList().sorted(), cDur, bDur, startDateMillis, slots, initialTemplate.courses))
                    }) { Text("保存", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {

            OutlinedTextField(
                value = templateName, onValueChange = { templateName = it },
                label = { Text("课表标准名称") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("星期显示", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply { timeInMillis = startDateMillis }
                    DatePickerDialog(context, { _, y, m, d ->
                        calendar.set(y, m, d)
                        startDateMillis = calendar.timeInMillis
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                }, contentPadding = PaddingValues(0.dp)) {
                    Text("开学日期: ${sdf.format(Date(startDateMillis))}", fontSize = 14.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                daysOfWeek.forEachIndexed { index, dayName ->
                    val dayId = index + 1
                    val isSelected = activeDays.contains(dayId)
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .clickable { activeDays = if (isSelected) activeDays - dayId else activeDays + dayId }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) { Text(dayName, fontSize = 12.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("时间划分", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Surface(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = classDurStr, onValueChange = { classDurStr = it }, label = { Text("上课(分)", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(value = breakDurStr, onValueChange = { breakDurStr = it }, label = { Text("课间(分)", fontSize = 12.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = triggerRecalculate, modifier = Modifier.height(50.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("更新", fontSize = 14.sp) }
                }
            }

            Text("手动点击修改时间会自动锁定(🔒)，不会被【更新】覆盖。", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(slots) { index, slot ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("第${index + 1}节", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (slot.isCustom) Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp).padding(start = 2.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(String.format("%02d:%02d", slot.startHour, slot.startMinute), fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { showTimePicker(context, slot.startHour, slot.startMinute) { h, m -> slots = slots.toMutableList().apply { this[index] = slot.copy(startHour = h, startMinute = m, isCustom = true) } } })
                                Text(" ~ ", modifier = Modifier.padding(horizontal = 4.dp))
                                Text(String.format("%02d:%02d", slot.endHour, slot.endMinute), fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { showTimePicker(context, slot.endHour, slot.endMinute) { h, m -> slots = slots.toMutableList().apply { this[index] = slot.copy(endHour = h, endMinute = m, isCustom = true) } } })
                            }
                            IconButton(onClick = { slots = slots.toMutableList().apply { removeAt(index) } }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Delete, "删除", tint = Color.Red, modifier = Modifier.size(22.dp)) }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            val newSlot = if (slots.isNotEmpty()) {
                                val last = slots.last(); val bDur = breakDurStr.toIntOrNull() ?: 20; val cDur = classDurStr.toIntOrNull() ?: 95
                                val startMins = last.endHour * 60 + last.endMinute + bDur; val endMins = startMins + cDur
                                TimeSlot(startHour = startMins / 60, startMinute = startMins % 60, endHour = endMins / 60, endMinute = endMins % 60, isCustom = false)
                            } else TimeSlot(startHour = 8, startMinute = 30, endHour = 10, endMinute = 5, isCustom = false)
                            slots = slots + newSlot
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(48.dp)
                    ) { Text("添加时间段", fontSize = 16.sp) }
                }
            }
        }
    }
}

// =========================================================================
// 课程详情编辑器弹窗 (CourseEditDialog - 解决Unresolved reference的核心)
// =========================================================================
@Composable
fun CourseEditDialog(
    initialCourse: Course?,
    defaultDay: Int,
    defaultSlot: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit
) {
    var name by remember { mutableStateOf(initialCourse?.name ?: "") }
    var location by remember { mutableStateOf(initialCourse?.location ?: "") }
    var teacher by remember { mutableStateOf(initialCourse?.teacher ?: "") }
    var activeWeeks by remember { mutableStateOf(initialCourse?.activeWeeks ?: emptySet()) }
    val allWeeks = (1..22).toList()

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initialCourse == null) "添加课程" else "修改课程") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称 (必填)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("老师") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Text("周次选择", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { activeWeeks = allWeeks.filter { it % 2 != 0 }.toSet() }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("单周", fontSize = 14.sp) }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { activeWeeks = allWeeks.filter { it % 2 == 0 }.toSet() }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("双周", fontSize = 14.sp) }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { activeWeeks = allWeeks.toSet() }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("全选", fontSize = 14.sp) }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(onClick = { activeWeeks = emptySet() }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp)) { Text("清空", fontSize = 14.sp) }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(180.dp).padding(top = 4.dp)) {
                    items(allWeeks) { w ->
                        val isSelected = activeWeeks.contains(w)
                        Box(modifier = Modifier.padding(2.dp).aspectRatio(1f).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).clickable { activeWeeks = if (isSelected) activeWeeks - w else activeWeeks + w }, contentAlignment = Alignment.Center) { Text("$w", color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && activeWeeks.isNotEmpty()) {
                        onSave(initialCourse?.copy(name = name, location = location, teacher = teacher, activeWeeks = activeWeeks) ?: Course(name = name, location = location, teacher = teacher, dayOfWeek = defaultDay, slotIndex = defaultSlot, activeWeeks = activeWeeks))
                    }
                },
                enabled = name.isNotBlank() && activeWeeks.isNotEmpty()
            ) { Text("保存", fontSize = 16.sp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", fontSize = 16.sp) } }
    )
}