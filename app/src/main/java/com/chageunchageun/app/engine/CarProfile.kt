package com.chageunchageun.app.engine

/**
 * 차종별 공식 기준값 묶음.
 *
 * ⚠⚠ sample() 의 숫자는 데모 동작용 샘플 값이다. 실제 수치가 아니다.
 *     출품·출시 전 반드시 해당 차종 취급설명서의 정기점검표와 도어 라벨의 권장 공기압으로 교체할 것.
 *     (CarInfo API의 제조사·모델·연식으로 프로필을 고르도록 확장 예정)
 */
data class CarProfile(
    val name: String,
    val tireSpec: TireSpec,
    val maintenanceItems: List<MaintenanceItem>,
) {
    companion object {
        fun sample() = CarProfile(
            name = "샘플 차종 (취급설명서 값으로 교체 필요)",
            tireSpec = TireSpec(recommendedKpa = 240f),
            maintenanceItems = listOf(
                MaintenanceItem("engine_oil", "엔진오일 교환", intervalKm = 10_000, intervalMonths = 12,
                    severeIntervalKm = 5_000, severeIntervalMonths = 6),
                MaintenanceItem("air_filter", "에어클리너 필터", intervalKm = 20_000, intervalMonths = 24,
                    severeIntervalKm = 10_000, severeIntervalMonths = 12),
                MaintenanceItem("cabin_filter", "에어컨 필터", intervalKm = 15_000, intervalMonths = 12),
                MaintenanceItem("brake_fluid", "브레이크액", intervalKm = 40_000, intervalMonths = 24),
                MaintenanceItem("tire_rotation", "타이어 위치 교환", intervalKm = 10_000, intervalMonths = null),
            ),
        )
    }
}
