/**
 * Google Apps Script — Alyn's Poultry POS → Google Sheets Sync
 * =================================================================
 * Deploy as a Web App (Execute as: "Me", Access: "Anyone"):
 *   1. Open https://script.google.com
 *   2. Create a new project, paste this entire file.
 *   3. Deploy → New Deployment → Web App → Deploy.
 *   4. Copy the web app URL — paste it into SheetSyncManager.kt (SHEET_WEB_APP_URL).
 *
 * The Android POS app POSTs JSON to this URL after every transaction.
 * If the device is offline the app queues the payload locally and sends
 * it when connectivity returns.
 */

// ── Your spreadsheet ────────────────────────────────────────────────────────
var SPREADSHEET_ID = '1TGqvatLBgD8Qgut94HvaLJg3vETWS-eJULywiv0BloY';
var SHEET_NAME = 'Transactions';

// ── Column headers (row 1) ──────────────────────────────────────────────────
var HEADERS = [
  'Date',
  'Time',
  'Order #',
  'Customer',
  'Status',
  'Items',
  'Qty',
  'Total (₱)',
  'Tx ID',
  'Synced At'
];

// Column indices (1-based) for updating specific cells
var COL_STATUS = 5;   // E — Status
var COL_TX_ID  = 9;   // I — Tx ID (hidden, for matching)
var COL_SYNCED = 10;  // J — Synced At

// ── Web App entry point ─────────────────────────────────────────────────────
function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);
    return handlePost(payload);
  } catch (err) {
    return jsonResponse({ success: false, error: err.toString() });
  }
}

function doGet(e) {
  return jsonResponse({ status: 'ok', message: 'Alyn\'s Poultry POS sync endpoint is running.' });
}

// ── Core logic ──────────────────────────────────────────────────────────────
function handlePost(data) {
  var sheet = getOrCreateSheet();

  // Write header row if the sheet is empty
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(HEADERS);
  }

  // Status update: find existing row by transaction ID and update the Status cell
  if (data.action === 'status_update' && data.transactionId) {
    return handleStatusUpdate(sheet, data);
  }

  // List all tracked transaction IDs — app uses this to find missing rows
  if (data.action === 'list_ids') {
    return handleListIds(sheet);
  }

  // Scan for and report duplicates in the sheet
  if (data.action === 'find_duplicates') {
    return handleFindDuplicates(sheet);
  }

  // Remove all duplicate rows (keeps first occurrence)
  if (data.action === 'cleanup_duplicates') {
    return handleCleanupDuplicates(sheet);
  }

  // ── Duplicate check (two-tier) ──
  // Tier 1: by transaction ID (most reliable)
  var txId = String(data.transactionId || '');
  if (txId && txId !== '0' && txId !== '-0') {
    var existingRow = findRowByTxId(sheet, txId);
    if (existingRow > 0) {
      return jsonResponse({ success: true, duplicate: true, row: existingRow,
        message: 'Transaction ' + txId + ' already exists at row ' + existingRow });
    }
  }

  // Tier 2: fallback composite match (date + time + items + total)
  // Catches duplicates that slipped through without a valid transaction ID.
  if (!txId || txId === '0' || txId === '-0') {
    var compositeMatch = findRowByComposite(sheet, data);
    if (compositeMatch > 0) {
      return jsonResponse({ success: true, duplicate: true, row: compositeMatch,
        message: 'Matching transaction already exists at row ' + compositeMatch + ' (composite match)' });
    }
  }

  // New transaction row — insert at the correct chronological position
  var newRow = insertSorted(sheet, data, txId);

  // Hide the Tx ID column so it doesn't clutter the sheet
  try { sheet.hideColumns(COL_TX_ID, 1); } catch(e) {}

  return jsonResponse({ success: true, row: newRow });
}

// ── Find a row by transaction ID (returns row number, or -1 if not found) ───
function findRowByTxId(sheet, txId) {
  var lastRow = sheet.getLastRow();
  var searchId = String(txId);
  for (var row = 2; row <= lastRow; row++) {
    var cellValue = String(sheet.getRange(row, COL_TX_ID).getValue() || '');
    if (cellValue === searchId) {
      return row;
    }
  }
  return -1;
}

// ── Status update: find & update ────────────────────────────────────────────
function handleStatusUpdate(sheet, data) {
  var txId = String(data.transactionId);
  var newStatus = data.status || 'PAID';
  var foundRow = findRowByTxId(sheet, txId);

  if (foundRow > 0) {
    sheet.getRange(foundRow, COL_STATUS).setValue(newStatus);
    sheet.getRange(foundRow, COL_SYNCED).setValue(
      new Date().toLocaleString('en-PH', { timeZone: 'Asia/Manila' })
    );
    return jsonResponse({ success: true, updated: true, txId: txId, status: newStatus });
  } else {
    // Row not found — transaction may not have synced yet, or was deleted.
    // Silently succeed so the app doesn't keep retrying.
    return jsonResponse({ success: true, updated: false, reason: 'txId not found in sheet' });
  }
}

// ── Helpers ─────────────────────────────────────────────────────────────────
function getOrCreateSheet() {
  var ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
  }
  return sheet;
}

/**
 * Inserts a new transaction row at the correct chronological position
 * (sorted by Date ascending, then Time ascending). Returns the row number.
 */
function insertSorted(sheet, data, txId) {
  var rowData = [
    data.date         || '',
    data.time         || '',
    data.orderNumber  || '',
    data.customer     || '',
    data.status       || '',
    data.items        || '',
    data.quantity     || '',
    data.total        || 0,
    txId,
    new Date().toLocaleString('en-PH', { timeZone: 'Asia/Manila' })
  ];

  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) {
    // Only header row exists — just append
    sheet.appendRow(rowData);
    return 2;
  }

  // Parse new entry's date+time for comparison
  var newDate = data.date || '';
  var newTime = data.time || '';
  var newTimeMinutes = timeToMinutes(newTime);

  // Find insertion point: scan from row 2 downward
  var insertAt = lastRow + 1; // default: append at end
  for (var row = 2; row <= lastRow; row++) {
    var rowDate = String(sheet.getRange(row, 1).getValue() || '');
    var rowTime = String(sheet.getRange(row, 2).getValue() || '');
    var rowTimeMinutes = timeToMinutes(rowTime);

    if (rowDate > newDate) {
      insertAt = row;
      break;
    } else if (rowDate === newDate && rowTimeMinutes > newTimeMinutes) {
      insertAt = row;
      break;
    }
  }

  if (insertAt <= lastRow) {
    sheet.insertRowBefore(insertAt);
  }
  sheet.getRange(insertAt, 1, 1, rowData.length).setValues([rowData]);
  return insertAt;
}

/**
 * Converts a time string like "2:30 PM" to minutes since midnight.
 */
function timeToMinutes(timeStr) {
  if (!timeStr) return 0;
  var parts = timeStr.toLowerCase().split(':');
  if (parts.length < 2) return 0;
  var hours = parseInt(parts[0], 10) || 0;
  var mins = parseInt(parts[1], 10) || 0;
  if (timeStr.indexOf('pm') >= 0 && hours < 12) hours += 12;
  if (timeStr.indexOf('am') >= 0 && hours === 12) hours = 0;
  return hours * 60 + mins;
}

/**
 * Returns all transaction IDs currently in the sheet.
 * The app uses this to determine which transactions are missing and need re-syncing.
 */
function handleListIds(sheet) {
  var ids = [];
  var lastRow = sheet.getLastRow();
  for (var row = 2; row <= lastRow; row++) {
    var txId = String(sheet.getRange(row, COL_TX_ID).getValue() || '');
    if (txId && txId !== '0') {
      ids.push(txId);
    }
  }
  return jsonResponse({ success: true, count: ids.length, ids: ids });
}

/**
 * Fallback duplicate check: matches on date + time + items summary + total.
 * Used when the transactionId is missing (e.g. offline transactions).
 */
function findRowByComposite(sheet, data) {
  var lastRow = sheet.getLastRow();
  var newDate = String(data.date || '');
  var newTime = String(data.time || '');
  var newItems = String(data.items || '');
  var newTotal = parseFloat(data.total || 0);

  if (!newDate || !newItems) return -1;

  for (var row = 2; row <= lastRow; row++) {
    var rowDate = String(sheet.getRange(row, 1).getValue() || '');
    var rowTime = String(sheet.getRange(row, 2).getValue() || '');
    var rowItems = String(sheet.getRange(row, 6).getValue() || '');
    var rowTotal = parseFloat(sheet.getRange(row, 8).getValue() || 0);

    if (rowDate === newDate && rowItems === newItems && Math.abs(rowTotal - newTotal) < 0.01) {
      var timeDiff = Math.abs(timeToMinutes(rowTime) - timeToMinutes(newTime));
      if (timeDiff <= 1) return row;
    }
  }
  return -1;
}

/**
 * Scans the sheet for duplicate transaction IDs and returns them.
 */
function handleFindDuplicates(sheet) {
  var lastRow = sheet.getLastRow();
  var seen = {};
  var duplicates = [];

  for (var row = 2; row <= lastRow; row++) {
    var txId = String(sheet.getRange(row, COL_TX_ID).getValue() || '');
    if (!txId || txId === '0') continue;

    if (seen[txId]) {
      duplicates.push({
        txId: txId,
        firstRow: seen[txId],
        duplicateRow: row,
        date: String(sheet.getRange(row, 1).getValue() || '')
      });
    } else {
      seen[txId] = row;
    }
  }

  return jsonResponse({
    success: true,
    duplicateCount: duplicates.length,
    duplicates: duplicates
  });
}

/**
 * Removes all duplicate rows (keeps the first occurrence by Tx ID).
 * Also removes composite duplicates (same date + items + total with no Tx ID).
 */
function handleCleanupDuplicates(sheet) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return jsonResponse({ success: true, removed: 0 });

  var seenTxIds = {};
  var compositeKeys = {};
  var rowsToDelete = [];
  var removed = 0;

  for (var row = 2; row <= lastRow; row++) {
    var txId = String(sheet.getRange(row, COL_TX_ID).getValue() || '');

    // Check by transaction ID
    if (txId && txId !== '0' && txId !== '-0') {
      if (seenTxIds[txId]) {
        rowsToDelete.push(row);
        continue;
      }
      seenTxIds[txId] = row;
    }

    // Check by composite key (date + items + total)
    var date = String(sheet.getRange(row, 1).getValue() || '');
    var items = String(sheet.getRange(row, 6).getValue() || '');
    var total = String(sheet.getRange(row, 8).getValue() || '');
    var compositeKey = date + '|' + items + '|' + total;

    if (compositeKeys[compositeKey]) {
      if (!txId || txId === '0' || txId === '-0') {
        rowsToDelete.push(row);
        continue;
      }
    }
    compositeKeys[compositeKey] = row;
  }

  // Delete from bottom up to preserve row numbers
  rowsToDelete.sort(function(a, b) { return b - a; });
  for (var i = 0; i < rowsToDelete.length; i++) {
    sheet.deleteRow(rowsToDelete[i]);
    removed++;
  }

  return jsonResponse({
    success: true,
    removed: removed,
    message: 'Removed ' + removed + ' duplicate row(s)'
  });
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
