package com.example.expensetracker.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.expensetracker.container
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.ui.navigation.Routes
import com.example.expensetracker.util.Money
import com.example.expensetracker.util.toEpochMillis
import com.example.expensetracker.util.toLocalDate
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val amountText: String = "",
    val amountError: String? = null,
    val merchant: String = "",
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val categoryId: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
    /** Shown after a receipt scan so the user knows the amount was machine-read. */
    val scanNotice: String? = null,
)

/** One-off events: things that must happen once, so they do not live in [AddEditUiState]. */
sealed interface AddEditEvent {
    data object Finished : AddEditEvent
}

class AddEditExpenseViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
) : ViewModel() {

    private val expenseId: Long = savedStateHandle.get<Long>(Routes.ARG_EXPENSE_ID) ?: Routes.NO_ID
    private var original: ExpenseEntity? = null

    private val form = MutableStateFlow(
        AddEditUiState(isEditing = expenseId != Routes.NO_ID, isLoading = expenseId != Routes.NO_ID),
    )

    /** The form the user is editing, combined with the live category list from Room. */
    val uiState: StateFlow<AddEditUiState> =
        combine(form, repository.observeCategories()) { current, categories ->
            current.copy(
                categories = categories,
                categoryId = current.categoryId ?: categories.firstOrNull()?.id,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    private val eventChannel = Channel<AddEditEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        if (expenseId != Routes.NO_ID) {
            viewModelScope.launch {
                val expense = repository.getExpense(expenseId)
                original = expense
                form.update { state ->
                    if (expense == null) {
                        state.copy(isLoading = false, isEditing = false)
                    } else {
                        state.copy(
                            isLoading = false,
                            amountText = Money.toInputText(expense.amountMinor),
                            merchant = expense.merchant,
                            note = expense.note,
                            date = expense.occurredAt.toLocalDate(),
                            categoryId = expense.categoryId,
                        )
                    }
                }
            }
        }
    }

    fun onAmountChange(text: String) {
        // Keep only what a money field can contain; validation happens on save.
        val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
        form.update { it.copy(amountText = cleaned, amountError = null) }
    }

    fun onMerchantChange(text: String) = form.update { it.copy(merchant = text.take(60)) }

    fun onNoteChange(text: String) = form.update { it.copy(note = text.take(200)) }

    fun onDateChange(date: LocalDate) = form.update { it.copy(date = date) }

    fun onCategorySelected(categoryId: Long) = form.update { it.copy(categoryId = categoryId) }

    /** Result handed back from the scan screen. The user still reviews it before saving. */
    fun onReceiptScanned(amountMinor: Long, merchant: String?) = form.update { state ->
        state.copy(
            amountText = Money.toInputText(amountMinor),
            amountError = null,
            merchant = state.merchant.ifBlank { merchant.orEmpty() },
            scanNotice = "Filled in from your receipt. Check the amount before saving.",
        )
    }

    fun save() {
        val state = uiState.value
        if (state.isSaving || state.isLoading) return

        val amountMinor = Money.parseToMinor(state.amountText)
        if (amountMinor == null) {
            form.update { it.copy(amountError = "Enter an amount greater than 0") }
            return
        }
        val categoryId = state.categoryId ?: return // categories are seeded, so this only guards startup

        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.saveExpense(
                ExpenseEntity(
                    id = original?.id ?: 0,
                    amountMinor = amountMinor,
                    categoryId = categoryId,
                    merchant = state.merchant.trim(),
                    note = state.note.trim(),
                    occurredAt = state.date.toEpochMillis(),
                    createdAt = original?.createdAt ?: now,
                ),
            )
            eventChannel.send(AddEditEvent.Finished)
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch {
            repository.deleteExpense(id)
            eventChannel.send(AddEditEvent.Finished)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AddEditExpenseViewModel(createSavedStateHandle(), container().repository) }
        }
    }
}
