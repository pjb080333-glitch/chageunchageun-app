package com.chageunchageun.app.data

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode

/**
 * Pleos TextToSpeech SDK 3.3.1 (ON_DEVICE 모드).
 * 출처: Pleos 개발자 문서 TextToSpeech SDK (intro, initialization, set-locale, start-and-stop, event-listeners).
 * ON_DEVICE 모드는 앱 등록(Client ID/Secret)이 필요 없다. 서버 음성(HYBRID)은 사용자가 Client ID 를 발급받은 뒤 전환.
 * 초기화에 실패하거나 재생 오류가 나면 안드로이드 기본 TTS(AndroidTtsVoice)로 자동 전환한다.
 * mock·pleos 두 플레이버가 함께 쓴다(Pleos 에뮬레이터에서는 데모도 Pleos 음성으로 말하고, 일반 에뮬레이터에서는 기본 TTS).
 * 재생 시작·완료·오류는 VoiceMonitor(로그 태그 ChageunVoice)에 남긴다.
 */
class PleosTtsVoice(context: Context, private val onStatus: (Boolean) -> Unit) : Voice {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var pleos: TextToSpeech? = null
    private var fallback: Voice? = null
    private var ready = false
    private var pending: Pair<String, (() -> Unit)?>? = null
    private var onDone: (() -> Unit)? = null

    private val events = object : EventListener {
        override fun onStart() = VoiceMonitor.started(ENGINE)
        override fun onReady() = Unit
        override fun onDone() {
            VoiceMonitor.done(ENGINE)
            finish()
        }
        override fun onStop() = VoiceMonitor.done(ENGINE, stopped = true)
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: TtsError) {
            VoiceMonitor.error(ENGINE, errorType.toString())
            Log.w(TAG, "Pleos TTS error=$errorType → 기본 TTS 로 전환")
            switchToFallback()
        }
    }

    init {
        val tts = runCatching { TextToSpeech(appContext, TtsMode.ON_DEVICE) }
            .onFailure { Log.w(TAG, "Pleos TTS 생성 실패", it) }.getOrNull()
        if (tts == null) {
            switchToFallback()
        } else {
            pleos = tts
            runCatching {
                tts.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        main.post {
                            Log.i(TAG, "Pleos TTS initialize 성공")
                            VoiceMonitor.engine(ENGINE)
                            runCatching {
                                tts.addEventListener(events)
                                tts.setLocale(Locale.KOREA)
                            }.onFailure { Log.w(TAG, "Pleos TTS 설정 실패", it) }
                            ready = true
                            onStatus(true)
                            pending?.let { (t, d) -> pending = null; speak(t, d) }
                        }
                    }

                    override fun onFailure(errorType: TtsError) {
                        main.post {
                            Log.w(TAG, "Pleos TTS initialize 실패=$errorType → 기본 TTS 로 전환")
                            switchToFallback()
                        }
                    }
                })
            }.onFailure {
                Log.w(TAG, "Pleos TTS initialize 예외", it)
                switchToFallback()
            }
            // 음성 서비스가 없어 응답이 오지 않는 경우 대비.
            main.postDelayed({
                if (!ready && fallback == null) {
                    Log.w(TAG, "Pleos TTS initialize 응답 없음(${INIT_TIMEOUT_MS}ms) → 기본 TTS 로 전환")
                    switchToFallback()
                }
            }, INIT_TIMEOUT_MS)
        }
    }

    private fun switchToFallback() {
        if (fallback != null) return
        ready = false
        pleos?.let { runCatching { it.removeEventListener(events); it.release() } }
        pleos = null
        val f = AndroidTtsVoice(appContext, onStatus)
        fallback = f
        val p = pending ?: onDone?.let { "" to it }
        pending = null
        onDone = null
        p?.let { (t, d) -> if (t.isBlank()) d?.invoke() else f.speak(t, d) }
    }

    private fun finish() {
        val d = onDone
        onDone = null
        d?.let { main.post(it) }
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        fallback?.let { return it.speak(text, onDone) }
        val tts = pleos
        if (!ready || tts == null) {
            pending = text to onDone
            return
        }
        this.onDone = onDone
        VoiceMonitor.requested(ENGINE, text)
        runCatching { tts.speak(text, true) }.onFailure {
            Log.w(TAG, "Pleos TTS speak 실패 → 기본 TTS 로 전환", it)
            pending = text to onDone
            this.onDone = null
            switchToFallback()
        }
    }

    override fun stop() {
        fallback?.stop() ?: runCatching { pleos?.stop() }
    }

    override fun release() {
        fallback?.release()
        pleos?.let { runCatching { it.removeEventListener(events); it.release() } }
    }

    private companion object {
        const val TAG = "ChageunTts"
        const val ENGINE = "Pleos TTS"
        const val INIT_TIMEOUT_MS = 5_000L
    }
}

/**
 * Pleos SpeechToText SDK 3.3.1 (ON_DEVICE 모드). 짧은 "응/아니" 응답 한 번 듣기.
 * 출처: Pleos 개발자 문서 SpeechToText SDK (intro, initialization, result-listeners, start-and-stop).
 * 초기화에 실패하면 아무것도 듣지 않는다(버튼으로 답한다).
 */
class PleosVoiceInput(context: Context) : VoiceInput {

    private val main = Handler(Looper.getMainLooper())
    /** STT SDK 호출 전용 스레드 (request·stop·release). 화면 스레드가 SDK 응답을 기다리지 않게. */
    private val workerThread = HandlerThread("chageun-stt").apply { start() }
    private val worker = Handler(workerThread.looper)
    private var stt: SpeechToText? = null
    private var ready = false
    private var callback: ((String?) -> Unit)? = null
    private var last: String? = null
    private val timeout = Runnable { stop() }

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = VoiceMonitor.listening()
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            last = stt
            if (completed) main.post { deliver(stt) }
        }
        override fun onEndedRecognition() {
            main.post { deliver(last) }
        }
        override fun onError(errorType: SttError) {
            Log.w(TAG, "STT error=$errorType")
            main.post { deliver(null) }
        }
    }

    init {
        stt = runCatching { SpeechToText(context.applicationContext, SttMode.ON_DEVICE) }
            .onFailure { Log.w(TAG, "STT 생성 실패", it) }.getOrNull()
        stt?.let { s ->
            runCatching {
                s.initialize(object : SttInitListener {
                    override fun onSuccess() {
                        Log.i(TAG, "STT initialize 성공")
                        runCatching { s.addListener(listener); s.setLocale(Locale.KOREA) }
                        ready = true
                    }
                    override fun onFailure(errorType: SttError) {
                        Log.w(TAG, "STT initialize 실패=$errorType (버튼으로만 응답)")
                    }
                })
            }.onFailure { Log.w(TAG, "STT initialize 예외", it) }
        }
    }

    override fun listen(onResult: (String?) -> Unit) {
        val s = stt
        if (!ready || s == null) return
        callback = onResult
        last = null
        VoiceMonitor.listening()
        // #004 B+++: SDK 호출은 화면 스레드 밖에서(인식 중 stop 이 화면을 멈추게 한 의심 — S6·S9).
        worker.post {
            runCatching { s.request() }.onFailure {
                Log.w(TAG, "STT request 실패", it)
                main.post { if (callback === onResult) callback = null }
            }
        }
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, LISTEN_MS)
    }

    private fun deliver(text: String?) {
        val cb = callback ?: return
        callback = null
        main.removeCallbacks(timeout)
        Log.i(TAG, "STT 결과=$text")
        VoiceMonitor.heard(text)
        cb(text)
    }

    override fun stop() {
        main.removeCallbacks(timeout)
        if (callback == null) return
        callback = null
        VoiceMonitor.heard(null, "시간 초과·취소")
        val s = stt ?: return
        val t0 = System.currentTimeMillis()
        worker.post {
            runCatching { s.stop() }.onFailure { Log.w(TAG, "STT stop 실패", it) }
            val ms = System.currentTimeMillis() - t0
            if (ms > 1_000) Log.w(TAG, "STT stop ${ms}ms 걸림")
        }
    }

    override fun release() {
        stop()
        val s = stt ?: return
        stt = null
        worker.post {
            runCatching { s.removeListener(listener); s.release() }
            workerThread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "ChageunStt"
        const val LISTEN_MS = 6_000L
    }
}
