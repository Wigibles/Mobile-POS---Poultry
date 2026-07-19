package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Shared corner-radius scale so every card/button/chip/sheet pulls from one set of
// values instead of one-off RoundedCornerShape(n.dp) calls scattered per screen.
val ShapeXS = RoundedCornerShape(8.dp)   // small chips, inline badges
val ShapeSM = RoundedCornerShape(12.dp)  // list rows, inputs
val ShapeMD = RoundedCornerShape(16.dp)  // standard cards
val ShapeLG = RoundedCornerShape(20.dp)  // prominent cards, dialogs
val ShapeXL = RoundedCornerShape(28.dp)  // hero cards, bottom sheets
