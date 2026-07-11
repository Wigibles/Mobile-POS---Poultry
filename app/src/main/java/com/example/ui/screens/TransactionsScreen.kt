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

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    val sdf = SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault())
    val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Filtered lists depending on ALL vs UNPAID main tab
    val displayedTxs = remember(filteredTxs, activeViewTab) {
        if (activeViewTab == "UNPAID") {
            filteredTxs.filter { it.status == "UNPAID" }
        } else {
            filteredTxs
        }
    }

    // Dynamic dates list for date filtering based on actual database entries
    val availableDates = remember(allTxs) {
        allTxs.map { dateOnlyFormat.format(Date(it.timestamp)) }.distinct().sortedDescending()
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
        Spacer(modifier = Modifier.height(24.dp))

        // Screen title
        Text(
            text = "Transaction History",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextDark
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 1. ALL vs UNPAID Toggle Sub-Tabs (As requested!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BorderLight)
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activeViewTab == "ALL") SurfaceLight else Color.Transparent)
                    .clickable { activeViewTab = "ALL" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "All",
                        tint = if (activeViewTab == "ALL") CoralPrimary else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All Transactions",
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activeViewTab == "UNPAID") SurfaceLight else Color.Transparent)
                    .clickable { activeViewTab = "UNPAID" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PendingActions,
                        contentDescription = "Unpaid",
                        tint = if (activeViewTab == "UNPAID") ColorUnpaid else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unpaid Debt Tabs",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (activeViewTab == "UNPAID") ColorUnpaid else TextMuted
                        )
                    )
                }
            }
        }

        // 2. FILTERS PANEL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = CoralPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Filters", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                    }

                    if (dateFilter != null || statusFilter != null || customerFilter != null) {
                        Text(
                            text = "Clear All",
                            style = MaterialTheme.typography.bodySmall.copy(color = CoralPrimary, fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable { viewModel.clearHistoryFilters() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date Filters Row
                Text("Date Filter:", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = dateFilter == null,
                            onClick = { viewModel.setHistoryFilters(null, statusFilter, customerFilter) },
                            label = { Text("All Dates") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoralPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = BorderLight,
                                labelColor = TextDark
                            )
                        )
                    }
                    items(availableDates) { dateStr ->
                        // Format date string to display, e.g. "Jul 11, 2026"
                        val displayDate = try {
                            val parsed = dateOnlyFormat.parse(dateStr)
                            SimpleDateFormat("MMM dd", Locale.getDefault()).format(parsed)
                        } catch (e: Exception) {
                            dateStr
                        }
                        FilterChip(
                            selected = dateFilter == dateStr,
                            onClick = { viewModel.setHistoryFilters(dateStr, statusFilter, customerFilter) },
                            label = { Text(displayDate) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoralPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = BorderLight,
                                labelColor = TextDark
                            )
                        )
                    }
                }

                // If Main tab is "ALL Transactions", show payment status filter
                if (activeViewTab == "ALL") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Payment Status:", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { viewModel.setHistoryFilters(dateFilter, null, customerFilter) },
                            label = { Text("All Statuses") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoralPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = BorderLight,
                                labelColor = TextDark
                            )
                        )
                        FilterChip(
                            selected = statusFilter == "PAID",
                            onClick = { viewModel.setHistoryFilters(dateFilter, "PAID", customerFilter) },
                            label = { Text("Paid Only") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ColorPaid,
                                selectedLabelColor = Color.White,
                                containerColor = BorderLight,
                                labelColor = TextDark
                            )
                        )
                        FilterChip(
                            selected = statusFilter == "UNPAID",
                            onClick = { viewModel.setHistoryFilters(dateFilter, "UNPAID", customerFilter) },
                            label = { Text("Unpaid Only") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ColorUnpaid,
                                selectedLabelColor = Color.White,
                                containerColor = BorderLight,
                                labelColor = TextDark
                            )
                        )
                    }
                }

                // CUSTOMER NAME FILTER FOR UNPAID TRANSACTIONS (As requested!)
                if (activeViewTab == "UNPAID" || statusFilter == "UNPAID") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Filter Unpaid by Customer Name:", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    if (unpaidCustomers.isEmpty()) {
                        Text("No customer records found", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = customerFilter == null,
                                    onClick = { viewModel.setHistoryFilters(dateFilter, statusFilter, null) },
                                    label = { Text("All Customers") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CoralPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = BorderLight,
                                        labelColor = TextDark
                                    )
                                )
                            }
                            items(unpaidCustomers) { customer ->
                                FilterChip(
                                    selected = customerFilter == customer,
                                    onClick = { viewModel.setHistoryFilters(dateFilter, statusFilter, customer) },
                                    label = { Text(customer) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ColorUnpaid,
                                        selectedLabelColor = Color.White,
                                        containerColor = BorderLight,
                                        labelColor = TextDark
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

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
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No matching transactions",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
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
                        onDelete = { viewModel.deleteTransaction(tx) }
                    )
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
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val cardModifier = if (transaction.status == "UNPAID") {
        Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = ColorUnpaid.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { expanded = !expanded }
    } else {
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
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
                        imageVector = if (transaction.status == "PAID") Icons.Default.CheckCircle else Icons.Default.Pending,
                        contentDescription = transaction.status,
                        tint = if (transaction.status == "PAID") ColorPaid else ColorUnpaid,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Order #${transaction.id}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    )
                }

                // Payment Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (transaction.status == "PAID") ColorPaid.copy(alpha = 0.1f)
                            else ColorUnpaid.copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = transaction.status,
                        color = if (transaction.status == "PAID") ColorPaid else ColorUnpaid,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-detail: Customer Name (if Unpaid)
            if (transaction.status == "UNPAID" && !transaction.customerName.isNullOrBlank()) {
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
                        text = "Customer Tab: ${transaction.customerName}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = ColorUnpaid,
                            fontWeight = FontWeight.Bold
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
                Text(
                    text = dateFormatter.format(Date(transaction.timestamp)),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )

                Text(
                    text = currencyFormatter.format(transaction.totalAmount).replace("PHP", "₱"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = if (transaction.status == "PAID") TextDark else ColorUnpaid
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
                                    text = "Pack: ${item.variationName} | Qty: ${item.quantity.toInt()}",
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = ColorUnpaid)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Transaction", fontSize = 12.sp)
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
}
