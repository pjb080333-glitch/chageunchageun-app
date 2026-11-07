package com.chageunchageun.app.data

import com.chageunchageun.app.model.Feature
import com.chageunchageun.app.model.Lamp
import com.chageunchageun.app.model.VehicleSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #005 H5: 한 APK 안에서 연동 ↔ 데모 전환. */
class DataSourcesTest {

    /** 실제 차량 대신 제어 호출 횟수만 세는 연동 차량. */
    private class CountingVehicle : VehicleDataSource {
        var controls = 0
        override suspend fun connect() = true
        override suspend fun supported() = Feature.entries.toSet()
        override suspend fun snapshot() = VehicleSnapshot(0L, 48_620.0, emptyList(), emptyList())
        override suspend fun setSeatHeat(level: Int): Boolean { controls++; return true }
        override suspend fun setSteeringWheelHeat(on: Boolean): Boolean { controls++; return true }
        override suspend fun setSideMirrorHeat(on: Boolean): Boolean { controls++; return true }
        override suspend fun setCabinTemperature(celsius: Float): Boolean { controls++; return true }
        override suspend fun setLamp(lamp: Lamp, on: Boolean): Boolean { controls++; return true }
        override fun release() = Unit
    }

    private val dir = File(System.getProperty("java.io.tmpdir"), "chageun-test")
    private val liveCar = CountingVehicle()
    private val live = DataSources(
        liveCar, MockNavigationSource(), MockScheduleSource(), NoWeather,
        HistoryStore(File(dir, HistoryFiles.name(demo = false))), isDemo = false,
    )
    private fun demo() = DataSources(
        MockVehicleDataSource(), MockNavigationSource(), MockScheduleSource(), NoWeather,
        HistoryStore(File(dir, HistoryFiles.name(demo = true))), isDemo = true,
    )

    @Test
    fun startMode_liveOnlyWhenConnectedWithFeatures() {
        assertEquals(AppMode.LIVE, ModeSelector.startMode(connected = true, supported = setOf(Feature.ODOMETER)))
        // 일반 안드로이드 기기: SDK 연결 실패 → 데모
        assertEquals(AppMode.DEMO, ModeSelector.startMode(connected = false, supported = emptySet()))
        // 연결은 됐지만 읽을 수 있는 기능이 하나도 없음 → 데모
        assertEquals(AppMode.DEMO, ModeSelector.startMode(connected = true, supported = emptySet()))
    }

    @Test
    fun switch_swapsAllSources() {
        val sw = SourceSwitch(live, ::demo)
        assertSame(live, sw.current)
        assertTrue(sw.use(demoMode = true))
        assertTrue(sw.current.isDemo)
        assertTrue(sw.current.vehicle is MockVehicleDataSource)
        assertNotEquals(live.navigation, sw.current.navigation)
        assertFalse(sw.use(demoMode = true)) // 이미 데모
        assertTrue(sw.use(demoMode = false))
        assertSame(live, sw.current)
    }

    @Test
    fun switch_withoutLive_staysDemo() {
        val sw = SourceSwitch(live = null, demoFactory = ::demo)
        assertTrue(sw.current.isDemo)
        assertFalse(sw.canLeaveDemo)
        assertFalse(sw.use(demoMode = false))
        assertTrue(sw.current.isDemo)
    }

    @Test
    fun demoMode_sendsNoRealVehicleControl() = runBlocking {
        val sw = SourceSwitch(live, ::demo)
        sw.use(demoMode = true)
        val text = WinterPreset.run(sw.current, Feature.entries.toSet())
        assertEquals(0, liveCar.controls)
        assertTrue(text.endsWith(WinterPreset.DEMO_NOTE))
        // 연동 모드에서는 실제로 보낸다(4개: 시트·핸들·사이드미러·실내 온도)
        sw.use(demoMode = false)
        val liveText = WinterPreset.run(sw.current, Feature.entries.toSet())
        assertEquals(4, liveCar.controls)
        assertFalse(liveText.contains("데모"))
    }

    @Test
    fun drivingStatus_parkedUnknownUntilBothKnown() {
        // 2단계에서 발견: 기어 값이 오기 전 "브레이크 해제"만으로 주차 아님이 되어 데모 칩이 사라졌다.
        assertEquals(null, DrivingStatus(parkingBrakeOn = false).parked)
        assertEquals(true, DrivingStatus(parkingBrakeOn = false, gearPark = true).parked)
        assertEquals(true, DrivingStatus(parkingBrakeOn = true).parked)
        assertEquals(false, DrivingStatus(parkingBrakeOn = false, gearPark = false).parked)
        assertEquals(null, DrivingStatus().parked)
    }

    @Test
    fun history_isSeparatedByMode() {
        val sw = SourceSwitch(live, ::demo)
        val liveFile = sw.current.history.file
        sw.use(demoMode = true)
        val demoFile = sw.current.history.file
        assertNotEquals(liveFile, demoFile)
        assertEquals(HistoryFiles.LIVE, liveFile.name)
        assertEquals(HistoryFiles.DEMO, demoFile.name)
    }
}
