package com.example.diabetesapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.diabetesapp.ui.theme.DiabetesAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiabetesAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InsulinCalculatorUI()
                }
            }
        }
    }
}

@Composable
fun InsulinCalculatorUI() {
    var carbsInput by remember { mutableStateOf("") }
    var bloodSugarInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    val icr = 10.0  // 1 IE für 10g Kohlenhydrate
    val targetBloodSugar = 100.0
    val correctionFactor = 30.0  // 1 IE senkt 30 mg/dl

    // Kontext, um die Activity zu starten
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Insulin-Rechner", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = carbsInput,
            onValueChange = { carbsInput = it },
            label = { Text("Kohlenhydrate (g)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = bloodSugarInput,
            onValueChange = { bloodSugarInput = it },
            label = { Text("Blutzucker (mg/dl)") },
            modifier = Modifier.fillMaxWidth()
        )

        // NEUER BUTTON: Barcode-Scanner starten
        Button(
            onClick = {
                val intent = Intent(context, BarcodeScannerActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Barcode scannen")
        }

        Button(
            onClick = {
                val carbs = carbsInput.toDoubleOrNull() ?: 0.0
                val bloodSugar = bloodSugarInput.toDoubleOrNull() ?: targetBloodSugar

                val bolus = carbs / icr
                val correction = if (bloodSugar > targetBloodSugar) {
                    (bloodSugar - targetBloodSugar) / correctionFactor
                } else 0.0

                val totalInsulin = bolus + correction
                result = "Empfohlene Insulinmenge: %.1f IE".format(totalInsulin)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Berechnen")
        }

        Text(text = result, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
fun InsulinCalculatorPreview() {
    DiabetesAppTheme {
        InsulinCalculatorUI()
    }
}
