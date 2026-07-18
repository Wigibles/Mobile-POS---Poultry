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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Category
import com.example.data.Product
import com.example.data.ProductVariation
import com.example.ui.theme.*
import com.example.viewmodel.POSViewModel
import java.text.NumberFormat
import java.util.Locale

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

    // Category filter for inventory list
    var inventoryCategoryFilter by remember { mutableStateOf<String?>(null) }

    // Form inputs state
    var editingProductId by remember { mutableStateOf(0) }
    var nameInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("") }
    var stockInput by remember { mutableStateOf("") }
    var lowStockThresholdInput by remember { mutableStateOf("5") }

    // Nested pricing variations state: list of (Name, Price, Multiplier) triples
    val variationOptions = remember { mutableStateListOf<Triple<String, String, String>>() }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))

    // Set editing fields if product is loaded from the edit flow
    fun startNewProductForm() {
        editingProductId = 0
        nameInput = ""
        categoryInput = categories.firstOrNull()?.name ?: "Feeds"
        stockInput = ""
        lowStockThresholdInput = "5"
        variationOptions.clear()
        variationOptions.add(Triple("per Kilo", "45", "1")) // default starter variation
        showForm = true
    }

    fun startEditProductForm(product: Product) {
        editingProductId = product.id
        nameInput = product.name
        categoryInput = product.category
        stockInput = if (product.stockLevel == product.stockLevel.toLong().toDouble()) product.stockLevel.toLong().toString() else product.stockLevel.toString()
        lowStockThresholdInput = if (product.lowStockThreshold == product.lowStockThreshold.toLong().toDouble()) product.lowStockThreshold.toLong().toString() else product.lowStockThreshold.toString()

        variationOptions.clear()
        val prodVars = variations.filter { it.productId == product.id }
        prodVars.forEach {
            variationOptions.add(Triple(
                it.name,
                if (it.price == it.price.toLong().toDouble()) it.price.toLong().toString() else it.price.toString(),
                if (it.multiplier == it.multiplier.toLong().toDouble()) it.multiplier.toLong().toString() else it.multiplier.toString()
            ))
        }
        if (variationOptions.isEmpty()) {
            variationOptions.add(Triple("Standard Unit", "100", "1"))
        }
        showForm = true
    }

    // Filter products by selected category
    val filteredProducts = remember(products, inventoryCategoryFilter) {
        if (inventoryCategoryFilter == null) products
        else products.filter { it.category.equals(inventoryCategoryFilter, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // Screen Top Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inventory Management",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            )

            Row {
                Button(
                    onClick = { startNewProductForm() },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("add_product_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Product", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Category Filter Chips
        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = inventoryCategoryFilter == null,
                        onClick = { inventoryCategoryFilter = null },
                        label = { Text("All", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralPrimary, selectedLabelColor = Color.White,
                            containerColor = BorderLight, labelColor = TextDark
                        ),
                        border = null,
                        modifier = Modifier.height(28.dp)
                    )
                }
                items(categories) { cat ->
                    FilterChip(
                        selected = inventoryCategoryFilter == cat.name,
                        onClick = { inventoryCategoryFilter = if (inventoryCategoryFilter == cat.name) null else cat.name },
                        label = { Text(cat.name, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralPrimary, selectedLabelColor = Color.White,
                            containerColor = BorderLight, labelColor = TextDark
                        ),
                        border = null,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Product Catalog List
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
                        imageVector = Icons.Default.Inventory,
                        contentDescription = "Empty Inventory",
                        tint = TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (products.isEmpty()) "Empty Inventory" else "No matching products",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = TextDark
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (products.isEmpty()) "Start by adding your first product to the catalog." else "Try changing your category filter.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (products.isEmpty()) {
                        Button(
                            onClick = { startNewProductForm() },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add Product", color = Color.White)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    val prodVars = variations.filter { it.productId == product.id }
                    InventoryProductRow(
                        product = product,
                        variations = prodVars,
                        currencyFormatter = currencyFormatter,
                        onEdit = { startEditProductForm(product) },
                        onDelete = { productToDelete = product }
                    )
                }
            }
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

    // PRODUCT INSERTION & EDITING FULL-SCREEN DIALOG
    if (showForm) {
        Dialog(onDismissRequest = { showForm = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (LocalConfiguration.current.screenHeightDp < 700) 0.95f else 0.88f)
                    .padding(4.dp),
                shape = RoundedCornerShape(24.dp),
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
                            text = if (editingProductId == 0) "Add New Product" else "Edit Product Details",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark)
                        )
                        IconButton(onClick = { showForm = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 12.dp))

                    // Form Body with vertical scrolling
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Product Name
                        Text("Product Name", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Enter the display name for this product", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            placeholder = { Text("e.g. Broiler Grower Feeds", color = TextMuted.copy(alpha = 0.6f)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoralPrimary,
                                unfocusedBorderColor = BorderLight,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight,
                                focusedTextColor = TextDark,
                                unfocusedTextColor = TextDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_product_name")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category Selector with inline "new category" input
                        Text("Category", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Tap a chip or type a new category name below", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(6.dp))

                        // Existing category chips
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
                                        selectedContainerColor = CoralPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = BorderLight,
                                        labelColor = TextDark
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Inline new-category text field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = if (categories.any { it.name.equals(categoryInput, ignoreCase = true) }) "" else categoryInput,
                                onValueChange = { categoryInput = it.trim() },
                                placeholder = { Text("Or type a new category…", color = TextMuted.copy(alpha = 0.6f)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CoralPrimary,
                                    unfocusedBorderColor = BorderLight,
                                    focusedContainerColor = BackgroundLight,
                                    unfocusedContainerColor = BackgroundLight,
                                    focusedTextColor = TextDark,
                                    unfocusedTextColor = TextDark
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stock levels & Low Stock threshold
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Current Stock levels
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Current Stock", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = TextDark))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("How many base units in inventory", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = stockInput,
                                    onValueChange = { stockInput = it },
                                    placeholder = { Text("e.g. 50", color = TextMuted.copy(alpha = 0.6f)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CoralPrimary,
                                        unfocusedBorderColor = BorderLight,
                                        focusedContainerColor = BackgroundLight,
                                        unfocusedContainerColor = BackgroundLight,
                                        focusedTextColor = TextDark,
                                        unfocusedTextColor = TextDark
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("form_product_stock")
                                )
                            }

                            // Low Stock Threshold
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Low Stock Alert At", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = TextDark))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Warn when stock drops below this", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = lowStockThresholdInput,
                                    onValueChange = { lowStockThresholdInput = it },
                                    placeholder = { Text("e.g. 5", color = TextMuted.copy(alpha = 0.6f)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CoralPrimary,
                                        unfocusedBorderColor = BorderLight,
                                        focusedContainerColor = BackgroundLight,
                                        unfocusedContainerColor = BackgroundLight,
                                        focusedTextColor = TextDark,
                                        unfocusedTextColor = TextDark
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Pricing & Package Options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pricing & Package Options",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = TextDark)
                                )
                                Text(
                                    text = "Define each sellable unit with its price and stock multiplier",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                                )
                            }

                            TextButton(
                                onClick = { variationOptions.add(Triple("", "", "1")) },
                                colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (variationOptions.isEmpty()) {
                            Text(
                                text = "Add at least one package option (e.g., per Kilo at ₱45, 50kg Bag at ₱2,100).",
                                style = MaterialTheme.typography.bodySmall.copy(color = ColorUnpaid),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            // Column headers for variation fields
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Package Name", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark, fontSize = 10.sp), modifier = Modifier.weight(1.8f))
                                Text("Price (₱)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark, fontSize = 10.sp), modifier = Modifier.weight(1f))
                                Text("Stock ×", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark, fontSize = 10.sp), modifier = Modifier.width(48.dp))
                                Spacer(modifier = Modifier.width(28.dp)) // for delete button spacing
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                variationOptions.forEachIndexed { index, triple ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Package option name
                                        OutlinedTextField(
                                            value = triple.first,
                                            onValueChange = { newVal ->
                                                variationOptions[index] = Triple(newVal, triple.second, triple.third)
                                            },
                                            placeholder = { Text("per Kilo", color = TextMuted.copy(alpha = 0.6f)) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = CoralPrimary,
                                                unfocusedBorderColor = BorderLight,
                                                focusedContainerColor = BackgroundLight,
                                                unfocusedContainerColor = BackgroundLight,
                                                focusedTextColor = TextDark,
                                                unfocusedTextColor = TextDark
                                            ),
                                            modifier = Modifier
                                                .weight(1.8f)
                                                .testTag("variation_name_$index")
                                        )

                                        // Package price
                                        OutlinedTextField(
                                            value = triple.second,
                                            onValueChange = { newVal ->
                                                variationOptions[index] = Triple(triple.first, newVal, triple.third)
                                            },
                                            placeholder = { Text("45", color = TextMuted.copy(alpha = 0.6f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = CoralPrimary,
                                                unfocusedBorderColor = BorderLight,
                                                focusedContainerColor = BackgroundLight,
                                                unfocusedContainerColor = BackgroundLight,
                                                focusedTextColor = TextDark,
                                                unfocusedTextColor = TextDark
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("variation_price_$index")
                                        )

                                        // Multiplier
                                        OutlinedTextField(
                                            value = triple.third,
                                            onValueChange = { newVal ->
                                                variationOptions[index] = Triple(triple.first, triple.second, newVal)
                                            },
                                            placeholder = { Text("1", color = TextMuted.copy(alpha = 0.6f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextDark),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = CoralPrimary,
                                                unfocusedBorderColor = BorderLight,
                                                focusedContainerColor = BackgroundLight,
                                                unfocusedContainerColor = BackgroundLight,
                                                focusedTextColor = TextDark,
                                                unfocusedTextColor = TextDark
                                            ),
                                            modifier = Modifier
                                                .width(48.dp)
                                                .testTag("variation_multiplier_$index")
                                        )

                                        // Delete option button
                                        IconButton(
                                            onClick = {
                                                if (variationOptions.size > 1) {
                                                    variationOptions.removeAt(index)
                                                } else {
                                                    Toast.makeText(context, "Must have at least 1 pricing option!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircle, contentDescription = "Remove Option", tint = ColorUnpaid, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // Multiplier explanation
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Stock × = how many base units one package consumes. e.g. \"50kg Bag\" = ×50, \"per Kilo\" = ×1",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            )
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextDark, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                if (isSaving) return@Button
                                val finalName = nameInput.trim()
                                val finalCategory = categoryInput.trim()
                                val finalStock = stockInput.toDoubleOrNull() ?: 0.0
                                val finalLowStock = lowStockThresholdInput.toDoubleOrNull() ?: 5.0

                                if (finalName.isBlank() || finalCategory.isBlank()) {
                                    Toast.makeText(context, "Name and Category are required!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val validVariations = variationOptions.mapNotNull {
                                    val vName = it.first.trim()
                                    val vPrice = it.second.toDoubleOrNull()
                                    val vMultiplier = it.third.toDoubleOrNull() ?: 1.0
                                    if (vName.isNotEmpty() && vPrice != null && vPrice >= 0.0) {
                                        ProductVariation(
                                            id = 0, // database auto-assigns
                                            productId = editingProductId,
                                            name = vName,
                                            price = vPrice,
                                            multiplier = vMultiplier.coerceAtLeast(0.01)
                                        )
                                    } else null
                                }

                                if (validVariations.isEmpty()) {
                                    Toast.makeText(context, "Please add at least 1 valid package option with a numeric price!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                viewModel.saveProduct(
                                    id = editingProductId,
                                    name = finalName,
                                    category = finalCategory,
                                    stockLevel = finalStock,
                                    lowStockThreshold = finalLowStock,
                                    variationsList = validVariations
                                ) {
                                    showForm = false
                                    Toast.makeText(context, "Product saved successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoralPrimary,
                                disabledContainerColor = CoralPrimary.copy(alpha = 0.5f)
                            ),
                            enabled = !isSaving,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("save_product_button")
                        ) {
                            Text("Save Product", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryProductRow(
    product: Product,
    variations: List<ProductVariation>,
    currencyFormatter: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = product.stockLevel <= product.lowStockThreshold
    val stockColor = if (isLowStock) ColorUnpaid else ColorPaid

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

    // Build compact price/variant summary string
    val priceSummary = when {
        variations.isEmpty() -> "No pricing"
        variations.size == 1 -> {
            val v = variations.first()
            "${v.name} ${currencyFormatter.format(v.price).replace("PHP", "₱")}"
        }
        else -> variations.take(2).joinToString("  ·  ") { v ->
            "${v.name} ${currencyFormatter.format(v.price).replace("PHP", "₱")}"
        } + if (variations.size > 2) " +${variations.size - 2} more" else ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isLowStock) ColorUnpaid.copy(alpha = 0.25f) else BorderLight)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji thumbnail — smaller
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(BackgroundLight),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Name + category + stock badge + price summary — all compact
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Stock badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(stockColor))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (product.stockLevel == product.stockLevel.toLong().toDouble()) "${product.stockLevel.toLong()}" else String.format("%.1f", product.stockLevel),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (isLowStock) ColorUnpaid else TextMuted
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp),
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(BorderLight).padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                    Text(
                        text = priceSummary,
                        style = MaterialTheme.typography.labelSmall.copy(color = CoralPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action buttons — more compact
            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Edit, "Edit", tint = CoralPrimary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = ColorUnpaid, modifier = Modifier.size(16.dp))
            }
        }
    }
}
