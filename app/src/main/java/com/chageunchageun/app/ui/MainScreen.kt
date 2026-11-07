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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chageunchageun.app.BuildConfig
import com.chageunchageun.app.data.DemoScenario
import com.chageunchageun.app.engine.Finding
import com.chageunchageun.app.engine.ItemMode
import com.chageunchageun.app.engine.LampAnswer
import com.chageunchageun.app.engine.NextService
import com.chageunchageun.app.engine.Severity
import com.chageunchageun.app.engine.SuggestedAction
import com.chageunchageun.app.engine.thousands
import com.chageunchageun.app.model.Lamp
import com.chageunchageun.app.ui.theme.OkColor
import com.chageunchageun.app.ui.theme.severityColor
import com.chageunchageun.app.ui.theme.severityContainer
import kotlin.math.roundToInt

/** 화면에서 ViewModel 로 보내는 동작. 미리보기(@Preview)에서는 빈 동작을 넣는다. */
class UiCallbacks(
    val onAction: (SuggestedAction) -> Unit = {},
    val onScenario: (DemoScenario) -> Unit = {},
    val onIgnitionOn: () -> Unit = {},
    val onIgnitionOff: () -> Unit = {},
    val onTab: (Screen) -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRecordMaintenance: (String) -> Unit = {},
    val onAnswerChecklist: (String, Boolean) -> Unit = { _, _ -> },
    val onCompleteChecklist: () -> Unit = {},
    val onStartLampCheck: () -> Unit = {},
    val onAnswerLamp: (LampAnswer) -> Unit = {},
    /** 데모 모드 켜기·끄기 (#005 H3) */
    val onDemo: (Boolean) -> Unit = {},
)

@Composable
fun MainScreen(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    val cb = remember(vm) {
        UiCallbacks(
            onAction = vm::runAction,
            onScenario = vm::selectScenario,
            onIgnitionOn = vm::ignitionOn,
            onIgnitionOff = vm::ignitionOff,
            onTab = vm::goTo,
            onDismiss = vm::dismissMessage,
            onRetry = vm::retryWaypoint,
            onRecordMaintenance = vm::recordMaintenance,
            onAnswerChecklist = vm::answerChecklist,
            onCompleteChecklist = vm::completeChecklist,
            onStartLampCheck = vm::startLampCheck,
            onAnswerLamp = vm::answerLamp,
            onDemo = vm::setDemoMode,
        )
    }
    MainContent(s, cb)
}

@Composable
fun MainContent(s: UiState, cb: UiCallbacks) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 주행 중: 탭을 흐리게 하고 한 줄 안내 (#004 B+ 3)
            Column {
                if (s.isDriving) {
                    Text(
                        "주행 중에는 음성으로 안내해요",
                        style = HomeText.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = if (s.isDriving) Modifier.alpha(0.4f) else Modifier,
            ) {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = s.screen == screen,
                        onClick = { cb.onTab(screen) },
                        // 주행 중에는 홈(음성 안내)만 쓴다.
                        enabled = !s.isDriving || screen == Screen.HOME,
                        icon = {},
                        label = { Text(screen.label, style = HomeText.tab) },
                    )
                }
            }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("차근차근", style = HomeText.section)
                // #005 H3: 데모 칩 — 주행 중에는 숨김, 연동 모드에서는 주차 중에만
                if (!s.isDriving && (s.isDemo || s.isParked)) DemoToggle(s, cb)
                if (s.isDemo && !s.isDriving && s.screen == Screen.HOME) ScenarioChips(s, cb, Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                // 시동 버튼: 데모 모드, 또는 시동 신호를 받을 수 없는 환경(에뮬레이터)에서만 (DECISIONS #111)
                if (s.screen == Screen.HOME && (s.isDemo || s.manualIgnition)) IgnitionButtons(s, cb)
            }
            Spacer(Modifier.height(8.dp))
            s.message?.let { MessageBanner(it, s, cb) }
            when (s.screen) {
                Screen.HOME -> HomeScreen(s, cb)
                Screen.CHECKLIST -> ChecklistScreen(s, cb)
                Screen.LAMP -> LampScreen(s, cb)
                Screen.HISTORY -> HistoryScreen(s)
            }
        }
    }
}

/** 안내 메시지. 장소 검색이 실패했으면 "다시 찾기" 버튼을 붙인다(#004 D). */
@Composable
private fun MessageBanner(text: String, s: UiState, cb: UiCallbacks) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        // #004 B+++: 안내 줄도 홈 글씨 비율
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = HomeText.label, modifier = Modifier.weight(1f))
            if (s.retryAction != null) {
                Button(onClick = cb.onRetry, enabled = s.busyAction == null, modifier = Modifier.height(HOME_BUTTON)) {
                    Text("다시 찾기", style = HomeText.button)
                }
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = cb.onDismiss, modifier = Modifier.height(HOME_BUTTON)) {
                Text("닫기", style = HomeText.button)
            }
        }
    }
}

/** "데모" 토글 칩. 켤 때만 확인 대화상자 1번 (#005 H3). */
@Composable
private fun DemoToggle(s: UiState, cb: UiCallbacks) {
    var confirm by remember { mutableStateOf(false) }
    FilterChip(
        selected = s.isDemo,
        onClick = { if (s.isDemo) cb.onDemo(false) else confirm = true },
        label = { Text("데모", style = HomeText.chip) },
        modifier = Modifier.padding(start = 12.dp).height(48.dp),
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("데모 모드", style = HomeText.title) },
            text = { Text("데모 모드에서는 목업 데이터로 상황을 재현합니다. 실제 차량은 제어하지 않아요.", style = HomeText.body) },
            confirmButton = {
                Button(onClick = { confirm = false; cb.onDemo(true) }, modifier = Modifier.height(HOME_BUTTON)) {
                    Text("켜기", style = HomeText.button)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.height(HOME_BUTTON)) {
                    Text("취소", style = HomeText.button)
                }
            },
        )
    }
}

@Composable
private fun ScenarioChips(s: UiState, cb: UiCallbacks, modifier: Modifier) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        item {
            Text(
                "데모 상황",
                style = HomeText.chip,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        items(DemoScenario.entries) { scenario ->
            FilterChip(
                selected = s.scenario == scenario,
                onClick = { cb.onScenario(scenario) },
                label = { Text(scenario.label, style = HomeText.chip) },
                modifier = Modifier.height(48.dp),
            )
        }
    }
}

@Composable
private fun IgnitionButtons(s: UiState, cb: UiCallbacks) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = cb.onIgnitionOn, enabled = !s.ignitionOn, modifier = Modifier.height(HOME_BUTTON)) {
            Text("시동 켜기", style = HomeText.button)
        }
        OutlinedButton(onClick = cb.onIgnitionOff, enabled = s.ignitionOn, modifier = Modifier.height(HOME_BUTTON)) {
            Text("시동 끄기", style = HomeText.button)
        }
    }
}

// 홈 화면·공통 카드는 HomeScreen.kt (#004 B+)

// ---------------- 점검표: 정기점검 + 계절점검 ----------------

@Composable
private fun ColumnScope.ChecklistScreen(s: UiState, cb: UiCallbacks) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
        item { SectionTitle("정기점검", s.profileName) }
        items(s.periodic, key = { it.id }) { FindingCard(it, cb.onAction, s.busyAction, big = true) }
        items(s.maintenanceItems, key = { "item-${it.id}" }) { item ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = HomeText.title)
                        Text(
                            listOfNotNull(
                                item.intervalKm?.let { "${thousands(it)}km" },
                                item.intervalMonths?.let { "${it}개월" },
                            ).joinToString(" 또는 ") + "마다",
                            style = HomeText.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { cb.onRecordMaintenance(item.id) }, modifier = Modifier.height(HOME_BUTTON)) {
                        Text("오늘 했어요", style = HomeText.label)
                    }
                }
            }
        }

        item { SectionTitle("${s.phase.label} 점검표", "자동 확인 항목은 차량 데이터로, 나머지는 눈으로 확인해 주세요.") }
        items(s.checklist, key = { "check-${it.id}" }) { item ->
            PanelCard {
                Text("${item.name} · ${item.mode.label}", style = HomeText.title)
                Text(item.how, style = HomeText.body)
                Spacer(Modifier.height(8.dp))
                if (item.mode == ItemMode.AUTO) {
                    Text(
                        when (item.autoResult) {
                            true -> "이상 없음"
                            false -> "손볼 필요"
                            null -> "확인 불가"
                        },
                        style = HomeText.label,
                        color = if (item.autoResult == false) severityColor(Severity.WARN) else OkColor,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = s.checklistAnswers[item.id] == true,
                            onClick = { cb.onAnswerChecklist(item.id, true) },
                            label = { Text("확인했어요", style = HomeText.label) },
                            modifier = Modifier.height(HOME_BUTTON),
                        )
                        FilterChip(
                            selected = s.checklistAnswers[item.id] == false,
                            onClick = { cb.onAnswerChecklist(item.id, false) },
                            label = { Text("손볼 필요", style = HomeText.label) },
                            modifier = Modifier.height(HOME_BUTTON),
                        )
                    }
                }
            }
        }
        item {
            if (!s.isParked) {
                Text(
                    "주차 상태(주차 브레이크 또는 P단)에서만 진행할 수 있어요.",
                    style = HomeText.body,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = cb.onCompleteChecklist,
                enabled = s.isParked,
                modifier = Modifier.fillMaxWidth().height(HOME_BUTTON),
            ) { Text("점검 완료 기록", style = HomeText.label) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = HomeText.section)
        Text(subtitle, style = HomeText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- 등화 점검 ----------------

@Composable
private fun ColumnScope.LampScreen(s: UiState, cb: UiCallbacks) {
    val step = s.lampStep
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            // #004 B+++: 시연 핵심 화면 — 등 이름 44sp, 안내 28sp, 답 버튼 112dp·32sp, 진행(3 / 9)
            step != null -> PanelCard(padding = 32.dp) {
                Text("등화 점검  ${s.lampProgress}", style = HomeText.title, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(step.lamp.label, style = HomeText.lampName)
                Spacer(Modifier.height(12.dp))
                Text(step.instruction, style = HomeText.empty)
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { cb.onAnswerLamp(LampAnswer.OK) }, modifier = Modifier.weight(1f).height(BIG_BUTTON)) {
                        Text("켜졌어요", style = HomeText.drivingButton)
                    }
                    OutlinedButton(onClick = { cb.onAnswerLamp(LampAnswer.FAIL) }, modifier = Modifier.weight(1f).height(BIG_BUTTON)) {
                        Text("안 켜졌어요", style = HomeText.drivingButton)
                    }
                    TextButton(onClick = { cb.onAnswerLamp(LampAnswer.SKIPPED) }, modifier = Modifier.height(BIG_BUTTON)) {
                        Text("건너뛰기", style = HomeText.button)
                    }
                }
            }
            s.lampSummary != null -> {
                FindingCard(s.lampSummary, cb.onAction, s.busyAction, big = true)
                if (s.lampResults.isNotEmpty()) LampResultGrid(s.lampResults)
                OutlinedButton(onClick = cb.onStartLampCheck, modifier = Modifier.width(320.dp).height(HOME_BUTTON)) {
                    Text("다시 점검", style = HomeText.button)
                }
            }
            else -> PanelCard {
                Text("등화 점검", style = HomeText.section)
                Spacer(Modifier.height(8.dp))
                Text(
                    "자동차 검사에서 가장 많이 떨어지는 항목이 등화장치예요. 한 달에 한 번, 또는 검사 한 달 전에 " +
                        "주차한 상태에서 벽이나 유리창 앞에 세우고 진행해 주세요. 등을 하나씩 켜면서 확인해요.",
                    style = HomeText.body,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = cb.onStartLampCheck, modifier = Modifier.width(480.dp).height(BIG_BUTTON)) {
                    Text("등화 점검 시작", style = HomeText.drivingButton)
                }
            }
        }
    }
}

/** 등화 점검 결과 목록 (#004 B++++): 3열, 등 이름 + ✓ 켜짐(초록) / ✕ 안 켜짐(주황) / – 건너뜀. 안 켜진 등이 맨 앞. */
@Composable
private fun LampResultGrid(results: List<Pair<Lamp, LampAnswer>>) {
    PanelCard(padding = 24.dp, verticalPadding = 12.dp) {
        results.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (lamp, answer) ->
                    val (mark, color) = when (answer) {
                        LampAnswer.OK -> "✓" to OkColor
                        LampAnswer.FAIL -> "✕" to severityColor(Severity.WARN)
                        LampAnswer.SKIPPED -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).height(64.dp)) {
                        Text(mark, style = HomeText.lampResult.copy(fontWeight = FontWeight.Bold), color = color)
                        Text(
                            "  ${lamp.label}",
                            style = HomeText.lampResult,
                            color = if (answer == LampAnswer.FAIL) color else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------- 이력 ----------------

@Composable
private fun ColumnScope.HistoryScreen(s: UiState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
        item { SectionTitle("점검 이력", "차량 안에만 저장돼요. 가족 공유는 동의한 경우에만.") }
        item {
            PanelCard {
                s.historyLines.forEach { line ->
                    Text(line, style = HomeText.body, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}
