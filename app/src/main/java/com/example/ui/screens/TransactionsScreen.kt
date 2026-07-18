package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.TransactionItem
import com.example.data.TransactionRecord
import com.example.ui.theme.*
import com.example.viewmodel.POSViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: POSViewModel,
    modifier: Modifier = Modifier
) {
    val filteredTxs by viewModel.filteredTransactions.collectAsState()
    val allTxs by viewModel.transactions.collectAsState()
    val allItems by viewModel.allTransactionItems.collectAsState()
    val unpaidCustomers by viewModel.unpaidCustomers.collectAsState()

    val dateFilter by viewModel.historyDateFilter.collectAsState()
    val statusFilter by viewModel.historyStatusFilter.collectAsState()
    val customerFilter by viewModel.historyCustomerFilter.collectAsState()

    var activeViewTab by remember { mutableStateOf("ALL") } // ALL, UNPAID

    // Sort
    var sortOrder by remember { mutableStateOf("newest") }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")) }
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault()) }
    val dateOnlyFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Dynamic dates list for date filtering
    val availableDates = remember(allTxs) {
        allTxs.map { dateOnlyFormat.format(Date(it.timestamp)) }.distinct().sortedDescending()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            return availableDates.contains(dateOnlyFormat.format(Date(utcTimeMillis)))
        }
        override fun isSelectableYear(year: Int): Boolean = true
    })

    // Customer profile dialog
    var showCustomerProfile by remember { mutableStateOf(false) }
    var customerSearchQuery by remember { mutableStateOf("") }
    var selectedCustomerProfile by remember { mutableStateOf<String?>(null) }

    // Filtered lists depending on ALL vs UNPAID main tab, with sort
    val displayedTxs = remember(filteredTxs, activeViewTab, sortOrder) {
        val base = if (activeViewTab == "UNPAID") {
            filteredTxs.filter { it.status == "UNPAID" }
        } else {
            filteredTxs
        }
        when (sortOrder) {
            "oldest" -> base.sortedBy { it.timestamp }
            "highest" -> base.sortedByDescending { it.totalAmount }
            "overdue" -> base.sortedByDescending { it.timestamp }.sortedBy { if (it.status == "UNPAID") 0 else 1 }
            else -> base // newest first (default from DB)
        }
    }

    // Automatically synchronize the view model status filter when switching main tabs
    LaunchedEffect(activeViewTab) {
        if (activeViewTab == "UNPAID") {
            viewModel.setHistoryFilters(dateFilter, "UNPAID", customerFilter)
        } else {
            viewModel.setHistoryFilters(dateFilter, null, customerFilter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // Screen title
        Text(
            text = "Transaction History",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextDark
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 1. ALL vs UNPAID Toggle Sub-Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceVariant)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (activeViewTab == "ALL") SurfaceLight else Color.Transparent)
                    .clickable { activeViewTab = "ALL" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "All",
                        tint = if (activeViewTab == "ALL") CoralPrimary else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All Records",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (activeViewTab == "ALL") CoralPrimary else TextMuted
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (activeViewTab == "UNPAID") SurfaceLight else Color.Transparent)
                    .clickable { activeViewTab = "UNPAID" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PendingActions,
                        contentDescription = "Unpaid",
                        tint = if (activeViewTab == "UNPAID") ColorUnpaid else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unpaid Tabs",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (activeViewTab == "UNPAID") ColorUnpaid else TextMuted
                        )
                    )
                }
            }
        }

        // ── Unpaid Summary Card (only on UNPAID tab) ──
        if (activeViewTab == "UNPAID") {
            val unpaidTxs = displayedTxs.filter { it.status == "UNPAID" }
            if (unpaidTxs.isNotEmpty()) {
                val totalReceivables = unpaidTxs.sumOf { it.totalAmount }
                val oldestDays = unpaidTxs.maxOfOrNull {
                    ((System.currentTimeMillis() - it.timestamp) / (1000 * 60 * 60 * 24)).toInt()
                } ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorUnpaid.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currencyFormatter.format(totalReceivables).replace("PHP", "₱"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = ColorUnpaid))
                            Text("Total Receivables", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${unpaidTxs.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                            Text("Unpaid Orders", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${oldestDays}d", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = if (oldestDays > 30) ColorUnpaid else TextDark))
                            Text("Oldest", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                        }
                    }
                }
            }
        }

        // ── Unified Toolbar (scrollable chips + fixed sort/clear) ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scrollable chips section
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Date picker
                item {
                    FilterChip(
                        selected = dateFilter != null,
                        onClick = { showDatePicker = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, null, Modifier.size(14.dp), tint = if (dateFilter != null) Color.White else TextMuted)
                                Spacer(Modifier.width(3.dp))
                                Text(if (dateFilter != null) SimpleDateFormat("MMM dd", Locale.getDefault()).format(dateOnlyFormat.parse(dateFilter)!!) else "Dates", fontSize = 10.sp)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CoralPrimary, selectedLabelColor = Color.White, containerColor = BorderLight, labelColor = TextDark),
                        border = null, modifier = Modifier.height(26.dp)
                    )
                }

                // Customer search button (opens profile dialog)
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showCustomerProfile = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PersonSearch, null, Modifier.size(14.dp), tint = TextMuted)
                                Spacer(Modifier.width(3.dp))
                                Text("Customers", fontSize = 10.sp)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(containerColor = BorderLight, labelColor = TextDark),
                        border = null, modifier = Modifier.height(26.dp)
                    )
                }

                // Status chips (ALL tab) or Customer chips (UNPAID tab)
                if (activeViewTab == "ALL") {
                    listOf(null to "All", "PAID" to "Paid", "UNPAID" to "Unpaid").forEach { (key, label) ->
                        item {
                            FilterChip(
                                selected = statusFilter == key,
                                onClick = { viewModel.setHistoryFilters(dateFilter, if (statusFilter == key) null else key, customerFilter) },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (key == "PAID") ColorPaid else if (key == "UNPAID") ColorUnpaid else CoralPrimary,
                                    selectedLabelColor = Color.White, containerColor = BorderLight, labelColor = TextDark
                                ), border = null, modifier = Modifier.height(26.dp)
                            )
                        }
                    }
                } else if (unpaidCustomers.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = customerFilter == null,
                            onClick = { viewModel.setHistoryFilters(dateFilter, statusFilter, null) },
                            label = { Text("All", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CoralPrimary, selectedLabelColor = Color.White, containerColor = BorderLight, labelColor = TextDark),
                            border = null, modifier = Modifier.height(26.dp)
                        )
                    }
                    items(unpaidCustomers) { customer ->
                        FilterChip(
                            selected = customerFilter == customer,
                            onClick = { viewModel.setHistoryFilters(dateFilter, statusFilter, if (customerFilter == customer) null else customer) },
                            label = { Text(customer, fontSize = 10.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ColorUnpaid, selectedLabelColor = Color.White, containerColor = BorderLight, labelColor = TextDark),
                            border = null, modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }

            // Fixed right side: sort + clear
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sortLabel = when (sortOrder) { "oldest" -> "Oldest"; "highest" -> "Amount"; "overdue" -> "Overdue"; else -> "Newest" }
                FilterChip(
                    selected = sortOrder != "newest",
                    onClick = { sortOrder = when (sortOrder) { "newest" -> "oldest"; "oldest" -> "highest"; "highest" -> "overdue"; else -> "newest" } },
                    label = {
                        Row {
                            Icon(Icons.Default.SwapVert, null, Modifier.size(14.dp), tint = if (sortOrder != "newest") Color.White else TextMuted)
                            Spacer(Modifier.width(3.dp))
                            Text(sortLabel, fontSize = 10.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CoralPrimary, selectedLabelColor = Color.White, containerColor = BorderLight, labelColor = TextDark),
                    border = null, modifier = Modifier.height(26.dp)
                )

                if (dateFilter != null || statusFilter != null || customerFilter != null) {
                    Text("Clear", fontSize = 10.sp, color = ColorUnpaid, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.clearHistoryFilters() }.padding(horizontal = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 3. TRANSACTIONS LIST
        if (displayedTxs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = "No Txs",
                        tint = TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No records found",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = TextDark
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try adjusting your filters or date range to see more results.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                items(displayedTxs) { tx ->
                    val txItems = allItems.filter { it.transactionId == tx.id }
                    TransactionCardItem(
                        transaction = tx,
                        items = txItems,
                        currencyFormatter = currencyFormatter,
                        dateFormatter = sdf,
                        onMarkPaid = { viewModel.markTransactionAsPaid(tx.id) },
                        onVoid = { viewModel.voidTransaction(tx) },
                        onDelete = { viewModel.deleteTransactionWithStockRestore(tx) }
                    )
                }
            }
        }

        // ── Date Picker Dialog ──
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val dateStr = dateOnlyFormat.format(Date(selectedMillis))
                            viewModel.setHistoryFilters(dateStr, statusFilter, customerFilter)
                        } else {
                            viewModel.setHistoryFilters(null, statusFilter, customerFilter)
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.setHistoryFilters(null, statusFilter, customerFilter)
                        showDatePicker = false
                    }) { Text("Clear") }
                }
            ) {
                DatePicker(state = datePickerState, title = { Text("Select Date") })
            }
        }

        // ── Customer Profile Dialog ──
        if (showCustomerProfile) {
            val allUnpaid = allTxs.filter { it.status == "UNPAID" && !it.customerName.isNullOrBlank() }
            val searchResults = if (customerSearchQuery.isBlank()) {
                unpaidCustomers
            } else {
                unpaidCustomers.filter { it.contains(customerSearchQuery, ignoreCase = true) }
            }

            Dialog(onDismissRequest = { showCustomerProfile = false; selectedCustomerProfile = null; customerSearchQuery = "" }) {
                Card(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectedCustomerProfile != null) {
                                    IconButton(onClick = { selectedCustomerProfile = null; customerSearchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ArrowBack, "Back", tint = TextDark)
                                    }
                                }
                                Text(
                                    if (selectedCustomerProfile != null) selectedCustomerProfile!! else "Customers",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                                )
                            }
                            IconButton(onClick = {
                                if (selectedCustomerProfile != null) { selectedCustomerProfile = null; customerSearchQuery = "" }
                                else { showCustomerProfile = false }
                            }) { Icon(Icons.Default.Close, "Close", tint = TextDark) }
                        }

                        if (selectedCustomerProfile == null) {
                            // Customer search + list
                            OutlinedTextField(
                                value = customerSearchQuery,
                                onValueChange = { customerSearchQuery = it },
                                placeholder = { Text("Search customer…", color = TextMuted.copy(alpha = 0.6f), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = TextMuted) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CoralPrimary, unfocusedBorderColor = BorderLight,
                                    focusedTextColor = TextDark, unfocusedTextColor = TextDark
                                )
                            )
                            Spacer(Modifier.height(8.dp))

                            if (searchResults.isEmpty()) {
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    Text("No customers found", color = TextMuted)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                                    items(searchResults) { customer ->
                                        val customerTxs = allUnpaid.filter { it.customerName == customer }
                                        val totalDebt = customerTxs.sumOf { it.totalAmount }
                                        val oldest = customerTxs.maxOfOrNull {
                                            ((System.currentTimeMillis() - it.timestamp) / (1000 * 60 * 60 * 24)).toInt()
                                        } ?: 0

                                        Surface(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                                                .clickable { selectedCustomerProfile = customer },
                                            color = SurfaceLight,
                                            shape = RoundedCornerShape(12.dp),
                                            shadowElevation = 1.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(customer, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                                                    Spacer(Modifier.height(2.dp))
                                                    Text("${customerTxs.size} unpaid · oldest ${oldest}d",
                                                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(currencyFormatter.format(totalDebt).replace("PHP", "₱"),
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = ColorUnpaid))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Customer detail: show all their unpaid transactions
                            val customerTxs = allUnpaid.filter { it.customerName == selectedCustomerProfile }
                            val totalDebt = customerTxs.sumOf { it.totalAmount }
                            val oldest = customerTxs.maxOfOrNull {
                                ((System.currentTimeMillis() - it.timestamp) / (1000 * 60 * 60 * 24)).toInt()
                            } ?: 0

                            // Summary
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = ColorUnpaid.copy(alpha = 0.06f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(currencyFormatter.format(totalDebt).replace("PHP", "₱"),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = ColorUnpaid))
                                        Text("Total Debt", fontSize = 10.sp, color = TextMuted)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${customerTxs.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                                        Text("Orders", fontSize = 10.sp, color = TextMuted)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${oldest}d", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = if (oldest > 30) ColorUnpaid else TextDark))
                                        Text("Oldest", fontSize = 10.sp, color = TextMuted)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Transaction list for this customer
                            LazyColumn(
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(customerTxs) { tx ->
                                    val txItems = allItems.filter { it.transactionId == tx.id }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("Order #${tx.id}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark))
                                                Text(currencyFormatter.format(tx.totalAmount).replace("PHP", "₱"),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black, color = ColorUnpaid, fontSize = 13.sp))
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text("${sdf.format(Date(tx.timestamp))} · ${((System.currentTimeMillis() - tx.timestamp) / (1000 * 60 * 60 * 24)).toInt()}d ago",
                                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                            if (txItems.isNotEmpty()) {
                                                Spacer(Modifier.height(6.dp))
                                                HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                                                Spacer(Modifier.height(6.dp))
                                                txItems.forEach { item ->
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("${item.productName} — ${item.variationName}",
                                                            fontSize = 11.sp, color = TextDark, modifier = Modifier.weight(1f))
                                                        Text("× ${item.quantity.toLong()}",
                                                            fontSize = 11.sp, color = TextMuted)
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = { viewModel.markTransactionAsPaid(tx.id) },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = ColorPaid)) {
                                                    Icon(Icons.Default.Payments, null, Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Mark Paid", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionCardItem(
    transaction: TransactionRecord,
    items: List<TransactionItem>,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat,
    onMarkPaid: () -> Unit,
    onVoid: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isVoided = transaction.status == "VOIDED"
    val isUnpaid = transaction.status == "UNPAID"
    val isPaid = transaction.status == "PAID"

    val daysAgo = ((System.currentTimeMillis() - transaction.timestamp) / (1000 * 60 * 60 * 24)).toInt()
    val isOverdue = isUnpaid && daysAgo > 30

    val statusColor = when {
        isVoided -> TextMuted
        isUnpaid -> ColorUnpaid
        else -> ColorPaid
    }

    val borderColor = when {
        isVoided -> TextMuted.copy(alpha = 0.3f)
        isOverdue -> ColorUnpaid.copy(alpha = 0.5f)
        isUnpaid -> ColorUnpaid.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val cardModifier = if (isVoided) {
        Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
    } else if (isUnpaid) {
        Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
    } else {
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isVoided) SurfaceLight.copy(alpha = 0.6f) else SurfaceLight
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top Row (Transaction # & Status tag)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            isVoided -> Icons.Default.Cancel
                            isPaid -> Icons.Default.CheckCircle
                            else -> Icons.Default.Pending
                        },
                        contentDescription = transaction.status,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Order #${transaction.id}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isVoided) TextMuted else TextDark
                        )
                    )
                }

                // Payment Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = transaction.status,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-detail: Customer Name (if Unpaid) + Settled timestamp
            if (isUnpaid && !transaction.customerName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorUnpaid.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Customer", tint = ColorUnpaid, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Customer: ${transaction.customerName}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = ColorUnpaid,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Show settled timestamp if was unpaid and got paid
            if (isPaid && transaction.settledTimestamp != null && transaction.customerName != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorPaid.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Settled", tint = ColorPaid, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Settled: ${dateFormatter.format(Date(transaction.settledTimestamp))}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ColorPaid,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Middle Row (Timestamp & Total amount)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date + aging grouped together on the left
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormatter.format(Date(transaction.timestamp)),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                    if (isUnpaid) {
                        Text(
                            text = " · ${daysAgo}d ago${if (isOverdue) " ⚠️" else ""}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isOverdue) ColorUnpaid else TextMuted,
                                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }

                Text(
                    text = currencyFormatter.format(transaction.totalAmount).replace("PHP", "₱"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = if (isVoided) TextMuted else if (isPaid) TextDark else ColorUnpaid
                    )
                )
            }

            // Expandable Breakdown section
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = BorderLight, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Items Breakdown:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = item.productName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                                )
                                Text(
                                    text = "${item.variationName} × ${if (item.quantity == item.quantity.toLong().toDouble()) item.quantity.toLong().toString() else String.format("%.1f", item.quantity)}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                                )
                            }

                            Text(
                                text = currencyFormatter.format(item.price * item.quantity).replace("PHP", "₱"),
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons row (6.3 + 6.6)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mark as Paid — only for UNPAID transactions (6.3)
                        if (isUnpaid) {
                            TextButton(
                                onClick = onMarkPaid,
                                colors = ButtonDefaults.textButtonColors(contentColor = ColorPaid)
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = "Mark Paid", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mark as Paid", fontSize = 12.sp)
                            }
                        }

                        // Void — for non-voided transactions (6.6)
                        if (!isVoided) {
                            TextButton(
                                onClick = { showVoidConfirm = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "Void", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Void", fontSize = 12.sp)
                            }
                        }

                        // Delete
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = ColorUnpaid)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Small Chevron guide at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    // Void confirmation dialog
    if (showVoidConfirm) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text("Void Transaction?") },
            text = {
                Text("This will mark the transaction as VOIDED and restore all deducted stock. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onVoid()
                        showVoidConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorUnpaid)
                ) {
                    Text("Void Transaction")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text = {
                Text(if (isVoided) {
                    "This will permanently delete this voided transaction."
                } else {
                    "This will delete the transaction and restore all deducted stock. This action cannot be undone."
                })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorUnpaid)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
