package com.chageunchageun.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chageunchageun.app.BuildConfig
import com.chageunchageun.app.engine.Finding
import com.chageunchageun.app.engine.NextService
import com.chageunchageun.app.engine.Severity
import com.chageunchageun.app.engine.SuggestedAction
import com.chageunchageun.app.engine.thousands
import com.chageunchageun.app.ui.theme.OkColor
import com.chageunchageun.app.ui.theme.severityColor
import com.chageunchageun.app.ui.theme.severityContainer
import kotlin.math.roundToInt

// ---------------- 홈 (#004 B: 요약 줄 + 2단, B+: 큰 글씨·남는 높이만큼 카드) ----------------

/**
 * 홈 화면 글씨 (#004 B+ 1 → B++: 실제 화면에서 Pleos 시스템 UI보다 작아 약 1.35배). 다른 탭은 기존 크기 그대로.
 */
object HomeText {
    val title = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 24.sp, lineHeight = 34.sp)
    val badge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
    val label = TextStyle(fontSize = 22.sp, lineHeight = 30.sp)
    val section = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val summaryTitle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    val summarySub = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
    val button = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
    val voice = TextStyle(fontSize = 24.sp, lineHeight = 36.sp)
    val driving = TextStyle(fontSize = 40.sp, lineHeight = 56.sp)
    val drivingButton = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    val empty = TextStyle(fontSize = 28.sp, lineHeight = 40.sp)
    val tab = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val chip = TextStyle(fontSize = 20.sp, lineHeight = 26.sp)
    /** 등화 점검 등 이름 (#004 B+++) */
    val lampName = TextStyle(fontSize = 44.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
    /** 등화 점검 결과 목록 (#004 B++++) */
    val lampResult = TextStyle(fontSize = 26.sp, lineHeight = 34.sp)
}

/** 카드 사이 간격 (홈, #004 B+++ 16→12dp). */
private val CARD_GAP: Dp = 12.dp

/** 홈 실행 버튼 높이 (#004 B+ 56dp → B++ 64dp → B+++ 56dp: 왼쪽 3장이 들어가게). */
internal val HOME_BUTTON: Dp = 56.dp

/** 주행 중 버튼·시동 꺼짐 화면 "시동 켜기"·등화 점검 답 버튼 높이 (#004 B++). */
internal val BIG_BUTTON: Dp = 112.dp

/** 지금 홈에 보여 줄 점검 결과: 시동 중·전이면 출발 브리핑, 시동을 끈 뒤면 하차 점검. */
internal val UiState.homeFindings: List<Finding>
    get() = if (exitMode) exitFindings else briefing

internal val UiState.exitMode: Boolean get() = !ignitionOn && exitDone

/** 아직 점검 결과가 없는 상태(앱을 막 열었거나 시나리오를 바꾼 직후). */
internal val UiState.notChecked: Boolean get() = homeFindings.isEmpty() && lastSpeech.isBlank()

@Composable
internal fun ColumnScope.HomeScreen(s: UiState, cb: UiCallbacks) {
    SummaryBar(s)
    Spacer(Modifier.height(12.dp))

    if (s.isDriving) {
        DrivingHome(s, cb)
        return
    }

    if (s.notChecked && !s.ignitionOn) {
        IgnitionOffHome(s, cb)
        return
    }

    val sections = HomeLayout.split(s.homeFindings)
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        if (maxWidth >= 900.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.15f).fillMaxHeight()) { ActionColumn(s, sections, cb) }
                Column(Modifier.weight(1f).fillMaxHeight()) { GuideColumn(s, sections, cb) }
            }
        } else {
            // 좁은 화면(일반 태블릿·휴대폰 에뮬레이터): 한 줄로 이어서 스크롤
            val guide = guideCards(s, sections, cb)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(CARD_GAP), modifier = Modifier.fillMaxSize()) {
                item { ActionHeader(s, sections.action) }
                items(sections.action, key = { it.id }) { FindingCard(it, cb.onAction, s.busyAction, big = true) }
                if (s.okItems.isNotEmpty()) item { OkBar(s.okItems) }
                item { SectionHeader("음성 브리핑 · 안내 · 다가오는 점검") }
                items(guide) { it() }
            }
        }
    }
}

/** 주행 중: 요약 줄 + 음성 브리핑(큰 글씨) + 큰 버튼(최대 3개)만. 화면에서 읽어야 하는 카드는 숨긴다(#004 B+ 3). */
@Composable
private fun ColumnScope.DrivingHome(s: UiState, cb: UiCallbacks) {
    val actions = s.homeFindings.flatMap { it.actions }
        .filter { it is SuggestedAction.Navigate || it is SuggestedAction.AddWaypoint || it == SuggestedAction.WinterPreset }
        .distinct()
        .take(3)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        Column(Modifier.padding(32.dp)) {
            Text("음성 브리핑", style = HomeText.title, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                s.lastSpeech.ifBlank { "시동을 켜면 오늘 일정과 차량 상태를 먼저 알려 드려요." },
                style = HomeText.driving,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (actions.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { action ->
                Button(
                    onClick = { cb.onAction(action) },
                    enabled = s.busyAction == null || action !is SuggestedAction.AddWaypoint,
                    modifier = Modifier.weight(1f).height(BIG_BUTTON),
                ) {
                    Text(if (s.busyAction == action) "찾는 중…" else action.label, style = HomeText.drivingButton)
                }
            }
        }
    }
}

/** 시동 꺼짐·아직 점검 전: 가운데 안내 문장 + 큰 "시동 켜기"(데모만, 연동 버전은 시동 신호를 기다림) (#004 B++). */
@Composable
private fun ColumnScope.IgnitionOffHome(s: UiState, cb: UiCallbacks) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
        Text(
            "시동을 켜면 오늘 일정과 차량 상태를 먼저 알려 드려요.",
            style = HomeText.empty,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (s.isDemo) {
            Spacer(Modifier.height(40.dp))
            Button(onClick = cb.onIgnitionOn, modifier = Modifier.width(480.dp).height(BIG_BUTTON)) {
                Text("시동 켜기", style = HomeText.drivingButton)
            }
        }
        if (s.okItems.isNotEmpty()) {
            Spacer(Modifier.height(40.dp))
            Box(Modifier.width(720.dp)) { OkBar(s.okItems) }
        }
    }
}

/** 상단 요약 줄: 조치 필요 건수 · 시동 상태 · 다음 일정 · 누적 주행거리 · 바깥 기온. */
@Composable
private fun SummaryBar(s: UiState) {
    val findings = s.homeFindings
    val top = HomeLayout.topSeverity(findings)
    val summary = if (s.notChecked) "시동을 켜면 점검해요" else HomeLayout.summary(findings)
    val accent = top?.let(::severityColor) ?: OkColor
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Icon(
                if (top == null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (s.notChecked) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                modifier = Modifier.size(44.dp),
            )
            Column {
                Text(
                    if (s.exitMode) "내리기 전 확인 요약" else "차량 상태 요약",
                    style = HomeText.summaryTitle,
                )
                Text(
                    summary,
                    style = HomeText.summarySub,
                    color = if (s.notChecked) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                )
            }
            IgnitionChip(s.ignitionOn)
            // #005 H3: 데모 모드면 항상 배지(실제 차량 값과 혼동 방지)
            if (s.isDemo) DemoBadge(if (s.liveUnavailable) "차량 연결 안 됨 · 데모 모드" else "데모")
            Box(Modifier.weight(1f)) {
                s.nextTripLine?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DateRange, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Text("  다음 일정  $it", style = HomeText.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // #004 B+++: 0km(에뮬레이터 기본값)면 칸을 숨긴다.
            s.odometerKm?.takeIf { it >= 1.0 }?.let { km ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("누적 주행거리  ", style = HomeText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${thousands(km.roundToInt())} km", style = HomeText.badge)
                }
            }
            s.outsideTempC?.let { t ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Text(
                        "바깥 ${t.roundToInt()}°C",
                        style = HomeText.label.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(
            text,
            style = HomeText.label.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun IgnitionChip(on: Boolean) {
    val color = if (on) OkColor else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Text(if (on) "  시동 켜짐" else "  시동 꺼짐", style = HomeText.label, color = color)
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(title, style = HomeText.section)
        Spacer(Modifier.weight(1f))
        trailing?.let { Text(it, style = HomeText.label, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ActionHeader(s: UiState, action: List<Finding>) =
    SectionHeader(if (s.exitMode) "내리기 전 확인할 것" else "지금 조치할 것", if (action.isEmpty()) null else "${action.size}건")

/**
 * 왼쪽: 지금 조치할 것. 남는 높이에 들어가는 만큼(최소 3개) 보여 주고 넘칠 때만 "N건 더 보기".
 * "확인한 항목 이상 없음" 바는 마지막 카드 바로 아래 (#004 B+ 1).
 */
@Composable
private fun ColumnScope.ActionColumn(s: UiState, sections: HomeSections, cb: UiCallbacks) {
    val action = sections.action
    ActionHeader(s, action)
    if (action.isEmpty()) {
        Text(
            if (s.notChecked) "시동을 켜면 오늘 일정과 차량 상태를 먼저 알려 드려요." else "지금 조치할 항목은 없어요.",
            style = HomeText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        if (s.okItems.isNotEmpty()) OkBar(s.okItems)
        return
    }
    var expanded by remember(action.map { it.id }) { mutableStateOf(false) }
    val footer: (@Composable () -> Unit)? = if (s.okItems.isNotEmpty()) ({ OkBar(s.okItems) }) else null
    if (expanded) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(CARD_GAP), modifier = Modifier.weight(1f)) {
            items(action, key = { it.id }) { FindingCard(it, cb.onAction, s.busyAction, big = true) }
            item { MoreButton("접기") { expanded = false } }
            footer?.let { f -> item { f() } }
        }
    } else {
        FitColumn(
            count = action.size,
            minVisible = HomeLayout.MIN_ACTION_VISIBLE,
            // #004 B+++ S6·S9 크래시: 카드 수가 줄면 이전 칸(item-i)이 새 목록으로 먼저 다시 그려져 범위를 넘었다 → 없는 칸은 비워 둔다.
            item = { i -> action.getOrNull(i)?.let { FindingCard(it, cb.onAction, s.busyAction, big = true) } },
            more = { hidden -> MoreButton("${hidden}건 더 보기") { expanded = true } },
            footer = footer,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/** 오른쪽: 음성 브리핑 · 안내 · 다가오는 점검. 왼쪽과 같이 남는 높이만큼. */
@Composable
private fun ColumnScope.GuideColumn(s: UiState, sections: HomeSections, cb: UiCallbacks) {
    SectionHeader("음성 브리핑 · 안내 · 다가오는 점검")
    val cards = guideCards(s, sections, cb)
    var expanded by remember(cards.size, s.lastSpeech) { mutableStateOf(false) }
    if (expanded) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(CARD_GAP), modifier = Modifier.weight(1f)) {
            items(cards) { it() }
            item { MoreButton("접기") { expanded = false } }
        }
    } else {
        FitColumn(
            count = cards.size,
            minVisible = 1,
            item = { i -> cards.getOrNull(i)?.invoke() },  // #004 B+++ S6 크래시 (위와 같음)
            more = { hidden -> MoreButton("안내 ${hidden}건 더 보기") { expanded = true } },
            footer = null,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/** 오른쪽 카드 목록: 음성 브리핑 → 안내 → 다가오는 점검(시기 임박 정기점검, 없으면 다음 정기점검 한 장). */
private fun guideCards(s: UiState, sections: HomeSections, cb: UiCallbacks): List<@Composable () -> Unit> = buildList {
    if (s.lastSpeech.isNotBlank()) add { VoiceCard(s) }
    sections.guide.forEach { f -> add { FindingCard(f, cb.onAction, s.busyAction, big = true) } }
    if (sections.upcoming.isNotEmpty()) {
        sections.upcoming.forEach { f -> add { FindingCard(f, cb.onAction, s.busyAction, big = true) } }
    } else if (!s.exitMode) {
        s.nextService?.let { next -> add { NextServiceCard(next, cb) } }
    }
}

/**
 * 카드를 위에서부터 남는 높이에 들어가는 만큼 놓는다. 넘치면 [more] 버튼, 마지막에 [footer].
 * 몇 개를 놓을지는 [HomeLayout.fitCount] (단위 테스트 대상).
 */
@Composable
private fun FitColumn(
    count: Int,
    minVisible: Int,
    item: @Composable (Int) -> Unit,
    more: @Composable (hidden: Int) -> Unit,
    footer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier.clipToBounds()) { c ->
        val width = Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth)
        val gap = CARD_GAP.roundToPx()
        val items = (0 until count).map { i -> subcompose("item-$i") { item(i) }.map { it.measure(width) } }
        val footerP = footer?.let { f -> subcompose("footer") { f() }.map { it.measure(width) } }
        val moreH = subcompose("more-probe") { more(count) }.sumOf { it.measure(width).height }
        val shown = HomeLayout.fitCount(
            heights = items.map { ps -> ps.sumOf { it.height } },
            gap = gap,
            available = c.maxHeight,
            footer = footerP?.sumOf { it.height },
            more = moreH,
            minVisible = minVisible,
        )
        val moreP = if (shown < count) subcompose("more") { more(count - shown) }.map { it.measure(width) } else null
        val blocks = items.take(shown) + listOfNotNull(moreP, footerP)
        layout(c.maxWidth, c.maxHeight) {
            var y = 0
            blocks.forEachIndexed { i, ps ->
                if (i > 0) y += gap
                ps.forEach { p ->
                    p.placeRelative(0, y)
                    y += p.height
                }
            }
        }
    }
}

@Composable
private fun MoreButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(HOME_BUTTON)) {
        Text(text, style = HomeText.button)
    }
}

/** "확인한 항목 이상 없음 (N개)" — 접힌 한 줄, 누르면 확인한 항목 목록. 데이터로 확인한 범위만 말한다. */
@Composable
private fun OkBar(items: List<String>) {
    var expanded by remember(items) { mutableStateOf(false) }
    Surface(
        color = OkColor.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, OkColor.copy(alpha = 0.5f)),
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = OkColor, modifier = Modifier.size(36.dp))
                Text("  확인한 항목 이상 없음 (${items.size}개)", style = HomeText.label, modifier = Modifier.weight(1f))
                Text(if (expanded) "접기" else "펼치기", style = HomeText.label, color = OkColor)
            }
            if (expanded) {
                Text(
                    items.joinToString("  ·  "),
                    style = HomeText.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NextServiceCard(next: NextService, cb: UiCallbacks) {
    PanelCard(padding = 24.dp, verticalPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text("  다음 정기점검", style = HomeText.label, modifier = Modifier.weight(1f))
            next.dDay?.let { Text(it, style = HomeText.badge, color = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(next.name, style = HomeText.title, modifier = Modifier.weight(1f))
            next.kmText?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("남은 거리", style = HomeText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it, style = HomeText.badge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ActionButtons(listOf(SuggestedAction.OpenChecklist), cb.onAction, null, Severity.INFO, big = true)
    }
}

/** 음성 안내 카드: 기본 5줄, 펼치기로 전체. 음성 엔진이 없으면 제목 옆에 표시. */
@Composable
private fun VoiceCard(s: UiState) {
    var expanded by remember(s.lastSpeech) { mutableStateOf(false) }
    var overflow by remember(s.lastSpeech) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
                Text("음성 브리핑", style = HomeText.label.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                if (s.voiceAvailable == false) {
                    Text("  음성 엔진 없음 · 화면 안내", style = HomeText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // debug 빌드: 실제로 말하고 듣는지 눈으로도 확인 (#003 B6). 녹화(demo_clean) 때는 숨긴다.
                if (BuildConfig.DEBUG && s.voiceActivity != null && !s.demoClean) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(
                            s.voiceActivity,
                            style = HomeText.label,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (overflow || expanded) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.height(56.dp)) {
                        Text(if (expanded) "접기" else "펼치기", style = HomeText.label)
                    }
                }
            }
            Text(
                s.lastSpeech,
                style = HomeText.voice,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
            )
        }
    }
}

// ---------------- 공통 카드 (홈·점검표·등화 점검·이력) ----------------

/** 모든 탭이 같은 모양을 쓰도록: 둥근 모서리 + 얇은 테두리. */
@Composable
fun PanelCard(
    container: Color = MaterialTheme.colorScheme.surface,
    border: Color = MaterialTheme.colorScheme.surfaceVariant,
    padding: Dp = 20.dp,
    borderWidth: Dp = 1.dp,
    verticalPadding: Dp = padding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(borderWidth, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = padding, vertical = verticalPadding), content = content)
    }
}

private fun severityIcon(severity: Severity): ImageVector = when (severity) {
    Severity.URGENT, Severity.WARN -> Icons.Filled.Warning
    Severity.ADVISE, Severity.INFO -> Icons.Filled.Info
}

/**
 * 점검 결과 카드: 심각도별 테두리·배경·아이콘, 오른쪽 위 핵심 숫자, 카드 폭 실행 버튼.
 * @param big 홈 화면용 큰 글씨(제목 30sp·본문 24sp·핵심 숫자 34sp·버튼 64dp, #004 B++). 다른 탭은 기존 크기.
 */
@Composable
fun FindingCard(finding: Finding, onAction: (SuggestedAction) -> Unit, busy: SuggestedAction? = null, big: Boolean = false) {
    val color = severityColor(finding.severity)
    val strong = finding.severity.rank >= Severity.WARN.rank
    val labelStyle = if (big) HomeText.label else MaterialTheme.typography.titleMedium
    PanelCard(
        container = severityContainer(finding.severity),
        border = if (strong) color else color.copy(alpha = 0.35f),
        padding = if (big) 24.dp else 20.dp,
        borderWidth = if (strong) 2.dp else 1.dp,
        // #004 B+++: 홈 카드 위아래 여백 24→16dp (왼쪽 3장이 들어가게)
        verticalPadding = if (big) 16.dp else 20.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color.copy(alpha = 0.18f), shape = MaterialTheme.shapes.small) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Icon(severityIcon(finding.severity), null, tint = color, modifier = Modifier.size(if (big) 30.dp else 20.dp))
                    Text(" ${finding.severity.label}", style = labelStyle.copy(fontWeight = FontWeight.SemiBold), color = color)
                }
            }
            Text(
                "  ${finding.kind.label}",
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            finding.badge?.let {
                Text(it, style = if (big) HomeText.badge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            }
        }
        Spacer(Modifier.height(if (big) 6.dp else 8.dp))
        Text(finding.title, style = if (big) HomeText.title else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(finding.speech, style = if (big) HomeText.body else MaterialTheme.typography.bodyLarge)
        if (finding.actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ActionButtons(finding.actions, onAction, busy, finding.severity, big)
        }
    }
}

/** 카드 전체 폭 버튼. 여러 개면 나란히 같은 폭. 첫 버튼은 심각도 색(주의·위험). */
@Composable
private fun ActionButtons(
    actions: List<SuggestedAction>,
    onAction: (SuggestedAction) -> Unit,
    busy: SuggestedAction?,
    severity: Severity,
    big: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        actions.forEachIndexed { i, action ->
            ActionButton(action, onAction, busy, primary = i == 0, severity = severity, big = big)
        }
    }
}

@Composable
private fun RowScope.ActionButton(
    action: SuggestedAction,
    onAction: (SuggestedAction) -> Unit,
    busy: SuggestedAction?,
    primary: Boolean,
    severity: Severity,
    big: Boolean,
) {
    // 경유지 검색 중에는 다른 경유 버튼도 막는다(검색이 쌓이지 않게).
    val enabled = busy == null || action !is SuggestedAction.AddWaypoint
    val label = if (busy == action) "찾는 중…" else action.label
    val modifier = Modifier.weight(1f).height(if (big) HOME_BUTTON else 52.dp)
    val style = if (big) HomeText.button else MaterialTheme.typography.titleMedium
    if (primary) {
        val strong = severity.rank >= Severity.WARN.rank
        Button(
            onClick = { onAction(action) },
            enabled = enabled,
            colors = if (strong) {
                ButtonDefaults.buttonColors(containerColor = severityColor(severity), contentColor = Color(0xFF14181C))
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = modifier,
        ) { Text(label, style = style) }
    } else {
        OutlinedButton(onClick = { onAction(action) }, enabled = enabled, modifier = modifier) {
            Text(label, style = style)
        }
    }
}
