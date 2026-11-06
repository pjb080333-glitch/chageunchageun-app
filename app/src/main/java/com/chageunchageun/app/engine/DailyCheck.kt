package com.chageunchageun.app.engine

import com.chageunchageun.app.model.VehicleSnapshot
import kotlin.math.roundToInt

/** ⚠ 운전석 도어 안쪽 라벨 또는 취급설명서의 권장 공기압으로 설정해야 한다. */
data class TireSpec(val recommendedKpa: Float)

/** 일상점검: 시동을 켤 때마다 차량 데이터만으로 자동 판정한다. */
object DailyCheck {

    const val LOW_RATIO = 0.90f
    const val VERY_LOW_RATIO = 0.80f

    fun run(snapshot: VehicleSnapshot, spec: TireSpec): List<Finding> {
        val out = mutableListOf<Finding>()

        // 세 바퀴 이상이 함께 "조금 낮음"이면 바퀴별로 반복하지 않고 한 건으로 말한다.
        val mildlyLow = snapshot.tires.filter { t ->
            val r = t.pressureKpa?.div(spec.recommendedKpa) ?: return@filter false
            r in VERY_LOW_RATIO..<LOW_RATIO
        }
        val mergeMild = mildlyLow.size >= 3
        if (mergeMild) {
            out += Finding(
                id = "tire-low-all",
                kind = CheckKind.DAILY,
                severity = Severity.ADVISE,
                title = "타이어 공기압 전체적으로 낮음",
                speech = "타이어 공기압이 전체적으로 조금 낮아요. 이번 주 안에 주유소에서 함께 보충하세요.",
                actions = listOf(SuggestedAction.AddWaypoint("타이어 공기 주입", "공기압 점검 가능한 곳 경유")),
            )
        }

        snapshot.tires.forEach { tire ->
            val kpa = tire.pressureKpa ?: return@forEach
            val ratio = kpa / spec.recommendedKpa
            val lowPct = ((1f - ratio) * 100).roundToInt()
            when {
                ratio < VERY_LOW_RATIO -> out += Finding(
                    id = "tire-low-${tire.wheel.name}",
                    kind = CheckKind.DAILY,
                    severity = Severity.URGENT,
                    title = "${tire.wheel.label} 타이어 공기압 매우 낮음",
                    speech = "${tire.wheel.label} 타이어 공기압이 권장값보다 ${lowPct}% 낮아요. " +
                        "오늘 바로 공기를 넣거나 펑크가 났는지 확인하세요.",
                    actions = listOf(SuggestedAction.AddWaypoint("타이어 공기 주입", "공기압 점검 가능한 곳 경유")),
                    badge = "-${lowPct}%",
                )
                ratio < LOW_RATIO && !mergeMild -> out += Finding(
                    id = "tire-low-${tire.wheel.name}",
                    kind = CheckKind.DAILY,
                    severity = Severity.ADVISE,
                    title = "${tire.wheel.label} 타이어 공기압 낮음",
                    speech = "${tire.wheel.label} 타이어 공기압이 조금 낮아요. 이번 주 안에 보충하세요.",
                    badge = "-${lowPct}%",
                )
            }
        }

        snapshot.warnings.forEach { out += WarningTranslator.translate(it) }

        if (snapshot.energy.lowFuelWarning) {
            out += Finding(
                id = "low-fuel",
                kind = CheckKind.DAILY,
                severity = Severity.WARN,
                title = "연료 부족",
                speech = "연료가 얼마 남지 않았어요. 가는 길에 주유소를 들를까요?",
                actions = listOf(SuggestedAction.AddWaypoint("주유소", "주유소 경유")),
                badge = snapshot.energy.rangeKm?.let { "${it}km" },
            )
        }
        return out
    }
}
