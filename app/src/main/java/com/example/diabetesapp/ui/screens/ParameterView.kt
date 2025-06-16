package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun ParameterView(
    tdd: String,
    onTddChange: (String) -> Unit,
    targetBZ: String,
    onTargetChange: (String) -> Unit,
    ioB: String,
    onIoBChange: (String) -> Unit
) {
    // Zustand für Dialog-Anzeigen
    var showTddCalc by rememberSaveable { mutableStateOf(false) }
    var showIoBCalc by rememberSaveable { mutableStateOf(false) }
    var showIoBInfo by remember { mutableStateOf(false) }

    // Zwischenwerte für TDD-Berechnung
    var weight by rememberSaveable { mutableStateOf("") }
    var factor by rememberSaveable { mutableStateOf("") }
    var calcTdd by rememberSaveable { mutableStateOf<String?>(null) }

    // Zwischenwerte für IoB-Berechnung
    var lastBolus by rememberSaveable { mutableStateOf("") }
    var elapsedMin by rememberSaveable { mutableStateOf("") }
    var calcIoB by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Parameter", style = MaterialTheme.typography.headlineSmall)

        // TDD-Feld mit Berechnungs-Dialog
        OutlinedTextField(
            value = tdd,
            onValueChange = { /* read-only */ },
            label = { Text("TDD (IE/Tag)") },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTddCalc = true },
            trailingIcon = {
                IconButton(onClick = { showTddCalc = true }) {
                    Icon(Icons.Default.Calculate, contentDescription = "TDD berechnen")
                }
            }
        )

        // Ziel-Blutzucker
        OutlinedTextField(
            value = targetBZ,
            onValueChange = onTargetChange,
            label = { Text("Ziel-BZ (mg/dl)") },
            modifier = Modifier.fillMaxWidth()
        )

        // IoB-Feld mit Info- und Berechnungs-Dialog
        OutlinedTextField(
            value = ioB,
            onValueChange = { /* read-only */ },
            label = { Text("IoB (IE)") },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showIoBCalc = true },
            trailingIcon = {
                Row {
                    IconButton(onClick = { showIoBInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Was ist IoB?")
                    }
                    IconButton(onClick = { showIoBCalc = true }) {
                        Icon(Icons.Default.Calculate, contentDescription = "IoB berechnen")
                    }
                }
            }
        )
    }

    // Dialog für TDD-Berechnung
    if (showTddCalc) {
        AlertDialog(
            onDismissRequest = { showTddCalc = false },
            title = { Text("TDD berechnen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Gewicht (kg) und Faktor (IE/kg) eingeben:")
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Gewicht (kg)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = factor,
                        onValueChange = { factor = it },
                        label = { Text("Faktor (IE/kg), z. B. 0,6") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val w = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val f = factor.replace(',', '.').toDoubleOrNull() ?: 0.0
                            calcTdd = if (w > 0 && f > 0) "%.1f".format(w * f) else "Fehler"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Berechnen")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    calcTdd?.let { Text("Ergebnis: $it IE/Tag", style = MaterialTheme.typography.bodyLarge) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    calcTdd?.takeIf { it != "Fehler" }?.let(onTddChange)
                    showTddCalc = false
                }) {
                    Text("Übernehmen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTddCalc = false }) { Text("Abbrechen") }
            }
        )
    }

    // Dialog für IoB-Berechnung
    if (showIoBCalc) {
        AlertDialog(
            onDismissRequest = { showIoBCalc = false },
            title = { Text("IoB berechnen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Letzter Bolus (IE) und verstrichene Zeit (Minuten) eingeben:")
                    OutlinedTextField(
                        value = lastBolus,
                        onValueChange = { lastBolus = it },
                        label = { Text("Bolusmenge (IE)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = elapsedMin,
                        onValueChange = { elapsedMin = it },
                        label = { Text("Minuten seit Bolus") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val b = lastBolus.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val m = elapsedMin.toIntOrNull() ?: 0
                            val dur = 240 // Wirkdauer: 4 Stunden
                            val rem = ((dur - m).coerceAtLeast(0)) / dur.toFloat()
                            calcIoB = "%.1f".format(b * rem)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Berechnen")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    calcIoB?.let { Text("IoB: $it IE", style = MaterialTheme.typography.bodyLarge) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    calcIoB?.let(onIoBChange)
                    showIoBCalc = false
                }) {
                    Text("Übernehmen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIoBCalc = false }) { Text("Abbrechen") }
            }
        )
    }

    // Info-Dialog für IoB
    if (showIoBInfo) {
        AlertDialog(
            onDismissRequest = { showIoBInfo = false },
            title = { Text("Was ist Insulin-on-Board (IoB)?") },
            text = {
                Text(
                    "Insulin-on-Board (IoB) sind die noch aktiven Insulineinheiten aus " +
                            "vorherigen Bolusgaben (Wirkdauer ca. 4 Stunden). Sie helfen, Unterzucker " +
                            "zu vermeiden, indem die Restwirkung beim nächsten Bolus berücksichtigt wird."
                )
            },
            confirmButton = {
                TextButton(onClick = { showIoBInfo = false }) { Text("Verstanden") }
            }
        )
    }
}
