package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * All read queries return [Flow]: Room re-runs them whenever the underlying tables change, which
 * is what keeps every screen in sync without manual refresh calls.
 *
 * Ranges are half-open: `[start, endExclusive)`.
 */
@Dao
interface ExpenseDao {

    @Transaction
    @Query(
        """
        SELECT * FROM expenses
        WHERE occurredAt >= :start AND occurredAt < :endExclusive
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun observeBetween(start: Long, endExclusive: Long): Flow<List<ExpenseWithCategory>>

    @Query(
        """
        SELECT c.id AS categoryId,
               c.name AS name,
               c.emoji AS emoji,
               c.colorSlot AS colorSlot,
               SUM(e.amountMinor) AS totalMinor
        FROM expenses e
        INNER JOIN categories c ON c.id = e.categoryId
        WHERE e.occurredAt >= :start AND e.occurredAt < :endExclusive
        GROUP BY c.id
        ORDER BY totalMinor DESC
        """,
    )
    fun observeCategoryTotals(start: Long, endExclusive: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT occurredAt, amountMinor FROM expenses
        WHERE occurredAt >= :start AND occurredAt < :endExclusive
        """,
    )
    fun observeAmountsBetween(start: Long, endExclusive: Long): Flow<List<DatedAmount>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Upsert
    suspend fun upsert(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
