package com.example.medreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.ensureLoaded(context)
        val key = intent.getStringExtra("key") ?: return
        val nag = intent.getIntExtra("nag", 0)
        var date = intent.getStringExtra("date") ?: ""
        if (date.isEmpty()) date = LocalDate.now().toString()
        if (nag == 0) Reminders.scheduleAll(context)
        Reminders.fire(context, key, date, nag)
        // সময় হলে widget দেখায়, নইলে লুকায়
        MedWidget.updateAll(context)
    }
}

// নোটিফিকেশন বা widget-এর "১৫ মিনিট পরে" বাটন
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.ensureLoaded(context)
        val key = intent.getStringExtra("key") ?: return
        val date = intent.getStringExtra("date") ?: return
        Reminders.snooze(context, key, date)
    }
}

class TakenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.ensureLoaded(context)
        val key = intent.getStringExtra("key") ?: return
        val date = intent.getStringExtra("date") ?: return
        Reminders.markTaken(context, key, date)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.scheduleAll(context)
        Reminders.resumeSnoozes(context)
    }
}
