package com.chageunchageun.app.ui

import com.chageunchageun.app.engine.CheckKind
import com.chageunchageun.app.engine.Finding
import com.chageunchageun.app.engine.MaintenanceItem
import com.chageunchageun.app.engine.MaintenanceRecord
import com.chageunchageun.app.engine.OkSummary
import com.chageunchageun.app.engine.PeriodicCheck
import com.chageunchageun.app.engine.Severity
import com.chageunchageun.app.engine.SuggestedAction
import com.chageunchageun.app.model.CabinState
import com.chageunchageun.app.model.EnergyState
import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.TireReading
import com.chageunchageun.app.model.VehicleSnapshot
import com.chageunchageun.app.model.Wheel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun f(id: String, kind: CheckKind, sev: Severity) = Finding(id, kind, sev, id, id)

    // ---------- B. 홈 배치 ----------

    @Test
    fun split_putsActionsLeftAndGuidesRight() {
        val s = HomeLayout.split(
            listOf(
                f("departure", CheckKind.DEPARTURE, Severity.INFO),
                f("leak", CheckKind.FOCUS, Severity.WARN),
                f("tire", CheckKind.DAILY, Severity.ADVISE),
                f("season", CheckKind.SEASONAL, Severity.ADVISE),
                f("preset", CheckKind.SEASONAL, Severity.INFO),
                f("oil-soon", CheckKind.PERIODIC, Severity.ADVISE),
                f("oil-over", CheckKind.PERIODIC, Severity.WARN),
            ),
        )
        // 순서는 BriefingComposer 와 같다: 일정 → 심각도 → 점검 종류 순. 버튼 없는 권장·참고는 오른쪽.
        assertEquals(listOf("departure", "oil-over", "leak"), s.action.map { it.id })
        assertEquals(setOf("tire", "season", "preset"), s.guide.map { it.id }.toSet())
        assertEquals(listOf("oil-soon"), s.upcoming.map { it.id })
    }

    @Test
    fun split_nowActionsGoLeft_regardlessOfSeverity() {
        val preset = f("winter-preset", CheckKind.SEASONAL, Severity.INFO).copy(actions = listOf(SuggestedAction.WinterPreset))
        val lamp = f("lamp", CheckKind.LAMP, Severity.INFO).copy(actions = listOf(SuggestedAction.StartLampCheck))
        val season = f("season", CheckKind.SEASONAL, Severity.ADVISE).copy(actions = listOf(SuggestedAction.OpenChecklist))
        val urgentNoAction = f("charging", CheckKind.DAILY, Severity.URGENT)
        val s = HomeLayout.split(listOf(preset, lamp, season, urgentNoAction))
        assertEquals(setOf("winter-preset", "lamp", "charging"), s.action.map { it.id }.toSet())
        assertEquals(listOf("season"), s.guide.map { it.id })
    }

    @Test
    fun split_exit_allLeftExceptInfo() {
        val s = HomeLayout.split(
            listOf(
                f("exit-window", CheckKind.EXIT, Severity.ADVISE),
                f("exit-door", CheckKind.EXIT, Severity.WARN),
                f("exit-parking-brake", CheckKind.EXIT, Severity.INFO),
            ),
        )
        assertEquals(setOf("exit-window", "exit-door"), s.action.map { it.id }.toSet())
        assertEquals(listOf("exit-parking-brake"), s.guide.map { it.id })
    }

    // ---------- B+ 남는 높이만큼 카드 ----------

    @Test
    fun fitCount_allFit_noMore() {
        // 카드 3개(경고등 미리보기처럼) + 이상 없음 바가 들어가면 3개 전부, "더 보기" 없음
        assertEquals(3, HomeLayout.fitCount(listOf(250, 250, 250), 16, 940, footer = 64, more = 56, minVisible = 3))
    }

    @Test
    fun fitCount_threeCards_noMore_whenTheyFit() {
        // 카드 3개가 바와 함께 들어가면 "더 보기"가 생기지 않는다(#004 B+ 4): 900 + 64 + 16*3 = 1012
        assertEquals(3, HomeLayout.fitCount(listOf(300, 300, 300), 16, 1012, footer = 64, more = 56, minVisible = 3))
    }

    @Test
    fun fitCount_bigText_minimumDoesNotFit_showsWhatFitsAndMore() {
        // #004 B++: 글씨가 커져 최소 3개도 안 들어가면 잘라 보이지 않고 들어가는 만큼 + 더 보기
        // 2개 + 더 보기 + 바 = 700 + 64 + 64 + 16*3 = 876
        assertEquals(2, HomeLayout.fitCount(listOf(350, 350, 350), 16, 900, footer = 64, more = 64, minVisible = 3))
        // 1개도 빠듯하면 1개 + 더 보기
        assertEquals(1, HomeLayout.fitCount(listOf(500, 500, 500), 16, 700, footer = 64, more = 64, minVisible = 3))
        // 아무것도 안 들어가도 1개는 보인다
        assertEquals(1, HomeLayout.fitCount(listOf(900, 900), 16, 500, footer = null, more = 64, minVisible = 3))
        assertEquals(0, HomeLayout.fitCount(emptyList(), 16, 500, footer = null, more = 64, minVisible = 3))
    }

    @Test
    fun fitCount_fillsRemainingHeight_thenMore() {
        val h = listOf(200, 200, 200, 200, 200, 200)
        // 4개 + 더 보기 + 바 = 800 + 56 + 64 + 16*5 = 1000
        assertEquals(4, HomeLayout.fitCount(h, 16, 1000, footer = 64, more = 56, minVisible = 3))
        // 공간이 커지면 더 많이, 전부 들어가면 더 보기 없음: 1200 + 64 + 16*6 = 1360
        assertEquals(6, HomeLayout.fitCount(h, 16, 1360, footer = 64, more = 56, minVisible = 3))
        // 최소 3개는 들어갈 때 지킨다: 600 + 56 + 16*3 = 704 (바 없음)
        assertEquals(3, HomeLayout.fitCount(h, 16, 704, footer = null, more = 56, minVisible = 3))
    }

    @Test
    fun summary_countsBySeverity_skipsInfo() {
        val list = listOf(
            f("a", CheckKind.DAILY, Severity.URGENT),
            f("b", CheckKind.FOCUS, Severity.WARN),
            f("c", CheckKind.DEPARTURE, Severity.INFO),
            f("d", CheckKind.SEASONAL, Severity.ADVISE),
        )
        assertEquals("조치 필요 2건 (위험 1 · 주의 1)", HomeLayout.summary(list))
        assertEquals(Severity.URGENT, HomeLayout.topSeverity(list))
        assertEquals("조치할 항목 없음", HomeLayout.summary(listOf(f("c", CheckKind.DEPARTURE, Severity.INFO))))
        assertNull(HomeLayout.topSeverity(emptyList()))
    }

    @Test
    fun previewStates_layout() {
        listOf(PreviewStates.winter(), PreviewStates.warning()).forEach { st ->
            val s = HomeLayout.split(st.briefing)
            assertTrue(s.action.isNotEmpty())
        }
        val winter = HomeLayout.split(PreviewStates.winter().briefing)
        assertEquals("departure", winter.action.first().id)
        assertTrue(winter.action.any { it.id.startsWith("focus-leak-") })
        // 겨울 출발 준비는 아침에 바로 실행할 일 → 왼쪽 (#004 B+ 2)
        assertTrue(winter.action.any { it.id == "winter-preset" })
        assertTrue(winter.guide.none { it.id == "winter-preset" })
        assertTrue(winter.upcoming.any { it.id == "periodic-engine_oil" })
    }

    // ---------- B. 핵심 숫자 · 다음 정기점검 ----------

    private val oil = MaintenanceItem("oil", "엔진오일 교환", 10_000, 12)
    private val brake = MaintenanceItem("brake", "브레이크액", 40_000, 24)

    @Test
    fun periodic_badges() {
        val over = PeriodicCheck.evaluate(listOf(oil), listOf(MaintenanceRecord("oil", 38_000.0, now - 30 * day)), 48_800.0, now, false)
        assertEquals("+800km 초과", over.single().badge)
        val soon = PeriodicCheck.evaluate(listOf(oil), listOf(MaintenanceRecord("oil", 38_700.0, now - 30 * day)), 48_400.0, now, false)
        assertEquals("300km 남음", soon.single().badge)
    }

    @Test
    fun periodic_next_picksClosestByOwnInterval() {
        val records = listOf(
            MaintenanceRecord("oil", 40_000.0, now - 60 * day), // 10,000km 중 2,000km 남음 (20%)
            MaintenanceRecord("brake", 20_000.0, now - 60 * day), // 40,000km 중 32,000km 남음
        )
        val next = PeriodicCheck.next(listOf(oil, brake), records, 48_000.0, now, false)!!
        assertEquals("엔진오일 교환", next.name)
        assertEquals("약 2,000km", next.kmText)
        assertTrue(next.dDay!!.startsWith("D-"))
    }

    // ---------- B. 확인한 항목 이상 없음 ----------

    @Test
    fun okSummary_onlyCheckedAndClean() {
        val snap = VehicleSnapshot(
            now, 48_000.0, Wheel.entries.map { TireReading(it, 240f) }, emptyList(), EnergyState(rangeKm = 420),
        )
        val ok = OkSummary.briefing(snap, emptyList(), setOf(Feature.SAFETY))
        assertEquals(listOf("타이어 공기압 (4개)", "차량 경고 없음", "주행가능거리 420km", "정기점검 시기"), ok)
        // 값이 없으면(연동 버전에서 공기압 비공개) 넣지 않는다.
        val noTire = OkSummary.briefing(snap.copy(tires = emptyList()), emptyList(), emptySet())
        assertFalse(noTire.any { it.startsWith("타이어") || it.startsWith("차량 경고") })
        // 문제가 나온 항목은 빠진다.
        val withLow = OkSummary.briefing(snap, listOf(f("tire-low-REAR_LEFT", CheckKind.DAILY, Severity.ADVISE)), emptySet())
        assertFalse(withLow.any { it.startsWith("타이어") })
    }

    @Test
    fun okSummary_exit() {
        val snap = VehicleSnapshot(
            now, cabin = CabinState(interiorLightOn = false, hazardOn = false, parkingBrakeOn = true, openDoors = listOf("운전석")),
        )
        assertEquals(listOf("실내등 꺼짐", "비상등 꺼짐", "주차 브레이크 채움"), OkSummary.exit(snap, setOf(Feature.DOOR)))
    }

    // ---------- C. 내비 목적지 재비교 중복 방지 ----------

    @Test
    fun routeRecheck_sameDestinationSameResult_speaksOnce() {
        val r = RouteRecheck()
        assertTrue(r.shouldSpeak("광명역", short = true))
        assertFalse(r.shouldSpeak("광명역", short = true))
        assertTrue(r.seen("광명역"))
        assertFalse(r.seen("수원역"))
        assertTrue(r.shouldSpeak("수원역", short = true))
        r.reset()
        assertTrue(r.shouldSpeak("수원역", short = true))
    }

    @Test
    fun routeRecheck_enoughRange_neverSpeaks() {
        val r = RouteRecheck()
        assertFalse(r.shouldSpeak("광명역", short = false))
        assertTrue(r.seen("광명역"))
    }

    // ---------- B+++ S6. 하차 실수 → 시동 끄기 화면 상태 ----------

    @Test
    fun exitState_isExitMode_withActionCards() {
        // 하차 실수 시나리오를 엔진으로 만든 상태(미리보기와 같은 값): 시동 꺼짐 + 하차 점검 결과
        val s = PreviewStates.exit()
        assertTrue(s.exitMode)
        assertFalse(s.notChecked) // 시동 꺼짐 가운데 화면(IgnitionOffHome)이 아니라 하차 점검 2단 화면
        assertTrue(s.homeFindings.isNotEmpty())
        val split = HomeLayout.split(s.homeFindings)
        // 실내등·뒷문·창문은 왼쪽 "내리기 전 확인할 것"
        assertTrue(split.action.map { it.id }.containsAll(listOf("exit-interior", "exit-door")))
        // 기어 P + 주차 브레이크 해제는 참고 등급(음성 제외)이라 왼쪽 위에 오지 않는다(#004 A)
        assertTrue(split.action.firstOrNull()?.severity != Severity.INFO)
    }

    // ---------- D. 다시 찾기 제한 ----------

    @Test
    fun retryLimiter_threePerMinute() {
        val l = RetryLimiter()
        assertTrue(l.tryAcquire(0))
        assertTrue(l.tryAcquire(10_000))
        assertTrue(l.tryAcquire(20_000))
        assertFalse(l.canRetry(30_000))
        assertFalse(l.tryAcquire(30_000))
        assertTrue(l.tryAcquire(60_000)) // 첫 번째가 1분 지나 빠짐
    }

    // ---------- E1. 마지막 운행 기록 ----------

    @Test
    fun lastDrive_recordsOnStartAndEndOnly() {
        val t = LastDriveTracker()
        assertFalse(t.onDriving(false)) // 앱을 주차 상태에서 엶
        assertTrue(t.onDriving(true)) // 주행 시작
        assertFalse(t.onDriving(true))
        assertTrue(t.onDriving(false)) // 주행 끝
        assertTrue(LastDriveTracker().onDriving(true)) // 주행 중에 앱을 엶
    }
}
