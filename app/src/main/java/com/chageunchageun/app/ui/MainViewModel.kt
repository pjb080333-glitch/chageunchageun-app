package com.chageunchageun.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chageunchageun.app.data.CalendarSeeder
import com.chageunchageun.app.data.ChecklistRecord
import com.chageunchageun.app.data.DemoScenario
import com.chageunchageun.app.data.DemoSeeder
import com.chageunchageun.app.data.DrivingStatus
import com.chageunchageun.app.data.ActiveRoute
import com.chageunchageun.app.data.GeoPoint
import com.chageunchageun.app.data.HistoryStore
import com.chageunchageun.app.data.LampRecord
import com.chageunchageun.app.data.MockVehicleDataSource
import com.chageunchageun.app.data.Place
import com.chageunchageun.app.data.Voice
import com.chageunchageun.app.data.VoiceMonitor
import com.chageunchageun.app.data.WaypointResult
import com.chageunchageun.app.data.YesNo
import com.chageunchageun.app.di.VehicleProvider
import com.chageunchageun.app.di.demoSources
import com.chageunchageun.app.data.AppMode
import com.chageunchageun.app.data.DataSources
import com.chageunchageun.app.data.ModeSelector
import com.chageunchageun.app.data.NavigationSource
import com.chageunchageun.app.data.ScheduleSource
import com.chageunchageun.app.data.SourceSwitch
import com.chageunchageun.app.data.VehicleDataSource
import com.chageunchageun.app.data.WeatherSource
import com.chageunchageun.app.data.WinterPreset
import com.chageunchageun.app.engine.BriefingComposer
import com.chageunchageun.app.engine.CarProfile
import com.chageunchageun.app.engine.ChecklistItem
import com.chageunchageun.app.engine.DailyCheck
import com.chageunchageun.app.engine.DepartureBriefing
import com.chageunchageun.app.engine.DrivingPattern
import com.chageunchageun.app.engine.ExitCheck
import com.chageunchageun.app.engine.Finding
import com.chageunchageun.app.engine.FocusWatch
import com.chageunchageun.app.engine.ItemMode
import com.chageunchageun.app.engine.LampAnswer
import com.chageunchageun.app.engine.LampCheckSession
import com.chageunchageun.app.engine.LampStep
import com.chageunchageun.app.engine.MaintenanceItem
import com.chageunchageun.app.engine.MaintenanceRecord
import com.chageunchageun.app.engine.NextService
import com.chageunchageun.app.engine.OkSummary
import com.chageunchageun.app.engine.PeriodicCheck
import com.chageunchageun.app.engine.SeasonPhase
import com.chageunchageun.app.engine.SeasonalCheck
import com.chageunchageun.app.engine.Severity
import com.chageunchageun.app.engine.SuggestedAction
import com.chageunchageun.app.engine.TirePoint
import com.chageunchageun.app.engine.TripLog
import com.chageunchageun.app.engine.UpcomingTrip
import com.chageunchageun.app.engine.WarningEvent
import com.chageunchageun.app.engine.eulReul
import com.chageunchageun.app.engine.josa
import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.Lamp
import com.chageunchageun.app.model.VehicleSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class Screen(val label: String) {
    HOME("홈"),
    CHECKLIST("점검표"),
    LAMP("등화 점검"),
    HISTORY("이력"),
}

data class UiState(
    val isDemo: Boolean,
    val scenario: DemoScenario? = null,
    val screen: Screen = Screen.HOME,
    val ignitionOn: Boolean = false,
    val isParked: Boolean = true,
    val profileName: String = "",
    val briefing: List<Finding> = emptyList(),
    val exitFindings: List<Finding> = emptyList(),
    val lastSpeech: String = "",
    val phase: SeasonPhase = SeasonPhase.NONE,
    val checklist: List<ChecklistItem> = emptyList(),
    val checklistAnswers: Map<String, Boolean> = emptyMap(),
    val periodic: List<Finding> = emptyList(),
    val maintenanceItems: List<MaintenanceItem> = emptyList(),
    val lampStep: LampStep? = null,
    val lampProgress: String = "",
    val lampSummary: Finding? = null,
    /** 등화 점검 결과 목록(안 켜진 등 먼저, #004 B++++) */
    val lampResults: List<Pair<Lamp, LampAnswer>> = emptyList(),
    val historyLines: List<String> = emptyList(),
    val message: String? = null,
    /** null = 확인 중, false = 음성 엔진 없음·한국어 미지원 → 화면 안내만. */
    val voiceAvailable: Boolean? = null,
    /** 주행 중(화면 조작 제한). 점검표·등화 점검·이력 탭을 막는다. */
    val isDriving: Boolean = false,
    /** 음성 재생·인식 상태("말하는 중", "듣는 중", "들은 말: 응"). debug 빌드에서만 화면에 보인다. */
    val voiceActivity: String? = null,
    /** 진행 중인 경유지 검색. 끝날 때까지 버튼을 "찾는 중…"으로 바꾸고 다시 누르지 못하게 한다. */
    val busyAction: SuggestedAction? = null,
    /** 상단 요약 줄: 누적 주행거리·바깥 기온(차량 값, 없으면 날씨)·다음 일정 한 줄 (#004 B). */
    val odometerKm: Double? = null,
    val outsideTempC: Float? = null,
    val nextTripLine: String? = null,
    /** 차량 데이터로 확인했고 이상이 없던 항목 ("확인한 항목 이상 없음 (N개)"). */
    val okItems: List<String> = emptyList(),
    /** 다음 정기점검 (홈 "다가오는 점검" 카드). */
    val nextService: NextService? = null,
    /** 장소 검색 실패 후 "다시 찾기" 버튼으로 다시 실행할 경유지 검색 (#004 D). null 이면 버튼 숨김. */
    val retryAction: SuggestedAction.AddWaypoint? = null,
    /** 시동을 끈 뒤 하차 점검을 보여 주는 중 (이상이 없어 카드가 0개여도). */
    val exitDone: Boolean = false,
    /** 시연 녹화용(--ez demo_clean true): debug 상태 칩을 숨긴다. */
    val demoClean: Boolean = false,
    /** 차량 연결 실패로 데모 모드로만 쓸 수 있음 (#005 H2: "차량 연결 안 됨 · 데모 모드"). */
    val liveUnavailable: Boolean = false,
    /** 시동 신호를 받을 수 없는 환경(에뮬레이터 등) → 연동 모드에서도 시동 켜기/끄기 버튼을 보인다 (#005, DECISIONS #111). */
    val manualIgnition: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    /** 연동(Pleos)·데모(목업) 데이터 묶음. 실행 중에 바꾼다 (#005 H1). 아래 값들은 지금 묶음을 가리킨다. */
    private val sources = SourceSwitch(VehicleProvider.live(app)) { demoSources(app) }
    private val src: DataSources get() = sources.current
    private val vehicle: VehicleDataSource get() = src.vehicle
    private val navigation: NavigationSource get() = src.navigation
    private val schedule: ScheduleSource get() = src.schedule
    private val weather: WeatherSource get() = src.weather
    private val history: HistoryStore get() = src.history

    /** 연동 차량 연결·기능 확인 결과 (시작할 때 한 번). 실패면 데모에서 나갈 수 없다. */
    private var liveOk = false
    private var liveSupported: Set<Feature> = emptySet()
    private var watchingIgnition = false
    private val modeLock = Mutex()
    private val voiceInput = VehicleProvider.voiceInput(app)
    private val drivingState = VehicleProvider.drivingState(app)
    private val profile = CarProfile.sample()

    private val _state = MutableStateFlow(
        UiState(
            isDemo = false,
            profileName = profile.name,
            maintenanceItems = profile.maintenanceItems,
        ),
    )
    val state: StateFlow<UiState> = _state

    private val voice: Voice = VehicleProvider.voice(app) { ok -> _state.update { it.copy(voiceAvailable = ok) } }
    private var lastIgnition: Boolean? = null
    private val briefingLock = Mutex()

    private var supported: Set<Feature> = emptySet()
    private var lampSession: LampCheckSession? = null

    /** 마지막으로 브리핑한 일정 장소(좌표 포함). 길안내·경유지 방향·날씨 위치에 쓴다. */
    private var lastTripPlace: Place? = null

    /** 시동을 끈 뒤 하차 점검 결과를 보여 주는 중인지 (상태 변화 리스너로 카드를 다시 계산할지). */
    private var exitShown = false
    private var lastTonightC: Float? = null
    private var cabinRefresh: Job? = null
    private var visible = false

    /** 이번 출발 브리핑에 쓴 일정 (요약 줄 "다음 일정" 한 줄). */
    private var lastTrip: UpcomingTrip? = null

    /** 같은 내비 목적지·같은 결과를 두 번 말하지 않게 (#004 C). */
    private val routeRecheck = RouteRecheck()
    private val retryLimiter = RetryLimiter()
    private val driveTracker = LastDriveTracker()

    init {
        viewModelScope.launch { VoiceMonitor.state.collect { v -> _state.update { it.copy(voiceActivity = v) } } }
        viewModelScope.launch {
            // #005 H2: 차량 연결에 성공하면 연동, 실패하면 데모로 시작(마지막 모드는 기억하지 않음).
            val mode = chooseStartMode()
            modeLock.withLock { enterMode(demo = mode == AppMode.DEMO) }
            if (liveOk) sources.live?.navigation?.watchRoute(::onRouteStarted)
            watchingIgnition = drivingState.start { status ->
                viewModelScope.launch { onDrivingStatus(status) }
            }
            _state.update { it.copy(manualIgnition = !watchingIgnition) }
            // 시동 상태를 받을 수 없는 환경: 앱을 연 순간을 시동으로 보고 출발 브리핑을 한 번 한다(연동 모드).
            if (!watchingIgnition && !src.isDemo) ignitionOn()
        }
    }

    // ---------------- 연동 ↔ 데모 (#005) ----------------

    private suspend fun chooseStartMode(): AppMode {
        val live = sources.live ?: return AppMode.DEMO
        val ok = runCatching { withTimeoutOrNull(10_000) { live.vehicle.connect() } == true }.getOrDefault(false)
        val sup = if (ok) runCatching { withTimeoutOrNull(15_000) { live.vehicle.supported() } }.getOrNull().orEmpty() else emptySet()
        liveOk = ModeSelector.startMode(ok, sup) == AppMode.LIVE
        liveSupported = sup
        return if (liveOk) AppMode.LIVE else AppMode.DEMO
    }

    /** 데모 칩 (#005 H3). 연동 연결이 없으면 데모를 끌 수 없다고 알린다. */
    fun setDemoMode(on: Boolean) {
        viewModelScope.launch {
            modeLock.withLock {
                if (on == src.isDemo) return@withLock
                if (!on && !liveOk) {
                    _state.update { it.copy(message = "차량에 연결되지 않아 데모 모드로만 쓸 수 있어요.") }
                    return@withLock
                }
                enterMode(demo = on, byUser = true)
                // 데모를 끄면 연동 데이터로 바로 다시 조회한다(시동이 꺼진 게 확실할 때만 브리핑 생략).
                if (!on && (!watchingIgnition || lastIgnition != false)) doIgnitionOn()
            }
        }
    }

    /** 데이터 묶음을 바꾸고 화면 상태를 새로 시작한다. 데모는 이전 데모 APK 처럼 시나리오 선택 상태로. */
    private suspend fun enterMode(demo: Boolean, byUser: Boolean = false) {
        vehicle.unwatchCabin()
        voiceInput.stop()
        routeRecheck.reset()
        lampSession = null
        exitShown = false
        lastTrip = null
        lastTripPlace = null
        sources.use(demo)
        supported = if (demo) {
            vehicle.connect()
            vehicle.supported()
        } else {
            liveSupported
        }
        _state.update {
            UiState(
                isDemo = demo,
                liveUnavailable = !liveOk,
                manualIgnition = it.manualIgnition,
                profileName = profile.name,
                maintenanceItems = profile.maintenanceItems,
                voiceAvailable = it.voiceAvailable,
                isDriving = it.isDriving,
                voiceActivity = it.voiceActivity,
                demoClean = it.demoClean,
                // 앱 시작 때 연동으로 들어갈 때는 안내하지 않는다(데모 칩으로 끌 때만, #005 2단계).
                message = if (byUser && !demo) "데모 모드를 껐어요. 차량 데이터로 다시 확인할게요." else null,
            )
        }
        if (visible) onVisible(true)
        val mock = vehicle as? MockVehicleDataSource
        if (demo && mock != null) selectScenarioNow(mock.scenario) else refreshPassive()
    }

    // ---------------- 시동·주행 상태 (pleos 플레이버) ----------------

    private fun onDrivingStatus(status: DrivingStatus) {
        status.drivingRestricted?.let { driving ->
            _state.update { it.copy(isDriving = driving, screen = if (driving) Screen.HOME else it.screen) }
            // #004 E1: 주행 시작·끝을 마지막 운행으로 기록 → 시동 신호가 없어도 장기 미운행 안내가 동작.
            if (!src.isDemo && driveTracker.onDriving(driving)) {
                history.data.lastDriveEndMillis = System.currentTimeMillis()
                history.save()
            }
        }
        // 데모 모드: 주행 제한(안전)만 따르고, 시동·주차는 시나리오 값을 쓴다(#005). 시동 값은 기억만 한다.
        if (src.isDemo) {
            status.ignitionOn?.let { lastIgnition = it }
            return
        }
        // 주차 = 주차 브레이크 채움 또는 기어 P (#003 A1)
        status.parked?.let { parked -> _state.update { it.copy(isParked = parked) } }
        val ign = status.ignitionOn ?: return
        val prev = lastIgnition
        lastIgnition = ign
        if (prev == ign) return
        // 처음 받은 값이 ON이면(앱을 시동 중에 연 경우 포함) 출발 브리핑, ON→OFF면 하차 점검.
        if (ign && !_state.value.ignitionOn) ignitionOn()
        if (!ign && prev == true && _state.value.ignitionOn) ignitionOff()
    }

    // ---------------- 시연 자동화 (debug 빌드에서만 MainActivity 가 호출) ----------------

    /** adb 인텐트 extra 로 장면을 만든다. 예: demo_scenario=WINTER_MORNING, demo_action=IGNITION_ON, demo_tab=HOME */
    fun runDemo(scenario: String?, action: String?, tab: String?, clean: Boolean = false) {
        if (clean) _state.update { it.copy(demoClean = true) }
        viewModelScope.launch {
            // #005: 시나리오를 고르면 데모 모드로(필요하면 먼저 전환). DEMO_OFF 로 연동 모드로.
            if (scenario != null && !src.isDemo) modeLock.withLock { enterMode(demo = true) }
            scenario?.let { name -> DemoScenario.entries.firstOrNull { it.name == name }?.let { selectScenarioNow(it) } }
            when (action) {
                "DEMO_ON" -> if (!src.isDemo) modeLock.withLock { enterMode(demo = true) }
                "DEMO_OFF" -> setDemoMode(false)
                "IGNITION_ON" -> doIgnitionOn()
                "IGNITION_OFF" -> doIgnitionOff()
                "WINTER_PRESET" -> say(winterPreset())
                "START_LAMP_CHECK" -> doStartLampCheck()
                "DISMISS_MESSAGE" -> dismissMessage()
                // 검증용: 에뮬레이터 캘린더에 테스트 일정(KTX·SRT 역) 넣기. _NOGEO = 첫 일정 좌표 없이(지오코딩 확인)
                "SEED_CALENDAR", "SEED_CALENDAR_NOGEO" -> {
                    val n = CalendarSeeder.seed(getApplication(), withGeo = action == "SEED_CALENDAR")
                    say(if (n > 0) "테스트 일정 ${n}개를 넣었어요." else "테스트 일정을 넣지 못했어요.")
                }
                "WAYPOINT_FUEL" -> runAction(SuggestedAction.AddWaypoint("주유소", "주유소 경유"))
                "WAYPOINT_REPAIR" -> runAction(SuggestedAction.AddWaypoint("자동차 정비소", "정비소 경유"))
            }
            tab?.let { name -> Screen.entries.firstOrNull { it.name == name }?.let(::goTo) }
        }
    }

    // ---------------- 데모 시나리오 (mock 플레이버) ----------------

    fun selectScenario(scenario: DemoScenario) {
        viewModelScope.launch { selectScenarioNow(scenario) }
    }

    private suspend fun selectScenarioNow(scenario: DemoScenario) {
        val mock = vehicle as? MockVehicleDataSource ?: return
        voiceInput.stop()
        mock.scenario = scenario
        exitShown = false
        history.reset(DemoSeeder.history(scenario, mock.odometerKm))
        lampSession = null
        _state.update {
            it.copy(
                scenario = scenario, ignitionOn = false, briefing = emptyList(), exitFindings = emptyList(), exitDone = false,
                lastSpeech = "", lampStep = null, lampSummary = null, lampResults = emptyList(), checklistAnswers = emptyMap(),
                okItems = emptyList(), nextTripLine = null, retryAction = null,
                message = "시나리오: ${scenario.label} — ${scenario.description}",
            )
        }
        refreshPassive()
    }

    // ---------------- 시동 켜기: 출발 브리핑 ----------------
    // pleos: DrivingStateSource 의 시동 상태 변화로 자동 호출(onDrivingStatus). mock: 버튼·데모 인텐트.

    fun ignitionOn() {
        viewModelScope.launch { doIgnitionOn() }
    }

    private suspend fun doIgnitionOn() {
        // 앱을 연 순간의 브리핑과 시동 켜기(버튼·시동 신호)가 겹치면 내비 요청 응답이 섞인다(#003 B7 확인) → 한 번에 하나씩.
        briefingLock.withLock {
            exitShown = false
            routeRecheck.reset()
            lastTrip = null
            val snap = withOutsideTemp(vehicle.snapshot())
            val now = System.currentTimeMillis()
            val h = history.data
            val daysIdle = h.lastDriveEndMillis?.let { ((now - it) / DAY).toInt() }

            snap.tires.forEach { t -> t.pressureKpa?.let { h.tirePoints += TirePoint(now, t.wheel, it) } }
            snap.warnings.forEach { h.warningEvents += WarningEvent(now, it.code) }
            h.driveStartOdometerKm = snap.odometerKm
            history.save()

            val phase = SeasonalCheck.phase(currentMonth())
            val severe = DrivingPattern.isSevere(h.trips)
            val findings = buildList<Finding> {
                addAll(departureFindings(now, snap))
                addAll(DailyCheck.run(snap, profile.tireSpec))
                snap.odometerKm?.let {
                    addAll(PeriodicCheck.evaluate(profile.maintenanceItems, h.maintenance, it, now, severe))
                }
                addAll(SeasonalCheck.findings(phase, snap, supported, seasonalDone(phase), daysIdle))
                addAll(FocusWatch.tireTrend(h.tirePoints, now))
                addAll(FocusWatch.repeatedWarnings(h.warningEvents, now))
            }.let(::dedupe)

            val speech = BriefingComposer.speech(findings)
            // 겨울 출발 준비를 물어봤다면("켤까요?") 말이 끝난 뒤 "응/아니"를 한 번 듣는다.
            val preset = findings.firstOrNull { SuggestedAction.WinterPreset in it.actions }
            voice.speak(speech, onDone = if (preset != null && speech.contains(preset.speech)) ::listenWinterPreset else null)
            _state.update {
                it.copy(
                    ignitionOn = true, isParked = snap.isParked, briefing = BriefingComposer.sort(findings),
                    exitFindings = emptyList(), exitDone = false, lastSpeech = speech, screen = Screen.HOME,
                    okItems = OkSummary.briefing(snap, findings, supported),
                    nextTripLine = lastTrip?.let { t ->
                        "${DepartureBriefing.clock(t.startMillis, ZoneId.systemDefault())} ${t.title} · ${t.destination}"
                    },
                )
            }
            refreshPassive()
        }
    }

    /** 누설 의심으로 이미 말한 바퀴는 "조금 낮음"을 다시 말하지 않는다. */
    private fun dedupe(findings: List<Finding>): List<Finding> {
        val leakWheels = findings.filter { it.id.startsWith("focus-leak-") }.map { it.id.removePrefix("focus-leak-") }.toSet()
        return findings.filterNot { f ->
            f.severity == Severity.ADVISE && f.id.startsWith("tire-low-") && f.id.removePrefix("tire-low-") in leakWheels
        }
    }

    /**
     * 출발 항목의 목적지 출처 (#003 B7): ① 내장 내비에서 길안내 중인 목적지 ② 캘린더 일정 ③ 없음.
     * 내비 목적지가 있으면 주행가능거리는 내비의 남은 거리로 비교하고, 일정이 함께 있으면 출발 여유만 덧붙인다.
     */
    private suspend fun departureFindings(now: Long, snap: VehicleSnapshot): List<Finding> {
        val route = withTimeoutOrNull(8_000) { navigation.activeRoute() }
        val trip = nextTrip(now)
        if (route == null) return DepartureBriefing.run(trip, now, snap.energy)
        lastTripPlace = route.place
        val range = DepartureBriefing.rangeCheck(route.place.name, route.remainKm, snap.energy)
        // 브리핑에서 비교한 목적지 → 뒤이어 오는 onRouteStarted·onStart 재조회는 같은 말을 다시 하지 않는다(#004 C).
        routeRecheck.shouldSpeak(route.place.name, range != null)
        return buildList {
            addAll(DepartureBriefing.run(trip, now, snap.energy).filterNot { it.id == RANGE_ID })
            range?.let(::add)
        }
    }

    /** 시동 후 운전자가 내비에서 목적지를 정해 길안내가 시작되면 주행가능거리를 다시 비교한다. 부족할 때만 말한다. */
    private fun onRouteStarted(route: ActiveRoute) {
        if (src.isDemo) return // 연동 내비 신호는 데모 화면에 섞지 않는다
        viewModelScope.launch { briefingLock.withLock { compareRoute(route) } }
    }

    /**
     * 앱이 화면 앞에 올 때마다(#004 C) 내비가 길안내 중인지 한 번 더 본다.
     * 앱이 떠 있는 동안 내비에서 시작한 안내는 onRouteStarted 가 오지 않을 수 있어서(#003 입회 확인).
     */
    private fun recheckRouteOnStart() {
        viewModelScope.launch {
            if (!_state.value.ignitionOn) return@launch
            val route = withTimeoutOrNull(8_000) { navigation.activeRoute() } ?: return@launch
            briefingLock.withLock {
                if (!routeRecheck.seen(route.place.name)) compareRoute(route)
            }
        }
    }

    /** 내비 목적지 남은 거리 vs 주행가능거리. 카드는 항상 갱신하고, 음성은 같은 목적지·같은 결과면 한 번만. */
    private suspend fun compareRoute(route: ActiveRoute) {
        if (!_state.value.ignitionOn) return
        lastTripPlace = route.place
        val snap = vehicle.snapshot()
        val f = DepartureBriefing.rangeCheck(route.place.name, route.remainKm, snap.energy)
        _state.update { s ->
            val rest = s.briefing.filterNot { it.id == RANGE_ID }
            s.copy(briefing = BriefingComposer.sort(if (f == null) rest else rest + f))
        }
        // 주행 중에도 음성으로만 안내한다(화면은 주행 중 홈 그대로).
        if (routeRecheck.shouldSpeak(route.place.name, f != null)) f?.let { voice.speak(it.speech) }
    }

    private suspend fun nextTrip(now: Long): UpcomingTrip? {
        val event = schedule.upcoming(withinHours = 4).firstOrNull { it.location != null } ?: return null
        val destination = event.location ?: return null
        val place = Place(destination, event.point)
        lastTripPlace = place
        // 경로 계산(지오코딩·네트워크 포함)이 늦어도 브리핑이 멈추지 않게 15초까지만 기다린다.
        val route = withTimeoutOrNull(15_000) { navigation.estimate(place) } ?: return null
        return UpcomingTrip(event.title, event.startMillis, destination, route.distanceKm, route.durationMin)
            .also { lastTrip = it }
    }

    // ---------------- 날씨 (pleos: Open-Meteo, mock: 시나리오 값) ----------------

    /** 날씨를 볼 위치: 내비 현재 위치 → 마지막 일정 장소 좌표 → 서울. */
    private suspend fun weatherPoint(): GeoPoint =
        navigation.currentLocation() ?: lastTripPlace?.point ?: SEOUL

    /** 차량에 바깥 기온 값이 없으면(Pleos 공개 API 에 없음) 날씨 예보의 현재 기온을 넣는다. */
    private suspend fun withOutsideTemp(snap: VehicleSnapshot): VehicleSnapshot {
        if (snap.outsideTempC != null || src.isDemo) return snap
        val now = weather.weather(weatherPoint())?.currentC ?: return snap
        return snap.copy(outsideTempC = now)
    }

    private suspend fun tonightMinTemp(): Float? =
        if (src.isDemo) _state.value.scenario?.tonightMinTempC else weather.weather(weatherPoint())?.tonightMinC

    // ---------------- 시동 끄기: 하차 점검 ----------------

    private fun listenWinterPreset() {
        voiceInput.listen { text ->
            if (YesNo.parse(text) == true) runAction(SuggestedAction.WinterPreset)
        }
    }

    fun ignitionOff() {
        viewModelScope.launch { doIgnitionOff() }
    }

    private suspend fun doIgnitionOff() {
        run {
            voiceInput.stop()
            routeRecheck.reset()
            val snap = vehicle.snapshot()
            val now = System.currentTimeMillis()
            // 오늘 밤 최저기온: mock 은 시나리오 값, pleos 는 Open-Meteo 예보(실패하면 null → 블랙박스 안내 생략)
            // #004 B+++: 날씨 서버가 늦어도 하차 점검 화면은 바로(4초 넘으면 블랙박스 안내만 생략).
            val tonight = withTimeoutOrNull(4_000) { tonightMinTemp() }
            lastTonightC = tonight
            exitShown = true
            val findings = BriefingComposer.sort(ExitCheck.run(snap, tonight))

            val h = history.data
            val mock = vehicle as? MockVehicleDataSource
            if (mock != null) mock.odometerKm += DEMO_TRIP_KM
            val endOdo = mock?.odometerKm ?: snap.odometerKm
            val startOdo = h.driveStartOdometerKm
            if (startOdo != null && endOdo != null && endOdo >= startOdo) {
                // TODO(pleos): 급제동 횟수는 차속 리스너로 집계 (공식 튜토리얼의 안전 운전 점수 예제 참고)
                h.trips += TripLog(now, endOdo - startOdo, hardBrakes = 0)
            }
            h.lastDriveEndMillis = now
            h.driveStartOdometerKm = null
            history.save()

            val speech = ExitCheck.speech(findings)
            voice.speak(speech)
            _state.update {
                it.copy(
                    ignitionOn = false, exitFindings = findings, exitDone = true, lastSpeech = speech, screen = Screen.HOME,
                    okItems = OkSummary.exit(snap, supported),
                )
            }
            refreshPassive()
        }
    }

    // ---------------- 실행 연결 ----------------

    fun runAction(action: SuggestedAction) {
        if (action is SuggestedAction.AddWaypoint) {
            // 공개 지도 서버가 느리면 한 번에 수십 초 걸린다. 검색 중 다시 누른 것은 무시하고 전체 시간을 제한한다.
            if (_state.value.busyAction != null) return
            _state.update { it.copy(busyAction = action, retryAction = null, message = null) }
        }
        viewModelScope.launch {
            var retry: SuggestedAction.AddWaypoint? = null
            val message: String? = when (action) {
                is SuggestedAction.Navigate ->
                    if (navigation.startGuidance(placeOf(action.destination))) {
                        "${action.destination.josa("으로", "로")} 길안내를 시작했어요."
                    } else {
                        "길안내를 시작하지 못했어요."
                    }
                is SuggestedAction.AddWaypoint ->
                    when (val r = withTimeoutOrNull(WAYPOINT_LIMIT_MS) { navigation.addWaypoint(action.query, lastTripPlace) }
                        ?: WaypointResult.NotFound) {
                        is WaypointResult.Added ->
                            "경유지로 ${r.name.eulReul()} 추가했어요." +
                                (r.distanceKm?.let { " 지금 위치에서 ${km(it)} 거리예요." } ?: "")
                        is WaypointResult.ShownOnly ->
                            "가까운 곳: ${r.name}" + (r.distanceKm?.let { "(${km(it)})" } ?: "") +
                                ". 경유지로 추가할 수 없어 화면에 표시만 해요."
                        WaypointResult.OpenedMapApp -> "지도 앱에서 '${action.query}' 검색 결과를 열었어요."
                        WaypointResult.NotFound -> {
                            // #004 D: 대체 안내와 함께 "다시 찾기" (1분 안에 3번까지)
                            val can = retryLimiter.canRetry(System.currentTimeMillis())
                            if (can) retry = action
                            "내비게이션에서 '${action.query}'${action.query.eulReul().last()} 검색해 주세요." +
                                if (can) "" else " 다시 찾기는 1분 뒤에 할 수 있어요."
                        }
                    }
                SuggestedAction.WinterPreset -> winterPreset()
                SuggestedAction.StartLampCheck -> {
                    startLampCheck()
                    null
                }
                SuggestedAction.OpenChecklist -> {
                    goTo(Screen.CHECKLIST)
                    null
                }
            }
            _state.update { it.copy(busyAction = null, retryAction = retry) }
            message?.let(::say)
        }
    }

    /** 장소 검색 실패 안내의 "다시 찾기" 버튼. 1분 안에 3번까지. */
    fun retryWaypoint() {
        val action = _state.value.retryAction ?: return
        if (_state.value.busyAction != null) return
        if (!retryLimiter.tryAcquire(System.currentTimeMillis())) {
            _state.update { it.copy(retryAction = null) }
            say("다시 찾기는 1분 뒤에 할 수 있어요. 내비게이션에서 '${action.query}'${action.query.eulReul().last()} 검색해 주세요.")
            return
        }
        runAction(action)
    }

    private fun placeOf(name: String): Place = lastTripPlace?.takeIf { it.name == name } ?: Place(name)

    private fun km(d: Double): String = if (d < 10) "%.1fkm".format(d) else "${d.roundToInt()}km"

    /** 겨울 출발 준비. 데모 모드면 목업 차량에만 보내고 "실제 차량은 제어하지 않았어요"를 붙인다(#005 H4). */
    private suspend fun winterPreset(): String = WinterPreset.run(src, supported)

    // ---------------- 등화 점검 ----------------

    fun startLampCheck() {
        viewModelScope.launch { doStartLampCheck() }
    }

    private suspend fun doStartLampCheck() {
        run {
            val snap = vehicle.snapshot()
            if (!(snap.isParked || _state.value.isParked && snap.cabin.parkingBrakeOn == null)) {
                say("주차 브레이크를 채운 뒤 시작할 수 있어요.")
                return
            }
            val controllable = if (Feature.LIGHT_CONTROL in supported) {
                Lamp.entries.filterNot { it.needsHelper }.toSet()
            } else {
                emptySet()
            }
            lampSession = LampCheckSession(controllable)
            _state.update { it.copy(screen = Screen.LAMP, lampSummary = null, lampResults = emptyList()) }
            showLampStep()
        }
    }

    fun answerLamp(answer: LampAnswer) {
        voiceInput.stop()
        viewModelScope.launch {
            val session = lampSession ?: return@launch
            val step = session.current ?: return@launch
            if (step.appControls) vehicle.setLamp(step.lamp, false)
            history.data.lamps += LampRecord(System.currentTimeMillis(), step.lamp, answer)
            session.answer(answer)
            showLampStep()
        }
    }

    private suspend fun showLampStep() {
        val session = lampSession ?: return
        val step = session.current
        if (step == null) {
            val summary = session.summary()
            val results = session.orderedResults()
            history.save()
            lampSession = null
            voice.speak(summary.speech)
            _state.update { it.copy(lampStep = null, lampProgress = "", lampSummary = summary, lampResults = results, historyLines = historyLines()) }
            return
        }
        if (step.appControls) vehicle.setLamp(step.lamp, true)
        _state.update { it.copy(lampStep = step, lampProgress = "${session.index + 1} / ${session.steps.size}") }
        val index = session.index
        voice.speak(step.instruction) {
            // "켜졌어/안 켜졌어" 음성 응답. 그사이 버튼으로 답했으면 무시한다.
            voiceInput.listen { text ->
                val yes = YesNo.parse(text) ?: return@listen
                viewModelScope.launch {
                    if (lampSession === session && session.index == index) answerLamp(if (yes) LampAnswer.OK else LampAnswer.FAIL)
                }
            }
        }
    }

    // ---------------- 점검표 (정기·계절) ----------------

    fun answerChecklist(itemId: String, ok: Boolean) {
        _state.update { it.copy(checklistAnswers = it.checklistAnswers + (itemId to ok)) }
    }

    fun completeChecklist() {
        viewModelScope.launch {
            val st = _state.value
            if (!st.isParked) {
                say("주차한 상태에서 진행해 주세요.")
                return@launch
            }
            val unchecked = st.checklist.count { it.mode == ItemMode.VISUAL && it.id !in st.checklistAnswers }
            if (unchecked > 0) {
                say("아직 확인하지 않은 항목이 ${unchecked}개 있어요.")
                return@launch
            }
            val now = System.currentTimeMillis()
            val needs = mutableListOf<String>()
            st.checklist.forEach { item ->
                val ok = if (item.mode == ItemMode.AUTO) item.autoResult else st.checklistAnswers[item.id]
                if (ok != null) history.data.checklist += ChecklistRecord(now, phaseKey(st.phase), item.id, ok)
                if (ok == false) needs += item.name
            }
            history.save()
            _state.update { it.copy(checklistAnswers = emptyMap()) }
            say(
                if (needs.isEmpty()) {
                    "점검 완료. 모두 이상 없어요."
                } else {
                    "점검 완료. ${needs.joinToString(", ").josa("은", "는")} 손봐야 해요. 가까운 정비소에 들러 주세요."
                },
            )
            refreshPassive()
        }
    }

    fun recordMaintenance(itemId: String) {
        viewModelScope.launch {
            val odo = vehicle.snapshot().odometerKm
            if (odo == null) {
                say("주행거리를 읽을 수 없어 기록하지 못했어요.")
                return@launch
            }
            history.data.maintenance += MaintenanceRecord(itemId, odo, System.currentTimeMillis())
            history.save()
            val name = profile.maintenanceItems.firstOrNull { it.id == itemId }?.name ?: itemId
            say("$name 기록했어요. 주행거리 ${odo.roundToInt()}km 기준이에요.")
            refreshPassive()
        }
    }

    // ---------------- 공통 ----------------

    /** 주행 중에는 홈 외의 화면(점검표·등화 점검·이력)으로 가지 않는다. */
    fun goTo(screen: Screen) = _state.update { if (it.isDriving && screen != Screen.HOME) it else it.copy(screen = screen) }

    fun dismissMessage() = _state.update { it.copy(message = null, retryAction = null) }

    private fun say(text: String) {
        voice.speak(text)
        _state.update { it.copy(message = text) }
    }

    /** 시동과 무관하게 점검표·정기점검·이력 화면을 갱신. */
    private suspend fun refreshPassive() {
        val snap = withOutsideTemp(vehicle.snapshot())
        val now = System.currentTimeMillis()
        val phase = SeasonalCheck.phase(currentMonth())
        val odo = snap.odometerKm
        val periodic = if (odo != null) {
            PeriodicCheck.evaluate(
                profile.maintenanceItems, history.data.maintenance, odo, now,
                DrivingPattern.isSevere(history.data.trips),
            )
        } else {
            emptyList()
        }
        val next = odo?.let {
            PeriodicCheck.next(profile.maintenanceItems, history.data.maintenance, it, now, DrivingPattern.isSevere(history.data.trips))
        }
        _state.update {
            it.copy(
                isParked = snap.isParked,
                odometerKm = odo,
                outsideTempC = snap.outsideTempC,
                nextService = next,
                phase = phase,
                checklist = SeasonalCheck.checklist(phase, snap, profile.tireSpec),
                periodic = periodic,
                historyLines = historyLines(),
            )
        }
    }

    private fun currentMonth(): Int =
        _state.value.scenario?.demoMonth ?: LocalDate.now().monthValue

    private fun phaseKey(phase: SeasonPhase) = "${phase.name}-${LocalDate.now().year}"

    private fun seasonalDone(phase: SeasonPhase) = history.data.checklist.any { it.phase == phaseKey(phase) }

    private fun historyLines(): List<String> {
        val h = history.data
        val lines = mutableListOf<String>()
        h.maintenance.sortedByDescending { it.doneAtMillis }.forEach { r ->
            val name = profile.maintenanceItems.firstOrNull { it.id == r.itemId }?.name ?: r.itemId
            lines += "${date(r.doneAtMillis)} · 정비 · $name (${r.doneAtKm.roundToInt()}km)"
        }
        h.lamps.sortedByDescending { it.timeMillis }.take(9).forEach {
            lines += "${date(it.timeMillis)} · 등화 점검 · ${it.lamp.label}: ${it.answer.label}"
        }
        h.checklist.sortedByDescending { it.timeMillis }.forEach {
            lines += "${date(it.timeMillis)} · 점검표 · ${it.itemId}: ${if (it.ok) "이상 없음" else "손볼 필요"}"
        }
        lines += "최근 운행 ${h.trips.size}회 기록 · 가혹 조건 주기 적용: ${if (DrivingPattern.isSevere(h.trips)) "예" else "아니오"}"
        return lines
    }

    private fun date(millis: Long): String {
        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        return "${d.monthValue}월 ${d.dayOfMonth}일"
    }

    // ---------------- 화면이 보이는 동안 실내 상태 감시 (#003 A4) ----------------

    /** MainActivity onStart/onStop 에서 호출. 보이는 동안만 문·창문·등 리스너를 등록한다. */
    fun onVisible(visible: Boolean) {
        this.visible = visible
        // 차량 연결·지원 기능 확인이 끝나기 전이면 init 에서 다시 부른다.
        if (visible) recheckRouteOnStart()
        if (visible && supported.isNotEmpty()) {
            vehicle.watchCabin {
                // 리스너가 한꺼번에 여러 번 올 수 있어 0.3초 모아서 한 번 다시 읽는다.
                cabinRefresh?.cancel()
                cabinRefresh = viewModelScope.launch {
                    delay(300)
                    refreshExitFindings()
                }
            }
        } else if (!visible) {
            vehicle.unwatchCabin()
        }
    }

    /** 시동을 끈 뒤(하차 점검 화면) 차 상태가 바뀌면 카드만 다시 계산한다. 음성은 다시 말하지 않는다. */
    private suspend fun refreshExitFindings() {
        if (!exitShown || _state.value.ignitionOn) return
        val snap = vehicle.snapshot()
        val findings = BriefingComposer.sort(ExitCheck.run(snap, lastTonightC))
        _state.update { it.copy(exitFindings = findings, isParked = snap.isParked, okItems = OkSummary.exit(snap, supported)) }
    }

    override fun onCleared() {
        if (src.isDemo) src.release()
        sources.live?.release()
        voiceInput.release()
        drivingState.release()
        voice.release()
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val DEMO_TRIP_KM = 24.0
        const val RANGE_ID = "range-short"
        /** 경유지 검색 전체 제한. 넘으면 "내비게이션에서 검색해 주세요"로 안내한다. */
        const val WAYPOINT_LIMIT_MS = 20_000L

        /** 위치를 모를 때 날씨 기준: 서울시청 */
        val SEOUL = GeoPoint(37.5663, 126.9779)
    }
}
