package com.example.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat

// ── Shared visual helpers used across Dashboard/POS/Inventory/Transactions ──
// Consolidates logic that was previously duplicated per-screen.

/** Emoji glyph for a product, guessed from its name. Used as a lightweight thumbnail
 *  everywhere a product photo would otherwise go. */
fun productEmoji(name: String): String = when {
    name.contains("Booster", ignoreCase = true) -> "🐣"
    name.contains("Grower", ignoreCase = true) -> "🐓"
    name.contains("Layer", ignoreCase = true) -> "🥚"
    name.contains("Vitamin", ignoreCase = true) -> "💊"
    name.contains("Feeder", ignoreCase = true) -> "🥣"
    name.contains("Waterer", ignoreCase = true) -> "🪣"
    name.contains("Antibiotic", ignoreCase = true) || name.contains("Medicine", ignoreCase = true) -> "💉"
    else -> "🌾"
}

/** Formats a peso amount, swapping the formatter's literal "PHP" prefix for the ₱ symbol. */
fun formatPeso(formatter: NumberFormat, amount: Double): String =
    formatter.format(amount).replace("PHP", "₱")

/** Soft pastel status badge — a tinted pill with an optional leading dot, replacing the
 *  ad-hoc Row/Box status blocks that used to be hand-built per screen. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    Row(
        modifier = modifier
            .clip(ShapeXS)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDot) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(6.dp).clip(CircleShape).background(color)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/** A subtle "press in" scale animation for tappable cards/rows — the app's main
 *  micro-interaction primitive, so it's defined once instead of hand-rolled per screen. */
fun Modifier.pressScale(
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )
}

/** Animates a numeric value counting up/down to its new target whenever it changes —
 *  used for hero stat figures on the dashboard so they feel alive rather than snapping. */
@Composable
fun AnimatedCounterText(
    value: Double,
    format: (Double) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "counter"
    )
    Text(text = format(animated.toDouble()), style = style, color = color, modifier = modifier)
}
