package com.familycontrol.app

import com.familycontrol.app.domain.SessionTimerEngine
import com.familycontrol.app.model.ChildPolicy
import com.familycontrol.app.model.SessionCheckpoint
import com.familycontrol.app.model.SessionState
import org.junit.Assert.*
import org.junit.Test

class SessionTimerEngineTest {

    @Test
    fun `test monotonic countdown reduces remaining time regardless of wall clock`() {
        var monotonicTime = 100_000L
        var wallTime = 1700000000000L

        val policy = ChildPolicy(
            dailyLimitMinutesWeekday = 30,
            sessionLimitMinutes = 30,
            warningThresholdMinutes = 5
        )

        val engine = SessionTimerEngine(
            policy = policy,
            monotonicClock = { monotonicTime },
            wallClock = { wallTime }
        )

        engine.startOrResumeSession()
        assertEquals(SessionState.ACTIVE, engine.getCurrentState())
        val initialRemaining = engine.getRemainingSeconds()
        assertEquals(30 * 60L, initialRemaining)

        // Symulacja upływu 10 sekund monotonicznych
        monotonicTime += 10_000L
        engine.tick()

        assertEquals(30 * 60L - 10L, engine.getRemainingSeconds())

        // Dziecko cofa zegar czasu rzeczywistego o 2 godziny
        wallTime -= 2 * 3600 * 1000L

        // Kolejne 5 sekund monotonicznych
        monotonicTime += 5_000L
        engine.tick()

        // Czas nadal ubywa prawidłowo, cofnięcie zegara nic nie pomogło!
        assertEquals(30 * 60L - 15L, engine.getRemainingSeconds())
        assertEquals(SessionState.ACTIVE, engine.getCurrentState())
    }

    @Test
    fun `test warning state triggers when remaining time drops below threshold`() {
        var monotonicTime = 50_000L

        // Limit sesji 6 minut, próg ostrzeżenia 5 minut
        val policy = ChildPolicy(
            dailyLimitMinutesWeekday = 60,
            sessionLimitMinutes = 6,
            warningThresholdMinutes = 5
        )

        val engine = SessionTimerEngine(
            policy = policy,
            monotonicClock = { monotonicTime }
        )

        engine.startOrResumeSession()
        assertEquals(SessionState.ACTIVE, engine.getCurrentState())

        // Upływ 70 sekund -> zostało 4 min 50s (mniej niż 5 min próg)
        monotonicTime += 70_000L
        engine.tick()

        assertEquals(SessionState.WARNING, engine.getCurrentState())
    }

    @Test
    fun `test time expired state triggers when limit is exhausted`() {
        var monotonicTime = 10_000L

        val policy = ChildPolicy(
            dailyLimitMinutesWeekday = 60,
            sessionLimitMinutes = 2,
            warningThresholdMinutes = 1
        )

        val engine = SessionTimerEngine(
            policy = policy,
            monotonicClock = { monotonicTime }
        )

        engine.startOrResumeSession()

        // Upływ 130 sekund (więcej niż 2 min = 120s)
        monotonicTime += 130_000L
        engine.tick()

        assertEquals(SessionState.TIME_EXPIRED, engine.getCurrentState())
        assertEquals(0L, engine.getRemainingSeconds())
    }

    @Test
    fun `test checkpoint restore survives simulated reboot`() {
        var monotonicTime = 100_000L
        val savedCheckpoints = mutableListOf<SessionCheckpoint>()

        val engine1 = SessionTimerEngine(
            policy = ChildPolicy(dailyLimitMinutesWeekday = 60, sessionLimitMinutes = 60),
            monotonicClock = { monotonicTime },
            onCheckpointSave = { savedCheckpoints.add(it) }
        )

        engine1.startOrResumeSession()
        monotonicTime += 35_000L // Minął interwał zapisu (30s)
        engine1.tick()
        engine1.pauseSession()

        assertTrue(savedCheckpoints.isNotEmpty())
        val lastCheckpoint = savedCheckpoints.last()
        assertTrue(lastCheckpoint.consumedSecondsToday >= 35L)

        // Symulacja restartu tabletu (czas monotoniczny startuje od nowa od małej wartości np. 5000ms)
        var rebootMonotonicTime = 5_000L
        val engine2 = SessionTimerEngine(
            policy = ChildPolicy(dailyLimitMinutesWeekday = 60, sessionLimitMinutes = 60),
            monotonicClock = { rebootMonotonicTime }
        )

        engine2.restoreFromCheckpoint(lastCheckpoint)
        assertEquals(lastCheckpoint.consumedSecondsToday, engine2.getConsumedSecondsToday())
    }
}
