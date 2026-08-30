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
import java.util.Locale

// ── Shared visual helpers used across Dashboard/POS/Inventory/Transactions ──
// Consolidates logic that was previously duplicated per-screen.

/** Emoji glyph for a product, matching its category and name. Used as a lightweight thumbnail
 *  everywhere a product photo would otherwise go. */
fun productEmoji(name: String, category: String = ""): String {
    val lowerName = name.lowercase(Locale.ROOT)
    val lowerCat = category.lowercase(Locale.ROOT)
    val text = "$lowerName $lowerCat"

    val words = text.split(Regex("[^a-z0-9\\-]+")).filter { it.isNotEmpty() }.toSet()

    fun hasWord(word: String): Boolean = words.contains(word)

    fun hasAny(vararg keywords: String): Boolean = keywords.any { kw -> text.contains(kw) }

    return when {
        // ── 1. Injectables & Vaccines (💉) ──
        lowerCat in listOf("injectable", "injectables", "vaccine", "vaccines", "biologics") ||
        hasAny("injectable", "injection", "vaccine", "vial", "ampoule", "biologic", "bexan xp", "ncd", "gumboro", "coryza", "fowl pox") ||
        hasWord("inject") -> "💉"

        // ── 2. Shampoo, Soap, Hygiene & Parasiticides (🧼) ──
        lowerCat in listOf("shampoo", "soap", "hygiene", "grooming", "disinfectant", "disinfectants") ||
        hasAny("shampoo", "soap", "wash out", "zero mite", "disinfect", "hygiene", "cleaner") ||
        hasWord("wash") || hasWord("bath") || hasWord("mite") || hasWord("lice") -> "🧼"

        // ── 3. Powders & Water Solubles (🧪) ──
        lowerCat in listOf("powder", "powders", "premix", "water soluble", "soluble") ||
        hasAny("powder", "pwd", "soluble", "wsp", "dextrose", "electrolyte", "selectogen", "vetracin", "tylosin", "probiotic", "premix", "trisul", "amprol") ||
        hasWord("ws") || hasWord("mix") -> "🧪"

        // ── 4. Liquids, Syrups & Drops (💧) ──
        lowerCat in listOf("liquid", "liquids", "syrup", "drops", "drop", "oral solution") ||
        hasAny("liquid", "syrup", "drop", "dropper", "suspension", "solution", "tonic", "respigen", "cod liver oil") ||
        hasWord("oil") -> "💧"

        // ── 5. Specific Accessories Overrides (🪢, 🥊, 🔪, 🏷️, 🥣, 🪣, 📦, ⚖️) ──
        hasAny("cord", "tether", "leash", "tali", "tie cord") || hasWord("tie") || hasWord("rope") -> "🪢"
        hasAny("glove", "boxing", "sparring", "muzzle", "boots", "tari cover") -> "🥊"
        hasAny("tari", "gaff", "blade", "sheath", "slasher") || hasWord("knife") -> "🔪"
        hasAny("wing band", "leg band", "tag", "tags") || hasWord("band") || hasWord("ring") -> "🏷️"
        hasAny("feeder", "feeding tray") || hasWord("tray") || hasWord("plate") -> "🥣"
        hasAny("waterer", "drinker", "gallon", "nipple") || hasWord("cup") -> "🪣"
        hasAny("cage", "coop", "teepee", "crate", "scratch pen", "carrying box") || hasWord("net") || hasWord("trap") -> "📦"
        hasAny("scale", "timbangan") || hasWord("weigh") -> "⚖️"

        // ── 6. General Accessories Category (🧰) ──
        lowerCat.contains("access") || lowerCat.contains("equip") || lowerCat.contains("suppl") ||
        lowerCat.contains("tool") || lowerCat.contains("gear") || lowerCat.contains("hardware") -> "🧰"

        // ── 7. Capsules, Tablets & Pills (💊) ──
        lowerCat in listOf("capsule", "capsules", "tablet", "tablets", "pill", "pills", "medicine", "medicines", "supplements", "vitamins") ||
        hasAny("capsule", "tablet", "pill", "bolus", "b12", "b-12", "b50", "b-50", "calvix", "pollen", "doxylac", "amptyl", "astig", "voltar", "viminolak", "red gel", "promotor", "reload plus", "tricon", "multivitamin", "vitamin", "calcium", "dewormer", "vermex", "anthelmintic") ||
        hasWord("cap") || hasWord("caps") || hasWord("tab") || hasWord("tabs") -> "💊"

        // ── 8. Feeds, Grains & Seeds by Life Stage / Type ──
        hasAny("booster", "starter", "pre-starter", "baby chick", "sisiw") || hasWord("chick") -> "🐣"
        hasAny("grower", "broiler", "cockerel", "stag", "bullstag", "rooster", "manok") -> "🐓"
        hasAny("layer", "quail", "pugo") || hasWord("egg") || hasWord("eggs") -> "🥚"
        hasAny("conditioner", "conditioning", "derby", "champion", "energy", "maintenance", "ready to fight", "bullet") || hasWord("power") -> "⚡"
        hasAny("corn", "mais", "grits") -> "🌽"
        hasAny("sunflower", "seed", "seeds", "munggo") || hasWord("peas") || hasWord("bean") || hasWord("beans") -> "🌱"

        // ── 9. Default Feeds / Grains / General Fallback ──
        else -> "🌾"
    }
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
