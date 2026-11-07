package com.chageunchageun.app.data

import com.chageunchageun.app.engine.eulReul
import com.chageunchageun.app.model.Feature

/**
 * 지금 화면·엔진이 쓰는 데이터 묶음 (#005 H1). 연동 = Pleos SDK, 데모 = 시나리오 목업.
 * 점검 엔진·화면은 하나이고, 이 묶음만 실행 중에 바꾼다.
 */
class DataSources(
    val vehicle: VehicleDataSource,
    val navigation: NavigationSource,
    val schedule: ScheduleSource,
    val weather: WeatherSource,
    val history: HistoryStore,
    val isDemo: Boolean,
) {
    fun release() {
        vehicle.unwatchCabin()
        navigation.release()
        vehicle.release()
    }
}

enum class AppMode { LIVE, DEMO }

/**
 * 시작 모드 (#005 H2): 차량 연결 성공 + 확인된 기능이 하나라도 있으면 연동, 아니면 데모.
 * 마지막 모드는 기억하지 않는다(앱을 켤 때마다 이 규칙대로) — 심사위원이 데모에 머무는 일을 막는다.
 */
object ModeSelector {
    fun startMode(connected: Boolean, supported: Set<Feature>): AppMode =
        if (connected && supported.isNotEmpty()) AppMode.LIVE else AppMode.DEMO
}

/** 이력 파일 (#005 H4): 데모 기록이 연동 이력과 섞이지 않게 파일을 나눈다. */
object HistoryFiles {
    const val LIVE = "history.json"
    const val DEMO = "history_demo.json"
    fun name(demo: Boolean) = if (demo) DEMO else LIVE
}

/**
 * 연동·데모 묶음 전환 (#005 H3). 연동 묶음이 없으면(차량 연결 실패) 데모에서 나갈 수 없다.
 * 데모 묶음은 처음 필요할 때 만든다.
 */
class SourceSwitch(val live: DataSources?, demoFactory: () -> DataSources) {
    val demo: DataSources by lazy(demoFactory)

    var current: DataSources = live ?: demo
        private set

    val canLeaveDemo: Boolean get() = live != null

    /** 모드를 바꾼다. 바뀌었으면 true. 연동 묶음이 없는데 데모를 끄려 하면 false(그대로 데모). */
    fun use(demoMode: Boolean): Boolean {
        val next = if (demoMode) demo else live ?: return false
        if (next === current) return false
        current = next
        return true
    }
}

/**
 * 겨울 출발 준비: 지원되는 열선·공조만 켠다. 운전자가 확인(버튼·"응")한 뒤에만 부른다.
 * 데모 묶음이면 목업 차량에만 보내므로 실제 차량 제어는 0회 (#005 H4) — 결과 문장 끝에 그 사실을 붙인다.
 */
object WinterPreset {
    suspend fun run(src: DataSources, supported: Set<Feature>): String {
        val v = src.vehicle
        val done = mutableListOf<String>()
        if (Feature.SEAT_HEAT in supported && v.setSeatHeat(level = 2)) done += "시트 열선"
        if (Feature.WHEEL_HEAT in supported && v.setSteeringWheelHeat(true)) done += "핸들 열선"
        if (Feature.MIRROR_HEAT in supported && v.setSideMirrorHeat(true)) done += "사이드미러 열선"
        if (Feature.HVAC in supported && v.setCabinTemperature(23f)) done += "실내 온도 23도"
        val text = if (done.isEmpty()) {
            "이 차에서는 앱으로 열선을 켤 수 없어요. 직접 켜 주세요."
        } else {
            "${done.joinToString(", ").eulReul()} 켰어요."
        }
        return if (src.isDemo) "$text $DEMO_NOTE" else text
    }

    const val DEMO_NOTE = "(데모: 실제 차량은 제어하지 않았어요)"
}
