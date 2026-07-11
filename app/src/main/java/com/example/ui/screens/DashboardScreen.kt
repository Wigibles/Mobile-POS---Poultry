package com.example.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val chartData by viewModel.last7DaysSalesChart.collectAsState()

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Safe spacing for status bar
        Spacer(modifier = Modifier.height(24.dp))

        // 1. App Header (Rishi's General Store style -> Alyn's Poultry Supply)
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

            // User Icon button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLight)
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Stats Grid (Today's Sales, Transactions, Splits, Avg Ticket)
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Column: Today's Sales Card & Splits
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .padding(end = 6.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CoralPrimary)
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Today's Sales",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currencyFormatter.format(stats.totalSalesToday).replace("PHP", "₱"),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = TextDark,
                            fontSize = 24.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Paid and Unpaid split icons & values
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = "Paid",
                                tint = ColorPaid,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "₱${stats.paidToday.toInt()}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = "Unpaid",
                                tint = ColorUnpaid,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "₱${stats.unpaidToday.toInt()}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                            )
                        }
                    }
                }
            }

            // Right Column: Transactions & Ticket Size
            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .padding(start = 6.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Transactions\nToday",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            ),
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${stats.salesCount} Sales",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CoralPrimary
                            )
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Avg. Ticket Size",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "₱${stats.avgTicketSize.toInt()}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CoralPrimary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Low Stock Products Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Low Stock Products",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            )
            Text(
                text = "View more",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.clickable { viewModel.navigateTo("INVENTORY") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (lowStockList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Stock OK",
                        tint = ColorPaid,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All stock levels are sufficient",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(lowStockList) { product ->
                    LowStockCard(product = product)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Last 7 Days Sales Graph
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        text = "Last 7 Days Sales",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    )
                    Text(
                        text = "View more",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.clickable { viewModel.navigateTo("TRANSACTIONS") }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Beautiful fully reactive custom bar graph using standard Row & Column layout
                val maxAmount = chartData.maxOfOrNull { it.amount }?.coerceAtLeast(1000.0) ?: 1000.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    chartData.forEach { point ->
                        val barHeightProportion = if (maxAmount > 0) (point.amount / maxAmount).toFloat() else 0f
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Amount above bar if not 0
                            if (point.amount > 0) {
                                Text(
                                    text = "₱${(point.amount / 1000).toInt()}k",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                )
                            } else {
                                Text(
                                    text = "₱0",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 9.sp,
                                        color = Color.Transparent
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // The colored bar
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.75f * barHeightProportion + 0.05f) // scale with safe min
                                    .width(22.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (point.amount == maxAmount) SoftOrange else CoralPrimary
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Day Label
                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp)) // Safe scrolling space for floating buttons
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundLight),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 20.sp)
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
                text = "Only ${product.stockLevel.toInt()} left",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = ColorUnpaid
                )
            )
        }
    }
}
