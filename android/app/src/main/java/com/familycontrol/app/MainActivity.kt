package com.familycontrol.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.familycontrol.app.data.InMemoryLocalRepository
import com.familycontrol.app.domain.PinSecurityManager
import com.familycontrol.app.domain.SessionTimerEngine
import com.familycontrol.app.model.AllowedApp
import com.familycontrol.app.model.SessionState
import com.familycontrol.app.service.SessionMonitorService
import com.familycontrol.app.ui.KidsHomeScreen
import com.familycontrol.app.ui.ParentConfigScreen
import com.familycontrol.app.ui.PinPadDialog
import com.familycontrol.app.ui.TimeExpiredScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val repository = InMemoryLocalRepository()
    private val pinSecurityManager = PinSecurityManager()
    private lateinit var timerEngine: SessionTimerEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        timerEngine = SessionTimerEngine(
            policy = repository.policyFlow.value,
            onCheckpointSave = { checkpoint ->
                lifecycleScope.launch {
                    repository.saveCheckpoint(checkpoint)
                }
            }
        )

        // Przechwycenie przycisku Wstecz (Back), aby uniemożliwić zamknięcie launchera przez dziecko
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (timerEngine.getCurrentState() == SessionState.PARENT_MODE) {
                    timerEngine.exitParentModeToKids()
                }
            }
        })

        // Skanowanie rzeczywistych aplikacji zainstalowanych na telefonie/tablecie
        lifecycleScope.launch(Dispatchers.Default) {
            scanAndSyncDeviceApps()
        }

        // Główna pętla zegarowa
        lifecycleScope.launch {
            while (isActive) {
                delay(1000L)
                val state = timerEngine.tick()
                val remaining = timerEngine.getRemainingSeconds()
                if (state != SessionState.PARENT_MODE) {
                    SessionMonitorService.start(this@MainActivity, remaining)
                }
            }
        }

        setContent {
            val policy by repository.policyFlow.collectAsState()
            val allowedApps by repository.allowedAppsFlow.collectAsState()
            val auditEvents by repository.auditEventsFlow.collectAsState()

            var showPinDialog by remember { mutableStateOf(false) }
            var pinErrorMessage by remember { mutableStateOf<String?>(null) }
            var currentEngineState by remember { mutableStateOf(timerEngine.getCurrentState()) }
            var remainingSec by remember { mutableStateOf(timerEngine.getRemainingSeconds()) }
            var isLockedOut by remember { mutableStateOf(pinSecurityManager.isCurrentlyLockedOut()) }
            var remainingLockout by remember { mutableStateOf(pinSecurityManager.getRemainingLockoutSeconds()) }
            var parentInitialTab by remember { mutableIntStateOf(0) }

            // Odświeżanie stanu UI
            LaunchedEffect(Unit) {
                while (isActive) {
                    delay(500L)
                    currentEngineState = timerEngine.getCurrentState()
                    remainingSec = timerEngine.getRemainingSeconds()
                    isLockedOut = pinSecurityManager.isCurrentlyLockedOut()
                    remainingLockout = pinSecurityManager.getRemainingLockoutSeconds()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentEngineState) {
                    SessionState.ACTIVE, SessionState.WARNING -> {
                        KidsHomeScreen(
                            remainingSeconds = remainingSec,
                            sessionState = currentEngineState,
                            allowedApps = allowedApps,
                            onAppClick = { app -> launchChildApp(app) },
                            onRequestParentMode = {
                                parentInitialTab = 0
                                pinErrorMessage = null
                                showPinDialog = true
                            },
                            onRequestAddApp = {
                                parentInitialTab = 1 // Przejdź od razu do zakładki "Aplikacje"
                                pinErrorMessage = null
                                showPinDialog = true
                            }
                        )
                    }
                    SessionState.TIME_EXPIRED -> {
                        TimeExpiredScreen(
                            onRequestParentMode = {
                                parentInitialTab = 0
                                pinErrorMessage = null
                                showPinDialog = true
                            }
                        )
                    }
                    SessionState.PARENT_MODE -> {
                        ParentConfigScreen(
                            policy = policy,
                            allowedApps = allowedApps,
                            auditEvents = auditEvents,
                            consumedSecondsToday = timerEngine.getConsumedSecondsToday(),
                            initialTab = parentInitialTab,
                            onUpdatePolicy = { newPolicy ->
                                lifecycleScope.launch {
                                    repository.updatePolicy(newPolicy)
                                    timerEngine.updatePolicy(newPolicy)
                                }
                            },
                            onToggleApp = { pkg, isAllowed ->
                                lifecycleScope.launch {
                                    repository.setAppAllowed(pkg, isAllowed)
                                    repository.logAuditEvent(
                                        "APP_TOGGLE",
                                        "Zmieniono uprawnienie dla $pkg: $isAllowed"
                                    )
                                }
                            },
                            onGrantBonusMinutes = { mins ->
                                timerEngine.grantBonusMinutes(mins)
                                lifecycleScope.launch {
                                    repository.logAuditEvent("BONUS_TIME", "Rodzic przyznał +$mins minut.")
                                }
                            },
                            onReturnToKidsMode = {
                                timerEngine.exitParentModeToKids()
                            },
                            onExitToAndroidSystemHome = {
                                exitToDefaultAndroidHome()
                            }
                        )
                    }
                }

                if (showPinDialog) {
                    PinPadDialog(
                        isLockedOut = isLockedOut,
                        remainingLockoutSeconds = remainingLockout,
                        errorMessage = pinErrorMessage,
                        onPinSubmit = { pin ->
                            val result = pinSecurityManager.verifyPin(pin)
                            if (result.isSuccess) {
                                showPinDialog = false
                                timerEngine.unlockParentMode()
                                lifecycleScope.launch {
                                    repository.logAuditEvent("PIN_SUCCESS", "Autoryzacja rodzica udana.")
                                }
                            } else {
                                pinErrorMessage = result.message
                                lifecycleScope.launch {
                                    repository.logAuditEvent("PIN_FAILED", "Błędna próba PIN (${result.failedAttempts}/5).")
                                }
                            }
                        },
                        onDismiss = { showPinDialog = false }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        timerEngine.startOrResumeSession()
    }

    override fun onPause() {
        super.onPause()
        timerEngine.pauseSession()
    }

    private suspend fun scanAndSyncDeviceApps() {
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val pkgAppsList = packageManager.queryIntentActivities(mainIntent, 0)
            val detected = mutableListOf<AllowedApp>()

            for (resolveInfo in pkgAppsList) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == packageName) continue // Nie pokazuj samego siebie

                val appLabel = resolveInfo.loadLabel(packageManager).toString()
                detected.add(
                    AllowedApp(
                        packageName = pkgName,
                        displayName = appLabel,
                        category = "Zainstalowana na telefonie",
                        isEnabled = false // Domyślnie zablokowana do zatwierdzenia przez rodzica
                    )
                )
            }

            // Posortuj alfabetycznie
            detected.sortBy { it.displayName }

            withContext(Dispatchers.Main) {
                repository.syncDetectedInstalledApps(detected)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchChildApp(app: AllowedApp) {
        if (!app.isEnabled) {
            Toast.makeText(this, "Aplikacja jest zablokowana przez rodzica", Toast.LENGTH_SHORT).show()
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Aplikacja '${app.displayName}' nie jest zainstalowana na tym urządzeniu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exitToDefaultAndroidHome() {
        val selectorIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(selectorIntent)
    }
}
