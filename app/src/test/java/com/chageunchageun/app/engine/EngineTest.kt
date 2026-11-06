package com.chageunchageun.app.engine

import com.chageunchageun.app.model.CabinState
import com.chageunchageun.app.model.EnergyState
import com.chageunchageun.app.model.Lamp
import com.chageunchageun.app.model.TireReading
import com.chageunchageun.app.model.VehicleSnapshot
import com.chageunchageun.app.model.VehicleWarning
import com.chageunchageun.app.model.Wheel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class EngineTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val spec = TireSpec(240f)

    // ---------- 조사 ----------

    @Test
    fun josa_picksByBatchim() {
        assertEquals("엔진오일을", "엔진오일".eulReul())
        assertEquals("브레이크액을", "브레이크액".eulReul())
        assertEquals("주유소를", "주유소".eulReul())
        assertEquals("강남역으로", "강남역".josa("으로", "로"))
    }

    // ---------- 출발 브리핑 ----------

    @Test
    fun departure_enoughTime_isInfo() {
        val trip = UpcomingTrip("미팅", now + 70 * 60_000L, "강남역", 32.0, 45)
        val f = DepartureBriefing.run(trip, now, EnergyState(rangeKm = 400), ZoneId.of("Asia/Seoul"))
        assertEquals(1, f.size)
        assertEquals(Severity.INFO, f[0].severity)
        assertTrue(f[0].speech.contains("25분 여유"))
    }

    @Test
    fun departure_late_isWarn() {
        val trip = UpcomingTrip("미팅", now + 30 * 60_000L, "강남역", 32.0, 45)
        val f = DepartureBriefing.run(trip, now, EnergyState(), ZoneId.of("Asia/Seoul"))
        assertEquals(Severity.WARN, f[0].severity)
        assertTrue(f[0].speech.contains("15분 늦을"))
    }

    @Test
    fun departure_rangeShort_addsFuelStop() {
        val trip = UpcomingTrip("미팅", now + 90 * 60_000L, "강남역", 32.0, 45)
        val f = DepartureBriefing.run(trip, now, EnergyState(rangeKm = 35), ZoneId.of("Asia/Seoul"))
        val range = f.first { it.id == "range-short" }
        assertTrue(range.actions.any { it is SuggestedAction.AddWaypoint && it.query == "주유소" })
    }

    @Test
    fun departure_naviDestinationOnly_rangeCheck() {
        // 일정 없이 내장 내비 목적지만 있을 때 (#003 B7): 같은 규칙(거리 × 1.3)으로 비교
        val ev = DepartureBriefing.rangeCheck("대전역", 160.0, EnergyState(rangeKm = 150, isEv = true))
        assertTrue(ev != null && ev.actions.any { it is SuggestedAction.AddWaypoint && it.query == "전기차 충전소" })
        assertTrue(DepartureBriefing.rangeCheck("대전역", 160.0, EnergyState(rangeKm = 300)) == null)
        assertTrue(DepartureBriefing.rangeCheck("대전역", 160.0, EnergyState()) == null)
    }

    // ---------- 일상점검 ----------

    @Test
    fun daily_oneLowTire_reportedPerWheel() {
        val snap = snapshot(tires = listOf(240f, 240f, 210f, 240f))
        val f = DailyCheck.run(snap, spec)
        assertEquals(listOf("tire-low-REAR_LEFT"), f.map { it.id })
        assertEquals(Severity.ADVISE, f[0].severity)
    }

    @Test
    fun daily_threeMildlyLow_mergedIntoOne() {
        val snap = snapshot(tires = listOf(213f, 212f, 214f, 240f))
        val f = DailyCheck.run(snap, spec)
        assertEquals(listOf("tire-low-all"), f.map { it.id })
    }

    @Test
    fun daily_veryLow_isUrgent() {
        val snap = snapshot(tires = listOf(180f, 240f, 240f, 240f))
        assertEquals(Severity.URGENT, DailyCheck.run(snap, spec).single().severity)
    }

    // ---------- 하차 점검 ----------

    @Test
    fun exit_detectsLightsDoorsAndColdNight() {
        val snap = snapshot().copy(
            cabin = CabinState(
                interiorLightOn = true, openDoors = listOf("뒷좌석 오른쪽"),
                openWindows = listOf("운전석"), parkingBrakeOn = true,
            ),
        )
        val ids = ExitCheck.run(snap, tonightMinTempC = -11f).map { it.id }.toSet()
        assertEquals(setOf("exit-interior", "exit-door", "exit-window", "exit-dashcam-cold"), ids)
    }

    @Test
    fun exit_allClear_isEmpty() {
        val snap = snapshot().copy(cabin = CabinState(false, false, false, emptyList(), emptyList(), true))
        assertTrue(ExitCheck.run(snap, tonightMinTempC = 5f).isEmpty())
    }

    @Test
    fun exit_parkingBrakeOff_gearP_isInfoAndNotSpoken() {
        val snap = snapshot().copy(cabin = CabinState(parkingBrakeOn = false, gearPark = true))
        val f = ExitCheck.run(snap, tonightMinTempC = 5f).single()
        assertEquals("exit-parking-brake", f.id)
        assertEquals(Severity.INFO, f.severity)
        assertTrue(f.speech.contains("경사로라면 주차 브레이크를 채워 주세요"))
        assertEquals("내리기 전 확인 끝. 이상 없어요.", ExitCheck.speech(listOf(f)))
    }

    @Test
    fun exit_parkingBrakeOff_gearNotP_isWarnAndSpoken() {
        val snap = snapshot().copy(cabin = CabinState(parkingBrakeOn = false, gearPark = false))
        val f = ExitCheck.run(snap, tonightMinTempC = 5f).single()
        assertEquals(Severity.WARN, f.severity)
        assertTrue(ExitCheck.speech(listOf(f)).contains(f.speech))
    }

    @Test
    fun exit_parkingBrakeOff_gearUnknown_isWarn() {
        val snap = snapshot().copy(cabin = CabinState(parkingBrakeOn = false, gearPark = null))
        assertEquals(Severity.WARN, ExitCheck.run(snap, tonightMinTempC = 5f).single().severity)
    }

    @Test
    fun exit_speech_skipsInfoButKeepsOthers() {
        val snap = snapshot().copy(
            cabin = CabinState(interiorLightOn = true, parkingBrakeOn = false, gearPark = true),
        )
        val speech = ExitCheck.speech(ExitCheck.run(snap, tonightMinTempC = 5f))
        assertTrue(speech.contains("실내등"))
        assertFalse(speech.contains("경사로"))
    }

    // ---------- 정기점검 ----------

    private val oil = MaintenanceItem("oil", "엔진오일 교환", 10_000, 12, severeIntervalKm = 5_000, severeIntervalMonths = 6)

    @Test
    fun periodic_soonByKm() {
        val rec = MaintenanceRecord("oil", 40_000.0, now - 60 * day)
        val f = PeriodicCheck.evaluate(listOf(oil), listOf(rec), 49_700.0, now, severe = false).single()
        assertEquals(Severity.ADVISE, f.severity)
        assertTrue(f.speech.contains("300km"))
    }

    @Test
    fun periodic_severeUsesShorterInterval() {
        val rec = MaintenanceRecord("oil", 40_000.0, now - 60 * day)
        val f = PeriodicCheck.evaluate(listOf(oil), listOf(rec), 45_100.0, now, severe = true).single()
        assertEquals(Severity.WARN, f.severity)
        assertTrue(f.speech.contains("가혹 조건"))
    }

    @Test
    fun periodic_overdueByDate() {
        val rec = MaintenanceRecord("oil", 49_000.0, now - 400 * day)
        val f = PeriodicCheck.evaluate(listOf(oil), listOf(rec), 49_500.0, now, severe = false).single()
        assertEquals(Severity.WARN, f.severity)
    }

    @Test
    fun periodic_noRecord_asksOnce() {
        val f = PeriodicCheck.evaluate(listOf(oil), emptyList(), 49_500.0, now, severe = false).single()
        assertEquals(Severity.INFO, f.severity)
        assertTrue(f.speech.startsWith("엔진오일 교환을"))
    }

    @Test
    fun drivingPattern_shortTrips_isSevere() {
        val trips = (1..12).map { TripLog(now - it * day, 4.0, 0) }
        assertTrue(DrivingPattern.isSevere(trips))
        val normal = (1..12).map { TripLog(now - it * day, 25.0, 1) }
        assertFalse(DrivingPattern.isSevere(normal))
    }

    // ---------- 중점관리 ----------

    @Test
    fun focus_singleWheelLeak_detected() {
        val pts = mutableListOf<TirePoint>()
        for (week in 3 downTo 0) {
            val t = now - week * 7 * day
            pts += TirePoint(t, Wheel.FRONT_LEFT, 237f)
            pts += TirePoint(t, Wheel.FRONT_RIGHT, 236f)
            pts += TirePoint(t, Wheel.REAR_LEFT, 205f + week * 10f)
            pts += TirePoint(t, Wheel.REAR_RIGHT, 235f)
        }
        val f = FocusWatch.tireTrend(pts, now)
        assertEquals(listOf("focus-leak-REAR_LEFT"), f.map { it.id })
    }

    @Test
    fun focus_allWheelsDrop_isTemperatureNote() {
        val pts = mutableListOf<TirePoint>()
        for (week in 3 downTo 0) {
            val t = now - week * 7 * day
            Wheel.entries.forEach { pts += TirePoint(t, it, 210f + week * 9f) }
        }
        assertEquals(listOf("focus-all-tires"), FocusWatch.tireTrend(pts, now).map { it.id })
    }

    @Test
    fun focus_repeatedWarning_promoted() {
        val ev = listOf(25, 14, 5).map { WarningEvent(now - it * day, "CHECK_ENGINE") } +
            WarningEvent(now - 40 * day, "CHECK_ENGINE")
        val f = FocusWatch.repeatedWarnings(ev, now).single()
        assertTrue(f.speech.contains("3번"))
    }

    // ---------- 등화 점검 ----------

    @Test
    fun lamp_session_collectsFailures() {
        val session = LampCheckSession(controllable = setOf(Lamp.LOW_BEAM))
        assertTrue(session.current!!.appControls)
        assertTrue(session.current!!.instruction.startsWith("제가 전조등(하향)을 켤게요"))
        session.answer(LampAnswer.OK)
        session.answer(LampAnswer.FAIL) // 상향등
        while (!session.isDone) session.answer(LampAnswer.OK)
        val summary = session.summary()
        assertEquals(Severity.WARN, summary.severity)
        assertTrue(summary.speech.startsWith("상향등이 안 켜져요"))
    }

    @Test
    fun lamp_orderedResults_failedFirst_thenStepOrder() {
        // #004 B++++ 결과 목록: 안 켜진 등이 맨 앞, 나머지는 점검 순서. 9개 모두.
        val session = LampCheckSession(controllable = emptySet())
        session.answer(LampAnswer.OK)      // 전조등(하향)
        session.answer(LampAnswer.OK)      // 상향등
        session.answer(LampAnswer.FAIL)    // 미등
        session.answer(LampAnswer.SKIPPED) // 왼쪽 방향지시등
        while (!session.isDone) session.answer(LampAnswer.OK)
        val r = session.orderedResults()
        assertEquals(9, r.size)
        assertEquals(Lamp.TAIL to LampAnswer.FAIL, r.first())
        assertEquals(listOf(Lamp.LOW_BEAM, Lamp.HIGH_BEAM, Lamp.TURN_LEFT), r.drop(1).take(3).map { it.first })
        assertEquals(LampAnswer.SKIPPED, r[3].second)
    }

    // ---------- 브리핑 조합 ----------

    @Test
    fun briefing_departureFirst_thenSevere_maxThree() {
        val findings = listOf(
            Finding("a", CheckKind.PERIODIC, Severity.ADVISE, "a", "정기"),
            Finding("b", CheckKind.DAILY, Severity.URGENT, "b", "긴급"),
            Finding("c", CheckKind.DEPARTURE, Severity.INFO, "c", "일정"),
            Finding("d", CheckKind.SEASONAL, Severity.INFO, "d", "참고"),
            Finding("e", CheckKind.FOCUS, Severity.WARN, "e", "주의"),
        )
        val speech = BriefingComposer.speech(findings)
        assertEquals("일정 긴급 주의 그 외 2건은 화면에서 확인하세요.", speech)
    }

    @Test
    fun briefing_noIssues_saysAllClear() {
        val findings = listOf(Finding("c", CheckKind.DEPARTURE, Severity.INFO, "c", "일정"))
        assertEquals("일정 차량 상태는 이상 없어요.", BriefingComposer.speech(findings))
    }

    // ---------- 계절점검 ----------

    @Test
    fun seasonal_phaseAndWinterPreset() {
        assertEquals(SeasonPhase.PRE_WINTER, SeasonalCheck.phase(11))
        val snap = snapshot().copy(outsideTempC = -6f)
        val f = SeasonalCheck.findings(
            SeasonPhase.PRE_WINTER, snap,
            supported = setOf(com.chageunchageun.app.model.Feature.SEAT_HEAT),
            seasonalDoneThisPhase = false, daysSinceLastDrive = 20,
        )
        assertEquals(setOf("seasonal-PRE_WINTER", "winter-preset", "long-idle"), f.map { it.id }.toSet())
        assertNotNull(f.first { it.id == "winter-preset" }.actions.firstOrNull { it == SuggestedAction.WinterPreset })
    }

    // ---------- #003 A2: 공개 Safety 상태 경고 ----------

    @Test
    fun warning_safetyLevels_severityByCriticality() {
        fun sev(code: String) = WarningTranslator.translate(VehicleWarning(code)).severity
        assertEquals(Severity.WARN, sev("ENGINE_OIL_LEVEL_LOW"))
        assertEquals(Severity.URGENT, sev("ENGINE_OIL_LEVEL_LOW_CRITICAL"))
        assertEquals(Severity.WARN, sev("BRAKE_FLUID_LOW"))
        assertEquals(Severity.URGENT, sev("BRAKE_FLUID_LOW_CRITICAL"))
        assertEquals(Severity.WARN, sev("TRANSMISSION_OIL_LOW"))
        assertEquals(Severity.URGENT, sev("TRANSMISSION_OIL_LOW_CRITICAL"))
        assertEquals(Severity.INFO, sev("WASHER_FLUID_LOW"))
    }

    @Test
    fun warning_safetyLevels_haveOwnTitleAndAdvice() {
        val oil = WarningTranslator.translate(VehicleWarning("ENGINE_OIL_LEVEL_LOW"))
        assertEquals("엔진오일 양 부족", oil.title)
        assertTrue(oil.actions.any { it is SuggestedAction.AddWaypoint })
        val brake = WarningTranslator.translate(VehicleWarning("BRAKE_FLUID_LOW_CRITICAL"))
        assertTrue(brake.speech.contains("운전하지 말고"))
        assertEquals("워셔액 부족", WarningTranslator.translate(VehicleWarning("WASHER_FLUID_LOW")).title)
        // 모르는 키는 일반 안내(주의)
        assertEquals(Severity.WARN, WarningTranslator.translate(VehicleWarning("UNKNOWN_X")).severity)
    }

    // ---------- #003 A1: 주차 판정 ----------

    @Test
    fun parked_byParkingBrakeOrGearP() {
        fun parked(brake: Boolean?, gearP: Boolean?) =
            VehicleSnapshot(now, cabin = CabinState(parkingBrakeOn = brake, gearPark = gearP)).isParked
        assertTrue(parked(brake = true, gearP = null))
        assertTrue(parked(brake = false, gearP = true))
        assertTrue(parked(brake = null, gearP = true))
        assertFalse(parked(brake = false, gearP = false))
        assertFalse(parked(brake = null, gearP = null))
    }

    private fun snapshot(tires: List<Float> = listOf(240f, 240f, 240f, 240f)) = VehicleSnapshot(
        timestampMillis = now,
        odometerKm = 48_000.0,
        tires = Wheel.entries.zip(tires).map { (w, p) -> TireReading(w, p) },
    )
}
