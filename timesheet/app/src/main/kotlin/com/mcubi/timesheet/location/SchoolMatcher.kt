package com.mcubi.timesheet.location

import android.content.Context
import org.json.JSONArray
import kotlin.math.*

class SchoolMatcher(private val context: Context) {

    data class School(
        val name:    String,
        val type:    String,
        val lat:     Double,
        val lng:     Double,
        val polygon: List<Pair<Double, Double>> = emptyList(),
        val radiusM: Double = 80.0
    )

    private val MIN_VISIT_MS = 35 * 60 * 1000L
    private val MIN_LOG_MS   = 10 * 60 * 1000L

    companion object {
        // Process-wide cache of the parsed school list (parsed once, reused).
        @Volatile private var cachedSchools: List<School>? = null
        private val cacheLock = Any()

        /** Call after the user adds/edits a custom location so the next load re-reads it. */
        fun invalidateCache() { cachedSchools = null }
    }

    // ── Load schools from asset ───────────────────────────────────────────────

    fun loadSchools(): List<School> {
        // Parse the ~590KB polygon DB at most once per process. Re-parsing on every
        // call (fetchTodayPending runs on each onResume) churned the heap and OOM-crashed.
        cachedSchools?.let { return it }
        synchronized(cacheLock) {
            cachedSchools?.let { return it }
            val parsed = try { parseSchools() } catch (_: Throwable) { emptyList() }
            cachedSchools = parsed
            return parsed
        }
    }

    private fun parseSchools(): List<School> {
        val list = mutableListOf<School>()

        // Primary polygon database
        val json = context.assets.open("hcmc_school_polygons.json").bufferedReader().readText()
        val arr  = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o    = arr.getJSONObject(i)
            val name = o.getString("name")
            val type = o.getString("type")
            val lat  = o.getDouble("lat")
            val lng  = o.getDouble("lng")

            if (o.has("polygon")) {
                val polyArr = o.getJSONArray("polygon")
                val pts = mutableListOf<Pair<Double, Double>>()
                for (j in 0 until polyArr.length()) {
                    val pt = polyArr.getJSONArray(j)
                    pts.add(Pair(pt.getDouble(0), pt.getDouble(1)))
                }
                list.add(School(name, type, lat, lng, polygon = pts))
            } else {
                val r = if (o.has("radius_m")) o.getDouble("radius_m") else 80.0
                list.add(School(name, type, lat, lng, radiusM = r))
            }
        }

        // Custom locations (non-school venues like STEM Club)
        try {
            val customJson = context.assets.open("custom_locations.json").bufferedReader().readText()
            val customArr  = JSONArray(customJson)
            for (i in 0 until customArr.length()) {
                val o = customArr.getJSONObject(i)
                val r = if (o.has("radius_m")) o.getDouble("radius_m") else 80.0
                list.add(School(
                    name    = o.getString("name"),
                    type    = o.optString("type", "custom"),
                    lat     = o.getDouble("lat"),
                    lng     = o.getDouble("lng"),
                    radiusM = r
                ))
            }
        } catch (_: Exception) {}

        // User-defined locations saved in-app (filesDir/user_locations.json)
        try {
            val userFile = java.io.File(context.filesDir, "user_locations.json")
            if (userFile.exists()) {
                val userArr = JSONArray(userFile.readText())
                for (i in 0 until userArr.length()) {
                    val o = userArr.getJSONObject(i)
                    val r = if (o.has("radius_m")) o.getDouble("radius_m") else 80.0
                    list.add(School(
                        name    = o.getString("name"),
                        type    = o.optString("type", "custom"),
                        lat     = o.getDouble("lat"),
                        lng     = o.getDouble("lng"),
                        radiusM = r
                    ))
                }
            }
        } catch (_: Exception) {}

        return list
    }

    // ── Containment check ─────────────────────────────────────────────────────

    // accuracyM: GPS accuracy radius — used to widen the containment check.
    // For polygon schools: accept if inside polygon OR within (radiusM + accuracyM) of
    //   the school centre. This handles GPS drift that pushes readings just outside
    //   a tight polygon boundary while still being physically at the school.
    // For radius-only schools: standard haversine + accuracy bonus.
    fun School.contains(lat: Double, lng: Double, accuracyM: Double = 0.0): Boolean =
        if (polygon.isNotEmpty())
            pointInPolygon(lat, lng, polygon) ||
                haversineM(lat, lng, this.lat, this.lng) <= radiusM + accuracyM
        else haversineM(lat, lng, this.lat, this.lng) <= radiusM + accuracyM

    private fun pointInPolygon(lat: Double, lng: Double,
                                poly: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i].second; val yi = poly[i].first
            val xj = poly[j].second; val yj = poly[j].first
            if ((yi > lat) != (yj > lat) &&
                lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    // ── Parse today's CSV rows ────────────────────────────────────────────────

    data class LocRow(val ts: Long, val lat: Double, val lng: Double, val accuracyM: Double)

    fun loadTodayRows(): List<LocRow> {
        val dir  = context.getExternalFilesDir(null) ?: context.filesDir
        val file = java.io.File(dir, "location_history.csv")
        if (!file.exists()) return emptyList()

        val todayStart = startOfTodayMs()
        val rows = mutableListOf<LocRow>()

        file.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val parts = line.split(",")
                if (parts.size < 4) return@forEach
                val ts  = parts[0].toLongOrNull() ?: return@forEach
                if (ts < todayStart) return@forEach
                val lat = parts[2].toDoubleOrNull() ?: return@forEach
                val lng = parts[3].toDoubleOrNull() ?: return@forEach
                val acc = parts.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                rows.add(LocRow(ts, lat, lng, acc))
            }
        }
        return rows.sortedBy { it.ts }
    }

    // ── Main matching logic ───────────────────────────────────────────────────

    data class SchoolVisit(
        val school:       School,
        val minutesSpent: Int,
        val likelyTaught: Boolean,
        val arrivedAt:    Long,
        val departedAt:   Long
    )

    fun findAllSchoolVisitsToday(): List<SchoolVisit> {
        val rows = loadTodayRows()
        if (rows.isEmpty()) return emptyList()
        val schools = loadSchools()

        val SEGMENT_GAP_MS     = 15 * 60 * 1000L   // skip pairs with no data between them
        val SEPARATE_VISIT_GAP = 60 * 60 * 1000L   // ≥ 60 min between windows = new visit

        data class Window(var firstTs: Long, var lastTs: Long, var totalMs: Long)
        val windowsMap = mutableMapOf<School, MutableList<Window>>()

        for (i in 0 until rows.size - 1) {
            val a   = rows[i]
            val b   = rows[i + 1]
            val gap = b.ts - a.ts
            if (gap > SEGMENT_GAP_MS) continue

            val midLat = (a.lat + b.lat) / 2
            val midLng = (a.lng + b.lng) / 2
            // Accuracy bonus for radius-only schools (no polygon). Capped at 150 m.
            // Schools with polygon data ignore this entirely (point-in-polygon check).
            val midAcc = minOf((a.accuracyM + b.accuracyM) / 2.0, 150.0)

            for (school in schools) {
                if (!school.contains(midLat, midLng, midAcc)) continue

                val windows = windowsMap.getOrPut(school) { mutableListOf() }
                val last    = windows.lastOrNull()

                if (last == null || (a.ts - last.lastTs) > SEPARATE_VISIT_GAP) {
                    windows.add(Window(firstTs = a.ts, lastTs = b.ts, totalMs = gap))
                } else {
                    if (a.ts < last.firstTs) last.firstTs = a.ts
                    if (b.ts > last.lastTs)  last.lastTs  = b.ts
                    last.totalMs += gap
                }
            }
        }

        val result = mutableListOf<SchoolVisit>()
        for ((school, windows) in windowsMap) {
            for (w in windows) {
                if (w.totalMs < MIN_LOG_MS) continue
                // Wall-clock span is the reliable measure: GPS gaps inside a session
                // don't shrink it below the 45-min threshold.
                val wallClockMs = w.lastTs - w.firstTs
                // likelyTaught if wall-clock ≥ 45 min OR measured GPS time ≥ 20 min
                // (the OR handles the case where the service was killed mid-session,
                //  leaving a gap that truncates the wall-clock span below 45 min)
                result.add(SchoolVisit(
                    school       = school,
                    minutesSpent = (w.totalMs / 60_000).toInt(),
                    likelyTaught = wallClockMs >= MIN_VISIT_MS || w.totalMs >= 20 * 60 * 1000L,
                    arrivedAt    = w.firstTs,
                    departedAt   = w.lastTs
                ))
            }
        }
        return result.sortedByDescending { it.minutesSpent }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun startOfTodayMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a    = sin(dLat / 2).pow(2) +
                   cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return R * 2 * asin(sqrt(a))
    }
}
