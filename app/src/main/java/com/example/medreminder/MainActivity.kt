package com.example.medreminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.reload(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Reminders.ensureChannel(this)
        Reminders.scheduleAll(this)
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        Store.reload(this)
    }
}

// "আজ" ট্যাবের আইকন: আজকের মাস ও তারিখসহ ছোট ক্যালেন্ডার (যেমন OCT ২৭)
@Composable
fun TodayCalendarIcon() {
    val today = LocalDate.now()
    val mon = today.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(4.5.dp)
    Column(
        modifier = Modifier
            .size(width = 24.dp, height = 25.5.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.1.dp, Color(0xFFB0BEC5), shape)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(9.dp).background(Color(0xFFE53935))
        ) {
            Text(mon, fontSize = 6.sp, lineHeight = 6.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(bn(today.dayOfMonth), fontSize = 11.25.sp, lineHeight = 11.25.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
        }
    }
}

// ট্যাব অ্যানিমেশনের ধরন (সেটিংস থেকে বদলানো যায়)। ০ = ডিফল্ট (জুম আউট → জুম ইন)
// ০ = কোনো অ্যানিমেশন নেই (ডিফল্ট)। বাকিগুলো ১ থেকে
// প্রতিটা ধরন নিচের সিলেক্ট বক্সের চলা এবং মূল স্ক্রিনের ট্রানজিশন, দুটোই ঠিক করে
val TAB_ANIMS = listOf(
    "🚫 কোনো অ্যানিমেশন নয় (ডিফল্ট)",
    "🔍 জুম আউট → ইন",
    "🌀 ঘুরতে ঘুরতে",
    "➡️ সোজা লাইনে",
    "🏀 লাফিয়ে (বাউন্স)",
    "🃏 ফ্লিপ (উল্টে)",
    "🍬 রাবারের মতো টান"
)

// মূল স্ক্রিনের ট্যাব বদলের ট্রানজিশন (নিচের সিলেক্ট বক্সের স্টাইলের সাথে মিল রেখে)
private fun tabTransition(style: Int, forward: Boolean): androidx.compose.animation.ContentTransform {
    if (style == 0) return androidx.compose.animation.EnterTransition.None togetherWith
        androidx.compose.animation.ExitTransition.None
    val d = if (forward) 1 else -1
    return when (style - 1) {
        1 -> androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(450)) togetherWith
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250))
        2 -> (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(380)) { it * d } +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(380))) togetherWith
            (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(380)) { -it * d } +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(380)))
        3 -> (androidx.compose.animation.slideInVertically(
            androidx.compose.animation.core.spring(
                dampingRatio = 0.55f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
            )
        ) { it / 4 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250))) togetherWith
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
        4 -> androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(350, delayMillis = 150)) togetherWith
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
        5 -> androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)) togetherWith
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500))
        else -> (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(300), initialScale = 0.85f)) togetherWith
            (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(200), targetScale = 0.85f))
    }
}

// নিচের বার: বাটন ২৫% ছোট। ট্যাব বদলালে সিলেক্টের শেড এক বাটন থেকে আরেক বাটনে যায়,
// কীভাবে যাবে তা style ঠিক করে (TAB_ANIMS)
@Composable
private fun AnimatedBottomBar(tab: Int, style: Int, sound: Boolean, volume: Float, onSelect: (Int) -> Unit) {
    val prog = remember { androidx.compose.animation.core.Animatable(1f) }
    var from by remember { mutableFloatStateOf(tab.toFloat()) }
    var to by remember { mutableIntStateOf(tab) }
    LaunchedEffect(tab) {
        if (to == tab) return@LaunchedEffect
        // চলতি অবস্থান থেকেই শুরু, তাই মাঝপথে আবার চাপলেও ঝটকা লাগবে না
        from = from + (to - from) * prog.value
        to = tab
        if (style == 0) {
            // কোনো অ্যানিমেশন নয়: শেড সাথে সাথে নতুন বাটনে বসে, সাউন্ডও নেই
            prog.snapTo(1f)
            return@LaunchedEffect
        }
        if (sound) TabSound.play(style - 1, volume)
        prog.snapTo(0f)
        when (style - 1) {
            1 -> prog.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            2 -> prog.animateTo(1f, androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.LinearEasing))
            3 -> prog.animateTo(
                1f,
                androidx.compose.animation.core.spring(
                    dampingRatio = 0.45f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            )
            4 -> prog.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            5 -> prog.animateTo(1f, androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            else -> prog.animateTo(1f, androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val p = prog.value
    val pos = from + (to - from) * p
    val dir = if (to >= from) 1f else -1f
    val wave = kotlin.math.sin(Math.PI * p.coerceIn(0f, 1f)).toFloat()
    // স্টাইল অনুযায়ী শেডের স্কেল/ঘোরা
    var sx = 1f; var sy = 1f; var rz = 0f; var ry = 0f
    when (style - 1) {
        1 -> { sx = 1f - 0.25f * wave; sy = sx; rz = 360f * p * dir }
        2 -> { }
        3 -> { }
        4 -> { ry = 360f * p * dir }
        5 -> { sx = 1f + 1.6f * wave; sy = 1f - 0.25f * wave }
        else -> { sx = 1f - 0.4f * wave; sy = sx }
    }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp)
        ) {
            val itemW = maxWidth / TABS.size
            val hlW = 33.dp
            val hlH = 33.dp
            // চলমান শেড
            Box(
                Modifier
                    .offset(x = itemW * pos + (itemW - hlW) / 2, y = 5.dp)
                    .size(width = hlW, height = hlH)
                    .graphicsLayer {
                        scaleX = sx; scaleY = sy; rotationZ = rz; rotationY = ry
                        cameraDistance = 12f * density
                    }
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Row(Modifier.fillMaxSize()) {
                TABS.forEachIndexed { i, t ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(i) }
                            .padding(top = 5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(hlH)) {
                            if (i == 0) TodayCalendarIcon() else Text(t.first, fontSize = 21.sp)
                        }
                        Text(
                            t.second,
                            fontSize = 8.25.sp,
                            lineHeight = 10.sp,
                            modifier = Modifier.padding(top = 3.dp),
                            color = if (tab == i) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private val TABS = listOf(
    "📅" to "আজ",
    "💊" to "ঔষধ",
    "🩺" to "রোগ",
    "⚙️" to "সেটিংস"
)

// নতুন রোগ যোগ করার পরে সেই রোগটাই ডিফল্ট ধরা হয়
private fun newMedicine(diseaseId: String = ""): Medicine = Medicine(
    id = UUID.randomUUID().toString(),
    name = "",
    dose = 1,
    diseaseId = diseaseId,
    timings = emptyList(),
    startDate = LocalDate.now().toString(),
    courseDays = 0,
    form = "TABLET",
    intervalDays = 1,
    muted = false
)

private fun newDisease() = Disease(
    id = UUID.randomUUID().toString(),
    name = "",
    doctor = "",
    date = LocalDate.now().toString(),
    images = emptyList()
)

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editMed by remember { mutableStateOf<Medicine?>(null) }
    var editDisease by remember { mutableStateOf<Disease?>(null) }
    val em = editMed
    val ed = editDisease

    val dark = when (Store.data.theme) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val scheme = if (dark) {
        androidx.compose.material3.darkColorScheme(
            primary = Color(0xFF4DB6AC),
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1E1E1E),
            surfaceContainerHighest = Color(0xFF1E1E1E)
        )
    } else {
        lightColorScheme(primary = Color(0xFF00796B))
    }

    // অ্যাপ খোলার সময় ৫ সেকেন্ড আয়াত দেখানো হয়
    var splash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(5000)
        splash = false
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (splash) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "وَاِذَا مَرِضۡتُ فَهُوَ يَشۡفِيۡنِ",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "ওয়া ইযা মারিদতু ফাহুওয়া ইয়াশফীন",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "\"আর যখন আমি অসুস্থ হই, তখন তিনিই(আল্লাহ) আমাকে আরোগ্য করেন।\" (আশ-শুআরা আয়াত ৮০)।",
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (em != null) {
                BackHandler { editMed = null }
                MedicineEditor(em) { editMed = null }
            } else if (ed != null) {
                BackHandler { editDisease = null }
                DiseaseEditor(ed) { editDisease = null }
            } else {
                // ফোনের ব্যাক: অন্য ট্যাব থেকে আগে ড্যাশ (আজ) এ আসবে, ড্যাশে থাকলে অ্যাপ বন্ধ হবে
                BackHandler(enabled = tab != 0) { tab = 0 }
                Scaffold(
                    bottomBar = { AnimatedBottomBar(tab, Store.data.tabAnim, Store.data.tabSound, Store.data.tabVolume) { tab = it } }
                ) { pad ->
                    val anim = Store.data.tabAnim
                    androidx.compose.animation.AnimatedContent(
                        targetState = tab,
                        modifier = Modifier.padding(pad),
                        transitionSpec = { tabTransition(anim, targetState > initialState) },
                        label = "tabs"
                    ) { t ->
                        // ঘুরে/উল্টে ঢোকা ও বেরোনো স্টাইলের জন্য (মূল স্ক্রিনেও একই ধরন)
                        val st = transition.animateFloat(
                            transitionSpec = { androidx.compose.animation.core.tween(500) },
                            label = "tabAnimState"
                        ) { s ->
                            when (s) {
                                androidx.compose.animation.EnterExitState.PreEnter -> -1f
                                androidx.compose.animation.EnterExitState.Visible -> 0f
                                androidx.compose.animation.EnterExitState.PostExit -> 1f
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val v = st.value
                                    when (anim - 1) {
                                        1 -> { rotationZ = 14f * v; val k = 1f - 0.12f * kotlin.math.abs(v); scaleX = k; scaleY = k }
                                        4 -> { rotationY = 90f * v; cameraDistance = 12f * density }
                                        else -> {}
                                    }
                                }
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            when (t) {
                                0 -> TodayScreen()
                                1 -> MedsScreen(
                                    onNew = { did -> editMed = newMedicine(did) },
                                    onEdit = { editMed = it }
                                )
                                2 -> DiseasesScreen(
                                    onNew = { editDisease = newDisease() },
                                    onEdit = { editDisease = it },
                                    onAddMed = { editMed = newMedicine(it.id); tab = 1 }
                                )
                                else -> SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
