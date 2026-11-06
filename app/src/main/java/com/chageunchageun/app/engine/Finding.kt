package com.chageunchageun.app.engine

enum class Severity(val rank: Int, val label: String) {
    INFO(0, "참고"),
    ADVISE(1, "권장"),
    WARN(2, "주의"),
    URGENT(3, "위험"),
}

enum class CheckKind(val label: String) {
    DEPARTURE("출발 브리핑"),
    DAILY("일상점검"),
    EXIT("하차 점검"),
    PERIODIC("정기점검"),
    SEASONAL("계절점검"),
    LAMP("등화 점검"),
    FOCUS("중점관리"),
}

/** 점검 결과에 붙는 "실행 연결" 버튼. */
sealed interface SuggestedAction {
    val label: String

    data class Navigate(val destination: String) : SuggestedAction {
        override val label: String get() = "길안내 시작"
    }

    data class AddWaypoint(val query: String, override val label: String) : SuggestedAction

    data object WinterPreset : SuggestedAction {
        override val label: String get() = "겨울 출발 준비 실행"
    }

    data object StartLampCheck : SuggestedAction {
        override val label: String get() = "등화 점검 시작"
    }

    data object OpenChecklist : SuggestedAction {
        override val label: String get() = "점검표 열기"
    }
}

data class Finding(
    val id: String,
    val kind: CheckKind,
    val severity: Severity,
    val title: String,
    /** 음성으로 읽어줄 문장. "지금 운전해도 되는지, 언제까지 뭘 해야 하는지"가 드러나게 쓴다. */
    val speech: String,
    val actions: List<SuggestedAction> = emptyList(),
    /** 카드 오른쪽 위 핵심 숫자(예: "+800km 초과", "D-24일"). 화면 표시용이며 판정에는 쓰지 않는다. */
    val badge: String? = null,
)

internal const val DAY_MILLIS = 24L * 60 * 60 * 1000
