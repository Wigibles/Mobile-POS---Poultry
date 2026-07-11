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

    var showForm by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    // Form inputs state
    var editingProductId by remember { mutableStateOf(0) }
    var nameInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("") }
    var stockInput by remember { mutableStateOf("") }
    var lowStockThresholdInput by remember { mutableStateOf("5") }

    // Nested pricing variations state: list of (Name, Price) pairs
    val variationOptions = remember { mutableStateListOf<Pair<String, String>>() }

    // Quick add custom category input
    var newCategoryName by remember { mutableStateOf("") }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    // Set editing fields if product is loaded from the edit flow
    fun startNewProductForm() {
        editingProductId = 0
        nameInput = ""
        categoryInput = categories.firstOrNull()?.name ?: "Feeds"
        stockInput = ""
        lowStockThresholdInput = "5"
        variationOptions.clear()
        variationOptions.add(Pair("per Kilo", "45")) // default starter variations for easy testing
        showForm = true
    }

    fun startEditProductForm(product: Product) {
        editingProductId = product.id
        nameInput = product.name
        categoryInput = product.category
        stockInput = product.stockLevel.toInt().toString()
        lowStockThresholdInput = product.lowStockThreshold.toInt().toString()

        variationOptions.clear()
        val prodVars = variations.filter { it.productId == product.id }
        prodVars.forEach {
            variationOptions.add(Pair(it.name, it.price.toInt().toString()))
        }
        if (variationOptions.isEmpty()) {
            variationOptions.add(Pair("Standard Unit", "100"))
        }
        showForm = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Screen Top Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Category options editor
                IconButton(
                    onClick = { showCategoryDialog = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceLight)
                ) {
                    Icon(Icons.Default.Category, contentDescription = "Manage Categories", tint = CoralPrimary)
                }

                Button(
                    onClick = { startNewProductForm() },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("add_product_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Product", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Product Catalog List
        if (products.isEmpty()) {
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
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your inventory is empty",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { startNewProductForm() },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                    ) {
                        Text("Add First Product", color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                items(products) { product ->
                    val prodVars = variations.filter { it.productId == product.id }
                    InventoryProductRow(
                        product = product,
                        variations = prodVars,
                        currencyFormatter = currencyFormatter,
                        onEdit = { startEditProductForm(product) },
                        onDelete = { viewModel.deleteProduct(product) }
                    )
                }
            }
        }
    }

    // PRODUCT INSERTION & EDITING FULL-SCREEN DIALOG
    if (showForm) {
        Dialog(onDismissRequest = { showForm = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
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

                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 10.dp))

                    // Form Body with vertical scrolling
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Product Name
                        Text("Product Name:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            placeholder = { Text("e.g. Broiler Grower Feeds", color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CoralPrimary,
                                unfocusedBorderColor = BorderLight,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_product_name")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Category Dropdown/Selector
                        Text("Category (User custom option):", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                        Spacer(modifier = Modifier.height(4.dp))

                        // Custom scrollable category row for easy rapid selection
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                val isSelected = categoryInput == cat.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { categoryInput = cat.name },
                                    label = { Text(cat.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CoralPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = BorderLight,
                                        labelColor = TextDark
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stock levels & Low Stock threshold
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Current Stock levels
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Stock Level:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = stockInput,
                                    onValueChange = { stockInput = it },
                                    placeholder = { Text("e.g. 50", color = TextMuted) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CoralPrimary,
                                        unfocusedBorderColor = BorderLight,
                                        focusedContainerColor = BackgroundLight,
                                        unfocusedContainerColor = BackgroundLight
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("form_product_stock")
                                )
                            }

                            // Low Stock Threshold
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Low Stock Alert:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextDark))
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = lowStockThresholdInput,
                                    onValueChange = { lowStockThresholdInput = it },
                                    placeholder = { Text("e.g. 5", color = TextMuted) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CoralPrimary,
                                        unfocusedBorderColor = BorderLight,
                                        focusedContainerColor = BackgroundLight,
                                        unfocusedContainerColor = BackgroundLight
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SPECIFIC PRICING FOR EACH ITEM (PACKAGE OPTIONS - AS REQUESTED!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pricing Variation Options:",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = TextDark)
                            )

                            // Add Option Button (User can add price options here!)
                            TextButton(
                                onClick = {
                                    variationOptions.add(Pair("", ""))
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add Option")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Option", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (variationOptions.isEmpty()) {
                            Text(
                                text = "Please add at least one price option (e.g., per Kilo, 50kg Bag).",
                                style = MaterialTheme.typography.bodySmall.copy(color = ColorUnpaid),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                variationOptions.forEachIndexed { index, pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Package option name (e.g. per kilo, 3 kilograms)
                                        OutlinedTextField(
                                            value = pair.first,
                                            onValueChange = { newVal ->
                                                variationOptions[index] = Pair(newVal, pair.second)
                                            },
                                            placeholder = { Text("e.g. per Kilo", color = TextMuted) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = CoralPrimary,
                                                unfocusedBorderColor = BorderLight,
                                                focusedContainerColor = BackgroundLight,
                                                unfocusedContainerColor = BackgroundLight
                                            ),
                                            modifier = Modifier
                                                .weight(1.5f)
                                                .testTag("variation_name_$index")
                                        )

                                        // Package price
                                        OutlinedTextField(
                                            value = pair.second,
                                            onValueChange = { newVal ->
                                                variationOptions[index] = Pair(pair.first, newVal)
                                            },
                                            placeholder = { Text("₱ Price", color = TextMuted) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = CoralPrimary,
                                                unfocusedBorderColor = BorderLight,
                                                focusedContainerColor = BackgroundLight,
                                                unfocusedContainerColor = BackgroundLight
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("variation_price_$index")
                                        )

                                        // Delete option button
                                        IconButton(
                                            onClick = {
                                                if (variationOptions.size > 1) {
                                                    variationOptions.removeAt(index)
                                                } else {
                                                    Toast.makeText(context, "Must have at least 1 pricing option!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.RemoveCircle, contentDescription = "Remove Option", tint = ColorUnpaid)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Form Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { showForm = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextMuted)
                        }

                        Button(
                            onClick = {
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
                                    if (vName.isNotEmpty() && vPrice != null && vPrice >= 0.0) {
                                        ProductVariation(
                                            id = 0, // database auto-assigns
                                            productId = editingProductId,
                                            name = vName,
                                            price = vPrice
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
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
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

    // DYNAMIC CATEGORY MANAGER DIALOG (As requested!)
    if (showCategoryDialog) {
        Dialog(onDismissRequest = { showCategoryDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manage Product Categories",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextDark)
                        )
                        IconButton(onClick = { showCategoryDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text Field to add dynamic Category
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                                                            value = newCategoryName,
                                                            onValueChange = { newCategoryName = it },
                                                            placeholder = { Text("e.g. Feed Additives", color = TextMuted) },
                                                            singleLine = true,
                                                            shape = RoundedCornerShape(8.dp),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = CoralPrimary,
                                                                unfocusedBorderColor = BorderLight,
                                                                focusedContainerColor = BackgroundLight,
                                                                unfocusedContainerColor = BackgroundLight
                                                            ),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .testTag("new_category_input")
                                                        )

                        Button(
                            onClick = {
                                val trimmed = newCategoryName.trim()
                                if (trimmed.isNotEmpty()) {
                                    viewModel.addCategory(trimmed)
                                    newCategoryName = ""
                                    Toast.makeText(context, "Category added!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text("Add", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Current Categories:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextDark)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable list of current categories with deletion capabilities
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(categories) { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BackgroundLight)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = cat.name, style = MaterialTheme.typography.bodyMedium.copy(color = TextDark))
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteCategory(cat)
                                            Toast.makeText(context, "Category deleted!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ColorUnpaid, modifier = Modifier.size(16.dp))
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

@Composable
fun InventoryProductRow(
    product: Product,
    variations: List<ProductVariation>,
    currencyFormatter: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini emoji thumbnail
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextDark),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Category: ${product.category}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }

                // Edit and Delete Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Product", tint = CoralPrimary, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Product", tint = ColorUnpaid, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(color = BorderLight, thickness = 1.dp)

            Spacer(modifier = Modifier.height(10.dp))

            // Sub details (Stock and Pricing list)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Stock section
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stock Levels", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (product.stockLevel <= product.lowStockThreshold) ColorUnpaid else ColorPaid
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${product.stockLevel.toInt()} units",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (product.stockLevel <= product.lowStockThreshold) ColorUnpaid else TextDark
                            )
                        )
                    }
                    if (product.stockLevel <= product.lowStockThreshold) {
                        Text(
                            text = "Low Stock Alert!",
                            color = ColorUnpaid,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Variation package options price list
                Column(
                    modifier = Modifier.weight(1.5f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("Price Options", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                    Spacer(modifier = Modifier.height(4.dp))
                    variations.forEach { variation ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = variation.name,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                            )
                            Text(
                                text = currencyFormatter.format(variation.price).replace("PHP", "₱"),
                                style = MaterialTheme.typography.bodySmall.copy(color = TextDark, fontWeight = FontWeight.Black)
                            )
                        }
                    }
                }
            }
        }
    }
}
