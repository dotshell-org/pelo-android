package com.pelotcl.app.generic.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pelotcl.app.generic.widget.schedule.WidgetRefreshScheduler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            MainScope().launch {
                WidgetRefreshScheduler.rescheduleAll(context)
                pendingResult.finish()
            }
        }
    }
}
