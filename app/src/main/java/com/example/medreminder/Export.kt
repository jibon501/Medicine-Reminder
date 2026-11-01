package com.example.medreminder

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

// তালিকার একটা লাইন: ঔষধ, কত দিনের, মোট কয়টা, আর বাদ দেওয়া হয়েছে কিনা
data class PurchaseItem(val med: Medicine, val days: Int, val qty: Int, val excluded: Boolean) {
    fun right(): String {
        val parts = ArrayList<String>()
        if (days > 0) parts.add(bn(days) + " দিনের কোর্স")
        if (qty > 0) parts.add(bn(qty) + " " + Forms.of(med.form).unit)
        return parts.joinToString("\n")
    }

    fun detail(): String = buildString {
        if (med.form != "TABLET") append(Forms.of(med.form).label).append(" · ")
        append("প্রতিবারে ").append(med.doseLabel())
        if (med.intervalDays > 1) append(" · ").append(bn(med.intervalDays)).append(" দিন পর পর")
        append("\n").append(med.timings.sortedBy { Reminders.slotOrder.indexOf(it) }.joinToString(", ") { Slots.label(it) })
    }
}

// কোনো ঔষধের জন্য নিজের দেওয়া দিন / পরিমাণ, বা বাদ দেওয়া
data class PurchaseOverride(val days: Int? = null, val qty: Int? = null, val excluded: Boolean = false)

// ঔষধ কেনার তালিকা: একটা রোগের সব ঔষধ, কোনটা মোট কয়টা লাগবে
object PurchaseList {
    private const val W = 1080
    private const val PAD = 48

    private fun paint(size: Float, bold: Boolean, color: Int): TextPaint {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.color = color
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return p
    }

    private fun layout(text: String, p: TextPaint, width: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(align)
            .setLineSpacing(0f, 1.1f)
            .build()

    fun medsOf(d: AppData, disease: Disease): List<Medicine> =
        d.medicines.filter { it.diseaseId == disease.id }

    fun build(disease: Disease, items: List<PurchaseItem>, days: Int = 0): Bitmap {
        val black = Color.parseColor("#1B1B1B")
        val grey = Color.parseColor("#666666")
        val teal = Color.parseColor("#00796B")

        val titleP = paint(58f, true, teal)
        val subP = paint(36f, false, grey)
        val subBoldP = paint(36f, true, black)
        val nameP = paint(44f, true, black)
        val detailP = paint(32f, false, grey)
        val totalP = paint(46f, true, teal)
        val footP = paint(30f, false, grey)

        val inner = W - PAD * 2
        val rightW = 300
        val leftW = inner - rightW - 24

        data class Row(val left: StaticLayout, val detail: StaticLayout, val right: StaticLayout, val h: Int)

        fun strike(p: TextPaint): TextPaint {
            val q = TextPaint(p)
            q.isStrikeThruText = true
            q.color = Color.parseColor("#9E9E9E")
            return q
        }
        val nameS = strike(nameP)
        val detailS = strike(detailP)
        val totalS = strike(totalP)

        // বাদ দেওয়া ঔষধও দেখায়, কিন্তু পুরো লাইন (নামসহ) কাটা অবস্থায়
        val rows = items.mapIndexed { i, pi ->
            val l = layout(bn(i + 1) + ". " + pi.med.name, if (pi.excluded) nameS else nameP, leftW)
            val dl = layout(pi.detail(), if (pi.excluded) detailS else detailP, leftW)
            val rl = layout(pi.right(), if (pi.excluded) totalS else totalP, rightW, Layout.Alignment.ALIGN_OPPOSITE)
            Row(l, dl, rl, maxOf(l.height + 8 + dl.height, rl.height) + 36)
        }

        val head = mutableListOf<StaticLayout>()
        head.add(layout("🛒 ঔষধ কেনার তালিকা", titleP, inner))
        head.add(layout("রোগ: " + disease.name, subBoldP, inner))
        if (days > 0) head.add(layout(bn(days) + " দিনের জন্য কিনতে হবে", subBoldP, inner))

        val foot = layout(
            "মোট " + bn(items.count { !it.excluded }) + " রকম · তালিকা তৈরি: " + prettyDate(LocalDate.now().toString()),
            footP, inner
        )

        var h = PAD
        head.forEach { h += it.height + 8 }
        h += 24 + 4 + 12
        rows.forEach { h += it.h + 2 }
        if (rows.isEmpty()) h += 80
        h += 24 + foot.height + PAD

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val line = Paint().also { it.color = Color.parseColor("#CCCCCC"); it.strokeWidth = 2f }
        val thick = Paint().also { it.color = teal; it.strokeWidth = 4f }

        var y = PAD.toFloat()
        head.forEach {
            c.save(); c.translate(PAD.toFloat(), y); it.draw(c); c.restore()
            y += it.height + 8
        }
        y += 24
        c.drawLine(PAD.toFloat(), y, (W - PAD).toFloat(), y, thick)
        y += 4 + 12

        if (rows.isEmpty()) {
            val e = layout("এই রোগে এখনও কোনো ঔষধ যোগ করা হয়নি", detailP, inner)
            c.save(); c.translate(PAD.toFloat(), y + 20); e.draw(c); c.restore()
            y += 80
        }
        rows.forEach { r ->
            val top = y + 18
            c.save(); c.translate(PAD.toFloat(), top); r.left.draw(c); c.restore()
            c.save(); c.translate(PAD.toFloat(), top + r.left.height + 8); r.detail.draw(c); c.restore()
            c.save(); c.translate((PAD + leftW + 24).toFloat(), top); r.right.draw(c); c.restore()
            y += r.h + 2
            c.drawLine(PAD.toFloat(), y - 1, (W - PAD).toFloat(), y - 1, line)
        }
        y += 24
        c.save(); c.translate(PAD.toFloat(), y); foot.draw(c); c.restore()
        return bmp
    }

    private fun fileName(disease: Disease): String {
        val safe = disease.name.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifBlank { "list" }
        return "ঔষধ-তালিকা-$safe-${LocalDate.now()}.png"
    }

    // শেয়ার (হোয়াটসঅ্যাপ, ড্রাইভ, গ্যালারি ইত্যাদি)
    fun share(ctx: Context, bmp: Bitmap, disease: Disease) {
        try {
            val dir = File(ctx.cacheDir, "shared")
            dir.mkdirs()
            val f = File(dir, "purchase-list.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val i = Intent(Intent.ACTION_SEND)
            i.type = "image/png"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(i, "কেনার তালিকা শেয়ার করুন")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            toast(ctx, "শেয়ার করা যায়নি")
        }
    }

    // গ্যালারিতে (Pictures/ঔষধের তালিকা) সেভ। Android 10+ এ
    fun canSaveToGallery(): Boolean = Build.VERSION.SDK_INT >= 29

    fun saveToGallery(ctx: Context, bmp: Bitmap, disease: Disease): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val cv = ContentValues()
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, fileName(disease))
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MedicineReminder")
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
