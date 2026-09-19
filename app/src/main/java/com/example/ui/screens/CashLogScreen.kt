package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CashOutEntry
import com.example.data.OperationalExpense
import com.example.data.Role
import com.example.data.TimeHorizon
import com.example.data.shopDateFormat
import com.example.ui.theme.*
import com.example.viewmodel.CapitalRecoveryOverview
import com.example.viewmodel.OperationalCycleSummary
import com.example.viewmodel.OperationalDailySummary
import com.example.viewmodel.OperationalReportSummary
import com.example.viewmodel.POSViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashLogScreen(viewModel: POSViewModel, modifier: Modifier = Modifier) {
    var activeTab by remember { mutableStateOf("EMPLOYEE") } // EMPLOYEE, OPERATIONAL

    // ── Employee Expenses state ──
    val entries by viewModel.filteredCashOutEntries.collectAsState()
    val allEntries by viewModel.cashOutEntries.collectAsState()
    val todayTotal by viewModel.todayExpenseTotal.collectAsState()
    val dateFilter by viewModel.cashLogDateFilter.collectAsState()
    val isSavingExpense by viewModel.isSavingCashOut.collectAsState()

    // ── Store Operational state ──
    val operationalReport by viewModel.operationalReport.collectAsState()
    val operationalHorizon by viewModel.operationalHorizon.collectAsState()
    val operationalCustomStartDate by viewModel.operationalCustomStartDate.collectAsState()
    val operationalCustomEndDate by viewModel.operationalCustomEndDate.collectAsState()
    val isSavingOperational by viewModel.isSavingOperationalExpense.collectAsState()
    val operationalCycles by viewModel.operationalCycles.collectAsState()
    val capitalRecoveryOverview by viewModel.capitalRecoveryOverview.collectAsState()

    val currentRole by viewModel.currentRole.collectAsState()
    val currentCashierName by viewModel.currentCashierName.collectAsState()
    val isAdmin = currentRole == Role.ADMIN

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")) }
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault()) }
    val dateOnlyFormat = remember { shopDateFormat("yyyy-MM-dd") }
    val dateSpanFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val availableDates = remember(allEntries) {
        allEntries.map { dateOnlyFormat.format(Date(it.timestamp)) }.distinct().sortedDescending()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            availableDates.contains(dateOnlyFormat.format(Date(utcTimeMillis)))
        override fun isSelectableYear(year: Int): Boolean = true
    })

    var showOperationalDateRangePicker by remember { mutableStateOf(false) }
    val operationalDateRangePickerState = rememberDateRangePickerState()

    var showEntryForm by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<CashOutEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<CashOutEntry?>(null) }

    var showOperationalForm by remember { mutableStateOf(false) }
    var editingOperationalEntry by remember { mutableStateOf<OperationalExpense?>(null) }
    var operationalEntryToDelete by remember { mutableStateOf<OperationalExpense?>(null) }

    var showPeriodBreakdown by remember { mutableStateOf(false) }
    var showAllCycles by remember { mutableStateOf(false) }

    fun canModify(entry: CashOutEntry) =
        isAdmin || (currentRole == Role.CASHIER && entry.cashierName == currentCashierName)

    Box(modifier = modifier.fillMaxSize().background(BackgroundLight)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Expenses & Operations",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextDark),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Employee Expenses / Store Operational Toggle ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(ShapeXL)
                    .background(SurfaceContainer)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ShapeLG)
                        .background(if (activeTab == "EMPLOYEE") SurfaceLight else Color.Transparent)
                        .clickable { activeTab = "EMPLOYEE" }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Employee Expenses",
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == "EMPLOYEE") BrandPrimary else TextMuted
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ShapeLG)
                        .background(if (activeTab == "OPERATIONAL") SurfaceLight else Color.Transparent)
                        .clickable { activeTab = "OPERATIONAL" }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Store Operational",
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == "OPERATIONAL") BrandPrimary else TextMuted
                        )
                    )
                }
            }

            if (activeTab == "EMPLOYEE") {
                // ── Hero Stat — Today's Employee Expenses ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .shadow(elevation = 8.dp, shape = ShapeXL, clip = false),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = ShapeXL,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(BrandPrimary, BrandSecondary)))
                            .padding(20.dp)
                    ) {
                        Text("Today's Employee Expenses", style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.85f)))
                        Spacer(Modifier.height(6.dp))
                        AnimatedCounterText(
                            value = todayTotal,
                            format = { formatPeso(currencyFormatter, it) },
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
                            color = Color.White
                        )
                    }
                }

                // ── Date Filter ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = dateFilter != null,
                        onClick = { showDatePicker = true },
                        label = {
                            val labelStr = if (dateFilter != null) {
                                try { SimpleDateFormat("MMM dd", Locale.getDefault()).format(dateOnlyFormat.parse(dateFilter!!) ?: Date()) }
                                catch (_: Exception) { dateFilter ?: "All Dates" }
                            } else "All Dates"
                            Text(labelStr, fontSize = 12.sp)
                        },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White,
                            containerColor = SurfaceContainer, labelColor = TextDark
                        ),
                        border = null
                    )
                    if (dateFilter != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.setCashLogDateFilter(null) }) {
                            Text("Clear", fontSize = 12.sp, color = ColorUnpaid)
                        }
                    }
                }

                // ── Employee Expense List ──
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No employee expenses logged", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
                    ) {
                        items(entries) { entry ->
                            CashOutEntryCard(
                                entry = entry,
                                currencyFormatter = currencyFormatter,
                                dateFormatter = sdf,
                                canModify = canModify(entry),
                                onEdit = { editingEntry = entry; showEntryForm = true },
                                onDelete = { entryToDelete = entry }
                            )
                        }
                    }
                }
            } else {
                // ── STORE OPERATIONAL TAB ──
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                ) {
                    // 1. All-Time Headline: Capital Recovery Overview Hero Card
                    item {
                        CapitalRecoveryHeroCard(
                            overview = capitalRecoveryOverview,
                            currencyFormatter = currencyFormatter
                        )
                    }

                    // 2. Collapsible Period Breakdown Toggle
                    item {
                        Surface(
                            color = SurfaceLight,
                            shape = ShapeLG,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeLG)
                                .clickable { showPeriodBreakdown = !showPeriodBreakdown }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (showPeriodBreakdown) "Hide period breakdown" else "View by period (day / week / month)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandPrimary
                                        )
                                    )
                                }
                                Icon(
                                    if (showPeriodBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (showPeriodBreakdown) {
                        // Date Horizon Filter Bar
                        item {
                            OperationalDateFilterBar(
                                selectedHorizon = operationalHorizon,
                                customStartDate = operationalCustomStartDate,
                                customEndDate = operationalCustomEndDate,
                                rangeLabel = operationalReport.rangeLabel,
                                dateOnlyFormat = dateOnlyFormat,
                                onSelectHorizon = { horizon ->
                                    viewModel.setOperationalFilter(horizon)
                                },
                                onPickCustomRange = {
                                    showOperationalDateRangePicker = true
                                },
                                onClearFilter = {
                                    viewModel.setOperationalFilter(TimeHorizon.ALL)
                                }
                            )
                        }

                        // Filtered Period Profitability Hero Card
                        item {
                            OperationalHeroCard(
                                report = operationalReport,
                                currencyFormatter = currencyFormatter
                            )
                        }

                        // 4-Pill Summary KPI Cards
                        item {
                            OperationalSummaryTiles(
                                report = operationalReport,
                                currencyFormatter = currencyFormatter
                            )
                        }
                    }

                    // 3. Purchase-by-Purchase Breakdown Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Purchase-by-Purchase Breakdown",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                            )
                            Surface(
                                color = SurfaceContainer,
                                shape = ShapeXL
                            ) {
                                Text(
                                    "${operationalCycles.size} purchase(s)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = TextMuted),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (operationalCycles.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                                shape = ShapeMD
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Inventory, contentDescription = null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text("No capital cycles logged yet", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                                    }
                                }
                            }
                        }
                    } else {
                        val visibleCycles = if (showAllCycles) operationalCycles else operationalCycles.take(5)
                        items(visibleCycles) { cycle ->
                            CapitalCycleCard(
                                cycle = cycle,
                                currencyFormatter = currencyFormatter,
                                dateFormatter = sdf
                            )
                        }

                        if (!showAllCycles && operationalCycles.size > 5) {
                            item {
                                TextButton(
                                    onClick = { showAllCycles = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Show all ${operationalCycles.size} purchases",
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandPrimary
                                    )
                                }
                            }
                        }
                    }

                    // 4. Daily Operations Ledger Table Title & Badge
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Daily Operations Ledger",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                            )
                            Surface(
                                color = SurfaceContainer,
                                shape = ShapeXL
                            ) {
                                Text(
                                    "${operationalReport.dailySummaries.size} active day(s)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = TextMuted),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // 5. Daily Ledger Table
                    item {
                        OperationalDailyTable(
                            dailySummaries = operationalReport.dailySummaries,
                            currencyFormatter = currencyFormatter,
                            onEditExpense = { exp ->
                                editingOperationalEntry = exp
                                showOperationalForm = true
                            },
                            onDeleteExpense = { exp ->
                                operationalEntryToDelete = exp
                            }
                        )
                    }

                    // 6. Store Operational Expenses Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Store Operational Expenses",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                            )
                            TextButton(
                                onClick = {
                                    editingOperationalEntry = null
                                    showOperationalForm = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandPrimary)
                                Spacer(Modifier.width(4.dp))
                                Text("Add Expense", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
                            }
                        }
                    }

                    // 7. Store Operational Expenses Item List
                    if (operationalReport.filteredStoreExpenses.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                                shape = ShapeMD
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(44.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text("No store expenses logged in this date range", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                                        Spacer(Modifier.height(10.dp))
                                        Button(
                                            onClick = { editingOperationalEntry = null; showOperationalForm = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                            shape = ShapeSM
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Log Store Expense")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(operationalReport.filteredStoreExpenses) { exp ->
                            OperationalExpenseItemCard(
                                expense = exp,
                                currencyFormatter = currencyFormatter,
                                dateFormatter = sdf,
                                onEdit = {
                                    editingOperationalEntry = exp
                                    showOperationalForm = true
                                },
                                onDelete = {
                                    operationalEntryToDelete = exp
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Floating Action Button ──
        FloatingActionButton(
            onClick = {
                if (activeTab == "EMPLOYEE") {
                    editingEntry = null
                    showEntryForm = true
                } else {
                    editingOperationalEntry = null
                    showOperationalForm = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 16.dp),
            containerColor = BrandPrimary,
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (activeTab == "EMPLOYEE") Icons.Default.Add else Icons.Default.LocalShipping,
                    contentDescription = "Log Expense"
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (activeTab == "EMPLOYEE") "Log Expense" else "Log Store Expense",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ── Date Picker Dialog for Employee Expenses ──
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    viewModel.setCashLogDateFilter(if (selectedMillis != null) dateOnlyFormat.format(Date(selectedMillis)) else null)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setCashLogDateFilter(null)
                    showDatePicker = false
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = datePickerState, title = { Text("Select Date") })
        }
    }

    // ── Date Range Picker Dialog for Store Operations ──
    if (showOperationalDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showOperationalDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = operationalDateRangePickerState.selectedStartDateMillis
                        val endMillis = operationalDateRangePickerState.selectedEndDateMillis ?: startMillis
                        if (startMillis != null) {
                            val startStr = dateOnlyFormat.format(Date(startMillis))
                            val endStr = if (endMillis != null) dateOnlyFormat.format(Date(endMillis)) else startStr
                            viewModel.setOperationalFilter(TimeHorizon.CUSTOM, startStr, endStr)
                        } else {
                            viewModel.setOperationalFilter(TimeHorizon.ALL)
                        }
                        showOperationalDateRangePicker = false
                    },
                    enabled = operationalDateRangePickerState.selectedStartDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setOperationalFilter(TimeHorizon.ALL)
                    showOperationalDateRangePicker = false
                }) { Text("Clear") }
            }
        ) {
            DateRangePicker(
                state = operationalDateRangePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                showModeToggle = false
            )
        }
    }

    // ── Employee Expense Add/Edit Dialog with 100 / 200 Quick Options ──
    if (showEntryForm) {
        CashOutEntryFormDialog(
            entry = editingEntry,
            defaultCashierName = if (currentRole == Role.CASHIER) currentCashierName ?: "" else "",
            lockCashierName = currentRole == Role.CASHIER,
            isSaving = isSavingExpense,
            onDismiss = { showEntryForm = false },
            onSave = { cashierName, amount, note ->
                val target = editingEntry
                if (target == null) {
                    viewModel.addCashOutEntry(cashierName, amount, note) { showEntryForm = false }
                } else {
                    viewModel.updateCashOutEntry(target.copy(cashierName = cashierName), amount, note) { showEntryForm = false }
                }
            }
        )
    }

    // ── Employee Expense Delete Confirmation ──
    val toDelete = entryToDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ColorUnpaid, modifier = Modifier.size(36.dp)) },
            title = { Text("Delete Expense?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete this expense entry. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteCashOutEntry(toDelete); entryToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ── Store Operational Expense Add/Edit Dialog ──
    if (showOperationalForm) {
        OperationalExpenseFormDialog(
            entry = editingOperationalEntry,
            isSaving = isSavingOperational,
            onDismiss = { showOperationalForm = false },
            onSave = { title, amount, note, timestamp ->
                val target = editingOperationalEntry
                if (target == null) {
                    viewModel.addOperationalExpense(title, amount, note, timestamp) { showOperationalForm = false }
                } else {
                    viewModel.updateOperationalExpense(target, title, amount, note, timestamp) { showOperationalForm = false }
                }
            }
        )
    }

    // ── Store Operational Expense Delete Confirmation ──
    val opToDelete = operationalEntryToDelete
    if (opToDelete != null) {
        AlertDialog(
            onDismissRequest = { operationalEntryToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ColorUnpaid, modifier = Modifier.size(36.dp)) },
            title = { Text("Delete Store Expense?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete this store expense entry.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteOperationalExpense(opToDelete); operationalEntryToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { operationalEntryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Headline Capital Recovery Hero Card ──
@Composable
private fun CapitalRecoveryHeroCard(
    overview: CapitalRecoveryOverview,
    currencyFormatter: NumberFormat
) {
    val isProfit = overview.isOverallProfitable
    val hasCycles = overview.totalCycles > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = ShapeXL, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeXL
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Status badge & All-Time tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when {
                        !hasCycles -> SurfaceContainer
                        isProfit -> ColorPaid.copy(alpha = 0.12f)
                        else -> ColorUnpaid.copy(alpha = 0.12f)
                    },
                    shape = ShapeXL
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(ShapeXL)
                                .background(
                                    when {
                                        !hasCycles -> TextMuted
                                        isProfit -> ColorPaid
                                        else -> ColorUnpaid
                                    }
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                !hasCycles -> "NO CAPITAL PURCHASES"
                                isProfit -> "YOU'RE AHEAD"
                                else -> "STILL EARNING IT BACK"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    !hasCycles -> TextMuted
                                    isProfit -> ColorPaid
                                    else -> ColorUnpaid
                                },
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Text(
                    "All-Time Purchases Rollup",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Net Profit / Deficit Highlight Surface
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    !hasCycles -> SurfaceContainer.copy(alpha = 0.5f)
                    isProfit -> ColorPaid.copy(alpha = 0.08f)
                    else -> ColorUnpaid.copy(alpha = 0.08f)
                },
                shape = ShapeMD
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Overall Net Capital Position",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Medium)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (!hasCycles) "₱0.00"
                            else "${if (overview.totalNetProfit > 0) "+" else ""}${formatPeso(currencyFormatter, overview.totalNetProfit)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = when {
                                    !hasCycles -> TextDark
                                    isProfit -> ColorPaid
                                    else -> ColorUnpaid
                                },
                                fontSize = 26.sp
                            )
                        )
                    }
                }
            }

            if (hasCycles) {
                Spacer(Modifier.height(12.dp))

                // Active Cycle Recovery Progress Bar
                LinearProgressIndicator(
                    progress = { (overview.activeCycleRecoveryRate / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(ShapeXL),
                    color = if (overview.activeCycleOutstanding == 0.0) ColorPaid else BrandPrimary,
                    trackColor = SurfaceContainer
                )

                Spacer(Modifier.height(8.dp))

                // Active Cycle Sub-Line
                val rateStr = String.format(Locale.US, "%.1f", overview.activeCycleRecoveryRate)
                val activeSubLine = if (overview.activeCycleOutstanding > 0.0) {
                    "Current batch: $rateStr% covered · ${formatPeso(currencyFormatter, overview.activeCycleOutstanding)} to go"
                } else {
                    "Current batch: $rateStr% covered · Break-even reached"
                }

                Text(
                    text = activeSubLine,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (overview.activeCycleOutstanding == 0.0) ColorPaid else TextDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                // Optional historical unrecovered footer line
                if (overview.historicalUnrecoveredCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${overview.historicalUnrecoveredCount} past purchase(s) didn't fully pay for themselves (–${formatPeso(currencyFormatter, overview.historicalUnrecoveredTotal)} total)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}

// ── Period Profitability Hero Card (Date-Filtered) ──
@Composable
private fun OperationalHeroCard(
    report: OperationalReportSummary,
    currencyFormatter: NumberFormat
) {
    val isProfit = report.isProfitable

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = ShapeXL, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeXL
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Status badge & range label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isProfit) ColorPaid.copy(alpha = 0.12f) else ColorUnpaid.copy(alpha = 0.12f),
                    shape = ShapeXL
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(ShapeXL)
                                .background(if (isProfit) ColorPaid else ColorUnpaid)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isProfit) "PROFIT (this period)" else "STILL RECOVERING (this period)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isProfit) ColorPaid else ColorUnpaid,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Text(
                    report.rangeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Net Profit / Deficit Highlight Surface
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isProfit) ColorPaid.copy(alpha = 0.08f) else ColorUnpaid.copy(alpha = 0.08f),
                shape = ShapeMD
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (isProfit) "Profit this period (${report.rangeLabel})" else "Remaining to Break-Even (${report.rangeLabel})",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Medium)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatPeso(currencyFormatter, if (isProfit) report.netProfit else report.remainingToBreakEven),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = if (isProfit) ColorPaid else ColorUnpaid,
                                fontSize = 26.sp
                            )
                        )
                    }
                    Surface(
                        color = if (isProfit) ColorPaid else ColorUnpaid,
                        shape = ShapeXL
                    ) {
                        Text(
                            text = if (isProfit) "Profitable (+${String.format(Locale.US, "%.1f", report.profitMargin)}%)"
                            else "Deficit (${report.recoveryRate.toInt()}% recovered)",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Break-Even Progress Bar
            val progressFraction = (report.recoveryRate / 100.0).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(ShapeXL),
                color = if (isProfit) ColorPaid else BrandPrimary,
                trackColor = SurfaceContainer
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Sales: ${formatPeso(currencyFormatter, report.totalSales)}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                )
                Text(
                    "Total Expenses: ${formatPeso(currencyFormatter, report.totalExpenses)}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                )
            }
        }
    }
}

// ── 4-Tile Financial Summary ──
@Composable
private fun OperationalSummaryTiles(
    report: OperationalReportSummary,
    currencyFormatter: NumberFormat
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Gross Sales
            Surface(
                modifier = Modifier.weight(1f),
                color = ColorPaid.copy(alpha = 0.08f),
                shape = ShapeSM
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Gross Sales (${report.totalOrders})", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(formatPeso(currencyFormatter, report.totalSales), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ColorPaid), maxLines = 1)
                }
            }

            // Total Expenses
            Surface(
                modifier = Modifier.weight(1f),
                color = SurfaceContainer.copy(alpha = 0.6f),
                shape = ShapeSM
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Total Expenses", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(formatPeso(currencyFormatter, report.totalExpenses), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark), maxLines = 1)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Store Expenses
            Surface(
                modifier = Modifier.weight(1f),
                color = BrandPrimary.copy(alpha = 0.08f),
                shape = ShapeSM
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Store Expenses", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(formatPeso(currencyFormatter, report.totalStoreExpenses), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = BrandPrimary), maxLines = 1)
                }
            }

            // Employee Expenses
            Surface(
                modifier = Modifier.weight(1f),
                color = ChartHighlight.copy(alpha = 0.08f),
                shape = ShapeSM
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Employee Logs", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(formatPeso(currencyFormatter, report.totalEmployeeExpenses), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ChartHighlight), maxLines = 1)
                }
            }
        }
    }
}

// ── Date Horizon Filter Bar ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationalDateFilterBar(
    selectedHorizon: TimeHorizon,
    customStartDate: String?,
    customEndDate: String?,
    rangeLabel: String,
    dateOnlyFormat: SimpleDateFormat,
    onSelectHorizon: (TimeHorizon) -> Unit,
    onPickCustomRange: () -> Unit,
    onClearFilter: () -> Unit
) {
    val displayFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val isCustom = selectedHorizon == TimeHorizon.CUSTOM && customStartDate != null

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val chips = listOf(
                TimeHorizon.ALL to "All Time",
                TimeHorizon.DAY to "Today",
                TimeHorizon.WEEK to "7D",
                TimeHorizon.THIRTY_DAYS to "30D",
                TimeHorizon.MTD to "MTD"
            )

            chips.forEach { (horizon, label) ->
                val isSelected = selectedHorizon == horizon && !isCustom
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectHorizon(horizon) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = SurfaceContainer,
                        labelColor = TextDark
                    ),
                    border = null,
                    modifier = Modifier.height(30.dp)
                )
            }

            // Custom Range Chip
            FilterChip(
                selected = isCustom,
                onClick = onPickCustomRange,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (isCustom) Color.White else TextMuted)
                        Spacer(Modifier.width(4.dp))
                        val customLabel = if (isCustom && !customStartDate.isNullOrBlank()) {
                            val s = try { displayFmt.format(dateOnlyFormat.parse(customStartDate) ?: Date()) } catch (_: Exception) { customStartDate }
                            val e = if (!customEndDate.isNullOrBlank() && customEndDate != customStartDate) {
                                try { displayFmt.format(dateOnlyFormat.parse(customEndDate) ?: Date()) } catch (_: Exception) { customEndDate }
                            } else null
                            if (e != null) "$s – $e" else "$s"
                        } else "Range"
                        Text(customLabel, fontSize = 11.sp, fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandPrimary,
                    selectedLabelColor = Color.White,
                    containerColor = SurfaceContainer,
                    labelColor = TextDark
                ),
                border = null,
                modifier = Modifier.height(30.dp)
            )

            if (isCustom) {
                TextButton(onClick = onClearFilter, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Clear", fontSize = 11.sp, color = ColorUnpaid)
                }
            }
        }
    }
}

// ── Daily Breakdown Table ──
@Composable
private fun OperationalDailyTable(
    dailySummaries: List<OperationalDailySummary>,
    currencyFormatter: NumberFormat,
    onEditExpense: (OperationalExpense) -> Unit,
    onDeleteExpense: (OperationalExpense) -> Unit
) {
    if (dailySummaries.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            shape = ShapeMD
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No operations or sales logged in this period.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                )
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeLG,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Table Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandPrimary.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Date",
                    modifier = Modifier.weight(1.3f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                )
                Text(
                    "Store Exp",
                    modifier = Modifier.weight(1.1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                )
                Text(
                    "Emp Exp",
                    modifier = Modifier.weight(1.1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                )
                Text(
                    "Sales",
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                )
                Text(
                    "Net P/L",
                    modifier = Modifier.weight(1.3f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                )
            }
            HorizontalDivider(color = BorderLight)

            dailySummaries.forEachIndexed { index, summary ->
                OperationalDailyTableRow(
                    summary = summary,
                    currencyFormatter = currencyFormatter,
                    isEven = index % 2 == 0,
                    onEditExpense = onEditExpense,
                    onDeleteExpense = onDeleteExpense
                )
                if (index < dailySummaries.size - 1) {
                    HorizontalDivider(color = BorderLight.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ── Daily Breakdown Table Row ──
@Composable
private fun OperationalDailyTableRow(
    summary: OperationalDailySummary,
    currencyFormatter: NumberFormat,
    isEven: Boolean,
    onEditExpense: (OperationalExpense) -> Unit,
    onDeleteExpense: (OperationalExpense) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isProfit = summary.isProfitable

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEven) Color.Transparent else SurfaceContainer.copy(alpha = 0.35f))
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date column
            Column(modifier = Modifier.weight(1.3f)) {
                Text(
                    summary.displayDate,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        summary.status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isProfit) ColorPaid else ColorUnpaid,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = TextMuted
                    )
                }
            }

            // Store Exp column
            Text(
                if (summary.storeExpense > 0) formatPeso(currencyFormatter, summary.storeExpense) else "—",
                modifier = Modifier.weight(1.1f),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (summary.storeExpense > 0) TextDark else TextMuted,
                    fontWeight = if (summary.storeExpense > 0) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )

            // Emp Exp column
            Text(
                if (summary.employeeExpense > 0) formatPeso(currencyFormatter, summary.employeeExpense) else "—",
                modifier = Modifier.weight(1.1f),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (summary.employeeExpense > 0) ChartHighlight else TextMuted,
                    fontWeight = if (summary.employeeExpense > 0) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )

            // Sales column
            Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                Text(
                    formatPeso(currencyFormatter, summary.sales),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (summary.sales > 0) ColorPaid else TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
                if (summary.orderCount > 0) {
                    Text(
                        "${summary.orderCount} order${if (summary.orderCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                    )
                }
            }

            // Net P/L column
            Column(modifier = Modifier.weight(1.3f), horizontalAlignment = Alignment.End) {
                Text(
                    "${if (summary.netProfit >= 0) "+" else ""}${formatPeso(currencyFormatter, summary.netProfit)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Black,
                        color = if (isProfit) ColorPaid else ColorUnpaid,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }

        // Expandable Detail Accordion
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Breakdown for ${summary.displayDate}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                )

                // Store expenses list
                if (summary.storeExpenses.isNotEmpty()) {
                    Text(
                        "Store Expenses Logged (${summary.storeExpenses.size}):",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.SemiBold)
                    )
                    summary.storeExpenses.forEach { exp ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                Text(exp.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark))
                                if (!exp.note.isNullOrBlank()) {
                                    Text("Note: ${exp.note}", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "-${formatPeso(currencyFormatter, exp.amount)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ColorUnpaid)
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { onEditExpense(exp) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { onDeleteExpense(exp) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ColorUnpaid, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Employee cash outs list
                if (summary.employeeExpenses.isNotEmpty()) {
                    Text(
                        "Employee Wages & Cash-Outs (${summary.employeeExpenses.size}):",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.SemiBold)
                    )
                    summary.employeeExpenses.forEach { cash ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                Text(
                                    if (cash.cashierName.isNotBlank()) cash.cashierName else "Employee Cash Out",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                                )
                                if (!cash.note.isNullOrBlank()) {
                                    Text("Note: ${cash.note}", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                                }
                            }
                            Text(
                                "-${formatPeso(currencyFormatter, cash.amount)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ChartHighlight)
                            )
                        }
                    }
                }

                // Sales summary line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Gross Sales Revenue (${summary.orderCount} orders):",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                    )
                    Text(
                        "+${formatPeso(currencyFormatter, summary.sales)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ColorPaid)
                    )
                }

                HorizontalDivider(color = BorderLight.copy(alpha = 0.5f))

                // Net Daily Profit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (summary.netProfit >= 0) "Daily Net Profit:" else "Daily Net Deficit:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black, color = if (isProfit) ColorPaid else ColorUnpaid)
                    )
                    Text(
                        "${if (summary.netProfit >= 0) "+" else ""}${formatPeso(currencyFormatter, summary.netProfit)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = if (isProfit) ColorPaid else ColorUnpaid)
                    )
                }
            }
        }
    }
}

// ── Itemized Store Operational Expense Card ──
@Composable
private fun OperationalExpenseItemCard(
    expense: OperationalExpense,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = ShapeMD, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeMD
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(ShapeMD)
                            .background(BrandPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            expense.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                        )
                        Text(
                            dateFormatter.format(Date(expense.timestamp)),
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                    }
                }

                Text(
                    formatPeso(currencyFormatter, expense.amount),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black, color = ColorUnpaid)
                )
            }

            if (!expense.note.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = SurfaceContainer,
                    shape = ShapeXS
                ) {
                    Text(
                        "Note: ${expense.note}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit, colors = ButtonDefaults.textButtonColors(contentColor = BrandPrimary), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", fontSize = 11.sp)
                }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = ColorUnpaid), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Capital Recovery Cycle Card ──
@Composable
private fun CapitalCycleCard(
    cycle: OperationalCycleSummary,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat
) {
    val isRecovered = cycle.netBalance >= 0.0
    val progressFraction = (cycle.recoveryRate / 100.0).toFloat().coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = ShapeMD, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeMD
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Purchase icon + Title + Date + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(ShapeMD)
                            .background(if (cycle.isActive) BrandPrimary.copy(alpha = 0.12f) else SurfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (cycle.isActive) Icons.Default.Autorenew else Icons.Default.Inventory,
                            contentDescription = null,
                            tint = if (cycle.isActive) BrandPrimary else TextDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            cycle.expense.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                        )
                        Text(
                            dateFormatter.format(Date(cycle.expense.timestamp)),
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (cycle.isActive) {
                        Surface(
                            color = BrandPrimary.copy(alpha = 0.12f),
                            shape = ShapeXL
                        ) {
                            Text(
                                "ONGOING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPrimary,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Surface(
                        color = if (isRecovered) ColorPaid.copy(alpha = 0.12f) else ColorUnpaid.copy(alpha = 0.12f),
                        shape = ShapeXL
                    ) {
                        Text(
                            if (isRecovered) "RECOVERED" else "DEFICIT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isRecovered) ColorPaid else ColorUnpaid,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Metrics Grid: Paid Sales, Employee Expenses, Total Cost
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceContainer.copy(alpha = 0.5f),
                shape = ShapeSM
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Paid Sales (${cycle.periodOrderCount})", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                        Text(formatPeso(currencyFormatter, cycle.periodSalesTotal), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark))
                    }
                    Column {
                        Text("Employee Wages", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                        Text(formatPeso(currencyFormatter, cycle.periodEmployeeExpenses), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark))
                    }
                    Column {
                        Text("Total Period Cost", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                        Text(formatPeso(currencyFormatter, cycle.totalPeriodCost), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = ColorUnpaid))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Recovery Progress Bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(ShapeXL),
                color = if (isRecovered) ColorPaid else BrandPrimary,
                trackColor = SurfaceContainer
            )

            Spacer(Modifier.height(8.dp))

            // Bottom row: Recovery % and Net Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", cycle.recoveryRate)}% covered",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isRecovered) "Net Profit: " else "Deficit: ",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                    )
                    Text(
                        text = (if (cycle.netBalance > 0) "+" else "") + formatPeso(currencyFormatter, cycle.netBalance),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = if (isRecovered) ColorPaid else ColorUnpaid,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

// ── Employee Expense Card ──
@Composable
private fun CashOutEntryCard(
    entry: CashOutEntry,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat,
    canModify: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = 2.dp, shape = ShapeMD, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeMD,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (entry.cashierName.isNotBlank()) entry.cashierName else "Employee Expense",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                    )
                }
                Text(
                    formatPeso(currencyFormatter, entry.amount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = ColorUnpaid)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(dateFormatter.format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
            if (!entry.note.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(entry.note, style = MaterialTheme.typography.bodySmall.copy(color = TextDark))
            }
            if (canModify) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit, colors = ButtonDefaults.textButtonColors(contentColor = BrandPrimary)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp)
                    }
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = ColorUnpaid)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── Employee Expense Form Dialog with 100 / 200 Quick Options ──
@Composable
private fun CashOutEntryFormDialog(
    entry: CashOutEntry?,
    defaultCashierName: String,
    lockCashierName: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (cashierName: String, amount: Double, note: String?) -> Unit
) {
    var cashierName by remember { mutableStateOf(entry?.cashierName ?: defaultCashierName) }
    var amountText by remember { mutableStateOf(entry?.amount?.let(::formatDraftAmount) ?: "") }
    var note by remember { mutableStateOf(entry?.note ?: "") }

    val amount = amountText.toDoubleOrNull()
    val canSave = !isSaving && amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(36.dp)) },
        title = { Text(if (entry == null) "Log Employee Expense" else "Edit Employee Expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cashierName,
                    onValueChange = { cashierName = it },
                    label = { Text("Employee Name (optional)") },
                    placeholder = { Text("e.g. Staff / Cashier name") },
                    singleLine = true,
                    enabled = !lockCashierName,
                    shape = ShapeXS,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary, unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextDark, unfocusedTextColor = TextDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Amount Presets: 100 or 200 PHP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quick:", style = MaterialTheme.typography.labelMedium.copy(color = TextMuted))
                    listOf(100.0, 200.0).forEach { quickAmt ->
                        val isSelected = amount == quickAmt
                        Surface(
                            modifier = Modifier
                                .clip(ShapeSM)
                                .clickable { amountText = quickAmt.toLong().toString() },
                            color = if (isSelected) BrandPrimary else SurfaceContainer,
                            shape = ShapeSM
                        ) {
                            Text(
                                text = "₱${quickAmt.toLong()}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextDark
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = ShapeXS,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary, unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextDark, unfocusedTextColor = TextDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = ShapeXS,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary, unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextDark, unfocusedTextColor = TextDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(cashierName.trim(), amount ?: 0.0, note.trim().ifBlank { null }) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) { Text(if (isSaving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

// ── Store Operational Expense Form Dialog ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationalExpenseFormDialog(
    entry: OperationalExpense?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, note: String?, timestamp: Long) -> Unit
) {
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var amountText by remember { mutableStateOf(entry?.amount?.let(::formatDraftAmount) ?: "") }
    var note by remember { mutableStateOf(entry?.note ?: "") }
    var expenseTimestamp by remember { mutableStateOf(entry?.timestamp ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val displayDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = expenseTimestamp)

    val amount = amountText.toDoubleOrNull()
    val canSave = !isSaving && title.isNotBlank() && amount != null && amount > 0.0

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { expenseTimestamp = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text("Select Expense Date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = { Icon(Icons.Default.LocalShipping, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(36.dp)) },
        title = { Text(if (entry == null) "Log Store Expense" else "Edit Store Expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Title (e.g. Feeds Delivery)") },
                    placeholder = { Text("e.g. Feeds Delivery, Supplies") },
                    singleLine = true,
                    shape = ShapeXS,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary, unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextDark, unfocusedTextColor = TextDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₱)") },
                    placeholder = { Text("e.g. 10000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = ShapeXS,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary, unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextDark, unfocusedTextColor = TextDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Supplier (optional)") },
                    singleLine = true,
                    shape = ShapeXS,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary, unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextDark, unfocusedTextColor = TextDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Picker Surface
                Surface(
                    onClick = { showDatePicker = true },
                    shape = ShapeXS,
                    color = SurfaceContainer,
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Expense Date", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                                Text(displayDateFormat.format(Date(expenseTimestamp)), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                            }
                        }
                        Text("Change", style = MaterialTheme.typography.labelSmall.copy(color = BrandPrimary, fontWeight = FontWeight.Bold))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), amount ?: 0.0, note.trim().ifBlank { null }, expenseTimestamp) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) { Text(if (isSaving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

private fun formatDraftAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
