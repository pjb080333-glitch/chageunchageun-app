package com.chageunchageun.app.engine

/**
 * 여러 점검 결과를 한 번의 음성 브리핑으로 묶는다.
 * 원칙: 출발 일정이 먼저, 그 다음 심각한 순서. 음성은 최대 3건, 나머지는 화면으로.
 * 이상이 없으면 짧게 "이상 없어요"만 말한다 (관리에 무관심한 운전자가 피로감을 느끼지 않도록).
 */
object BriefingComposer {

    const val MAX_SPOKEN = 3

    fun sort(findings: List<Finding>): List<Finding> =
        findings.sortedWith(
            compareBy<Finding> { if (it.kind == CheckKind.DEPARTURE) 0 else 1 }
                .thenByDescending { it.severity.rank }
                .thenBy { it.kind.ordinal },
        )

    fun speech(findings: List<Finding>): String {
        val sorted = sort(findings)
        val departure = sorted.filter { it.kind == CheckKind.DEPARTURE }
        val issues = sorted.filter { it.kind != CheckKind.DEPARTURE && it.severity != Severity.INFO }
        val notes = sorted.filter { it.kind != CheckKind.DEPARTURE && it.severity == Severity.INFO }

        val spoken = (departure + issues + notes).take(MAX_SPOKEN)
        val parts = spoken.map { it.speech }.toMutableList()
        if (issues.isEmpty()) parts += "차량 상태는 이상 없어요."
        val rest = sorted.size - spoken.size
        if (rest > 0) parts += "그 외 ${rest}건은 화면에서 확인하세요."
        return parts.joinToString(" ")
    }
}
