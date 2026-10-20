package com.familycontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Numeryczna klawiatura PIN rodzica z ochroną przed brute-force i wskaźnikiem blokady czasowej.
 */
@Composable
fun PinPadDialog(
    isLockedOut: Boolean,
    remainingLockoutSeconds: Int,
    errorMessage: String?,
    onPinSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredDigits by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Autoryzacja Rodzica",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isLockedOut) "Blokada bezpieczeństwa!" else "Wprowadź kod PIN, aby wyjść z trybu dziecka",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isLockedOut) {
                    Text(
                        text = "Zbyt wiele prób. Spróbuj za ${remainingLockoutSeconds}s",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    // Wskaźniki wprowadzonych cyfr (dots)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < enteredDigits.length
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    if (!errorMessage.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Klawiatura numeryczna 3x4
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "OK")
                    )

                    for (row in keys) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (key in row) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (key) {
                                                "OK" -> MaterialTheme.colorScheme.primary
                                                "C" -> MaterialTheme.colorScheme.surfaceVariant
                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        )
                                        .clickable {
                                            when (key) {
                                                "C" -> if (enteredDigits.isNotEmpty()) enteredDigits = enteredDigits.dropLast(1)
                                                "OK" -> {
                                                    if (enteredDigits.length >= 4) {
                                                        onPinSubmit(enteredDigits)
                                                        enteredDigits = ""
                                                    }
                                                }
                                                else -> {
                                                    if (enteredDigits.length < 6) {
                                                        enteredDigits += key
                                                        if (enteredDigits.length == 4) {
                                                            onPinSubmit(enteredDigits)
                                                            enteredDigits = ""
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (key == "OK") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text("Anuluj i wróć")
                }
            }
        }
    }
}
