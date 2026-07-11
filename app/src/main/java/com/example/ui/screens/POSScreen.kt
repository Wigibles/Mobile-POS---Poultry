package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Product
import com.example.data.ProductVariation
import com.example.ui.theme.*
import com.example.viewmodel.POSViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun POSScreen(
    viewModel: POSViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val variations by viewModel.variations.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedProductForVariations by viewModel.selectedProductForVariations.collectAsState()

    // Sub-navigation: Catalog (Product selection) vs Active Order (Cart)
    var activePosTab by remember { mutableStateOf("CATALOG") } // CATALOG, CART

    // Transaction dialog trigger states
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var confirmationStatus by remember { mutableStateOf("PAID") } // PAID or UNPAID
    var customerNameInput by remember { mutableStateOf("") }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    // Filters products based on search and category
    val filteredProducts = products.filter { product ->
        val matchesCategory = selectedCategory == null || product.category.equals(selectedCategory, ignoreCase = true)
        val matchesSearch = product.name.contains(searchQuery, ignoreCase = true) || product.category.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        val isWideScreen = maxWidth >= 680.dp

        if (isWideScreen) {
            // WIDE SCREEN SPLIT-PANE LAYOUT
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                // Left Column: Catalog (width weight 1.15f)
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                ) {
                    // Header Brand
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CoralPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Agriculture,
                                    contentDescription = "Agriculture",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Alyn's Poultry",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                )
                                Text(
                                    text = "POINT OF SALE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        color = TextMuted,
                                        fontSize = 8.sp,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.navigateTo("TRANSACTIONS") },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BorderLight)
                        ) {
                            Icon(Icons.Default.History, contentDescription = "History", tint = TextDark, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Search product name...", color = TextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceLight,
                            unfocusedContainerColor = SurfaceLight,
                            focusedBorderColor = CoralPrimary,
                            unfocusedBorderColor = BorderLight
                        ),
                        singleLine = true
                    )

                    // Categories Horizontal Scroll
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { viewModel.selectCategory(null) },
                                label = { Text("All Products", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CoralPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = BorderLight,
                                    labelColor = TextDark
                                ),
                                border = null
                            )
                        }

                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat.name,
                                onClick = { viewModel.selectCategory(cat.name) },
                                label = { Text(cat.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CoralPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = BorderLight,
                                    labelColor = TextDark
                                ),
                                border = null
                            )
                        }
                    }

                    // Product Grid Content
                    if (filteredProducts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Empty",
                                    tint = TextMuted,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No products found",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredProducts) { product ->
                                val pVars = variations.filter { it.productId == product.id }
                                val startingPrice = pVars.minOfOrNull { it.price } ?: 0.0

                                POSProductCard(
                                    product = product,
                                    startingPrice = startingPrice,
                                    onProductSelected = { viewModel.selectProductForVariations(product) }
                                )
                            }
                        }
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(BorderLight)
                )

                // Right Column: Active Bill / Cart Summary (width weight 0.85f)
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                        .background(BorderLight.copy(alpha = 0.25f))
                ) {
                    // Cart Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Current Order",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Customer", tint = TextMuted, modifier = Modifier.size(14.dp))
                            Text("Walk-in Customer", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (cartItems.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Empty",
                                    tint = TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Your active bill is empty",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextMuted),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Click catalog items to add packages.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
                            ) {
                                items(cartItems) { item ->
                                    CartItemRow(
                                        item = item,
                                        onQtyIncrease = { viewModel.updateCartQuantity(item.product, item.variation, item.quantity + 1.0) },
                                        onQtyDecrease = { viewModel.updateCartQuantity(item.product, item.variation, item.quantity - 1.0) },
                                        onRemove = { viewModel.removeFromCart(item.product, item.variation) }
                                    )
                                }
                            }
                        }
                    }

                    // Totals / Checkout panel (Bottom of split pane)
                    val subtotal = cartItems.sumOf { it.variation.price * it.quantity }
                    val tax = subtotal * 0.12
                    val totalAmount = subtotal + tax

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                                Text(
                                    text = currencyFormatter.format(subtotal).replace("PHP", "₱"),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("VAT (12%)", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                                Text(
                                    text = currencyFormatter.format(tax).replace("PHP", "₱"),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontWeight = FontWeight.Bold)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = BorderLight, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Amount", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                                Text(
                                    text = currencyFormatter.format(totalAmount).replace("PHP", "₱"),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = CoralPrimary,
                                        fontSize = 18.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (cartItems.isEmpty()) return@Button
                                        confirmationStatus = "UNPAID"
                                        customerNameInput = ""
                                        showConfirmationDialog = true
                                    },
                                    enabled = cartItems.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("unpaid_button")
                                ) {
                                    Text("Unpaid Tab", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        if (cartItems.isEmpty()) return@Button
                                        confirmationStatus = "PAID"
                                        customerNameInput = ""
                                        showConfirmationDialog = true
                                    },
                                    enabled = cartItems.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorPaid),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("paid_button")
                                ) {
                                    Text("Paid Cash", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // PORTRAIT/MOBILE TABBED LAYOUT (Compact screens)
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Branded Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CoralPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = "Agriculture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Alyn's Poultry",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            )
                            Text(
                                text = "POINT OF SALE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = TextMuted,
                                    fontSize = 8.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }

                    // Tabs
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BorderLight)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activePosTab == "CATALOG") SurfaceLight else Color.Transparent)
                                .clickable { activePosTab = "CATALOG" }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Catalog",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (activePosTab == "CATALOG") CoralPrimary else TextMuted
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activePosTab == "CART") SurfaceLight else Color.Transparent)
                                .clickable { activePosTab = "CART" }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Cart",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (activePosTab == "CART") CoralPrimary else TextMuted
                                    )
                                )
                                if (cartItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(CoralPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${cartItems.sumOf { it.quantity.toInt() }}",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (activePosTab == "CATALOG") {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Search product name...", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("search_bar"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceLight,
                            unfocusedContainerColor = SurfaceLight,
                            focusedBorderColor = CoralPrimary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        singleLine = true
                    )

                    // Categories Scroll
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { viewModel.selectCategory(null) },
                                label = { Text("All Products") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CoralPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = BorderLight,
                                    labelColor = TextDark
                                ),
                                border = null
                            )
                        }

                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat.name,
                                onClick = { viewModel.selectCategory(cat.name) },
                                label = { Text(cat.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CoralPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = BorderLight,
                                    labelColor = TextDark
                                ),
                                border = null
                            )
                        }
                    }

                    // Product Grid
                    if (filteredProducts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Empty",
                                    tint = TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No products found",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredProducts) { product ->
                                val pVars = variations.filter { it.productId == product.id }
                                val startingPrice = pVars.minOfOrNull { it.price } ?: 0.0

                                POSProductCard(
                                    product = product,
                                    startingPrice = startingPrice,
                                    onProductSelected = { viewModel.selectProductForVariations(product) }
                                )
                            }
                        }
                    }
                } else {
                    // CART/ACTIVE ORDER VIEW (Screen 2)
                    if (cartItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Empty Cart",
                                    tint = TextMuted,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Your active bill is empty",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add some poultry feeds or equipment from the Catalog.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextMuted,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { activePosTab = "CATALOG" },
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                ) {
                                    Text("Browse Products", color = Color.White)
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                            ) {
                                items(cartItems) { item ->
                                    CartItemRow(
                                        item = item,
                                        onQtyIncrease = { viewModel.updateCartQuantity(item.product, item.variation, item.quantity + 1.0) },
                                        onQtyDecrease = { viewModel.updateCartQuantity(item.product, item.variation, item.quantity - 1.0) },
                                        onRemove = { viewModel.removeFromCart(item.product, item.variation) }
                                    )
                                }
                            }

                            val subtotal = cartItems.sumOf { it.variation.price * it.quantity }
                            val tax = subtotal * 0.12
                            val totalAmount = subtotal + tax

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Subtotal", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                                        Text(
                                            text = currencyFormatter.format(subtotal).replace("PHP", "₱"),
                                            style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("VAT (12%)", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                                        Text(
                                            text = currencyFormatter.format(tax).replace("PHP", "₱"),
                                            style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    HorizontalDivider(color = BorderLight, thickness = 1.dp)

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Total Amount", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                                        Text(
                                            text = currencyFormatter.format(totalAmount).replace("PHP", "₱"),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                color = CoralPrimary,
                                                fontSize = 22.sp
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                confirmationStatus = "UNPAID"
                                                customerNameInput = ""
                                                showConfirmationDialog = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("unpaid_button")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.CreditCard, contentDescription = "Unpaid", tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Unpaid Tab", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                confirmationStatus = "PAID"
                                                customerNameInput = ""
                                                showConfirmationDialog = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorPaid),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("paid_button")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.Payments, contentDescription = "Paid", tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Paid Cash", color = Color.White, fontWeight = FontWeight.Bold)
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

    // SCREEN 3: PRODUCT VARIATIONS DIALOG (VARIED PRICING SELECTION)
    selectedProductForVariations?.let { product ->
        val prodVars = variations.filter { it.productId == product.id }
        var tempSelectedVar by remember { mutableStateOf<ProductVariation?>(prodVars.firstOrNull()) }
        var tempQty by remember { mutableStateOf(1.0) }

        // Reset state on different product load
        LaunchedEffect(product.id) {
            tempSelectedVar = prodVars.firstOrNull()
            tempQty = 1.0
        }

        Dialog(onDismissRequest = { viewModel.selectProductForVariations(null) }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select Variation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark)
                        )
                        IconButton(onClick = { viewModel.selectProductForVariations(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Product Details
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = CoralPrimary),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Current Stock: ${product.stockLevel.toInt()} units",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Grid of variations
                    Text(
                        text = "Varied Package Options:",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextDark, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        prodVars.forEach { variation ->
                            val isSelected = tempSelectedVar?.id == variation.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) CoralPrimary else BorderLight
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(if (isSelected) CoralPrimary.copy(alpha = 0.05f) else Color.Transparent)
                                    .clickable { tempSelectedVar = variation }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { tempSelectedVar = variation },
                                        colors = RadioButtonDefaults.colors(selectedColor = CoralPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = variation.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = TextDark
                                        )
                                    )
                                }

                                Text(
                                    text = currencyFormatter.format(variation.price).replace("PHP", "₱"),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) CoralPrimary else TextDark
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Quantity Counter Section (Screen 3 Bottom Style)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quantity:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BorderLight)
                                .padding(2.dp)
                        ) {
                            IconButton(
                                onClick = { tempQty = (tempQty - 1.0).coerceAtLeast(1.0) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Minus", tint = TextDark, modifier = Modifier.size(16.dp))
                            }

                            Text(
                                text = "${tempQty.toInt()}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            IconButton(
                                onClick = { tempQty = (tempQty + 1.0).coerceIn(1.0, product.stockLevel) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Plus", tint = TextDark, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Add to Bill Button
                    Button(
                        onClick = {
                            val selectedVar = tempSelectedVar
                            if (selectedVar != null) {
                                viewModel.addToCart(product, selectedVar, tempQty)
                                viewModel.selectProductForVariations(null)
                                Toast.makeText(context, "Added to active bill!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("add_in_bill_button"),
                        enabled = tempSelectedVar != null && product.stockLevel > 0
                    ) {
                        Text(
                            text = if (product.stockLevel <= 0) "OUT OF STOCK" else "Add in bill",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // TRANSACTION CONFIRMATION DIALOG (Triggers for PAID and UNPAID status)
    if (showConfirmationDialog) {
        val finalSubtotal = cartItems.sumOf { it.variation.price * it.quantity }
        val finalTax = finalSubtotal * 0.12
        val finalTotal = finalSubtotal + finalTax

        Dialog(onDismissRequest = { showConfirmationDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (confirmationStatus == "PAID") Icons.Default.CheckCircle else Icons.Default.AccountBalanceWallet,
                        contentDescription = confirmationStatus,
                        tint = if (confirmationStatus == "PAID") ColorPaid else ColorUnpaid,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (confirmationStatus == "PAID") "Confirm Paid Transaction" else "Unpaid Debt Tab",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Are you sure you want to save this transaction?",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Total breakdown box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundLight)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Billing:", style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.Bold))
                            Text(
                                text = currencyFormatter.format(finalTotal).replace("PHP", "₱"),
                                style = MaterialTheme.typography.titleMedium.copy(color = CoralPrimary, fontWeight = FontWeight.Black)
                            )
                        }
                    }

                    // MANDATORY INPUT FOR UNPAID CUSTOMER NAME
                    if (confirmationStatus == "UNPAID") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Customer Name (Required):",
                            style = MaterialTheme.typography.labelLarge.copy(color = TextDark, fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customerNameInput,
                            onValueChange = { customerNameInput = it },
                            placeholder = { Text("e.g. Mang Juan", color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoralPrimary,
                                unfocusedBorderColor = BorderLight,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unpaid_customer_name_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { showConfirmationDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextMuted)
                        }

                        Button(
                            onClick = {
                                if (confirmationStatus == "UNPAID" && customerNameInput.isBlank()) {
                                    Toast.makeText(context, "Please enter customer name to track unpaid tabs!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                viewModel.finalizeTransaction(confirmationStatus, customerNameInput) {
                                    showConfirmationDialog = false
                                    activePosTab = "CATALOG"
                                    Toast.makeText(context, "Transaction successfully processed!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (confirmationStatus == "PAID") ColorPaid else ColorUnpaid
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("confirm_transaction_button")
                        ) {
                            Text("Confirm", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun POSProductCard(
    product: Product,
    startingPrice: Double,
    onProductSelected: () -> Unit
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

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
            .fillMaxWidth()
            .clickable { onProductSelected() }
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Box for image placeholder / emoji
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BackgroundLight),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 42.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = product.category,
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currencyFormatter.format(startingPrice).replace("PHP", "₱"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = CoralPrimary, fontSize = 14.sp)
                )

                // Stock indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (product.stockLevel <= product.lowStockThreshold) ColorUnpaid.copy(alpha = 0.1f)
                            else ColorPaid.copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Stock: ${product.stockLevel.toInt()}",
                        color = if (product.stockLevel <= product.lowStockThreshold) ColorUnpaid else ColorPaid,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: com.example.viewmodel.CartItem,
    onQtyIncrease: () -> Unit,
    onQtyDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini emoji thumbnail
            val emoji = when {
                item.product.name.contains("Booster", ignoreCase = true) -> "🐣"
                item.product.name.contains("Grower", ignoreCase = true) -> "🐓"
                item.product.name.contains("Layer", ignoreCase = true) -> "🥚"
                item.product.name.contains("Vitamin", ignoreCase = true) -> "💊"
                item.product.name.contains("Feeder", ignoreCase = true) -> "🥣"
                item.product.name.contains("Waterer", ignoreCase = true) -> "🪣"
                else -> "🌾"
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundLight),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pack: ${item.variation.name}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
                Text(
                    text = currencyFormatter.format(item.variation.price).replace("PHP", "₱"),
                    style = MaterialTheme.typography.bodySmall.copy(color = CoralPrimary, fontWeight = FontWeight.Bold)
                )
            }

            // Quantity buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BorderLight)
                    .padding(2.dp)
            ) {
                IconButton(
                    onClick = onQtyDecrease,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TextDark, modifier = Modifier.size(12.dp))
                }

                Text(
                    text = "${item.quantity.toInt()}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark),
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                IconButton(
                    onClick = onQtyIncrease,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = TextDark, modifier = Modifier.size(12.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Delete button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ColorUnpaid, modifier = Modifier.size(18.dp))
            }
        }
    }
}
