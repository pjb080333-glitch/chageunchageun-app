package com.chageunchageun.app.engine

import kotlin.math.roundToInt

/**
 * 정기점검 항목. 값은 반드시 해당 차종 취급설명서의 정기점검표(일반/가혹 조건)에서 가져온다.
 * severe* 값이 비어 있으면 가혹 조건에서도 일반 주기를 쓴다.
 */
data class MaintenanceItem(
    val id: String,
    val name: String,
    val intervalKm: Int?,
    val intervalMonths: Int?,
    val severeIntervalKm: Int? = null,
    val severeIntervalMonths: Int? = null,
)

data class MaintenanceRecord(val itemId: String, val doneAtKm: Double, val doneAtMillis: Long)

/** 정기점검: 주행거리(Odometer API로 자동 추적)와 날짜 중 먼저 도래하는 쪽으로 판단한다. */
object PeriodicCheck {

    const val SOON_KM = 500
    const val SOON_DAYS = 30
    private const val DAYS_PER_MONTH = 30.44

    fun evaluate(
        items: List<MaintenanceItem>,
        records: List<MaintenanceRecord>,
        odometerKm: Double,
        nowMillis: Long,
        severe: Boolean,
    ): List<Finding> = items.mapNotNull { item ->
        val last = records.filter { it.itemId == item.id }.maxByOrNull { it.doneAtMillis }
            ?: return@mapNotNull Finding(
                id = "periodic-${item.id}-unknown",
                kind = CheckKind.PERIODIC,
                severity = Severity.INFO,
                title = "${item.name} 기록 없음",
                speech = "${item.name.eulReul()} 마지막으로 언제 했는지 기록이 없어요. " +
                    "정비 영수증을 보고 한 번만 입력해 주세요.",
                actions = listOf(SuggestedAction.OpenChecklist),
            )

        val (kmLeft, daysLeft) = left(item, last, odometerKm, nowMillis, severe)

        val overdue = (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0)
        val soon = !overdue &&
            ((kmLeft != null && kmLeft <= SOON_KM) || (daysLeft != null && daysLeft <= SOON_DAYS))
        val basis = if (severe) " (운전 패턴상 가혹 조건 주기 적용)" else ""

        when {
            overdue -> Finding(
                id = "periodic-${item.id}",
                kind = CheckKind.PERIODIC,
                severity = Severity.WARN,
                title = "${item.name} 시기 지남",
                speech = "${item.name} 시기가 지났어요$basis. 가까운 정비소에 들러 주세요.",
                actions = listOf(
                    SuggestedAction.AddWaypoint("자동차 정비소", "정비소 경유"),
                    SuggestedAction.OpenChecklist,
                ),
                badge = overdueBadge(kmLeft, daysLeft),
            )
            soon -> Finding(
                id = "periodic-${item.id}",
                kind = CheckKind.PERIODIC,
                severity = Severity.ADVISE,
                title = "${item.name} 시기 임박",
                speech = "${item.name} 시기가 다가와요. ${remainText(kmLeft, daysLeft)} 남았어요$basis.",
                actions = listOf(SuggestedAction.OpenChecklist),
                badge = remainBadge(kmLeft, daysLeft),
            )
            else -> null
        }
    }

    /**
     * 기록이 있는 항목 중 가장 먼저 돌아오는 정기점검 (홈 "다가오는 점검" 카드용).
     * 판정 규칙은 [evaluate] 와 같고(주행거리·날짜 중 먼저 도래), 시기가 멀어도 돌려준다.
     */
    fun next(
        items: List<MaintenanceItem>,
        records: List<MaintenanceRecord>,
        odometerKm: Double,
        nowMillis: Long,
        severe: Boolean,
    ): NextService? = items.mapNotNull { item ->
        val last = records.filter { it.itemId == item.id }.maxByOrNull { it.doneAtMillis } ?: return@mapNotNull null
        val (kmLeft, daysLeft) = left(item, last, odometerKm, nowMillis, severe)
        if (kmLeft == null && daysLeft == null) return@mapNotNull null
        // 서로 단위가 다른 거리·날짜를 비교하지 않도록, 각자 주기 대비 남은 비율이 가장 작은 항목을 고른다.
        val km = (if (severe) item.severeIntervalKm else null) ?: item.intervalKm
        val months = (if (severe) item.severeIntervalMonths else null) ?: item.intervalMonths
        val ratio = minOf(
            if (kmLeft != null && km != null) kmLeft / km else Double.MAX_VALUE,
            if (daysLeft != null && months != null) daysLeft / (months * DAYS_PER_MONTH) else Double.MAX_VALUE,
        )
        ratio to NextService(item.name, kmLeft?.roundToInt(), daysLeft?.roundToInt())
    }.minByOrNull { it.first }?.second

    private fun left(
        item: MaintenanceItem,
        last: MaintenanceRecord,
        odometerKm: Double,
        nowMillis: Long,
        severe: Boolean,
    ): Pair<Double?, Double?> {
        val km = (if (severe) item.severeIntervalKm else null) ?: item.intervalKm
        val months = (if (severe) item.severeIntervalMonths else null) ?: item.intervalMonths
        val kmLeft = km?.let { last.doneAtKm + it - odometerKm }
        val daysLeft = months?.let {
            val dueMillis = last.doneAtMillis + (it * DAYS_PER_MONTH * DAY_MILLIS).toLong()
            (dueMillis - nowMillis).toDouble() / DAY_MILLIS
        }
        return kmLeft to daysLeft
    }

    private fun overdueBadge(kmLeft: Double?, daysLeft: Double?): String? = when {
        kmLeft != null && kmLeft <= 0 -> "+${thousands((-kmLeft).roundToInt())}km 초과"
        daysLeft != null && daysLeft <= 0 -> "${(-daysLeft).roundToInt()}일 지남"
        else -> null
    }

    private fun remainBadge(kmLeft: Double?, daysLeft: Double?): String? = when {
        kmLeft != null && kmLeft <= SOON_KM -> "${thousands(kmLeft.roundToInt())}km 남음"
        daysLeft != null && daysLeft <= SOON_DAYS -> "D-${daysLeft.roundToInt()}일"
        else -> null
    }

    private fun remainText(kmLeft: Double?, daysLeft: Double?): String {
        val parts = mutableListOf<String>()
        if (kmLeft != null && kmLeft <= SOON_KM) parts += "${kmLeft.roundToInt()}km"
        if (daysLeft != null && daysLeft <= SOON_DAYS) parts += "${daysLeft.roundToInt()}일"
        return parts.joinToString(" 또는 ")
    }
}

/** 다음 정기점검까지 남은 거리·날짜. 모르는 쪽은 null. */
data class NextService(val name: String, val kmLeft: Int?, val daysLeft: Int?) {
    /** 홈 카드 핵심 숫자: "D-24일", "약 2,190km" */
    val dDay: String? get() = daysLeft?.let { if (it >= 0) "D-${it}일" else "${-it}일 지남" }
    val kmText: String? get() = kmLeft?.let { if (it >= 0) "약 ${thousands(it)}km" else "${thousands(-it)}km 초과" }
}

/** 1234 → "1,234" */
fun thousands(n: Int): String = String.format(java.util.Locale.US, "%,d", n)

data class TripLog(val startMillis: Long, val distanceKm: Double, val hardBrakes: Int)

/**
 * 운전 패턴으로 취급설명서의 "가혹 조건" 주기를 적용할지 고른다.
 * 부품 마모를 측정하는 것이 아니라 공식 기준 중 어느 주기를 적용할지 선택하는 것.
 * ⚠ 기준값은 취급설명서의 가혹 조건 정의(단거리 반복 등)에 맞춰 조정할 것.
 * 급제동 횟수는 차속(PERF_VEHICLE_SPEED) 변화로 계산 (공식 튜토리얼의 안전 운전 점수 예제 참고).
 */
object DrivingPattern {
    const val MIN_TRIPS = 10
    const val SHORT_TRIP_KM = 8.0
    const val SHORT_TRIP_RATIO = 0.5
    const val HARD_BRAKES_PER_100KM = 10.0

    fun isSevere(trips: List<TripLog>): Boolean {
        if (trips.size < MIN_TRIPS) return false
        val shortRatio = trips.count { it.distanceKm < SHORT_TRIP_KM }.toDouble() / trips.size
        val totalKm = trips.sumOf { it.distanceKm }
        val hardPer100 = if (totalKm > 0) trips.sumOf { it.hardBrakes } * 100.0 / totalKm else 0.0
        return shortRatio >= SHORT_TRIP_RATIO || hardPer100 >= HARD_BRAKES_PER_100KM
    }
}
