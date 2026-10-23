package com.familycontrol.app.domain

import android.content.Context
import com.familycontrol.app.data.InMemoryLocalRepository
import com.familycontrol.app.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Globalny menedżer sesji łączący repozytorium, silnik czasu i weryfikację PIN-u.
 * Zapewnia współdzielenie stanu pomiędzy MainActivity a SessionMonitorService,
 * dzięki czemu licznik działa nieprzerwanie podczas gry dziecka.
 */
object SessionManager {

    val repository = InMemoryLocalRepository()
    val pinSecurityManager = PinSecurityManager()
    lateinit var timerEngine: SessionTimerEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isInitialized = false

    var isActivityResumed: Boolean = false

    private val _currentState = MutableStateFlow(SessionState.ACTIVE)
    val currentState: StateFlow<SessionState> = _currentState.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    fun init(context: Context) {
        if (isInitialized) return

        timerEngine = SessionTimerEngine(
            policy = repository.policyFlow.value,
            onCheckpointSave = { checkpoint ->
                scope.launch {
                    repository.saveCheckpoint(checkpoint)
                }
            }
        )

        // Rozpoczęcie sesji dziecka
        timerEngine.startOrResumeSession()
        _currentState.value = timerEngine.getCurrentState()
        _remainingSeconds.value = timerEngine.getRemainingSeconds()

        // Obserwacja zmian polityki rodzica
        scope.launch {
            repository.policyFlow.collect { newPolicy ->
                timerEngine.updatePolicy(newPolicy)
                _currentState.value = timerEngine.getCurrentState()
                _remainingSeconds.value = timerEngine.getRemainingSeconds()
            }
        }

        isInitialized = true
    }

    /**
     * Odliczanie jednej sekundy czasu ekranowego.
     * Wywoływane przez działający w tle SessionMonitorService.
     */
    fun tick(): SessionState {
        if (!isInitialized) return SessionState.ACTIVE
        val state = timerEngine.tick()
        _currentState.value = state
        _remainingSeconds.value = timerEngine.getRemainingSeconds()
        return state
    }

    fun grantBonusMinutes(minutes: Int) {
        if (!isInitialized) return
        timerEngine.grantBonusMinutes(minutes)
        timerEngine.startOrResumeSession()
        _currentState.value = timerEngine.getCurrentState()
        _remainingSeconds.value = timerEngine.getRemainingSeconds()
    }

    fun unlockParentMode() {
        if (!isInitialized) return
        timerEngine.unlockParentMode()
        _currentState.value = timerEngine.getCurrentState()
    }

    fun exitParentModeToKids() {
        if (!isInitialized) return
        timerEngine.exitParentModeToKids()
        _currentState.value = timerEngine.getCurrentState()
        _remainingSeconds.value = timerEngine.getRemainingSeconds()
    }
}
