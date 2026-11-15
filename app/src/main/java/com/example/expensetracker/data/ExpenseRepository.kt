package com.example.expensetracker.data

import com.example.expensetracker.data.local.CategoryDao
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.CategoryTotal
import com.example.expensetracker.data.local.ExpenseDao
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.local.ExpenseWithCategory
import com.example.expensetracker.util.epochRange
import com.example.expensetracker.util.toLocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Total spent in one calendar month. */
data class MonthTotal(val month: YearMonth, val totalMinor: Long)

/**
 * Single source of truth for the UI layer. Room is the only data source, so the app is fully
 * usable offline; if sync is added later, it plugs in behind this class (write locally first,
 * push in the background) without touching any ViewModel.
 */
class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeExpenses(month: YearMonth): Flow<List<ExpenseWithCategory>> {
        val range = month.epochRange(zone())
        return expenseDao.observeBetween(range.start, range.endExclusive)
    }

    fun observeCategoryTotals(month: YearMonth): Flow<List<CategoryTotal>> {
        val range = month.epochRange(zone())
        return expenseDao.observeCategoryTotals(range.start, range.endExclusive)
    }

    /**
     * Totals for the [monthCount] months ending at [endMonth], oldest first, with empty months
     * filled in as zero. Bucketing happens here with java.time instead of SQLite's strftime so
     * month boundaries follow the device time zone (including DST) exactly.
     */
    fun observeMonthlyTotals(endMonth: YearMonth, monthCount: Int = 6): Flow<List<MonthTotal>> {
        val zoneId = zone()
        val months = (monthCount - 1 downTo 0).map { endMonth.minusMonths(it.toLong()) }
        val start = months.first().epochRange(zoneId).start
        val endExclusive = months.last().epochRange(zoneId).endExclusive
        return expenseDao.observeAmountsBetween(start, endExclusive)
            .map { rows ->
                val sums = HashMap<YearMonth, Long>()
                for (row in rows) {
                    val month = YearMonth.from(row.occurredAt.toLocalDate(zoneId))
                    sums[month] = (sums[month] ?: 0L) + row.amountMinor
                }
                months.map { MonthTotal(it, sums[it] ?: 0L) }
            }
            .flowOn(Dispatchers.Default)
    }

    suspend fun getExpense(id: Long): ExpenseEntity? = expenseDao.getById(id)

    suspend fun saveExpense(expense: ExpenseEntity): Long = expenseDao.upsert(expense)

    suspend fun deleteExpense(id: Long) = expenseDao.deleteById(id)
}
