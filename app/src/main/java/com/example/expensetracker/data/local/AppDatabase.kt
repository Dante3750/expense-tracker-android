package com.example.expensetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, ExpenseEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        private const val NAME = "expenses.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addCallback(SeedDefaultCategories)
                // When the schema changes: bump `version` and add a Migration here.
                // Do not use fallbackToDestructiveMigration() - this database IS the user's data.
                .build()
    }
}

/** Runs once, when the database file is first created. */
private object SeedDefaultCategories : RoomDatabase.Callback() {

    // name, emoji, colour slot (0..7 - one per palette entry, never reused)
    private val defaults = listOf(
        Triple("Food & Dining", "🍽️", 0),
        Triple("Groceries", "🛒", 1),
        Triple("Transport", "🚌", 2),
        Triple("Shopping", "🛍️", 3),
        Triple("Bills & Utilities", "💡", 4),
        Triple("Health", "💊", 5),
        Triple("Entertainment", "🎬", 6),
        Triple("Other", "📦", 7),
    )

    override fun onCreate(db: SupportSQLiteDatabase) {
        defaults.forEach { (name, emoji, slot) ->
            db.execSQL(
                "INSERT INTO categories (name, emoji, colorSlot) VALUES (?, ?, ?)",
                arrayOf<Any>(name, emoji, slot),
            )
        }
    }
}
