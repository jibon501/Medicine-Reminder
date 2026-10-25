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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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

private val TABS = listOf(
    "📅" to "আজ",
    "💊" to "ওষুধ",
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
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TABS.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Text(t.first) },
                                    label = { Text(t.second, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> TodayScreen()
                            1 -> MedsScreen(
                                onNew = {
                                    val cur = Store.data.currentDiseaseId
                                    val ok = Store.data.diseases.any { it.id == cur }
                                    editMed = newMedicine(if (ok) cur else "")
                                },
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
