package com.familycontrol.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isDefaultLauncher()) {
            Toast.makeText(
                this,
                "✓ Family Control jest teraz domyślnym ekranem głównym!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
            var isDefaultHome by remember { mutableStateOf(isDefaultLauncher()) }

            // Odświeżanie stanu UI
            LaunchedEffect(Unit) {
                while (isActive) {
                    delay(500L)
                    currentEngineState = timerEngine.getCurrentState()
                    remainingSec = timerEngine.getRemainingSeconds()
                    isLockedOut = pinSecurityManager.isCurrentlyLockedOut()
                    remainingLockout = pinSecurityManager.getRemainingLockoutSeconds()
                    isDefaultHome = isDefaultLauncher()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentEngineState) {
                    SessionState.ACTIVE, SessionState.WARNING -> {
                        KidsHomeScreen(
                            remainingSeconds = remainingSec,
                            sessionState = currentEngineState,
                            allowedApps = allowedApps,
                            isDefaultLauncher = isDefaultHome,
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
                            },
                            onRequestSetDefaultLauncher = {
                                requestSetDefaultLauncher()
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
                            isDefaultLauncher = isDefaultHome,
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
                            },
                            onRequestSetDefaultLauncher = {
                                requestSetDefaultLauncher()
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
        lifecycleScope.launch(Dispatchers.Default) {
            scanAndSyncDeviceApps()
        }
    }

    override fun onPause() {
        super.onPause()
        timerEngine.pauseSession()
    }

    private suspend fun scanAndSyncDeviceApps() {
        try {
            val pm = packageManager
            val detectedMap = mutableMapOf<String, AllowedApp>()

            // 1. Skanowanie aplikacji z filtrem LAUNCHER
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }

            for (resolveInfo in launcherList) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == packageName) continue

                val appLabel = resolveInfo.loadLabel(pm).toString()
                val isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    resolveInfo.activityInfo.applicationInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                } else false

                detectedMap[pkgName] = AllowedApp(
                    packageName = pkgName,
                    displayName = appLabel,
                    category = if (isGame) "Gra" else "Aplikacja",
                    isEnabled = false
                )
            }

            // 2. Dodatkowe sprawdzenie wszystkich zainstalowanych pakietów (dla gier, które nie rejestrują się standardowo)
            val allPackages = pm.getInstalledPackages(0)
            for (pkgInfo in allPackages) {
                val pkgName = pkgInfo.packageName
                if (pkgName == packageName) continue
                if (!detectedMap.containsKey(pkgName)) {
                    val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                    if (launchIntent != null) {
                        val appLabel = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkgName
                        val isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            pkgInfo.applicationInfo?.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                        } else false

                        detectedMap[pkgName] = AllowedApp(
                            packageName = pkgName,
                            displayName = appLabel,
                            category = if (isGame) "Gra" else "Aplikacja",
                            isEnabled = false
                        )
                    }
                }
            }

            val resultList = detectedMap.values.toMutableList()
            resultList.sortBy { it.displayName }

            withContext(Dispatchers.Main) {
                repository.syncDetectedInstalledApps(resultList)
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
        try {
            // Znajdź natywny launcher telefonu rodzica (np. ColorOS / Realme UI, Samsung One UI, Pixel Launcher)
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val homeActivities = packageManager.queryIntentActivities(homeIntent, 0)
            val otherLauncher = homeActivities.firstOrNull { 
                it.activityInfo.packageName != packageName && 
                !it.activityInfo.packageName.contains("resolver", ignoreCase = true)
            }

            if (otherLauncher != null) {
                val targetIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    setClassName(otherLauncher.activityInfo.packageName, otherLauncher.activityInfo.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(targetIntent)
            } else {
                openHomeSettingsDirectly()
            }
        } catch (e: Exception) {
            openHomeSettingsDirectly()
        }
    }

    private fun isDefaultLauncher(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    return true
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val currentPkg = resolveInfo?.activityInfo?.packageName
            currentPkg == packageName
        } catch (e: Exception) {
            false
        }
    }

    private fun requestSetDefaultLauncher() {
        openHomeSettingsDirectly()
    }

    private fun openHomeSettingsDirectly() {
        var launched = false

        // Krok 1: Bezpośrednie otwarcie systemowego ekranu wyboru launchera (ACTION_HOME_SETTINGS)
        // Działa niezawodnie na Realme UI / ColorOS, MIUI/HyperOS, Samsung One UI i Android 10+
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            launched = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Krok 2: Jeśli ACTION_HOME_SETTINGS nie jest dostępne, spróbuj ACTION_MANAGE_DEFAULT_APPS_SETTINGS
        if (!launched) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                launched = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Krok 3: Próba przez RoleManager (standard Android 10+ dla czystego AOSP / Pixeli)
        if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    homeRoleLauncher.launch(intent)
                    launched = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Krok 4: Jeśli żadne ze specjalnych okien nie ruszyło, otwórz ogólne Ustawienia
        if (!launched) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                launched = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Zawsze wyświetlamy czytelny komunikat dla użytkownika z instrukcją
        Toast.makeText(
            this,
            "Wybierz 'Family Control' jako domyślną aplikację ekranu głównego",
            Toast.LENGTH_LONG
        ).show()
    }
}
