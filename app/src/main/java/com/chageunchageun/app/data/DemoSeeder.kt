package com.chageunchageun.app.data

import com.chageunchageun.app.engine.MaintenanceRecord
import com.chageunchageun.app.engine.TirePoint
import com.chageunchageun.app.engine.TripLog
import com.chageunchageun.app.engine.WarningEvent
import com.chageunchageun.app.model.Wheel

/**
 * 데모 시나리오에 맞는 과거 이력을 만든다 (공기압 추세, 정비 기록, 반복 경고).
 * mock 플레이버에서 시나리오를 바꿀 때만 사용한다.
 */
object DemoSeeder {

    private const val DAY = 24L * 60 * 60 * 1000
    private const val MONTH = 30L * DAY

    fun history(scenario: DemoScenario, odometerKm: Double, now: Long = System.currentTimeMillis()): History {
        val h = History()

        // 정비 기록: 기본은 모두 최근에 한 것으로.
        h.maintenance += MaintenanceRecord("engine_oil", odometerKm - 3_000, now - 3 * MONTH)
        h.maintenance += MaintenanceRecord("air_filter", odometerKm - 3_000, now - 3 * MONTH)
        h.maintenance += MaintenanceRecord("cabin_filter", odometerKm - 3_000, now - 3 * MONTH)
        h.maintenance += MaintenanceRecord("brake_fluid", odometerKm - 12_000, now - 10 * MONTH)
        h.maintenance += MaintenanceRecord("tire_rotation", odometerKm - 3_000, now - 3 * MONTH)

        // 출퇴근 위주의 평범한 운행 기록 (가혹 조건 아님).
        repeat(20) { i -> h.trips += TripLog(now - (i + 1) * DAY, 24.0, 1) }

        when (scenario) {
            DemoScenario.WINTER_MORNING -> {
                // 엔진오일: 약 9,700km 전 교환 → 300km 남음 (샘플 주기 10,000km 기준).
                h.maintenance.removeAll { it.itemId == "engine_oil" }
                h.maintenance += MaintenanceRecord("engine_oil", odometerKm - 9_700, now - 8 * MONTH)
                // 뒤 왼쪽 타이어만 3주간 꾸준히 하락 (주당 약 10kPa), 나머지는 안정.
                for (week in 3 downTo 1) {
                    val t = now - week * 7 * DAY
                    h.tirePoints += TirePoint(t, Wheel.FRONT_LEFT, 237f)
                    h.tirePoints += TirePoint(t, Wheel.FRONT_RIGHT, 236f)
                    h.tirePoints += TirePoint(t, Wheel.REAR_LEFT, 205f + week * 10f)
                    h.tirePoints += TirePoint(t, Wheel.REAR_RIGHT, 235f)
                }
            }
            DemoScenario.WARNING_LIGHTS -> {
                // 최근 한 달 동안 엔진 점검 경고가 세 번 켜졌다 꺼짐.
                h.warningEvents += WarningEvent(now - 25 * DAY, "CHECK_ENGINE")
                h.warningEvents += WarningEvent(now - 14 * DAY, "CHECK_ENGINE")
                h.warningEvents += WarningEvent(now - 5 * DAY, "CHECK_ENGINE")
            }
            else -> Unit
        }

        h.lastDriveEndMillis = now - scenario.daysSinceLastDrive * DAY
        return h
    }
}
