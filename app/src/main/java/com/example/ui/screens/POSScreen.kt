package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

/** Formats a quantity/multiplier without a trailing ".0" for whole numbers. */
private fun fmtQty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)

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

    // Different packages of the same product consume different amounts of base stock
    // (e.g. "per Sack" = ×50). A quantity is only ever safe to add if the BASE UNITS it
    // needs — quantity × multiplier, plus whatever's already reserved by other lines of
    // this same product sitting in the cart — fit within the product's current stock.
    fun remainingBaseUnits(productId: Int, currentStock: Double, excludingVariationId: Int? = null): Double {
        val reserved = cartItems
            .filter { it.product.id == productId && it.variation.id != excludingVariationId }
            .sumOf { it.quantity * it.variation.multiplier }
        return (currentStock - reserved).coerceAtLeast(0.0)
    }

    // Direct tap: single-variant → instant add; multi-variant → picker sheet
    fun onProductTapped(product: Product) {
        val productVars = variations.filter { it.productId == product.id }
        if (productVars.isEmpty()) return
        if (productVars.size == 1 && product.stockLevel > 0) {
            val variation = productVars.first()
            val remaining = remainingBaseUnits(product.id, product.stockLevel)
            if (remaining >= variation.multiplier) {
                viewModel.addToCart(product, variation, 1.0)
                Toast.makeText(context, "${product.name} added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Not enough stock for ${variation.name} — needs ${fmtQty(variation.multiplier)}, ${fmtQty(remaining)} left", Toast.LENGTH_LONG).show()
            }
        } else {
            variantSheetProduct = product
        }
    }

    val cartCount = cartItems.sumOf { it.quantity }
    val cartTotal = cartItems.sumOf { it.variation.price * it.quantity }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(BackgroundLight)
    ) {
        val columns = when {
            maxWidth < 360.dp -> 2
            maxWidth < 600.dp -> 2
            else -> 3
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── HEADER — card-wrapped brand row + tonal action button ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .shadow(elevation = 3.dp, shape = ShapeMD, clip = false)
                        .clip(ShapeMD)
                        .background(SurfaceLight)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Box(modifier = Modifier.size(38.dp).clip(ShapeSM).background(BrandPrimaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Storefront, contentDescription = "Store", tint = BrandPrimary, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Alyn's Poultry", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        Text("SALES LOG", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = TextMuted, fontSize = 10.sp, letterSpacing = 1.5.sp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(ShapeMD)
                        .background(SurfaceContainer)
                        .border(1.dp, BorderLight, ShapeMD)
                        .clickable { viewModel.navigateTo("TRANSACTIONS") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.History, contentDescription = "History", tint = TextDark, modifier = Modifier.size(20.dp))
                }
            }

            // ── SEARCH ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search products…", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Default.Close, "Clear", tint = TextMuted, modifier = Modifier.size(18.dp)) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = ShapeXL,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceLight,
                    unfocusedContainerColor = SurfaceContainer,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )

            // ── CATEGORIES — plain text segmented chips, bordered when inactive ──
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    val selected = selectedCategory == null
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White, containerColor = SurfaceLight, labelColor = TextDark),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = selected,
                            borderColor = BorderLight, selectedBorderColor = Color.Transparent, borderWidth = 1.dp
                        )
                    )
                }
                items(categories) { cat ->
                    val selected = selectedCategory == cat.name
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectCategory(cat.name) },
                        label = { Text(cat.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White, containerColor = SurfaceLight, labelColor = TextDark),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = selected,
                            borderColor = BorderLight, selectedBorderColor = Color.Transparent, borderWidth = 1.dp
                        )
                    )
                }
            }

            // ── PRODUCT GRID ──
            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(SurfaceContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Inventory, null, tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
                        }
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
                    // Extra bottom clearance — the floating cart pill overlaps this area now
                    // that there's no full-width bar reserving space in the layout flow.
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
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
        }

        // ── FLOATING CART PILL — replaces the old full-width bar; only present when
        // there's something to check out, so it never crowds an empty screen ──
        AnimatedVisibility(
            visible = cartItems.isNotEmpty(),
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp)
        ) {
            Surface(
                onClick = { showCartSheet = true },
                shape = ShapeMD,
                color = BrandPrimary,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Text(fmtQty(cartCount), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatPeso(currencyFormatter, cartTotal), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    // CART BOTTOM SHEET (swipe up / tap the floating pill)
    // ═══════════════════════════════════════════
    if (showCartSheet) {
        val subtotal = cartItems.sumOf { it.variation.price * it.quantity }
        val totalAmount = subtotal

        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            sheetState = cartSheetState,
            containerColor = SurfaceLight,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
                            // Use the live stock figure (not the possibly-stale snapshot
                            // captured in item.product when it was first added) so the cap
                            // stays correct if stock changed since.
                            val liveStock = products.find { it.id == item.product.id }?.stockLevel ?: item.product.stockLevel
                            val remaining = remainingBaseUnits(item.product.id, liveStock, item.variation.id)
                            val canIncrease = (item.quantity + 1.0) * item.variation.multiplier <= remaining + 1e-9
                            CartItemRow(
                                item = item,
                                canIncrease = canIncrease,
                                onQtyIncrease = {
                                    if (canIncrease) viewModel.updateCartQuantity(item.product, item.variation, item.quantity + 1.0)
                                    else Toast.makeText(context, "Not enough stock left for ${item.variation.name}", Toast.LENGTH_SHORT).show()
                                },
                                onQtyDecrease = { viewModel.updateCartQuantity(item.product, item.variation, item.quantity - 1.0) },
                                onRemove = { viewModel.removeFromCart(item.product, item.variation) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Order summary — a tinted card instead of bare divider rows, so the
                // total reads as the receipt's headline rather than just another line.
                Box(Modifier.fillMaxWidth().clip(ShapeMD).background(SurfaceContainer).padding(16.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                            Text(formatPeso(currencyFormatter, subtotal), style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontWeight = FontWeight.Bold))
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = BorderLight)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                            Text(formatPeso(currencyFormatter, totalAmount), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = BrandPrimary, fontSize = 22.sp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // "Log as Paid" is the everyday action so it gets the filled, dominant button;
                // unpaid tabs are routine too (not an error), so they get a calm tonal style
                // rather than alarm-red fill.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        if (cartItems.isEmpty() || isProcessing) return@Button
                        showCartSheet = false
                        confirmationStatus = "UNPAID"; customerNameInput = ""
                        viewModel.loadCustomerSuggestions()
                        showConfirmationDialog = true
                    }, enabled = cartItems.isNotEmpty() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid.copy(alpha = 0.12f), contentColor = ColorUnpaid),
                        shape = ShapeSM, modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Log as Unpaid", color = ColorUnpaid, fontWeight = FontWeight.Bold) }
                    Button(onClick = {
                        if (cartItems.isEmpty() || isProcessing) return@Button
                        showCartSheet = false
                        confirmationStatus = "PAID"; customerNameInput = ""
                        showConfirmationDialog = true
                    }, enabled = cartItems.isNotEmpty() && !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = ColorPaid),
                        shape = ShapeSM, modifier = Modifier.weight(1.4f).height(52.dp)
                    ) { Text("Log as Paid", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
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

        // How many BASE units this package selection actually needs — not raw product
        // stock. A "per Sack" pick (×50) against 5 units in stock must gate on 50, not 5.
        val remainingForSelected = remainingBaseUnits(product.id, product.stockLevel, selectedVar?.id)
        val selectedMultiplier = selectedVar?.multiplier ?: 1.0
        val maxAdditionalQty = if (selectedMultiplier > 0) kotlin.math.floor(remainingForSelected / selectedMultiplier + 1e-9) else 0.0
        val hasEnoughStock = selectedVar != null && qty * selectedMultiplier <= remainingForSelected + 1e-9

        ModalBottomSheet(
            onDismissRequest = { variantSheetProduct = null },
            sheetState = variantSheetState,
            containerColor = SurfaceLight,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Select Package", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                    IconButton(onClick = { variantSheetProduct = null }) { Icon(Icons.Default.Close, "Close") }
                }
                Text(product.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = BrandPrimary))
                Text("Stock: ${fmtQty(product.stockLevel)} units", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))

                Spacer(Modifier.height(16.dp))

                // Selector cards — filled brand color when picked, with a check badge,
                // instead of a plain radio row. Reads faster at a glance from arm's length.
                productVars.forEach { variation ->
                    val isSelected = selectedVar?.id == variation.id
                    // Selecting a different package resets quantity — a leftover qty from
                    // a small-multiplier package is not a safe default for a bigger one.
                    val selectThis = { selectedVar = variation; qty = 1.0 }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(ShapeSM)
                            .background(if (isSelected) BrandPrimary else SurfaceLight)
                            .border(1.dp, if (isSelected) Color.Transparent else BorderLight, ShapeSM)
                            .clickable { selectThis() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            variation.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else TextDark)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatPeso(currencyFormatter, variation.price),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = if (isSelected) Color.White else BrandPrimary)
                            )
                            if (isSelected) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantity:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(ShapeSM).background(SurfaceContainer).padding(2.dp)) {
                        IconButton(onClick = { qty = (qty - 1.0).coerceAtLeast(1.0) }, enabled = qty > 1.0, modifier = Modifier.size(46.dp)) { Icon(Icons.Default.Remove, "Decrease quantity", tint = TextDark, modifier = Modifier.size(20.dp)) }
                        Text("${qty.toLong()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextDark), modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { qty += 1.0 }, enabled = qty < maxAdditionalQty, modifier = Modifier.size(46.dp)) { Icon(Icons.Default.Add, "Increase quantity", tint = TextDark, modifier = Modifier.size(20.dp)) }
                    }
                }

                if (selectedVar != null && !hasEnoughStock) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Only ${fmtQty(maxAdditionalQty)} \"${selectedVar!!.name}\" available — needs ${fmtQty(qty * selectedMultiplier)} units, ${fmtQty(remainingForSelected)} left",
                        style = MaterialTheme.typography.bodySmall.copy(color = ColorUnpaid, fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(onClick = {
                    if (!hasEnoughStock) return@Button
                    selectedVar?.let { viewModel.addToCart(product, it, qty) }
                    variantSheetProduct = null
                    Toast.makeText(context, "Added to cart!", Toast.LENGTH_SHORT).show()
                }, enabled = selectedVar != null && hasEnoughStock,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = ShapeSM, modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        when {
                            product.stockLevel <= 0 -> "OUT OF STOCK"
                            selectedVar != null && !hasEnoughStock -> "Not Enough Stock"
                            else -> "Add to Cart"
                        },
                        color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ═══════════════════════════════════════
    // TRANSACTION CONFIRMATION DIALOG
    // ═══════════════════════════════════════
    if (showConfirmationDialog) {
        val finalTotal = cartItems.sumOf { it.variation.price * it.quantity }
        val statusColor = if (confirmationStatus == "PAID") ColorPaid else ColorUnpaid

        Dialog(onDismissRequest = { showConfirmationDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = ShapeLG,
                colors = CardDefaults.cardColors(containerColor = SurfaceLight), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (confirmationStatus == "PAID") Icons.Default.CheckCircle else Icons.Default.AccountBalanceWallet,
                            contentDescription = null, tint = statusColor, modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (confirmationStatus == "PAID") "Log Paid Sale" else "Log Unpaid Tab",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (confirmationStatus == "PAID") "This sale will be added to the records as paid."
                        else "This will be recorded as an open tab under the customer's name.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))

                    Box(Modifier.fillMaxWidth().clip(ShapeSM).background(SurfaceContainer).padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Billing:", style = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontWeight = FontWeight.Bold))
                            Text(formatPeso(currencyFormatter, finalTotal), style = MaterialTheme.typography.titleMedium.copy(color = BrandPrimary, fontWeight = FontWeight.Black))
                        }
                    }

                    if (confirmationStatus == "UNPAID") {
                        Spacer(Modifier.height(16.dp))
                        Text("Customer Name (Required):", style = MaterialTheme.typography.labelLarge.copy(color = TextDark, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = customerNameInput,
                                onValueChange = { customerNameInput = it; showCustomerDropdown = it.isNotEmpty() },
                                placeholder = { Text("e.g. Mang Juan", color = TextMuted.copy(alpha = 0.6f)) }, singleLine = true, shape = ShapeXS,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandPrimary, unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = SurfaceContainer, unfocusedContainerColor = SurfaceContainer,
                                    focusedTextColor = TextDark, unfocusedTextColor = TextDark
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("unpaid_customer_name_input"))
                            val filtered = if (customerNameInput.isNotBlank()) customerSuggestions.filter { it.contains(customerNameInput, ignoreCase = true) } else customerSuggestions
                            if (showCustomerDropdown && filtered.isNotEmpty()) {
                                Card(Modifier.fillMaxWidth().padding(top = 2.dp), shape = ShapeXS,
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
                        }, enabled = !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                            shape = ShapeSM, modifier = Modifier.weight(1.5f).height(48.dp).testTag("confirm_transaction_button")
                        ) { Text(if (isProcessing) "Saving…" else "Confirm", color = Color.White, fontWeight = FontWeight.Bold) }
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
    // Compact price format: "₱47" for whole pesos, "₱22.50" for centavos
    fun formatCompact(amount: Double): String {
        val formatted = formatPeso(currencyFormatter, amount)
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
    val isLowStock = product.stockLevel <= product.lowStockThreshold
    val isOutOfStock = product.stockLevel <= 0
    val stockLabel = when {
        isOutOfStock -> "Out"
        isLowStock -> "Low · ${fmtQty(product.stockLevel)}"
        else -> fmtQty(product.stockLevel)
    }
    val stockColor = when {
        isOutOfStock -> ColorUnpaid
        isLowStock -> ColorLowStockText
        else -> ColorPaid
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .shadow(elevation = if (isOutOfStock) 0.dp else 1.dp, shape = ShapeMD, clip = false)
            .pressScale(onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = if (isOutOfStock) SurfaceLight.copy(alpha = 0.5f) else SurfaceLight),
        shape = ShapeMD,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLowStock && !isOutOfStock) ColorLowStock.copy(alpha = 0.4f) else BorderLight
        )
    ) {
        Column {
            // Product image tile — flat, neutral tone (no per-category color variety) so
            // the grid reads as structured inventory data, not a colorful storefront.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(SurfaceContainer)
            ) {
                Text(text = productEmoji(product.name), fontSize = 32.sp, modifier = Modifier.align(Alignment.Center))

                if (isOutOfStock) {
                    StatusPill(
                        text = "SOLD OUT", color = ColorUnpaid, showDot = false, fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(26.dp)
                            .clip(ShapeXS)
                            .background(BrandPrimary)
                            .pressScale(onClick = onTap),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add to cart", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp).padding(top = 8.dp, bottom = 8.dp)) {
                // The title is the first thing a cashier scans for — heaviest weight and
                // largest type on the card, ahead of price, so it reads at arm's length.
                Text(
                    product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = if (isOutOfStock) TextMuted else TextDark,
                        fontSize = 15.sp,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        priceText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, color = BrandPrimary, fontSize = 14.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (variantCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${variantCount}v",
                            style = MaterialTheme.typography.labelSmall.copy(color = BrandPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.clip(ShapeXS).background(BrandPrimaryContainer).padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                StatusPill(text = stockLabel, color = stockColor, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: com.example.viewmodel.CartItem,
    onQtyIncrease: () -> Unit,
    onQtyDecrease: () -> Unit,
    onRemove: () -> Unit,
    canIncrease: Boolean = true
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))

    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = 2.dp, shape = ShapeMD, clip = false),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeMD,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ShapeSM)
                    .background(BrandPrimaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = productEmoji(item.product.name), fontSize = 22.sp)
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
                    text = formatPeso(currencyFormatter, item.variation.price),
                    style = MaterialTheme.typography.bodySmall.copy(color = BrandPrimary, fontWeight = FontWeight.Bold)
                )
            }

            // Quantity stepper — 40dp targets so it's tappable at speed; when quantity is 1
            // the minus becomes a delete (trash) affordance so removal is a deliberate,
            // separate gesture instead of a tiny red button 8dp from "+".
            val isLastUnit = item.quantity <= 1.0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(ShapeSM)
                    .background(SurfaceContainer)
                    .padding(2.dp)
            ) {
                IconButton(
                    onClick = { if (isLastUnit) onRemove() else onQtyDecrease() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (isLastUnit) Icons.Default.DeleteOutline else Icons.Default.Remove,
                        contentDescription = if (isLastUnit) "Remove from cart" else "Decrease quantity",
                        tint = if (isLastUnit) ColorUnpaid else TextDark,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = if (item.quantity == item.quantity.toLong().toDouble()) "${item.quantity.toLong()}" else String.format("%.1f", item.quantity),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark),
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                IconButton(
                    onClick = onQtyIncrease,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase quantity", tint = if (canIncrease) TextDark else TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
