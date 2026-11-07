package com.chageunchageun.app.di

import android.content.Context
import com.chageunchageun.app.data.DataSources
import com.chageunchageun.app.data.DrivingStateSource
import com.chageunchageun.app.data.NoDrivingState
import com.chageunchageun.app.data.PleosTtsVoice
import com.chageunchageun.app.data.PleosVoiceInput
import com.chageunchageun.app.data.Voice
import com.chageunchageun.app.data.VoiceInput

/**
 * mock 플레이버(개발·단위 테스트용, #005부터 release 는 만들지 않음): 연동 묶음이 없어 항상 데모 모드.
 * 음성은 Pleos TTS/STT → 없으면 안드로이드 TTS.
 */
object VehicleProvider {
    /** 연동 묶음이 있는 빌드인지(캘린더 권한 요청 여부). */
    const val hasLive = false
    fun live(context: Context): DataSources? = null
    fun voice(context: Context, onStatus: (Boolean) -> Unit): Voice = PleosTtsVoice(context, onStatus)
    fun voiceInput(context: Context): VoiceInput = PleosVoiceInput(context)
    fun drivingState(context: Context): DrivingStateSource = NoDrivingState
}
