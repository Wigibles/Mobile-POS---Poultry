package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Product
import com.example.ui.theme.*
import com.example.viewmodel.POSViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: POSViewModel,
    onEnterPOS: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.dashboardStats.collectAsState()
    val lowStockList by viewModel.lowStockProducts.collectAsState()
    val chartData by viewModel.salesChartData.collectAsState()
    val chartPeriod by viewModel.chartPeriod.collectAsState()
    val context = LocalContext.current

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))

    // Settings
    var showSettings by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val isClearing by viewModel.isClearingData.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. App Header — Alyn's Poultry Supply
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLight)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CoralPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Store",
                        tint = CoralPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Alyn's Poultry Supply",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Settings gear
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLight)
                    .clickable { showSettings = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Stats Grid — modern flat cards
        // Today's Sales — main card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CoralPrimary),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Today's Sales", style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.8f)))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = currencyFormatter.format(stats.totalSalesToday).replace("PHP", "₱"),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 32.sp)
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Paid tag
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(currencyFormatter.format(stats.paidToday).replace("PHP", "₱"), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }
                    }
                    // Unpaid tag
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreditCard, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(currencyFormatter.format(stats.unpaidToday).replace("PHP", "₱"), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Secondary stats row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${stats.salesCount}", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = TextDark, fontSize = 28.sp))
                    Text("Transactions", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currencyFormatter.format(stats.avgTicketSize).replace("PHP", "₱"), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = TextDark, fontSize = 28.sp))
                    Text("Avg Ticket", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Low Stock Products Section
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Low Stock", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
            Text("View all", fontSize = 12.sp, color = CoralPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { viewModel.navigateTo("INVENTORY") })
        }

        Spacer(Modifier.height(8.dp))

        if (lowStockList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = ColorPaid, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("All stock levels are sufficient", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                items(lowStockList) { product -> LowStockCard(product = product) }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Sales Chart — modern flat card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sales Overview",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    )
                    Text(
                        text = "View all",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable { viewModel.navigateTo("TRANSACTIONS") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Period filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("7D" to "7 Days", "1M" to "This Month", "1Y" to "This Year").forEach { (key, label) ->
                        FilterChip(
                            selected = chartPeriod == key,
                            onClick = { viewModel.setChartPeriod(key) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoralPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = BorderLight,
                                labelColor = TextDark
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Chart bars
                val maxAmount = chartData.maxOfOrNull { it.amount }?.coerceAtLeast(1000.0) ?: 1000.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    chartData.take(if (chartPeriod == "1M") 31 else if (chartPeriod == "1Y") 12 else 7).forEach { point ->
                        val barHeightProportion = if (maxAmount > 0) (point.amount / maxAmount).toFloat() else 0f
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (point.amount > 0 && (chartPeriod != "1M" || chartData.size <= 15)) {
                                Text(
                                    text = "₱${(point.amount / 1000).toInt()}k",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp, color = TextMuted)
                                )
                            } else {
                                Text("", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp))
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.70f * barHeightProportion + 0.04f)
                                    .width(if (chartPeriod == "1M") 6.dp else 22.dp)
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                    .background(if (point.amount == maxAmount && maxAmount > 0) SoftOrange else CoralPrimary)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 8.sp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp)) // Safe scrolling space for bottom nav
    }

    // ── Settings Dialog ──
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = CoralPrimary, modifier = Modifier.size(36.dp)) },
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Sync Data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync Data", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Check connection and refresh all data", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
                        }
                        Button(
                            onClick = {
                                viewModel.syncData { total ->
                                    Toast.makeText(context, "Synced — $total records found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(if (isSyncing) "…" else "Sync", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = BorderLight)

                    // Delete All Data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delete All Data", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ColorUnpaid))
                            Text("Permanently remove everything from cloud", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
                        }
                        Button(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Close") }
            }
        )
    }

    // ── Clear All Data Confirmation Dialog ──
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { if (!isClearing) showClearDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ColorUnpaid, modifier = Modifier.size(36.dp)) },
            title = { Text("Delete All Data?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently delete all products, transactions, categories, and counters from Firestore. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData { count ->
                            showClearDialog = false
                            showSettings = false
                            Toast.makeText(context, "$count records deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isClearing,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid)
                ) {
                    Text(if (isClearing) "Deleting…" else "Delete All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }, enabled = !isClearing) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LowStockCard(product: Product) {
    // Determine a neat emoji or symbol based on name/category
    val emoji = when {
        product.name.contains("Booster", ignoreCase = true) -> "🐣"
        product.name.contains("Grower", ignoreCase = true) -> "🐓"
        product.name.contains("Layer", ignoreCase = true) -> "🥚"
        product.name.contains("Vitamin", ignoreCase = true) -> "💊"
        product.name.contains("Feeder", ignoreCase = true) -> "🥣"
        product.name.contains("Waterer", ignoreCase = true) -> "🪣"
        else -> "🌾"
    }

    Card(
        modifier = Modifier
            .width(140.dp)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Emojis/Icons
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CoralPrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = product.category,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Only ${if (product.stockLevel == product.stockLevel.toLong().toDouble()) product.stockLevel.toLong().toString() else String.format("%.1f", product.stockLevel)} left",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = ColorUnpaid
                )
            )
        }
    }
}
