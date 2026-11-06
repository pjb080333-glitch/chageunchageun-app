package com.chageunchageun.app.data

/**
 * 차량 운행 상태. null = 이 환경에서 알 수 없음.
 * @param ignitionOn 시동(IGNITION_STATE ON/START) 여부
 * @param drivingRestricted 주행 중이라 화면 조작을 줄여야 하는지 (CarUxRestrictions 기준)
 */
data class DrivingStatus(
    val ignitionOn: Boolean? = null,
    val drivingRestricted: Boolean? = null,
    val parkingBrakeOn: Boolean? = null,
    /** 기어 P 여부 (AAOS GEAR_SELECTION, 없으면 CURRENT_GEAR) */
    val gearPark: Boolean? = null,
) {
    /** 주차 브레이크 또는 기어 P. 둘 다 모르면 null. */
    val parked: Boolean?
        get() = if (parkingBrakeOn == true || gearPark == true) true
        else if (parkingBrakeOn == null && gearPark == null) null else false
}

/**
 * 시동·주행 제한 상태 변화를 알려 준다.
 *  - mock 플레이버: NoDrivingState (버튼과 시나리오의 주차 브레이크 값 사용)
 *  - pleos 플레이버: AAOS Car API (CarPropertyManager + CarUxRestrictionsManager)
 */
interface DrivingStateSource {
    /** 감시를 시작한다. 지원하지 않으면 false 를 돌려주고 콜백은 오지 않는다. */
    fun start(onChange: (DrivingStatus) -> Unit): Boolean
    fun release()
}

object NoDrivingState : DrivingStateSource {
    override fun start(onChange: (DrivingStatus) -> Unit) = false
    override fun release() = Unit
}
