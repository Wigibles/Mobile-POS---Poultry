package com.example.data

import android.util.Log
import com.example.data.local.PendingSheetEntry
import com.example.data.local.PendingTransactionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "SheetSyncManager"

/**
 * Pushes transaction records to Google Sheets via a Google Apps Script web app.
 *
 * When online, entries are sent immediately. When offline, they're queued in the
 * local Room database and flushed automatically when connectivity returns.
 *
 * Usage:
 *   1. Deploy [sheets-sync.gs] as a Google Apps Script Web App.
 *   2. Paste the web app URL into [SHEET_WEB_APP_URL] below.
 *   3. Call [enqueueSheetEntry] after every transaction.
 *   4. [syncPendingSheetEntries] is called automatically on reconnect.
 */
class SheetSyncManager(
    private val pendingDao: PendingTransactionDao,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Set this to your deployed Google Apps Script web app URL. */
    var sheetWebAppUrl: String = ""

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Queue a transaction for Google Sheets sync. If the device is online the
     * payload is sent immediately; otherwise it's stored locally for later.
     */
    fun enqueueSheetEntry(
        transactionId: Int,
        payload: JSONObject
    ) {
        if (sheetWebAppUrl.isBlank()) {
            Log.d(TAG, "Sheet sync disabled (no URL) — not queuing tx #$transactionId")
            return
        }
        scope.launch {
            try {
                if (networkMonitor.isOnline.value) {
                    // Try immediate send
                    val success = postToSheet(payload)
                    if (success) {
                        Log.d(TAG, "Sheet entry sent immediately for tx #$transactionId")
                        return@launch
                    }
                }
                // Queue for later (offline or send failed)
                pendingDao.insertSheetEntry(
                    PendingSheetEntry(
                        transactionId = transactionId,
                        payloadJson = payload.toString()
                    )
                )
                Log.d(TAG, "Sheet entry queued for tx #$transactionId (offline or retry)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue sheet entry for tx #$transactionId", e)
                // Final try to store locally
                try {
                    pendingDao.insertSheetEntry(
                        PendingSheetEntry(
                            transactionId = transactionId,
                            payloadJson = payload.toString()
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Sends a status-update payload to Google Sheets (UNPAID → PAID).
     * If offline the update is queued and retried when connectivity returns.
     */
    fun enqueueStatusUpdate(transactionId: Int, newStatus: String) {
        if (sheetWebAppUrl.isBlank()) {
            Log.d(TAG, "Sheet sync disabled — not sending status update for tx #$transactionId")
            return
        }
        val payload = JSONObject().apply {
            put("action", "status_update")
            put("transactionId", transactionId.toString())
            put("status", newStatus)
        }
        scope.launch {
            try {
                if (networkMonitor.isOnline.value) {
                    val success = postToSheet(payload)
                    if (success) {
                        Log.d(TAG, "Status update sent immediately for tx #$transactionId → $newStatus")
                        return@launch
                    }
                }
                // Queue for later
                pendingDao.insertSheetEntry(
                    PendingSheetEntry(
                        transactionId = transactionId,
                        payloadJson = payload.toString()
                    )
                )
                Log.d(TAG, "Status update queued for tx #$transactionId → $newStatus")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue status update for tx #$transactionId", e)
                try {
                    pendingDao.insertSheetEntry(
                        PendingSheetEntry(
                            transactionId = transactionId,
                            payloadJson = payload.toString()
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Flush all queued sheet entries to Google Sheets.
     * Called automatically when the device reconnects.
     * Returns the number of successfully synced entries.
     */
    suspend fun syncPendingSheetEntries(): Int = withContext(Dispatchers.IO) {
        if (sheetWebAppUrl.isBlank()) {
            Log.w(TAG, "Sheet web app URL is not configured — skipping sync")
            return@withContext 0
        }
        var synced = 0
        try {
            val unsynced = pendingDao.getUnsyncedSheetEntries()
            if (unsynced.isEmpty()) return@withContext 0

            Log.i(TAG, "Syncing ${unsynced.size} pending sheet entries")

            for (entry in unsynced) {
                try {
                    val payload = JSONObject(entry.payloadJson)
                    val success = postToSheet(payload)
                    if (success) {
                        pendingDao.deleteSheetEntryById(entry.localId)
                        synced++
                    } else {
                        // Stop on first failure — remaining entries stay queued
                        Log.w(TAG, "Sheet sync paused after failure on entry ${entry.localId}")
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync sheet entry ${entry.localId}", e)
                    break
                }
            }

            Log.i(TAG, "Sheet sync complete: $synced/${unsynced.size} entries sent")
        } catch (e: Exception) {
            Log.e(TAG, "Sheet sync batch failed", e)
        }
        synced
    }

    // ── HTTP helpers ────────────────────────────────────────────────────────

    private suspend fun postToSheet(payload: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(sheetWebAppUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false // don't follow Google auth redirects

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val responseBody = try {
                    connection.inputStream.bufferedReader().readText()
                } catch (_: Exception) {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }
                connection.disconnect()

                if (responseCode in 200..299) {
                    Log.d(TAG, "Sheet POST succeeded (HTTP $responseCode): $responseBody")
                    true
                } else {
                    Log.w(TAG, "Sheet POST failed (HTTP $responseCode): $responseBody")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sheet POST error: ${e.javaClass.simpleName} — ${e.message}")
                false
            }
        }
}
