package com.example.refocus.system.overlay.ui.minigame.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumericKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onOk: () -> Unit,
    enabled: Boolean = true,
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
            DigitButton(1, onDigit, enabled, Modifier.weight(1f))
            DigitButton(2, onDigit, enabled, Modifier.weight(1f))
            DigitButton(3, onDigit, enabled, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DigitButton(4, onDigit, enabled, Modifier.weight(1f))
            DigitButton(5, onDigit, enabled, Modifier.weight(1f))
            DigitButton(6, onDigit, enabled, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DigitButton(7, onDigit, enabled, Modifier.weight(1f))
            DigitButton(8, onDigit, enabled, Modifier.weight(1f))
            DigitButton(9, onDigit, enabled, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onBackspace,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(
                    text = "⌫",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            DigitButton(0, onDigit, enabled, Modifier.weight(1f))
            Button(
                onClick = onOk,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(
                    text = "OK",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun DigitButton(
    digit: Int,
    onDigit: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onDigit(digit) },
        enabled = enabled,
        modifier = modifier.height(52.dp),
    ) {
        Text(
            text = digit.toString(),
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}
