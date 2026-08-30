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

enum class TimeHorizon(val label: String, val shortLabel: String) {
    DAY("Day", "Today"),
    WEEK("Week", "7D"),
    THIRTY_DAYS("30D", "30D"),
    MTD("MTD", "MTD"),
    CUSTOM("Custom", "Custom"),
    ALL("All Time", "All")
}

data class DateRangeBoundary(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val displayLabel: String
)

fun getTimeHorizonBoundary(
    horizon: TimeHorizon,
    customStartDateStr: String? = null,
    customEndDateStr: String? = null
): DateRangeBoundary {
    val cal = shopCalendar()
    val sdf = shopDateFormat("yyyy-MM-dd")
    val displayFmt = shopDateFormat("MMM d, yyyy")
    val shortMonthDay = shopDateFormat("MMM d")

    when (horizon) {
        TimeHorizon.DAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            return DateRangeBoundary(start, end, "Today (${shortMonthDay.format(java.util.Date(start))})")
        }
        TimeHorizon.WEEK -> {
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            cal.add(Calendar.DAY_OF_YEAR, -6)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            return DateRangeBoundary(start, end, "Last 7 Days (${shortMonthDay.format(java.util.Date(start))} – ${shortMonthDay.format(java.util.Date(end))})")
        }
        TimeHorizon.THIRTY_DAYS -> {
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            cal.add(Calendar.DAY_OF_YEAR, -29)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            return DateRangeBoundary(start, end, "Last 30 Days (${shortMonthDay.format(java.util.Date(start))} – ${shortMonthDay.format(java.util.Date(end))})")
        }
        TimeHorizon.MTD -> {
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            return DateRangeBoundary(start, end, "Month to Date (${shortMonthDay.format(java.util.Date(start))} – ${shortMonthDay.format(java.util.Date(end))})")
        }
        TimeHorizon.CUSTOM -> {
            val startParsed = customStartDateStr?.let { try { sdf.parse(it) } catch (_: Exception) { null } }
            val endParsed = customEndDateStr?.let { try { sdf.parse(it) } catch (_: Exception) { null } } ?: startParsed

            if (startParsed != null && endParsed != null) {
                cal.time = startParsed
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.time = endParsed
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis

                val label = if (sdf.format(startParsed) == sdf.format(endParsed)) {
                    displayFmt.format(startParsed)
                } else {
                    "${shortMonthDay.format(startParsed)} – ${displayFmt.format(endParsed)}"
                }
                return DateRangeBoundary(start, end, label)
            }
            return DateRangeBoundary(0L, Long.MAX_VALUE, "Custom Range")
        }
        TimeHorizon.ALL -> {
            return DateRangeBoundary(0L, Long.MAX_VALUE, "All Time")
        }
    }
}
