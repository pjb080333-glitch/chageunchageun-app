package com.chageunchageun.app.model

/** 차량 데이터 모델. Android·Pleos 의존성 없이 순수 Kotlin으로 유지 (단위 테스트 가능). */

enum class Wheel(val label: String) {
    FRONT_LEFT("앞 왼쪽"),
    FRONT_RIGHT("앞 오른쪽"),
    REAR_LEFT("뒤 왼쪽"),
    REAR_RIGHT("뒤 오른쪽"),
}

data class TireReading(val wheel: Wheel, val pressureKpa: Float?)

/** Safety API 경고. code 값은 실제 SDK 확인 후 WarningTranslator 매핑과 맞춰야 함. */
data class VehicleWarning(val code: String, val rawText: String = "")

data class EnergyState(
    val isEv: Boolean = false,
    val rangeKm: Int? = null,
    val lowFuelWarning: Boolean = false,
    val evSocPercent: Int? = null,
)

/** 하차 점검에 쓰는 실내·차체 상태. null = 이 차에서 확인 불가. */
data class CabinState(
    val interiorLightOn: Boolean? = null,
    val headlightOn: Boolean? = null,
    val hazardOn: Boolean? = null,
    val openDoors: List<String> = emptyList(),
    val openWindows: List<String> = emptyList(),
    val parkingBrakeOn: Boolean? = null,
    /** 기어가 P(주차)인지. null = 이 차에서 확인 불가. */
    val gearPark: Boolean? = null,
)

data class CarInfo(val make: String, val model: String, val year: Int?)

data class VehicleSnapshot(
    val timestampMillis: Long,
    val odometerKm: Double? = null,
    val tires: List<TireReading> = emptyList(),
    val warnings: List<VehicleWarning> = emptyList(),
    val energy: EnergyState = EnergyState(),
    val cabin: CabinState = CabinState(),
    val outsideTempC: Float? = null,
    val carInfo: CarInfo? = null,
) {
    /** 주차 브레이크가 채워져 있거나 기어가 P면 주차로 보고 점검표·등화 점검을 진행한다. */
    val isParked: Boolean get() = cabin.parkingBrakeOn == true || cabin.gearPark == true
}

/** 차종마다 지원 범위가 다르므로, capability 확인 결과를 이 집합으로 표현한다. */
enum class Feature {
    TIRE, SAFETY, ODOMETER, FUEL_RANGE, EV_BATTERY,
    LIGHT_READ, LIGHT_CONTROL, DOOR, WINDOW, BRAKE,
    HVAC, SEAT_HEAT, WHEEL_HEAT, MIRROR_HEAT, OUTSIDE_TEMP,
}

enum class Lamp(val label: String, val needsHelper: Boolean = false) {
    LOW_BEAM("전조등(하향)"),
    HIGH_BEAM("상향등"),
    TAIL("미등"),
    TURN_LEFT("왼쪽 방향지시등"),
    TURN_RIGHT("오른쪽 방향지시등"),
    HAZARD("비상등"),
    BRAKE("브레이크등", needsHelper = true),
    REVERSE("후진등", needsHelper = true),
    PLATE("번호판등", needsHelper = true),
}
