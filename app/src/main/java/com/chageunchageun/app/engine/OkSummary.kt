package com.chageunchageun.app.engine

import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.VehicleSnapshot

/**
 * 홈의 "확인한 항목 이상 없음 (N개)" 목록 (#004 B).
 * 차량 데이터로 실제 확인했고 문제가 나오지 않은 항목만 넣는다. 값을 못 읽은 항목은 넣지 않는다
 * ("전반 정상"처럼 확인하지 않은 범위까지 말하지 않기 위해).
 */
object OkSummary {

    /** 시동을 켤 때(출발 브리핑) 확인한 항목. */
    fun briefing(snapshot: VehicleSnapshot, findings: List<Finding>, supported: Set<Feature>): List<String> {
        val ids = findings.map { it.id }
        val out = mutableListOf<String>()
        val tires = snapshot.tires.count { it.pressureKpa != null }
        if (tires > 0 && ids.none { it.startsWith("tire-") || it.startsWith("focus-leak-") || it == "focus-all-tires" }) {
            out += "타이어 공기압 (${tires}개)"
        }
        if (Feature.SAFETY in supported && snapshot.warnings.isEmpty() && ids.none { it.startsWith("focus-repeat-") }) {
            out += "차량 경고 없음"
        }
        snapshot.energy.rangeKm?.let { range ->
            if (ids.none { it == "range-short" || it == "low-fuel" }) out += "주행가능거리 ${thousands(range)}km"
        }
        if (snapshot.odometerKm != null && findings.none { it.kind == CheckKind.PERIODIC }) {
            out += "정기점검 시기"
        }
        return out
    }

    /** 시동을 끌 때(하차 점검) 확인한 항목. */
    fun exit(snapshot: VehicleSnapshot, supported: Set<Feature>): List<String> {
        val c = snapshot.cabin
        val out = mutableListOf<String>()
        if (c.interiorLightOn == false) out += "실내등 꺼짐"
        if (c.hazardOn == false) out += "비상등 꺼짐"
        if (c.headlightOn == false) out += "전조등 꺼짐"
        if (Feature.DOOR in supported && c.openDoors.isEmpty()) out += "문 닫힘"
        if (Feature.WINDOW in supported && c.openWindows.isEmpty()) out += "창문 닫힘"
        if (c.parkingBrakeOn == true) out += "주차 브레이크 채움"
        if (c.gearPark == true) out += "기어 P"
        return out
    }
}
