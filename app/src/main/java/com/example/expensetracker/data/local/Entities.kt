package com.example.expensetracker.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A spending category.
 *
 * [colorSlot] is an index into the chart palette (see ui/theme/ChartPalette.kt) rather than a raw
 * colour, so the same category keeps its identity in light and dark themes.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val colorSlot: Int,
)

/**
 * A single expense.
 *
 * - [amountMinor] is stored in minor units (paise / cents) so sums are exact; never store money as
 *   a floating-point column.
 * - [occurredAt] is epoch milliseconds, indexed because every screen filters by a date range.
 * - Deleting a category that still has expenses is blocked (RESTRICT) instead of silently
 *   cascading away the user's history.
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("occurredAt")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val categoryId: Long,
    val merchant: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
)

/** Expense joined with its category, for list rows. */
data class ExpenseWithCategory(
    @Embedded val expense: ExpenseEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity,
)

/** Result row of the per-category aggregate query. */
data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val colorSlot: Int,
    val totalMinor: Long,
)

/** Light projection used to bucket spending into calendar months in the repository. */
data class DatedAmount(
    val occurredAt: Long,
    val amountMinor: Long,
)
