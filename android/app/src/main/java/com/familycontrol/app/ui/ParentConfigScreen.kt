package com.familycontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familycontrol.app.model.AllowedApp
import com.familycontrol.app.model.AuditEvent
import com.familycontrol.app.model.ChildPolicy

@Composable
fun ParentConfigScreen(
    policy: ChildPolicy,
    allowedApps: List<AllowedApp>,
    auditEvents: List<AuditEvent>,
    consumedSecondsToday: Long,
    onUpdatePolicy: (ChildPolicy) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onGrantBonusMinutes: (Int) -> Unit,
    onReturnToKidsMode: () -> Unit,
    onExitToAndroidSystemHome: () -> Unit
) {
    var weekdayMinutes by remember(policy) { mutableFloatStateOf(policy.dailyLimitMinutesWeekday.toFloat()) }
    var weekendMinutes by remember(policy) { mutableFloatStateOf(policy.dailyLimitMinutesWeekend.toFloat()) }
    var sessionMinutes by remember(policy) { mutableFloatStateOf(policy.sessionLimitMinutes.toFloat()) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Limity, 1: Aplikacje, 2: Audyt i bezpieczeństwo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp)
    ) {
        // Nagłówek panelu rodzica
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Panel Zarządzania Rodzica",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Konfiguracja lokalna offline na współdzielonym tablecie",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onReturnToKidsMode,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChildCare,
                        contentDescription = "Kids",
                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                    )
                    Text("Uruchom Tryb Dziecka")
                }

                OutlinedButton(
                    onClick = onExitToAndroidSystemHome,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Wyjdź",
                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                    )
                    Text("Pulpit Dorosłego")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Zakładki
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF38BDF8)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Limity i Czas", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Dozwolone Aplikacje", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Dziennik Zdarzeń i Bezpieczeństwo", fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (selectedTab) {
            0 -> {
                // Konfiguracja limitów
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Dzisiejsze zużycie: ${(consumedSecondsToday / 60)} min",
                            color = Color(0xFF38BDF8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bonus szybki
                        Text("Szybkie przedłużenie czasu:", color = Color.White, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { onGrantBonusMinutes(15) }) { Text("+15 min") }
                            OutlinedButton(onClick = { onGrantBonusMinutes(30) }) { Text("+30 min") }
                            OutlinedButton(onClick = { onGrantBonusMinutes(60) }) { Text("+1 godzina") }
                        }

                        Divider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFF334155))

                        Text("Limit dzienny w dni powszednie (pon-pt): ${weekdayMinutes.toInt()} min", color = Color.White)
                        Slider(
                            value = weekdayMinutes,
                            onValueChange = { weekdayMinutes = it },
                            onValueChangeFinished = {
                                onUpdatePolicy(policy.copy(dailyLimitMinutesWeekday = weekdayMinutes.toInt()))
                            },
                            valueRange = 15f..180f,
                            steps = 10
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Limit dzienny w weekendy (sob-nd): ${weekendMinutes.toInt()} min", color = Color.White)
                        Slider(
                            value = weekendMinutes,
                            onValueChange = { weekendMinutes = it },
                            onValueChangeFinished = {
                                onUpdatePolicy(policy.copy(dailyLimitMinutesWeekend = weekendMinutes.toInt()))
                            },
                            valueRange = 30f..300f,
                            steps = 8
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Maksymalna pojedyncza sesja: ${sessionMinutes.toInt()} min", color = Color.White)
                        Slider(
                            value = sessionMinutes,
                            onValueChange = { sessionMinutes = it },
                            onValueChangeFinished = {
                                onUpdatePolicy(policy.copy(sessionLimitMinutes = sessionMinutes.toInt()))
                            },
                            valueRange = 10f..120f,
                            steps = 10
                        )
                    }
                }
            }
            1 -> {
                // Lista aplikacji
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        items(allowedApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = app.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(text = app.packageName, color = Color(0xFF64748B), fontSize = 12.sp)
                                }
                                Switch(
                                    checked = app.isEnabled,
                                    onCheckedChange = { isChecked -> onToggleApp(app.packageName, isChecked) }
                                )
                            }
                            Divider(color = Color(0xFF334155))
                        }
                    }
                }
            }
            2 -> {
                // Dziennik audytu i próby obejścia
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Rejestr audytu bezpieczeństwa i testów",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (auditEvents.isEmpty()) {
                            Text(
                                text = "Brak zarejestrowanych anomalii i prób manipulacji.",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(260.dp)) {
                                items(auditEvents) { event ->
                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            text = "${event.eventType}: ${event.details}",
                                            color = if (event.eventType.contains("ALERT")) Color(0xFFF43F5E) else Color(0xFFE2E8F0),
                                            fontSize = 13.sp
                                        )
                                        Divider(color = Color(0xFF334155), modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
