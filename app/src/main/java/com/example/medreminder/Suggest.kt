package com.example.medreminder

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream

// ঔষধের নামের সাজেশন।
// ১) ইন্টারনেট থাকলে একবার NLM RxNorm থেকে ঔষধের নামের তালিকা নামিয়ে ফোনে রাখা হয় (প্রতি ৩০ দিনে নতুন করে)।
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

    // ---------- Excel (xlsx/csv) তালিকা: প্রথম কলাম = ঔষধের নাম, দ্বিতীয় কলাম = পাওয়ার ----------
    @Volatile
    private var excel: List<String> = emptyList()

    @Volatile
    private var excelLower: List<String> = emptyList()

    val excelCount: Int get() = excel.size

    private fun excelFile(ctx: Context) = File(ctx.filesDir, "drug_list.txt")

    private fun setExcel(list: List<String>) {
        excel = list
        excelLower = list.map { it.lowercase() }
    }

    // ফোনে রাখা তালিকা, না থাকলে অ্যাপের ভেতরের assets/medicines.xlsx
    fun loadExcel(ctx: Context) {
        if (excel.isNotEmpty()) return
        try {
            val f = excelFile(ctx)
            if (f.exists()) {
                setExcel(f.readLines().filter { it.isNotBlank() })
                return
            }
            val names = ctx.assets.open("medicines.xlsx").use { parseXlsx(it.readBytes()) }
            if (names.isNotEmpty()) setExcel(names)
        } catch (e: Exception) {
            // কোনো ফাইল নেই
        }
    }

    // ইউজারের বাছাই করা ফাইল ইমপোর্ট। সফল হলে কয়টা নাম পাওয়া গেল সেটা, না হলে -1
    fun importExcel(ctx: Context, uri: Uri): Int {
        return try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return -1
            val isZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
            val names = if (isZip) parseXlsx(bytes) else parseCsv(String(bytes, Charsets.UTF_8))
            if (names.isEmpty()) return -1
            excelFile(ctx).writeText(names.joinToString("\n"))
            setExcel(names)
            names.size
        } catch (e: Exception) {
            -1
        }
    }

    private fun combine(name: String, power: String): String {
        val n = name.trim()
        val p = power.trim()
        return if (p.isEmpty()) n else "$n $p"
    }

    private val headerNames = setOf("name", "medicine", "medicine name", "drug", "drug name", "ওষুধ", "ওষুধের নাম", "ঔষধ", "ঔষধের নাম", "নাম")
    private val headerPower = setOf("power", "strength", "পাওয়ার")

    private fun rowsToNames(rows: List<Pair<String, String>>): List<String> {
        var list = rows
        if (list.isNotEmpty()) {
            val (a, b) = list[0]
            if (a.trim().lowercase() in headerNames || b.trim().lowercase() in headerPower) list = list.drop(1)
        }
        return list.map { combine(it.first, it.second) }.filter { it.isNotEmpty() && it.length <= 80 }.distinct()
    }

    private fun parseCsv(text: String): List<String> {
        val rows = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }.map { line ->
            val cells = line.split(',', '\t', ';').map { it.trim().trim('"') }
            Pair(cells.getOrElse(0) { "" }, cells.getOrElse(1) { "" })
        }
        return rowsToNames(rows)
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else break
        }
        return n - 1
    }

    private fun parseXlsx(bytes: ByteArray): List<String> {
        var shared: ByteArray? = null
        val sheets = sortedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                when {
                    e.name == "xl/sharedStrings.xml" -> shared = z.readBytes()
                    e.name.startsWith("xl/worksheets/") && e.name.endsWith(".xml") -> sheets[e.name] = z.readBytes()
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        val sheet = sheets.values.firstOrNull() ?: return emptyList()

        // shared strings
        val strings = ArrayList<String>()
        shared?.let { data ->
            val p = Xml.newPullParser()
            p.setInput(data.inputStream(), "UTF-8")
            var inSi = false
            var inT = false
            val sb = StringBuilder()
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "si" -> { inSi = true; sb.setLength(0) }
                        "t" -> inT = true
                    }
                    XmlPullParser.TEXT -> if (inSi && inT) sb.append(p.text)
                    XmlPullParser.END_TAG -> when (p.name) {
                        "t" -> inT = false
                        "si" -> { inSi = false; strings.add(sb.toString()) }
                    }
                }
                ev = p.next()
            }
        }

        // sheet: row -> (col A, col B)
        val rows = ArrayList<Pair<String, String>>()
        val p = Xml.newPullParser()
        p.setInput(sheet.inputStream(), "UTF-8")
        var a = ""
        var b = ""
        var col = -1
        var type = ""
        var inV = false
        var inT = false
        val cell = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> { a = ""; b = "" }
                    "c" -> {
                        col = colIndex(p.getAttributeValue(null, "r") ?: "")
                        type = p.getAttributeValue(null, "t") ?: ""
                        cell.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inV || inT) cell.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        var value = cell.toString()
                        if (type == "s") value = strings.getOrElse(value.trim().toIntOrNull() ?: -1) { "" }
                        else if (type != "inlineStr" && type != "str" && value.endsWith(".0")) value = value.removeSuffix(".0")
                        if (col == 0) a = value else if (col == 1) b = value
                    }
                    "row" -> if (a.isNotBlank()) rows.add(Pair(a, b))
                }
            }
            ev = p.next()
        }
        return rowsToNames(rows)
    }

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
        // Excel তালিকা থাকলে শুধু সেটা (আর আগে দেওয়া নাম) থেকে সাজেশন
        if (excel.isNotEmpty()) {
            val ex = excel
            val exl = excelLower
            for (i in exl.indices) {
                if (result.size >= 8) break
                if (exl[i].startsWith(s)) add(ex[i])
            }
            mine.filter { it.lowercase().startsWith(s) }.forEach { add(it) }
            for (i in exl.indices) {
                if (result.size >= 8) break
                if (exl[i].contains(s)) add(ex[i])
            }
            return result.values.filter { it.lowercase() != s }.take(8)
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
