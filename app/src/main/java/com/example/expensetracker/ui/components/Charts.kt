package com.example.expensetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Both charts are drawn with Compose primitives: no chart library, nothing to download.

data class DonutSlice(val value: Float, val color: Color)

/**
 * Share-of-total ring. Slices are separated by a small gap so neighbours never rely on colour
 * alone to be told apart; [center] is for the headline number. Names and values belong in a
 * legend next to the chart, not on the ring.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    description: String,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    center: @Composable () -> Unit = {},
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description },
        ) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val total = slices.sumOf { it.value.toDouble() }.toFloat()

            if (total <= 0f) {
                drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gapDegrees = if (slices.size > 1) 2f else 0f
            var startAngle = -90f // 12 o'clock
            for (slice in slices) {
                val sweep = slice.value / total * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle + gapDegrees / 2f,
                    sweepAngle = (sweep - gapDegrees).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke),
                )
                startAngle += sweep
            }
        }
        center()
    }
}

data class MonthBar(
    val label: String,
    val valueLabel: String,
    val value: Long,
    val selected: Boolean,
)

/**
 * Month-by-month totals. One series, one colour (the selected month is emphasised, the rest
 * recede), bars grow from a shared zero baseline, and each bar is a real button so TalkBack
 * users get "October, ₹12,400" and can select it.
 */
@Composable
fun MonthlyBarChart(
    bars: List<MonthBar>,
    onBarClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 180.dp,
) {
    val max = bars.maxOfOrNull { it.value } ?: 0L
    val selectedColor = MaterialTheme.colorScheme.primary
    val restColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bars.forEachIndexed { index, bar ->
            val target = if (max == 0L) 0f else bar.value.toFloat() / max
            val fraction by animateFloatAsState(targetValue = target, label = "barHeight")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = { onBarClick(index) })
                    .semantics {
                        contentDescription = "${bar.label}, ${bar.valueLabel}"
                        selected = bar.selected
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Only the selected bar carries a value label; a number on every bar is noise.
                Text(
                    text = if (bar.selected) bar.valueLabel else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics { }, // spoken via the bar's description
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight(fraction.coerceIn(0.01f, 1f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (bar.selected) selectedColor else restColor),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}
