package com.example.medreminder

import android.content.Context
import java.time.Instant
import java.time.ZoneId

// ইতিহাস এডিট: মুছে ফেলা, তারিখ/সময় বদলানো, বাদ পড়া ওষুধ নিজে যোগ করা
object HistoryEdit {
    private fun dayOf(e: LogEntry): String =
        Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    // ওই দিনের ওই স্লটের আর কোনো রেকর্ড না থাকলে "আজ" ট্যাবেও সেটা খাওয়া হয়নি ধরা হবে
    private fun syncTaken(s: AppData, dateStr: String, key: String): AppData {
        val stillThere = s.log.any { it.slotKey == key && dayOf(it) == dateStr }
        return if (stillThere) s else s.copy(taken = s.taken - "$dateStr|$key")
    }

    fun delete(ctx: Context, e: LogEntry) {
        Store.update(ctx) { s ->
            syncTaken(s.copy(log = s.log.filterNot { it.id == e.id }), dayOf(e), e.slotKey)
        }
    }

    // old == null হলে নতুন যোগ, নইলে old এর জায়গায় e বসে
    fun upsert(ctx: Context, old: LogEntry?, e: LogEntry) {
        Store.update(ctx) { s ->
            if (old == null) {
                s.copy(log = s.log + e)
            } else {
                syncTaken(
                    s.copy(log = s.log.map { if (it.id == old.id) e else it }),
                    dayOf(old),
                    old.slotKey
                )
            }
        }
    }
}
