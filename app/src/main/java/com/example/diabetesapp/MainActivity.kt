package com.example.diabetesapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.diabetesapp.network.NetworkModule
import com.example.diabetesapp.ui.theme.DiabetesAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val PREFS = "settings"
    private val KEY_TDD = "tdd"
    private val KEY_TARGET = "targetBZ"
    private val KEY_IOB = "ioB"

    // Steuere, ob wir im Setup- oder Calculator-Modus sind
    private var showSetup by mutableStateOf(false)

    // Setup-Werte
    private var tdd by mutableStateOf("")
    private var targetBZ by mutableStateOf("")
    private var insulinOnBoard by mutableStateOf("")

    // Calculator-Werte
    private var carbsPer100g by mutableStateOf("")
    private var portionWeight by mutableStateOf("")
    private var bloodSugar by mutableStateOf("")
    private var resultText by mutableStateOf("")

    // Launcher für BarcodeScannerActivity
    private val barcodeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                res.data
                    ?.getStringExtra("EXTRA_BARCODE")
                    ?.let { fetchProductData(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SharedPreferences laden
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_TDD) && prefs.contains(KEY_TARGET) && prefs.contains(KEY_IOB)) {
            tdd = prefs.getString(KEY_TDD, "")!!
            targetBZ = prefs.getString(KEY_TARGET, "")!!
            insulinOnBoard = prefs.getString(KEY_IOB, "")!!
            showSetup = false
        } else {
            showSetup = true
        }

        setContent {
            DiabetesAppTheme {
                Scaffold(
                    bottomBar = {
                        BottomAppBar {
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showSetup = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Einstellungen"
                                )
                            }
                        }
                    }
                ) { padding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        if (showSetup) {
                            SetupScreen(
                                initialTdd = tdd,
                                initialTargetBZ = targetBZ,
                                initialIoB = insulinOnBoard
                            ) { newTdd, newTarget, newIoB ->
                                // speichern
                                prefs.edit()
                                    .putString(KEY_TDD, newTdd)
                                    .putString(KEY_TARGET, newTarget)
                                    .putString(KEY_IOB, newIoB)
                                    .apply()
                                // übernehmen und wechseln
                                tdd = newTdd
                                targetBZ = newTarget
                                insulinOnBoard = newIoB
                                showSetup = false
                            }
                        } else {
                            CalculatorScreen(
                                tdd = tdd,
                                targetBZ = targetBZ,
                                insulinOnBoard = insulinOnBoard,
                                carbsPer100g = carbsPer100g,
                                onCarbsChange = { carbsPer100g = it },
                                portionWeight = portionWeight,
                                onWeightChange = { portionWeight = it },
                                bloodSugar = bloodSugar,
                                onSugarChange = { bloodSugar = it },
                                onScanClick = {
                                    barcodeLauncher.launch(
                                        Intent(this@MainActivity, BarcodeScannerActivity::class.java)
                                    )
                                },
                                onCalculate = { calculateBolus() },
                                result = resultText
                            )
                        }
                    }
                }
            }
        }
    }

    private fun calculateBolus() {
        // parse & fallback
        val tddVal = tdd.toDoubleOrNull() ?: run {
            Toast.makeText(this, "Bitte TDD eingeben", Toast.LENGTH_SHORT).show()
            return
        }
        val targetVal = targetBZ.toDoubleOrNull() ?: run {
            Toast.makeText(this, "Bitte Ziel-BZ eingeben", Toast.LENGTH_SHORT).show()
            return
        }
        val ioBVal = insulinOnBoard.toDoubleOrNull() ?: 0.0

        val kh100 = carbsPer100g.toDoubleOrNull() ?: 0.0
        val weight = portionWeight.toDoubleOrNull() ?: 0.0
        val current = bloodSugar.toDoubleOrNull() ?: run {
            Toast.makeText(this, "Bitte aktuellen BZ eingeben", Toast.LENGTH_SHORT).show()
            return
        }

        val ikr = 500.0 / tddVal
        val kf = 1800.0 / tddVal

        val totalCarbs = kh100 * (weight / 100.0)
        val carbUnits = totalCarbs / ikr
        val deltaBZ = current - targetVal
        val corrUnits = if (deltaBZ > 0) deltaBZ / kf else 0.0
        val bolus = carbUnits + corrUnits - ioBVal

        resultText = buildString {
            append("IKR: %.1f g/IE\n".format(ikr))
            append("KF:  %.1f mg/dL IE\n\n".format(kf))
            append("KH: %.1f g → %.1f IE\n".format(totalCarbs, carbUnits))
            append("Korr.: %.1f IE\n".format(corrUnits))
            append("IoB: %.1f IE\n\n".format(ioBVal))
            append("Gesamt-Bolus: %.1f IE".format(bolus))
        }
    }

    private fun fetchProductData(barcode: String) {
        lifecycleScope.launch {
            try {
                val resp = NetworkModule.openFoodApi.getProduct(barcode)
                if (resp.status == 1 && resp.product != null) {
                    val kh100 = resp.product.nutriments?.carbohydrates_100g ?: 0.0
                    carbsPer100g = kh100.toString()
                    Toast
                        .makeText(this@MainActivity, "Produkt: $kh100 g KH/100g", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast
                        .makeText(this@MainActivity, "Produkt nicht gefunden", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Toast
                    .makeText(this@MainActivity, "Netzwerkfehler", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}

@Composable
fun SetupScreen(
    initialTdd: String,
    initialTargetBZ: String,
    initialIoB: String,
    onSave: (String, String, String) -> Unit
) {
    var tddState by rememberSaveable { mutableStateOf(initialTdd) }
    var targetState by rememberSaveable { mutableStateOf(initialTargetBZ) }
    var ioBState by rememberSaveable { mutableStateOf(initialIoB) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Erst-Setup", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = tddState,
            onValueChange = { tddState = it },
            label = { Text("TDD (IE/Tag)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = targetState,
            onValueChange = { targetState = it },
            label = { Text("Ziel-BZ (mg/dL)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = ioBState,
            onValueChange = { ioBState = it },
            label = { Text("Insulin-on-Board (IE)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onSave(tddState, targetState, ioBState) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Speichern und weiter")
        }
    }
}

@Composable
fun CalculatorScreen(
    tdd: String,
    targetBZ: String,
    insulinOnBoard: String,
    carbsPer100g: String,
    onCarbsChange: (String) -> Unit,
    portionWeight: String,
    onWeightChange: (String) -> Unit,
    bloodSugar: String,
    onSugarChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onCalculate: () -> Unit,
    result: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Insulin-Bolus-Rechner",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "TDD: $tdd IE/Tag  •  Ziel-BZ: $targetBZ mg/dL  • IoB: $insulinOnBoard IE",
            style = MaterialTheme.typography.bodyMedium
        )

        Divider()

        OutlinedTextField(
            value = carbsPer100g,
            onValueChange = onCarbsChange,
            label = { Text("KH/100 g") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = portionWeight,
            onValueChange = onWeightChange,
            label = { Text("Portionsgröße (g)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = bloodSugar,
            onValueChange = onSugarChange,
            label = { Text("aktueller BZ (mg/dL)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Barcode scannen")
        }
        Button(
            onClick = onCalculate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bolus berechnen")
        }

        Spacer(Modifier.height(8.dp))
        Text(result, style = MaterialTheme.typography.bodyMedium)
    }
}
