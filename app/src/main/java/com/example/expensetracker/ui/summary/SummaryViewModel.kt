package com.example.expensetracker.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.expensetracker.container
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.MonthTotal
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CategoryShare(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val colorSlot: Int,
    val totalMinor: Long,
    /** 0f..1f share of the month's spending. */
    val share: Float,
)

data class SummaryUiState(
    val month: YearMonth,
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
    val totalMinor: Long = 0,
    val categories: List<CategoryShare> = emptyList(),
    /** Oldest first; always [TREND_MONTHS] entries, zero-filled. */
    val trend: List<MonthTotal> = emptyList(),
)

private const val TREND_MONTHS = 6

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<SummaryUiState> = selectedMonth
        .flatMapLatest { month ->
            combine(
                repository.observeCategoryTotals(month),
                repository.observeMonthlyTotals(trendWindowEnd(month), TREND_MONTHS),
            ) { categoryTotals, trend ->
                val total = categoryTotals.sumOf { it.totalMinor }
                SummaryUiState(
                    month = month,
                    canGoNext = month < YearMonth.now(),
                    isLoading = false,
                    totalMinor = total,
                    categories = categoryTotals.map {
                        CategoryShare(
                            categoryId = it.categoryId,
                            name = it.name,
                            emoji = it.emoji,
                            colorSlot = it.colorSlot,
                            totalMinor = it.totalMinor,
                            share = if (total == 0L) 0f else it.totalMinor.toFloat() / total,
                        )
                    },
                    trend = trend,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SummaryUiState(month = selectedMonth.value),
        )

    fun selectMonth(month: YearMonth) {
        if (month <= YearMonth.now()) selectedMonth.value = month
    }

    fun showPreviousMonth() = selectedMonth.update { it.minusMonths(1) }

    fun showNextMonth() = selectMonth(selectedMonth.value.plusMonths(1))

    /**
     * Keeps the bar chart still while the user taps around recent months: the window ends at the
     * current month unless the selection is older than that window can show.
     */
    private fun trendWindowEnd(selected: YearMonth): YearMonth {
        val now = YearMonth.now()
        return if (selected >= now.minusMonths(TREND_MONTHS - 1L)) now else selected
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SummaryViewModel(container().repository) }
        }
    }
}
