package com.example.expensetracker.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.expensetracker.ui.edit.AddEditExpenseScreen
import com.example.expensetracker.ui.edit.AddEditExpenseViewModel
import com.example.expensetracker.ui.expenses.ExpenseListScreen
import com.example.expensetracker.ui.expenses.ExpenseListViewModel
import com.example.expensetracker.ui.scan.ReceiptScanScreen
import com.example.expensetracker.ui.scan.ReceiptScanViewModel
import com.example.expensetracker.ui.summary.SummaryScreen
import com.example.expensetracker.ui.summary.SummaryViewModel

object Routes {
    const val EXPENSES = "expenses"
    const val SUMMARY = "summary"
    const val SCAN = "scan"

    const val ARG_EXPENSE_ID = "expenseId"
    const val NO_ID = -1L
    const val EDIT = "edit?$ARG_EXPENSE_ID={$ARG_EXPENSE_ID}"
    fun edit(expenseId: Long? = null): String = if (expenseId == null) "edit" else "edit?$ARG_EXPENSE_ID=$expenseId"

    // Keys the scan screen uses to hand its result back to the edit screen's back-stack entry.
    const val RESULT_SCAN_AMOUNT = "scan_amount_minor"
    const val RESULT_SCAN_MERCHANT = "scan_merchant"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.EXPENSES) {

        composable(Routes.EXPENSES) {
            val viewModel: ExpenseListViewModel = viewModel(factory = ExpenseListViewModel.Factory)
            ExpenseListScreen(
                viewModel = viewModel,
                onAddExpense = { navController.navigate(Routes.edit()) },
                onEditExpense = { id -> navController.navigate(Routes.edit(id)) },
                bottomBar = { AppBottomBar(navController, current = Routes.EXPENSES) },
            )
        }

        composable(Routes.SUMMARY) {
            val viewModel: SummaryViewModel = viewModel(factory = SummaryViewModel.Factory)
            SummaryScreen(
                viewModel = viewModel,
                bottomBar = { AppBottomBar(navController, current = Routes.SUMMARY) },
            )
        }

        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument(Routes.ARG_EXPENSE_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.NO_ID
                },
            ),
        ) { entry ->
            val viewModel: AddEditExpenseViewModel = viewModel(factory = AddEditExpenseViewModel.Factory)

            // The scan screen writes its result into this entry's SavedStateHandle and pops.
            // Consume it exactly once, then clear it so rotation does not re-apply it.
            val scannedAmount by entry.savedStateHandle
                .getStateFlow<Long?>(Routes.RESULT_SCAN_AMOUNT, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(scannedAmount) {
                val amount = scannedAmount ?: return@LaunchedEffect
                viewModel.onReceiptScanned(amount, entry.savedStateHandle.get<String>(Routes.RESULT_SCAN_MERCHANT))
                entry.savedStateHandle.set<Long?>(Routes.RESULT_SCAN_AMOUNT, null)
                entry.savedStateHandle.set<String?>(Routes.RESULT_SCAN_MERCHANT, null)
            }

            AddEditExpenseScreen(
                viewModel = viewModel,
                onScanReceipt = { navController.navigate(Routes.SCAN) },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.SCAN) {
            val viewModel: ReceiptScanViewModel = viewModel(factory = ReceiptScanViewModel.Factory)
            ReceiptScanScreen(
                viewModel = viewModel,
                onResult = { amountMinor, merchant ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[Routes.RESULT_SCAN_MERCHANT] = merchant
                        handle[Routes.RESULT_SCAN_AMOUNT] = amountMinor
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController, current: String) {
    fun switchTo(route: String) {
        if (route == current) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    NavigationBar {
        NavigationBarItem(
            selected = current == Routes.EXPENSES,
            onClick = { switchTo(Routes.EXPENSES) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text("Expenses") },
        )
        NavigationBarItem(
            selected = current == Routes.SUMMARY,
            onClick = { switchTo(Routes.SUMMARY) },
            icon = { BarsIcon() },
            label = { Text("Summary") },
        )
    }
}

/** Tiny bar-chart glyph, drawn here so the app does not need the large extended-icons artifact. */
@Composable
private fun BarsIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val barWidth = size.width * 0.17f
        val heights = listOf(0.35f, 0.6f, 0.85f)
        heights.forEachIndexed { index, fraction ->
            val left = size.width * (0.17f + index * 0.25f)
            val barHeight = size.height * 0.75f * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height * 0.875f - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 4f),
            )
        }
    }
}
