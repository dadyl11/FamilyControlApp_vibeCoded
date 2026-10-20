package com.familycontrol.app.model

/**
 * Stany sesji w aplikacji Family Control.
 */
enum class SessionState {
    /** Tryb aktywny: dziecko ma dostępny czas i korzysta z dozwolonych aplikacji. */
    ACTIVE,
    /** Ostrzeżenie: pozostało mniej niż próg ostrzeżenia (np. 5 minut). */
    WARNING,
    /** Limit czasu wyczerpany: zablokowany dostęp do aplikacji, wymagany PIN rodzica. */
    TIME_EXPIRED,
    /** Tryb rodzica: odblokowany pełny dostęp i panel administracyjny. */
    PARENT_MODE
}

/**
 * Informacje o dozwolonej aplikacji dla dziecka.
 */
data class AllowedApp(
    val packageName: String,
    val displayName: String,
    val category: String = "Edukacja",
    val isEnabled: Boolean = true
)

/**
 * Polityka limitów czasowych dla danego profilu dziecka.
 */
data class ChildPolicy(
    val dailyLimitMinutesWeekday: Int = 60,
    val dailyLimitMinutesWeekend: Int = 120,
    val sessionLimitMinutes: Int = 30,
    val warningThresholdMinutes: Int = 5,
    val allowedPackages: Set<String> = setOf(
        "com.google.android.apps.youtube.kids",
        "org.scratchjr.android",
        "com.duolingo",
        "org.khankids.android"
    )
)

/**
 * Trwały punkt kontrolny zapisu stanu zużycia czasu.
 * Odporny na restarty urządzenia i manipulacje zegarem.
 */
data class SessionCheckpoint(
    val consumedSecondsToday: Long = 0,
    val sessionElapsedSeconds: Long = 0,
    val lastBootElapsedRealtime: Long = 0,
    val epochDayNumber: Long = 0,
    val isTamperSuspected: Boolean = false,
    val lastSavedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Stan weryfikatora kodu PIN rodzica (ochrona brute-force).
 */
data class PinVerificationResult(
    val isSuccess: Boolean,
    val isLockedOut: Boolean = false,
    val remainingLockoutSeconds: Int = 0,
    val failedAttempts: Int = 0,
    val message: String = ""
)

/**
 * Zdarzenie audytu bezpieczeństwa.
 */
data class AuditEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val details: String
)
