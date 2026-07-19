package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Product
import com.example.data.Role
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
    var showManagePins by remember { mutableStateOf(false) }
    val isClearing by viewModel.isClearingData.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val currentRole by viewModel.currentRole.collectAsState()
    val isAdmin = currentRole == Role.ADMIN

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
                    .shadow(elevation = 3.dp, shape = ShapeMD, clip = false)
                    .clip(ShapeMD)
                    .background(SurfaceLight)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BrandPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Store",
                        tint = BrandPrimary
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
                    .shadow(elevation = 3.dp, shape = ShapeMD, clip = false)
                    .clip(ShapeMD)
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

        // 2. Hero Stat — Today's Sales, soft gradient + animated count-up
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = ShapeXL, clip = false),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = ShapeXL,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(BrandPrimary, BrandSecondary)))
                    .padding(24.dp)
            ) {
                Text("Today's Sales", style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.85f)))
                Spacer(Modifier.height(8.dp))
                AnimatedCounterText(
                    value = stats.totalSalesToday,
                    format = { formatPeso(currencyFormatter, it) },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Paid tag
                    Surface(color = Color.White.copy(alpha = 0.18f), shape = ShapeSM) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(formatPeso(currencyFormatter, stats.paidToday), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }
                    }
                    // Unpaid tag
                    Surface(color = Color.White.copy(alpha = 0.18f), shape = ShapeSM) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreditCard, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(formatPeso(currencyFormatter, stats.unpaidToday), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Secondary stats row — soft per-metric tint
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                tint = BrandPrimary,
                tintBackground = BrandPrimaryContainer,
                value = stats.salesCount.toDouble(),
                format = { it.toInt().toString() },
                label = "Transactions"
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                tint = ChartHighlight,
                tintBackground = ChartHighlight.copy(alpha = 0.16f),
                value = stats.avgTicketSize,
                format = { formatPeso(currencyFormatter, it) },
                label = "Avg Ticket"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Low Stock Products Section
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Low Stock", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
            TextButton(onClick = { viewModel.navigateTo("INVENTORY") }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("View all", fontSize = 13.sp, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (lowStockList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().shadow(elevation = 2.dp, shape = ShapeLG, clip = false),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                shape = ShapeLG,
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
                items(lowStockList, key = { it.id }) { product -> LowStockCard(product = product) }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Sales Chart — soft rounded bars, animated on data/period change
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).shadow(elevation = 4.dp, shape = ShapeXL, clip = false),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            shape = ShapeXL,
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
                    TextButton(onClick = { viewModel.navigateTo("TRANSACTIONS") }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("View all", fontSize = 13.sp, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Period filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("7D" to "7 Days", "1M" to "This Month", "1Y" to "This Year").forEach { (periodKey, label) ->
                        FilterChip(
                            selected = chartPeriod == periodKey,
                            onClick = { viewModel.setChartPeriod(periodKey) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceContainer,
                                labelColor = TextDark
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Chart bars. The month view is rolled up into weekly buckets — 31 hairline
                // bars are decoration, 4–5 weekly bars are readable at a glance.
                data class ChartBar(val key: String, val label: String, val amount: Double)
                val bars = when (chartPeriod) {
                    "1M" -> chartData.take(31).chunked(7).map { week ->
                        ChartBar(week.first().dateString, week.first().label, week.sumOf { it.amount })
                    }
                    "1Y" -> chartData.take(12).map { ChartBar(it.dateString, it.label, it.amount) }
                    else -> chartData.take(7).map { ChartBar(it.dateString, it.label, it.amount) }
                }
                val maxAmount = bars.maxOfOrNull { it.amount }?.coerceAtLeast(1000.0) ?: 1000.0
                fun compactAmount(a: Double): String = when {
                    a >= 100_000 -> "₱${(a / 1000).toInt()}k"
                    a >= 1000 -> "₱${String.format("%.1f", a / 1000).removeSuffix(".0")}k"
                    a > 0 -> "₱${a.toInt()}"
                    else -> ""
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    bars.forEach { bar ->
                        key(bar.key) {
                            val barHeightProportion = if (maxAmount > 0) (bar.amount / maxAmount).toFloat() else 0f
                            val animatedProportion by animateFloatAsState(
                                targetValue = barHeightProportion,
                                animationSpec = tween(durationMillis = 500),
                                label = "chartBar"
                            )
                            val isPeak = bar.amount == maxAmount && maxAmount > 0

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = compactAmount(bar.amount),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp, color = if (isPeak) ColorLowStockText else TextMuted),
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(0.70f * animatedProportion + 0.04f)
                                        .width(22.dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                        .background(if (isPeak) ChartHighlight else BrandPrimary)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = bar.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 9.sp),
                                    maxLines = 1
                                )
                            }
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
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(36.dp)) },
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isAdmin) {
                        // Sync Data
                        SettingsRow(
                            icon = Icons.Default.Sync,
                            iconTint = BrandPrimary,
                            title = "Sync Data",
                            subtitle = "Check connection and refresh all data",
                            titleColor = TextDark
                        ) {
                            Button(
                                onClick = {
                                    viewModel.syncData { total ->
                                        Toast.makeText(context, "Synced — $total records found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                shape = ShapeXS,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(if (isSyncing) "…" else "Sync", fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = BorderLight)

                        // Manage PINs
                        SettingsRow(
                            icon = Icons.Default.Password,
                            iconTint = BrandPrimary,
                            title = "Manage PINs",
                            subtitle = "Change the Admin and Cashier login PINs",
                            titleColor = TextDark
                        ) {
                            Button(
                                onClick = { showManagePins = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                shape = ShapeXS,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Edit", fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = BorderLight)

                        // Delete All Data
                        SettingsRow(
                            icon = Icons.Default.DeleteForever,
                            iconTint = ColorUnpaid,
                            title = "Delete All Data",
                            subtitle = "Permanently remove everything from cloud",
                            titleColor = ColorUnpaid
                        ) {
                            Button(
                                onClick = { showClearDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid),
                                shape = ShapeXS,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = BorderLight)
                    }

                    // Logout — available to both roles
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconTint = TextMuted,
                        title = "Logout",
                        subtitle = if (currentRole == Role.CASHIER) "Sign out of this Cashier session" else "Sign out of this Admin session",
                        titleColor = TextDark
                    ) {
                        Button(
                            onClick = {
                                viewModel.logout()
                                showSettings = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                            shape = ShapeXS,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Logout", fontSize = 12.sp, color = TextDark)
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

    // ── Manage PINs Dialog (admin only) ──
    if (showManagePins) {
        val authSettings by viewModel.authSettings.collectAsState()
        var newAdminPin by remember(authSettings) { mutableStateOf(authSettings?.adminPin ?: "") }
        var newCashierPin by remember(authSettings) { mutableStateOf(authSettings?.cashierPin ?: "") }
        AlertDialog(
            onDismissRequest = { showManagePins = false },
            icon = { Icon(Icons.Default.Password, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(36.dp)) },
            title = { Text("Manage PINs", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newAdminPin,
                        onValueChange = { newAdminPin = it },
                        label = { Text("Admin PIN") },
                        singleLine = true,
                        shape = ShapeXS,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPrimary, unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextDark, unfocusedTextColor = TextDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCashierPin,
                        onValueChange = { newCashierPin = it },
                        label = { Text("Cashier PIN") },
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
                    onClick = {
                        viewModel.updatePins(newAdminPin.trim(), newCashierPin.trim())
                        showManagePins = false
                    },
                    enabled = newAdminPin.isNotBlank() && newCashierPin.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManagePins = false }) { Text("Cancel") }
            }
        )
    }

    // ── Clear All Data Confirmation Dialog ──
    // Wiping every record on a shared shop device deserves more friction than one tap:
    // the user must type DELETE before the destructive button arms itself.
    if (showClearDialog) {
        var deleteConfirmText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!isClearing) showClearDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ColorUnpaid, modifier = Modifier.size(36.dp)) },
            title = { Text("Delete All Data?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This will permanently delete all products, transactions, categories, and counters from Firestore. This action cannot be undone.")
                    Spacer(Modifier.height(12.dp))
                    Text("Type DELETE to confirm:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        placeholder = { Text("DELETE", color = TextMuted.copy(alpha = 0.5f)) },
                        singleLine = true,
                        enabled = !isClearing,
                        shape = ShapeXS,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorUnpaid,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextDark, unfocusedTextColor = TextDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                    enabled = !isClearing && deleteConfirmText.trim().equals("DELETE", ignoreCase = false),
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
private fun StatTile(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    tintBackground: Color,
    value: Double,
    format: (Double) -> String,
    label: String
) {
    Card(
        modifier = modifier.shadow(elevation = 2.dp, shape = ShapeLG, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeLG,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(tintBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(8.dp))
            AnimatedCounterText(
                value = value,
                format = format,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                color = TextDark
            )
            Text(label, style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = titleColor))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
            }
        }
        trailing()
    }
}

@Composable
fun LowStockCard(product: Product) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .padding(vertical = 4.dp)
            .shadow(elevation = 3.dp, shape = ShapeSM, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeSM,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(ColorLowStock))
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BrandPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = productEmoji(product.name), fontSize = 22.sp)
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
                        color = ColorLowStockText
                    )
                )
            }
        }
    }
}
