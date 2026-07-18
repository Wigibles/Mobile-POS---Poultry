package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Fixed timezone for deciding which calendar day a sale belongs to. Pinned rather than using
 * the device's own timezone so "today" (and chart/history day-bucketing) means the same thing
 * on every device, even if one device's clock or timezone setting has drifted.
 */
val SHOP_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Manila")

/** A day-bucketing formatter pinned to [SHOP_TIME_ZONE]. Use for grouping/filtering transactions
 *  by calendar day — not for display-only timestamps, where the device's local time is fine. */
fun shopDateFormat(pattern: String): SimpleDateFormat =
    SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = SHOP_TIME_ZONE }

fun shopCalendar(): Calendar = Calendar.getInstance(SHOP_TIME_ZONE)
