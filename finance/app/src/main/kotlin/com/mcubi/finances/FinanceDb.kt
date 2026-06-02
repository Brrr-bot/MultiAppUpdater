package com.mcubi.finances

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class FinanceDb(context: Context) : SQLiteOpenHelper(context, "finance.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS entries (
                id        INTEGER PRIMARY KEY,
                ts        INTEGER NOT NULL,
                direction TEXT    NOT NULL,
                amount    REAL    NOT NULL,
                what      TEXT    NOT NULL,
                category  TEXT    NOT NULL,
                synced    INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS savings_cache (
                id    INTEGER PRIMARY KEY DEFAULT 1,
                total REAL    NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("INSERT OR IGNORE INTO savings_cache (id, total) VALUES (1, 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 -> v2: add the `synced` column (existing rows came from the server, so synced=1)
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE entries ADD COLUMN synced INTEGER NOT NULL DEFAULT 1") } catch (_: Exception) {}
        }
        onCreate(db)
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Replace the server-synced set with a fresh server snapshot, but KEEP locally-added
     *  entries that haven't been pushed yet (synced = 0) so offline work is never lost. */
    fun replaceAllEntries(entries: JSONArray) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM entries WHERE synced = 1")
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                db.insertWithOnConflict("entries", null, entryToValues(e), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertEntry(id: Int, ts: Long, direction: String, amount: Double, what: String, category: String) {
        val cv = ContentValues().apply {
            put("id", id); put("ts", ts); put("direction", direction)
            put("amount", amount); put("what", what); put("category", category); put("synced", 1)
        }
        writableDatabase.insertWithOnConflict("entries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Save a brand-new entry locally with synced=0 (not yet on the server). Returns its
     *  local id (a negative number, so it can never collide with a server id). */
    fun insertLocalEntry(ts: Long, direction: String, amount: Double, what: String, category: String): Int {
        val db = writableDatabase
        val minId = db.rawQuery("SELECT MIN(id) FROM entries", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val localId = minOf(minId, 0) - 1
        val cv = ContentValues().apply {
            put("id", localId); put("ts", ts); put("direction", direction)
            put("amount", amount); put("what", what); put("category", category); put("synced", 0)
        }
        db.insertWithOnConflict("entries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return localId
    }

    /** Entries added locally that still need to be pushed to the server. */
    fun getUnsyncedEntries(): JSONArray =
        cursorToArray(readableDatabase.rawQuery(
            "SELECT * FROM entries WHERE synced = 0 ORDER BY ts ASC", null))

    /** After a successful push: swap the temporary local row for the server-assigned id. */
    fun markEntrySynced(localId: Int, serverId: Int, serverTs: Long,
                        direction: String, amount: Double, what: String, category: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entries", "id = ?", arrayOf(localId.toString()))
            val cv = ContentValues().apply {
                put("id", serverId); put("ts", serverTs); put("direction", direction)
                put("amount", amount); put("what", what); put("category", category); put("synced", 1)
            }
            db.insertWithOnConflict("entries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteEntry(id: Int) {
        writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
    }

    fun saveSavingsTotal(total: Double) {
        val cv = ContentValues().apply { put("id", 1); put("total", total) }
        writableDatabase.insertWithOnConflict("savings_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun getSalaryDates(): List<LocalDate> {
        val cursor = readableDatabase.rawQuery(
            "SELECT ts FROM entries WHERE category = 'Salary' AND direction = 'in' ORDER BY ts ASC", null
        )
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dates = mutableSetOf<LocalDate>()
        cursor.use {
            while (it.moveToNext()) dates.add(LocalDate.parse(fmt.format(java.util.Date(it.getLong(0)))))
        }
        return dates.toList()
    }

    fun getEntriesForPeriod(from: LocalDate, to: LocalDate): JSONArray {
        val start = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM entries WHERE ts >= ? AND ts < ? ORDER BY ts DESC",
            arrayOf(start.toString(), end.toString())
        )
        return cursorToArray(cursor)
    }

    fun getSummaryForPeriod(from: LocalDate?, to: LocalDate): Pair<Double, Double> {
        val end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val cursor = if (from != null) {
            val start = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            readableDatabase.rawQuery(
                "SELECT direction, SUM(amount) FROM entries WHERE ts >= ? AND ts < ? GROUP BY direction",
                arrayOf(start.toString(), end.toString())
            )
        } else {
            readableDatabase.rawQuery(
                "SELECT direction, SUM(amount) FROM entries WHERE ts < ? GROUP BY direction",
                arrayOf(end.toString())
            )
        }
        var income = 0.0; var expense = 0.0
        cursor.use {
            while (it.moveToNext()) {
                if (it.getString(0) == "in") income = it.getDouble(1)
                else expense = it.getDouble(1)
            }
        }
        return income to expense
    }

    fun getSavingsTotal(): Double {
        val cursor = readableDatabase.rawQuery("SELECT total FROM savings_cache WHERE id = 1", null)
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    /** Per-category totals for a date range, split into income (in) and expense (out).
     *  Returns an array of {category, in, out} objects, sorted by out desc. */
    fun getCategoryBreakdown(from: LocalDate, to: LocalDate): JSONArray {
        val start = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val cursor = readableDatabase.rawQuery(
            """SELECT category, direction, SUM(amount) AS total, COUNT(*) AS cnt
               FROM entries WHERE ts >= ? AND ts < ?
               GROUP BY category, direction""",
            arrayOf(start.toString(), end.toString())
        )
        // category -> [in, out, inCount, outCount]
        val map = LinkedHashMap<String, DoubleArray>()
        cursor.use {
            while (it.moveToNext()) {
                val cat = it.getString(0); val dir = it.getString(1)
                val total = it.getDouble(2); val cnt = it.getDouble(3)
                val row = map.getOrPut(cat) { DoubleArray(4) }
                if (dir == "in") { row[0] = total; row[2] = cnt } else { row[1] = total; row[3] = cnt }
            }
        }
        val list = map.entries.sortedByDescending { it.value[1] }   // by expense desc
        val out = JSONArray()
        for ((cat, v) in list) {
            out.put(JSONObject().apply {
                put("category", cat); put("in", v[0]); put("out", v[1])
                put("inCount", v[2].toInt()); put("outCount", v[3].toInt())
            })
        }
        return out
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun entryToValues(e: JSONObject) = ContentValues().apply {
        put("id",        e.getInt("id"))
        put("ts",        e.getLong("ts"))
        put("direction", e.getString("direction"))
        put("amount",    e.getDouble("amount"))
        put("what",      e.getString("what"))
        put("category",  e.getString("category"))
        put("synced",    1)   // came from the server
    }

    private fun cursorToArray(cursor: android.database.Cursor): JSONArray {
        val result = JSONArray()
        cursor.use {
            while (it.moveToNext()) {
                result.put(JSONObject().apply {
                    put("id",        it.getInt(it.getColumnIndexOrThrow("id")))
                    put("ts",        it.getLong(it.getColumnIndexOrThrow("ts")))
                    put("direction", it.getString(it.getColumnIndexOrThrow("direction")))
                    put("amount",    it.getDouble(it.getColumnIndexOrThrow("amount")))
                    put("what",      it.getString(it.getColumnIndexOrThrow("what")))
                    put("category",  it.getString(it.getColumnIndexOrThrow("category")))
                })
            }
        }
        return result
    }
}
