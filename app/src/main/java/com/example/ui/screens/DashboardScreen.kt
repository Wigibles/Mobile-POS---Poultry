package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Product
import com.example.data.Role
import com.example.ui.theme.*
import com.example.viewmodel.ChartDataPoint
import com.example.viewmodel.POSViewModel
import com.example.data.TimeHorizon
import com.example.data.shopDateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: POSViewModel,
    onEnterPOS: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.dashboardStats.collectAsState()
    val rangeStats by viewModel.dashboardRangeStats.collectAsState()
    val dashboardHorizon by viewModel.dashboardHorizon.collectAsState()
    val customDashboardDate by viewModel.customDashboardDate.collectAsState()
    val allTxs by viewModel.transactions.collectAsState()
    val productList by viewModel.products.collectAsState()
    val todayExpenseTotal by viewModel.todayExpenseTotal.collectAsState()
    val borrowStats by viewModel.borrowStats.collectAsState()
    val unpaidCustomers by viewModel.unpaidCustomers.collectAsState()
    val chartData by viewModel.salesChartData.collectAsState()
    val chartPeriod by viewModel.chartPeriod.collectAsState()
    val context = LocalContext.current

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
    val dateOnlyFormat = remember { shopDateFormat("yyyy-MM-dd") }

    val availableDates = remember(allTxs) {
        allTxs.map { dateOnlyFormat.format(Date(it.timestamp)) }.distinct().sortedDescending()
    }

    var showDashboardDateRangePicker by remember { mutableStateOf(false) }
    val dashboardDateRangePickerState = rememberDateRangePickerState()

    var showSettings by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showManagePins by remember { mutableStateOf(false) }
    val isClearing by viewModel.isClearingData.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isResyncingSheets by viewModel.isResyncingSheets.collectAsState()
    val currentRole by viewModel.currentRole.collectAsState()
    val isAdmin = currentRole == Role.ADMIN

    var hasLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasLaunched = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        StaggeredFadeSlide(visible = hasLaunched, delayMs = 0) {
            ModernHeader(onSettingsClick = { showSettings = true })
        }

        // Offline / pending-sync indicator — only visible when relevant
        val isOnline by viewModel.isOnline.collectAsState()
        val pendingCount by viewModel.pendingSyncCount.collectAsState()
        val syncInProgress by viewModel.isOfflineSyncing.collectAsState()
        if (!isOnline || pendingCount > 0) {
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (!isOnline) ColorUnpaid.copy(alpha = 0.10f) else BrandPrimary.copy(alpha = 0.12f)
                ),
                shape = ShapeSM
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (!isOnline) Icons.Default.CloudOff else Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (!isOnline) ColorUnpaid else BrandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (!isOnline) "You're offline — sales & expenses are saved locally and will sync when connected."
                        else if (syncInProgress) "Syncing $pendingCount pending record(s)…"
                        else "$pendingCount pending record(s) waiting to sync.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (!isOnline) ColorUnpaid else BrandPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    if (pendingCount > 0 && isOnline && !syncInProgress) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { viewModel.syncNow() }) {
                            Text("Sync now", color = BrandPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        StaggeredFadeSlide(visible = hasLaunched, delayMs = 80) {
            HeroSalesCard(
                rangeLabel = rangeStats.rangeLabel,
                totalSales = rangeStats.totalSales,
                paidAmount = rangeStats.paidAmount,
                unpaidAmount = rangeStats.unpaidAmount,
                formatter = currencyFormatter,
                selectedHorizon = dashboardHorizon,
                onHorizonChange = { viewModel.setDashboardHorizon(it) },
                onPickDate = { showDashboardDateRangePicker = true }
            )
        }

        Spacer(Modifier.height(16.dp))

        StaggeredFadeSlide(visible = hasLaunched, delayMs = 160) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernStatTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        gradientStart = BrandPrimary,
                        gradientEnd = BrandSecondary,
                        value = rangeStats.salesCount.toDouble(),
                        format = { it.toInt().toString() },
                        label = "Transactions"
                    )
                    ModernStatTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ShoppingCart,
                        gradientStart = Color(0xFFE8913A),
                        gradientEnd = Color(0xFFF4B35E),
                        value = rangeStats.expenseTotal,
                        format = { formatPeso(currencyFormatter, it) },
                        label = "Period Expenses",
                        labelColor = Color(0xFFB8731F)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModernStatTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.People,
                        gradientStart = Color(0xFF6B5B9E),
                        gradientEnd = Color(0xFF8F7DC4),
                        value = borrowStats.totalOutstanding,
                        format = { formatPeso(currencyFormatter, it) },
                        label = "Outstanding Borrows",
                        subtitle = "${borrowStats.outstandingCount} open · oldest ${borrowStats.oldestDays}d"
                    )
                    ModernStatTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccountBalanceWallet,
                        gradientStart = ColorPaid,
                        gradientEnd = ColorPaid.copy(alpha = 0.7f),
                        value = rangeStats.netAmount,
                        format = { formatPeso(currencyFormatter, it) },
                        label = "Net Income"
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        StaggeredFadeSlide(visible = hasLaunched, delayMs = 240) {
            SectionHeader(
                title = "Products",
                actionLabel = "View all",
                onAction = { viewModel.navigateTo("INVENTORY") }
            )
            Spacer(Modifier.height(10.dp))
            if (productList.isEmpty()) {
                Text(
                    "No products yet",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(productList, key = { it.id }) { product ->
                        ProductSnapshotCard(product = product)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (unpaidCustomers.isNotEmpty()) {
            StaggeredFadeSlide(visible = hasLaunched, delayMs = 280) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 4.dp, shape = ShapeLG, clip = false)
                        .pressScale(onClick = { viewModel.navigateTo("TRANSACTIONS") }),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = ShapeLG,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ColorUnpaid.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = ColorUnpaid,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${unpaidCustomers.size} customer${if (unpaidCustomers.size != 1) "s" else ""} with unpaid balances",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            )
                            Text(
                                text = unpaidCustomers.take(3).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "View",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        StaggeredFadeSlide(visible = hasLaunched, delayMs = 320) {
            ModernSalesChart(
                chartData = chartData,
                chartPeriod = chartPeriod,
                onPeriodChange = { viewModel.setChartPeriod(it) },
                onViewAll = { viewModel.navigateTo("TRANSACTIONS") }
            )
        }

        Spacer(Modifier.height(72.dp))
    }

    if (showDashboardDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDashboardDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dashboardDateRangePickerState.selectedStartDateMillis
                        val endMillis = dashboardDateRangePickerState.selectedEndDateMillis ?: startMillis
                        if (startMillis != null) {
                            val startStr = dateOnlyFormat.format(Date(startMillis))
                            val endStr = if (endMillis != null) dateOnlyFormat.format(Date(endMillis)) else startStr
                            viewModel.setDashboardHorizon(TimeHorizon.CUSTOM, startStr, endStr)
                        }
                        showDashboardDateRangePicker = false
                    },
                    enabled = dashboardDateRangePickerState.selectedStartDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setDashboardHorizon(TimeHorizon.DAY, null, null)
                    showDashboardDateRangePicker = false
                }) { Text("Reset") }
            }
        ) {
            DateRangePicker(
                state = dashboardDateRangePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                showModeToggle = false
            )
        }
    }

    if (showSettings) {
        ModernSettingsDialog(
            isAdmin = isAdmin,
            isSyncing = isSyncing,
            isResyncingSheets = isResyncingSheets,
            isClearing = isClearing,
            currentRole = currentRole,
            onDismiss = { showSettings = false },
            onSync = { _ -> viewModel.syncData { } },
            onResyncToSheets = { viewModel.resyncToSheets() },
            onManagePins = { showManagePins = true },
            onClearData = { showClearDialog = true },
            onLogout = {
                viewModel.logout()
                showSettings = false
            }
        )
    }

    if (showManagePins) {
        ManagePinsDialog(
            viewModel = viewModel,
            onDismiss = { showManagePins = false }
        )
    }

    if (showClearDialog) {
        ClearDataDialog(
            isClearing = isClearing,
            onDismiss = { showClearDialog = false },
            onConfirm = { _ ->
                viewModel.clearAllData { count ->
                    showClearDialog = false
                    showSettings = false
                    Toast.makeText(context, "$count records deleted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun StaggeredFadeSlide(
    visible: Boolean,
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    val show by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "staggered"
    )
    AnimatedVisibility(
        visible = show > 0.01f,
        enter = fadeIn(animationSpec = tween(400, delayMs)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialOffsetY = { it / 2 }
                )
    ) {
        content()
    }
}

@Composable
private fun ModernHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(ShapeMD)
                .background(SurfaceLight.copy(alpha = 0.85f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(BrandPrimary, BrandSecondary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = "Store",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Alyn's Poultry Supply",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Poultry & Farm Supplies",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(ShapeMD)
                .background(SurfaceLight.copy(alpha = 0.85f))
                .pressScale(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun HeroSalesCard(
    rangeLabel: String,
    totalSales: Double,
    paidAmount: Double,
    unpaidAmount: Double,
    formatter: NumberFormat,
    selectedHorizon: TimeHorizon,
    onHorizonChange: (TimeHorizon) -> Unit,
    onPickDate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = ShapeXL, clip = false),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = ShapeXL,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BrandPrimary, BrandSecondary),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
                .drawWithContent {
                    drawContent()
                    val stroke = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = stroke
                    )
                }
                .padding(20.dp)
        ) {
            Column {
                // Time Horizon Preset Tabs inside card header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeMD)
                        .background(Color.Black.copy(alpha = 0.15f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        TimeHorizon.DAY to "Day",
                        TimeHorizon.WEEK to "Week",
                        TimeHorizon.THIRTY_DAYS to "30D",
                        TimeHorizon.MTD to "MTD"
                    ).forEach { (h, label) ->
                        val isSelected = selectedHorizon == h
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ShapeSM)
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .clickable { onHorizonChange(h) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) BrandPrimary else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Calendar / Custom Date Icon
                    val isCustom = selectedHorizon == TimeHorizon.CUSTOM
                    Box(
                        modifier = Modifier
                            .clip(ShapeSM)
                            .background(if (isCustom) Color.White else Color.Transparent)
                            .clickable { onPickDate() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Custom Date",
                            tint = if (isCustom) BrandPrimary else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.6f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        rangeLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            letterSpacing = 0.8.sp,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                AnimatedCounterText(
                    value = totalSales,
                    format = { formatPeso(formatter, it) },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = Color.White
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Payments,
                        label = "Paid",
                        amount = paidAmount,
                        formatter = formatter
                    )
                    HeroChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CreditCard,
                        label = "Unpaid",
                        amount = unpaidAmount,
                        formatter = formatter
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: Double,
    formatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = ShapeSM,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp
                    )
                )
                Text(
                    formatPeso(formatter, amount),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ModernStatTile(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientStart: Color,
    gradientEnd: Color,
    value: Double,
    format: (Double) -> String,
    label: String,
    subtitle: String? = null,
    labelColor: Color = TextMuted
) {
    Card(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = ShapeLG, clip = false)
            .pressScale(onClick = {}),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeLG,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(gradientStart, gradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            AnimatedCounterText(
                value = value,
                format = format,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = TextDark
                )
            )

            Spacer(Modifier.height(2.dp))

            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = labelColor,
                    fontSize = 12.sp
                )
            )

            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp, 20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BrandPrimary)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    actionLabel,
                    fontSize = 13.sp,
                    color = BrandPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ProductSnapshotCard(product: Product) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .pressScale(onClick = {}),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeSM,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BrandPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = productEmoji(product.name, product.category), fontSize = 22.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

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

        }
    }
}

@Composable
private fun ModernSalesChart(
    chartData: List<ChartDataPoint>,
    chartPeriod: String,
    onPeriodChange: (String) -> Unit,
    onViewAll: () -> Unit
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    // ── Process data points ──────────────────────────────────────────
    val dataPoints = remember(chartData, chartPeriod) {
        when (chartPeriod) {
            "1M" -> {
                // Group daily data into weeks for readable labels
                chartData.take(31).chunked(7).mapIndexed { index, week ->
                    val startDay = index * 7 + 1
                    val endDay = minOf(startDay + 6, 31)
                    ChartDataPoint(
                        label = "W${index + 1}",
                        amount = week.sumOf { it.amount },
                        dateString = week.firstOrNull()?.dateString ?: ""
                    )
                }
            }
            "1Y" -> chartData.take(12)
            else -> chartData.take(7)
        }
    }

    val maxAmount = dataPoints.maxOfOrNull { it.amount }?.coerceAtLeast(100.0) ?: 100.0
    val niceMax = computeNiceCeiling(maxAmount)
    val totalForPeriod = dataPoints.sumOf { it.amount }
    val avgForPeriod = if (dataPoints.isNotEmpty()) totalForPeriod / dataPoints.size else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .shadow(elevation = 6.dp, shape = ShapeXL, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeXL,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ── Header ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp, 20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BrandPrimary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Sales Overview",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    )
                }
                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("View all", fontSize = 13.sp, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Period filter chips ───────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "DAY" to "Day",
                    "7D" to "Week",
                    "30D" to "30D",
                    "MTD" to "MTD",
                    "1Y" to "Year"
                ).forEach { (periodKey, label) ->
                    FilterChip(
                        selected = chartPeriod == periodKey,
                        onClick = {
                            onPeriodChange(periodKey)
                            selectedPointIndex = null
                        },
                        label = {
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = if (chartPeriod == periodKey) FontWeight.Bold else FontWeight.Normal
                            )
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
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Summary stats row ─────────────────────────────────────
            if (dataPoints.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandPrimary.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = formatPeso(currencyFormatter, totalForPeriod),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                fontSize = 14.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(BorderLight)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Average",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = formatPeso(currencyFormatter, avgForPeriod),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                fontSize = 14.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(BorderLight)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Peak",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = formatPeso(currencyFormatter, maxAmount),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Chart area ────────────────────────────────────────────
            if (dataPoints.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No sales data for this period",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                val yAxisWidth = 52.dp
                val chartHeight = 190.dp

                Row(modifier = Modifier.fillMaxWidth()) {
                    // ── Y-axis labels ─────────────────────────────────
                    Column(
                        modifier = Modifier.width(yAxisWidth).height(chartHeight),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 4 evenly-spaced labels from niceMax down to 0
                        (0..3).reversed().forEach { i ->
                            val value = niceMax * i / 3.0
                            Text(
                                text = compactCurrency(currencyFormatter, value),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 9.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // ── Canvas chart ──────────────────────────────────
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(chartHeight)
                    ) {
                        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                        val heightPx = with(LocalDensity.current) { chartHeight.toPx() }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(dataPoints) {
                                    detectTapGestures { offset ->
                                        val effectiveWidth = if (dataPoints.size > 1) widthPx else 1f
                                        val dx = if (dataPoints.size > 1)
                                            widthPx / (dataPoints.size - 1)
                                        else 0f
                                        val index = ((offset.x + dx / 2f) / dx)
                                            .toInt()
                                            .coerceIn(0, dataPoints.size - 1)
                                        selectedPointIndex =
                                            if (selectedPointIndex == index) null else index
                                    }
                                }
                        ) {
                            val dx = if (dataPoints.size > 1) widthPx / (dataPoints.size - 1) else widthPx

                            // ── Horizontal grid lines ─────────────────
                            val gridLines = 3
                            for (i in 0..gridLines) {
                                val y = heightPx - (i * (heightPx / gridLines))
                                drawLine(
                                    color = BorderLight.copy(alpha = 0.6f),
                                    start = Offset(0f, y),
                                    end = Offset(widthPx, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(8f, 6f), 0f
                                    )
                                )
                            }

                            // ── Baseline (y = 0) ──────────────────────
                            drawLine(
                                color = BorderLight,
                                start = Offset(0f, heightPx),
                                end = Offset(widthPx, heightPx),
                                strokeWidth = 1.5.dp.toPx()
                            )

                            // ── Build coordinates ─────────────────────
                            val coordinates = dataPoints.mapIndexed { index, point ->
                                val x = if (dataPoints.size > 1) index * dx else widthPx / 2f
                                val y = heightPx - ((point.amount / niceMax).toFloat() * heightPx).coerceIn(0f, heightPx)
                                Offset(x, y)
                            }

                            if (coordinates.size >= 2) {
                                val strokePath = Path()
                                val fillPath = Path()

                                strokePath.moveTo(coordinates.first().x, coordinates.first().y)
                                fillPath.moveTo(coordinates.first().x, heightPx)
                                fillPath.lineTo(coordinates.first().x, coordinates.first().y)

                                for (i in 0 until coordinates.size - 1) {
                                    val p0 = coordinates[i]
                                    val p1 = coordinates[i + 1]
                                    val cp1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                                    val cp2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
                                    strokePath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p1.x, p1.y)
                                    fillPath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p1.x, p1.y)
                                }

                                fillPath.lineTo(coordinates.last().x, heightPx)
                                fillPath.close()

                                // ── Gradient fill under the curve ─────
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            BrandPrimary.copy(alpha = 0.25f),
                                            BrandPrimary.copy(alpha = 0.02f)
                                        ),
                                        startY = 0f,
                                        endY = heightPx
                                    )
                                )

                                // ── Stroke line ───────────────────────
                                drawPath(
                                    path = strokePath,
                                    color = BrandPrimary,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // ── Dot markers for each point ────────
                                coordinates.forEachIndexed { i, pt ->
                                    val isSelected = selectedPointIndex == i
                                    if (isSelected) {
                                        drawCircle(Color.White, 7.dp.toPx(), pt)
                                        drawCircle(BrandPrimary, 5.dp.toPx(), pt)
                                        drawCircle(
                                            BrandPrimary.copy(alpha = 0.18f),
                                            14.dp.toPx(), pt
                                        )
                                    } else {
                                        drawCircle(
                                            BrandPrimary.copy(alpha = 0.35f),
                                            3.dp.toPx(), pt
                                        )
                                    }
                                }
                            } else if (coordinates.size == 1) {
                                // Single data point — show a centered dot
                                val pt = coordinates.first()
                                drawCircle(Color.White, 7.dp.toPx(), pt)
                                drawCircle(BrandPrimary, 5.dp.toPx(), pt)
                                drawCircle(BrandPrimary.copy(alpha = 0.18f), 14.dp.toPx(), pt)
                            }
                        }

                        // ── Floating tooltip ──────────────────────────
                        selectedPointIndex?.let { index ->
                            val point = dataPoints[index]
                            val density = LocalDensity.current
                            val dx = if (dataPoints.size > 1)
                                widthPx / (dataPoints.size - 1) else 0f
                            val rawX = if (dataPoints.size > 1) index * dx else widthPx / 2f
                            val rawY = heightPx * (1f - (point.amount / niceMax).toFloat())
                                .coerceIn(0f, heightPx)

                            val tooltipWidth = 72.dp
                            val tooltipHeight = 26.dp
                            val halfTooltipPx = with(density) { tooltipWidth.toPx() / 2f }

                            // Bounds-aware horizontal positioning
                            val tx = when {
                                rawX - halfTooltipPx < 0f -> 0.dp
                                rawX + halfTooltipPx > widthPx -> maxWidth - tooltipWidth
                                else -> with(density) { (rawX / this.density).dp - tooltipWidth / 2 }
                            }

                            // Show tooltip above the point (or below if near top)
                            val showAbove = rawY > with(density) { tooltipHeight.toPx() + 8.dp.toPx() }
                            val ty = if (showAbove)
                                with(density) { (rawY / this.density).dp - tooltipHeight - 4.dp }
                            else
                                with(density) { (rawY / this.density).dp + 8.dp }

                            Surface(
                                modifier = Modifier.offset(x = tx, y = ty),
                                color = BrandPrimary,
                                shape = RoundedCornerShape(6.dp),
                                shadowElevation = 6.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = formatPeso(currencyFormatter, point.amount),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = point.label,
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── X-axis labels ─────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp), // align with chart (yAxisWidth + spacer)
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dataPoints.forEachIndexed { index, point ->
                        val showLabel = when {
                            dataPoints.size <= 7 -> true          // always show for 7D
                            dataPoints.size <= 12 -> true         // always show for 1Y
                            else -> index % 2 == 0 || index == dataPoints.size - 1  // every other for larger sets
                        }
                        if (showLabel) {
                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selectedPointIndex == index) BrandPrimary else TextMuted,
                                    fontWeight = if (selectedPointIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        } else {
                            Spacer(Modifier.width(1.dp)) // placeholder for spacing
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rounds a value up to a "nice" ceiling for chart y-axis scaling
 * (e.g. 1,234 → 1,500; 5,678 → 6,000; 920 → 1,000).
 */
private fun computeNiceCeiling(value: Double): Double {
    if (value <= 0) return 100.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(value)))
    val normalized = value / magnitude
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

/**
 * Compact currency formatter for y-axis labels — uses "₱1.2k" style
 * when values are large, and regular formatting otherwise.
 */
private fun compactCurrency(formatter: NumberFormat, amount: Double): String {
    return when {
        amount >= 1_000_000 -> "₱${"%,.1f".format(amount / 1_000_000)}M"
        amount >= 10_000 -> "₱${"%,.0f".format(amount / 1_000)}k"
        amount >= 1_000 -> "₱${"%,.1f".format(amount / 1_000)}k"
        else -> formatPeso(formatter, amount)
    }
}

@Composable
private fun ModernSettingsDialog(
    isAdmin: Boolean,
    isSyncing: Boolean,
    isResyncingSheets: Boolean,
    isClearing: Boolean,
    currentRole: Role?,
    onDismiss: () -> Unit,
    onSync: (callback: (Int) -> Unit) -> Unit,
    onResyncToSheets: () -> Unit,
    onManagePins: () -> Unit,
    onClearData: () -> Unit,
    onLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ShapeXL,
        containerColor = SurfaceLight,
        icon = {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text("Settings", fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAdmin) {
                    SettingsRow(
                        icon = Icons.Default.Sync,
                        iconTint = BrandPrimary,
                        title = "Sync Data",
                        subtitle = "Check connection & refresh",
                        titleColor = TextDark
                    ) {
                        Button(
                            onClick = { onSync { } },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = ShapeXS,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(if (isSyncing) "Syncing…" else "Sync", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = BorderLight)

                    SettingsRow(
                        icon = Icons.Default.Cloud,
                        iconTint = BrandPrimary,
                        title = "Resync to Sheets",
                        subtitle = "Re-send all transactions to Google Sheets",
                        titleColor = TextDark
                    ) {
                        Button(
                            onClick = onResyncToSheets,
                            enabled = !isResyncingSheets,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = ShapeXS,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(if (isResyncingSheets) "Syncing…" else "Resync", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = BorderLight)

                    SettingsRow(
                        icon = Icons.Default.Password,
                        iconTint = BrandPrimary,
                        title = "Manage PINs",
                        subtitle = "Change Admin & Cashier PINs",
                        titleColor = TextDark
                    ) {
                        Button(
                            onClick = onManagePins,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = ShapeXS,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Edit", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = BorderLight)

                    SettingsRow(
                        icon = Icons.Default.DeleteForever,
                        iconTint = ColorUnpaid,
                        title = "Delete All Data",
                        subtitle = "Permanently remove everything",
                        titleColor = ColorUnpaid
                    ) {
                        Button(
                            onClick = onClearData,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid),
                            shape = ShapeXS,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = BorderLight)
                }

                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = TextMuted,
                    title = "Logout",
                    subtitle = if (currentRole == Role.CASHIER) "Sign out of Cashier session" else "Sign out of Admin session",
                    titleColor = TextDark
                ) {
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = ShapeXS,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Logout", fontSize = 12.sp, color = TextDark)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        }
    )
}

@Composable
private fun ManagePinsDialog(
    viewModel: POSViewModel,
    onDismiss: () -> Unit
) {
    val authSettings by viewModel.authSettings.collectAsState()
    var newAdminPin by remember(authSettings) { mutableStateOf(authSettings?.adminPin ?: "") }
    var newCashierPin by remember(authSettings) { mutableStateOf(authSettings?.cashierPin ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ShapeXL,
        containerColor = SurfaceLight,
        icon = {
            Icon(Icons.Default.Password, null, tint = BrandPrimary, modifier = Modifier.size(40.dp))
        },
        title = {
            Text("Manage PINs", fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = newAdminPin,
                    onValueChange = { newAdminPin = it },
                    label = { Text("Admin PIN") },
                    singleLine = true,
                    shape = ShapeXS,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCashierPin,
                    onValueChange = { newCashierPin = it },
                    label = { Text("Cashier PIN") },
                    singleLine = true,
                    shape = ShapeXS,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updatePins(newAdminPin.trim(), newCashierPin.trim())
                    onDismiss()
                },
                enabled = newAdminPin.isNotBlank() && newCashierPin.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = ShapeXS
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun ClearDataDialog(
    isClearing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (callback: (Int) -> Unit) -> Unit
) {
    var deleteConfirmText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isClearing) onDismiss() },
        shape = ShapeXL,
        containerColor = SurfaceLight,
        icon = {
            Icon(Icons.Default.Warning, null, tint = ColorUnpaid, modifier = Modifier.size(40.dp))
        },
        title = {
            Text("Delete All Data?", fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = {
            Column {
                Text(
                    "This will permanently delete all products, transactions, categories, and counters from Firestore.",
                    color = TextMuted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = deleteConfirmText,
                    onValueChange = { deleteConfirmText = it },
                    placeholder = { Text("DELETE") },
                    singleLine = true,
                    enabled = !isClearing,
                    shape = ShapeXS,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm { } },
                enabled = !isClearing && deleteConfirmText.trim().equals("DELETE", ignoreCase = false),
                colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid),
                shape = ShapeXS
            ) {
                Text(if (isClearing) "Deleting…" else "Delete All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isClearing) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
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
