package com.chageunchageun.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chageunchageun.app.engine.Severity

/** 차량 화면은 어두운 테마 + 큰 글씨 + 큰 터치 영역을 기본으로 한다. */
private val CarColors = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    onPrimary = Color(0xFF0B1A2E),
    background = Color(0xFF0E1216),
    surface = Color(0xFF161B21),
    surfaceVariant = Color(0xFF232A32),
    onBackground = Color(0xFFE6E9EC),
    onSurface = Color(0xFFE6E9EC),
    onSurfaceVariant = Color(0xFFAAB4BE),
)

/** 차량 화면 글씨: 작은 글자 스타일도 16sp 이상(본문·칩·버튼 라벨). */
private val CarTypography = Typography().let { t ->
    t.copy(
        bodyMedium = t.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodySmall = t.bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = t.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
        labelLarge = t.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        labelMedium = t.labelMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
        labelSmall = t.labelSmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
    )
}

/** 모든 탭 공통 카드 모양 (#004 B). */
private val CarShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** "이상 없음"·시동 켜짐 표시 색. */
val OkColor = Color(0xFF4CC38A)

fun severityColor(severity: Severity): Color = when (severity) {
    Severity.URGENT -> Color(0xFFF2726B)
    Severity.WARN -> Color(0xFFF0A93B)
    Severity.ADVISE -> Color(0xFF6CA8F0)
    Severity.INFO -> Color(0xFF8B96A1)
}

/** 카드 배경: 심각도 색을 어둡게 깐다(위험·주의만 눈에 띄게). */
fun severityContainer(severity: Severity): Color = when (severity) {
    Severity.URGENT -> Color(0xFF2E1719)
    Severity.WARN -> Color(0xFF2A2216)
    Severity.ADVISE -> Color(0xFF17202B)
    Severity.INFO -> Color(0xFF161B21)
}

@Composable
fun ChageunTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CarColors, typography = CarTypography, shapes = CarShapes, content = content)
}
