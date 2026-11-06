package com.chageunchageun.app.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Log
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 시연·검증용(debug 빌드의 시연 인텐트 demo_action=SEED_CALENDAR 에서만 호출).
 * 로컬 캘린더 계정 "차근차근 테스트"를 만들고, 그 안의 기존 테스트 일정을 지운 뒤 KTX·SRT 역 일정 4개를 넣는다.
 * WRITE_CALENDAR 는 debug 매니페스트에만 선언(release 에는 없음).
 */
object CalendarSeeder {
    const val ACCOUNT = "차근차근 테스트"
    private const val TAG = "ChageunSeed"

    private data class Seed(val title: String, val place: String, val start: (Long) -> Long, val geo: String)

    private const val MIN = 60_000L

    private val seeds = listOf(
        Seed("KTX 탑승 (부산행)", "광명역", { it + 60 * MIN }, "geo:37.4166,126.8849"),
        Seed("KTX 탑승 (강릉행)", "서울역", { it + 180 * MIN }, "geo:37.5547,126.9707"),
        Seed("SRT 탑승", "수서역", { tomorrowAt(8, 30) }, "geo:37.4875,127.1018"),
        Seed("대전 출장", "대전역", { it + 240 * MIN }, "geo:36.3315,127.4346"),
    )

    /**
     * @param withGeo false 면 첫 일정(광명역)의 설명 칸 좌표를 비워 지오코딩 경로를 확인한다.
     * @return 넣은 일정 수, 실패하면 -1
     */
    fun seed(context: Context, withGeo: Boolean = true, now: Long = System.currentTimeMillis()): Int {
        if (context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "WRITE_CALENDAR 권한 없음 (adb shell pm grant <패키지> android.permission.WRITE_CALENDAR)")
            return -1
        }
        return runCatching {
            val cr = context.contentResolver
            val calId = calendarId(context) ?: error("캘린더 생성 실패")
            val deleted = cr.delete(Events.CONTENT_URI.asSyncAdapter(), "${Events.CALENDAR_ID}=?", arrayOf(calId.toString()))
            val tz = TimeZone.getDefault().id
            seeds.forEachIndexed { i, s ->
                val start = s.start(now)
                cr.insert(Events.CONTENT_URI, ContentValues().apply {
                    put(Events.CALENDAR_ID, calId)
                    put(Events.TITLE, s.title)
                    put(Events.EVENT_LOCATION, s.place)
                    put(Events.DESCRIPTION, if (withGeo || i != 0) "${s.geo} (차근차근 테스트 일정)" else "(차근차근 테스트 일정, 좌표 없음)")
                    put(Events.DTSTART, start)
                    put(Events.DTEND, start + 60 * MIN)
                    put(Events.EVENT_TIMEZONE, tz)
                })
            }
            Log.i(TAG, "캘린더 $calId: 기존 $deleted 개 삭제, ${seeds.size}개 추가 (withGeo=$withGeo)")
            seeds.size
        }.onFailure { Log.w(TAG, "seed 실패", it) }.getOrDefault(-1)
    }

    private fun calendarId(context: Context): Long? {
        val cr = context.contentResolver
        cr.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL), null,
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        val uri = cr.insert(Calendars.CONTENT_URI.asSyncAdapter(), ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, ACCOUNT)
            put(Calendars.CALENDAR_DISPLAY_NAME, ACCOUNT)
            put(Calendars.CALENDAR_COLOR, Color.rgb(0x2E, 0x4A, 0x7F))
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        })
        return uri?.lastPathSegment?.toLongOrNull()
    }

    private fun Uri.asSyncAdapter(): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun tomorrowAt(h: Int, m: Int): Long =
        LocalDate.now().plusDays(1).atTime(LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
