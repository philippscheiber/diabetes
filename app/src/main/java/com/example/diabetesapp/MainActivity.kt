@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.diabetesapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.diabetesapp.network.NetworkModule
import com.example.diabetesapp.ui.theme.DiabetesAppTheme
import com.example.diabetesapp.ui.screens.ParameterView
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class Screen { Calculation, Chart, Parameters }

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS        = "settings"
        private const val KEY_TDD      = "tdd"
        private const val KEY_TARGETBZ = "targetBZ"
        private const val KEY_IOB      = "ioB"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val barcodeLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { res ->
            if (res.resultCode == RESULT_OK) {
                res.data?.getStringExtra("EXTRA_BARCODE")?.let { code ->
                    lifecycleScope.launch {
                        try {
                            val resp = NetworkModule.openFoodApi.getProduct(code)
                            if (resp.status == 1 && resp.product != null) {
                                val kh100 = resp.product.nutriments?.carbohydrates_100g ?: 0.0
                                Toast.makeText(
                                    this@MainActivity,
                                    "Produkt: $kh100 g KH/100g",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Produkt nicht gefunden",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                "Netzwerkfehler",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        setContent {
            DiabetesAppTheme {
                var currentScreen by rememberSaveable { mutableStateOf(Screen.Calculation) }
                var tdd by rememberSaveable { mutableStateOf(prefs.getString(KEY_TDD, "")!!) }
                var targetBZ by rememberSaveable { mutableStateOf(prefs.getString(KEY_TARGETBZ, "")!!) }
                var ioB by rememberSaveable { mutableStateOf(prefs.getString(KEY_IOB, "")!!) }
                var carbsInput by rememberSaveable { mutableStateOf("") }
                var portion by rememberSaveable { mutableStateOf("") }
                var bloodSugar by rememberSaveable { mutableStateOf("") }
                var result by rememberSaveable { mutableStateOf("") }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(
                                when(currentScreen) {
                                    Screen.Calculation -> "Bolus-Rechner"
                                    Screen.Chart       -> "Blutzucker-Verlauf"
                                    Screen.Parameters  -> "Parameter"
                                }
                            )},
                            actions = {
                                IconButton(onClick = {
                                    prefs.edit().clear().apply()
                                    tdd = ""; targetBZ = ""; ioB = ""
                                    currentScreen = Screen.Parameters
                                }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        BottomAppBar {
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { currentScreen = Screen.Calculation }) {
                                Icon(Icons.Default.Calculate, contentDescription = "Rechner")
                            }
                            IconButton(onClick = { currentScreen = Screen.Chart }) {
                                Icon(Icons.Default.Assessment, contentDescription = "Verlauf")
                            }
                            IconButton(onClick = { currentScreen = Screen.Parameters }) {
                                Icon(Icons.Default.Settings, contentDescription = "Parameter")
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when(currentScreen) {
                            Screen.Calculation -> CalculationView(
                                carbsInput = carbsInput,
                                onCarbsChange = { carbsInput = it },
                                portion = portion,
                                onPortionChange = { portion = it },
                                bloodSugarInput = bloodSugar,
                                onSugarChange = { bloodSugar = it },
                                onScanClick = {
                                    barcodeLauncher.launch(
                                        Intent(this@MainActivity, BarcodeScannerActivity::class.java)
                                    )
                                },
                                onCalculate = {
                                    // 1) TDD
                                    val rawTdd = tdd.toDoubleOrNull() ?: run {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Bitte TDD eingeben",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@CalculationView
                                    }
                                    val tddVal = if(rawTdd > 0) rawTdd else run {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "TDD muss > 0 sein",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@CalculationView
                                    }
                                    // 2) Werte
                                    val ikr = 500.0 / tddVal
                                    val kf  = 1800.0 / tddVal
                                    val kh  = carbsInput.toDoubleOrNull() ?: 0.0
                                    val wt  = portion.toDoubleOrNull()     ?: 0.0
                                    val sugarVal = bloodSugar.toDoubleOrNull() ?: 0.0
                                    val totalKH   = kh * (wt / 100.0)
                                    val carbU     = totalKH / ikr
                                    val corrU     = ((sugarVal - targetBZ.toDoubleOrNull()!!)
                                        .coerceAtLeast(0.0)) / kf
                                    val baseBolus = carbU + corrU
                                    // 3) Zeit-Faktor
                                    val hour = Calendar.getInstance()
                                        .get(Calendar.HOUR_OF_DAY)
                                    val timeFactor = when(hour) {
                                        in 6..11  -> 1.2
                                        in 12..16 -> 1.0
                                        in 17..20 -> 0.9
                                        else      -> 0.8
                                    }
                                    // 4) Bolus inkl. IoB
                                    val ioBVal     = ioB.toDoubleOrNull() ?: 0.0
                                    val totalBolus = (baseBolus * timeFactor) - ioBVal
                                    // 5) Text
                                    result = buildString {
                                        append("IKR: %.1f g/IE\n".format(ikr))
                                        append("KF:  %.1f mg/dl·IE\n\n".format(kf))
                                        append("KH: %.1f g → %.1f IE\n".format(totalKH, carbU))
                                        append("Korr.: %.1f IE\n".format(corrU))
                                        append("Basis: %.1f IE\n".format(baseBolus))
                                        append("Zeit-Faktor: x%.2f\n".format(timeFactor))
                                        append("IoB:   %.1f IE\n\n".format(ioBVal))
                                        append("Gesamt: %.1f IE".format(totalBolus))
                                    }
                                },
                                result = result
                            )
                            Screen.Chart -> ChartView()
                            Screen.Parameters -> ParameterView(
                                tdd = tdd,
                                onTddChange = {
                                    tdd = it
                                    prefs.edit().putString(KEY_TDD, it).apply()
                                },
                                targetBZ = targetBZ,
                                onTargetChange = {
                                    targetBZ = it
                                    prefs.edit().putString(KEY_TARGETBZ, it).apply()
                                },
                                ioB = ioB,
                                onIoBChange = {
                                    ioB = it
                                    prefs.edit().putString(KEY_IOB, it).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculationView(
    carbsInput: String,
    onCarbsChange: (String) -> Unit,
    portion: String,
    onPortionChange: (String) -> Unit,
    bloodSugarInput: String,
    onSugarChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onCalculate: () -> Unit,
    result: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = carbsInput,
            onValueChange = onCarbsChange,
            label = { Text("KH / 100 g") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = portion,
            onValueChange = onPortionChange,
            label = { Text("Portionsgröße (g)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = bloodSugarInput,
            onValueChange = onSugarChange,
            label = { Text("akt. BZ (mg/dl)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScanClick, modifier = Modifier.weight(1f)) {
                Text("Scannen")
            }
            Button(onClick = onCalculate, modifier = Modifier.weight(1f)) {
                Text("Berechnen")
            }
        }
        if (result.isNotBlank()) {
            Text(result, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ChartView() {
    val points = listOf(100f, 120f, 90f, 110f, 105f, 130f)
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Blutzucker-Verlauf", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFFE0E0E0))
        ) {
            val w = size.width;
            val h = size.height;
            val max = points.maxOrNull() ?: 1f;
            val min = points.minOrNull() ?: 0f;
            val range = max - min;
            val step = w / (points.size - 1).coerceAtLeast(1);
            val path = Path().apply {
                points.forEachIndexed { i, v ->
                    val x = i * step
                    val y = h - ((v - min) / range) * h
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(path, Color(0xFF6200EE), style = Stroke(width = 4.dp.toPx()))
        }
    }
}