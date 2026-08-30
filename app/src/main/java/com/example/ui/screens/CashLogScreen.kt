package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CashOutEntry
import com.example.data.OperationalExpense
import com.example.data.Role
import com.example.data.shopDateFormat
import com.example.ui.theme.*
import com.example.viewmodel.OperationalCycleSummary
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
    val operationalCycles by viewModel.operationalCycles.collectAsState()
    val isSavingOperational by viewModel.isSavingOperationalExpense.collectAsState()

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

    var showEntryForm by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<CashOutEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<CashOutEntry?>(null) }

    var showOperationalForm by remember { mutableStateOf(false) }
    var editingOperationalEntry by remember { mutableStateOf<OperationalExpense?>(null) }
    var operationalEntryToDelete by remember { mutableStateOf<OperationalExpense?>(null) }

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
                if (operationalCycles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                            shape = ShapeLG,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(ShapeXL).background(BrandPrimary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.LocalShipping, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(32.dp))
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No Store Operational Expenses",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Log a shipment or feeds delivery (e.g. ₱10,000 delivery on Aug 30) to automatically track and compare incoming sales against your delivery & employee costs from that point forward.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
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
                } else {
                    val activeCycle = operationalCycles.firstOrNull { it.isActive } ?: operationalCycles.first()

                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 88.dp)
                    ) {
                        // ── Active Cycle Hero Overview ──
                        item {
                            ActiveCycleHeroCard(
                                cycle = activeCycle,
                                currencyFormatter = currencyFormatter,
                                dateSpanFormat = dateSpanFormat
                            )
                        }

                        item {
                            Text(
                                "Delivery Cycles & Break-Even History",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextDark),
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }

                        // ── All Cycles Breakdown List ──
                        items(operationalCycles) { cycle ->
                            OperationalCycleCard(
                                cycle = cycle,
                                currencyFormatter = currencyFormatter,
                                dateSpanFormat = dateSpanFormat,
                                onEdit = { editingOperationalEntry = cycle.expense; showOperationalForm = true },
                                onDelete = { operationalEntryToDelete = cycle.expense }
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
            onSave = { title, amount, note ->
                val target = editingOperationalEntry
                if (target == null) {
                    viewModel.addOperationalExpense(title, amount, note) { showOperationalForm = false }
                } else {
                    viewModel.updateOperationalExpense(target, title, amount, note, target.timestamp) { showOperationalForm = false }
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
            text = { Text("This will delete this store expense and re-adjust the timeline cycle calculations.") },
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

// ── Active Delivery Cycle Hero Card ──
@Composable
private fun ActiveCycleHeroCard(
    cycle: OperationalCycleSummary,
    currencyFormatter: NumberFormat,
    dateSpanFormat: SimpleDateFormat
) {
    val startDateStr = dateSpanFormat.format(Date(cycle.periodStartTimestamp))
    val isProfitable = cycle.netBalance >= 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = ShapeXL, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeXL
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = BrandPrimary.copy(alpha = 0.12f),
                    shape = ShapeXL
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(ShapeXL).background(BrandPrimary))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "ACTIVE DELIVERY CYCLE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BrandPrimary, fontSize = 11.sp)
                        )
                    }
                }
                Text(
                    "$startDateStr – Present",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                cycle.expense.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark)
            )

            Spacer(Modifier.height(12.dp))

            // ── Net Profit / Balance Badge ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isProfitable) ColorPaid.copy(alpha = 0.08f) else ColorUnpaid.copy(alpha = 0.08f),
                shape = ShapeMD
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (isProfitable) "Net Profit Generated" else "Remaining to Break-Even",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                        Text(
                            formatPeso(currencyFormatter, kotlin.math.abs(cycle.netBalance)),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = if (isProfitable) ColorPaid else ColorUnpaid
                            )
                        )
                    }
                    Surface(
                        color = if (isProfitable) ColorPaid else ColorUnpaid,
                        shape = ShapeXL
                    ) {
                        Text(
                            text = if (isProfitable) "Profitable (${cycle.recoveryRate.toInt()}%)" else "Recovering (${cycle.recoveryRate.toInt()}%)",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Progress Bar ──
            val progressFraction = (cycle.recoveryRate / 100.0).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(ShapeXL),
                color = if (isProfitable) ColorPaid else BrandPrimary,
                trackColor = SurfaceContainer
            )

            Spacer(Modifier.height(14.dp))

            // ── Detailed Grid Breakdown ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Delivery Cost", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Text(formatPeso(currencyFormatter, cycle.expense.amount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                }
                Column {
                    Text("Employee Logs", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Text("+${formatPeso(currencyFormatter, cycle.periodEmployeeExpenses)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sales Generated", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Text(formatPeso(currencyFormatter, cycle.periodSalesTotal), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ColorPaid))
                }
            }
        }
    }
}

// ── Operational Cycle Timeline Card ──
@Composable
private fun OperationalCycleCard(
    cycle: OperationalCycleSummary,
    currencyFormatter: NumberFormat,
    dateSpanFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val startDateStr = dateSpanFormat.format(Date(cycle.periodStartTimestamp))
    val endDateStr = if (cycle.periodEndTimestamp != null) dateSpanFormat.format(Date(cycle.periodEndTimestamp)) else "Present"
    val isProfitable = cycle.netBalance >= 0.0

    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = 2.dp, shape = ShapeMD, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeMD
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (cycle.isActive) Icons.Default.LocalShipping else Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = if (cycle.isActive) BrandPrimary else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        cycle.expense.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = TextDark)
                    )
                }

                Surface(
                    color = if (cycle.isActive) BrandPrimary.copy(alpha = 0.12f) else SurfaceContainer,
                    shape = ShapeXL
                ) {
                    Text(
                        text = if (cycle.isActive) "ACTIVE" else "COMPLETED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (cycle.isActive) BrandPrimary else TextMuted,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "$startDateStr – $endDateStr",
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
            )

            if (!cycle.expense.note.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    cycle.expense.note,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextDark)
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BorderLight)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Cycle Cost", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Text(formatPeso(currencyFormatter, cycle.totalPeriodCost), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Cycle Sales (${cycle.periodOrderCount})", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Text(formatPeso(currencyFormatter, cycle.periodSalesTotal), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = BrandPrimary))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (isProfitable) "Net Gain" else "Balance", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Text(
                        "${if (isProfitable) "+" else ""}${formatPeso(currencyFormatter, cycle.netBalance)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = if (isProfitable) ColorPaid else ColorUnpaid)
                    )
                }
            }

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
@Composable
private fun OperationalExpenseFormDialog(
    entry: OperationalExpense?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, note: String?) -> Unit
) {
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var amountText by remember { mutableStateOf(entry?.amount?.let(::formatDraftAmount) ?: "") }
    var note by remember { mutableStateOf(entry?.note ?: "") }

    val amount = amountText.toDoubleOrNull()
    val canSave = !isSaving && title.isNotBlank() && amount != null && amount > 0.0

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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), amount ?: 0.0, note.trim().ifBlank { null }) },
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
