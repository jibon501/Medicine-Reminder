package com.example.medreminder

import java.time.LocalDate
import java.util.Locale

fun bn(s: Any): String {
    val digits = "০১২৩৪৫৬৭৮৯"
    val sb = StringBuilder()
    for (c in s.toString()) {
        if (c in '0'..'9') sb.append(digits[c - '0']) else sb.append(c)
    }
    return sb.toString()
}

fun hhmm(h: Int, m: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", h, m)

fun toMin(t: String): Int {
    val p = t.split(":")
    return p[0].toInt() * 60 + p[1].toInt()
}

fun pretty(t: String): String {
    val p = t.split(":")
    val h = p[0].toInt()
    val m = p[1].toInt()
    val period = when (h) {
        in 4..5 -> "ভোর"
        in 6..11 -> "সকাল"
        in 12..15 -> "দুপুর"
        in 16..17 -> "বিকাল"
        in 18..19 -> "সন্ধ্যা"
        else -> "রাত"
    }
    val h12 = if (h % 12 == 0) 12 else h % 12
    return bn(period + " " + String.format(Locale.ENGLISH, "%d:%02d", h12, m))
}

val BN_MONTHS = listOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
)

fun prettyDate(iso: String): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return bn("${d.dayOfMonth} ${BN_MONTHS[d.monthValue - 1]} ${d.year}")
}

object Slots {
    val meals = listOf("MORNING", "NOON", "NIGHT")

    fun mealLabel(m: String): String = when (m) {
        "MORNING" -> "সকাল"
        "NOON" -> "দুপুর"
        else -> "রাত"
    }

    fun mealTime(d: AppData, meal: String): String = when (meal) {
        "MORNING" -> d.morning
        "NOON" -> d.noon
        else -> d.night
    }

    fun isBefore(key: String) = key.endsWith("_BEFORE")

    fun isAfter(key: String) = key.endsWith("_AFTER")

    fun mealOf(key: String): String = key.removeSuffix("_BEFORE").removeSuffix("_AFTER")

    fun label(key: String): String = when {
        key.endsWith("_BEFORE") -> mealLabel(key.removeSuffix("_BEFORE")) + " · খাবারের আগে"
        else -> mealLabel(key.removeSuffix("_AFTER")) + " · খাবারের পরে"
    }
}
