package com.chageunchageun.app.engine

import com.chageunchageun.app.model.EnergyState
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** 다음 일정 + 내비 경로 추정값. */
data class UpcomingTrip(
    val title: String,
    val startMillis: Long,
    val destination: String,
    val distanceKm: Double,
    val durationMin: Int,
)

/** 출발 브리핑: 일정 도착 시간에 맞춘 출발 여유와, 주행가능거리가 충분한지 판단한다. */
object DepartureBriefing {

    /** 도착 후 주차·이동에 쓰는 여유(분). */
    const val BUFFER_MIN = 10

    /** 주행가능거리 표시값의 오차를 감안한 여유 배수. */
    const val RANGE_MARGIN = 1.3

    fun run(
        trip: UpcomingTrip?,
        nowMillis: Long,
        energy: EnergyState,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Finding> {
        if (trip == null) return emptyList()
        val out = mutableListOf<Finding>()

        val minutesUntilStart = ((trip.startMillis - nowMillis) / 60_000L).toInt()
        val margin = minutesUntilStart - trip.durationMin
        val at = clock(trip.startMillis, zone)
        val (severity, speech) = when {
            margin >= BUFFER_MIN ->
                Severity.INFO to "$at ${trip.title} 일정이 있어요. 지금 출발하면 ${margin}분 여유가 있어요."
            margin >= 0 ->
                Severity.ADVISE to "$at ${trip.title} 일정이에요. 여유가 ${margin}분뿐이라 바로 출발하는 게 좋아요."
            else ->
                Severity.WARN to "$at ${trip.title} 일정에 약 ${-margin}분 늦을 수 있어요. 미리 연락해 두세요."
        }
        out += Finding(
            id = "departure",
            kind = CheckKind.DEPARTURE,
            severity = severity,
            title = "다음 일정: ${trip.title}",
            speech = speech,
            actions = listOf(SuggestedAction.Navigate(trip.destination)),
            badge = if (margin >= 0) "여유 ${margin}분" else "약 ${-margin}분 늦음",
        )

        rangeCheck(trip.destination, trip.distanceKm, energy)?.let { out += it }
        return out
    }

    /**
     * 목적지까지 거리 대비 주행가능거리가 부족한지. 일정 없이 내장 내비 목적지만 있을 때도 쓴다(#003 B7).
     * 규칙은 기존과 같다: 주행가능거리 < 거리 × [RANGE_MARGIN] 이면 경고.
     */
    fun rangeCheck(destination: String, distanceKm: Double, energy: EnergyState): Finding? {
        val range = energy.rangeKm ?: return null
        if (range >= distanceKm * RANGE_MARGIN) return null
        val stop = if (energy.isEv) "전기차 충전소" else "주유소"
        return Finding(
            id = "range-short",
            kind = CheckKind.DEPARTURE,
            severity = Severity.WARN,
            title = "주행가능거리 부족",
            speech = "주행가능거리가 ${range}km라 ${destination}까지 " +
                "${distanceKm.roundToInt()}km를 가기엔 빠듯해요. 가는 길에 ${stop.eulReul()} 들를까요?",
            actions = listOf(SuggestedAction.AddWaypoint(stop, "$stop 경유")),
            badge = "${range}km / ${distanceKm.roundToInt()}km",
        )
    }

    internal fun clock(millis: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return if (t.minute == 0) "${t.hour}시" else "${t.hour}시 ${t.minute}분"
    }
}
