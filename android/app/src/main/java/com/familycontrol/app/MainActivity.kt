package com.familycontrol.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
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
import com.familycontrol.app.domain.SessionManager
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

        // Inicjalizacja globalnego menedżera sesji
        SessionManager.init(applicationContext)

        // Uruchomienie ciągłego serwisu w tle, który liczy czas podczas grania w gry
        SessionMonitorService.start(this)

        // Przechwycenie przycisku Wstecz (Back), aby uniemożliwić zamknięcie launchera i ekranu blokady
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (SessionManager.currentState.value) {
                    SessionState.PARENT_MODE -> {
                        SessionManager.exitParentModeToKids()
                    }
                    SessionState.TIME_EXPIRED -> {
                        // Klawisz Wstecz zablokowany! Nie pozwala ominąć ekranu końca czasu
                    }
                    else -> {
                        // W trybie dziecka przycisk Wstecz nie pozwala opuścić aplikacji
                    }
                }
            }
        })

        // Skanowanie rzeczywistych aplikacji zainstalowanych na telefonie/tablecie
        lifecycleScope.launch(Dispatchers.Default) {
            scanAndSyncDeviceApps()
        }

        setContent {
            val policy by SessionManager.repository.policyFlow.collectAsState()
            val allowedApps by SessionManager.repository.allowedAppsFlow.collectAsState()
            val auditEvents by SessionManager.repository.auditEventsFlow.collectAsState()
            val currentEngineState by SessionManager.currentState.collectAsState()
            val remainingSec by SessionManager.remainingSeconds.collectAsState()

            var showPinDialog by remember { mutableStateOf(false) }
            var pinErrorMessage by remember { mutableStateOf<String?>(null) }
            var isLockedOut by remember { mutableStateOf(SessionManager.pinSecurityManager.isCurrentlyLockedOut()) }
            var remainingLockout by remember { mutableStateOf(SessionManager.pinSecurityManager.getRemainingLockoutSeconds()) }
            var parentInitialTab by remember { mutableIntStateOf(0) }
            var isDefaultHome by remember { mutableStateOf(isDefaultLauncher()) }
            var isOverlayGranted by remember { mutableStateOf(canDrawOverlays()) }

            // Okresowe odświeżanie stanu systemowych uprawnień
            LaunchedEffect(Unit) {
                while (isActive) {
                    delay(1000L)
                    isLockedOut = SessionManager.pinSecurityManager.isCurrentlyLockedOut()
                    remainingLockout = SessionManager.pinSecurityManager.getRemainingLockoutSeconds()
                    isDefaultHome = isDefaultLauncher()
                    isOverlayGranted = canDrawOverlays()
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
                            consumedSecondsToday = SessionManager.getConsumedSecondsToday(),
                            initialTab = parentInitialTab,
                            isDefaultLauncher = isDefaultHome,
                            isOverlayGranted = isOverlayGranted,
                            onUpdatePolicy = { newPolicy ->
                                lifecycleScope.launch {
                                    SessionManager.repository.updatePolicy(newPolicy)
                                }
                            },
                            onToggleApp = { pkg, isAllowed ->
                                lifecycleScope.launch {
                                    SessionManager.repository.setAppAllowed(pkg, isAllowed)
                                    SessionManager.repository.logAuditEvent(
                                        "APP_TOGGLE",
                                        "Zmieniono uprawnienie dla $pkg: $isAllowed"
                                    )
                                }
                            },
                            onGrantBonusMinutes = { mins ->
                                SessionManager.grantBonusMinutes(mins)
                                lifecycleScope.launch {
                                    SessionManager.repository.logAuditEvent("BONUS_TIME", "Rodzic przyznał +$mins minut.")
                                }
                            },
                            onReturnToKidsMode = {
                                SessionManager.exitParentModeToKids()
                            },
                            onExitToAndroidSystemHome = {
                                exitToDefaultAndroidHome()
                            },
                            onRequestSetDefaultLauncher = {
                                requestSetDefaultLauncher()
                            },
                            onRequestOverlayPermission = {
                                requestOverlayPermission()
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
                            val result = SessionManager.pinSecurityManager.verifyPin(pin)
                            if (result.isSuccess) {
                                showPinDialog = false
                                SessionManager.unlockParentMode()
                                lifecycleScope.launch {
                                    SessionManager.repository.logAuditEvent("PIN_SUCCESS", "Autoryzacja rodzica udana.")
                                }
                            } else {
                                pinErrorMessage = result.message
                                lifecycleScope.launch {
                                    SessionManager.repository.logAuditEvent("PIN_FAILED", "Błędna próba PIN (${result.failedAttempts}/5).")
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
        SessionManager.isActivityResumed = true

        // Jeśli czas wygasł, wymuś podtrzymanie ekranu w celu zaprezentowania blokady
        if (SessionManager.currentState.value == SessionState.TIME_EXPIRED) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        lifecycleScope.launch(Dispatchers.Default) {
            scanAndSyncDeviceApps()
        }
    }

    override fun onPause() {
        super.onPause()
        SessionManager.isActivityResumed = false
        // WAŻNE: NIE wstrzymujemy sesji (pauseSession)!
        // Gdy dziecko przełącza się na wybraną grę, licznik w tle musi nadal odliczać czas!
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra("EXTRA_FORCE_TIME_EXPIRED", false) == true) {
            // Wymuszenie wyświetlenia ekranu blokady
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private suspend fun scanAndSyncDeviceApps() {
        try {
            val pm = packageManager
            val detectedMap = mutableMapOf<String, AllowedApp>()

            // 1. Skanowanie aplikacji z filtrem LAUNCHER
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }

            for (resolveInfo in launcherList) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == packageName) continue

                val appLabel = resolveInfo.loadLabel(pm).toString()
                val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                        val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                SessionManager.repository.syncDetectedInstalledApps(resultList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchChildApp(app: AllowedApp) {
        if (SessionManager.currentState.value == SessionState.TIME_EXPIRED) {
            Toast.makeText(this, "Limit czasu na dziś został wyczerpany!", Toast.LENGTH_SHORT).show()
            return
        }

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

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                Toast.makeText(
                    this,
                    "Zezwól Family Control na wyświetlanie nad innymi aplikacjami, aby blokować gry w locie.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun requestSetDefaultLauncher() {
        openHomeSettingsDirectly()
    }

    private fun openHomeSettingsDirectly() {
        var launched = false

        // Krok 1: Bezpośrednie otwarcie systemowego ekranu wyboru launchera (ACTION_HOME_SETTINGS)
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

        Toast.makeText(
            this,
            "Wybierz 'Family Control' jako domyślną aplikację ekranu głównego",
            Toast.LENGTH_LONG
        ).show()
    }
}
