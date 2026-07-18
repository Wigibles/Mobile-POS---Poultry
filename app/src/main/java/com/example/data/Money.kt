package com.example.data

import kotlin.math.roundToLong

/**
 * Rounds a Double to exactly 2 decimal places (centavo precision)
 * to avoid floating-point accumulation errors in price calculations.
 */
fun Double.roundToCentavos(): Double = (this * 100.0).roundToLong() / 100.0

/**
 * Result of stock availability validation.
 */
data class StockValidationResult(
    val isAvailable: Boolean,
    val availableStock: Double,
    val baseUnitsNeeded: Double
)
