package com.mcubi.timesheet.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class LocationTracker(
    private val context: Context,
    private val onLog: ((String) -> Unit)? = null,
    private val onTeachingDetected: ((SchoolMatcher.School, Long, Int) -> Unit)? = null,
    private val onTeachingEnded: ((SchoolMatcher.School, Int) -> Unit)? = null
) {

    private val TAG = "LocationTracker"

    private fun log(msg: String) {
        onLog?.invoke(msg) ?: Log.i(TAG, msg)
    }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile private var bestLocation: Location? = null

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // ── Live school proximity tracking ────────────────────────────────────────

    private val SCHOOL_DETECT_MS  = 10 * 60 * 1000L
    private val SCHOOL_TEACH_MS   = 35 * 60 * 1000L
    private val SCHOOL_GAP_MAX_MS = 20 * 60 * 1000L  // tolerate up to 20 min service gaps

    private var schoolsList: List<SchoolMatcher.School>? = null

    private data class SchoolTrack(
        var firstTs:      Long,
        var lastTs:       Long,
        var totalMs:      Long,
        var logged10:     Boolean = false,
        var logged45:     Boolean = false,
        var insideRadius: Boolean = true,
        var loggedDepart: Boolean = false,
        var outsideCount: Int     = 0
    )
    private val schoolTracks = mutableMapOf<SchoolMatcher.School, SchoolTrack>()

    private var prevFixLoc: Location? = null
    private var prevFixTs:  Long = 0L

    // ── CSV log file ──────────────────────────────────────────────────────────

    private val csvFile: File by lazy {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, "location_history.csv").also { f ->
            if (!f.exists()) f.writeText("timestamp_ms,datetime,lat,lng,accuracy_m,provider\n")
        }
    }

    // ── Location listener ─────────────────────────────────────────────────────

    private val listener = LocationListener { loc ->
        val current = bestLocation
        val isMoreAccurate = loc.accuracy < (current?.accuracy ?: Float.MAX_VALUE)
        val isSignificantlyNewer = current != null && (loc.time - current.time) > 90_000L
        if (current == null || isMoreAccurate || isSignificantlyNewer) {
            bestLocation = loc
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun hasPermission(): Boolean {
        val fine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        if (!hasPermission()) {
            Log.w(TAG, "start() — no location permission")
            return
        }
        try {
            var registered = 0
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider, 30_000L, 0f, listener, Looper.getMainLooper()
                    )
                    registered++
                }
            }
            seedLastKnown()
            log("[LOC] start() — $registered provider(s) registered")
        } catch (e: SecurityException) {
            Log.e(TAG, "start() SecurityException: ${e.message}")
        }
    }

    fun stop() {
        try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
    }

    // ── Main log-and-send (called every minute) ───────────────────────────────

    fun logAndSend() {
        if (!hasPermission()) return
        val loc = freshLocation()
        if (loc == null) {
            log("[LOC] no fix available")
            return
        }

        val ts       = System.currentTimeMillis()
        val dtStr    = dateFmt.format(Date(ts))
        val lat      = loc.latitude
        val lng      = loc.longitude
        val acc      = loc.accuracy
        val provider = loc.provider ?: "unknown"

        log("[LOC] $dtStr  lat=${"%.5f".format(lat)}  lng=${"%.5f".format(lng)}  acc=${acc.toInt()}m  via=$provider")

        try {
            csvFile.appendText("$ts,$dtStr,$lat,$lng,$acc,$provider\n")
        } catch (_: Exception) {}

        checkSchoolProximity(loc, ts)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun seedLastKnown() {
        try {
            val now = System.currentTimeMillis()
            for (provider in locationManager.getProviders(true)) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                val cur = bestLocation
                val isNewer   = cur == null || loc.time > cur.time
                val notTooOld = (now - loc.time) < 5 * 60 * 1000L
                if (isNewer && notTooOld) bestLocation = loc
            }
        } catch (_: SecurityException) {}
    }

    private fun freshLocation(): Location? {
        val live = bestLocation
        if (live != null && System.currentTimeMillis() - live.time < 300_000L) return live
        seedLastKnown()
        return bestLocation
    }

    private fun SchoolMatcher.School.contains(lat: Double, lng: Double,
                                               matcher: SchoolMatcher,
                                               accuracyM: Double = 0.0): Boolean =
        with(matcher) { contains(lat, lng, accuracyM) }

    // ── Live school proximity ─────────────────────────────────────────────────

    private fun checkSchoolProximity(loc: Location, ts: Long) {
        if (schoolsList == null) {
            try { schoolsList = SchoolMatcher(context).loadSchools() } catch (_: Exception) { return }
        }
        val schools = schoolsList ?: return

        val prev   = prevFixLoc
        val prevTs = prevFixTs
        prevFixLoc = loc
        prevFixTs  = ts

        if (prev == null) return
        val gap = ts - prevTs
        // Skip accumulation for gaps too large to be a continuous session, but still
        // update prevFix so the NEXT call doesn't inherit a stale timestamp and skip again.
        if (gap <= 0 || gap > SCHOOL_GAP_MAX_MS) return

        val midLat = (prev.latitude  + loc.latitude)  / 2.0
        val midLng = (prev.longitude + loc.longitude) / 2.0
        // Use the worse of the two fixes so we don't over-extend on a bad reading.
        val midAcc = minOf(maxOf(prev.accuracy.toDouble(), loc.accuracy.toDouble()), 150.0)

        val matcher = SchoolMatcher(context)

        for ((school, track) in schoolTracks) {
            val insideNow = school.contains(loc.latitude, loc.longitude, matcher, loc.accuracy.toDouble().coerceAtMost(150.0))
            if (insideNow) {
                track.outsideCount = 0
                if (!track.insideRadius) {
                    track.insideRadius = true
                    track.loggedDepart = false
                    // New visit after confirmed departure — reset accumulation so
                    // the 35-min threshold can fire again for this return session.
                    track.totalMs  = 0L
                    track.firstTs  = ts
                    track.lastTs   = ts
                    track.logged10 = false
                    track.logged45 = false
                }
            } else {
                track.outsideCount++
                // Require 5 consecutive outside readings (~5 min) before confirming departure.
                // This prevents brief GPS drift inside a building from resetting the accumulator.
                if (track.outsideCount >= 5 && track.insideRadius) {
                    track.insideRadius = false
                    if (!track.loggedDepart) {
                        track.loggedDepart = true
                        val mins = (track.totalMs / 60_000L).toInt()
                        log("[SCHOOL] left: ${school.name} (was ~${mins} min)")
                        if (track.logged45) onTeachingEnded?.invoke(school, mins)
                    }
                }
            }
        }

        for (school in schools) {
            if (!school.contains(midLat, midLng, matcher, midAcc)) continue

            val track = schoolTracks.getOrPut(school) {
                SchoolTrack(firstTs = prevTs, lastTs = ts, totalMs = 0L)
            }
            if (!track.insideRadius) continue

            if (prevTs < track.firstTs) track.firstTs = prevTs
            if (ts     > track.lastTs)  track.lastTs  = ts
            track.totalMs += gap

            val mins = (track.totalMs / 60_000L).toInt()
            when {
                track.totalMs >= SCHOOL_TEACH_MS -> {
                    if (!track.logged45) {
                        track.logged45 = true
                        onTeachingDetected?.invoke(school, track.firstTs, mins)
                    }
                    log("[SCHOOL] ${school.name} (~${mins} min) ★ teaching")
                }
                track.totalMs >= SCHOOL_DETECT_MS -> log("[SCHOOL] ${school.name} (~${mins} min)")
                else                              -> log("[SCHOOL] nearby: ${school.name} (~${mins} min)")
            }
        }
    }
}
