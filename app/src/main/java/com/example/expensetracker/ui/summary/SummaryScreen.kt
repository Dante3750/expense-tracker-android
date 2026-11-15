package com.example.expensetracker.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensetracker.ui.components.DonutChart
import com.example.expensetracker.ui.components.DonutSlice
import com.example.expensetracker.ui.components.MonthBar
import com.example.expensetracker.ui.components.MonthSwitcher
import com.example.expensetracker.ui.components.MonthlyBarChart
import com.example.expensetracker.ui.theme.categoryColor
import com.example.expensetracker.util.Money
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val barLabelFormatter = DateTimeFormatter.ofPattern("MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel,
    bottomBar: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Summary") }) },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MonthSwitcher(
                month = state.month,
                canGoNext = state.canGoNext,
                onPrevious = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
            )
            CategoryBreakdownCard(state)
            TrendCard(state, onSelectMonth = viewModel::selectMonth)
        }
    }
}

@Composable
private fun CategoryBreakdownCard(state: SummaryUiState) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Where it went", style = MaterialTheme.typography.titleMedium)

            DonutChart(
                slices = state.categories.map { DonutSlice(it.totalMinor.toFloat(), categoryColor(it.colorSlot)) },
                description = "Spending by category, total ${Money.format(state.totalMinor)}",
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Money.format(state.totalMinor),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!state.isLoading && state.categories.isEmpty()) {
                Text(
                    "Nothing recorded for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Legend doubles as the data table: name, amount and share are always readable as
            // text, so nobody has to decode the ring by colour.
            state.categories.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(categoryColor(category.colorSlot)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${category.emoji} ${category.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        "${(category.share * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Money.format(category.totalMinor),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendCard(state: SummaryUiState, onSelectMonth: (java.time.YearMonth) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a bar to see that month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MonthlyBarChart(
                bars = state.trend.map { entry ->
                    MonthBar(
                        label = entry.month.format(barLabelFormatter),
                        valueLabel = Money.format(entry.totalMinor),
                        value = entry.totalMinor,
                        selected = entry.month == state.month,
                    )
                },
                onBarClick = { index -> onSelectMonth(state.trend[index].month) },
            )
        }
    }
}
