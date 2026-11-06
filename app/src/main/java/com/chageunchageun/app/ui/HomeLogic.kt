package com.chageunchageun.app.ui

import com.chageunchageun.app.engine.BriefingComposer
import com.chageunchageun.app.engine.CheckKind
import com.chageunchageun.app.engine.Finding
import com.chageunchageun.app.engine.Severity
import com.chageunchageun.app.engine.SuggestedAction

// 홈 화면 배치·중복 방지 규칙. Android 의존성 없이 순수 Kotlin (JVM 단위 테스트 대상, #004 B·C·D·E1).

/** 홈 2단 배치: 왼쪽 "지금 조치할 것" / 오른쪽 "안내"·"다가오는 점검". 심각도는 엔진 값 그대로. */
data class HomeSections(
    val action: List<Finding>,
    val guide: List<Finding>,
    val upcoming: List<Finding>,
)

object HomeLayout {

    /** 왼쪽에 최소한 보여 주는 카드 수. 남는 높이가 있으면 더 보여 주고, 넘칠 때만 "N건 더 보기" (#004 B+ 1). */
    const val MIN_ACTION_VISIBLE = 3

    /**
     * 남는 높이 [available] 안에 카드를 몇 개 놓을지 (#004 B+ 1·4). 단위는 px.
     * 쌓는 순서: 카드 n개 → (다 못 놓으면) "더 보기" 버튼 → (있으면) [footer]. 블록 사이는 [gap].
     * 전부 들어가면 전부(“더 보기” 없음), 아니면 들어가는 만큼, 그래도 최소 [minVisible] 개.
     */
    fun fitCount(heights: List<Int>, gap: Int, available: Int, footer: Int?, more: Int, minVisible: Int): Int {
        val total = heights.size
        fun used(n: Int): Int {
            val blocks = heights.take(n) + listOfNotNull(if (n < total) more else null, footer)
            return blocks.sum() + gap * (blocks.size - 1).coerceAtLeast(0)
        }
        // 최소 개수는 실제로 들어갈 때만 지킨다. 글씨가 커져(#004 B++) 최소 개수도 안 들어가면
        // 카드를 잘라 보이지 않고 들어가는 만큼(최소 1개) + "더 보기".
        val floor = minOf(minVisible, total)
        return (total downTo floor).firstOrNull { used(it) <= available }
            ?: (floor - 1 downTo 1).firstOrNull { used(it) <= available }
            ?: minOf(1, total)
    }

    fun split(findings: List<Finding>): HomeSections {
        val sorted = BriefingComposer.sort(findings)
        val action = sorted.filter(::isAction)
        val rest = sorted.filterNot(::isAction)
        return HomeSections(
            action = action,
            guide = rest.filter { it.kind != CheckKind.PERIODIC },
            upcoming = rest.filter { it.kind == CheckKind.PERIODIC },
        )
    }

    /**
     * 지금 조치할 것 (#004 B+ 2, DECISIONS #79 — #66 수정):
     *  - 실행 버튼이 "지금 하는 동작"(겨울 출발 준비 실행·길안내·경유지 추가·등화 점검 시작)인 카드 — 심각도와 상관없이
     *  - 주의·위험 전부 (버튼이 없어도 위험한 것은 왼쪽)
     *  - 출발 일정, 하차 점검(참고 제외)
     * 점검표 열기처럼 "나중에 해도 되는 동작"만 있거나 버튼이 없는 권장·참고 항목은 오른쪽.
     */
    fun isAction(f: Finding): Boolean =
        f.kind == CheckKind.DEPARTURE ||
            (f.kind == CheckKind.EXIT && f.severity != Severity.INFO) ||
            f.severity.rank >= Severity.WARN.rank ||
            f.actions.any(::isNowAction)

    /** 운전자가 지금 바로 실행하는 동작. 점검표 열기는 "나중에 해도 되는 동작". */
    fun isNowAction(a: SuggestedAction): Boolean =
        a is SuggestedAction.Navigate || a is SuggestedAction.AddWaypoint ||
            a == SuggestedAction.WinterPreset || a == SuggestedAction.StartLampCheck

    /** 상단 요약 줄: "조치 필요 3건 (위험 1 · 주의 1 · 권장 1)". 참고 항목은 세지 않는다. */
    fun summary(findings: List<Finding>): String {
        val counted = findings.filter(::isAction).filter { it.severity != Severity.INFO }
        if (counted.isEmpty()) return "조치할 항목 없음"
        val parts = listOf(Severity.URGENT, Severity.WARN, Severity.ADVISE)
            .mapNotNull { sev -> counted.count { it.severity == sev }.takeIf { it > 0 }?.let { "${sev.label} $it" } }
        return "조치 필요 ${counted.size}건 (${parts.joinToString(" · ")})"
    }

    /** 요약 줄 색에 쓰는 가장 높은 심각도. 조치할 것이 없으면 null. */
    fun topSeverity(findings: List<Finding>): Severity? =
        findings.filter(::isAction).filter { it.severity != Severity.INFO }.maxByOrNull { it.severity.rank }?.severity
}

/**
 * 장소 검색 "다시 찾기" 제한 (#004 D): [windowMillis] 안에 [max] 번까지.
 * 공개 지도 서버가 장애일 때 연달아 눌러 요청이 쌓이지 않게 한다.
 */
class RetryLimiter(private val max: Int = 3, private val windowMillis: Long = 60_000) {
    private val times = ArrayDeque<Long>()

    /** 지금 다시 찾을 수 있으면 기록하고 true. */
    fun tryAcquire(now: Long): Boolean {
        prune(now)
        if (times.size >= max) return false
        times.addLast(now)
        return true
    }

    fun canRetry(now: Long): Boolean {
        prune(now)
        return times.size < max
    }

    fun reset() = times.clear()

    private fun prune(now: Long) {
        while (times.isNotEmpty() && now - times.first() >= windowMillis) times.removeFirst()
    }
}

/**
 * 내비 목적지 주행가능거리 재비교 중복 방지 (#004 C).
 * 출발 브리핑·onRouteStarted·앱이 앞에 올 때(onStart) 세 경로가 같은 목적지를 비교한다.
 * 같은 목적지·같은 결과(부족/충분)면 한 번만 말한다.
 */
class RouteRecheck {
    private var last: String? = null

    /** 비교 결과를 기록하고, 음성으로 말해야 하면 true (부족하고 아직 같은 결과를 말하지 않았을 때). */
    fun shouldSpeak(destination: String, short: Boolean): Boolean {
        val key = "$destination|$short"
        if (key == last) return false
        last = key
        return short
    }

    /** 이미 같은 목적지·결과를 비교했는지 (onStart 재조회에서 다시 계산할 필요가 없는지). */
    fun seen(destination: String): Boolean = last?.substringBeforeLast('|') == destination

    /** 시동을 새로 켜거나 끄면 처음부터. */
    fun reset() {
        last = null
    }
}

/**
 * 마지막 운행 시각 기록 (#004 E1). 주행 중 화면 제한(CarUxRestrictions)이 켜지고 꺼지는 순간에만 기록한다.
 * 시동 신호가 없는 환경에서도 "마지막 운행일"이 남아 장기 미운행 안내가 동작한다.
 */
class LastDriveTracker {
    private var driving: Boolean? = null

    /** @return 지금 시각을 마지막 운행으로 기록해야 하면 true (주행 시작·주행 끝). */
    fun onDriving(now: Boolean): Boolean {
        val prev = driving
        driving = now
        return if (prev == null) now else prev != now
    }
}
