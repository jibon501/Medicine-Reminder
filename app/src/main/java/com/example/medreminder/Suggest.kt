package com.example.medreminder

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

// ওষুধের নামের সাজেশন।
// ১) ইন্টারনেট থাকলে একবার NLM RxNorm থেকে ওষুধের নামের তালিকা নামিয়ে ফোনে রাখা হয় (প্রতি ৩০ দিনে নতুন করে)।
// ২) তালিকা ফোনেই খোঁজা হয়, তাই আপনার টাইপ করা কিছুই অনলাইনে যায় না।
// ৩) এর সাথে বাংলাদেশের পরিচিত নাম ও আপনার আগে দেওয়া নামও দেখানো হয়।
object Suggest {
    private const val URL_NAMES = "https://rxnav.nlm.nih.gov/REST/displaynames.json"
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    private val common = listOf(
        "Napa", "Napa Extra", "Ace", "Ace Plus", "Seclo", "Maxpro", "Losectil", "Sergel", "Nexum",
        "Paracetamol", "Omeprazole", "Esomeprazole", "Pantoprazole", "Rabeprazole", "Domperidone",
        "Ranitidine", "Metronidazole", "Ciprofloxacin", "Azithromycin", "Amoxicillin", "Cefixime",
        "Cefuroxime", "Cetirizine", "Levocetirizine", "Fexofenadine", "Montelukast", "Salbutamol",
        "Metformin", "Glimepiride", "Gliclazide", "Sitagliptin", "Insulin", "Amlodipine", "Losartan",
        "Telmisartan", "Atenolol", "Bisoprolol", "Atorvastatin", "Rosuvastatin", "Clopidogrel",
        "Aspirin", "Levothyroxine", "Calcium", "Vitamin D3", "Vitamin B Complex", "Iron", "Folic Acid",
        "Diclofenac", "Ibuprofen", "Naproxen", "Prednisolone", "Furosemide", "Tamsulosin",
        "Pregabalin", "Gabapentin", "Zinc", "ORS"
    )

    @Volatile
    private var online: List<String> = emptyList()

    @Volatile
    private var onlineLower: List<String> = emptyList()

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "drug_names.txt")

    val onlineCount: Int get() = online.size

    // ক্যাশ ফাইল থেকে মেমরিতে আনে
    fun loadCache(ctx: Context) {
        if (online.isNotEmpty()) return
        val f = cacheFile(ctx)
        if (!f.exists()) return
        try {
            val list = f.readLines().filter { it.isNotBlank() }
            online = list
            onlineLower = list.map { it.lowercase() }
        } catch (e: Exception) {
            // ignore
        }
    }

    // দরকার হলে ইন্টারনেট থেকে নামের তালিকা নামায়। সফল হলে true
    fun refreshIfNeeded(ctx: Context): Boolean {
        val f = cacheFile(ctx)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS
        if (fresh) return false
        return try {
            val c = URL(URL_NAMES).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 30000
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONObject("displayTermsList").getJSONArray("term")
            val names = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val n = arr.getString(i).trim()
                if (n.isNotEmpty() && n.length <= 60) names.add(n)
            }
            if (names.isEmpty()) return false
            val tmp = File(ctx.filesDir, "drug_names.tmp")
            tmp.writeText(names.joinToString("\n"))
            tmp.renameTo(f)
            online = names
            onlineLower = names.map { it.lowercase() }
            true
        } catch (e: Exception) {
            false
        }
    }

    // টাইপ করা অংশ দিয়ে শুরু হওয়া নাম আগে, তারপর যেগুলোর ভেতরে আছে
    fun query(q: String, mine: List<String>): List<String> {
        val s = q.trim().lowercase()
        if (s.length < 2) return emptyList()
        val result = LinkedHashMap<String, String>()
        fun add(name: String) {
            val k = name.lowercase()
            if (!result.containsKey(k)) result[k] = name
        }
        val local = mine + common
        local.filter { it.lowercase().startsWith(s) }.forEach { add(it) }
        val snapshot = online
        val lower = onlineLower
        for (i in lower.indices) {
            if (result.size >= 8) break
            if (lower[i].startsWith(s)) add(snapshot[i])
        }
        if (result.size < 8) local.filter { it.lowercase().contains(s) }.forEach { add(it) }
        if (result.size < 8) {
            for (i in lower.indices) {
                if (result.size >= 8) break
                if (lower[i].contains(s)) add(snapshot[i])
            }
        }
        // যা টাইপ করা হয়েছে হুবহু তাই হলে সাজেশন দেখানোর দরকার নেই
        return result.values.filter { it.lowercase() != s }.take(8)
    }
}
