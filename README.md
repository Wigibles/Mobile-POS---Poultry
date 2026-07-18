# 🐓 Alyn's Poultry Supply — Mobile POS

A cloud-synced point-of-sale application built for poultry supply businesses in the Philippines. Manage inventory, process sales, track customer debts, and view real-time analytics — all from your Android phone.

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/Firebase-Firestore-FFCA28?logo=firebase" />
</p>

---

## ✨ Features

### 🛒 Point of Sale
- **Tap-to-add** product grid — single tap for instant cart addition
- **Multi-variant support** — per Kilo, 50kg Bag, half Kilo with automatic stock multiplier deduction
- **Swipe-up cart** — bottom sheet shows running order with quantity controls
- **Paid / Unpaid** — mark transactions as cash or customer debt tab
- **Customer autocomplete** — suggests previous customer names for unpaid orders

### 📦 Inventory Management
- **CRUD products** with multiple pricing variants (name, price, stock multiplier)
- **Category system** — filter products, create categories inline while adding products
- **Low stock alerts** — dashboard highlights products below threshold
- **Delete confirmation** — prevents accidental product removal

### 💰 Debt Tracking
- **Unpaid totals** — total receivables, unpaid order count, oldest debt age at a glance
- **Aging display** — shows days since order; overdue (>30 days) flagged in red
- **Customer profiles** — searchable customer list with full debt history per customer
- **One-tap settlement** — mark individual orders as paid from any view

### 📊 Analytics Dashboard
- **Today's Sales** — total, paid, unpaid, transaction count, average ticket size
- **Sales chart** — toggle between 7-day, monthly, and yearly views
- **Low stock products** — quick glance at items needing restock

### 🔄 Cloud Sync (Firestore)
- **Real-time multi-device** — all workers share the same database instantly
- **Offline support** — Firestore persistence queues writes when offline
- **Duplicate prevention** — guards against double-tapping save/checkout

### ⚙️ Settings
- **Sync data** — health check to verify cloud connectivity
- **Delete all data** — clear Firestore with confirmation dialog

---

## 🏗 Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + Repository pattern |
| Cloud Database | Firebase Firestore (real-time sync) |
| State Management | Kotlin StateFlow + Compose collectAsState |
| Build System | Gradle KTS + Version Catalog |
| Min SDK | Android 7.0 (API 24) |
| Target SDK | Android 15 (API 36) |

---

## 🚀 Setup

**Prerequisites:** Android Studio Hedgehog or later

1. Clone and open in Android Studio
2. Sync Gradle
3. Set up Firebase (see below)
4. Remove `signingConfig = signingConfigs.getByName("debugConfig")` from `app/build.gradle.kts` if not using a debug keystore
5. Run on device or emulator

### Firebase Setup

1. Create a project at [Firebase Console](https://console.firebase.google.com)
2. Add an Android app with package `com.aistudio.alynspoultrypos.pnzrqs`
3. Download `google-services.json` → place in `app/` folder
4. Enable **Cloud Firestore** (start in test mode)
5. Build & run — data syncs automatically across devices

---

## 📱 Screens

| Screen | Purpose |
|---|---|
| **POS Mode** | Product grid with tap-to-add, cart bottom sheet, checkout |
| **Inventory** | Manage products, categories, pricing variants |
| **Transaction History** | All/unpaid transactions with filters, date picker, customer profiles |
| **Dashboard** | Sales stats, charts, low stock alerts, settings |

---

## 📂 Project Structure

```
app/src/main/java/com/example/
├── data/
│   ├── Entities.kt          # Data models (Product, Transaction, etc.)
│   ├── FirestoreRepository.kt  # Cloud database operations
│   └── Money.kt             # Utility functions (roundToCentavos, StockValidationResult)
├── viewmodel/
│   └── POSViewModel.kt      # State management, business logic
├── ui/
│   ├── screens/
│   │   ├── DashboardScreen.kt
│   │   ├── POSScreen.kt
│   │   ├── InventoryScreen.kt
│   │   └── TransactionsScreen.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── MainActivity.kt          # Entry point, navigation
```

---

## 🤝 Sharing with Workers

1. Build the APK: `./gradlew assembleDebug`
2. Distribute via **Firebase App Distribution** (free) or direct APK install
3. All devices connect to the same Firestore database — data syncs in real-time
4. No separate server or backend needed

---

Built for **Alyn's Poultry Supply** 🇵🇭
