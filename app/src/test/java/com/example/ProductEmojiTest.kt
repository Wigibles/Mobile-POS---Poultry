package com.example

import com.example.ui.theme.productEmoji
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductEmojiTest {

    @Test
    fun testCapsuleCategoryAndNames() {
        assertEquals("💊", productEmoji("Amptyl Capsule", "Capsule"))
        assertEquals("💊", productEmoji("Astig Capsule", "Capsule"))
        assertEquals("💊", productEmoji("B12", "Capsule"))
        assertEquals("💊", productEmoji("B50", "Capsule"))
        assertEquals("💊", productEmoji("Bee Pollen", "Capsule"))
        assertEquals("💊", productEmoji("Calvix", "Capsule"))
        assertEquals("💊", productEmoji("Doxylac", "Capsule"))
        assertEquals("💊", productEmoji("Multivitamins Tablet", "Tablets"))
        assertEquals("💊", productEmoji("Dewormer Bolus", "Medicine"))
    }

    @Test
    fun testPowderCategoryAndNames() {
        assertEquals("🧪", productEmoji("Selectogen", "Powder"))
        assertEquals("🧪", productEmoji("Dextrose Powder", "Powder"))
        assertEquals("🧪", productEmoji("Vetracin Gold", "Powder"))
        assertEquals("🧪", productEmoji("Electrolytes WSP", "Water Soluble"))
        assertEquals("🧪", productEmoji("Tylosin Soluble", "Premix"))
    }

    @Test
    fun testLiquidAndInjectables() {
        assertEquals("💧", productEmoji("Respigen Drops", "Liquid"))
        assertEquals("💧", productEmoji("Vitamin Syrup", "Liquid"))
        assertEquals("💉", productEmoji("Bexan XP Injectable", "Liquid"))
        assertEquals("💉", productEmoji("NCD Vaccine", "Vaccines"))
        assertEquals("💉", productEmoji("Antibiotic Injection", "Medicine"))
    }

    @Test
    fun testShampooAndHygiene() {
        assertEquals("🧼", productEmoji("Zero Mite Shampoo", "Accessories"))
        assertEquals("🧼", productEmoji("Wash Out Shampoo", "Hygiene"))
        assertEquals("🧼", productEmoji("Disinfectant Spray", "Supplies"))
    }

    @Test
    fun testAccessoriesAndGear() {
        assertEquals("🪢", productEmoji("Tie Cord 2m", "Accessories"))
        assertEquals("🥊", productEmoji("Sparring Gloves", "Accessories"))
        assertEquals("🔪", productEmoji("Tari Blade", "Accessories"))
        assertEquals("🏷️", productEmoji("Leg Band #5", "Accessories"))
        assertEquals("🥣", productEmoji("Plastic Feeder", "Accessories"))
        assertEquals("🪣", productEmoji("Water Gallon Drinker", "Accessories"))
        assertEquals("📦", productEmoji("Scratch Pen Coop", "Accessories"))
        assertEquals("⚖️", productEmoji("Weighing Scale", "Accessories"))
        assertEquals("🧰", productEmoji("Custom Farm Gear", "Accessories"))
    }

    @Test
    fun testFeedsAndGrains() {
        assertEquals("🐣", productEmoji("Baby Chick Booster", "3kl"))
        assertEquals("🐓", productEmoji("Broiler Grower", "Feeds"))
        assertEquals("🥚", productEmoji("Layer Mash", "Feeds"))
        assertEquals("🌽", productEmoji("Crack Corn", "3kl"))
        assertEquals("🌱", productEmoji("Sunflower Seeds", "Feeds"))
        assertEquals("⚡", productEmoji("Thunderbird Derby Conditioner", "Feeds"))
        assertEquals("🌾", productEmoji("Integral Feeds", "3kl"))
    }
}
