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
import androidx.compose.ui.text.style.TextOverflow
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
    initialTab: Int = 0,
    onUpdatePolicy: (ChildPolicy) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onGrantBonusMinutes: (Int) -> Unit,
    onReturnToKidsMode: () -> Unit,
    onExitToAndroidSystemHome: () -> Unit
) {
    var weekdayMinutes by remember(policy) { mutableFloatStateOf(policy.dailyLimitMinutesWeekday.toFloat()) }
    var weekendMinutes by remember(policy) { mutableFloatStateOf(policy.dailyLimitMinutesWeekend.toFloat()) }
    var sessionMinutes by remember(policy) { mutableFloatStateOf(policy.sessionLimitMinutes.toFloat()) }

    var selectedTab by remember { mutableIntStateOf(initialTab) } // 0: Limity, 1: Aplikacje, 2: Audyt
    var appSearchQuery by remember { mutableStateOf("") }

    val activeCount = allowedApps.count { it.isEnabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        // Nagłówek panelu rodzica - responsywny pionowo i poziomo
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Panel Rodzica",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onReturnToKidsMode,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChildCare,
                            contentDescription = "Kids",
                            modifier = Modifier.size(15.dp).padding(end = 4.dp)
                        )
                        Text("Kącik Dziecka", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onExitToAndroidSystemHome,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Wyjdź",
                            modifier = Modifier.size(15.dp).padding(end = 4.dp)
                        )
                        Text("Pulpit", fontSize = 12.sp)
                    }
                }
            }

            Text(
                text = "Zarządzanie czasem i dozwolonymi aplikacjami",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Zakładki
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF38BDF8)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Limity", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Aplikacje ($activeCount)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Audyt", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedTab) {
            0 -> {
                // Konfiguracja limitów
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        item {
                            Text(
                                text = "Dzisiejsze zużycie czasu: ${(consumedSecondsToday / 60)} min",
                                color = Color(0xFF38BDF8),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Szybkie przedłużenie czasu:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onGrantBonusMinutes(15) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("+15 min", fontSize = 12.sp) }
                                OutlinedButton(
                                    onClick = { onGrantBonusMinutes(30) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("+30 min", fontSize = 12.sp) }
                                OutlinedButton(
                                    onClick = { onGrantBonusMinutes(60) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("+1h", fontSize = 12.sp) }
                            }

                            Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFF334155))

                            Text("Limit dzienny pon-pt: ${weekdayMinutes.toInt()} min", color = Color.White, fontSize = 13.sp)
                            Slider(
                                value = weekdayMinutes,
                                onValueChange = { weekdayMinutes = it },
                                onValueChangeFinished = {
                                    onUpdatePolicy(policy.copy(dailyLimitMinutesWeekday = weekdayMinutes.toInt()))
                                },
                                valueRange = 15f..180f,
                                steps = 10
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Limit dzienny weekend: ${weekendMinutes.toInt()} min", color = Color.White, fontSize = 13.sp)
                            Slider(
                                value = weekendMinutes,
                                onValueChange = { weekendMinutes = it },
                                onValueChangeFinished = {
                                    onUpdatePolicy(policy.copy(dailyLimitMinutesWeekend = weekendMinutes.toInt()))
                                },
                                valueRange = 30f..300f,
                                steps = 8
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Maksymalna pojedyncza sesja: ${sessionMinutes.toInt()} min", color = Color.White, fontSize = 13.sp)
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
            }
            1 -> {
                // Lista aplikacji z wyszukiwarką
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Wyszukiwarka
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            placeholder = { Text("Szukaj gry lub aplikacji...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val filteredApps = allowedApps.filter {
                            it.displayName.contains(appSearchQuery, ignoreCase = true) ||
                            it.packageName.contains(appSearchQuery, ignoreCase = true)
                        }

                        Text(
                            text = "Znaleziono ${filteredApps.size} aplikacji (Aktywne dla dziecka: $activeCount)",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredApps) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.displayName,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Switch(
                                        checked = app.isEnabled,
                                        onCheckedChange = { isChecked -> onToggleApp(app.packageName, isChecked) },
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                Divider(color = Color(0xFF334155))
                            }
                        }
                    }
                }
            }
            2 -> {
                // Dziennik audytu i próby obejścia
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Rejestr zdarzeń i bezpieczeństwa",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (auditEvents.isEmpty()) {
                            Text(
                                text = "Brak zarejestrowanych anomalii.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(auditEvents) { event ->
                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            text = "${event.eventType}: ${event.details}",
                                            color = if (event.eventType.contains("ALERT") || event.eventType.contains("FAILED")) Color(0xFFF43F5E) else Color(0xFFE2E8F0),
                                            fontSize = 12.sp
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
