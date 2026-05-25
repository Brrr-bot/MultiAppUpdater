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

class FinanceDb(context: Context) : SQLiteOpenHelper(context, "finance.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS entries (
                id        INTEGER PRIMARY KEY,
                ts        INTEGER NOT NULL,
                direction TEXT    NOT NULL,
                amount    REAL    NOT NULL,
                what      TEXT    NOT NULL,
                category  TEXT    NOT NULL
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = onCreate(db)

    // ── Write ─────────────────────────────────────────────────────────────────

    fun replaceAllEntries(entries: JSONArray) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM entries")
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
            put("amount", amount); put("what", what); put("category", category)
        }
        writableDatabase.insertWithOnConflict("entries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun entryToValues(e: JSONObject) = ContentValues().apply {
        put("id",        e.getInt("id"))
        put("ts",        e.getLong("ts"))
        put("direction", e.getString("direction"))
        put("amount",    e.getDouble("amount"))
        put("what",      e.getString("what"))
        put("category",  e.getString("category"))
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
