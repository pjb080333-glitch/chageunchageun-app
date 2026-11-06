package com.chageunchageun.app.engine

import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.VehicleSnapshot
import kotlin.math.roundToInt

enum class ItemMode(val label: String) { AUTO("자동 확인"), VISUAL("눈으로 확인") }

/** 점검표 항목. AUTO는 앱이 판정(autoResult), VISUAL은 운전자가 "확인했어요/교체 필요"로 체크. */
data class ChecklistItem(
    val id: String,
    val name: String,
    val how: String,
    val mode: ItemMode,
    val autoResult: Boolean? = null,
)

enum class SeasonPhase(val label: String) {
    PRE_WINTER("동절기 전 점검"),
    WINTER("동절기"),
    PRE_SUMMER("하절기 전 점검"),
    SUMMER("하절기"),
    NONE("평상시"),
}

/** 계절점검 + 겨울 출발 프리셋 제안 + 장기 미운행 후 첫 시동 점검. */
object SeasonalCheck {

    /** 이 기온 이하이면 열선 프리셋을 제안한다. */
    const val COLD_PRESET_C = 3f
    const val LONG_IDLE_DAYS = 14

    fun phase(month: Int): SeasonPhase = when (month) {
        11 -> SeasonPhase.PRE_WINTER
        12, 1, 2 -> SeasonPhase.WINTER
        6 -> SeasonPhase.PRE_SUMMER
        7, 8 -> SeasonPhase.SUMMER
        else -> SeasonPhase.NONE
    }

    fun checklist(phase: SeasonPhase, snapshot: VehicleSnapshot, spec: TireSpec): List<ChecklistItem> {
        val tireItem = tireItem(snapshot, spec)
        val coolant = ChecklistItem(
            "coolant", "냉각수", "엔진이 식은 상태에서 보조 탱크 눈금이 MIN과 MAX 사이인지 확인하세요.", ItemMode.VISUAL,
        )
        val wiper = ChecklistItem(
            "wiper", "와이퍼", "작동시켰을 때 줄무늬가 남거나 고무가 갈라졌는지 확인하세요.", ItemMode.VISUAL,
        )
        return when (phase) {
            SeasonPhase.PRE_WINTER, SeasonPhase.WINTER -> listOf(
                tireItem,
                coolant.copy(name = "냉각수(부동액)"),
                ChecklistItem(
                    "battery", "배터리",
                    "시동이 평소보다 무겁게 걸리거나 오래 사용한 배터리라면 정비소에서 점검받으세요.", ItemMode.VISUAL,
                ),
                wiper,
                ChecklistItem("washer", "워셔액", "겨울용 워셔액으로 채워져 있는지 확인하세요.", ItemMode.VISUAL),
                ChecklistItem(
                    "heaters", "열선",
                    "겨울 출발 준비를 한 번 실행해 시트·핸들이 따뜻해지는지 확인하세요.", ItemMode.VISUAL,
                ),
            )
            SeasonPhase.PRE_SUMMER, SeasonPhase.SUMMER -> listOf(
                tireItem,
                coolant,
                ChecklistItem("aircon", "에어컨", "에어컨을 켜고 1~2분 안에 찬바람이 나오는지 확인하세요.", ItemMode.VISUAL),
                wiper.copy(how = "장마 전, 작동시켰을 때 줄무늬가 남는지 확인하세요."),
            )
            SeasonPhase.NONE -> listOf(tireItem, coolant, wiper)
        }
    }

    private fun tireItem(snapshot: VehicleSnapshot, spec: TireSpec): ChecklistItem {
        val readings = snapshot.tires.mapNotNull { it.pressureKpa }
        return if (readings.isEmpty()) {
            ChecklistItem("tires", "타이어 공기압·마모", "주유소에서 공기압을 재고, 홈 깊이를 확인하세요.", ItemMode.VISUAL)
        } else {
            ChecklistItem(
                "tires", "타이어 공기압", "차량 센서 값으로 자동 확인했어요.", ItemMode.AUTO,
                autoResult = readings.all { it >= spec.recommendedKpa * DailyCheck.LOW_RATIO },
            )
        }
    }

    fun findings(
        phase: SeasonPhase,
        snapshot: VehicleSnapshot,
        supported: Set<Feature>,
        seasonalDoneThisPhase: Boolean,
        daysSinceLastDrive: Int?,
    ): List<Finding> {
        val out = mutableListOf<Finding>()

        if ((phase == SeasonPhase.PRE_WINTER || phase == SeasonPhase.PRE_SUMMER) && !seasonalDoneThisPhase) {
            out += Finding(
                id = "seasonal-${phase.name}",
                kind = CheckKind.SEASONAL,
                severity = Severity.ADVISE,
                title = phase.label,
                speech = "${phase.label}표가 준비돼 있어요. 주차했을 때 5분이면 끝나요.",
                actions = listOf(SuggestedAction.OpenChecklist),
            )
        }

        val temp = snapshot.outsideTempC
        val canHeat = supported.any { it == Feature.SEAT_HEAT || it == Feature.WHEEL_HEAT || it == Feature.MIRROR_HEAT }
        if (temp != null && temp <= COLD_PRESET_C && canHeat) {
            val tempText = if (temp < 0) "영하 ${(-temp).roundToInt()}도" else "${temp.roundToInt()}도"
            out += Finding(
                id = "winter-preset",
                kind = CheckKind.SEASONAL,
                severity = Severity.INFO,
                title = "겨울 출발 준비",
                speech = "바깥 기온이 ${tempText}예요. 시트·핸들 열선과 사이드미러 열선을 켤까요?",
                actions = listOf(SuggestedAction.WinterPreset),
                badge = "${temp.roundToInt()}°C",
            )
        }

        if (daysSinceLastDrive != null && daysSinceLastDrive >= LONG_IDLE_DAYS) {
            out += Finding(
                id = "long-idle",
                kind = CheckKind.SEASONAL,
                severity = Severity.ADVISE,
                title = "${daysSinceLastDrive}일 만의 운행",
                speech = "${daysSinceLastDrive}일 만에 운행하시네요. 오래 세워두면 배터리와 타이어 공기압이 약해질 수 있어요. " +
                    "가능하면 오늘은 조금 길게 달려 배터리를 충전해 주세요.",
                badge = "${daysSinceLastDrive}일",
            )
        }
        return out
    }
}
