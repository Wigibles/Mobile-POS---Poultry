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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Role
import com.example.data.TimeHorizon
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

    val startDateFilter by viewModel.historyStartDateFilter.collectAsState()
    val endDateFilter by viewModel.historyEndDateFilter.collectAsState()
    val dateFilter by viewModel.historyDateFilter.collectAsState()
    val statusFilter by viewModel.historyStatusFilter.collectAsState()
    val customerFilter by viewModel.historyCustomerFilter.collectAsState()
    val historyHorizon by viewModel.historyHorizon.collectAsState()
    val rangeSummary by viewModel.historyRangeSummary.collectAsState()

    var activeViewTab by remember { mutableStateOf("ALL") } // ALL, UNPAID

    // Sort
    var sortOrder by remember { mutableStateOf("newest") }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")) }
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault()) }
    // Pinned to the shop's fixed timezone so the date filter agrees with the ViewModel's
    // own day-bucketing (dashboard, charts) regardless of this device's own clock/timezone.
    val dateOnlyFormat = remember { shopDateFormat("yyyy-MM-dd") }

    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    // Customer profile dialog
    var showCustomerProfile by remember { mutableStateOf(false) }
    var customerSearchQuery by remember { mutableStateOf("") }
    var selectedCustomerProfile by remember { mutableStateOf<String?>(null) }

    var collapsedDates by remember { mutableStateOf(setOf<String>()) }

    // Filtered lists with sort
    val displayedTxs = remember(filteredTxs, sortOrder) {
        when (sortOrder) {
            "oldest" -> filteredTxs.sortedBy { it.timestamp }
            "highest" -> filteredTxs.sortedByDescending { it.totalAmount }
            "overdue" -> filteredTxs.sortedByDescending { it.timestamp }.sortedBy { if (it.status == "UNPAID") 0 else 1 }
            else -> filteredTxs.sortedByDescending { it.timestamp } // newest first
        }
    }

    // Date header formatter — e.g. "July 25, 2026"
    val headerDateFormat = remember { shopDateFormat("MMMM dd, yyyy") }

    // Group transactions by date, assign chronological daily order numbers, and sort descending (last log on top)
    val groupedTransactionList = remember(displayedTxs, sortOrder, collapsedDates) {
        val grouped = displayedTxs.groupBy { dateOnlyFormat.format(Date(it.timestamp)) }
        val result = mutableListOf<TransactionListItem>()
        val sortedDates = when (sortOrder) {
            "oldest" -> grouped.keys.sorted() // oldest dates first
            else -> grouped.keys.sortedDescending() // newest dates first
        }
        for (date in sortedDates) {
            val dayTxs = grouped[date] ?: continue
            val headerDate = try { dateOnlyFormat.parse(date) } catch (_: Exception) { Date() }
            val isCollapsed = collapsedDates.contains(date)
            val dayTotal = dayTxs.filter { it.status != "VOIDED" }.sumOf { it.totalAmount }

            result.add(
                TransactionListItem.DateHeader(
                    dateKey = date,
                    formattedDate = headerDateFormat.format(headerDate),
                    orderCount = dayTxs.size,
                    dayTotal = dayTotal,
                    isCollapsed = isCollapsed
                )
            )

            if (!isCollapsed) {
                // Determine chronological sequence numbers (#1 = first order of day, #N = last order of day)
                val chronologicalDayTxs = dayTxs.sortedBy { it.timestamp }
                val txOrderMap = chronologicalDayTxs.mapIndexed { index, tx -> tx.id to (index + 1) }.toMap()

                // Sort descending so the newest/last log is displayed at the top
                val sortedDayTxs = when (sortOrder) {
                    "oldest" -> dayTxs.sortedBy { it.timestamp }
                    "highest" -> dayTxs.sortedByDescending { it.totalAmount }
                    "overdue" -> dayTxs.sortedByDescending { it.timestamp }.sortedBy { if (it.status == "UNPAID") 0 else 1 }
                    else -> dayTxs.sortedByDescending { it.timestamp } // newest/last log at top
                }

                sortedDayTxs.forEach { tx ->
                    result.add(TransactionListItem.TransactionEntry(tx, txOrderMap[tx.id] ?: 1))
                }
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // ── Time Horizon Presets Row ──
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                TimeHorizon.ALL to "All",
                TimeHorizon.DAY to "Day",
                TimeHorizon.WEEK to "Week",
                TimeHorizon.THIRTY_DAYS to "30D",
                TimeHorizon.MTD to "MTD"
            ).forEach { (horizon, label) ->
                item {
                    val isSelected = historyHorizon == horizon && dateFilter == null
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setHistoryHorizon(horizon) },
                        label = {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceContainer,
                            labelColor = TextDark
                        ),
                        border = null,
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            // Custom Date Range Picker chip
            item {
                val isCustom = historyHorizon == TimeHorizon.CUSTOM && startDateFilter != null
                FilterChip(
                    selected = isCustom,
                    onClick = { showDateRangePicker = true },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isCustom) Color.White else TextMuted
                            )
                            Spacer(Modifier.width(4.dp))
                            val rangeChipLabel = if (isCustom && !startDateFilter.isNullOrBlank()) {
                                val s = try { SimpleDateFormat("MMM d", Locale.getDefault()).format(dateOnlyFormat.parse(startDateFilter!!) ?: Date()) } catch (_: Exception) { startDateFilter }
                                val e = if (!endDateFilter.isNullOrBlank() && endDateFilter != startDateFilter) {
                                    try { SimpleDateFormat("MMM d", Locale.getDefault()).format(dateOnlyFormat.parse(endDateFilter!!) ?: Date()) } catch (_: Exception) { endDateFilter }
                                } else null
                                if (e != null) "$s – $e" else "$s"
                            } else "Date Range"
                            Text(
                                rangeChipLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = SurfaceContainer,
                        labelColor = TextDark
                    ),
                    border = null,
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        // ── Range Sales Summary Card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .shadow(elevation = 3.dp, shape = ShapeMD, clip = false),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            shape = ShapeMD
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BrandPrimary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (rangeSummary.rangeLabel.isNotBlank()) rangeSummary.rangeLabel else "All Records",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${rangeSummary.count} order${if (rangeSummary.count != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Total Sales Amount",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                        Text(
                            text = formatPeso(currencyFormatter, rangeSummary.totalSales),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = BrandPrimary,
                                fontSize = 22.sp
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Paid", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                            Text(
                                formatPeso(currencyFormatter, rangeSummary.paidAmount),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorPaid
                                )
                            )
                        }
                        if (rangeSummary.unpaidAmount > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Unpaid", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                                Text(
                                    formatPeso(currencyFormatter, rangeSummary.unpaidAmount),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ColorUnpaid
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Secondary Filters & Sort Toolbar ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scrollable chips section
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {

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

                // Status filter chips: All, Paid, Unpaid
                listOf(null to "All", "PAID" to "Paid", "UNPAID" to "Unpaid").forEach { (key, label) ->
                    item {
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { viewModel.setHistoryFilters(startDateFilter, endDateFilter, if (statusFilter == key) null else key, customerFilter) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (key == "PAID") ColorPaid else if (key == "UNPAID") ColorUnpaid else BrandPrimary,
                                selectedLabelColor = Color.White, containerColor = SurfaceContainer, labelColor = TextDark
                            ), border = null, modifier = Modifier.height(32.dp)
                        )
                    }
                }

                if (customerFilter != null) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.setHistoryFilters(startDateFilter, endDateFilter, statusFilter, null) },
                            label = { Text("Customer: $customerFilter ✕", fontSize = 12.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White),
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
                            DateHeaderRow(
                                date = listItem.formattedDate,
                                orderCount = listItem.orderCount,
                                dayTotalSales = listItem.dayTotal,
                                currencyFormatter = currencyFormatter,
                                isCollapsed = listItem.isCollapsed,
                                onToggleCollapse = {
                                    collapsedDates = if (collapsedDates.contains(listItem.dateKey)) {
                                        collapsedDates - listItem.dateKey
                                    } else {
                                        collapsedDates + listItem.dateKey
                                    }
                                }
                            )
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

        // ── Date Range Picker Dialog ──
        if (showDateRangePicker) {
            DatePickerDialog(
                onDismissRequest = { showDateRangePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val startMillis = dateRangePickerState.selectedStartDateMillis
                            val endMillis = dateRangePickerState.selectedEndDateMillis ?: startMillis
                            if (startMillis != null) {
                                val startStr = dateOnlyFormat.format(Date(startMillis))
                                val endStr = if (endMillis != null) dateOnlyFormat.format(Date(endMillis)) else startStr
                                viewModel.setHistoryFilters(startStr, endStr, statusFilter, customerFilter, TimeHorizon.CUSTOM)
                            } else {
                                viewModel.setHistoryFilters(null, null, statusFilter, customerFilter, TimeHorizon.ALL)
                            }
                            showDateRangePicker = false
                        },
                        enabled = dateRangePickerState.selectedStartDateMillis != null
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.setHistoryFilters(null, null, statusFilter, customerFilter, TimeHorizon.ALL)
                        showDateRangePicker = false
                    }) { Text("Clear") }
                }
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    title = { Text("Select Date Range", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                    showModeToggle = false
                )
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
    data class DateHeader(
        val dateKey: String,
        val formattedDate: String,
        val orderCount: Int,
        val dayTotal: Double,
        val isCollapsed: Boolean
    ) : TransactionListItem
    data class TransactionEntry(val transaction: TransactionRecord, val dailyNumber: Int) : TransactionListItem
}

/** A collapsible date header that separates transaction groups by day, displaying order count and day total. */
@Composable
private fun DateHeaderRow(
    date: String,
    orderCount: Int,
    dayTotalSales: Double,
    currencyFormatter: NumberFormat,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(ShapeSM)
            .clickable { onToggleCollapse() },
        color = BrandPrimary.copy(alpha = 0.08f),
        shape = ShapeSM
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = BrandPrimary.copy(alpha = 0.15f),
                    shape = ShapeXL
                ) {
                    Text(
                        text = "$orderCount order${if (orderCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = BrandPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatPeso(currencyFormatter, dayTotalSales),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
