package com.chageunchageun.app.di

import android.content.Context
import com.chageunchageun.app.data.CalendarScheduleSource
import com.chageunchageun.app.data.DataSources
import com.chageunchageun.app.data.DrivingStateSource
import com.chageunchageun.app.data.HistoryFiles
import com.chageunchageun.app.data.HistoryStore
import com.chageunchageun.app.data.OpenMeteoWeather
import com.chageunchageun.app.data.PleosTtsVoice
import com.chageunchageun.app.data.PleosVoiceInput
import com.chageunchageun.app.data.Voice
import com.chageunchageun.app.data.VoiceInput
import com.chageunchageun.app.pleos.AaosCarState
import com.chageunchageun.app.pleos.PleosNavigationSource
import com.chageunchageun.app.pleos.PleosVehicleDataSource

/**
 * pleos 플레이버 = 제출용 통합 앱 (#005): 연동 묶음(Pleos SDK + AAOS + 기기 캘린더 + Open-Meteo)과
 * 데모 묶음(demoSources)을 둘 다 갖고, 실행 중에 고른다(MainViewModel). 연결에 실패하면 데모로 시작.
 */
object VehicleProvider {
    /** 연동 묶음이 있는 빌드인지(캘린더 권한 요청 여부). */
    const val hasLive = true

    private var carState: AaosCarState? = null

    /** 시동·주차 브레이크·주행 제한(AAOS). 차량 데이터와 운행 상태가 같은 연결을 쓴다. */
    private fun car(context: Context): AaosCarState =
        carState ?: AaosCarState(context).also { carState = it }

    /** 연동 묶음. 만들기 자체가 실패하면(SDK 없는 기기 등) null → 데모로 시작. */
    fun live(context: Context): DataSources? = runCatching {
        DataSources(
            vehicle = PleosVehicleDataSource(context, car(context)),
            navigation = PleosNavigationSource(context),
            schedule = CalendarScheduleSource(context),
            weather = OpenMeteoWeather(),
            history = HistoryStore(context, HistoryFiles.LIVE),
            isDemo = false,
        )
    }.getOrNull()

    fun voice(context: Context, onStatus: (Boolean) -> Unit): Voice = PleosTtsVoice(context, onStatus)
    fun voiceInput(context: Context): VoiceInput = PleosVoiceInput(context)
    fun drivingState(context: Context): DrivingStateSource = car(context)
}
