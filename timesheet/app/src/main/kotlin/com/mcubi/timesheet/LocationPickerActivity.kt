package com.mcubi.timesheet

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class LocationPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_INITIAL_LAT = "initial_lat"
        const val EXTRA_INITIAL_LNG = "initial_lng"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled   = true
            domStorageEnabled   = true
            userAgentString     = "TimesheetApp/1.0 AndroidWebView"
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onLocationPicked(lat: Double, lng: Double) {
                runOnUiThread {
                    setResult(RESULT_OK, Intent().apply {
                        putExtra(EXTRA_LAT, lat)
                        putExtra(EXTRA_LNG, lng)
                    })
                    finish()
                }
            }

            @JavascriptInterface
            fun showToast(msg: String) {
                runOnUiThread { Toast.makeText(this@LocationPickerActivity, msg, Toast.LENGTH_SHORT).show() }
            }
        }, "Android")

        val html = assets.open("map_picker.html").bufferedReader().readText()
        // Use https://localhost as base URL so that CDN and Nominatim requests are not CORS-blocked
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // If an initial location was passed in (e.g. from "Use Current GPS" pre-fill), use it
                val initLat = intent.getDoubleExtra(EXTRA_INITIAL_LAT, Double.NaN)
                val initLng = intent.getDoubleExtra(EXTRA_INITIAL_LNG, Double.NaN)
                if (!initLat.isNaN() && !initLng.isNaN()) {
                    view.evaluateJavascript("setInitialLocation($initLat, $initLng)", null)
                    return
                }
                // Otherwise centre on last known GPS (no pin placed yet)
                trySetGpsPosition(view, placePin = false)
            }
        }
    }

    private fun trySetGpsPosition(view: WebView, placePin: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return
        val lm  = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: return
        val fn = if (placePin) "setInitialLocation" else "centreMap"
        view.evaluateJavascript("$fn(${loc.latitude}, ${loc.longitude})", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
