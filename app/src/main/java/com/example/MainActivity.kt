package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FirestorePOSRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.POSScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.CoralPrimary
import com.example.viewmodel.POSViewModel
import com.example.viewmodel.POSViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize Firestore cloud repository (real-time sync across devices)
        val repository = FirestorePOSRepository()

        // 3. Initialize view model via custom factory
        val viewModel: POSViewModel by viewModels {
            POSViewModelFactory(repository)
        }

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val cartItems by viewModel.cartItems.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("bottom_nav")
                        ) {
                            // Home Tab
                            NavigationBarItem(
                                selected = currentScreen == "HOME",
                                onClick = { viewModel.navigateTo("HOME") },
                                label = { Text("Home", fontSize = 11.sp) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CoralPrimary,
                                    selectedTextColor = CoralPrimary,
                                    indicatorColor = CoralPrimary.copy(alpha = 0.1f)
                                )
                            )

                            // POS Mode Tab
                            NavigationBarItem(
                                selected = currentScreen == "POS",
                                onClick = { viewModel.navigateTo("POS") },
                                label = { Text("POS Mode", fontSize = 11.sp) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (cartItems.isNotEmpty()) {
                                                Badge(containerColor = CoralPrimary) {
                                                    val total = cartItems.sumOf { it.quantity }
                                                    Text(if (total == total.toLong().toDouble()) "${total.toLong()}" else String.format("%.1f", total))
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = "POS Mode")
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CoralPrimary,
                                    selectedTextColor = CoralPrimary,
                                    indicatorColor = CoralPrimary.copy(alpha = 0.1f)
                                )
                            )

                            // Invoices/Transactions Tab
                            NavigationBarItem(
                                selected = currentScreen == "TRANSACTIONS",
                                onClick = { viewModel.navigateTo("TRANSACTIONS") },
                                label = { Text("Invoices", fontSize = 11.sp) },
                                icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Invoices") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CoralPrimary,
                                    selectedTextColor = CoralPrimary,
                                    indicatorColor = CoralPrimary.copy(alpha = 0.1f)
                                )
                            )

                            // Inventory Management Tab
                            NavigationBarItem(
                                selected = currentScreen == "INVENTORY",
                                onClick = { viewModel.navigateTo("INVENTORY") },
                                label = { Text("Inventory", fontSize = 11.sp) },
                                icon = { Icon(Icons.Default.Inventory, contentDescription = "Inventory") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CoralPrimary,
                                    selectedTextColor = CoralPrimary,
                                    indicatorColor = CoralPrimary.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            "HOME" -> DashboardScreen(
                                viewModel = viewModel,
                                onEnterPOS = { viewModel.navigateTo("POS") }
                            )
                            "POS" -> POSScreen(
                                viewModel = viewModel
                            )
                            "TRANSACTIONS" -> TransactionsScreen(
                                viewModel = viewModel
                            )
                            "INVENTORY" -> InventoryScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
