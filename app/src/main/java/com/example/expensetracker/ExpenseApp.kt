package com.example.expensetracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.local.AppDatabase
import com.example.expensetracker.data.ocr.ReceiptTextRecognizer

class ExpenseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Hand-rolled dependency container: three objects do not justify Hilt. Everything is lazy so
 * app start does no disk or model work. Swapping this for Hilt later only changes the
 * ViewModel factories.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val repository: ExpenseRepository by lazy {
        ExpenseRepository(database.expenseDao(), database.categoryDao())
    }

    val receiptTextRecognizer: ReceiptTextRecognizer by lazy { ReceiptTextRecognizer(appContext) }
}

/** Lets ViewModel factories reach the container: `initializer { Vm(container().repository) }`. */
fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExpenseApp).container
