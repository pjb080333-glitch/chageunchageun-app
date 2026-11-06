package com.chageunchageun.app.data

import com.chageunchageun.app.model.CabinState
import com.chageunchageun.app.model.CarInfo
import com.chageunchageun.app.model.EnergyState
import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.Lamp
import com.chageunchageun.app.model.TireReading
import com.chageunchageun.app.model.VehicleSnapshot
import com.chageunchageun.app.model.VehicleWarning
import com.chageunchageun.app.model.Wheel

/** 데모 영상·심사 시연용 시나리오. 버튼 하나로 상황을 바꿔 가며 기능을 보여 준다. */
enum class DemoScenario(
    val label: String,
    val description: String,
    /** 오늘 밤 최저기온 예보 (하차 점검 블랙박스 안내용). */
    val tonightMinTempC: Float?,
    /** 마지막 운행 후 지난 날짜 (장기 미운행 시연용). */
    val daysSinceLastDrive: Int,
    /** 계절점검 시연을 위해 달을 고정 (null = 실제 날짜). */
    val demoMonth: Int? = null,
) {
    NORMAL("평상시", "이상 없음. 일정 브리핑만.", 5f, 1),
    WINTER_MORNING("겨울 아침", "11월 영하 6도, 뒤 왼쪽 타이어 미세 누설, 엔진오일 교환 임박.", -12f, 1, demoMonth = 11),
    WARNING_LIGHTS("경고등", "충전 계통 경고 + 반복되는 엔진 점검 경고.", 8f, 2),
    LOW_RANGE("주행가능거리 부족", "목적지까지 연료가 빠듯함.", 10f, 1),
    LONG_IDLE("장기 미운행", "20일 만의 운행, 공기압 저하.", 2f, 20),
    EXIT_MISTAKES("하차 실수", "실내등·뒷문·창문 미처리, 한파 예보.", -11f, 1, demoMonth = 12),
}

class MockVehicleDataSource : VehicleDataSource {

    var scenario: DemoScenario = DemoScenario.WINTER_MORNING
    /** 시뮬레이션용 누적 주행거리. 시동을 끌 때 운행 거리만큼 증가시킨다. */
    var odometerKm: Double = 48_620.0
    private val lampsOn = mutableSetOf<Lamp>()

    override suspend fun connect() = true

    override suspend fun supported(): Set<Feature> = Feature.entries.toSet()

    override suspend fun snapshot(): VehicleSnapshot {
        val now = System.currentTimeMillis()
        val base = VehicleSnapshot(
            timestampMillis = now,
            odometerKm = odometerKm,
            tires = tires(240f, 238f, 241f, 239f),
            energy = EnergyState(isEv = false, rangeKm = 420),
            cabin = CabinState(
                interiorLightOn = false, headlightOn = false, hazardOn = false,
                parkingBrakeOn = true, gearPark = true,
            ),
            outsideTempC = 14f,
            carInfo = CarInfo("Hyundai", "데모 차량", 2026),
        )
        return when (scenario) {
            DemoScenario.NORMAL -> base
            DemoScenario.WINTER_MORNING -> base.copy(
                tires = tires(236f, 235f, 205f, 234f),
                outsideTempC = -6f,
            )
            DemoScenario.WARNING_LIGHTS -> base.copy(
                warnings = listOf(VehicleWarning("CHARGING_SYSTEM"), VehicleWarning("CHECK_ENGINE")),
            )
            DemoScenario.LOW_RANGE -> base.copy(
                energy = EnergyState(isEv = false, rangeKm = 35, lowFuelWarning = true),
            )
            DemoScenario.LONG_IDLE -> base.copy(
                tires = tires(214f, 213f, 212f, 215f),
                outsideTempC = 1f,
            )
            DemoScenario.EXIT_MISTAKES -> base.copy(
                outsideTempC = -4f,
                cabin = CabinState(
                    interiorLightOn = true, headlightOn = false, hazardOn = false,
                    openDoors = listOf("뒷좌석 오른쪽"), openWindows = listOf("운전석"),
                    parkingBrakeOn = true, gearPark = true,
                ),
            )
        }
    }

    private fun tires(fl: Float, fr: Float, rl: Float, rr: Float) = listOf(
        TireReading(Wheel.FRONT_LEFT, fl),
        TireReading(Wheel.FRONT_RIGHT, fr),
        TireReading(Wheel.REAR_LEFT, rl),
        TireReading(Wheel.REAR_RIGHT, rr),
    )

    override suspend fun setSeatHeat(level: Int) = true
    override suspend fun setSteeringWheelHeat(on: Boolean) = true
    override suspend fun setSideMirrorHeat(on: Boolean) = true
    override suspend fun setCabinTemperature(celsius: Float) = true

    override suspend fun setLamp(lamp: Lamp, on: Boolean): Boolean {
        if (lamp.needsHelper) return false
        if (on) lampsOn += lamp else lampsOn -= lamp
        return true
    }

    override fun release() = Unit
}
