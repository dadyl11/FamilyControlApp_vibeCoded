package com.familycontrol.app.data

import com.familycontrol.app.model.AllowedApp
import com.familycontrol.app.model.AuditEvent
import com.familycontrol.app.model.ChildPolicy
import com.familycontrol.app.model.SessionCheckpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interfejs lokalnego repozytorium danych (Room / DataStore).
 * Zapewnia separację domeny od mechanizmów składowania.
 */
interface LocalRepository {
    val policyFlow: StateFlow<ChildPolicy>
    val checkpointFlow: StateFlow<SessionCheckpoint>
    val allowedAppsFlow: StateFlow<List<AllowedApp>>
    val auditEventsFlow: StateFlow<List<AuditEvent>>

    suspend fun saveCheckpoint(checkpoint: SessionCheckpoint)
    suspend fun updatePolicy(policy: ChildPolicy)
    suspend fun setAppAllowed(packageName: String, isAllowed: Boolean)
    suspend fun logAuditEvent(eventType: String, details: String)
}

/**
 * Domyślna implementacja repozytorium z pamięcią podręczną.
 */
class InMemoryLocalRepository : LocalRepository {

    private val _policyFlow = MutableStateFlow(ChildPolicy())
    override val policyFlow: StateFlow<ChildPolicy> = _policyFlow.asStateFlow()

    private val _checkpointFlow = MutableStateFlow(SessionCheckpoint())
    override val checkpointFlow: StateFlow<SessionCheckpoint> = _checkpointFlow.asStateFlow()

    private val defaultApps = listOf(
        AllowedApp("com.google.android.apps.youtube.kids", "YouTube Kids", "Wideo i Muzyka", true),
        AllowedApp("org.scratchjr.android", "ScratchJr", "Programowanie", true),
        AllowedApp("com.duolingo", "Duolingo", "Języki obce", true),
        AllowedApp("org.khankids.android", "Khan Academy Kids", "Edukacja ogólna", true),
        AllowedApp("com.brak.drawing", "Szkicownik dla dzieci", "Kreatywność", true),
        AllowedApp("com.android.calculator2", "Kalkulator", "Narzędzia", true)
    )

    private val _allowedAppsFlow = MutableStateFlow(defaultApps)
    override val allowedAppsFlow: StateFlow<List<AllowedApp>> = _allowedAppsFlow.asStateFlow()

    private val _auditEventsFlow = MutableStateFlow<List<AuditEvent>>(emptyList())
    override val auditEventsFlow: StateFlow<List<AuditEvent>> = _auditEventsFlow.asStateFlow()

    override suspend fun saveCheckpoint(checkpoint: SessionCheckpoint) {
        _checkpointFlow.value = checkpoint
    }

    override suspend fun updatePolicy(policy: ChildPolicy) {
        _policyFlow.value = policy
    }

    override suspend fun setAppAllowed(packageName: String, isAllowed: Boolean) {
        val current = _allowedAppsFlow.value
        _allowedAppsFlow.value = current.map {
            if (it.packageName == packageName) it.copy(isEnabled = isAllowed) else it
        }
        val enabledPackages = _allowedAppsFlow.value.filter { it.isEnabled }.map { it.packageName }.toSet()
        _policyFlow.value = _policyFlow.value.copy(allowedPackages = enabledPackages)
    }

    override suspend fun logAuditEvent(eventType: String, details: String) {
        val event = AuditEvent(eventType = eventType, details = details)
        val current = _auditEventsFlow.value.toMutableList()
        current.add(0, event)
        if (current.size > 100) current.removeAt(current.lastIndex)
        _auditEventsFlow.value = current
    }
}
