package com.example.aiteachingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        android.util.Log.d("UpdateCheck", "InstallReceiver status=$status")
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                android.util.Log.d("UpdateCheck", "Install SUCCESS")
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val userIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (userIntent != null) {
                    userIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(userIntent) } catch (e: Exception) {
                        Toast.makeText(context, "Could not open install dialog: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                android.util.Log.e("UpdateCheck", "Install FAILED code=$status msg=$msg")
                Toast.makeText(context, "Install failed (code $status): $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
