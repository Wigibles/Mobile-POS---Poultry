package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: POSViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val variations by viewModel.variations.collectAsState()
    val isSaving by viewModel.isSavingProduct.collectAsState()

    var showForm by remember { mutableStateOf(false) }

    // Delete confirmation
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    // Local list filters — search + category
    var inventoryCategoryFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Form inputs state
    var editingProductId by remember { mutableStateOf(0) }
    var nameInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("") }
    var supplyCountInput by remember { mutableStateOf("") }

    // Nested pricing variations state. The first row is always the "base" package that
    // anchors price suggestions for every other row — see PackageDraft/pricing helpers below.
    val variationOptions = remember { mutableStateListOf<PackageDraft>() }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))

    fun startNewProductForm() {
        editingProductId = 0
        nameInput = ""
        categoryInput = categories.firstOrNull()?.name ?: "Feeds"
        supplyCountInput = ""
        variationOptions.clear()
        variationOptions.add(PackageDraft(name = "per Kilo", price = "45", size = "1")) // base package
        showForm = true
    }

    fun startEditProductForm(product: Product) {
        editingProductId = product.id
        nameInput = product.name
        categoryInput = product.category
        supplyCountInput = product.supplyCount?.let { formatDraftNumber(it) } ?: ""

        variationOptions.clear()
        val prodVars = variations.filter { it.productId == product.id }
        prodVars.forEach {
            // Existing, already-saved prices are deliberate business decisions — mark them
            // manual so editing the base price never silently rewrites historical pricing.
            variationOptions.add(PackageDraft(
                name = it.name,
                price = formatDraftNumber(it.price),
                size = formatDraftNumber(it.multiplier),
                priceManual = true
            ))
        }
        if (variationOptions.isEmpty()) {
            variationOptions.add(PackageDraft("Standard Unit", "100", "1", priceManual = true))
        }
        showForm = true
    }

    val filteredProducts = remember(products, inventoryCategoryFilter, searchQuery) {
        products.filter { p ->
            val matchesCategory = inventoryCategoryFilter == null || p.category.equals(inventoryCategoryFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || p.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BackgroundLight)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── HEADER — title + a live stock-health readout ──
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp)) {
                Text(
                    text = "Inventory",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextDark)
                )
                Text(
                    text = if (products.isEmpty()) "No products yet" else "${products.size} products",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }

            // ── SEARCH ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search products…", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear search", tint = TextMuted, modifier = Modifier.size(18.dp)) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceLight,
                    unfocusedContainerColor = SurfaceContainer,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )

            // ── FILTER CHIPS — categories ──
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = inventoryCategoryFilter == null,
                        onClick = { inventoryCategoryFilter = null },
                        label = { Text("All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White,
                            containerColor = SurfaceContainer, labelColor = TextDark
                        ),
                        border = null
                    )
                }
                items(categories) { cat ->
                    FilterChip(
                        selected = inventoryCategoryFilter == cat.name,
                        onClick = { inventoryCategoryFilter = if (inventoryCategoryFilter == cat.name) null else cat.name },
                        label = { Text(cat.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPrimary, selectedLabelColor = Color.White,
                            containerColor = SurfaceContainer, labelColor = TextDark
                        ),
                        border = null
                    )
                }
            }

            // ── PRODUCT LIST ──
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = "Empty Inventory",
                            tint = TextMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                products.isEmpty() -> "Empty Inventory"
                                searchQuery.isNotBlank() -> "No matches for \"$searchQuery\""
                                else -> "No matching products"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (products.isEmpty()) "Start by adding your first product to the catalog." else "Try a different search or filter.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        if (products.isEmpty()) {
                            Button(
                                onClick = { startNewProductForm() },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                shape = ShapeSM
                            ) {
                                Text("Add Product", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val prodVars = variations.filter { it.productId == product.id }
                        InventoryProductRow(
                            product = product,
                            variations = prodVars,
                            currencyFormatter = currencyFormatter,
                            onClick = { startEditProductForm(product) }
                        )
                    }
                }
            }
        }

        // ── NEW PRODUCT FAB — thumb zone, always reachable ──
        ExtendedFloatingActionButton(
            onClick = { startNewProductForm() },
            containerColor = BrandPrimary,
            contentColor = Color.White,
            shape = ShapeMD,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 20.dp)
                .testTag("add_product_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Product", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    // ── Delete Product Confirmation ──
    if (productToDelete != null) {
        val product = productToDelete!!
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorUnpaid, modifier = Modifier.size(32.dp)) },
            title = { Text("Delete Product?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Permanently delete \"${product.name}\"? All pricing options for this product will also be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(product)
                        productToDelete = null
                        showForm = false
                        Toast.makeText(context, "\"${product.name}\" deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorUnpaid)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── PRODUCT ADD/EDIT FORM — sectioned, with per-package cards ──
    if (showForm) {
        Dialog(onDismissRequest = { showForm = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (LocalConfiguration.current.screenHeightDp < 700) 0.95f else 0.88f)
                    .padding(4.dp),
                shape = ShapeLG,
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingProductId == 0) "New Product" else "Edit Product",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark)
                        )
                        IconButton(onClick = { showForm = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 10.dp))

                    // Form Body with vertical scrolling
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FormSectionLabel("BASICS")

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Product name") },
                            placeholder = { Text("e.g. Broiler Grower Feeds", color = TextMuted.copy(alpha = 0.6f)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark),
                            shape = ShapeSM,
                            colors = formFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_product_name")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category — tap an existing chip or type a new one
                        Text("Category", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                val isSelected = categoryInput.equals(cat.name, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { categoryInput = cat.name },
                                    label = { Text(cat.name, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = SurfaceContainer,
                                        labelColor = TextDark
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = if (categories.any { it.name.equals(categoryInput, ignoreCase = true) }) "" else categoryInput,
                            onValueChange = { categoryInput = it.trim() },
                            placeholder = { Text("Or type a new category…", color = TextMuted.copy(alpha = 0.6f)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                            shape = ShapeXS,
                            colors = formFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = supplyCountInput,
                            onValueChange = { supplyCountInput = it },
                            label = { Text("Supply count (Optional)") },
                            placeholder = { Text("e.g. 100", color = TextMuted.copy(alpha = 0.6f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark),
                            shape = ShapeSM,
                            colors = formFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_product_supply_count")
                        )

                        // Pricing & Package Options — size-first, price-suggested. The first
                        // card is the base package; every other package's price auto-fills
                        // from its size × base price, and stays editable at any time.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                FormSectionLabel("PRICING & PACKAGES")
                            }
                            TextButton(
                                onClick = { variationOptions.add(PackageDraft(priceManual = false)) },
                                colors = ButtonDefaults.textButtonColors(contentColor = BrandPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                        Text(
                            text = "The first package sets your base price. Every other package suggests its own price automatically — override anytime.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (variationOptions.isEmpty()) {
                            Text(
                                text = "Add at least one package option (e.g., per Kilo at ₱45, 50kg Bag at ₱2,100).",
                                style = MaterialTheme.typography.bodySmall.copy(color = ColorUnpaid),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            val basePrice = variationOptions.getOrNull(0)?.price?.toDoubleOrNull()

                            fun recomputeDependents(newBasePrice: Double?) {
                                if (newBasePrice == null) return
                                for (i in 1 until variationOptions.size) {
                                    val row = variationOptions[i]
                                    if (row.priceManual) continue
                                    val size = row.size.toDoubleOrNull() ?: continue
                                    variationOptions[i] = row.copy(price = formatDraftNumber(size * newBasePrice))
                                }
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                variationOptions.forEachIndexed { index, draft ->
                                    val isBase = index == 0
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer.copy(alpha = 0.6f)),
                                        shape = ShapeSM,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            if (isBase) {
                                                StatusPill(text = "BASE PACKAGE", color = BrandPrimary, showDot = false, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = draft.name,
                                                    onValueChange = { newVal -> variationOptions[index] = draft.copy(name = newVal) },
                                                    label = { Text("Package name") },
                                                    placeholder = { Text("per Kilo", color = TextMuted.copy(alpha = 0.6f)) },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                                    shape = ShapeXS,
                                                    colors = formFieldColors(),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("variation_name_$index")
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = {
                                                        if (variationOptions.size > 1) {
                                                            variationOptions.removeAt(index)
                                                        } else {
                                                            Toast.makeText(context, "Must have at least 1 pricing option!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(Icons.Default.RemoveCircle, contentDescription = "Remove Option", tint = ColorUnpaid, modifier = Modifier.size(20.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            if (isBase) {
                                                // The base package has no size of its own (it IS the
                                                // unit everything else is measured against).
                                                OutlinedTextField(
                                                    value = draft.price,
                                                    onValueChange = { newVal ->
                                                        variationOptions[index] = draft.copy(price = newVal)
                                                        recomputeDependents(newVal.toDoubleOrNull())
                                                    },
                                                    label = { Text("Price ₱") },
                                                    placeholder = { Text("45", color = TextMuted.copy(alpha = 0.6f)) },
                                                    supportingText = if (variationOptions.size > 1) {
                                                        { Text("Other packages price themselves from this", fontSize = 10.sp, color = TextMuted) }
                                                    } else null,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                                    shape = ShapeXS,
                                                    colors = formFieldColors(),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("variation_price_$index")
                                                )
                                            } else {
                                                val size = draft.size.toDoubleOrNull()
                                                val actualPrice = draft.price.toDoubleOrNull()
                                                val linearPrice = if (basePrice != null && size != null) size * basePrice else null
                                                val diffPct = if (linearPrice != null && linearPrice > 0 && actualPrice != null)
                                                    (actualPrice - linearPrice) / linearPrice * 100 else null
                                                val canReset = draft.priceManual && basePrice != null && size != null

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = draft.size,
                                                        onValueChange = { newVal ->
                                                            val updated = draft.copy(size = newVal)
                                                            variationOptions[index] = updated
                                                            if (!draft.priceManual) {
                                                                val newSize = newVal.toDoubleOrNull()
                                                                if (basePrice != null && newSize != null) {
                                                                    variationOptions[index] = updated.copy(price = formatDraftNumber(newSize * basePrice))
                                                                }
                                                            }
                                                        },
                                                        label = { Text("Package size") },
                                                        placeholder = { Text("e.g. 50", color = TextMuted.copy(alpha = 0.6f)) },
                                                        supportingText = { Text("= ${draft.size.ifBlank { "?" }} base units", fontSize = 10.sp, color = TextMuted) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                                        shape = ShapeXS,
                                                        colors = formFieldColors(),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .testTag("variation_multiplier_$index")
                                                    )
                                                    OutlinedTextField(
                                                        value = draft.price,
                                                        onValueChange = { newVal ->
                                                            variationOptions[index] = draft.copy(price = newVal, priceManual = true)
                                                        },
                                                        label = { Text("Price ₱") },
                                                        placeholder = { Text("auto", color = TextMuted.copy(alpha = 0.6f)) },
                                                        trailingIcon = if (canReset) {
                                                            {
                                                                IconButton(
                                                                    onClick = {
                                                                        variationOptions[index] = draft.copy(
                                                                            price = formatDraftNumber(size!! * basePrice!!),
                                                                            priceManual = false
                                                                        )
                                                                    },
                                                                    modifier = Modifier.size(32.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Restore, contentDescription = "Reset to suggested price", tint = TextMuted, modifier = Modifier.size(16.dp))
                                                                }
                                                            }
                                                        } else null,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                                        shape = ShapeXS,
                                                        colors = formFieldColors(),
                                                        modifier = Modifier
                                                            .weight(1.4f)
                                                            .testTag("variation_price_$index")
                                                    )
                                                }

                                                // Live sanity-check badge — only appears when the
                                                // price genuinely deviates from straight-line pricing,
                                                // so a typo like ₱1750→₱175 is visible immediately.
                                                if (diffPct != null && abs(diffPct) >= 1.0) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    val isDiscount = diffPct < 0
                                                    val badgeColor = if (isDiscount) ColorPaid else ColorLowStockText
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            if (isDiscount) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                                                            contentDescription = null,
                                                            tint = badgeColor,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "${abs(diffPct).roundToInt()}% ${if (isDiscount) "bulk discount" else "markup"} vs base — ${formatPeso(currencyFormatter, linearPrice!!)} would be linear",
                                                            style = MaterialTheme.typography.labelSmall.copy(color = badgeColor, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Delete lives inside the editor, away from the everyday list.
                        if (editingProductId != 0) {
                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider(color = BorderLight)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { productToDelete = products.find { it.id == editingProductId } },
                                colors = ButtonDefaults.textButtonColors(contentColor = ColorUnpaid),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete this product", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Form Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { showForm = false },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Cancel", color = TextDark, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                if (isSaving) return@Button
                                val finalName = nameInput.trim()
                                val finalCategory = categoryInput.trim()

                                if (finalName.isBlank() || finalCategory.isBlank()) {
                                    Toast.makeText(context, "Name and Category are required!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val validVariations = variationOptions.mapIndexedNotNull { idx, draft ->
                                    val vName = draft.name.trim()
                                    val vPrice = draft.price.toDoubleOrNull()
                                    // The base package (index 0) is always ×1 by definition —
                                    // its size field isn't shown, so never trust stray data in it.
                                    val vMultiplier = if (idx == 0) 1.0 else (draft.size.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.01)
                                    if (vName.isNotEmpty() && vPrice != null && vPrice >= 0.0) {
                                        ProductVariation(
                                            id = 0, // database auto-assigns
                                            productId = editingProductId,
                                            name = vName,
                                            price = vPrice,
                                            multiplier = vMultiplier
                                        )
                                    } else null
                                }

                                if (validVariations.isEmpty()) {
                                    Toast.makeText(context, "Please add at least 1 valid package option with a numeric price!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                val finalSupplyCount = supplyCountInput.trim().toDoubleOrNull()

                                viewModel.saveProduct(
                                    id = editingProductId,
                                    name = finalName,
                                    category = finalCategory,
                                    supplyCount = finalSupplyCount,
                                    variationsList = validVariations
                                ) {
                                    showForm = false
                                    Toast.makeText(context, "Product saved successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPrimary,
                                disabledContainerColor = BrandPrimary.copy(alpha = 0.5f)
                            ),
                            enabled = !isSaving,
                            shape = ShapeSM,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .testTag("save_product_button")
                        ) {
                            Text(if (isSaving) "Saving…" else "Save Product", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** One row in the pricing/packages editor. `priceManual` tracks whether the shopkeeper
 *  typed this price themselves — once true, auto-suggestion stops touching it. */
private data class PackageDraft(
    val name: String = "",
    val price: String = "",
    val size: String = "",
    val priceManual: Boolean = false
)

/** Formats a numeric value the way the form expects: whole numbers with no decimal point. */
private fun formatDraftNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.2f", value)

/** Small overline label that visually groups form fields into sections. */
@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Black,
            color = TextMuted,
            letterSpacing = 1.2.sp,
            fontSize = 11.sp
        ),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** Shared field styling for the product form. */
@Composable
private fun formFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandPrimary,
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = SurfaceContainer,
    unfocusedContainerColor = SurfaceContainer,
    focusedTextColor = TextDark,
    unfocusedTextColor = TextDark,
    focusedLabelColor = BrandPrimary,
    unfocusedLabelColor = TextMuted
)

@Composable
fun InventoryProductRow(
    product: Product,
    variations: List<ProductVariation>,
    currencyFormatter: NumberFormat,
    onClick: () -> Unit
) {
    // Build compact price/variant summary string. One full price + a count — never two
    // prices squeezed into one line, which ellipsized mid-number ("per Sack ₱1,…").
    val priceSummary = when {
        variations.isEmpty() -> "No pricing"
        variations.size == 1 -> {
            val v = variations.first()
            "${v.name} ${formatPeso(currencyFormatter, v.price)}"
        }
        else -> {
            val v = variations.first()
            "${v.name} ${formatPeso(currencyFormatter, v.price)} · +${variations.size - 1} more"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = ShapeMD, clip = false)
            .pressScale(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = ShapeMD,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(ShapeSM).background(BrandPrimaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = productEmoji(product.name, product.category), fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = priceSummary,
                    style = MaterialTheme.typography.labelSmall.copy(color = BrandPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp),
                        modifier = Modifier.clip(ShapeXS).background(SurfaceContainer).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    if (product.supplyCount != null) {
                        Text(
                            text = "Supply: ${formatDraftNumber(product.supplyCount)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp),
                            modifier = Modifier.clip(ShapeXS).background(SurfaceContainer).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = "Edit", tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}
