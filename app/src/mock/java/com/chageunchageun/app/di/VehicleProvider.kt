package com.chageunchageun.app.di

import android.content.Context
import com.chageunchageun.app.data.DrivingStateSource
import com.chageunchageun.app.data.MockNavigationSource
import com.chageunchageun.app.data.MockScheduleSource
import com.chageunchageun.app.data.MockVehicleDataSource
import com.chageunchageun.app.data.NavigationSource
import com.chageunchageun.app.data.NoDrivingState
import com.chageunchageun.app.data.NoWeather
import com.chageunchageun.app.data.PleosTtsVoice
import com.chageunchageun.app.data.PleosVoiceInput
import com.chageunchageun.app.data.ScheduleSource
import com.chageunchageun.app.data.VehicleDataSource
import com.chageunchageun.app.data.Voice
import com.chageunchageun.app.data.VoiceInput
import com.chageunchageun.app.data.WeatherSource

/**
 * mock 플레이버: 차량·일정·내비·날씨는 데모 시나리오로 대체.
 * 음성은 Pleos TTS/STT(음성 서비스가 있는 Pleos 에뮬레이터에서 소리가 나도록) → 없으면 안드로이드 TTS.
 */
object VehicleProvider {
    const val isDemo = true
    fun vehicle(context: Context): VehicleDataSource = MockVehicleDataSource()
    fun navigation(context: Context): NavigationSource = MockNavigationSource()
    fun schedule(context: Context): ScheduleSource = MockScheduleSource()
    fun weather(context: Context): WeatherSource = NoWeather
    fun voice(context: Context, onStatus: (Boolean) -> Unit): Voice = PleosTtsVoice(context, onStatus)
    fun voiceInput(context: Context): VoiceInput = PleosVoiceInput(context)
    fun drivingState(context: Context): DrivingStateSource = NoDrivingState
}
