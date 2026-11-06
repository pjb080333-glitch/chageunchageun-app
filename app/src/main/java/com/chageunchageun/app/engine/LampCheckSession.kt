package com.chageunchageun.app.engine

import com.chageunchageun.app.model.Lamp

enum class LampAnswer(val label: String) { OK("켜짐"), FAIL("안 켜짐"), SKIPPED("건너뜀") }

data class LampStep(val lamp: Lamp, val appControls: Boolean) {
    val instruction: String
        get() = when {
            lamp == Lamp.BRAKE ->
                "브레이크 페달을 밟아 주세요. 뒤에 있는 사람에게 봐 달라고 하거나, 뒤쪽 유리나 벽에 비친 불빛을 확인하세요."
            lamp == Lamp.REVERSE ->
                "브레이크를 밟은 채 기어를 R에 두고 뒤쪽 불빛을 확인하세요. 확인 후 바로 P로 돌려 주세요."
            lamp == Lamp.PLATE ->
                "미등을 켠 상태에서 뒤 번호판 위 불빛을 확인하세요."
            appControls ->
                "제가 ${lamp.label.eulReul()} 켤게요. 켜졌는지 확인해 알려 주세요."
            else ->
                "${lamp.label.eulReul()} 켜 주세요. 켜졌는지 확인해 알려 주세요."
        }
}

/**
 * 등화 점검: 주차 상태에서 등을 하나씩 켜고 운전자가 "켜졌어/안 켜졌어"로 답한다.
 * 2024년 자동차 검사 불합격 1위(등화장치)를 검사 전에 잡는 것이 목적.
 *
 * @param controllable 앱이 직접 켤 수 있는 등 (Light 제어 capability 확인 결과). 나머지는 운전자에게 요청.
 */
class LampCheckSession(controllable: Set<Lamp>) {

    val steps: List<LampStep> = Lamp.entries.map { LampStep(it, it in controllable) }
    private val answers = linkedMapOf<Lamp, LampAnswer>()

    var index: Int = 0
        private set

    val current: LampStep? get() = steps.getOrNull(index)
    val isDone: Boolean get() = index >= steps.size

    fun answer(answer: LampAnswer) {
        val step = current ?: return
        answers[step.lamp] = answer
        index++
    }

    fun results(): Map<Lamp, LampAnswer> = answers.toMap()

    /** 결과 화면용 (#004 B++++): 안 켜진 등을 맨 앞에, 나머지는 점검 순서대로. */
    fun orderedResults(): List<Pair<Lamp, LampAnswer>> {
        val all = steps.mapNotNull { st -> answers[st.lamp]?.let { st.lamp to it } }
        return all.filter { it.second == LampAnswer.FAIL } + all.filterNot { it.second == LampAnswer.FAIL }
    }

    fun summary(): Finding {
        val failed = answers.filterValues { it == LampAnswer.FAIL }.keys
        return if (failed.isEmpty()) {
            Finding(
                id = "lamp-summary",
                kind = CheckKind.LAMP,
                severity = Severity.INFO,
                title = "등화 점검 이상 없음",
                speech = "확인한 등은 모두 잘 켜져요.",
            )
        } else {
            val names = failed.joinToString(", ") { it.label }
            Finding(
                id = "lamp-summary",
                kind = CheckKind.LAMP,
                severity = Severity.WARN,
                title = "등화 점검: ${failed.size}개 이상",
                speech = "${names.iGa()} 안 켜져요. 자동차 검사에서 가장 많이 떨어지는 항목이 등화장치예요. " +
                    "전구 교체는 정비소에서 금방 할 수 있어요.",
                actions = listOf(SuggestedAction.AddWaypoint("자동차 정비소", "정비소 경유")),
            )
        }
    }
}
