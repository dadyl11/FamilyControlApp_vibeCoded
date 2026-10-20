package com.familycontrol.app.domain

import com.familycontrol.app.model.PinVerificationResult
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Bezpieczny zarządca kodu PIN rodzica.
 * Realizuje solenie hasza SHA-256, ochronę przed atakiem brute force (blokada po 5 próbach)
 * oraz weryfikację procedury odzyskiwania.
 */
class PinSecurityManager(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val INITIAL_LOCKOUT_SECONDS = 60
    }

    private var storedSaltHex: String = ""
    private var storedHashHex: String = ""
    private var recoveryCodeHashHex: String = ""

    private var failedAttempts: Int = 0
    private var lockoutUntilTimestamp: Long = 0L

    init {
        // Domyślny PIN demonstracyjny: "1234" z wygenerowaną solą
        setupPin("1234", recoveryCode = "88990011")
    }

    fun setupPin(newPin: String, recoveryCode: String) {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        storedSaltHex = bytesToHex(salt)
        storedHashHex = hashWithSalt(newPin, salt)
        recoveryCodeHashHex = hashSimple(recoveryCode)
        failedAttempts = 0
        lockoutUntilTimestamp = 0L
    }

    fun verifyPin(inputPin: String): PinVerificationResult {
        val now = timeProvider()
        if (now < lockoutUntilTimestamp) {
            val remainingSec = ((lockoutUntilTimestamp - now) / 1000).toInt() + 1
            return PinVerificationResult(
                isSuccess = false,
                isLockedOut = true,
                remainingLockoutSeconds = remainingSec,
                failedAttempts = failedAttempts,
                message = "Zbyt wiele nieudanych prób. Klawiatura zablokowana na ${remainingSec}s."
            )
        }

        val salt = hexToBytes(storedSaltHex)
        val computedHash = hashWithSalt(inputPin, salt)

        if (computedHash == storedHashHex) {
            failedAttempts = 0
            lockoutUntilTimestamp = 0L
            return PinVerificationResult(
                isSuccess = true,
                isLockedOut = false,
                remainingLockoutSeconds = 0,
                failedAttempts = 0,
                message = "PIN poprawny."
            )
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                // Wykładniczy czas blokady
                val multiplier = 1 shl (failedAttempts - MAX_FAILED_ATTEMPTS).coerceAtMost(4)
                val lockoutSec = INITIAL_LOCKOUT_SECONDS * multiplier
                lockoutUntilTimestamp = now + (lockoutSec * 1000L)
                return PinVerificationResult(
                    isSuccess = false,
                    isLockedOut = true,
                    remainingLockoutSeconds = lockoutSec,
                    failedAttempts = failedAttempts,
                    message = "Zbyt wiele prób! Blokada na ${lockoutSec}s."
                )
            }
            return PinVerificationResult(
                isSuccess = false,
                isLockedOut = false,
                remainingLockoutSeconds = 0,
                failedAttempts = failedAttempts,
                message = "Niepoprawny PIN. Pozostało prób: ${MAX_FAILED_ATTEMPTS - failedAttempts}."
            )
        }
    }

    fun verifyRecoveryCode(code: String): Boolean {
        val computed = hashSimple(code)
        if (computed == recoveryCodeHashHex) {
            failedAttempts = 0
            lockoutUntilTimestamp = 0L
            return true
        }
        return false
    }

    fun isCurrentlyLockedOut(): Boolean {
        return timeProvider() < lockoutUntilTimestamp
    }

    fun getRemainingLockoutSeconds(): Int {
        val now = timeProvider()
        return if (now < lockoutUntilTimestamp) {
            ((lockoutUntilTimestamp - now) / 1000).toInt() + 1
        } else {
            0
        }
    }

    private fun hashWithSalt(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    private fun hashSimple(value: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(value.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
