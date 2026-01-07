package com.example.refocus.system.overlay.ui.minigame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NumericKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DigitButton(1, onDigit, Modifier.weight(1f))
            DigitButton(2, onDigit, Modifier.weight(1f))
            DigitButton(3, onDigit, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DigitButton(4, onDigit, Modifier.weight(1f))
            DigitButton(5, onDigit, Modifier.weight(1f))
            DigitButton(6, onDigit, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DigitButton(7, onDigit, Modifier.weight(1f))
            DigitButton(8, onDigit, Modifier.weight(1f))
            DigitButton(9, onDigit, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onBackspace,
                modifier = Modifier.weight(1f),
            ) {
                Text("⌫")
            }
            DigitButton(0, onDigit, Modifier.weight(1f))
            Button(
                onClick = onOk,
                modifier = Modifier.weight(1f),
            ) {
                Text("OK")
            }
        }
    }
}

@Composable
private fun DigitButton(
    digit: Int,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onDigit(digit) },
        modifier = modifier,
    ) {
        Text(
            text = digit.toString(),
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}
