package com.chageunchageun.app.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.MirrorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import android.util.Log
import com.chageunchageun.app.data.VehicleDataSource
import com.chageunchageun.app.model.CabinState
import com.chageunchageun.app.model.EnergyState
import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.Lamp
import com.chageunchageun.app.model.VehicleSnapshot
import com.chageunchageun.app.model.VehicleWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Pleos Vehicle SDK 2.3.2 구현.
 *
 * 메서드 이름·시그니처 출처: 공식 샘플 pleos-sample-vehicle-app 의 공개 API 카탈로그(PublicApiPolicy)와
 * SDK 클래스(ai.pleos.playground.vehicle.api.*). 공식 샘플이 공개 API 에서 제외한
 * Tire.getTirePressure / Safety.getTelltalesInfo(경고등) 는 쓰지 않는다 → 공기압은 null(눈으로 확인),
 * 경고는 공개된 Safety 상태(엔진오일·브레이크액·변속기오일 양, 워셔액 부족)로만 만든다.
 * 주차 브레이크는 Vehicle SDK 에 없어 AAOS(AaosCarState)에서 읽는다.
 *
 * 모든 호출은 콜백 방식이라 응답이 없을 때를 대비해 TIMEOUT_MS 안에 오지 않으면 null 로 본다.
 */
class PleosVehicleDataSource(context: Context, private val aaos: AaosCarState) : VehicleDataSource {

    private val vehicle = Vehicle(context.applicationContext)
    private var connected = false
    private var features: Set<Feature> = emptySet()

    /** 앱이 켠 등을 원래 스위치 값으로 되돌리기 위해 기억한다. */
    private val lampRestore = mutableMapOf<Lamp, LightSwitch>()

    override suspend fun connect(): Boolean {
        if (connected) return true
        connected = runCatching { vehicle.initialize() }
            .onFailure { Log.w(TAG, "initialize failed", it) }
            .isSuccess
        return connected
    }

    // ---------------- capability ----------------

    override suspend fun supported(): Set<Feature> {
        if (!connected) return emptySet()
        val set = mutableSetOf<Feature>()
        val safety = vehicle.getSafety()
        if (listOf(
                cap { ok, fail -> safety.checkEngineOilLevelStateCapabilities({ c, _ -> ok(c) }, fail) },
                cap { ok, fail -> safety.checkBrakeFluidLevelStateCapabilities({ c, _ -> ok(c) }, fail) },
                cap { ok, fail -> safety.checkLowWasherFluidWarningStateCapabilities(ok, fail) },
            ).any { it.canRead() }
        ) set += Feature.SAFETY

        val odo = vehicle.getOdometer()
        if (cap { ok, fail -> odo.checkCurrentDistanceDrivenCapabilities(ok, fail) }.canRead()) set += Feature.ODOMETER
        // 주행가능거리는 capability 확인 메서드가 없어 실제로 읽히는지로 판단한다.
        if (call<Float> { ok, fail -> odo.getRangeRemainingDistance(ok, fail) } != null) set += Feature.FUEL_RANGE
        if (cap { ok, fail -> vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(ok, fail) }.canRead()) {
            set += Feature.EV_BATTERY
        }

        val light = vehicle.getLight()
        if (cap { ok, fail -> light.checkLowBeamLightStateCapabilities({ c, _ -> ok(c) }, fail) }.canRead() ||
            cap { ok, fail -> light.checkCabinLightStateCapabilities({ c, _ -> ok(c) }, fail) }.canRead()
        ) set += Feature.LIGHT_READ
        if (cap { ok, fail -> light.checkLowBeamLightSwitchCapabilities({ c, _ -> ok(c) }, fail) }.canWrite()) {
            set += Feature.LIGHT_CONTROL
        }

        if (doorBounds().isNotEmpty()) set += Feature.DOOR
        if (windowBounds().isNotEmpty()) set += Feature.WINDOW
        if (aaos.parkingBrakeOn() != null) set += Feature.BRAKE

        if (cap { ok, fail -> vehicle.getHVAC().checkHvacTemperatureCapabilities({ c, _ -> ok(c) }, fail) }.canWrite()) {
            set += Feature.HVAC
        }
        if (cap { ok, fail -> vehicle.getSeat().checkSeatTemperatureCapabilities({ c, _ -> ok(c) }, fail) }.canWrite()) {
            set += Feature.SEAT_HEAT
        }
        if (cap { ok, fail -> vehicle.getSteeringWheel().checkHvacSteeringWheelHeatCapabilities({ c, _ -> ok(c) }, fail) }.canWrite()) {
            set += Feature.WHEEL_HEAT
        }
        if (cap { ok, fail -> vehicle.getSideMirror().checkHvacSideMirrorHeatCapabilities({ c, _ -> ok(c) }, fail) }.canWrite()) {
            set += Feature.MIRROR_HEAT
        }
        // TIRE: 공개 API 에 공기압 읽기가 없음. OUTSIDE_TEMP: 외기온 API 없음(HVAC 는 실내 온도만).
        features = set
        Log.i(TAG, "supported=$set")
        return set
    }

    // ---------------- 읽기 ----------------

    override suspend fun snapshot(): VehicleSnapshot {
        if (!connected) {
            return VehicleSnapshot(
                System.currentTimeMillis(),
                cabin = CabinState(parkingBrakeOn = aaos.parkingBrakeOn(), gearPark = aaos.gearPark()),
            )
        }
        val f = features
        val odo = vehicle.getOdometer()

        // 단위: 주행거리 km, 주행가능거리 m (Virtual Console 사용 가이드: Odometer 0~1,000,000 km, Remaining Range 0~1,000,000 m,
        // 에뮬레이터에서 RANGE_REMAINING=500000 일 때 계기판 500km 표시 확인).
        val odometerKm = if (Feature.ODOMETER in f) call<Float> { ok, fail -> odo.getCurrentDistanceDriven(ok, fail) }?.toDouble() else null
        val rangeKm = if (Feature.FUEL_RANGE in f) call<Float> { ok, fail -> odo.getRangeRemainingDistance(ok, fail) }?.let { (it / 1000f).roundToInt() } else null
        val soc = if (Feature.EV_BATTERY in f) call<Int> { ok, fail -> vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, fail) } else null
        val fuelPercent = call<Int> { ok, fail -> vehicle.getFuel().getFuelPercent(ok, fail) }

        val snap = VehicleSnapshot(
            timestampMillis = System.currentTimeMillis(),
            odometerKm = odometerKm,
            warnings = if (Feature.SAFETY in f) warnings() else emptyList(),
            energy = EnergyState(
                // 연료량이 없고 배터리 잔량만 읽히면 전기차로 본다.
                isEv = soc != null && fuelPercent == null,
                rangeKm = rangeKm,
                evSocPercent = soc,
            ),
            cabin = cabin(f),
        )
        Log.i(TAG, "snapshot odo=$odometerKm range=$rangeKm soc=$soc fuel%=$fuelPercent warnings=${snap.warnings} cabin=${snap.cabin}")
        return snap
    }

    private suspend fun warnings(): List<VehicleWarning> {
        val s = vehicle.getSafety()
        val out = mutableListOf<VehicleWarning>()
        // LOW → "<키>", CRITICALLY_LOW → "<키>_CRITICAL" (WarningTranslator 가 심각도를 나눠 안내)
        fun level(key: String, raw: String, state: OilLevelState?) {
            when (state) {
                OilLevelState.CRITICALLY_LOW -> out += VehicleWarning("${key}_CRITICAL", "$raw(매우 부족)")
                OilLevelState.LOW -> out += VehicleWarning(key, raw)
                else -> Unit
            }
        }
        level("ENGINE_OIL_LEVEL_LOW", "엔진오일 양 부족", call<OilLevelState> { ok, fail -> s.getEngineOilLevelState(ok, fail) })
        level("BRAKE_FLUID_LOW", "브레이크액 부족", call<OilLevelState> { ok, fail -> s.getBrakeFluidLevelState(ok, fail) })
        level("TRANSMISSION_OIL_LOW", "변속기 오일 부족", call<OilLevelState> { ok, fail -> s.getTransmissionOilLevelState(ok, fail) })
        if (call<Boolean> { ok, fail -> s.hasLowWasherFluidWarning(ok, fail) } == true) out += VehicleWarning("WASHER_FLUID_LOW", "워셔액 부족")
        return out
    }

    private suspend fun cabin(f: Set<Feature>): CabinState {
        val light = vehicle.getLight()
        val interior = if (Feature.LIGHT_READ in f) call<LightState> { ok, fail -> light.getCurrentCabinLightState(ok, fail) } else null
        val lowBeam = if (Feature.LIGHT_READ in f) call<LightState> { ok, fail -> light.getCurrentLowBeamLightState(ok, fail) } else null

        val openDoors = mutableListOf<String>()
        if (Feature.DOOR in f) {
            doorBounds().forEach { (area, min) ->
                val pos = call<Int> { ok, fail -> vehicle.getDoor().getCurrentDoorPosition(area, ok, fail) }
                if (pos != null && pos > min) openDoors += doorName(area)
            }
        }
        val openWindows = mutableListOf<String>()
        if (Feature.WINDOW in f) {
            windowBounds().forEach { (area, min) ->
                val pos = call<Int> { ok, fail -> vehicle.getWindow().getCurrentWindowPosition(area, ok, fail) }
                if (pos != null && pos > min) openWindows += windowName(area)
            }
        }
        return CabinState(
            interiorLightOn = interior?.let { it == LightState.ON },
            headlightOn = lowBeam?.let { it == LightState.ON },
            hazardOn = null, // 비상등 상태 API 없음 (TurnSignal 은 OFF/LEFT/RIGHT 만)
            openDoors = openDoors,
            openWindows = openWindows,
            parkingBrakeOn = aaos.parkingBrakeOn(),
            gearPark = aaos.gearPark(),
        )
    }

    /** 지원되는 문 영역과 "닫힘" 값(최솟값). */
    private suspend fun doorBounds(): List<Pair<DoorArea, Int>> =
        call<Map<DoorArea, Int>> { ok, fail ->
            vehicle.getDoor().checkDoorPositionCapabilities({ _, bounds -> ok(bounds.mapValues { it.value.minValue }) }, fail)
        }?.toList().orEmpty()

    private suspend fun windowBounds(): List<Pair<WindowArea, Int>> =
        call<Map<WindowArea, Int>> { ok, fail ->
            vehicle.getWindow().checkWindowPositionCapabilities({ _, bounds -> ok(bounds.mapValues { it.value.minValue }) }, fail)
        }?.toList().orEmpty()

    private fun doorName(area: DoorArea): String = when (area.values.firstOrNull()) {
        DoorValue.ROW_1_LEFT -> "운전석"
        DoorValue.ROW_1_RIGHT -> "조수석"
        DoorValue.ROW_2_LEFT -> "뒷좌석 왼쪽"
        DoorValue.ROW_2_RIGHT -> "뒷좌석 오른쪽"
        DoorValue.ROW_3_LEFT -> "3열 왼쪽"
        DoorValue.ROW_3_RIGHT -> "3열 오른쪽"
        DoorValue.HOOD -> "보닛"
        DoorValue.REAR -> "트렁크"
        null -> "어느"
    }

    private fun windowName(area: WindowArea): String = when (area.values.firstOrNull()) {
        WindowValue.ROW_1_LEFT -> "운전석"
        WindowValue.ROW_1_RIGHT -> "조수석"
        WindowValue.ROW_2_LEFT -> "뒷좌석 왼쪽"
        WindowValue.ROW_2_RIGHT -> "뒷좌석 오른쪽"
        WindowValue.ROOF_TOP_1, WindowValue.ROOF_TOP_2 -> "선루프"
        else -> "어느"
    }

    // ---------------- 제어: 운전자가 확인한 뒤에만 호출된다. 실패하면 false → "직접 켜 주세요" ----------------

    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)

    override suspend fun setSeatHeat(level: Int): Boolean {
        val max = call<Int> { ok, fail ->
            vehicle.getSeat().checkSeatTemperatureCapabilities({ _, b -> ok(b[driverSeat]?.maxValue ?: 0) }, fail)
        } ?: return false
        if (max <= 0) return false
        return call<Int> { ok, fail ->
            vehicle.getSeat().setHvacSeatTemperature(driverSeat, level.coerceIn(1, max), { _, v -> ok(v) }, fail)
        }.logSet("seatHeat") != null
    }

    override suspend fun setSteeringWheelHeat(on: Boolean): Boolean {
        val wheel = vehicle.getSteeringWheel()
        val max = call<Int> { ok, fail -> wheel.checkHvacSteeringWheelHeatCapabilities({ _, b -> ok(b.maxValue) }, fail) } ?: return false
        return call<Int> { ok, fail -> wheel.setHvacSteeringWheelHeat(if (on) max else 0, ok, fail) }.logSet("wheelHeat") != null
    }

    override suspend fun setSideMirrorHeat(on: Boolean): Boolean {
        val mirror = vehicle.getSideMirror()
        val target = call<Pair<MirrorArea, Int>> { ok, fail ->
            mirror.checkHvacSideMirrorHeatCapabilities({ _, b -> ok(b.entries.firstOrNull()?.let { it.key to it.value.maxValue }) }, fail)
        } ?: return false
        return call<Int> { ok, fail ->
            mirror.setHvacSideMirrorHeat(target.first, if (on) target.second else 0, { _, v -> ok(v) }, fail)
        }.logSet("mirrorHeat") != null
    }

    override suspend fun setCabinTemperature(celsius: Float): Boolean =
        call<Float> { ok, fail ->
            vehicle.getHVAC().setHvacTemperature(driverSeat, celsius, TemperatureUnit.CELSIUS, { _, v -> ok(v) }, fail)
        }.logSet("hvacTemp") != null

    override suspend fun setLamp(lamp: Lamp, on: Boolean): Boolean {
        val light = vehicle.getLight()
        val get: (((LightSwitch?) -> Unit), (Exception) -> Unit) -> Unit
        val set: (LightSwitch, ((LightSwitch?) -> Unit), (Exception) -> Unit) -> Unit
        when (lamp) {
            Lamp.LOW_BEAM -> {
                get = { ok, fail -> light.getLowBeamLightSwitch(ok, fail) }
                set = { v, ok, fail -> light.setLowBeamLightSwitch(v, ok, fail) }
            }
            Lamp.HIGH_BEAM -> {
                get = { ok, fail -> light.getHighBeamLightSwitch(ok, fail) }
                set = { v, ok, fail -> light.setHighBeamLightSwitch(v, ok, fail) }
            }
            Lamp.TAIL -> {
                get = { ok, fail -> light.getTailLampLightSwitch(ok, fail) }
                set = { v, ok, fail -> light.setTailLampLightSwitch(v, ok, fail) }
            }
            // 방향지시등·비상등은 공개 API 로 켤 수 없다 → 운전자에게 요청.
            else -> return false
        }
        val target = if (on) {
            call(get)?.let { lampRestore[lamp] = it }
            LightSwitch.ON
        } else {
            lampRestore.remove(lamp) ?: LightSwitch.OFF
        }
        return call<LightSwitch> { ok, fail -> set(target, ok, fail) }.logSet("lamp $lamp=$target") != null
    }

    // ---------------- 상태 변화 리스너 (SDK register*/unregister* 패턴) ----------------

    private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** 해제할 때 부를 unregister 목록. */
    private val unregisters = mutableListOf<() -> Unit>()
    @Volatile private var watching = false

    override fun watchCabin(onChange: () -> Unit) {
        if (!connected || watching) return
        watching = true
        val f = features
        watchScope.launch {
            val light = vehicle.getLight()
            if (Feature.LIGHT_READ in f) {
                val cabinL = lightListener("cabinLight", onChange)
                val lowL = lightListener("lowBeam", onChange)
                register("cabinLight", { light.registerCabinLightState(cabinL) }, { light.unregisterCabinLightState(cabinL) })
                register("lowBeam", { light.registerLowBeamLightState(lowL) }, { light.unregisterLowBeamLightState(lowL) })
            }
            if (Feature.DOOR in f) {
                val door = vehicle.getDoor()
                doorBounds().forEach { (area, _) ->
                    val l = object : DoorPositionListener {
                        override fun onPositionUpdated(value: Int?) {
                            Log.d(TAG, "door ${doorName(area)}=$value")
                            onChange()
                        }
                        override fun onFailed(e: Exception) = Log.d(TAG, "door listener: ${e.message}").let { }
                    }
                    register("door ${doorName(area)}", { door.registerDoorPosition(area, l) }, { door.unregisterDoorPosition(area, l) })
                }
            }
            if (Feature.WINDOW in f) {
                val window = vehicle.getWindow()
                windowBounds().forEach { (area, _) ->
                    val l = object : WindowPositionListener {
                        override fun onWindowPositionUpdated(value: Int?) {
                            Log.d(TAG, "window ${windowName(area)}=$value")
                            onChange()
                        }
                        override fun onFailed(e: Exception) = Log.d(TAG, "window listener: ${e.message}").let { }
                    }
                    register("window ${windowName(area)}", { window.registerWindowPosition(area, l) }, { window.unregisterWindowPosition(area, l) })
                }
            }
            Log.i(TAG, "watchCabin 등록 ${unregisters.size}개")
            if (!watching) unwatchCabin() // 등록 도중 화면을 떠난 경우
        }
    }

    private fun lightListener(name: String, onChange: () -> Unit) = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            Log.d(TAG, "$name=$state")
            onChange()
        }
        override fun onFailed(e: Exception) = Log.d(TAG, "$name listener: ${e.message}").let { }
    }

    private fun register(name: String, reg: () -> Unit, unreg: () -> Unit) {
        runCatching(reg)
            .onSuccess { synchronized(unregisters) { unregisters += unreg } }
            .onFailure { Log.w(TAG, "register $name 실패: ${it.message}") }
    }

    override fun unwatchCabin() {
        watching = false
        val list = synchronized(unregisters) { unregisters.toList().also { unregisters.clear() } }
        list.forEach { runCatching(it) }
        if (list.isNotEmpty()) Log.i(TAG, "watchCabin 해제 ${list.size}개")
    }

    override fun release() {
        unwatchCabin()
        watchScope.cancel()
        if (connected) runCatching { vehicle.release() }
        connected = false
        aaos.release()
    }

    // ---------------- 콜백 → suspend ----------------

    private suspend fun <T> call(block: (ok: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<T?> { cont ->
                val result: (T?) -> Unit = { v -> if (cont.isActive) cont.resume(v) }
                runCatching { block({ result(it) }, { e -> Log.d(TAG, "call failed: ${e.message}"); result(null) }) }
                    .onFailure { Log.d(TAG, "call threw: ${it.message}"); result(null) }
            }
        }

    private suspend fun cap(block: (ok: (CapabilityConfig?) -> Unit, fail: (Exception) -> Unit) -> Unit): CapabilityConfig? =
        call(block)

    private fun CapabilityConfig?.canRead() = this?.accessType == AccessType.READ || this?.accessType == AccessType.READ_WRITE
    private fun CapabilityConfig?.canWrite() = this?.accessType == AccessType.WRITE || this?.accessType == AccessType.READ_WRITE

    private fun <T> T?.logSet(what: String): T? = also { Log.i(TAG, "set $what -> ${it ?: "실패"}") }

    private companion object {
        const val TAG = "ChageunPleos"
        const val TIMEOUT_MS = 2_000L
    }
}
