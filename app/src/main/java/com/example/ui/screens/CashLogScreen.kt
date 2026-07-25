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
import com.example.data.BorrowEntry
import com.example.data.CashOutEntry
import com.example.data.Role
import com.example.data.shopDateFormat
import com.example.ui.theme.*
import com.example.viewmodel.POSViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashLogScreen(viewModel: POSViewModel, modifier: Modifier = Modifier) {
    var activeTab by remember { mutableStateOf("EXPENSES") } // EXPENSES, BORROWING

    // ── Employee Expenses state ──
    val entries by viewModel.filteredCashOutEntries.collectAsState()
    val allEntries by viewModel.cashOutEntries.collectAsState()
    val todayTotal by viewModel.todayExpenseTotal.collectAsState()
    val dateFilter by viewModel.cashLogDateFilter.collectAsState()
    val isSavingExpense by viewModel.isSavingCashOut.collectAsState()

    // ── Family Borrowing state ──
    val borrowEntries by viewModel.borrowEntries.collectAsState()
    val borrowStats by viewModel.borrowStats.collectAsState()
    val isSavingBorrow by viewModel.isSavingBorrow.collectAsState()

    val currentRole by viewModel.currentRole.collectAsState()
    val currentCashierName by viewModel.currentCashierName.collectAsState()
    val isAdmin = currentRole == Role.ADMIN

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")) }
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault()) }
    // Pinned to the shop's fixed timezone so the date filter agrees with the ViewModel's
    // own day-bucketing (dashboard, charts, transaction history).
    val dateOnlyFormat = remember { shopDateFormat("yyyy-MM-dd") }

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

    var showBorrowForm by remember { mutableStateOf(false) }
    var editingBorrowEntry by remember { mutableStateOf<BorrowEntry?>(null) }
    var borrowEntryToDelete by remember { mutableStateOf<BorrowEntry?>(null) }

    fun canModify(entry: CashOutEntry) =
        isAdmin || (currentRole == Role.CASHIER && entry.cashierName == currentCashierName)

    Box(modifier = modifier.fillMaxSize().background(BackgroundLight)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Money Log",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextDark),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Employee Expenses / Family Borrowing Toggle ──
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
                        .background(if (activeTab == "EXPENSES") SurfaceLight else Color.Transparent)
                        .clickable { activeTab = "EXPENSES" }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Employee Expenses",
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == "EXPENSES") BrandPrimary else TextMuted
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ShapeLG)
                        .background(if (activeTab == "BORROWING") SurfaceLight else Color.Transparent)
                        .clickable { activeTab = "BORROWING" }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cash Borrowing",
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == "BORROWING") ColorUnpaid else TextMuted
                        )
                    )
                }
            }

            if (activeTab == "EXPENSES") {
                // ── Hero Stat — Today's Employee Expenses ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(elevation = 10.dp, shape = ShapeXL, clip = false),
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

                Spacer(Modifier.height(12.dp))

                // ── Date Filter ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = dateFilter != null,
                        onClick = { showDatePicker = true },
                        label = {
                            Text(
                                if (dateFilter != null) SimpleDateFormat("MMM dd", Locale.getDefault()).format(dateOnlyFormat.parse(dateFilter)!!) else "All Dates",
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp), tint = if (dateFilter != null) Color.White else TextMuted) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White,
                            containerColor = SurfaceContainer, labelColor = TextDark
                        ),
                        border = null
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No expenses logged yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
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
                // ── Family Borrowing Summary ──
                if (borrowStats.outstandingCount > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorUnpaid.copy(alpha = 0.08f)),
                        shape = ShapeSM
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatPeso(currencyFormatter, borrowStats.totalOutstanding), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = ColorUnpaid))
                                Text("Total Outstanding", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${borrowStats.outstandingCount}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                                Text("Open Borrows", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${borrowStats.oldestDays}d", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = if (borrowStats.oldestDays > 30) ColorUnpaid else TextDark))
                                Text("Oldest", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                if (borrowEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No borrows logged yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                    ) {
                        items(borrowEntries, key = { it.id }) { entry ->
                            BorrowEntryCard(
                                entry = entry,
                                currencyFormatter = currencyFormatter,
                                dateFormatter = sdf,
                                onMarkReturned = { viewModel.markBorrowReturned(entry) },
                                onEdit = { editingBorrowEntry = entry; showBorrowForm = true },
                                onDelete = { borrowEntryToDelete = entry }
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                if (activeTab == "EXPENSES") {
                    editingEntry = null; showEntryForm = true
                } else {
                    editingBorrowEntry = null; showBorrowForm = true
                }
            },
            containerColor = if (activeTab == "EXPENSES") BrandPrimary else ColorUnpaid,
            contentColor = Color.White,
            shape = ShapeMD,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (activeTab == "EXPENSES") "Log Expense" else "Log Borrow", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    // ── Date Picker Dialog (Expenses tab) ──
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

    // ── Expense Add/Edit Dialog ──
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

    // ── Expense Delete Confirmation ──
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

    // ── Borrow Add/Edit Dialog ──
    if (showBorrowForm) {
        BorrowEntryFormDialog(
            entry = editingBorrowEntry,
            isSaving = isSavingBorrow,
            onDismiss = { showBorrowForm = false },
            onSave = { borrowerName, amount, note ->
                val target = editingBorrowEntry
                if (target == null) {
                    viewModel.addBorrowEntry(borrowerName, amount, note) { showBorrowForm = false }
                } else {
                    viewModel.updateBorrowEntry(target.copy(borrowerName = borrowerName), amount, note) { showBorrowForm = false }
                }
            }
        )
    }

    // ── Borrow Delete Confirmation ──
    val borrowToDelete = borrowEntryToDelete
    if (borrowToDelete != null) {
        AlertDialog(
            onDismissRequest = { borrowEntryToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ColorUnpaid, modifier = Modifier.size(36.dp)) },
            title = { Text("Delete Borrow Entry?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete this borrow entry. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteBorrowEntry(borrowToDelete); borrowEntryToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { borrowEntryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

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
                    Text(entry.cashierName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
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
    val canSave = !isSaving && cashierName.isNotBlank() && amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(36.dp)) },
        title = { Text(if (entry == null) "Log Expense" else "Edit Expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cashierName,
                    onValueChange = { cashierName = it },
                    label = { Text("Cashier Name") },
                    singleLine = true,
                    enabled = !lockCashierName,
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
                    label = { Text("Amount") },
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

@Composable
private fun BorrowEntryCard(
    entry: BorrowEntry,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat,
    onMarkReturned: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isReturned = entry.returnedTimestamp != null
    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = if (isReturned) 0.dp else 2.dp, shape = ShapeMD, clip = false),
        colors = CardDefaults.cardColors(containerColor = if (isReturned) SurfaceLight.copy(alpha = 0.6f) else SurfaceLight),
        shape = ShapeMD,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = if (isReturned) TextMuted else BrandPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        entry.borrowerName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (isReturned) TextMuted else TextDark)
                    )
                }
                StatusPill(text = if (isReturned) "Returned" else "Outstanding", color = if (isReturned) ColorPaid else ColorUnpaid)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormatter.format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                Text(
                    formatPeso(currencyFormatter, entry.amount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = if (isReturned) TextMuted else ColorUnpaid)
                )
            }
            if (!entry.note.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(entry.note, style = MaterialTheme.typography.bodySmall.copy(color = TextDark))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isReturned) {
                    TextButton(onClick = onMarkReturned, colors = ButtonDefaults.textButtonColors(contentColor = ColorPaid)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Mark Returned", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark Returned", fontSize = 12.sp)
                    }
                }
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

@Composable
private fun BorrowEntryFormDialog(
    entry: BorrowEntry?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (borrowerName: String, amount: Double, note: String?) -> Unit
) {
    var borrowerName by remember { mutableStateOf(entry?.borrowerName ?: "") }
    var amountText by remember { mutableStateOf(entry?.amount?.let(::formatDraftAmount) ?: "") }
    var note by remember { mutableStateOf(entry?.note ?: "") }

    val amount = amountText.toDoubleOrNull()
    val canSave = !isSaving && borrowerName.isNotBlank() && amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = { Icon(Icons.Default.Groups, contentDescription = null, tint = ColorUnpaid, modifier = Modifier.size(36.dp)) },
        title = { Text(if (entry == null) "Log Borrow" else "Edit Borrow", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = borrowerName,
                    onValueChange = { borrowerName = it },
                    label = { Text("Borrower Name") },
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
                    label = { Text("Amount") },
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
                onClick = { onSave(borrowerName.trim(), amount ?: 0.0, note.trim().ifBlank { null }) },
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
