package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Soft Green Palette — modern, muted, structured ──
// Brand/status colors that carry white text or act as text themselves are tuned to
// pass WCAG AA (≥4.5:1); softer variants are reserved for fills, dots, and borders.
val BrandPrimary = Color(0xFF3D8159)          // Sage green, deep enough for white text (4.7:1)
val BrandSecondary = Color(0xFF7BC49A)        // Lighter mint for accents
val BrandPrimaryContainer = Color(0xFFDCF0E3) // Pale mint — chip/container fills
val ChartHighlight = Color(0xFFF2B84B)        // Warm amber — draws the eye to the best day on a chart

// Neutral colors — warm, soft grays instead of stark flat gray
val BackgroundLight = Color(0xFFF6F7F3)       // Warm off-white background
val SurfaceLight = Color(0xFFFFFFFF)          // White cards — lifted off the warm background
val SurfaceContainer = Color(0xFFF0F2EC)      // Soft tinted surface — inputs, nested containers
val SurfaceContainerHigh = Color(0xFFE7EAE2)  // One step up — pressed/hover states
val TextDark = Color(0xFF262B24)              // Soft near-black, warm undertone
val TextMuted = Color(0xFF6B7268)             // Warm gray for secondary text (4.9:1 on white)
val BorderLight = Color(0xFFE4E7DE)           // Soft border — used sparingly, shadows do most of the work
val SurfaceVariant = Color(0xFFEFF1EA)        // Slightly deeper surface for contrast

// Status colors — softened, still legible
val ColorPaid = Color(0xFF3F8F63)             // Muted green for Paid
val ColorUnpaid = Color(0xFFC1483C)           // Warm red for Unpaid — deep enough for white text
val ColorLowStock = Color(0xFFEA9F3D)         // Warm amber — fills, dots, and borders only
val ColorLowStockText = Color(0xFF9A6417)     // Darkened amber for low-stock TEXT (4.5:1+ on white)
val ColorOverdue = Color(0xFFC1483C)          // Deeper warm red for overdue

// Dark Theme Colors — soft charcoal with a green undertone, not pure black
val BrandPrimaryDark = Color(0xFF7FCB9F)      // Soft light green for contrast on dark
val BackgroundDark = Color(0xFF141811)        // Soft near-black background
val SurfaceDark = Color(0xFF1C221C)           // Dark card surface
val SurfaceContainerDark = Color(0xFF242B23)  // Nested container on dark
val TextLight = Color(0xFFE7EAE2)             // Soft warm white text
val TextMutedDark = Color(0xFF9AA398)         // Muted warm gray
val BorderDark = Color(0xFF2E352E)            // Dark border
