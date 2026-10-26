@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.medreminder

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// ------------------------------------------------------------------ small pieces

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it in '0'..'9' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun Thumb(name: String, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = name) {
        value = withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options()
                o.inSampleSize = 8
                BitmapFactory.decodeFile(File(imageDir(ctx), name).absolutePath, o)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(100.dp).clickable { onClick() }
        )
    } else {
        Box(Modifier.size(100.dp))
    }
}

// ------------------------------------------------------------------ today

private val MEAL_EMOJI = mapOf("MORNING" to "🌅", "NOON" to "☀️", "NIGHT" to "🌙")

@Composable
fun TodayScreen() {
    val d = Store.data
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = LocalDateTime.now()
        }
    }
    val today = now.toLocalDate()
    val todayStr = today.toString()
    fun visible(key: String) =
        d.taken.contains("$todayStr|$key") || Reminders.medsDue(d, key, today).isNotEmpty()
    val meals = Slots.meals.filter { visible(it + "_BEFORE") || visible(it + "_AFTER") }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("আজকের ওষুধ", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(prettyDate(todayStr), style = MaterialTheme.typography.bodyMedium)
        }
        if (meals.isEmpty()) {
            Text("আজকের জন্য কোনো ওষুধ নেই। \"ওষুধ\" ট্যাবে গিয়ে ওষুধ যোগ করুন।")
        }
        meals.forEach { meal ->
            val kb = meal + "_BEFORE"
            val ka = meal + "_AFTER"
            val nb = Reminders.medsFor(d, kb, today).size
            val na = Reminders.medsFor(d, ka, today).size
            val most = maxOf(nb, na)
            val fs = if (most > 4) 11.sp else if (most > 2) 12.sp else 14.sp
            Card(modifier = Modifier.fillMaxWidth().weight((most + 3).toFloat())) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        (MEAL_EMOJI[meal] ?: "") + " " + Slots.mealLabel(meal) + " · " + pretty(Slots.mealTime(d, meal)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Row(Modifier.fillMaxSize().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SlotPane(kb, "খাবারের আগে", visible(kb), d, today, now, fs, Modifier.weight(1f).fillMaxSize())
                        SlotPane(ka, "খাবারের পরে", visible(ka), d, today, now, fs, Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotPane(
    key: String,
    title: String,
    show: Boolean,
    d: AppData,
    today: LocalDate,
    now: LocalDateTime,
    fs: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier
) {
    val ctx = LocalContext.current
    val todayStr = today.toString()
    Column(modifier) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = fs, color = MaterialTheme.colorScheme.primary)
        if (!show) {
            Text("— নেই —", fontSize = fs, color = Color.Gray)
            return@Column
        }
        val done = d.taken.contains("$todayStr|$key")
        val meds = Reminders.medsFor(d, key, today)
        val blocked = Reminders.afterBlocked(d, key, todayStr)

        // ওষুধ বেশি হলে শুধু এই অংশটা স্ক্রল হবে, বাটন নিচে স্থির থাকবে
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Reminders.numberedLines(meds).forEach { Text(it, fontSize = fs, lineHeight = fs * 1.2f) }
        }
        if (blocked && !done) Text("আগের ওষুধ আগে নিন", fontSize = 10.sp, color = Color.Gray)
        // একটাই বাটন: না খেলে "খেয়েছি", খেলে "খাওয়া হয়েছে · খাইনি" (চাপলে আবার না-খাওয়া হয়ে যায়)
        if (done) {
            OutlinedButton(
                onClick = { Reminders.unmarkTaken(ctx, key, todayStr) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Reminders.doneTitle(meds), fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text(Reminders.undoTitle(meds), fontSize = 11.sp)
                }
            }
        } else {
            Button(
                enabled = !blocked,
                onClick = {
                    if (!Reminders.markTaken(ctx, key, todayStr)) toast(ctx, "আগের ওষুধ আগে নিন")
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { Text(Reminders.takenButtonLabel(meds), fontSize = 13.sp) }
        }
    }
}

// ------------------------------------------------------------------ medicines

@Composable
fun MedsScreen(onNew: () -> Unit, onEdit: (Medicine) -> Unit) {
    val ctx = LocalContext.current
    val d = Store.data
    // কোন কোন ওষুধের ডিটেলস খোলা আছে (বাকিগুলো মিনিমাইজ)
    var open by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (d.medicines.isEmpty()) {
                item { Text("এখনও কোনো ওষুধ নেই।", modifier = Modifier.padding(top = 8.dp)) }
            }
            items(d.medicines, key = { it.id }) { m ->
                val disease = d.diseases.firstOrNull { it.id == m.diseaseId }
                val f = Forms.of(m.form)
                val isOpen = open.contains(m.id)
                Card(
                    onClick = { open = if (isOpen) open - m.id else open + m.id },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                f.emoji + " " + m.name + (if (m.muted) "  🔕" else ""),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                m.doseLabel() + " · " + bn(m.timings.size) + " বার",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(if (isOpen) "  ▴" else "  ▾", style = MaterialTheme.typography.bodySmall)
                        }
                        if (isOpen) {
                            if (m.muted) Text("🔕 সাইলেন্ট: এর জন্য কোনো নোটিফিকেশন আসবে না", color = Color(0xFFC62828))
                            Text("শুরু: " + prettyDate(m.startDate))
                            Text("প্রতিবারে: " + m.doseLabel() + " · দিনে " + bn(m.timings.size) + " বার")
                            if (m.intervalDays > 1) {
                                Text("প্রতি " + bn(m.intervalDays) + " দিন পর পর")
                            }
                            Text(m.timings.joinToString("\n") { "• " + Slots.label(it) })
                            if (disease != null) Text("রোগ: " + disease.name)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (m.muted) "🔕" else "🔔")
                                Switch(
                                    checked = !m.muted,
                                    onCheckedChange = { on ->
                                        Store.update(ctx) { s ->
                                            s.copy(medicines = s.medicines.map { if (it.id == m.id) it.copy(muted = !on) else it })
                                        }
                                        Reminders.scheduleAll(ctx)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                Box(Modifier.weight(1f))
                                OutlinedButton(onClick = { onEdit(m) }) { Text("✏️ সম্পাদনা") }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) { Text("+ নতুন ওষুধ যোগ করুন") }
    }
}

@Composable
fun MedicineEditor(init: Medicine, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val d = Store.data
    val isNew = d.medicines.none { it.id == init.id }

    var name by remember { mutableStateOf(init.name) }
    var form by remember { mutableStateOf(init.form) }
    var dose by remember { mutableStateOf(init.dose.toString()) }
    var diseaseId by remember { mutableStateOf(init.diseaseId) }
    var timings by remember { mutableStateOf(init.timings) }
    var start by remember { mutableStateOf(init.startDate) }
    var interval by remember { mutableStateOf(init.intervalDays.toString()) }
    var muted by remember { mutableStateOf(init.muted) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun toggle(key: String) {
        timings = if (timings.contains(key)) timings - key else timings + key
    }

    val f = Forms.of(form)
    val preview = init.copy(
        dose = (dose.toIntOrNull() ?: 1).coerceAtLeast(1),
        timings = timings,
        courseDays = 0,
        intervalDays = (interval.toIntOrNull() ?: 1).coerceAtLeast(1)
    )

    fun save() {
        if (name.isBlank()) {
            toast(ctx, "ওষুধের নাম দিন")
            return
        }
        if (timings.isEmpty()) {
            toast(ctx, "কখন নেবেন তা অন্তত একটি বাছাই করুন")
            return
        }
        val m = preview.copy(
            name = name.trim(),
            form = form,
            diseaseId = diseaseId,
            startDate = start,
            muted = muted
        )
        Store.update(ctx) { s ->
            s.copy(
                medicines = if (isNew) s.medicines + m
                else s.medicines.map { if (it.id == m.id) m else it }
            )
        }
        Reminders.scheduleAll(ctx)
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (isNew) "নতুন ওষুধ" else "ওষুধ সম্পাদনা",
            style = MaterialTheme.typography.headlineSmall
        )
        var suggestions by remember { mutableStateOf(emptyList<String>()) }
        var skipNext by remember { mutableStateOf(false) }
        var listVersion by remember { mutableStateOf(0) }
        val mine = d.medicines.map { it.name }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) { Suggest.loadExcel(ctx); Suggest.loadCache(ctx) }
            listVersion++
            val updated = if (Suggest.excelCount > 0) false
            else withContext(Dispatchers.IO) { Suggest.refreshIfNeeded(ctx) }
            if (updated) listVersion++
        }
        LaunchedEffect(name, listVersion) {
            if (skipNext) {
                skipNext = false
                suggestions = emptyList()
                return@LaunchedEffect
            }
            delay(200)
            suggestions = withContext(Dispatchers.Default) { Suggest.query(name, mine) }
        }

        Text("কী ধরনের?", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Forms.all.forEach { fm ->
                FilterChip(
                    selected = form == fm.id,
                    onClick = { form = fm.id },
                    label = { Text(fm.emoji + " " + fm.label) }
                )
            }
        }

        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("রোগ: " + (d.diseases.firstOrNull { it.id == diseaseId }?.name ?: "বাছাই করুন (ঐচ্ছিক)"))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("কোনো রোগ নয়") },
                    onClick = { diseaseId = ""; menu = false }
                )
                d.diseases.forEach { ds ->
                    DropdownMenuItem(
                        text = { Text(ds.name) },
                        onClick = { diseaseId = ds.id; menu = false }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { pickDate(ctx, start) { start = it } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("শুরুর তারিখ: " + prettyDate(start)) }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("নাম ও পাওয়ার (টাইপ করলে সাজেশন আসবে)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (suggestions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    suggestions.forEach { s ->
                        Text(
                            s,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    skipNext = true
                                    name = s
                                    suggestions = emptyList()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
        NumField("প্রতিবারে কত " + f.unit + "?", dose) { dose = it }

        Text("কখন? (খাবারের আগে / পরে)", style = MaterialTheme.typography.titleMedium)
        for (meal in Slots.meals) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.width(96.dp)) {
                    Text(Slots.mealLabel(meal))
                    Text(pretty(Slots.mealTime(d, meal)), style = MaterialTheme.typography.bodySmall)
                }
                val kb = meal + "_BEFORE"
                val ka = meal + "_AFTER"
                Checkbox(checked = timings.contains(kb), onCheckedChange = { toggle(kb) })
                Text("আগে", modifier = Modifier.padding(end = 16.dp))
                Checkbox(checked = timings.contains(ka), onCheckedChange = { toggle(ka) })
                Text("পরে")
            }
        }

        NumField("কত দিন পর পর? (১ = প্রতিদিন, ২ = একদিন পর পর, ৩ = দুই দিন পর পর)", interval) { interval = it }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔕 সাইলেন্ট (নোটিফিকেশন লাগবে না)", modifier = Modifier.weight(1f))
            Switch(checked = muted, onCheckedChange = { muted = it })
        }

        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("সংরক্ষণ করুন") }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("বাতিল") }
        if (!isNew) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("🗑 মুছে ফেলুন", color = Color(0xFFC62828))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("মুছে ফেলবেন?") },
            text = { Text(init.name + " তালিকা থেকে মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    Store.update(ctx) { s -> s.copy(medicines = s.medicines.filter { it.id != init.id }) }
                    Reminders.scheduleAll(ctx)
                    confirmDelete = false
                    onClose()
                }) { Text("মুছুন") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("না") } }
        )
    }
}

// ------------------------------------------------------------------ diseases / prescriptions

@Composable
fun PurchaseListDialog(disease: Disease, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val meds = PurchaseList.medsOf(Store.data, disease)
    // কয় দিনের ওষুধ কিনতে চান। ফাঁকা থাকলে শুধু নাম, সময় ও মাত্রা দেখাবে
    var daysText by remember { mutableStateOf("") }
    val days = daysText.toIntOrNull() ?: 0
    val bmp = remember(disease.id, Store.data.medicines, days) { PurchaseList.build(disease, meds, days) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛒 কেনার তালিকা") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumField("কত দিনের ওষুধ কিনতে চান?", daysText) { daysText = it.take(3) }
                if (days <= 0) Text("আগে দিন সংখ্যা দিন, তারপর সেভ ও শেয়ার চালু হবে", style = MaterialTheme.typography.bodySmall)
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "কেনার তালিকা",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
                if (PurchaseList.canSaveToGallery()) {
                    OutlinedButton(
                        enabled = days > 0,
                        onClick = {
                            if (PurchaseList.saveToGallery(ctx, bmp, disease)) toast(ctx, "গ্যালারিতে সেভ হয়েছে")
                            else toast(ctx, "সেভ করা যায়নি")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🖼 গ্যালারিতে সেভ করুন") }
                }
            }
        },
        confirmButton = { TextButton(enabled = days > 0, onClick = { PurchaseList.share(ctx, bmp, disease) }) { Text("📤 শেয়ার") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বন্ধ") } }
    )
}

// রোগ এক্সপোর্ট: ফাইলের জায়গা বাছাই করে ওই রোগ + তার সব ওষুধের ডিটেলস zip করে সেভ করে
@Composable
fun rememberDiseaseExporter(): (Disease) -> Unit {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf<Disease?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val ds = target
        if (uri != null && ds != null) {
            if (exportDiseaseZip(ctx, uri, ds)) toast(ctx, "এক্সপোর্ট হয়েছে") else toast(ctx, "এক্সপোর্ট করা যায়নি")
        }
    }
    return { ds ->
        target = ds
        val safe = ds.name.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifBlank { "rog" }
        launcher.launch("রোগ-" + safe + "-" + LocalDate.now().toString() + ".zip")
    }
}

@Composable
fun DiseasesScreen(onNew: () -> Unit, onEdit: (Disease) -> Unit, onAddMed: (Disease) -> Unit) {
    val d = Store.data
    var listFor by remember { mutableStateOf<Disease?>(null) }
    listFor?.let { PurchaseListDialog(it) { listFor = null } }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "\"আর যখন আমি অসুস্থ হই, তখন তিনিই(আল্লাহ) আমাকে আরোগ্য করেন।\"",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
            if (d.diseases.isEmpty()) {
                item { Text("এখনও কোনো রোগ নেই।", modifier = Modifier.padding(top = 8.dp)) }
            }
            items(d.diseases, key = { it.id }) { ds ->
                val meds = d.medicines.filter { it.diseaseId == ds.id }
                Card(onClick = { onEdit(ds) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            ds.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("তারিখ: " + prettyDate(ds.date))
                        if (meds.isNotEmpty()) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color(0xFF6D4C41), fontWeight = FontWeight.Bold)) { append("ওষুধ: ") }
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                                        append(meds.joinToString(", ") { it.name })
                                    }
                                }
                            )
                        }
                        FlowRow {
                            TextButton(onClick = { onAddMed(ds) }) { Text("+ এই রোগে ওষুধ যোগ") }
                            TextButton(onClick = { listFor = ds }) { Text("🛒 কেনার তালিকা") }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) { Text("+ নতুন রোগ যোগ করুন") }
    }
}

@Composable
fun DiseaseEditor(init: Disease, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = Store.data.diseases.none { it.id == init.id }
    var name by remember { mutableStateOf(init.name) }
    var date by remember { mutableStateOf(init.date) }
    var images by remember { mutableStateOf(init.images) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    if (showList) PurchaseListDialog(init) { showList = false }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val f = newImageFile(ctx)
            try {
                copyUriToFile(ctx, uri, f)
                images = images + f.name
            } catch (e: Exception) {
                toast(ctx, "ছবি যোগ করা যায়নি")
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        if (ok && pendingName.isNotEmpty()) images = images + pendingName
    }

    fun save() {
        if (name.isBlank()) {
            toast(ctx, "রোগের নাম দিন")
            return
        }
        val ds = init.copy(name = name.trim(), date = date, images = images)
        Store.update(ctx) { s ->
            s.copy(
                diseases = if (isNew) s.diseases + ds
                else s.diseases.map { if (it.id == ds.id) ds else it },
                currentDiseaseId = if (isNew) ds.id else s.currentDiseaseId
            )
        }
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (isNew) "নতুন রোগ" else "রোগের তথ্য", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("রোগের নাম (যেমন: ডায়াবেটিস)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }, modifier = Modifier.fillMaxWidth()) {
            Text("প্রেসক্রিপশনের তারিখ: " + prettyDate(date))
        }

        Text("প্রেসক্রিপশনের ছবি", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val f = newImageFile(ctx)
                pendingName = f.name
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                camera.launch(uri)
            }) { Text("📷 ক্যামেরা") }
            Button(onClick = { gallery.launch("image/*") }) { Text("🖼 গ্যালারি") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(images) { img ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Thumb(img) { openImage(ctx, img) }
                    TextButton(onClick = { images = images - img }) { Text("সরান") }
                }
            }
        }

        if (!isNew) {
            val cnt = Store.data.medicines.count { it.diseaseId == init.id }
            OutlinedButton(onClick = { showList = true }, modifier = Modifier.fillMaxWidth()) {
                Text("🛒 কেনার তালিকা ছবি করুন (" + bn(cnt) + "টি ওষুধ)")
            }
        }

        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("সংরক্ষণ করুন") }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("বাতিল") }
        if (!isNew) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("🗑 রোগটি মুছে ফেলুন", color = Color(0xFFC62828))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("মুছে ফেলবেন?") },
            text = { Text(init.name + " ও এর প্রেসক্রিপশনের ছবিগুলো মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    init.images.forEach { File(imageDir(ctx), it).delete() }
                    Store.update(ctx) { s ->
                        s.copy(
                            diseases = s.diseases.filter { it.id != init.id },
                            currentDiseaseId = if (s.currentDiseaseId == init.id) "" else s.currentDiseaseId,
                            medicines = s.medicines.map {
                                if (it.diseaseId == init.id) it.copy(diseaseId = "") else it
                            }
                        )
                    }
                    confirmDelete = false
                    onClose()
                }) { Text("মুছুন") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("না") } }
        )
    }
}

// ------------------------------------------------------------------ history

@Composable
fun HistoryScreen() {
    val d = Store.data
    val zone = ZoneId.systemDefault()
    var editing by remember { mutableStateOf<LogEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val groups = d.log
        .sortedByDescending { it.time }
        .groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }

    editing?.let { e -> LogEditDialog(e) { editing = null } }
    if (adding) LogEditDialog(null) { adding = false }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (groups.isEmpty()) {
                item { Text("এখনও কোনো ওষুধ খাওয়ার রেকর্ড নেই।") }
            }
            groups.forEach { (date, entries) ->
                item {
                    Text(
                        prettyDate(date.toString()),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                items(entries, key = { it.id }) { e ->
                    val t = Instant.ofEpochMilli(e.time).atZone(zone).toLocalTime()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = e }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "✅ " + bn(hhmm(t.hour, t.minute)) + "  " + e.medName +
                                " · " + Slots.label(e.slotKey),
                            modifier = Modifier.weight(1f)
                        )
                        Text("✏️", fontSize = 13.sp)
                    }
                }
            }
        }
        Button(
            onClick = {
                if (Store.data.medicines.isEmpty()) toast(ctx, "আগে ওষুধ যোগ করুন")
                else adding = true
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) { Text("+ ভুলে বাদ পড়া ওষুধ যোগ করুন") }
    }
}

// ইতিহাসের একটা এন্ট্রি বদলানো / মুছে ফেলা, অথবা (entry == null হলে) বাদ পড়া ওষুধ নিজে যোগ করা
@Composable
fun LogEditDialog(entry: LogEntry?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val d = Store.data
    val zone = ZoneId.systemDefault()
    val isNew = entry == null
    val initDt = remember {
        if (entry != null) Instant.ofEpochMilli(entry.time).atZone(zone).toLocalDateTime()
        else LocalDateTime.now().withSecond(0).withNano(0)
    }
    var date by remember { mutableStateOf(initDt.toLocalDate().toString()) }
    var time by remember { mutableStateOf(hhmm(initDt.hour, initDt.minute)) }
    var med by remember { mutableStateOf(d.medicines.firstOrNull()) }
    var slot by remember {
        mutableStateOf(entry?.slotKey ?: d.medicines.firstOrNull()?.timings?.firstOrNull() ?: Reminders.slotOrder[0])
    }
    var medMenu by remember { mutableStateOf(false) }
    var slotMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val dt = runCatching { LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.parse(time)) }.getOrNull()
        if (dt == null) {
            toast(ctx, "তারিখ বা সময় ঠিক নেই")
            return
        }
        val millis = dt.atZone(zone).toInstant().toEpochMilli()
        if (entry != null) {
            HistoryEdit.upsert(ctx, entry, entry.copy(time = millis))
        } else {
            val m = med
            if (m == null) {
                toast(ctx, "ওষুধ বাছাই করুন")
                return
            }
            HistoryEdit.upsert(ctx, null, LogEntry(millis, m.name, slot, m.dose, m.id))
        }
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "বাদ পড়া ওষুধ যোগ" else "ইতিহাস সম্পাদনা") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (entry != null) {
                    Text(entry.medName, fontWeight = FontWeight.Bold)
                    Text(Slots.label(entry.slotKey))
                } else {
                    Box {
                        OutlinedButton(onClick = { medMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("ওষুধ: " + (med?.name ?: "বাছাই করুন"))
                        }
                        DropdownMenu(expanded = medMenu, onDismissRequest = { medMenu = false }) {
                            d.medicines.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.name) },
                                    onClick = {
                                        med = m
                                        m.timings.firstOrNull()?.let { slot = it }
                                        medMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { slotMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("কখন: " + Slots.label(slot))
                        }
                        DropdownMenu(expanded = slotMenu, onDismissRequest = { slotMenu = false }) {
                            Reminders.slotOrder.forEach { k ->
                                DropdownMenuItem(
                                    text = { Text(Slots.label(k)) },
                                    onClick = { slot = k; slotMenu = false }
                                )
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("তারিখ: " + prettyDate(date))
                }
                OutlinedButton(onClick = { pickTime(ctx, time) { time = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("সময়: " + pretty(time))
                }
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("🗑 মুছে ফেলুন", color = Color(0xFFC62828))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text("সংরক্ষণ") } },
        dismissButton = { TextButton(onClick = onClose) { Text("বাতিল") } }
    )

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("মুছে ফেলবেন?") },
            text = { Text(entry.medName + " এর এই রেকর্ডটি ইতিহাস থেকে মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    HistoryEdit.delete(ctx, entry)
                    confirmDelete = false
                    onClose()
                }) { Text("মুছুন") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("না") } }
        )
    }
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val d = Store.data

    // ব্যাকআপ/রিস্টোরে প্রথমবার স্টোরেজ পারমিশন চাওয়া হয় (Android ১২ বা তার নিচে)। পরে কাজটা চলে
    var afterPerm by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val run = afterPerm
        afterPerm = null
        run?.invoke()
    }
    fun withStorage(action: () -> Unit) {
        val prefs = ctx.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
        if (android.os.Build.VERSION.SDK_INT <= 32 && !prefs.getBoolean("storageAsked", false)) {
            prefs.edit().putBoolean("storageAsked", true).apply()
            afterPerm = action
            permLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        } else {
            action()
        }
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            if (exportZip(ctx, uri)) toast(ctx, "ব্যাকআপ সেভ হয়েছে") else toast(ctx, "ব্যাকআপ করা যায়নি")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (importZip(ctx, uri)) toast(ctx, "রিস্টোর সম্পন্ন") else toast(ctx, "রিস্টোর করা যায়নি")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("থিম", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("light" to "☀️ লাইট", "dark" to "🌙 ব্ল্যাক", "system" to "📱 অটো").forEach { (k, label) ->
                if (d.theme == k) {
                    Button(onClick = { Store.update(ctx) { it.copy(theme = k) } }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { Store.update(ctx) { it.copy(theme = k) } }) { Text(label) }
                }
            }
        }

        var showMeals by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showMeals = true }, modifier = Modifier.fillMaxWidth()) {
            Text("🍽 খাবারের সময়")
        }
        if (showMeals) {
            AlertDialog(
                onDismissRequest = { showMeals = false },
                title = { Text("খাবারের সময়") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("সকালের খাবার", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                pickTime(ctx, d.morning) { t ->
                                    Store.update(ctx) { it.copy(morning = t) }
                                    Reminders.scheduleAll(ctx)
                                }
                            }) { Text(pretty(d.morning)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("দুপুরের খাবার", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                pickTime(ctx, d.noon) { t ->
                                    Store.update(ctx) { it.copy(noon = t) }
                                    Reminders.scheduleAll(ctx)
                                }
                            }) { Text(pretty(d.noon)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("রাতের খাবার", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                pickTime(ctx, d.night) { t ->
                                    Store.update(ctx) { it.copy(night = t) }
                                    Reminders.scheduleAll(ctx)
                                }
                            }) { Text(pretty(d.night)) }
                        }
                        Text(
                            "\"খাবারের আগের\" ওষুধের নোটিফিকেশন খাবারের সময়ের " + bn(Reminders.BEFORE_LEAD) +
                                " মিনিট আগে, আর \"খাবারের পরের\" ওষুধের নোটিফিকেশন খাবারের সময়ের " + bn(Reminders.AFTER_LAG) +
                                " মিনিট পরে আসবে। না খাওয়া পর্যন্ত প্রতি " + bn(Reminders.NAG_MINUTES) +
                                " মিনিট পর পর আবার আসবে।",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { showMeals = false }) { Text("বন্ধ") } }
            )
        }

        Button(
            onClick = { withStorage { exporter.launch("medicine-backup-" + LocalDate.now().toString() + ".zip") } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("☁️ ব্যাকআপ") }
        OutlinedButton(
            onClick = { withStorage { importer.launch(arrayOf("*/*")) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("📥 রিস্টোর") }

        var showHelp by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showHelp = true }, modifier = Modifier.fillMaxWidth()) {
            Text("❓ হেল্প")
        }
        if (showHelp) {
            AlertDialog(
                onDismissRequest = { showHelp = false },
                title = { Text("হেল্প") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("নোটিফিকেশন ঠিকমতো আসার জন্য", fontWeight = FontWeight.Bold)
                        Button(onClick = { Reminders.testNotification(ctx) }, modifier = Modifier.fillMaxWidth()) {
                            Text("🔔 টেস্ট নোটিফিকেশন পাঠান")
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                } catch (e: Exception) {
                                    toast(ctx, "সেটিংস খোলা যায়নি")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("🔋 ব্যাটারি সেভার থেকে অ্যাপটি বাদ দিন") }
                    }
                },
                confirmButton = { TextButton(onClick = { showHelp = false }) { Text("বন্ধ") } }
            )
        }

        var showAbout by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showAbout = true }, modifier = Modifier.fillMaxWidth()) {
            Text("ℹ️ ABOUT")
        }
        if (showAbout) {
            Dialog(
                onDismissRequest = { showAbout = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Image(
                        painter = painterResource(R.drawable.p1),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    TextButton(
                        onClick = { showAbout = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 8.dp)
                    ) { Text("✕ বন্ধ", color = Color.White, fontWeight = FontWeight.Bold) }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color(0xCC000000))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "মোঃ জাহাঙ্গীর আলম জীবন",
                            color = Color(0xFF80CBC4),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://jibon501.github.io/my-id/")))
                                } catch (e: Exception) {
                                    toast(ctx, "পেজ খোলা যায়নি")
                                }
                            }
                        )
                        Text("চরিতাবাড়ী, আদিতমারী, লালমনিরহাট", color = Color.White, fontSize = 14.sp)
                        Text(
                            "📞 ০১৭৬৭০৮০৬৬৬",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:01767080666")))
                                } catch (e: Exception) {
                                    toast(ctx, "ফোন অ্যাপ খোলা যায়নি")
                                }
                            }
                        )
                        Text("ইমেইল: jibonr8@gmail.com · ভার্শন: ২৫-১০-২৬_০৭", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
