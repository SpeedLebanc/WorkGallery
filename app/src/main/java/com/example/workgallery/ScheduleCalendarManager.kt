package com.example.workgallery

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

object ScheduleCalendarManager {

    suspend fun exportToCalendar(context: Context, template: ScheduleTemplate, advanceMins: Int = 10) {
        withContext(Dispatchers.IO) {
            val calId = getSystemCalendarId(context)
            if (calId == -1L) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "未找到可用的系统日历账户", Toast.LENGTH_SHORT).show() }
                return@withContext
            }

            var insertCount = 0

            template.courses.forEach { course ->
                val slot = template.slots.getOrNull(course.slotIndex) ?: return@forEach

                course.activeWeeks.forEach { week ->
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = template.startDateMillis
                        firstDayOfWeek = Calendar.MONDAY
                        val currentDayOfWeek = get(Calendar.DAY_OF_WEEK)
                        val offsetToMonday = if (currentDayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - currentDayOfWeek
                        add(Calendar.DAY_OF_YEAR, offsetToMonday)
                        add(Calendar.WEEK_OF_YEAR, week - 1)
                        add(Calendar.DAY_OF_YEAR, course.dayOfWeek - 1)
                    }

                    val startCal = cal.clone() as Calendar
                    startCal.set(Calendar.HOUR_OF_DAY, slot.startHour)
                    startCal.set(Calendar.MINUTE, slot.startMinute)
                    startCal.set(Calendar.SECOND, 0)

                    val endCal = cal.clone() as Calendar
                    endCal.set(Calendar.HOUR_OF_DAY, slot.endHour)
                    endCal.set(Calendar.MINUTE, slot.endMinute)
                    endCal.set(Calendar.SECOND, 0)

                    val values = ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, startCal.timeInMillis)
                        put(CalendarContract.Events.DTEND, endCal.timeInMillis)
                        put(CalendarContract.Events.TITLE, course.name)
                        put(CalendarContract.Events.EVENT_LOCATION, course.location)
                        put(CalendarContract.Events.DESCRIPTION, "第${week}周 | 教师: ${course.teacher}")
                        put(CalendarContract.Events.CALENDAR_ID, calId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    }

                    val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    val eventId = uri?.lastPathSegment?.toLongOrNull()

                    if (eventId != null) {
                        val reminders = ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, eventId)
                            put(CalendarContract.Reminders.MINUTES, advanceMins)
                            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        }
                        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminders)
                        insertCount++
                    }
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "成功将 $insertCount 节课写入系统日历！请打开日历查看", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getSystemCalendarId(context: Context): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.IS_PRIMARY} = 1"
        try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, "${CalendarContract.Calendars.VISIBLE} = 1", null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return -1L
    }
}