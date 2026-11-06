package com.chageunchageun.app.engine

import com.chageunchageun.app.model.VehicleWarning

/**
 * 계기판 경고를 "지금 운전해도 되는지, 무엇을 해야 하는지"로 풀어 준다.
 *
 * ⚠ 경고 코드(키)는 목업용 이름이다. Pleos Safety API가 실제로 돌려주는 값을 확인한 뒤
 *   이 표의 키만 바꾸면 된다. 안내 문장은 일반적인 취급설명서 대응 요령 수준으로 작성했다.
 */
object WarningTranslator {

    private data class Rule(
        val title: String,
        val severity: Severity,
        val speech: String,
        val actions: List<SuggestedAction> = emptyList(),
    )

    private val toWorkshop = SuggestedAction.AddWaypoint("자동차 정비소", "정비소 경유")

    private val rules = mapOf(
        "ENGINE_OIL_PRESSURE" to Rule(
            "엔진오일 압력 경고", Severity.URGENT,
            "엔진오일 압력 경고가 켜졌어요. 계속 달리면 엔진이 크게 손상될 수 있어요. " +
                "안전한 곳에 바로 세우고 시동을 끈 뒤 긴급출동을 부르세요.",
        ),
        "COOLANT_TEMP" to Rule(
            "냉각수 온도 경고", Severity.URGENT,
            "엔진이 과열되고 있어요. 안전한 곳에 세우고 시동을 끄세요. 뜨거울 때는 냉각수 뚜껑을 열지 마세요.",
        ),
        "BRAKE_SYSTEM" to Rule(
            "브레이크 경고", Severity.URGENT,
            "브레이크 계통 경고예요. 주차 브레이크를 풀었는데도 켜져 있다면 운행을 멈추고 점검을 받으세요.",
        ),
        "CHARGING_SYSTEM" to Rule(
            "충전 계통 경고", Severity.WARN,
            "배터리 충전 경고가 켜졌어요. 주행 중 시동이 꺼질 수 있으니 오늘 안에 정비소에 들르세요.",
            listOf(toWorkshop),
        ),
        "CHECK_ENGINE" to Rule(
            "엔진 점검 경고", Severity.WARN,
            "엔진 점검 경고예요. 당장 멈출 필요는 없지만 이번 주 안에 정비소에서 확인받으세요.",
            listOf(toWorkshop),
        ),
        "TPMS" to Rule(
            "타이어 공기압 경고", Severity.WARN,
            "타이어 공기압 경고예요. 가까운 주유소나 정비소에서 공기압을 확인하세요.",
            listOf(SuggestedAction.AddWaypoint("타이어 공기 주입", "공기압 점검 가능한 곳 경유")),
        ),
        "LAMP_FAULT" to Rule(
            "램프 고장 경고", Severity.ADVISE,
            "전구가 나간 것 같아요. 주차한 뒤 등화 점검으로 어느 등인지 확인해 볼까요?",
            listOf(SuggestedAction.StartLampCheck),
        ),

        // ---- Pleos 공개 Safety 상태(PleosVehicleDataSource.warnings). LOW = 주의, CRITICALLY_LOW = 긴급 ----
        "ENGINE_OIL_LEVEL_LOW" to Rule(
            "엔진오일 양 부족", Severity.WARN,
            "엔진오일이 부족해요. 지금 운전은 할 수 있지만 오늘 안에 정비소나 주유소에서 엔진오일을 보충하세요.",
            listOf(toWorkshop),
        ),
        "ENGINE_OIL_LEVEL_LOW_CRITICAL" to Rule(
            "엔진오일 매우 부족", Severity.URGENT,
            "엔진오일이 거의 없어요. 이대로 달리면 엔진이 손상될 수 있어요. " +
                "운전하지 말고 정비소에 연락하거나 긴급출동을 부르세요.",
        ),
        "BRAKE_FLUID_LOW" to Rule(
            "브레이크액 부족", Severity.WARN,
            "브레이크액이 부족해요. 브레이크가 평소보다 밀리면 바로 세우세요. 오늘 안에 정비소에서 점검받으세요.",
            listOf(toWorkshop),
        ),
        "BRAKE_FLUID_LOW_CRITICAL" to Rule(
            "브레이크액 매우 부족", Severity.URGENT,
            "브레이크액이 거의 없어요. 브레이크가 제대로 듣지 않을 수 있어 위험해요. " +
                "운전하지 말고 긴급출동을 부르세요.",
        ),
        "TRANSMISSION_OIL_LOW" to Rule(
            "변속기 오일 부족", Severity.WARN,
            "변속기 오일이 부족해요. 지금 운전은 할 수 있지만 이번 주 안에 정비소에서 점검받으세요.",
            listOf(toWorkshop),
        ),
        "TRANSMISSION_OIL_LOW_CRITICAL" to Rule(
            "변속기 오일 매우 부족", Severity.URGENT,
            "변속기 오일이 거의 없어요. 계속 달리면 변속기가 손상될 수 있어요. " +
                "운전을 멈추고 정비소에 연락하세요.",
        ),
        "WASHER_FLUID_LOW" to Rule(
            "워셔액 부족", Severity.INFO,
            "워셔액이 부족해요. 운전에는 문제없지만 주유소나 마트에서 워셔액을 사서 채워 두세요.",
        ),
    )

    fun titleOf(code: String): String = rules[code]?.title ?: "차량 경고($code)"

    fun translate(warning: VehicleWarning): Finding {
        val rule = rules[warning.code] ?: Rule(
            "차량 경고", Severity.WARN,
            "계기판에 경고가 켜졌어요(${warning.rawText.ifBlank { warning.code }}). " +
                "의미를 모르겠다면 운행 전에 정비소에 문의하세요.",
            listOf(toWorkshop),
        )
        return Finding(
            id = "warning-${warning.code}",
            kind = CheckKind.DAILY,
            severity = rule.severity,
            title = rule.title,
            speech = rule.speech,
            actions = rule.actions,
        )
    }
}
