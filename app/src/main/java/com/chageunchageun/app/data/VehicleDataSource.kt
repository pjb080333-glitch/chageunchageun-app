package com.chageunchageun.app.data

import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.Lamp
import com.chageunchageun.app.model.VehicleSnapshot

/**
 * 차량 데이터 접근 계층. 앱의 나머지 부분은 이 인터페이스만 본다.
 *  - mock 플레이버: MockVehicleDataSource (시나리오 목업)
 *  - pleos 플레이버: PleosVehicleDataSource (Pleos Vehicle SDK)
 *
 * 규칙: 차종마다 지원 범위가 다르므로 supported() 로 확인된 기능만 사용한다.
 * 확인 불가한 값은 null 로 두고, 점검 엔진은 그 항목을 "눈으로 확인"으로 전환한다.
 */
interface VehicleDataSource {
    suspend fun connect(): Boolean
    suspend fun supported(): Set<Feature>
    suspend fun snapshot(): VehicleSnapshot

    /** 아래 제어 함수들은 운전자가 확인(버튼·음성 "응")한 뒤에만 호출한다. 성공 시 true. */
    suspend fun setSeatHeat(level: Int): Boolean
    suspend fun setSteeringWheelHeat(on: Boolean): Boolean
    suspend fun setSideMirrorHeat(on: Boolean): Boolean
    suspend fun setCabinTemperature(celsius: Float): Boolean

    /** 등화 점검용. 앱이 켤 수 없는 등이면 false 를 돌려주고 UI가 운전자에게 요청한다. */
    suspend fun setLamp(lamp: Lamp, on: Boolean): Boolean

    /**
     * 문·창문·실내등·전조등 상태가 바뀌면 onChange 를 부른다(지원하는 차만, 메인 스레드 보장 없음).
     * 화면이 보일 때 시작하고 화면을 떠나면 unwatchCabin() 으로 해제한다.
     */
    fun watchCabin(onChange: () -> Unit) = Unit
    fun unwatchCabin() = Unit

    fun release()
}
