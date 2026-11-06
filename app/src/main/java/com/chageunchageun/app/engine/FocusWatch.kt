package com.chageunchageun.app.engine

import com.chageunchageun.app.model.Wheel
import kotlin.math.abs
import kotlin.math.roundToInt

data class TirePoint(val timeMillis: Long, val wheel: Wheel, val kpa: Float)
data class WarningEvent(val timeMillis: Long, val code: String)

/** 중점관리: 한 번의 값이 아니라 추세와 반복을 본다. */
object FocusWatch {

    const val WINDOW_DAYS = 21
    const val MIN_POINTS = 3
    /** 이 이상 빠지면 누설 의심 (kPa/주). */
    const val LEAK_KPA_PER_WEEK = 7f
    /** 다른 바퀴가 이 범위 안이면 "그대로"로 본다 (kPa/주). */
    const val STABLE_KPA_PER_WEEK = 3f
    const val REPEAT_COUNT = 3
    const val REPEAT_WINDOW_DAYS = 30

    fun tireTrend(points: List<TirePoint>, nowMillis: Long): List<Finding> {
        val recent = points.filter { nowMillis - it.timeMillis <= WINDOW_DAYS * DAY_MILLIS }
        val slopes: Map<Wheel, Float> = Wheel.entries.mapNotNull { wheel ->
            slopePerWeek(recent.filter { it.wheel == wheel })?.let { wheel to it }
        }.toMap()
        if (slopes.size < 2) return emptyList()

        val leaking = slopes.filterValues { it <= -LEAK_KPA_PER_WEEK }
        val others = slopes.filterKeys { it !in leaking.keys }

        if (leaking.isNotEmpty() && others.isNotEmpty() && others.values.all { abs(it) < STABLE_KPA_PER_WEEK }) {
            return leaking.map { (wheel, slope) ->
                Finding(
                    id = "focus-leak-${wheel.name}",
                    kind = CheckKind.FOCUS,
                    severity = Severity.WARN,
                    title = "${wheel.label} 타이어 미세 누설 의심",
                    speech = "${wheel.label} 타이어만 일주일에 약 ${(-slope).roundToInt()}kPa씩 공기압이 빠지고 있어요. " +
                        "다른 바퀴는 그대로라 못이나 밸브 문제일 수 있어요. 타이어 점검을 받아 보세요.",
                    actions = listOf(SuggestedAction.AddWaypoint("타이어 수리", "타이어 점검 가능한 곳 경유")),
                    badge = "-${(-slope).roundToInt()}kPa/주",
                )
            }
        }
        if (slopes.size == Wheel.entries.size && slopes.values.all { it <= -LEAK_KPA_PER_WEEK }) {
            return listOf(
                Finding(
                    id = "focus-all-tires",
                    kind = CheckKind.FOCUS,
                    severity = Severity.INFO,
                    title = "네 바퀴 공기압 동반 하락",
                    speech = "네 바퀴 모두 비슷하게 공기압이 내려갔어요. 기온이 떨어지면 흔한 일이니 다음 주유 때 함께 보충해 주세요.",
                ),
            )
        }
        return emptyList()
    }

    fun repeatedWarnings(events: List<WarningEvent>, nowMillis: Long): List<Finding> =
        events.filter { nowMillis - it.timeMillis <= REPEAT_WINDOW_DAYS * DAY_MILLIS }
            .groupingBy { it.code }.eachCount()
            .filterValues { it >= REPEAT_COUNT }
            .map { (code, count) ->
                Finding(
                    id = "focus-repeat-$code",
                    kind = CheckKind.FOCUS,
                    severity = Severity.WARN,
                    title = "반복되는 경고: ${WarningTranslator.titleOf(code)}",
                    speech = "최근 ${REPEAT_WINDOW_DAYS}일 동안 같은 경고가 ${count}번 켜졌어요. " +
                        "일시적인 현상이 아닐 수 있으니 정비소 점검을 받아 보세요.",
                    actions = listOf(SuggestedAction.AddWaypoint("자동차 정비소", "정비소 경유")),
                    badge = "${REPEAT_WINDOW_DAYS}일 ${count}회",
                )
            }

    /** 최소제곱 기울기 (kPa/주). 점이 부족하면 null. */
    internal fun slopePerWeek(points: List<TirePoint>): Float? {
        if (points.size < MIN_POINTS) return null
        val weekMillis = 7.0 * DAY_MILLIS
        val t0 = points.minOf { it.timeMillis }
        val xs = points.map { (it.timeMillis - t0) / weekMillis }
        val ys = points.map { it.kpa.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        if (sxx == 0.0) return null
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        return (sxy / sxx).toFloat()
    }
}
