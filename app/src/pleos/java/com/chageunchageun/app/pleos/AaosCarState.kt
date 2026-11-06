package com.chageunchageun.app.pleos

import android.car.Car
import android.car.VehicleGear
import android.car.VehicleIgnitionState
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.chageunchageun.app.data.DrivingStateSource
import com.chageunchageun.app.data.DrivingStatus

/**
 * Pleos Vehicle SDK 에 없는 시동·주차 브레이크·주행 제한 상태를 AAOS 표준 Car API 로 받는다.
 *  - IGNITION_STATE, PARKING_BRAKE_ON, GEAR_SELECTION(없으면 CURRENT_GEAR)
 *                                     : CarPropertyManager (권한 android.car.permission.CAR_POWERTRAIN)
 *  - 주행 중 화면 제한               : CarUxRestrictionsManager (권한 없음)
 * Pleos SDK 카탈로그에는 Brake(브레이크 페달)만 있고 주차 브레이크·시동 상태가 없어서 이 경로를 쓴다.
 * 자동차 기기가 아니거나 권한이 없으면 각 값은 null 로 남고, 앱은 버튼·주차 브레이크 기준으로 동작한다.
 */
class AaosCarState(context: Context) : DrivingStateSource {

    private val appContext = context.applicationContext
    private var car: Car? = null
    private var props: CarPropertyManager? = null
    private var ux: CarUxRestrictionsManager? = null
    private var status = DrivingStatus()
    /** 실제로 등록된 기어 속성(GEAR_SELECTION 또는 CURRENT_GEAR). 둘 다 안 되면 null. */
    private var gearProp: Int? = null
    private var onChange: ((DrivingStatus) -> Unit)? = null

    private val propCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.IGNITION_STATE -> {
                    val v = value.value as? Int ?: return
                    Log.i(TAG, "IGNITION_STATE=$v")
                    update(status.copy(ignitionOn = v == VehicleIgnitionState.ON || v == VehicleIgnitionState.START))
                }
                VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.CURRENT_GEAR -> {
                    val v = value.value as? Int ?: return
                    Log.i(TAG, "${VehiclePropertyIds.toString(value.propertyId)}=$v")
                    update(status.copy(gearPark = v == VehicleGear.GEAR_PARK))
                }
                VehiclePropertyIds.PARKING_BRAKE_ON -> {
                    val v = value.value as? Boolean ?: return
                    Log.i(TAG, "PARKING_BRAKE_ON=$v")
                    update(status.copy(parkingBrakeOn = v))
                }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "property error prop=$propId zone=$zone")
        }
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> onUx(r) }

    /** @return 시동 상태를 받을 수 있으면 true (false 면 앱이 실행 시점 출발 브리핑으로 대신한다). */
    override fun start(onChange: (DrivingStatus) -> Unit): Boolean {
        this.onChange = onChange
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
        val c = runCatching { Car.createCar(appContext) }.onFailure { Log.w(TAG, "createCar failed", it) }.getOrNull() ?: return false
        car = c
        props = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager

        ux?.let { m ->
            runCatching {
                m.registerListener(uxListener)
                m.currentCarUxRestrictions?.let(::onUx)
            }.onFailure { Log.w(TAG, "ux restrictions unavailable", it) }
        }
        register(VehiclePropertyIds.PARKING_BRAKE_ON)
        gearProp = listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.CURRENT_GEAR).firstOrNull { register(it) }
        return register(VehiclePropertyIds.IGNITION_STATE)
    }

    /** 지금 주차 브레이크 상태. 읽을 수 없으면 null. */
    fun parkingBrakeOn(): Boolean? {
        status.parkingBrakeOn?.let { return it }
        val p = props ?: return null
        return runCatching { p.getBooleanProperty(VehiclePropertyIds.PARKING_BRAKE_ON, 0) }
            .onFailure { Log.w(TAG, "PARKING_BRAKE_ON read failed: ${it.message}") }
            .getOrNull()
    }

    /** 지금 기어가 P인지. 읽을 수 없으면 null. */
    fun gearPark(): Boolean? {
        status.gearPark?.let { return it }
        val p = props ?: return null
        val id = gearProp ?: return null
        return runCatching { p.getIntProperty(id, 0) == VehicleGear.GEAR_PARK }
            .onFailure { Log.w(TAG, "gear read failed: ${it.message}") }
            .getOrNull()
    }

    private fun register(propId: Int): Boolean {
        val p = props ?: return false
        return runCatching { p.registerCallback(propCallback, propId, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
            .onFailure { Log.w(TAG, "register ${VehiclePropertyIds.toString(propId)} failed: ${it.message}") }
            .getOrDefault(false)
            .also { Log.i(TAG, "register ${VehiclePropertyIds.toString(propId)} -> $it") }
    }

    private fun onUx(r: CarUxRestrictions) {
        Log.i(TAG, "UX restrictions requiresDO=${r.isRequiresDistractionOptimization} active=${r.activeRestrictions}")
        update(status.copy(drivingRestricted = r.isRequiresDistractionOptimization))
    }

    @Synchronized
    private fun update(next: DrivingStatus) {
        status = next
        onChange?.invoke(next)
    }

    override fun release() {
        runCatching { props?.unregisterCallback(propCallback) }
        runCatching { ux?.unregisterListener() }
        runCatching { car?.disconnect() }
        car = null
        props = null
        ux = null
        gearProp = null
        onChange = null
    }

    private companion object {
        const val TAG = "ChageunCar"
    }
}
