package com.chageunchageun.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** @param point 일정 설명 칸에 적힌 geo:위도,경도 (없으면 장소 이름으로 좌표를 찾는다) */
data class ScheduleEvent(val title: String, val startMillis: Long, val location: String?, val point: GeoPoint? = null)

interface ScheduleSource {
    /** 지금부터 withinHours 시간 안에 시작하는 일정, 시작 순. */
    suspend fun upcoming(withinHours: Int): List<ScheduleEvent>
}

/** 기기 캘린더(CalendarContract)에서 일정을 읽는다. READ_CALENDAR 권한이 없으면 빈 목록. */
class CalendarScheduleSource(private val context: Context) : ScheduleSource {

    override suspend fun upcoming(withinHours: Int): List<ScheduleEvent> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        val now = System.currentTimeMillis()
        val end = now + withinHours * 60L * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DESCRIPTION,
        )
        val events = mutableListOf<ScheduleEvent>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
            ?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(3) == 1) continue // 종일 일정은 출발 시간 계산에서 제외
                    events += ScheduleEvent(
                        title = c.getString(0) ?: "일정",
                        startMillis = c.getLong(1),
                        location = c.getString(2)?.takeIf { it.isNotBlank() },
                        point = parseGeo(c.getString(4)),
                    )
                }
            }
        events.filter { it.startMillis >= now }
    }
}

/**
 * 데모용: 항상 "정확히 60분 뒤 광명역 KTX 탑승"이 있는 것으로 가정.
 * 시동 켜는 순간 호출되므로 mock 경로(45분)와 합쳐 늘 "15분 여유"가 나온다.
 */
class MockScheduleSource : ScheduleSource {
    override suspend fun upcoming(withinHours: Int): List<ScheduleEvent> {
        val inOneHour = System.currentTimeMillis() + 60 * 60_000L
        return listOf(ScheduleEvent("KTX 탑승 (부산행)", inOneHour, "광명역"))
    }
}
