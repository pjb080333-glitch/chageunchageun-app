package com.chageunchageun.app.data

/**
 * 짧은 음성 응답("응/아니", "켜졌어/안 켜졌어") 듣기. 버튼은 그대로 두고, 음성은 보조 수단이다.
 *  - 두 플레이버 모두: Pleos SpeechToText SDK(ON_DEVICE, PleosVoiceInput). 음성 서비스가 없으면 듣지 않는다(버튼만).
 */
interface VoiceInput {
    /** 한 번 듣고 최종 인식 문장을 넘긴다. 인식 실패·시간 초과면 null. */
    fun listen(onResult: (String?) -> Unit)
    fun stop()
    fun release()
}

object NoVoiceInput : VoiceInput {
    override fun listen(onResult: (String?) -> Unit) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

/** 인식 문장 → 예/아니오. 판단할 수 없으면 null. "안 켜졌어"처럼 부정이 앞서면 아니오로 본다. */
object YesNo {
    private val no = listOf("아니", "안 켜", "안켜", "꺼져", "안 돼", "안돼", "아뇨", "싫어", "고장")
    private val yes = listOf("응", "네", "예", "그래", "좋아", "켜졌", "켜 졌", "됐어", "돼", "맞아")

    fun parse(text: String?): Boolean? {
        val t = text?.trim()?.plus(" ") ?: return null
        if (no.any { t.contains(it) }) return false
        if (yes.any { t.contains(it) }) return true
        return null
    }
}
