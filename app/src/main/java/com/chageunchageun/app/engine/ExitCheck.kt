package com.chageunchageun.app.engine

import com.chageunchageun.app.model.VehicleSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 하차 점검: 시동을 끄는 순간에 확인한다.
 * 보험사 긴급출동 1위인 배터리 방전의 주요 원인(실내등·비상등 켜둠, 문 덜 닫힘, 겨울 블랙박스 상시 녹화)을 겨냥.
 */
object ExitCheck {

    /** 이 기온 이하로 내려가는 밤에는 블랙박스 주차 녹화를 끄도록 권한다. */
    const val DASHCAM_COLD_C = -10f

    fun run(snapshot: VehicleSnapshot, tonightMinTempC: Float?): List<Finding> {
        val c = snapshot.cabin
        val out = mutableListOf<Finding>()

        if (c.interiorLightOn == true) out += finding(
            "exit-interior", Severity.WARN, "실내등 켜짐",
            "실내등이 켜져 있어요. 그대로 두면 배터리가 방전될 수 있어요.",
        )
        if (c.hazardOn == true) out += finding(
            "exit-hazard", Severity.WARN, "비상등 켜짐",
            "비상등이 켜져 있어요. 필요한 상황이 아니라면 꺼 주세요.",
        )
        if (c.headlightOn == true) out += finding(
            "exit-headlight", Severity.ADVISE, "전조등 켜짐",
            "전조등이 켜져 있어요. 자동 모드가 아니라면 꺼 주세요.",
        )
        if (c.openDoors.isNotEmpty()) out += finding(
            "exit-door", Severity.WARN, "문 덜 닫힘",
            "${c.openDoors.joinToString(", ")} 문이 덜 닫혔어요.",
        )
        if (c.openWindows.isNotEmpty()) out += finding(
            "exit-window", Severity.ADVISE, "창문 열림",
            "${c.openWindows.joinToString(", ")} 창문이 열려 있어요.",
        )
        // #004 A (사용자 결정 A안): 기어가 P면 차는 고정돼 있으므로 참고로 낮추고 음성으로 말하지 않는다.
        // 기어가 P가 아니거나 기어 값을 모르면 지금처럼 주의 + 음성.
        if (c.parkingBrakeOn == false) out += if (c.gearPark == true) finding(
            "exit-parking-brake", Severity.INFO, "주차 브레이크 해제",
            "기어는 P예요. 경사로라면 주차 브레이크를 채워 주세요.",
        ) else finding(
            "exit-parking-brake", Severity.WARN, "주차 브레이크 해제",
            "주차 브레이크가 채워지지 않았어요.",
        )
        if (tonightMinTempC != null && tonightMinTempC <= DASHCAM_COLD_C) out += finding(
            "exit-dashcam-cold", Severity.ADVISE, "한파 예보: 블랙박스 주차 녹화",
            "오늘 밤 영하 ${abs(tonightMinTempC).roundToInt()}도까지 내려가요. " +
                "블랙박스 주차 녹화를 끄면 배터리 방전을 줄일 수 있어요.",
        )
        return out
    }

    /** 하차 점검 음성. 참고(INFO) 항목은 화면 카드로만 보여 주고 말하지 않는다. */
    fun speech(findings: List<Finding>): String {
        val spoken = findings.filter { it.severity != Severity.INFO }
        return if (spoken.isEmpty()) {
            "내리기 전 확인 끝. 이상 없어요."
        } else {
            "내리기 전에 확인해 주세요. " + spoken.joinToString(" ") { it.speech }
        }
    }

    private fun finding(id: String, severity: Severity, title: String, speech: String) =
        Finding(id, CheckKind.EXIT, severity, title, speech)
}
