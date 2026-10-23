package com.familycontrol.app.domain

import com.familycontrol.app.model.ChildPolicy
import com.familycontrol.app.model.SessionCheckpoint
import com.familycontrol.app.model.SessionState
import java.util.Calendar

/**
 * Silnik odmierzania czasu sesji i limitów dziennych.
 * Odporny na manipulacje zegarem systemowym dzięki wykorzystaniu czasu monotonicznego.
 */
class SessionTimerEngine(
    private var policy: ChildPolicy = ChildPolicy(),
    private val monotonicClock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    private val onCheckpointSave: (SessionCheckpoint) -> Unit = {}
) {
    private var state: SessionState = SessionState.ACTIVE

    // Skonsumowane sekundy w bieżącym dniu
    private var consumedSecondsToday: Long = 0L

    // Czas trwania bieżącej sesji w sekundach
    private var sessionElapsedSeconds: Long = 0L

    // Ostatni punkt pomiarowy monotoniczny
    private var lastMonotonicMark: Long = 0L

    // Znacznik monotoniczny ostatniego zapisu checkpointu
    private var lastCheckpointMark: Long = 0L

    // Czy licznik sesji aktualnie bije
    private var isSessionActive: Boolean = false

    // Wykryta anomalia zegara
    private var isTamperFlagged: Boolean = false

    // Ostatni znany dzień w formacie YYYYMMDD
    private var currentDayCode: Int = calculateDayCode(wallClock())

    companion object {
        const val CHECKPOINT_INTERVAL_MILLIS = 30_000L // Zapis co 30 sekund
    }

    /**
     * Inicjalizacja stanu z trwałego magazynu (np. po restarcie urządzenia).
     */
    fun restoreFromCheckpoint(checkpoint: SessionCheckpoint) {
        val nowDayCode = calculateDayCode(wallClock())
        if (nowDayCode == checkpoint.epochDayNumber.toInt()) {
            consumedSecondsToday = checkpoint.consumedSecondsToday
            sessionElapsedSeconds = checkpoint.sessionElapsedSeconds
        } else {
            // Nowy dzień - zerujemy zużycie dzienne i sesyjne
            consumedSecondsToday = 0L
            sessionElapsedSeconds = 0L
            currentDayCode = nowDayCode
        }
        isTamperFlagged = checkpoint.isTamperSuspected
        lastMonotonicMark = monotonicClock()
        lastCheckpointMark = lastMonotonicMark
        evaluateState()
    }

    /**
     * Uruchomienie lub wznowienie sesji dziecka (np. onResume launchera).
     */
    fun startOrResumeSession() {
        val remaining = getRemainingSeconds()
        if (remaining <= 0L) {
            state = SessionState.TIME_EXPIRED
            isSessionActive = false
            return
        }

        val nowMonotonic = monotonicClock()
        lastMonotonicMark = nowMonotonic
        lastCheckpointMark = nowMonotonic
        isSessionActive = true
        evaluateState()
    }

    /**
     * Wstrzymanie naliczania (np. onPause launchera przy przejściu rodzica).
     */
    fun pauseSession() {
        if (!isSessionActive) return
        tick() // dopisz zaległy czas
        isSessionActive = false
        triggerCheckpoint()
    }

    /**
     * Główny cykl aktualizacji zegara (wywoływany co 1 sekundę przez coroutine lub timer).
     */
    fun tick(): SessionState {
        if (!isSessionActive || state == SessionState.TIME_EXPIRED || state == SessionState.PARENT_MODE) {
            return state
        }

        val nowMonotonic = monotonicClock()
        val deltaMillis = nowMonotonic - lastMonotonicMark

        if (deltaMillis >= 1000L) {
            val deltaSeconds = deltaMillis / 1000L
            consumedSecondsToday += deltaSeconds
            sessionElapsedSeconds += deltaSeconds
            lastMonotonicMark += deltaSeconds * 1000L

            // Sprawdzenie zmiany dnia
            val today = calculateDayCode(wallClock())
            if (today != currentDayCode) {
                // Jeśli zmiana dnia nastąpiła naturalnie, resetujemy limit
                consumedSecondsToday = 0L
                sessionElapsedSeconds = 0L
                currentDayCode = today
            }

            // Sprawdzenie czy minął interwał zapisu punktu kontrolnego
            if (nowMonotonic - lastCheckpointMark >= CHECKPOINT_INTERVAL_MILLIS) {
                triggerCheckpoint()
                lastCheckpointMark = nowMonotonic
            }

            evaluateState()
        }

        return state
    }

    /**
     * Zwraca całkowity limit w sekundach dla dzisiejszego dnia.
     */
    fun getTodayDailyLimitSeconds(): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = wallClock() }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
        val limitMinutes = if (isWeekend) policy.dailyLimitMinutesWeekend else policy.dailyLimitMinutesWeekday
        return limitMinutes * 60L
    }

    fun getSessionLimitSeconds(): Long = policy.sessionLimitMinutes * 60L

    /**
     * Zwraca pozostały czas (mniejszy z limitu dziennego i limitu sesji).
     */
    fun getRemainingSeconds(): Long {
        val remainingDaily = (getTodayDailyLimitSeconds() - consumedSecondsToday).coerceAtLeast(0L)
        val remainingSession = (getSessionLimitSeconds() - sessionElapsedSeconds).coerceAtLeast(0L)
        return minOf(remainingDaily, remainingSession)
    }

    fun getCurrentState(): SessionState = state

    fun getConsumedSecondsToday(): Long = consumedSecondsToday

    fun getSessionElapsedSeconds(): Long = sessionElapsedSeconds

    fun isTamperDetected(): Boolean = isTamperFlagged

    fun updatePolicy(newPolicy: ChildPolicy) {
        policy = newPolicy
        evaluateState()
    }

    fun unlockParentMode() {
        pauseSession()
        state = SessionState.PARENT_MODE
    }

    fun exitParentModeToKids() {
        state = SessionState.ACTIVE
        startOrResumeSession()
    }

    /**
     * Przyznanie dodatkowego czasu przez rodzica (np. +15 minut).
     */
    fun grantBonusMinutes(minutes: Int) {
        val bonusSeconds = minutes * 60L
        consumedSecondsToday = (consumedSecondsToday - bonusSeconds).coerceAtLeast(0L)
        sessionElapsedSeconds = (sessionElapsedSeconds - bonusSeconds).coerceAtLeast(0L)
        lastMonotonicMark = monotonicClock()
        isSessionActive = true
        evaluateState()
        triggerCheckpoint()
    }

    private fun evaluateState() {
        val remaining = getRemainingSeconds()
        val warningSec = policy.warningThresholdMinutes * 60L

        state = when {
            state == SessionState.PARENT_MODE -> SessionState.PARENT_MODE
            remaining <= 0L -> {
                isSessionActive = false
                triggerCheckpoint()
                SessionState.TIME_EXPIRED
            }
            remaining <= warningSec -> {
                isSessionActive = true
                SessionState.WARNING
            }
            else -> {
                isSessionActive = true
                SessionState.ACTIVE
            }
        }
    }

    private fun triggerCheckpoint() {
        val checkpoint = SessionCheckpoint(
            consumedSecondsToday = consumedSecondsToday,
            sessionElapsedSeconds = sessionElapsedSeconds,
            lastBootElapsedRealtime = monotonicClock(),
            epochDayNumber = currentDayCode.toLong(),
            isTamperSuspected = isTamperFlagged,
            lastSavedTimestamp = wallClock()
        )
        onCheckpointSave(checkpoint)
    }

    private fun calculateDayCode(epochMillis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return year * 10000 + month * 100 + day
    }
}
