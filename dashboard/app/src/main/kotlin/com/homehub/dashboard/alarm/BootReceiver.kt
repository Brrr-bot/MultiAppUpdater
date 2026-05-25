package com.homehub.dashboard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.homehub.dashboard.HomeHubApp
import com.homehub.dashboard.service.KeepAliveService
import com.homehub.dashboard.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            context.startForegroundService(Intent(context, KeepAliveService::class.java))
            val app = context.applicationContext as HomeHubApp
            CoroutineScope(Dispatchers.IO).launch {
                app.alarmScheduler.rescheduleAll()
            }
        }
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}
