package com.example.expensetracker.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.expensetracker.container
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.local.ExpenseWithCategory
import com.example.expensetracker.util.toLocalDate
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ExpenseRow(
    val id: Long,
    val title: String,
    val categoryName: String,
    val emoji: String,
    val colorSlot: Int,
    val amountMinor: Long,
)

data class DaySection(
    val date: LocalDate,
    val totalMinor: Long,
    val rows: List<ExpenseRow>,
)

data class ExpenseListUiState(
    val month: YearMonth,
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
    val totalMinor: Long = 0,
    val days: List<DaySection> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseListViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now())

    /**
     * selected month -> Room query for that month -> immutable UI state.
     * WhileSubscribed(5s) keeps the query alive across rotation but stops it once the screen
     * is really gone.
     */
    val uiState: StateFlow<ExpenseListUiState> = selectedMonth
        .flatMapLatest { month ->
            repository.observeExpenses(month).map { expenses -> buildState(month, expenses) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExpenseListUiState(month = selectedMonth.value),
        )

    fun showPreviousMonth() = selectedMonth.update { it.minusMonths(1) }

    fun showNextMonth() = selectedMonth.update { month ->
        if (month < YearMonth.now()) month.plusMonths(1) else month
    }

    private fun buildState(month: YearMonth, expenses: List<ExpenseWithCategory>): ExpenseListUiState {
        val days = expenses
            .groupBy { it.expense.occurredAt.toLocalDate() } // query order (newest first) is preserved
            .map { (date, items) ->
                DaySection(
                    date = date,
                    totalMinor = items.sumOf { it.expense.amountMinor },
                    rows = items.map { item ->
                        ExpenseRow(
                            id = item.expense.id,
                            title = item.expense.merchant.ifBlank { item.category.name },
                            categoryName = item.category.name,
                            emoji = item.category.emoji,
                            colorSlot = item.category.colorSlot,
                            amountMinor = item.expense.amountMinor,
                        )
                    },
                )
            }
        return ExpenseListUiState(
            month = month,
            canGoNext = month < YearMonth.now(),
            isLoading = false,
            totalMinor = expenses.sumOf { it.expense.amountMinor },
            days = days,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ExpenseListViewModel(container().repository) }
        }
    }
}
