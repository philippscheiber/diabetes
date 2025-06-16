package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ParameterView(
    tdd: String,
    onTddChange: (String) -> Unit,
    targetBZ: String,
    onTargetChange: (String) -> Unit,
    ioB: String,
    onIoBChange: (String) -> Unit
) {
    // sofort öffnender TDD-Dialog
    var showTddCalc by rememberSaveable { mutableStateOf(true) }
    var weight     by rememberSaveable { mutableStateOf("") }
    var factor     by rememberSaveable { mutableStateOf("") }
    var calcResult by rememberSaveable { mutableStateOf<String?>(null) }
    // Info-Dialog für Faktor
    var showFactorInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Parameter", style = MaterialTheme.typography.headlineSmall)

        // TDD-Feld (read-only)
        OutlinedTextField(
            value = tdd,
            onValueChange = { /* no-op */ },
            readOnly = true,
            label = { Text("TDD (IE/Tag)") },
            trailingIcon = {
                Icon(Icons.Default.Calculate, contentDescription = "TDD berechnen")
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Ziel-BZ
        OutlinedTextField(
            value = targetBZ,
            onValueChange = onTargetChange,
            label = { Text("Ziel-BZ (mg/dl)") },
            modifier = Modifier.fillMaxWidth()
        )

        // Insulin on Board
        OutlinedTextField(
            value = ioB,
            onValueChange = onIoBChange,
            label = { Text("IoB (IE)") },
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Dialog: TDD berechnen
    if (showTddCalc) {
        AlertDialog(
            onDismissRequest = { showTddCalc = false },
            title = { Text("TDD berechnen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Gib dein Gewicht (kg) und den Faktor (IE/kg) ein:")
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
                        label = { Text("Faktor (IE/kg)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showFactorInfo = true }) {
                                Icon(Icons.Default.Info, contentDescription = "Faktor Info")
                            }
                        }
                    )
                    Button(
                        onClick = {
                            val w = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val f = factor.replace(',', '.').toDoubleOrNull() ?: 0.0
                            calcResult = if (w > 0 && f > 0)
                                String.format("%.1f", w * f)
                            else
                                "Fehler"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Berechnen")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    calcResult?.let { result ->
                        Text("Ergebnis: $result IE/Tag", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    calcResult
                        ?.takeIf { it != "Fehler" }
                        ?.let(onTddChange)
                    showTddCalc = false
                }) {
                    Text("Übernehmen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTddCalc = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Dialog: Faktor-Info
    if (showFactorInfo) {
        AlertDialog(
            onDismissRequest = { showFactorInfo = false },
            title = { Text("Info zum Faktor") },
            text = {
                Text(
                    "Der Insulin-pro-Kg-Faktor (IE/kg) wird individuell von deinem " +
                            "Arzt festgelegt. Bitte verwende den dir mitgeteilten Wert."
                )
            },
            confirmButton = {
                TextButton(onClick = { showFactorInfo = false }) {
                    Text("Verstanden")
                }
            }
        )
    }
}
