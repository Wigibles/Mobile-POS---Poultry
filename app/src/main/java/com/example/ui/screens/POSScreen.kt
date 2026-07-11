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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
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
import androidx.compose.ui.graphics.Brush
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
    val customerSuggestions by viewModel.customerSuggestions.collectAsState()
    val isProcessing by viewModel.isProcessingTransaction.collectAsState()

    // Cart bottom sheet
    var showCartSheet by remember { mutableStateOf(false) }
    val cartSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Variant picker bottom sheet
    var variantSheetProduct by remember { mutableStateOf<Product?>(null) }
    val variantSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Confirmation dialog
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var confirmationStatus by remember { mutableStateOf("PAID") }
    var customerNameInput by remember { mutableStateOf("") }
    var showCustomerDropdown by remember { mutableStateOf(false) }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { product ->
            val matchesCategory = selectedCategory == null || product.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() || product.name.contains(searchQuery, ignoreCase = true) || product.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Direct tap: single-variant → instant add; multi-variant → picker sheet
    fun onProductTapped(product: Product) {
        val productVars = variations.filter { it.productId == product.id }
        if (productVars.isEmpty()) return
        if (productVars.size == 1 && product.stockLevel > 0) {
            viewModel.addToCart(product, productVars.first(), 1.0)
            Toast.makeText(context, "${product.name} added", Toast.LENGTH_SHORT).show()
        } else {
            variantSheetProduct = product
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(BackgroundLight)
    ) {
        val columns = when {
            maxWidth < 360.dp -> 2
            maxWidth < 600.dp -> 2
            else -> 3
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── HEADER ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(CoralPrimary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Storefront, contentDescription = "Store", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Alyn's Poultry", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        Text("POINT OF SALE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = TextMuted, fontSize = 8.sp, letterSpacing = 1.sp))
                    }
                }
                IconButton(onClick = { viewModel.navigateTo("TRANSACTIONS") }, modifier = Modifier.size(36.dp).clip(CircleShape).background(BorderLight)) {
                    Icon(Icons.Default.History, contentDescription = "History", tint = TextDark, modifier = Modifier.size(18.dp))
                }
            }

            // ── SEARCH ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search products…", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Default.Close, "Clear", tint = TextMuted, modifier = Modifier.size(18.dp)) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceLight, unfocusedContainerColor = SurfaceLight, focusedBorderColor = CoralPrimary, unfocusedBorderColor = BorderLight),
                singleLine = true
            )

            // ── CATEGORIES ──
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(selected = selectedCategory == null, onClick = { viewModel.selectCategory(null) },
                        label = { Text("All", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CoralPrimary, selectedLabelColor = Color.White, containerColor = BorderLight, labelColor = TextDark), border = null)
                }
                items(categories) { cat ->
                    FilterChip(selected = selectedCategory == cat.name, onClick = { viewModel.selectCategory(cat.name) },
                        label = { Text(cat.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CoralPrimary, selectedLabelColor = Color.White, containerColor = BorderLight, labelColor = TextDark), border = null)
                }
            }

            // ── PRODUCT GRID ──
            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory, null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(80.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No products yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextMuted))
                        Spacer(Modifier.height(4.dp))
                        Text("Add products in Inventory first", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val productVars = variations.filter { it.productId == product.id }
                        POSProductCard(
                            product = product,
                            variations = productVars,
                            currencyFormatter = currencyFormatter,
                            onTap = { onProductTapped(product) }
                        )
                    }
                }
            }

            // ── CART SUMMARY BAR (tap to open cart sheet) ──
            val cartCount = cartItems.sumOf { it.quantity }
            val cartTotal = cartItems.sumOf { it.variation.price * it.quantity }

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { if (cartItems.isNotEmpty()) showCartSheet = true },
                color = if (cartItems.isNotEmpty()) CoralPrimary else SurfaceLight,
                shadowElevation = if (cartItems.isNotEmpty()) 8.dp else 0.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShoppingCart, null, tint = if (cartItems.isNotEmpty()) Color.White else TextMuted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (cartItems.isEmpty()) "Cart is empty" else "${if (cartCount == cartCount.toLong().toDouble()) cartCount.toLong().toString() else String.format("%.1f", cartCount)} item${if (cartCount > 1.0) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (cartItems.isNotEmpty()) Color.White else TextMuted)
                        )
                    }
                    if (cartItems.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currencyFormatter.format(cartTotal).replace("PHP", "₱"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = Color.White))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.KeyboardArrowUp, "Open cart", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    // CART BOTTOM SHEET (swipe up / tap cart bar)
    // ═══════════════════════════════════════════
    if (showCartSheet) {
        val subtotal = cartItems.sumOf { it.variation.price * it.quantity }
        val totalAmount = subtotal

        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            sheetState = cartSheetState,
            containerColor = SurfaceLight,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Current Order", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                        Text("Swipe down to close", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                    }
                    IconButton(onClick = { showCartSheet = false }) { Icon(Icons.Default.Close, "Close", tint = TextMuted) }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderLight)
                Spacer(Modifier.height(8.dp))

                if (cartItems.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("Cart is empty", color = TextMuted) }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderLight)
                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                    Text(currencyFormatter.format(subtotal).replace("PHP", "₱"), style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(4.dp))
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = BorderLight)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                    Text(currencyFormatter.format(totalAmount).replace("PHP", "₱"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = CoralPrimary, fontSize = 22.sp))
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        if (cartItems.isEmpty() || isProcessing) return@Button
                        showCartSheet = false
                        confirmationStatus = "UNPAID"; customerNameInput = ""
                        viewModel.loadCustomerSuggestions()
                        showConfirmationDialog = true
                    }, enabled = cartItems.isNotEmpty() && !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Unpaid Tab", color = Color.White, fontWeight = FontWeight.Bold) }
                    Button(onClick = {
                        if (cartItems.isEmpty() || isProcessing) return@Button
                        showCartSheet = false
                        confirmationStatus = "PAID"; customerNameInput = ""
                        showConfirmationDialog = true
                    }, enabled = cartItems.isNotEmpty() && !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = ColorPaid),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Paid Cash", color = Color.White, fontWeight = FontWeight.Bold) }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ═══════════════════════════════════
    // VARIANT PICKER BOTTOM SHEET
    // ═══════════════════════════════════
    if (variantSheetProduct != null) {
        val product = variantSheetProduct!!
        val productVars = variations.filter { it.productId == product.id }
        var selectedVar by remember(product.id) { mutableStateOf(productVars.firstOrNull()) }
        var qty by remember(product.id) { mutableStateOf(1.0) }

        ModalBottomSheet(
            onDismissRequest = { variantSheetProduct = null },
            sheetState = variantSheetState,
            containerColor = SurfaceLight,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Select Package", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                    IconButton(onClick = { variantSheetProduct = null }) { Icon(Icons.Default.Close, "Close") }
                }
                Text(product.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = CoralPrimary))
                Text("Stock: ${if (product.stockLevel == product.stockLevel.toLong().toDouble()) product.stockLevel.toLong().toString() else String.format("%.1f", product.stockLevel)} units", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))

                Spacer(Modifier.height(16.dp))

                productVars.forEach { variation ->
                    val isSelected = selectedVar?.id == variation.id
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) CoralPrimary else BorderLight, RoundedCornerShape(12.dp))
                            .background(if (isSelected) CoralPrimary.copy(alpha = 0.05f) else Color.Transparent)
                            .clickable { selectedVar = variation }.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(isSelected, onClick = { selectedVar = variation }, colors = RadioButtonDefaults.colors(selectedColor = CoralPrimary))
                            Spacer(Modifier.width(6.dp))
                            Text(variation.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = TextDark))
                        }
                        Text(currencyFormatter.format(variation.price).replace("PHP", "₱"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = if (isSelected) CoralPrimary else TextDark))
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantity:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BorderLight).padding(2.dp)) {
                        IconButton(onClick = { qty = (qty - 1.0).coerceAtLeast(1.0) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Remove, null, tint = TextDark, modifier = Modifier.size(16.dp)) }
                        Text("${qty.toLong()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark), modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { qty = (qty + 1.0).coerceAtMost(product.stockLevel) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, null, tint = TextDark, modifier = Modifier.size(16.dp)) }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(onClick = {
                    selectedVar?.let { viewModel.addToCart(product, it, qty) }
                    variantSheetProduct = null
                    Toast.makeText(context, "Added to cart!", Toast.LENGTH_SHORT).show()
                }, enabled = selectedVar != null && product.stockLevel > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text(if (product.stockLevel <= 0) "OUT OF STOCK" else "Add to Cart", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ═══════════════════════════════════════
    // TRANSACTION CONFIRMATION DIALOG
    // ═══════════════════════════════════════
    if (showConfirmationDialog) {
        val finalTotal = cartItems.sumOf { it.variation.price * it.quantity }

        Dialog(onDismissRequest = { showConfirmationDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (confirmationStatus == "PAID") Icons.Default.CheckCircle else Icons.Default.AccountBalanceWallet,
                        contentDescription = null, tint = if (confirmationStatus == "PAID") ColorPaid else ColorUnpaid, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(if (confirmationStatus == "PAID") "Confirm Paid Transaction" else "Unpaid Debt Tab",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                    Spacer(Modifier.height(8.dp))
                    Text("Confirm this transaction?", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))

                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundLight).padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Billing:", style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.Bold))
                            Text(currencyFormatter.format(finalTotal).replace("PHP", "₱"), style = MaterialTheme.typography.titleMedium.copy(color = CoralPrimary, fontWeight = FontWeight.Black))
                        }
                    }

                    if (confirmationStatus == "UNPAID") {
                        Spacer(Modifier.height(16.dp))
                        Text("Customer Name (Required):", style = MaterialTheme.typography.labelLarge.copy(color = TextDark, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = customerNameInput,
                                onValueChange = { customerNameInput = it; showCustomerDropdown = it.isNotEmpty() },
                                placeholder = { Text("e.g. Mang Juan", color = TextMuted.copy(alpha = 0.6f)) }, singleLine = true, shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CoralPrimary, unfocusedBorderColor = BorderLight,
                                    focusedContainerColor = BackgroundLight, unfocusedContainerColor = BackgroundLight,
                                    focusedTextColor = TextDark, unfocusedTextColor = TextDark
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("unpaid_customer_name_input"))
                            val filtered = if (customerNameInput.isNotBlank()) customerSuggestions.filter { it.contains(customerNameInput, ignoreCase = true) } else customerSuggestions
                            if (showCustomerDropdown && filtered.isNotEmpty()) {
                                Card(Modifier.fillMaxWidth().padding(top = 2.dp), shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceLight), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                                    Column { filtered.take(5).forEach { s ->
                                        Row(Modifier.fillMaxWidth().clickable { customerNameInput = s; showCustomerDropdown = false }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Person, null, tint = TextMuted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(s, style = MaterialTheme.typography.bodyMedium.copy(color = TextDark))
                                        }
                                    }}
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { showConfirmationDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancel", color = TextMuted) }
                        Button(onClick = {
                            if (isProcessing) return@Button
                            if (confirmationStatus == "UNPAID" && customerNameInput.isBlank()) {
                                Toast.makeText(context, "Please enter customer name!", Toast.LENGTH_LONG).show(); return@Button
                            }
                            viewModel.finalizeTransaction(confirmationStatus, customerNameInput) {
                                showConfirmationDialog = false
                                Toast.makeText(context, "Transaction processed!", Toast.LENGTH_SHORT).show()
                            }
                        }, enabled = !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = if (confirmationStatus == "PAID") ColorPaid else ColorUnpaid),
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1.5f).testTag("confirm_transaction_button")
                        ) { Text("Confirm", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
fun POSProductCard(
    product: Product,
    variations: List<ProductVariation>,
    currencyFormatter: NumberFormat,
    onTap: () -> Unit
) {
    val emoji = when {
        product.name.contains("Booster", ignoreCase = true) -> "🐣"
        product.name.contains("Grower", ignoreCase = true) -> "🐓"
        product.name.contains("Layer", ignoreCase = true) -> "🥚"
        product.name.contains("Vitamin", ignoreCase = true) -> "💊"
        product.name.contains("Feeder", ignoreCase = true) -> "🥣"
        product.name.contains("Waterer", ignoreCase = true) -> "🪣"
        product.name.contains("Antibiotic", ignoreCase = true) || product.name.contains("Medicine", ignoreCase = true) -> "💉"
        else -> "🌾"
    }

    // Compact price format: "₱47" for whole pesos, "₱22.50" for centavos
    fun formatCompact(amount: Double): String {
        val formatted = currencyFormatter.format(amount).replace("PHP", "₱")
        return if (amount == amount.toLong().toDouble()) formatted.removeSuffix(".00") else formatted
    }

    val priceText = when {
        variations.isEmpty() -> "—"
        variations.size == 1 -> formatCompact(variations.first().price)
        else -> {
            val min = variations.minOf { it.price }
            val max = variations.maxOf { it.price }
            if (min == max) formatCompact(min)
            else "${formatCompact(min)} – ${formatCompact(max)}"
        }
    }

    val variantCount = variations.size
    val variantHint = if (variantCount > 1) "${variantCount} variants" else ""

    val isLowStock = product.stockLevel <= product.lowStockThreshold
    val isOutOfStock = product.stockLevel <= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(containerColor = if (isOutOfStock) SurfaceLight.copy(alpha = 0.5f) else SurfaceLight),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isLowStock && !isOutOfStock) BorderStroke(1.dp, ColorUnpaid.copy(alpha = 0.2f)) else null
    ) {
        Column {
            // Product image with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                CoralPrimary.copy(alpha = 0.08f),
                                CoralPrimary.copy(alpha = 0.02f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 32.sp)
                if (isOutOfStock) {
                    Text("OUT OF STOCK", fontSize = 8.sp, fontWeight = FontWeight.Black, color = ColorUnpaid,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp).padding(top = 8.dp, bottom = 8.dp)) {
                // Product name
                Text(
                    product.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = if (isOutOfStock) TextMuted else TextDark),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                // Price + variant badge — stacked vertically for clarity
                Text(
                    priceText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, color = CoralPrimary, fontSize = 13.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (variantHint.isNotEmpty()) {
                    Text(
                        variantHint,
                        style = MaterialTheme.typography.labelSmall.copy(color = CoralPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Stock badge — always at the bottom
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(when { isOutOfStock -> ColorUnpaid; isLowStock -> ColorUnpaid; else -> ColorPaid })
                    )
                    Text(
                        text = if (isOutOfStock) "Sold out" else "Stock: ${if (product.stockLevel == product.stockLevel.toLong().toDouble()) product.stockLevel.toLong().toString() else String.format("%.1f", product.stockLevel)}",
                        color = when { isOutOfStock -> ColorUnpaid; isLowStock -> ColorUnpaid; else -> TextMuted },
                        fontSize = 10.sp, fontWeight = FontWeight.Medium
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
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))

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
                    text = item.variation.name,
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
                    text = if (item.quantity == item.quantity.toLong().toDouble()) "${item.quantity.toLong()}" else String.format("%.1f", item.quantity),
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
