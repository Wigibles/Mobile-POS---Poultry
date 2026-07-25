package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Role
import com.example.data.TransactionItem
import com.example.data.TransactionRecord
import com.example.data.shopDateFormat
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
    val currentRole by viewModel.currentRole.collectAsState()
    val isAdmin = currentRole == Role.ADMIN

    val dateFilter by viewModel.historyDateFilter.collectAsState()
    val statusFilter by viewModel.historyStatusFilter.collectAsState()
    val customerFilter by viewModel.historyCustomerFilter.collectAsState()

    var activeViewTab by remember { mutableStateOf("ALL") } // ALL, UNPAID

    // Sort
    var sortOrder by remember { mutableStateOf("newest") }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")) }
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault()) }
    // Pinned to the shop's fixed timezone so the date filter agrees with the ViewModel's
    // own day-bucketing (dashboard, charts) regardless of this device's own clock/timezone.
    val dateOnlyFormat = remember { shopDateFormat("yyyy-MM-dd") }

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

    // Date header formatter — e.g. "July 25, 2026"
    val headerDateFormat = remember { shopDateFormat("MMMM dd, yyyy") }

    // Group transactions by date, assign daily order numbers, and flatten into display list
    val groupedTransactionList = remember(displayedTxs, sortOrder) {
        val grouped = displayedTxs.groupBy { dateOnlyFormat.format(Date(it.timestamp)) }
        val result = mutableListOf<TransactionListItem>()
        val sortedDates = when (sortOrder) {
            "oldest" -> grouped.keys.sorted() // oldest dates first
            else -> grouped.keys.sortedDescending() // newest dates first
        }
        for (date in sortedDates) {
            val dayTxs = grouped[date] ?: continue
            val headerDate = try { dateOnlyFormat.parse(date) } catch (_: Exception) { Date() }
            result.add(TransactionListItem.DateHeader(date, headerDateFormat.format(headerDate)))
            // Assign daily order numbers: oldest transaction of the day = #1
            val sortedDayTxs = dayTxs.sortedBy { it.timestamp }
            sortedDayTxs.forEachIndexed { index, tx ->
                result.add(TransactionListItem.TransactionEntry(tx, index + 1))
            }
        }
        result
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
                .clip(ShapeXL)
                .background(SurfaceContainer)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ShapeLG)
                    .background(if (activeViewTab == "ALL") SurfaceLight else Color.Transparent)
                    .clickable { activeViewTab = "ALL" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "All",
                        tint = if (activeViewTab == "ALL") BrandPrimary else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All Records",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (activeViewTab == "ALL") BrandPrimary else TextMuted
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ShapeLG)
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
                    shape = ShapeSM
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatPeso(currencyFormatter, totalReceivables), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = ColorUnpaid))
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
                                Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp), tint = if (dateFilter != null) Color.White else TextMuted)
                                Spacer(Modifier.width(3.dp))
                                Text(if (dateFilter != null) SimpleDateFormat("MMM dd", Locale.getDefault()).format(dateOnlyFormat.parse(dateFilter)!!) else "Dates", fontSize = 12.sp)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White, containerColor = SurfaceContainer, labelColor = TextDark),
                        border = null, modifier = Modifier.height(32.dp)
                    )
                }

                // Customer search button (opens profile dialog)
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showCustomerProfile = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PersonSearch, null, Modifier.size(16.dp), tint = TextMuted)
                                Spacer(Modifier.width(3.dp))
                                Text("Customers", fontSize = 12.sp)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceContainer, labelColor = TextDark),
                        border = null, modifier = Modifier.height(32.dp)
                    )
                }

                // Status chips (ALL tab) or Customer chips (UNPAID tab)
                if (activeViewTab == "ALL") {
                    listOf(null to "All", "PAID" to "Paid", "UNPAID" to "Unpaid").forEach { (key, label) ->
                        item {
                            FilterChip(
                                selected = statusFilter == key,
                                onClick = { viewModel.setHistoryFilters(dateFilter, if (statusFilter == key) null else key, customerFilter) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (key == "PAID") ColorPaid else if (key == "UNPAID") ColorUnpaid else BrandPrimary,
                                    selectedLabelColor = Color.White, containerColor = SurfaceContainer, labelColor = TextDark
                                ), border = null, modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                } else if (unpaidCustomers.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = customerFilter == null,
                            onClick = { viewModel.setHistoryFilters(dateFilter, statusFilter, null) },
                            label = { Text("All", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White, containerColor = SurfaceContainer, labelColor = TextDark),
                            border = null, modifier = Modifier.height(32.dp)
                        )
                    }
                    items(unpaidCustomers) { customer ->
                        FilterChip(
                            selected = customerFilter == customer,
                            onClick = { viewModel.setHistoryFilters(dateFilter, statusFilter, if (customerFilter == customer) null else customer) },
                            label = { Text(customer, fontSize = 12.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ColorUnpaid, selectedLabelColor = Color.White, containerColor = SurfaceContainer, labelColor = TextDark),
                            border = null, modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            // Fixed right side: sort + clear. Sort opens a menu of named options instead of
            // blindly cycling four hidden states — recognition over recall.
            Row(verticalAlignment = Alignment.CenterVertically) {
                var showSortMenu by remember { mutableStateOf(false) }
                val sortLabel = when (sortOrder) { "oldest" -> "Oldest"; "highest" -> "Amount"; "overdue" -> "Overdue"; else -> "Newest" }
                Box {
                    FilterChip(
                        selected = sortOrder != "newest",
                        onClick = { showSortMenu = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SwapVert, null, Modifier.size(16.dp), tint = if (sortOrder != "newest") Color.White else TextMuted)
                                Spacer(Modifier.width(3.dp))
                                Text(sortLabel, fontSize = 12.sp)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White, containerColor = SurfaceContainer, labelColor = TextDark),
                        border = null, modifier = Modifier.height(32.dp)
                    )
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf(
                            "newest" to "Newest first",
                            "oldest" to "Oldest first",
                            "highest" to "Highest amount",
                            "overdue" to "Overdue first"
                        ).forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontWeight = if (sortOrder == key) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    if (sortOrder == key) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = BrandPrimary)
                                    else Spacer(Modifier.size(18.dp))
                                },
                                onClick = { sortOrder = key; showSortMenu = false }
                            )
                        }
                    }
                }

                if (dateFilter != null || statusFilter != null || customerFilter != null) {
                    TextButton(
                        onClick = { viewModel.clearHistoryFilters() },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Clear", fontSize = 12.sp, color = ColorUnpaid, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 3. TRANSACTIONS LIST — grouped by date with daily order numbers
        if (groupedTransactionList.isEmpty()) {
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
                items(groupedTransactionList) { listItem ->
                    when (listItem) {
                        is TransactionListItem.DateHeader -> {
                            DateHeaderRow(date = listItem.formattedDate)
                        }
                        is TransactionListItem.TransactionEntry -> {
                            val tx = listItem.transaction
                            val txItems = allItems.filter { it.transactionId == tx.id }
                            TransactionCardItem(
                                transaction = tx,
                                items = txItems,
                                dailyNumber = listItem.dailyNumber,
                                currencyFormatter = currencyFormatter,
                                dateFormatter = sdf,
                                isAdmin = isAdmin,
                                onMarkPaid = { viewModel.markTransactionAsPaid(tx.id) },
                                onVoid = { viewModel.voidTransaction(tx) },
                                onDelete = { viewModel.deleteTransactionWithStockRestore(tx) }
                            )
                        }
                    }
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
                    shape = ShapeLG,
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
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextDark)
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
                                shape = ShapeSM,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandPrimary, unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = SurfaceContainer, unfocusedContainerColor = SurfaceContainer,
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
                                                .shadow(elevation = 1.dp, shape = ShapeSM, clip = false)
                                                .clickable { selectedCustomerProfile = customer },
                                            color = SurfaceLight,
                                            shape = ShapeSM
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
                                                    Text(formatPeso(currencyFormatter, totalDebt),
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
                                shape = ShapeSM
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(formatPeso(currencyFormatter, totalDebt),
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

                            // Transaction list for this customer — compute daily order numbers
                            val customerTxsWithDaily = remember(customerTxs) {
                                val byDate = customerTxs.groupBy { dateOnlyFormat.format(Date(it.timestamp)) }
                                val sorted = mutableListOf<Pair<TransactionRecord, Int>>()
                                byDate.keys.sortedDescending().forEach { date ->
                                    val dayTxs = (byDate[date] ?: emptyList()).sortedBy { it.timestamp }
                                    dayTxs.forEachIndexed { index, tx -> sorted.add(tx to index + 1) }
                                }
                                sorted
                            }
                            LazyColumn(
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(customerTxsWithDaily) { (tx, dailyNum) ->
                                    val txItems = allItems.filter { it.transactionId == tx.id }
                                    Card(
                                        modifier = Modifier.fillMaxWidth().shadow(elevation = 1.dp, shape = ShapeSM, clip = false),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                                        shape = ShapeSM
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("Order #$dailyNum", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextDark))
                                                Text(formatPeso(currencyFormatter, tx.totalAmount),
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
    dailyNumber: Int = 0,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat,
    isAdmin: Boolean,
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

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chevronRotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = if (isVoided) 0.dp else 2.dp, shape = ShapeMD, clip = false)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (isVoided) SurfaceLight.copy(alpha = 0.6f) else SurfaceLight
        ),
        shape = ShapeMD,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (isOverdue) androidx.compose.foundation.BorderStroke(1.dp, ColorUnpaid.copy(alpha = 0.35f)) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            )
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
                        text = if (dailyNumber > 0) "Order #$dailyNumber" else "Order #${transaction.id}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isVoided) TextMuted else TextDark
                        )
                    )
                }

                StatusPill(text = transaction.status, color = statusColor)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-detail: Customer Name (if Unpaid) + Settled timestamp
            if (isUnpaid && !transaction.customerName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeXS)
                        .background(ColorUnpaid.copy(alpha = 0.06f))
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
                        .clip(ShapeXS)
                        .background(ColorPaid.copy(alpha = 0.06f))
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
                    text = formatPeso(currencyFormatter, transaction.totalAmount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = if (isVoided) TextMuted else if (isPaid) TextDark else ColorUnpaid
                    )
                )
            }

            // Quick settle — settling a tab is the most frequent action on this screen,
            // so it lives on the collapsed card face instead of hiding behind expand.
            if (isUnpaid && !expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onMarkPaid,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPaid.copy(alpha = 0.12f), contentColor = ColorPaid),
                        shape = ShapeXS,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark as Paid", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
                                text = formatPeso(currencyFormatter, item.price * item.quantity),
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
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Void", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Void", fontSize = 12.sp)
                            }
                        }

                        // Delete — admin only, irreversible data loss
                        if (isAdmin) {
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
            }

            // Small Chevron guide at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp).rotate(chevronRotation)
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
                Text("This will mark the transaction as VOIDED. This action cannot be undone.")
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
                    "This will delete the transaction. This action cannot be undone."
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

/** Sealed interface for items in the transaction history list — either a date header or a transaction entry. */
private sealed interface TransactionListItem {
    data class DateHeader(val dateKey: String, val formattedDate: String) : TransactionListItem
    data class TransactionEntry(val transaction: TransactionRecord, val dailyNumber: Int) : TransactionListItem
}

/** A sticky-looking date header that separates transaction groups by day. */
@Composable
private fun DateHeaderRow(date: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        color = BrandPrimary.copy(alpha = 0.08f),
        shape = ShapeSM
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = BrandPrimary,
                    fontSize = 13.sp
                )
            )
        }
    }
}
