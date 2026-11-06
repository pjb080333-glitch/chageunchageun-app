package com.chageunchageun.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.chageunchageun.app.data.DemoScenario
import com.chageunchageun.app.engine.BriefingComposer
import com.chageunchageun.app.engine.CarProfile
import com.chageunchageun.app.engine.DailyCheck
import com.chageunchageun.app.engine.DepartureBriefing
import com.chageunchageun.app.engine.ExitCheck
import com.chageunchageun.app.engine.FocusWatch
import com.chageunchageun.app.engine.MaintenanceRecord
import com.chageunchageun.app.engine.NextService
import com.chageunchageun.app.engine.OkSummary
import com.chageunchageun.app.engine.PeriodicCheck
import com.chageunchageun.app.engine.SeasonPhase
import com.chageunchageun.app.engine.SeasonalCheck
import com.chageunchageun.app.engine.TirePoint
import com.chageunchageun.app.engine.UpcomingTrip
import com.chageunchageun.app.engine.WarningEvent
import com.chageunchageun.app.model.CabinState
import com.chageunchageun.app.model.EnergyState
import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.TireReading
import com.chageunchageun.app.model.VehicleSnapshot
import com.chageunchageun.app.model.VehicleWarning
import com.chageunchageun.app.model.Wheel
import com.chageunchageun.app.ui.theme.ChageunTheme

// 홈 디자인 미리보기 (#004 B). 차량 앱 영역 약 1707×1210, 다크. 카드 내용은 점검 엔진이 실제로 만든 결과.

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 1_800_000_000_000L
private const val ODO = 48_620.0
private val profile = CarProfile.sample()
private val allFeatures = Feature.entries.toSet()

private fun tires(fl: Float, fr: Float, rl: Float, rr: Float) = listOf(
    TireReading(Wheel.FRONT_LEFT, fl), TireReading(Wheel.FRONT_RIGHT, fr),
    TireReading(Wheel.REAR_LEFT, rl), TireReading(Wheel.REAR_RIGHT, rr),
)

private fun snap(tires: List<TireReading>, tempC: Float, warnings: List<VehicleWarning> = emptyList(), cabin: CabinState = CabinState()) =
    VehicleSnapshot(NOW, ODO, tires, warnings, EnergyState(rangeKm = 420), cabin, tempC)

private fun records(oilKmAgo: Double) = listOf(
    MaintenanceRecord("engine_oil", ODO - oilKmAgo, NOW - 240 * DAY),
    MaintenanceRecord("air_filter", ODO - 3_000, NOW - 90 * DAY),
    MaintenanceRecord("cabin_filter", ODO - 3_000, NOW - 90 * DAY),
    MaintenanceRecord("brake_fluid", ODO - 12_000, NOW - 300 * DAY),
    MaintenanceRecord("tire_rotation", ODO - 3_000, NOW - 90 * DAY),
)

private fun baseState(findings: List<com.chageunchageun.app.engine.Finding>, s: VehicleSnapshot, oilKmAgo: Double) = UiState(
    isDemo = true,
    ignitionOn = true,
    briefing = BriefingComposer.sort(findings),
    lastSpeech = BriefingComposer.speech(findings),
    odometerKm = ODO,
    outsideTempC = s.outsideTempC,
    okItems = OkSummary.briefing(s, findings, allFeatures),
    nextService = PeriodicCheck.next(profile.maintenanceItems, records(oilKmAgo), ODO, NOW, false),
    voiceAvailable = true,
)

private fun winterState(): UiState {
    val s = snap(tires(236f, 235f, 205f, 234f), -6f)
    val points = (3 downTo 1).flatMap { w ->
        val t = NOW - w * 7 * DAY
        listOf(
            TirePoint(t, Wheel.FRONT_LEFT, 237f), TirePoint(t, Wheel.FRONT_RIGHT, 236f),
            TirePoint(t, Wheel.REAR_LEFT, 205f + w * 10f), TirePoint(t, Wheel.REAR_RIGHT, 235f),
        )
    } + Wheel.entries.map { TirePoint(NOW, it, s.tires.first { t -> t.wheel == it }.pressureKpa!!) }
    val trip = UpcomingTrip("거래처 미팅", NOW + 60 * 60_000L, "광명역", 29.4, 45)
    val findings = buildList {
        addAll(DepartureBriefing.run(trip, NOW, s.energy))
        addAll(DailyCheck.run(s, profile.tireSpec).filterNot { it.id == "tire-low-REAR_LEFT" })
        addAll(PeriodicCheck.evaluate(profile.maintenanceItems, records(9_700.0), ODO, NOW, false))
        addAll(SeasonalCheck.findings(SeasonPhase.PRE_WINTER, s, allFeatures, false, 1))
        addAll(FocusWatch.tireTrend(points, NOW))
    }
    return baseState(findings, s, 9_700.0).copy(
        scenario = DemoScenario.WINTER_MORNING,
        nextTripLine = "${DepartureBriefing.clock(trip.startMillis, java.time.ZoneId.of("Asia/Seoul"))} 거래처 미팅 · 광명역",
    )
}

private fun warningState(): UiState {
    val s = snap(tires(240f, 238f, 241f, 239f), 8f, listOf(VehicleWarning("CHARGING_SYSTEM"), VehicleWarning("CHECK_ENGINE")))
    val events = listOf(25L, 14L, 5L, 0L).map { WarningEvent(NOW - it * DAY, "CHECK_ENGINE") }
    val findings = buildList {
        addAll(DailyCheck.run(s, profile.tireSpec))
        addAll(PeriodicCheck.evaluate(profile.maintenanceItems, records(10_800.0), ODO, NOW, false))
        addAll(FocusWatch.repeatedWarnings(events, NOW))
    }
    return baseState(findings, s, 10_800.0).copy(scenario = DemoScenario.WARNING_LIGHTS)
}

private fun exitState(): UiState {
    val cabin = CabinState(
        interiorLightOn = true, headlightOn = false, hazardOn = false,
        openDoors = listOf("뒷좌석 오른쪽"), openWindows = listOf("운전석"),
        parkingBrakeOn = false, gearPark = true,
    )
    val s = snap(tires(240f, 238f, 241f, 239f), -4f, cabin = cabin)
    val findings = BriefingComposer.sort(ExitCheck.run(s, -11f))
    return UiState(
        isDemo = true, scenario = DemoScenario.EXIT_MISTAKES, ignitionOn = false,
        exitFindings = findings, exitDone = true, lastSpeech = ExitCheck.speech(findings),
        odometerKm = ODO, outsideTempC = -4f, okItems = OkSummary.exit(s, allFeatures),
        nextService = NextService("엔진오일 교환", 2_190, 24), voiceAvailable = true,
    )
}

@Preview(name = "홈 · 겨울 아침", widthDp = 1707, heightDp = 1210, showBackground = true, backgroundColor = 0xFF0E1216)
@Composable
private fun PreviewWinter() = ChageunTheme { MainContent(winterState(), UiCallbacks()) }

@Preview(name = "홈 · 경고등", widthDp = 1707, heightDp = 1210, showBackground = true, backgroundColor = 0xFF0E1216)
@Composable
private fun PreviewWarning() = ChageunTheme { MainContent(warningState(), UiCallbacks()) }

@Preview(name = "홈 · 하차 점검", widthDp = 1707, heightDp = 1210, showBackground = true, backgroundColor = 0xFF0E1216)
@Composable
private fun PreviewExit() = ChageunTheme { MainContent(exitState(), UiCallbacks()) }

@Preview(name = "홈 · 주행 중", widthDp = 1707, heightDp = 1210, showBackground = true, backgroundColor = 0xFF0E1216)
@Composable
private fun PreviewDriving() = ChageunTheme { MainContent(winterState().copy(isDriving = true), UiCallbacks()) }

@Preview(name = "홈 · 시동 꺼짐", widthDp = 1707, heightDp = 1210, showBackground = true, backgroundColor = 0xFF0E1216)
@Composable
private fun PreviewIgnitionOff() = ChageunTheme {
    MainContent(UiState(isDemo = true, scenario = DemoScenario.WINTER_MORNING, odometerKm = ODO, outsideTempC = -6f), UiCallbacks())
}

@Preview(name = "장소 검색 실패 · 다시 찾기", widthDp = 1707, heightDp = 1210, showBackground = true, backgroundColor = 0xFF0E1216)
@Composable
private fun PreviewRetry() = ChageunTheme {
    val waypoint = com.chageunchageun.app.engine.SuggestedAction.AddWaypoint("주유소", "주유소 경유")
    MainContent(
        warningState().copy(message = "내비게이션에서 '주유소'를 검색해 주세요.", retryAction = waypoint),
        UiCallbacks(),
    )
}

/** 미리보기 상태를 단위 테스트에서도 쓴다(한 화면 배치 규칙 확인). */
internal object PreviewStates {
    fun winter() = winterState()
    fun warning() = warningState()
    fun exit() = exitState()
}
