package com.familycontrol.app

import com.familycontrol.app.domain.PinSecurityManager
import org.junit.Assert.*
import org.junit.Test

class PinSecurityManagerTest {

    @Test
    fun `test valid pin authorizes correctly`() {
        val manager = PinSecurityManager()
        val result = manager.verifyPin("1234")
        assertTrue(result.isSuccess)
        assertFalse(result.isLockedOut)
        assertEquals(0, result.failedAttempts)
    }

    @Test
    fun `test invalid pin triggers lockout after 5 attempts`() {
        var currentTime = 1_000_000L
        val manager = PinSecurityManager(timeProvider = { currentTime })

        // 4 błędne próby - jeszcze brak blokady czasowej
        for (i in 1..4) {
            val res = manager.verifyPin("9999")
            assertFalse(res.isSuccess)
            assertFalse(res.isLockedOut)
            assertEquals(i, res.failedAttempts)
        }

        // 5 błędna próba - blokada na 60 sekund
        val lockResult = manager.verifyPin("9999")
        assertFalse(lockResult.isSuccess)
        assertTrue(lockResult.isLockedOut)
        assertTrue(manager.isCurrentlyLockedOut())
        assertEquals(60, lockResult.remainingLockoutSeconds)

        // Próba wpisania nawet poprawnego PIN w trakcie blokady zostaje odrzucona
        currentTime += 30_000L // Minęło 30 sekund (pozostało 30)
        val duringLock = manager.verifyPin("1234")
        assertTrue(duringLock.isLockedOut)
        assertFalse(duringLock.isSuccess)

        // Upływ pełnych 60 sekund odblokowuje możliwość autoryzacji
        currentTime += 31_000L
        val afterLock = manager.verifyPin("1234")
        assertTrue(afterLock.isSuccess)
        assertFalse(afterLock.isLockedOut)
    }

    @Test
    fun `test emergency recovery code resets lockout`() {
        var currentTime = 1_000_000L
        val manager = PinSecurityManager(timeProvider = { currentTime })

        for (i in 1..5) {
            manager.verifyPin("0000")
        }
        assertTrue(manager.isCurrentlyLockedOut())

        // Użycie kodu ratunkowego
        val recoverySuccess = manager.verifyRecoveryCode("88990011")
        assertTrue(recoverySuccess)
        assertFalse(manager.isCurrentlyLockedOut())
    }
}
