package com.example.workgallery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// =========================================================================
// 1. Data Models (数据模型区)
// =========================================================================

data class TimeSlot(
    val id: String = UUID.randomUUID().toString(),
    var startHour: Int, var startMinute: Int,
    var endHour: Int, var endMinute: Int,
    var isCustom: Boolean = false
)

data class Course(
    val id: String = UUID.randomUUID().toString(),
    var name: String, var location: String = "", var teacher: String = "",
    var dayOfWeek: Int, var slotIndex: Int,
    var activeWeeks: Set<Int> = emptySet()
)

data class ScheduleTemplate(
    val id: String = UUID.randomUUID().toString(),
    var name: String, var activeDays: List<Int>,
    var classDuration: Int, var breakDuration: Int,
    var startDateMillis: Long = System.currentTimeMillis(),
    var slots: List<TimeSlot>, var courses: List<Course> = emptyList()
)

enum class ScheduleViewState { TABLE_VIEW, TEMPLATE_LIST, EDITOR }

// =========================================================================
// 2. Persistence (数据持久化)
// =========================================================================

fun saveTemplatesToLocal(context: Context, templates: List<ScheduleTemplate>) {
    try {
        val jsonArray = JSONArray()
        templates.forEach { tmpl ->
            val obj = JSONObject().apply {
                put("id", tmpl.id); put("name", tmpl.name)
                put("activeDays", JSONArray(tmpl.activeDays))
                put("classDuration", tmpl.classDuration); put("breakDuration", tmpl.breakDuration)
                put("startDateMillis", tmpl.startDateMillis)

                val slotsArray = JSONArray()
                tmpl.slots.forEach { slot ->
                    slotsArray.put(JSONObject().apply {
                        put("id", slot.id); put("startHour", slot.startHour); put("startMinute", slot.startMinute)
                        put("endHour", slot.endHour); put("endMinute", slot.endMinute); put("isCustom", slot.isCustom)
                    })
                }
                put("slots", slotsArray)

                val coursesArray = JSONArray()
                tmpl.courses.forEach { course ->
                    coursesArray.put(JSONObject().apply {
                        put("id", course.id); put("name", course.name); put("location", course.location)
                        put("teacher", course.teacher); put("dayOfWeek", course.dayOfWeek); put("slotIndex", course.slotIndex)
                        put("activeWeeks", JSONArray(course.activeWeeks.toList()))
                    })
                }
                put("courses", coursesArray)
            }
            jsonArray.put(obj)
        }
        File(context.filesDir, "schedule_templates.json").writeText(jsonArray.toString())
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadTemplatesFromLocal(context: Context): List<ScheduleTemplate> {
    val file = File(context.filesDir, "schedule_templates.json")
    if (!file.exists()) return emptyList()

    val list = mutableListOf<ScheduleTemplate>()
    try {
        val jsonArray = JSONArray(file.readText())
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val daysList = mutableListOf<Int>()
            val daysArray = obj.getJSONArray("activeDays")
            for (j in 0 until daysArray.length()) daysList.add(daysArray.getInt(j))

            val slotsList = mutableListOf<TimeSlot>()
            val slotsArray = obj.getJSONArray("slots")
            for (j in 0 until slotsArray.length()) {
                val sObj = slotsArray.getJSONObject(j)
                slotsList.add(TimeSlot(sObj.getString("id"), sObj.getInt("startHour"), sObj.getInt("startMinute"), sObj.getInt("endHour"), sObj.getInt("endMinute"), sObj.getBoolean("isCustom")))
            }

            val coursesList = mutableListOf<Course>()
            if (obj.has("courses")) {
                val coursesArray = obj.getJSONArray("courses")
                for (j in 0 until coursesArray.length()) {
                    val cObj = coursesArray.getJSONObject(j)
                    val wSet = mutableSetOf<Int>()
                    val wArray = cObj.getJSONArray("activeWeeks")
                    for (k in 0 until wArray.length()) wSet.add(wArray.getInt(k))
                    coursesList.add(Course(cObj.getString("id"), cObj.getString("name"), cObj.getString("location"), cObj.getString("teacher"), cObj.getInt("dayOfWeek"), cObj.getInt("slotIndex"), wSet))
                }
            }

            list.add(ScheduleTemplate(obj.getString("id"), obj.getString("name"), daysList, obj.getInt("classDuration"), obj.getInt("breakDuration"), if (obj.has("startDateMillis")) obj.getLong("startDateMillis") else System.currentTimeMillis(), slotsList, coursesList))
        }
    } catch (e: Exception) { e.printStackTrace() }
    return list
}