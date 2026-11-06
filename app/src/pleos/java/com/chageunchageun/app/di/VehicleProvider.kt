package com.chageunchageun.app.di

import android.content.Context
import com.chageunchageun.app.data.CalendarScheduleSource
import com.chageunchageun.app.data.DrivingStateSource
import com.chageunchageun.app.data.NavigationSource
import com.chageunchageun.app.data.OpenMeteoWeather
import com.chageunchageun.app.data.PleosTtsVoice
import com.chageunchageun.app.data.PleosVoiceInput
import com.chageunchageun.app.data.ScheduleSource
import com.chageunchageun.app.data.VehicleDataSource
import com.chageunchageun.app.data.Voice
import com.chageunchageun.app.data.VoiceInput
import com.chageunchageun.app.data.WeatherSource
import com.chageunchageun.app.pleos.AaosCarState
import com.chageunchageun.app.pleos.PleosNavigationSource
import com.chageunchageun.app.pleos.PleosVehicleDataSource

/** pleos 플레이버: Pleos SDK(Vehicle·NaviHelper·TTS·STT) + AAOS Car API + 기기 캘린더 + 공개 날씨(Open-Meteo). */
object VehicleProvider {
    const val isDemo = false

    private var carState: AaosCarState? = null

    /** 시동·주차 브레이크·주행 제한(AAOS). 차량 데이터와 운행 상태가 같은 연결을 쓴다. */
    private fun car(context: Context): AaosCarState =
        carState ?: AaosCarState(context).also { carState = it }

    fun vehicle(context: Context): VehicleDataSource = PleosVehicleDataSource(context, car(context))
    fun navigation(context: Context): NavigationSource = PleosNavigationSource(context)
    fun schedule(context: Context): ScheduleSource = CalendarScheduleSource(context)
    fun weather(context: Context): WeatherSource = OpenMeteoWeather()
    fun voice(context: Context, onStatus: (Boolean) -> Unit): Voice = PleosTtsVoice(context, onStatus)
    fun voiceInput(context: Context): VoiceInput = PleosVoiceInput(context)
    fun drivingState(context: Context): DrivingStateSource = car(context)
}
