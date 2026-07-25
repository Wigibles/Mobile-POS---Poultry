package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FirestorePOSRepository
import com.example.data.Role
import com.example.data.SessionManager
import com.example.ui.screens.CashLogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.POSScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.BrandPrimary
import com.example.viewmodel.POSViewModel
import com.example.viewmodel.POSViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screens are designed light-only, so pin the system bars to light styling too —
        // otherwise a device in dark mode gets dark bars over light content.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
            )
        )

        // 1. Initialize Firestore cloud repository (real-time sync across devices)
        val repository = FirestorePOSRepository(applicationContext)

        // Configure Google Sheets sync — paste your deployed Google Apps Script web app URL here.
        // Leave blank to disable sheet sync. See sheets-sync.gs for deployment instructions.
        repository.sheetSyncManager.sheetWebAppUrl = "https://script.google.com/macros/s/AKfycbziH2otpVqq9HoZW3UePz9ZjiHXP-IWUW9vzpr7_SQWTgYbyi_gfYlCoFOM3eDjS_9C/exec"

        // 2. Device-local login session (not synced — each device logs in independently)
        val sessionManager = SessionManager(applicationContext)

        // 3. Initialize view model via custom factory
        val viewModel: POSViewModel by viewModels {
            POSViewModelFactory(repository, sessionManager)
        }

        setContent {
            // Force the light scheme: screen composables hardcode the light palette, so letting
            // MaterialTheme flip dark would only restyle the nav bar/dialogs and split the UI.
            MyApplicationTheme(darkTheme = false) {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val cartItems by viewModel.cartItems.collectAsState()
                val currentRole by viewModel.currentRole.collectAsState()

                // Surface repository/write errors so silent data loss becomes visible.
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    viewModel.userMessages.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                if (currentRole == null) {
                    LoginScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                } else {
                val isAdmin = currentRole == Role.ADMIN
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        val navBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("bottom_nav")
                                .shadow(elevation = 12.dp, shape = navBarShape, clip = false)
                                .clip(navBarShape)
                        ) {
                            // Home Tab
                            NavigationBarItem(
                                selected = currentScreen == "HOME",
                                onClick = { viewModel.navigateTo("HOME") },
                                label = { Text("Home", fontSize = 11.sp) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BrandPrimary,
                                    selectedTextColor = BrandPrimary,
                                    indicatorColor = BrandPrimary.copy(alpha = 0.1f)
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
                                                Badge(containerColor = BrandPrimary) {
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
                                    selectedIconColor = BrandPrimary,
                                    selectedTextColor = BrandPrimary,
                                    indicatorColor = BrandPrimary.copy(alpha = 0.1f)
                                )
                            )

                            // Invoices/Transactions Tab
                            NavigationBarItem(
                                selected = currentScreen == "TRANSACTIONS",
                                onClick = { viewModel.navigateTo("TRANSACTIONS") },
                                label = { Text("History", fontSize = 11.sp) },
                                icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "History") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BrandPrimary,
                                    selectedTextColor = BrandPrimary,
                                    indicatorColor = BrandPrimary.copy(alpha = 0.1f)
                                )
                            )

                            // Employee Expenses Tab — both roles
                            NavigationBarItem(
                                selected = currentScreen == "CASH_LOG",
                                onClick = { viewModel.navigateTo("CASH_LOG") },
                                label = { Text("Expenses", fontSize = 11.sp) },
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Employee Expenses") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BrandPrimary,
                                    selectedTextColor = BrandPrimary,
                                    indicatorColor = BrandPrimary.copy(alpha = 0.1f)
                                )
                            )

                            // Inventory Management Tab — admin only
                            if (isAdmin) {
                                NavigationBarItem(
                                    selected = currentScreen == "INVENTORY",
                                    onClick = { viewModel.navigateTo("INVENTORY") },
                                    label = { Text("Inventory", fontSize = 11.sp) },
                                    icon = { Icon(Icons.Default.Inventory, contentDescription = "Inventory") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BrandPrimary,
                                        selectedTextColor = BrandPrimary,
                                        indicatorColor = BrandPrimary.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(
                            targetState = currentScreen,
                            animationSpec = tween(220),
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
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
                                "CASH_LOG" -> CashLogScreen(
                                    viewModel = viewModel
                                )
                                "INVENTORY" -> if (isAdmin) {
                                    InventoryScreen(viewModel = viewModel)
                                } else {
                                    // Defense-in-depth: the nav tab is hidden for cashiers, but if
                                    // screen state is ever left on "INVENTORY" (e.g. a stale admin
                                    // session on a shared device), redirect instead of rendering it.
                                    LaunchedEffect(Unit) { viewModel.navigateTo("HOME") }
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
