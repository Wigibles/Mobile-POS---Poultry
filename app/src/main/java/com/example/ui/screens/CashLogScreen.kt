package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var selectedCycleForBreakdown by remember { mutableStateOf<OperationalCycleSummary?>(null) }

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
                        contentPadding = PaddingValues(top = 6.dp, bottom = 128.dp)
                    ) {
                        // ── Active Cycle Hero Overview ──
                        item {
                            ActiveCycleHeroCard(
                                cycle = activeCycle,
                                currencyFormatter = currencyFormatter,
                                dateSpanFormat = dateSpanFormat,
                                onViewBreakdown = { selectedCycleForBreakdown = activeCycle }
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
                                onViewBreakdown = { selectedCycleForBreakdown = cycle },
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

    // ── Cycle Financial Breakdown & Employee Logs Bottom Sheet ──
    val cycleToBreakdown = selectedCycleForBreakdown
    if (cycleToBreakdown != null) {
        CycleBreakdownBottomSheet(
            cycle = cycleToBreakdown,
            allEmployeeEntries = allEntries,
            currencyFormatter = currencyFormatter,
            dateSpanFormat = dateSpanFormat,
            onDismiss = { selectedCycleForBreakdown = null }
        )
    }
}

// ── Active Delivery Cycle Hero Card ──
@Composable
private fun ActiveCycleHeroCard(
    cycle: OperationalCycleSummary,
    currencyFormatter: NumberFormat,
    dateSpanFormat: SimpleDateFormat,
    onViewBreakdown: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val startDateStr = dateSpanFormat.format(Date(cycle.periodStartTimestamp))
    val isProfitable = cycle.isProfitable

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = ShapeXL, clip = false),
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = TextDark, fontSize = 20.sp)
            )

            Spacer(Modifier.height(12.dp))

            // ── Net Profit / Balance Hero Box ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isProfitable) ColorPaid.copy(alpha = 0.08f) else ColorUnpaid.copy(alpha = 0.08f),
                shape = ShapeMD
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (isProfitable) "Net Profit Generated" else "Remaining to Break-Even",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Medium)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatPeso(currencyFormatter, kotlin.math.abs(cycle.netBalance)),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = if (isProfitable) ColorPaid else ColorUnpaid,
                                fontSize = 24.sp
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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

            // ── Enterprise 3-Column Metrics Grid ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Feeds Delivery", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Spacer(Modifier.height(2.dp))
                    Text(formatPeso(currencyFormatter, cycle.storeExpense), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Employee Logs", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Spacer(Modifier.height(2.dp))
                    Text("+${formatPeso(currencyFormatter, cycle.periodEmployeeExpenses)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ChartHighlight))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sales Generated", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                    Spacer(Modifier.height(2.dp))
                    Text(formatPeso(currencyFormatter, cycle.periodSalesTotal), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ColorPaid))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Action Row: Financial Report & Chart Peek ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onViewBreakdown,
                    shape = ShapeSM,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BrandPrimary.copy(alpha = 0.12f),
                        contentColor = BrandPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Financial Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    colors = ButtonDefaults.textButtonColors(contentColor = BrandPrimary)
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isExpanded) "Hide Chart" else "Quick Chart", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    CycleProfitabilityChart(
                        cycle = cycle,
                        currencyFormatter = currencyFormatter
                    )
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
    onViewBreakdown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val startDateStr = dateSpanFormat.format(Date(cycle.periodStartTimestamp))
    val endDateStr = if (cycle.periodEndTimestamp != null) dateSpanFormat.format(Date(cycle.periodEndTimestamp)) else "Present"
    val isProfitable = cycle.isProfitable

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = ShapeMD, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeMD
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Icon, Title, Date Span, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(ShapeMD)
                            .background(if (cycle.isActive) BrandPrimary.copy(alpha = 0.1f) else SurfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (cycle.isActive) Icons.Default.LocalShipping else Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = if (cycle.isActive) BrandPrimary else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            cycle.expense.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                        )
                        Text(
                            "$startDateStr – $endDateStr",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                    }
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!cycle.expense.note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = SurfaceContainer.copy(alpha = 0.6f),
                    shape = ShapeXS
                ) {
                    Text(
                        cycle.expense.note,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BorderLight)
            Spacer(Modifier.height(12.dp))

            // ── Primary 4-Metric Enterprise Tiles ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Feeds Delivery Tile
                Surface(
                    modifier = Modifier.weight(1f),
                    color = SurfaceContainer.copy(alpha = 0.55f),
                    shape = ShapeSM
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Feeds Delivery", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text(formatPeso(currencyFormatter, cycle.storeExpense), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark), maxLines = 1)
                    }
                }

                // Employee Logs Tile
                Surface(
                    modifier = Modifier.weight(1f),
                    color = ChartHighlight.copy(alpha = 0.08f),
                    shape = ShapeSM
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Employee Logs", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text("+${formatPeso(currencyFormatter, cycle.periodEmployeeExpenses)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark), maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cycle Sales Tile
                Surface(
                    modifier = Modifier.weight(1f),
                    color = SurfaceContainer.copy(alpha = 0.55f),
                    shape = ShapeSM
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Cycle Sales (${cycle.periodOrderCount})", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text(formatPeso(currencyFormatter, cycle.periodSalesTotal), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = BrandPrimary), maxLines = 1)
                    }
                }

                // Net Gain Tile
                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (isProfitable) ColorPaid.copy(alpha = 0.1f) else ColorUnpaid.copy(alpha = 0.1f),
                    shape = ShapeSM
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(if (isProfitable) "Net Gain" else "Balance", style = MaterialTheme.typography.labelSmall.copy(color = if (isProfitable) ColorPaid else ColorUnpaid, fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${if (isProfitable) "+" else ""}${formatPeso(currencyFormatter, cycle.netGain)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black, color = if (isProfitable) ColorPaid else ColorUnpaid),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Inline Chart Peek Accordion ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CycleProfitabilityChart(
                        cycle = cycle,
                        currencyFormatter = currencyFormatter
                    )
                }
            }

            // ── Action Bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onViewBreakdown,
                        shape = ShapeSM,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandPrimary.copy(alpha = 0.12f),
                            contentColor = BrandPrimary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Financial Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.width(6.dp))

                    IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.BarChart,
                            contentDescription = "Toggle Chart",
                            tint = BrandPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ColorUnpaid, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── Cycle Profitability & Financial Chart (Mobile Enterprise Grade) ──
@Composable
private fun CycleProfitabilityChart(
    cycle: OperationalCycleSummary,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val totalCost = cycle.totalPeriodCost
    val feedsExpense = cycle.storeExpense
    val empExpense = cycle.periodEmployeeExpenses
    val sales = cycle.periodSalesTotal
    val netGain = cycle.netGain
    val isProfitable = cycle.isProfitable

    val feedsPct = if (totalCost > 0) (feedsExpense / totalCost) * 100.0 else 100.0
    val empPct = if (totalCost > 0) (empExpense / totalCost) * 100.0 else 0.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeLG)
            .background(SurfaceContainer.copy(alpha = 0.45f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. Header & Profit Verdict ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    "Profit & Expense Analysis",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                )
                Text(
                    if (isProfitable) "Cycle generated net operating profit" else "Cycle in cost recovery phase",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                )
            }
            Surface(
                color = if (isProfitable) ColorPaid.copy(alpha = 0.15f) else ColorUnpaid.copy(alpha = 0.15f),
                shape = ShapeXL
            ) {
                Text(
                    text = if (isProfitable) "Profitable (+${String.format(Locale.US, "%.1f", cycle.profitMargin)}%)" else "Deficit (${cycle.recoveryRate.toInt()}%)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isProfitable) ColorPaid else ColorUnpaid,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // ── 2. Expense Split / Ratio Bar (Feeds Delivery vs Employee Logs) ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Expense Distribution",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold)
                )
                Text(
                    "Total Cost: ${formatPeso(currencyFormatter, totalCost)}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextDark, fontWeight = FontWeight.Bold)
                )
            }

            // Segmented proportion bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(ShapeXL)
                    .background(SurfaceContainerHigh)
            ) {
                if (totalCost > 0) {
                    val feedsWeight = (feedsExpense / totalCost).coerceIn(0.01, 0.99).toFloat()
                    val empWeight = (empExpense / totalCost).coerceIn(0.01, 0.99).toFloat()

                    if (feedsExpense > 0) {
                        Box(
                            modifier = Modifier
                                .weight(feedsWeight)
                                .fillMaxHeight()
                                .background(BrandPrimary)
                        )
                    }
                    if (empExpense > 0) {
                        Box(
                            modifier = Modifier
                                .weight(empWeight)
                                .fillMaxHeight()
                                .background(ChartHighlight)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(SurfaceContainerHigh))
                }
            }

            // Two-column side-by-side tiles for legend (clean, no wrapping/clipping!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = SurfaceLight,
                    shape = ShapeMD
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(ShapeXL).background(BrandPrimary))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Feeds Delivery", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                                Spacer(Modifier.width(4.dp))
                                Text("(${String.format(Locale.US, "%.0f", feedsPct)}%)", style = MaterialTheme.typography.labelSmall.copy(color = BrandPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                            }
                            Text(formatPeso(currencyFormatter, feedsExpense), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark), maxLines = 1)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = SurfaceLight,
                    shape = ShapeMD
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(ShapeXL).background(ChartHighlight))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Employee Logs", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp), maxLines = 1)
                                Spacer(Modifier.width(4.dp))
                                Text("(${String.format(Locale.US, "%.0f", empPct)}%)", style = MaterialTheme.typography.labelSmall.copy(color = ChartHighlight, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                            }
                            Text(formatPeso(currencyFormatter, empExpense), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark), maxLines = 1)
                        }
                    }
                }
            }
        }

        // ── 3. Comparative Bar Chart (Equal-width columns, never truncates!) ──
        val maxVal = maxOf(feedsExpense, empExpense, sales, kotlin.math.abs(netGain), 100.0)

        Surface(
            color = SurfaceLight,
            shape = ShapeMD,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Cash Flow & Profitability Comparison",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    ChartBarColumn(
                        modifier = Modifier.weight(1f),
                        label = "Feeds",
                        icon = "🚚",
                        amount = feedsExpense,
                        maxAmount = maxVal,
                        barBrush = Brush.verticalGradient(listOf(BrandPrimary, BrandSecondary))
                    )

                    ChartBarColumn(
                        modifier = Modifier.weight(1f),
                        label = "Labor",
                        icon = "👤",
                        amount = empExpense,
                        maxAmount = maxVal,
                        barBrush = Brush.verticalGradient(listOf(ChartHighlight, Color(0xFFFFD580)))
                    )

                    ChartBarColumn(
                        modifier = Modifier.weight(1f),
                        label = "Sales",
                        icon = "🛒",
                        amount = sales,
                        maxAmount = maxVal,
                        barBrush = Brush.verticalGradient(listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)))
                    )

                    ChartBarColumn(
                        modifier = Modifier.weight(1f),
                        label = if (isProfitable) "Profit" else "Deficit",
                        icon = if (isProfitable) "📈" else "📉",
                        amount = netGain,
                        maxAmount = maxVal,
                        barBrush = if (isProfitable)
                            Brush.verticalGradient(listOf(ColorPaid, Color(0xFF81C784)))
                        else
                            Brush.verticalGradient(listOf(ColorUnpaid, Color(0xFFE57373))),
                        isNetGain = true
                    )
                }
            }
        }

        // ── 4. Waterfall P&L Breakdown Table (No Amount Wrapping!) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeMD)
                .background(SurfaceLight)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Cycle Financial Statement",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold)
            )
            HorizontalDivider(color = BorderLight)

            BreakdownLedgerRow(
                label = "Gross Sales Revenue (${cycle.periodOrderCount} orders)",
                amountStr = "+${formatPeso(currencyFormatter, sales)}",
                color = ColorPaid
            )

            BreakdownLedgerRow(
                label = "(-) Feeds / Store Delivery",
                amountStr = "-${formatPeso(currencyFormatter, feedsExpense)}",
                color = TextDark
            )

            BreakdownLedgerRow(
                label = "(=) Gross Operating Profit",
                amountStr = "${if (cycle.grossProfit >= 0) "+" else ""}${formatPeso(currencyFormatter, cycle.grossProfit)}",
                color = if (cycle.grossProfit >= 0) BrandPrimary else ColorUnpaid,
                isBold = true
            )

            BreakdownLedgerRow(
                label = "(-) Employee Wages & Logs",
                amountStr = "-${formatPeso(currencyFormatter, empExpense)}",
                color = ChartHighlight
            )

            Spacer(Modifier.height(4.dp))

            // Highlighted Net Gain Executive Box (Guaranteed Single-Line!)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isProfitable) ColorPaid.copy(alpha = 0.08f) else ColorUnpaid.copy(alpha = 0.08f),
                shape = ShapeMD
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isProfitable) "(=) Net Profit Pocketed" else "(=) Net Deficit / Balance",
                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) ColorPaid else ColorUnpaid
                        )
                    )
                    Text(
                        "${if (isProfitable) "+" else ""}${formatPeso(currencyFormatter, netGain)}",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = if (isProfitable) ColorPaid else ColorUnpaid,
                            fontSize = 16.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartBarColumn(
    modifier: Modifier = Modifier,
    label: String,
    icon: String,
    amount: Double,
    maxAmount: Double,
    barBrush: Brush,
    isNetGain: Boolean = false
) {
    val absAmount = kotlin.math.abs(amount)
    val heightFraction = if (maxAmount > 0) (absAmount / maxAmount).toFloat().coerceIn(0.08f, 1f) else 0.08f
    val totalRailHeightDp = 80.dp
    val barHeightDp = (totalRailHeightDp * heightFraction).coerceIn(8.dp, totalRailHeightDp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier
    ) {
        Text(
            text = formatCompactAmount(amount, isNetGain),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (isNetGain) (if (amount >= 0) ColorPaid else ColorUnpaid) else TextDark
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(6.dp))

        // Background rail track + styled bar
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(totalRailHeightDp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(SurfaceContainerHigh.copy(alpha = 0.35f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeightDp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(barBrush)
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 11.sp)
            Spacer(Modifier.width(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = TextDark,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

private fun formatCompactAmount(amount: Double, isNetGain: Boolean = false): String {
    val abs = kotlin.math.abs(amount)
    val prefix = if (amount < 0) "-₱" else if (isNetGain && amount > 0) "+₱" else "₱"
    return when {
        abs >= 1_000_000 -> "$prefix${String.format(Locale.US, "%.1fM", abs / 1_000_000)}"
        abs >= 1_000 -> "$prefix${String.format(Locale.US, "%.1fk", abs / 1_000)}"
        else -> "$prefix${String.format(Locale.US, "%.0f", abs)}"
    }
}

@Composable
private fun BreakdownLedgerRow(
    label: String,
    amountStr: String,
    color: Color,
    isBold: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextDark,
                fontSize = fontSize,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            )
        )
        Text(
            amountStr,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall.copy(
                color = color,
                fontSize = fontSize,
                fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold
            )
        )
    }
}

// ── Full-Width Mobile Enterprise Modal Bottom Sheet ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleBreakdownBottomSheet(
    cycle: OperationalCycleSummary,
    allEmployeeEntries: List<CashOutEntry>,
    currencyFormatter: NumberFormat,
    dateSpanFormat: SimpleDateFormat,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val startDateStr = dateSpanFormat.format(Date(cycle.periodStartTimestamp))
    val endDateStr = if (cycle.periodEndTimestamp != null) dateSpanFormat.format(Date(cycle.periodEndTimestamp)) else "Present"
    val sdfFull = remember { SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault()) }

    val cycleEmpEntries = remember(cycle, allEmployeeEntries) {
        allEmployeeEntries.filter { entry ->
            entry.timestamp >= cycle.periodStartTimestamp &&
            (cycle.periodEndTimestamp == null || entry.timestamp < cycle.periodEndTimestamp)
        }.sortedByDescending { it.timestamp }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundLight,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Top Header with Dismiss Action ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            cycle.expense.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = TextDark,
                                fontSize = 22.sp
                            )
                        )
                        Spacer(Modifier.width(8.dp))
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
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp)
                    )
                    if (!cycle.expense.note.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Supplier: ${cycle.expense.note}",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(ShapeXL)
                        .background(SurfaceContainer)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextDark, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = BorderLight)

            // ── Executive Hero Card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                shape = ShapeLG,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (cycle.isProfitable) "NET PROFIT POCKETED" else "CYCLE REMAINING DEFICIT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.sp
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${if (cycle.isProfitable) "+" else ""}${formatPeso(currencyFormatter, cycle.netGain)}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (cycle.isProfitable) ColorPaid else ColorUnpaid,
                                    fontSize = 28.sp
                                )
                            )
                        }

                        Surface(
                            color = if (cycle.isProfitable) ColorPaid.copy(alpha = 0.12f) else ColorUnpaid.copy(alpha = 0.12f),
                            shape = ShapeXL
                        ) {
                            Text(
                                text = if (cycle.isProfitable) "Profitable (+${String.format(Locale.US, "%.1f", cycle.profitMargin)}%)" else "Deficit (${cycle.recoveryRate.toInt()}%)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (cycle.isProfitable) ColorPaid else ColorUnpaid,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = BorderLight.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))

                    // 3 KPI Pills Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Sales", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                            Text(formatPeso(currencyFormatter, cycle.periodSalesTotal), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Orders", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                            Text("${cycle.periodOrderCount}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Cost", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                            Text(formatPeso(currencyFormatter, cycle.totalPeriodCost), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        }
                    }
                }
            }

            // ── Interactive Responsive Chart & Ledger ──
            CycleProfitabilityChart(
                cycle = cycle,
                currencyFormatter = currencyFormatter
            )

            // ── Itemized Employee Expenses in this Cycle ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Employee Expenses Logged",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark, fontSize = 16.sp)
                    )
                    Surface(
                        color = ChartHighlight.copy(alpha = 0.15f),
                        shape = ShapeXL
                    ) {
                        Text(
                            "${cycleEmpEntries.size} entries • ${formatPeso(currencyFormatter, cycle.periodEmployeeExpenses)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (cycleEmpEntries.isEmpty()) {
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
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No employee expenses recorded during this cycle.", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                            }
                        }
                    }
                } else {
                    cycleEmpEntries.forEach { empEntry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                            shape = ShapeMD,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(ShapeMD)
                                            .background(ChartHighlight.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = TextDark, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            if (empEntry.cashierName.isNotBlank()) empEntry.cashierName else "Employee Cash Out",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                                        )
                                        Text(
                                            sdfFull.format(Date(empEntry.timestamp)),
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                                        )
                                        if (!empEntry.note.isNullOrBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Surface(
                                                color = SurfaceContainer,
                                                shape = ShapeXS
                                            ) {
                                                Text(
                                                    empEntry.note,
                                                    style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontSize = 11.sp),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    formatPeso(currencyFormatter, empEntry.amount),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = ColorUnpaid
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = ShapeMD,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Done", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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
