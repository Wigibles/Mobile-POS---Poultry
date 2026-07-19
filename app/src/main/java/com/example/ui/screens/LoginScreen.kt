package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Role
import com.example.ui.theme.*
import com.example.viewmodel.POSViewModel

@Composable
fun LoginScreen(viewModel: POSViewModel, modifier: Modifier = Modifier) {
    val authSettings by viewModel.authSettings.collectAsState()

    var selectedRole by remember { mutableStateOf(Role.CASHIER) }
    var pin by remember { mutableStateOf("") }
    var cashierName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    val nameValid = selectedRole == Role.ADMIN || cashierName.isNotBlank()
    val canSubmit = authSettings != null && pin.isNotBlank() && nameValid && !isLoggingIn

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(elevation = 6.dp, shape = ShapeXL, clip = false)
                .clip(ShapeXL)
                .background(BrandPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = "Store",
                tint = SurfaceLight,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Alyn's Poultry Supply",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextDark)
        )
        Text(
            text = "Sign in to continue",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
        )

        Spacer(Modifier.height(28.dp))

        // Role toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeLG)
                .background(SurfaceContainer)
                .padding(4.dp)
        ) {
            RoleTab(
                label = "Cashier",
                selected = selectedRole == Role.CASHIER,
                modifier = Modifier.weight(1f)
            ) {
                selectedRole = Role.CASHIER
                errorMessage = null
            }
            RoleTab(
                label = "Admin",
                selected = selectedRole == Role.ADMIN,
                modifier = Modifier.weight(1f)
            ) {
                selectedRole = Role.ADMIN
                errorMessage = null
            }
        }

        Spacer(Modifier.height(20.dp))

        if (selectedRole == Role.CASHIER) {
            OutlinedTextField(
                value = cashierName,
                onValueChange = { cashierName = it; errorMessage = null },
                label = { Text("Your Name") },
                singleLine = true,
                shape = ShapeSM,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = BorderLight,
                    focusedTextColor = TextDark, unfocusedTextColor = TextDark
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8) { pin = it; errorMessage = null } },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = ShapeSM,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandPrimary,
                unfocusedBorderColor = BorderLight,
                focusedTextColor = TextDark, unfocusedTextColor = TextDark
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMessage!!, color = ColorUnpaid, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                isLoggingIn = true
                viewModel.login(
                    role = selectedRole,
                    pin = pin,
                    cashierName = cashierName.trim().ifBlank { null }
                ) { success ->
                    isLoggingIn = false
                    if (!success) {
                        errorMessage = "Incorrect PIN"
                        pin = ""
                    }
                }
            },
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            shape = ShapeSM,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                if (authSettings == null) "Loading…" else if (isLoggingIn) "Signing in…" else "Log In",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RoleTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) SurfaceLight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) BrandPrimary else TextMuted
        )
    }
}
