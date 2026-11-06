package com.chageunchageun.app.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 음성 출력.
 *  - 두 플레이버 모두: Pleos TextToSpeech SDK(ON_DEVICE, PleosTtsVoice) → 실패 시 AndroidTtsVoice 로 자동 전환
 */
interface Voice {
    /** @param onDone 말하기가 끝나면 호출(음성 응답 듣기 시작용). 말할 수 없으면 바로 호출된다. */
    fun speak(text: String, onDone: (() -> Unit)? = null)
    fun stop()
    fun release()
}

/**
 * @param onStatus 초기화가 끝나면 한 번 호출된다. true = 한국어로 말할 수 있음,
 *                 false = 음성 엔진이 없거나 한국어를 지원하지 않음(화면 안내로 대신).
 */
class AndroidTtsVoice(context: Context, private val onStatus: (Boolean) -> Unit = {}) : Voice {

    private var ready = false
    private var initDone = false
    private var pending: Pair<String, (() -> Unit)?>? = null
    private val doneCallbacks = mutableMapOf<String, () -> Unit>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        Log.i(TAG, "init status=$status (SUCCESS=${TextToSpeech.SUCCESS}) engine=${runCatching { tts.defaultEngine }.getOrNull()}")
        if (status == TextToSpeech.SUCCESS) {
            val lang = tts.setLanguage(Locale.KOREAN)
            Log.i(TAG, "setLanguage(KOREAN)=$lang (MISSING_DATA=${TextToSpeech.LANG_MISSING_DATA}, NOT_SUPPORTED=${TextToSpeech.LANG_NOT_SUPPORTED})")
            ready = lang >= TextToSpeech.LANG_AVAILABLE
            if (ready) VoiceMonitor.engine(ENGINE)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = VoiceMonitor.started(ENGINE)
                override fun onDone(utteranceId: String?) {
                    VoiceMonitor.done(ENGINE)
                    synchronized(doneCallbacks) { doneCallbacks.remove(utteranceId) }?.invoke()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    VoiceMonitor.error(ENGINE, "onError")
                    synchronized(doneCallbacks) { doneCallbacks.remove(utteranceId) }?.invoke()
                }
            })
        }
        initDone = true
        val p = pending
        pending = null
        if (p != null) {
            if (ready) speak(p.first, p.second) else p.second?.invoke()
        }
        onStatus(ready)
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        if (!ready) {
            if (initDone) onDone?.invoke() else pending = text to onDone
            return
        }
        val id = "chageun-${System.nanoTime()}"
        VoiceMonitor.requested(ENGINE, text)
        if (onDone != null) synchronized(doneCallbacks) { doneCallbacks[id] = onDone }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override fun stop() {
        tts.stop()
    }

    override fun release() {
        tts.shutdown()
    }

    private companion object {
        const val TAG = "ChageunTts"
        const val ENGINE = "Android TTS"
    }
}

/**
 * 음성 재생·인식이 실제로 일어났는지 확인하기 위한 기록(로그 태그 ChageunVoice).
 * state 는 debug 빌드에서 음성 안내 카드 옆 상태 칩("말하는 중", "듣는 중", "들은 말: 응")으로 보인다.
 */
object VoiceMonitor {
    const val TAG = "ChageunVoice"
    private val _state = MutableStateFlow<String?>(null)
    val state: StateFlow<String?> = _state

    fun engine(name: String) = Log.i(TAG, "[$name] 사용 준비됨").let { }
    fun requested(engine: String, text: String) = Log.i(TAG, "[$engine] 말하기 요청: ${text.take(40)}").let { }
    fun started(engine: String) {
        Log.i(TAG, "[$engine] 재생 시작")
        _state.value = "말하는 중"
    }
    fun done(engine: String, stopped: Boolean = false) {
        Log.i(TAG, "[$engine] 재생 ${if (stopped) "중지" else "완료"}")
        if (_state.value == "말하는 중") _state.value = null
    }
    fun error(engine: String, why: String) {
        Log.w(TAG, "[$engine] 재생 오류: $why")
        _state.value = "음성 오류"
    }
    fun listening() {
        Log.i(TAG, "[Pleos STT] 듣는 중")
        _state.value = "듣는 중"
    }
    fun heard(text: String?, why: String? = null) {
        Log.i(TAG, "[Pleos STT] 인식 결과: ${text ?: "없음"}${why?.let { " ($it)" } ?: ""}")
        _state.value = if (text.isNullOrBlank()) "못 들었어요" else "들은 말: $text"
    }
}
