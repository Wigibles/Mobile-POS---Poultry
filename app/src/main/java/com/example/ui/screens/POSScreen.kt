package com.example.ui.screens

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream
import java.io.IOException
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
    val isOnline by viewModel.isOnline.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()

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

    // Transaction completed popup
    var showConfirmedDialog by remember { mutableStateOf(false) }
    var confirmedStatus by remember { mutableStateOf("PAID") }
    var confirmedCustomer by remember { mutableStateOf("") }
    var confirmedTotal by remember { mutableStateOf(0.0) }
    var confirmedCartItems by remember { mutableStateOf<List<com.example.viewmodel.CartItem>>(emptyList()) }

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
        if (productVars.size == 1) {
            val variation = productVars.first()
            viewModel.addToCart(product, variation, 1.0)
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

            // Offline indicator — compact chip, only visible when offline
            if (!isOnline) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = ColorUnpaid.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = ColorUnpaid,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (pendingSyncCount > 0) "$pendingSyncCount record(s) pending sync"
                                else "Offline — saved locally",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ColorUnpaid,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
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
                            CartItemRow(
                                item = item,
                                canIncrease = true,
                                onQtyIncrease = {
                                    viewModel.updateCartQuantity(item.product, item.variation, item.quantity + 1.0)
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
                        IconButton(onClick = { qty += 1.0 }, modifier = Modifier.size(46.dp)) { Icon(Icons.Default.Add, "Increase quantity", tint = TextDark, modifier = Modifier.size(20.dp)) }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(onClick = {
                    selectedVar?.let { viewModel.addToCart(product, it, qty) }
                    variantSheetProduct = null
                }, enabled = selectedVar != null,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = ShapeSM, modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        "Add to Cart",
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
                                confirmedStatus = confirmationStatus
                                confirmedCustomer = customerNameInput
                                confirmedTotal = finalTotal
                                confirmedCartItems = cartItems.toList()
                                showConfirmedDialog = true
                            }
                        }, enabled = !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                            shape = ShapeSM, modifier = Modifier.weight(1.5f).height(48.dp).testTag("confirm_transaction_button")
                        ) { Text(if (isProcessing) "Saving…" else "Confirm", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // TRANSACTION CONFIRMED POPUP
    // ═══════════════════════════════════════
    if (showConfirmedDialog) {
        val statusColor = if (confirmedStatus == "PAID") ColorPaid else ColorUnpaid
        val statusLabel = if (confirmedStatus == "PAID") "Paid" else "Unpaid"

        Dialog(onDismissRequest = { showConfirmedDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = ShapeLG,
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Success checkmark
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Confirmed",
                            tint = statusColor,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Transaction Confirmed",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = TextDark
                        )
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Status: $statusLabel",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    if (confirmedCustomer.isNotBlank()) {
                        Text(
                            "Customer: $confirmedCustomer",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Order summary card
                    Box(
                        Modifier.fillMaxWidth().clip(ShapeSM).background(SurfaceContainer).padding(14.dp)
                    ) {
                        Column {
                            Text(
                                "Order Summary",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = BorderLight)
                            Spacer(Modifier.height(8.dp))

                            confirmedCartItems.forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${item.product.name} — ${item.variation.name}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "× ${fmtQty(item.quantity)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = BorderLight)
                            Spacer(Modifier.height(8.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Total",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = TextDark
                                    )
                                )
                                Text(
                                    formatPeso(currencyFormatter, confirmedTotal),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = BrandPrimary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action buttons
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Print Receipt button (optional)
                        OutlinedButton(
                            onClick = {
                                generateReceiptPdf(
                                    context = context,
                                    status = confirmedStatus,
                                    customer = confirmedCustomer,
                                    items = confirmedCartItems,
                                    total = confirmedTotal,
                                    currencyFormatter = currencyFormatter
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = ShapeSM
                        ) {
                            Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Receipt", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showConfirmedDialog = false },
                            modifier = Modifier.weight(1.5f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = ShapeSM
                        ) {
                            Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .shadow(elevation = 1.dp, shape = ShapeMD, clip = false)
            .pressScale(onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeMD,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
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

            Column(modifier = Modifier.padding(horizontal = 10.dp).padding(top = 8.dp, bottom = 8.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = TextDark,
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

/**
 * Generates a simple PDF receipt and opens the Android Print dialog so the user
 * can save as PDF, print to a printer, or share. This is optional — tap "Receipt"
 * in the transaction-confirmed popup to trigger it.
 */
private fun generateReceiptPdf(
    context: Context,
    status: String,
    customer: String,
    items: List<com.example.viewmodel.CartItem>,
    total: Double,
    currencyFormatter: NumberFormat
) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val doc = PrintedPdfDocument(context, PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
            .setMinMargins(PrintAttributes.Margins(36, 36, 36, 36))
            .build())

        val page = doc.startPage(0)
        val canvas = page.canvas
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 12f
            color = android.graphics.Color.BLACK
        }
        val bold = android.graphics.Paint(paint).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        var y = 40f
        val lineH = 18f
        val left = 36f
        val right = page.canvas.width - 36f

        // Header
        bold.textSize = 18f
        canvas.drawText("Alyn's Poultry", left, y, bold)
        y += 24f
        bold.textSize = 12f
        canvas.drawText("SALES RECEIPT", left, y, bold)
        y += lineH + 4f

        // Divider
        canvas.drawLine(left, y, right, y, paint)
        y += 10f

        // Date & status
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy  h:mm a", java.util.Locale.getDefault())
        paint.textSize = 10f
        canvas.drawText("Date: ${sdf.format(java.util.Date())}", left, y, paint)
        y += lineH
        val statusLabel = if (status == "PAID") "PAID" else "UNPAID"
        canvas.drawText("Status: $statusLabel", left, y, paint)
        y += lineH
        if (customer.isNotBlank()) {
            canvas.drawText("Customer: $customer", left, y, paint)
            y += lineH
        }
        y += 4f
        canvas.drawLine(left, y, right, y, paint)
        y += 10f

        // Items header
        bold.textSize = 10f
        canvas.drawText("Item", left, y, bold)
        canvas.drawText("Qty", right - 80f, y, bold)
        canvas.drawText("Price", right - 40f, y, bold)
        y += lineH + 2f
        canvas.drawLine(left, y, right, y, paint)
        y += 8f

        // Items
        paint.textSize = 10f
        items.forEach { item ->
            val name = "${item.product.name} - ${item.variation.name}"
            val qty = if (item.quantity == item.quantity.toLong().toDouble())
                "${item.quantity.toLong()}" else String.format("%.1f", item.quantity)
            val price = formatPesoRaw(item.variation.price * item.quantity)

            val maxNameWidth = right - left - 120f
            val displayName = if (paint.measureText(name) > maxNameWidth) {
                var truncated = name
                while (paint.measureText("$truncated…") > maxNameWidth && truncated.length > 3) {
                    truncated = truncated.dropLast(1)
                }
                "$truncated…"
            } else name

            canvas.drawText(displayName, left, y, paint)
            canvas.drawText(qty, right - 80f, y, paint)
            canvas.drawText(price, right - 40f, y, paint)
            y += lineH
        }

        // Total
        y += 4f
        canvas.drawLine(left, y, right, y, paint)
        y += 10f
        bold.textSize = 14f
        canvas.drawText("TOTAL:", left, y, bold)
        bold.textSize = 14f
        canvas.drawText(
            formatPesoRaw(total),
            right - bold.measureText(formatPesoRaw(total)),
            y,
            bold
        )

        y += 30f
        paint.textSize = 9f
        paint.color = android.graphics.Color.GRAY
        canvas.drawText("Thank you for your purchase!", left, y, paint)

        doc.finishPage(page)

        val jobName = "Alyn's Poultry Receipt - ${sdf.format(java.util.Date())}"
        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    doc.writeTo(FileOutputStream(destination.fileDescriptor))
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: IOException) {
                    callback.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }
        }, null)

    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't generate receipt: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/** Formats a Double as peso string without the currency symbol for PDF use. */
private fun formatPesoRaw(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("en-PH"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return "\u20B1${formatter.format(amount)}"
}
