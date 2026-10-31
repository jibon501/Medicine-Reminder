package com.example.medreminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

// হোম স্ক্রিনের widget: ঔষধের নোটিফিকেশনের সময় না হলে খালি ও স্বচ্ছ (লুকানো) থাকে,
// সময় হলে সেই বেলার ঔষধ আর "খেয়েছি" / "১৫ মিনিট পরে" বাটন নিয়ে ভেসে ওঠে
class MedWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, manager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: android.os.Bundle
    ) {
        render(context, manager, id)
    }

    companion object {
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, MedWidget::class.java))
                for (id in ids) render(ctx, mgr, id)
            } catch (e: Exception) {
                // widget হালনাগাদ ব্যর্থ হলেও রিমাইন্ডার চলবে
            }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_med)
            val key = Reminders.widgetDueSlot(ctx)
            val date = LocalDate.now().toString()
            val meds = if (key == null) emptyList() else Reminders.medsFor(Store.data, key, LocalDate.now())

            if (key == null || meds.isEmpty()) {
                rv.setViewVisibility(R.id.w_card, View.GONE)
            } else {
                rv.setViewVisibility(R.id.w_card, View.VISIBLE)
                rv.setTextViewText(R.id.w_title, "🔔 " + Slots.label(key))
                rv.setTextViewText(R.id.w_lines, Reminders.numberedLines(meds).joinToString("\n"))
                rv.setTextViewText(R.id.w_ok, Reminders.takenButtonLabel(meds))
                rv.setTextViewText(R.id.w_snooze, Reminders.snoozeLabel())

                val takeIntent = Intent(ctx, TakenReceiver::class.java)
                takeIntent.putExtra("key", key)
                takeIntent.putExtra("date", date)
                val takePi = PendingIntent.getBroadcast(ctx, ("WT" + key).hashCode(), takeIntent, FLAGS)
                rv.setOnClickPendingIntent(R.id.w_ok, takePi)

                rv.setOnClickPendingIntent(
                    R.id.w_snooze,
                    Reminders.snoozePendingIntent(ctx, key, date, "WZ")
                )

                val open = PendingIntent.getActivity(
                    ctx, 1, Intent(ctx, MainActivity::class.java), FLAGS
                )
                rv.setOnClickPendingIntent(R.id.w_title, open)
                rv.setOnClickPendingIntent(R.id.w_lines, open)
            }
            mgr.updateAppWidget(id, rv)
        }
    }
}
