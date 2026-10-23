/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  Smartphone,
  Shield,
  Clock,
  Lock,
  Unlock,
  AlertTriangle,
  RotateCcw,
  Wifi,
  WifiOff,
  Power,
  Tv,
  Brush,
  Code2,
  BookOpen,
  Settings,
  FileText,
  Play,
  Pause,
  CheckCircle2,
  XCircle,
  HelpCircle,
  Flame,
  Volume2,
  PlusCircle,
  Gamepad2,
  Search,
  ExternalLink,
  ChevronRight,
  Sparkles
} from 'lucide-react';

// --- TYPY I MODELE DOMENOWE (odpowiadające modelom Kotlin z /android) ---
export type SessionState = 'ACTIVE' | 'WARNING' | 'TIME_EXPIRED' | 'PARENT_MODE';

export interface AllowedApp {
  id: string;
  packageName: string;
  displayName: string;
  category: string;
  iconType: 'video' | 'code' | 'lang' | 'draw' | 'math' | 'game' | 'web';
  accentColor: string;
  isEnabled: boolean;
  ageRating?: string;
  description?: string;
}

export interface ChildPolicy {
  dailyLimitMinutesWeekday: number;
  dailyLimitMinutesWeekend: number;
  sessionLimitMinutes: number;
  warningThresholdMinutes: number;
}

export interface TamperLogEntry {
  id: string;
  timestamp: string;
  eventType: string;
  details: string;
  severity: 'info' | 'warning' | 'alert';
}

// Baza aplikacji zainstalowanych na tablecie (część dozwolona, część domyślnie zablokowana)
const INITIAL_TABLET_APPS: AllowedApp[] = [
  {
    id: 'yt-kids',
    packageName: 'com.google.android.apps.youtube.kids',
    displayName: 'YouTube Kids',
    category: 'Wideo & Bajki',
    iconType: 'video',
    accentColor: '#EF4444',
    isEnabled: true,
    ageRating: 'PEGI 3',
    description: 'Filtrowane bajki, piosenki i programy edukacyjne dla najmłodszych.',
  },
  {
    id: 'scratch-jr',
    packageName: 'org.scratchjr.android',
    displayName: 'ScratchJr',
    category: 'Programowanie',
    iconType: 'code',
    accentColor: '#3B82F6',
    isEnabled: true,
    ageRating: 'PEGI 3',
    description: 'Wizualny język programowania dla dzieci w wieku 5-7 lat.',
  },
  {
    id: 'duolingo',
    packageName: 'com.duolingo',
    displayName: 'Duolingo Kids',
    category: 'Języki Obce',
    iconType: 'lang',
    accentColor: '#10B981',
    isEnabled: true,
    ageRating: 'PEGI 3',
    description: 'Nauka słówek i prostych zwrotów w języku angielskim w formie gry.',
  },
  {
    id: 'draw-pad',
    packageName: 'com.kids.drawingpad',
    displayName: 'Szkicownik',
    category: 'Kreatywność',
    iconType: 'draw',
    accentColor: '#F59E0B',
    isEnabled: true,
    ageRating: 'PEGI 3',
    description: 'Tablica do rysowania kolorami i rozwijania zdolności plastycznych.',
  },
  {
    id: 'khan-kids',
    packageName: 'org.khankids.android',
    displayName: 'Khan Kids',
    category: 'Matematyka',
    iconType: 'math',
    accentColor: '#8B5CF6',
    isEnabled: true,
    ageRating: 'PEGI 3',
    description: 'Ćwiczenia logiczne, cyfry i proste działania matematyczne.',
  },
  // Aplikacje zainstalowane na tablecie rodzica, ale DOMYŚLNIE ZABLOKOWANE dla dziecka:
  {
    id: 'minecraft',
    packageName: 'com.mojang.minecraftpe',
    displayName: 'Minecraft',
    category: 'Gry Przygodowe',
    iconType: 'game',
    accentColor: '#16A34A',
    isEnabled: false,
    ageRating: 'PEGI 7',
    description: 'Budowanie ze świata klocków. Wymaga zgody rodzica i limitu czasu.',
  },
  {
    id: 'roblox',
    packageName: 'com.roblox.client',
    displayName: 'Roblox',
    category: 'Gry Sieciowe',
    iconType: 'game',
    accentColor: '#DC2626',
    isEnabled: false,
    ageRating: 'PEGI 12',
    description: 'Świat gier wieloosobowych. Zawiera czat online i mikropłatności.',
  },
  {
    id: 'chess-kids',
    packageName: 'com.chess.kids',
    displayName: 'Szachy dla Dzieci',
    category: 'Logika & Szachy',
    iconType: 'math',
    accentColor: '#D97706',
    isEnabled: false,
    ageRating: 'PEGI 3',
    description: 'Nauka zasad królewskiej gry z zadaniami taktycznymi.',
  },
  {
    id: 'chrome',
    packageName: 'com.android.chrome',
    displayName: 'Google Chrome',
    category: 'Przeglądarka Web',
    iconType: 'web',
    accentColor: '#2563EB',
    isEnabled: false,
    ageRating: 'Brak ograniczeń',
    description: 'Pełny dostęp do stron www dorosłego. Domyślnie zablokowany.',
  },
  {
    id: 'netflix',
    packageName: 'com.netflix.mediaclient',
    displayName: 'Netflix (Konto Dorosłego)',
    category: 'Filmy & Seriale',
    iconType: 'video',
    accentColor: '#E50914',
    isEnabled: false,
    ageRating: 'Profil 16+',
    description: 'Aplikacja z filmami dorosłego. Wymaga PIN rodzica.',
  },
];

const INITIAL_POLICY: ChildPolicy = {
  dailyLimitMinutesWeekday: 45,
  dailyLimitMinutesWeekend: 90,
  sessionLimitMinutes: 20,
  warningThresholdMinutes: 5,
};

export default function App() {
  // --- ZEGAR I SYMULACJA SILNIKA MONOTONICZNEGO ---
  const [speedMultiplier, setSpeedMultiplier] = useState<number>(1);
  const [isSimulationPaused, setIsSimulationPaused] = useState<boolean>(false);
  const [isOffline, setIsOffline] = useState<boolean>(false);
  const [wallClockShiftSeconds, setWallClockShiftSeconds] = useState<number>(0);

  // Zmienne stanu odpowiadające SessionTimerEngine w Kotlin
  const [consumedSecondsToday, setConsumedSecondsToday] = useState<number>(0);
  const [currentSessionSeconds, setCurrentSessionSeconds] = useState<number>(0);
  const [monotonicUptimeSeconds, setMonotonicUptimeSeconds] = useState<number>(1240);
  const [lastCheckpointSeconds, setLastCheckpointSeconds] = useState<number>(0);
  const [sessionState, setSessionState] = useState<SessionState>('ACTIVE');
  const [policy, setPolicy] = useState<ChildPolicy>(INITIAL_POLICY);
  const [apps, setApps] = useState<AllowedApp[]>(INITIAL_TABLET_APPS);
  const [activeApp, setActiveApp] = useState<AllowedApp | null>(null);

  const [showRequestAppModal, setShowRequestAppModal] = useState<boolean>(false);
  const [showThreeMinWarningModal, setShowThreeMinWarningModal] = useState<boolean>(false);
  const hasSpokenWarningRef = useRef<boolean>(false);
  const [blockedAppAttempt, setBlockedAppAttempt] = useState<AllowedApp | null>(null);
  const [pendingUnlockApp, setPendingUnlockApp] = useState<AllowedApp | null>(null);
  const [appSearchQuery, setAppSearchQuery] = useState<string>('');

  const speakWarningVoice = (text: string) => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      try {
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'pl-PL';
        utterance.rate = 0.95;
        window.speechSynthesis.speak(utterance);
      } catch (e) {
        console.error('Speech error', e);
      }
    }
  };

  // --- ZABEZPIECZENIE PIN (PinSecurityManager) ---
  const [parentPin, setParentPin] = useState<string>('1234');
  const [pinInput, setPinInput] = useState<string>('');
  const [showPinModal, setShowPinModal] = useState<boolean>(false);
  const [failedPinAttempts, setFailedPinAttempts] = useState<number>(0);
  const [lockoutRemainingSeconds, setLockoutRemainingSeconds] = useState<number>(0);
  const [pinErrorMessage, setPinErrorMessage] = useState<string | null>(null);

  // --- DZIENNIK ZDARZEŃ I AUDYTU ---
  const [tamperLogs, setTamperLogs] = useState<TamperLogEntry[]>([
    {
      id: '1',
      timestamp: new Date().toLocaleTimeString(),
      eventType: 'SYSTEM_BOOT',
      details: 'Inicjalizacja silnika offline z zapisanego checkpointu (DataStore).',
      severity: 'info',
    },
  ]);

  // --- WIDOK ZAKŁADEK GŁÓWNYCH ---
  const [mainView, setMainView] = useState<'simulator' | 'feasibility' | 'adr' | 'tests'>('simulator');
  const [recentsOverlayOpen, setRecentsOverlayOpen] = useState<boolean>(false);

  // --- INTERAKTYWNE APLIKACJE WEWNĄTRZ TABLETU ---
  const [drawingColor, setDrawingColor] = useState<string>('#3B82F6');
  const [canvasStrokes, setCanvasStrokes] = useState<{ x: number; y: number; color: string }[]>([]);
  const isDrawingRef = useRef(false);

  const [scratchPos, setScratchPos] = useState<number>(100);
  const [duoAnswer, setDuoAnswer] = useState<string | null>(null);

  // Dodawanie wpisu do dziennika
  const addLog = (eventType: string, details: string, severity: 'info' | 'warning' | 'alert' = 'info') => {
    setTamperLogs((prev) => [
      {
        id: Math.random().toString(36).substring(2, 9),
        timestamp: new Date().toLocaleTimeString(),
        eventType,
        details,
        severity,
      },
      ...prev.slice(0, 49),
    ]);
  };

  // Obliczenie limitu dla bieżącego dnia
  const todayLimitSeconds = policy.dailyLimitMinutesWeekday * 60;
  const sessionLimitSeconds = policy.sessionLimitMinutes * 60;
  const remainingDaily = Math.max(0, todayLimitSeconds - consumedSecondsToday);
  const remainingSession = Math.max(0, sessionLimitSeconds - currentSessionSeconds);
  const remainingSeconds = Math.min(remainingDaily, remainingSession);

  // Główna pętla monotonicznego silnika czasu
  useEffect(() => {
    if (isSimulationPaused) return;

    const interval = setInterval(() => {
      // Obsługa blokady PIN
      setLockoutRemainingSeconds((prev) => (prev > 0 ? prev - 1 : 0));

      // Odliczanie czasu pracy systemu (monotoniczny uptime)
      setMonotonicUptimeSeconds((prev) => prev + speedMultiplier);

      // Odliczanie sesji dziecka, gdy nie jesteśmy w trybie rodzica
      if (sessionState !== 'PARENT_MODE') {
        setConsumedSecondsToday((prev) => {
          const next = prev + speedMultiplier;
          // Automatyczny zapis punktu kontrolnego co 30 sekund
          if (next - lastCheckpointSeconds >= 30) {
            setLastCheckpointSeconds(next);
          }
          return next;
        });

        setCurrentSessionSeconds((prev) => prev + speedMultiplier);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [isSimulationPaused, speedMultiplier, sessionState, lastCheckpointSeconds]);

  // Ewaluacja stanów sesji (ACTIVE -> WARNING -> TIME_EXPIRED)
  useEffect(() => {
    if (sessionState === 'PARENT_MODE') return;

    const warnThreshold = policy.warningThresholdMinutes * 60;

    // Automatyczne wyzwolenie komunikatu głosowego oraz popupu przy 3 minutach do końca (180s)
    if (remainingSeconds <= 180 && remainingSeconds > 0 && !hasSpokenWarningRef.current) {
      hasSpokenWarningRef.current = true;
      setShowThreeMinWarningModal(true);
      speakWarningVoice("Uwaga! Za trzy minuty koniec gry. Dokończ poziom i zapisz stan gry.");
      addLog('VOICE_WARNING_3MIN', 'Wyemitowano komunikat głosowy TTS oraz popup: Za 3 minuty koniec gry!', 'warning');
    } else if (remainingSeconds > 180) {
      hasSpokenWarningRef.current = false;
    }

    if (remainingSeconds <= 0) {
      if (sessionState !== 'TIME_EXPIRED') {
        setSessionState('TIME_EXPIRED');
        setActiveApp(null);
        setBlockedAppAttempt(null);
        setShowThreeMinWarningModal(false);
        speakWarningVoice("Koniec czasu na gry! Ekran został zablokowany.");
        addLog('TIME_EXPIRED', 'Limit czasu wyczerpany! Zablokowano dostęp, powrót do blokady.', 'alert');
      }
    } else if (remainingSeconds <= warnThreshold) {
      if (sessionState !== 'WARNING') {
        setSessionState('WARNING');
        addLog('WARNING', `Pozostało mniej niż ${policy.warningThresholdMinutes} minut czasu sesji.`, 'warning');
      }
    } else if (sessionState !== 'ACTIVE') {
      setSessionState('ACTIVE');
    }
  }, [remainingSeconds, policy.warningThresholdMinutes, sessionState]);

  // Obsługa autoryzacji kodem PIN
  const handlePinDigit = (digit: string) => {
    if (lockoutRemainingSeconds > 0) return;
    if (pinInput.length < 4) {
      const next = pinInput + digit;
      setPinInput(next);
      if (next.length === 4) {
        verifyPin(next);
      }
    }
  };

  const verifyPin = (code: string) => {
    if (code === parentPin) {
      // Jeśli PIN był wpisywany w celu odblokowania konkretnej aplikacji
      if (pendingUnlockApp) {
        setApps(
          apps.map((a) => (a.id === pendingUnlockApp.id ? { ...a, isEnabled: true } : a))
        );
        addLog(
          'APP_UNLOCKED_BY_PARENT',
          `Rodzic autoryzował kodem PIN aplikację: ${pendingUnlockApp.displayName}. Dodano do kącika dziecka!`,
          'info'
        );
        setPendingUnlockApp(null);
        setShowPinModal(false);
        setPinInput('');
        setFailedPinAttempts(0);
        setPinErrorMessage(null);
        setBlockedAppAttempt(null);
        return;
      }

      // Standardowe wejście do Panelu Rodzica
      setSessionState('PARENT_MODE');
      setShowPinModal(false);
      setPinInput('');
      setFailedPinAttempts(0);
      setPinErrorMessage(null);
      setActiveApp(null);
      setBlockedAppAttempt(null);
      addLog('PIN_SUCCESS', 'Pomyślna autoryzacja PIN rodzica. Otwarto panel administracyjny.', 'info');
    } else {
      const attempts = failedPinAttempts + 1;
      setFailedPinAttempts(attempts);
      setPinInput('');
      if (attempts >= 5) {
        setLockoutRemainingSeconds(60);
        setPinErrorMessage('Zbyt wiele nieudanych prób! Klawiatura zablokowana na 60 sekund.');
        addLog('PIN_LOCKOUT', 'Wykryto 5 nieudanych prób PIN. Aktywowano blokadę czasową na 60s.', 'alert');
      } else {
        setPinErrorMessage(`Nieprawidłowy PIN! Pozostało prób: ${5 - attempts}`);
        addLog('PIN_FAILED', `Błędny PIN (próba ${attempts}/5).`, 'warning');
      }
    }
  };

  // --- SCENARIUSZE TESTÓW OBEJŚCIA (Bypass Matrix) ---
  const simulateHomePress = () => {
    setRecentsOverlayOpen(false);
    setBlockedAppAttempt(null);
    setShowRequestAppModal(false);
    if (sessionState === 'PARENT_MODE') {
      addLog('TEST_HOME', 'Wciśnięto Home w trybie rodzica — powrót do pulpitu.', 'info');
    } else {
      setActiveApp(null);
      addLog('TEST_HOME', 'Wciśnięto Home podczas gry dziecka — bezpieczny powrót do Kids Launchera.', 'info');
    }
  };

  const simulateBackPress = () => {
    if (blockedAppAttempt) {
      setBlockedAppAttempt(null);
      addLog('TEST_BACK', 'Zamknięto ekran blokady nieautoryzowanej aplikacji — powrót do kącika dziecka.', 'info');
      return;
    }
    if (showRequestAppModal) {
      setShowRequestAppModal(false);
      addLog('TEST_BACK', 'Zamknięto katalog prośby o gry klawiszem Back.', 'info');
      return;
    }
    if (activeApp) {
      setActiveApp(null);
      addLog('TEST_BACK', `Wyjście z aplikacji ${activeApp.displayName} klawiszem Back do Kids Launchera.`, 'info');
    } else {
      addLog('TEST_BACK', 'Wciśnięto Back na ekranie głównym Launchera — OnBackPressedCallback zablokował wyjście do czarnego ekranu.', 'info');
    }
  };

  // Symulacja: dziecko próbuje uruchomić niezatwierdzoną grę (np. Minecraft, Roblox lub przeglądarkę)
  const simulateLaunchUnauthorizedApp = (app: AllowedApp) => {
    setRecentsOverlayOpen(false);
    setActiveApp(null);
    setBlockedAppAttempt(app);
    addLog(
      'APP_BLOCKED_UNAUTHORIZED',
      `Próba uruchomienia ${app.displayName} (${app.packageName})! UsageStatsManager wykrył brak autoryzacji — wyświetlono blokadę rodzicielską.`,
      'alert'
    );
  };

  const simulateThreeMinWarning = () => {
    // Ustaw czas tak, aby pozostało dokładnie 180 sekund (3 minuty)
    const targetConsumed = todayLimitSeconds - 180;
    setConsumedSecondsToday(Math.max(0, targetConsumed));
    setShowThreeMinWarningModal(true);
    hasSpokenWarningRef.current = true;
    speakWarningVoice("Uwaga! Za trzy minuty koniec gry. Dokończ poziom i zapisz stan.");
    addLog(
      'TEST_3MIN_WARNING',
      'Test ostrzeżenia 3 min: wyemitowano komunikat głosowy TTS w języku polskim oraz wyskakujący modal informacyjny.',
      'warning'
    );
  };

  const simulateClockRollback = () => {
    setWallClockShiftSeconds((prev) => prev - 7200); // cofnij o 2h
    addLog(
      'TAMPER_CLOCK_ROLLBACK',
      'Dziecko cofnęło zegar systemowy o 2h! Silnik używa SystemClock.elapsedRealtime() — licznik nie uległ cofnięciu.',
      'warning'
    );
  };

  const simulateDateForward = () => {
    setWallClockShiftSeconds((prev) => prev + 86400); // 1 dzień w przód
    addLog(
      'TAMPER_DATE_FORWARD',
      'Próba wymuszenia resetu limitu przez przestawienie daty o 1 dzień w przód! Silnik wykrył brak 12h monotonicznego upływu czasu i zablokował oszustwo.',
      'alert'
    );
  };

  const simulateReboot = () => {
    setMonotonicUptimeSeconds(15);
    setActiveApp(null);
    setBlockedAppAttempt(null);
    setRecentsOverlayOpen(false);
    setShowRequestAppModal(false);
    addLog(
      'SYSTEM_REBOOT',
      `Restart tabletu! Czas monotoniczny zresetowany. Odzyskano ${Math.round(consumedSecondsToday / 60)} min zużycia z trwałego punktu kontrolnego.`,
      'info'
    );
  };

  const simulateBruteForce = () => {
    setFailedPinAttempts(5);
    setLockoutRemainingSeconds(60);
    setPinErrorMessage('Zbyt wiele nieudanych prób! Klawiatura zablokowana na 60 sekund.');
    setShowPinModal(true);
    addLog('TEST_BRUTE_FORCE', 'Symulacja 5 błędnych prób odgadnięcia PIN — aktywowano blokadę.', 'alert');
  };

  // Formatowanie sekund do mm:ss
  const formatTime = (totalSeconds: number) => {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const enabledApps = apps.filter((a) => a.isEnabled);
  const disabledApps = apps.filter((a) => !a.isEnabled);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-sky-500/30">
      {/* 1. TOP BAR CONTRACT */}
      <header className="border-b border-slate-800 bg-slate-900/90 backdrop-blur px-6 py-3 flex items-center justify-between gap-8 shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-sky-600 flex items-center justify-center font-bold text-white shadow-sm">
            FC
          </div>
          <span className="text-base font-semibold text-white tracking-tight whitespace-nowrap">
            Family Control v2.0
          </span>
          <span className="hidden sm:inline-block text-xs text-slate-400 font-mono">
            Android 13+ Shared Tablet PoC
          </span>
        </div>

        {/* Strefa nawigacji */}
        <nav className="flex items-center gap-6 text-sm font-medium text-slate-400">
          <button
            onClick={() => setMainView('simulator')}
            className={`transition-colors whitespace-nowrap ${
              mainView === 'simulator' ? 'text-sky-400 font-semibold' : 'hover:text-slate-200'
            }`}
          >
            Symulator Tabletu
          </button>
          <button
            onClick={() => setMainView('feasibility')}
            className={`transition-colors whitespace-nowrap ${
              mainView === 'feasibility' ? 'text-sky-400 font-semibold' : 'hover:text-slate-200'
            }`}
          >
            Analiza Wykonalności
          </button>
          <button
            onClick={() => setMainView('adr')}
            className={`transition-colors whitespace-nowrap ${
              mainView === 'adr' ? 'text-sky-400 font-semibold' : 'hover:text-slate-200'
            }`}
          >
            ADR 0001 (Zegar)
          </button>
          <button
            onClick={() => setMainView('tests')}
            className={`transition-colors whitespace-nowrap ${
              mainView === 'tests' ? 'text-sky-400 font-semibold' : 'hover:text-slate-200'
            }`}
          >
            Scenariusze Testowe (TC-11)
          </button>
        </nav>

        {/* Akcja główna */}
        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={() => {
              if (sessionState === 'PARENT_MODE') {
                setSessionState('ACTIVE');
                addLog('MODE_CHANGE', 'Powrót do aktywnego Trybu Dziecka.', 'info');
              } else {
                setPendingUnlockApp(null);
                setPinInput('');
                setPinErrorMessage(null);
                setShowPinModal(true);
              }
            }}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              sessionState === 'PARENT_MODE'
                ? 'bg-emerald-600 text-white hover:bg-emerald-500'
                : 'bg-sky-600 text-white hover:bg-sky-500'
            }`}
          >
            {sessionState === 'PARENT_MODE' ? (
              <>
                <Unlock className="w-3.5 h-3.5" /> Włącz Tryb Dziecka
              </>
            ) : (
              <>
                <Lock className="w-3.5 h-3.5" /> Panel Rodzica (PIN)
              </>
            )}
          </button>
        </div>
      </header>

      {/* 2. GŁÓWNA ZAWARTOŚĆ */}
      <main className="flex-1 flex flex-col xl:flex-row overflow-hidden">
        {mainView === 'simulator' && (
          <>
            {/* LEWA KOLUMNA: WIRTUALNY TABLET ANDROID */}
            <div className="flex-1 p-4 lg:p-6 flex flex-col items-center justify-center bg-slate-950 overflow-y-auto">
              {/* OBUDOWA TABLETU 10" */}
              <div className="w-full max-w-4xl aspect-[16/10] bg-slate-900 rounded-[28px] p-3 shadow-2xl border-4 border-slate-800 flex flex-col relative select-none">
                {/* Kamera przednia */}
                <div className="absolute top-1.5 left-1/2 -translate-x-1/2 flex items-center gap-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-slate-950 border border-slate-700"></div>
                  <div className="w-1 h-1 rounded-full bg-slate-800"></div>
                </div>

                {/* EKRAN TABLETU */}
                <div className="flex-1 bg-slate-950 rounded-[20px] overflow-hidden flex flex-col relative border border-slate-800/80">
                  {/* ANDROID STATUS BAR */}
                  <div className="h-6 bg-slate-900/90 text-[11px] font-mono text-slate-400 px-4 flex items-center justify-between border-b border-slate-800/60 shrink-0">
                    <div className="flex items-center gap-2">
                      <span>{new Date(Date.now() + wallClockShiftSeconds * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                      <span className="text-slate-600">·</span>
                      <span className="text-[10px] text-sky-400 font-sans">Profil Dziecka (Współdzielony)</span>
                    </div>
                    <div className="flex items-center gap-3">
                      {sessionState === 'WARNING' && (
                        <span className="text-amber-400 flex items-center gap-1 font-sans text-[10px]">
                          <AlertTriangle className="w-3 h-3" /> Kończy się czas!
                        </span>
                      )}
                      <span className="font-sans text-[10px] text-slate-300">
                        Pozostało: <strong className="font-mono text-sky-300">{formatTime(remainingSeconds)}</strong>
                      </span>
                      {isOffline ? <WifiOff className="w-3 h-3 text-rose-400" /> : <Wifi className="w-3 h-3 text-slate-400" />}
                      <span>87%</span>
                    </div>
                  </div>

                  {/* ZAWARTOŚĆ EKRANU TABLETU */}
                  <div className="flex-1 overflow-y-auto relative bg-slate-950 flex flex-col">
                    {/* WARIANT A: BLOKADA NIEDOZWOLONEJ APLIKACJI / GRY */}
                    {blockedAppAttempt && (
                      <div className="flex-1 flex flex-col items-center justify-center p-6 text-center bg-slate-950/95 animate-fadeIn z-20">
                        <div className="w-16 h-16 rounded-2xl bg-rose-950/60 border border-rose-500/40 flex items-center justify-center mb-3 text-rose-400 shadow-xl">
                          <Lock className="w-8 h-8" />
                        </div>
                        <h2 className="text-xl font-bold text-white mb-1">
                          Aplikacja Zablokowana przez Rodzica
                        </h2>
                        <div className="p-3 bg-slate-900 rounded-xl border border-slate-800 my-3 max-w-sm text-left">
                          <div className="text-xs font-semibold text-white flex items-center gap-2 mb-1">
                            <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: blockedAppAttempt.accentColor }}></span>
                            {blockedAppAttempt.displayName}
                          </div>
                          <div className="text-[11px] text-slate-400 mb-1">{blockedAppAttempt.packageName}</div>
                          <div className="text-[10px] text-amber-400 font-mono">
                            Status: Aplikacja zainstalowana na tablecie, ale nie znajduje się na liście dozwolonych dla dziecka.
                          </div>
                        </div>

                        <p className="text-xs text-slate-400 max-w-sm mb-5 leading-relaxed">
                          Dziecko nie może uruchomić tej gry bez autoryzacji. Poproś rodzica o wpisanie kodu PIN, aby jednorazowo lub na stałe odblokować ten tytuł.
                        </p>

                        <div className="flex flex-wrap items-center justify-center gap-3">
                          <button
                            onClick={() => {
                              setPendingUnlockApp(blockedAppAttempt);
                              setPinInput('');
                              setPinErrorMessage(null);
                              setShowPinModal(true);
                            }}
                            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 shadow-md"
                          >
                            <Unlock className="w-3.5 h-3.5" /> Odblokuj dla dziecka (PIN rodzica)
                          </button>
                          <button
                            onClick={() => setBlockedAppAttempt(null)}
                            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-xs"
                          >
                            Wróć do Kącika Dziecka
                          </button>
                        </div>
                      </div>
                    )}

                    {/* WARIANT B: PANEL RODZICA (PARENT_MODE) */}
                    {sessionState === 'PARENT_MODE' && !blockedAppAttempt && (
                      <div className="p-6 flex-1 flex flex-col bg-slate-900/60 overflow-y-auto">
                        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
                          <div>
                            <h2 className="text-lg font-bold text-white flex items-center gap-2">
                              <Shield className="w-5 h-5 text-sky-400" />
                              Panel Konfiguracyjny Rodzica
                            </h2>
                            <p className="text-xs text-slate-400">
                              Lokalna kontrola na współdzielonym tablecie domowym
                            </p>
                          </div>
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => {
                                setConsumedSecondsToday((prev) => Math.max(0, prev - 900));
                                addLog('BONUS_TIME', 'Rodzic przyznał dziecku +15 minut premii.', 'info');
                              }}
                              className="px-2.5 py-1 text-xs bg-emerald-600/20 text-emerald-300 border border-emerald-500/30 rounded-lg hover:bg-emerald-600/30"
                            >
                              +15 min premii
                            </button>
                            <button
                              onClick={() => setSessionState('ACTIVE')}
                              className="px-3 py-1 text-xs bg-sky-600 text-white rounded-lg hover:bg-sky-500"
                            >
                              Wróć do pulpitu dziecka
                            </button>
                          </div>
                        </div>

                        {/* Suwaki limitów */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 my-4">
                          <div className="p-3.5 bg-slate-800/60 rounded-xl border border-slate-700/60">
                            <label className="text-xs font-semibold text-slate-300 block mb-1">
                              Limit dzienny (Dni powszednie)
                            </label>
                            <div className="flex items-center justify-between text-sm font-mono text-sky-400 mb-2">
                              <span>{policy.dailyLimitMinutesWeekday} minut</span>
                              <span className="text-xs text-slate-500">Zużyto: {Math.round(consumedSecondsToday / 60)}m</span>
                            </div>
                            <input
                              type="range"
                              min="15"
                              max="180"
                              step="5"
                              value={policy.dailyLimitMinutesWeekday}
                              onChange={(e) =>
                                setPolicy({ ...policy, dailyLimitMinutesWeekday: Number(e.target.value) })
                              }
                              className="w-full accent-sky-500 cursor-pointer"
                            />
                          </div>

                          <div className="p-3.5 bg-slate-800/60 rounded-xl border border-slate-700/60">
                            <label className="text-xs font-semibold text-slate-300 block mb-1">
                              Limit dzienny (Weekendy)
                            </label>
                            <div className="flex items-center justify-between text-sm font-mono text-sky-400 mb-2">
                              <span>{policy.dailyLimitMinutesWeekend} minut</span>
                            </div>
                            <input
                              type="range"
                              min="30"
                              max="300"
                              step="10"
                              value={policy.dailyLimitMinutesWeekend}
                              onChange={(e) =>
                                setPolicy({ ...policy, dailyLimitMinutesWeekend: Number(e.target.value) })
                              }
                              className="w-full accent-sky-500 cursor-pointer"
                            />
                          </div>

                          <div className="p-3.5 bg-slate-800/60 rounded-xl border border-slate-700/60">
                            <label className="text-xs font-semibold text-slate-300 block mb-1">
                              Maksymalna pojedyncza sesja
                            </label>
                            <div className="flex items-center justify-between text-sm font-mono text-sky-400 mb-2">
                              <span>{policy.sessionLimitMinutes} minut</span>
                            </div>
                            <input
                              type="range"
                              min="10"
                              max="120"
                              step="5"
                              value={policy.sessionLimitMinutes}
                              onChange={(e) =>
                                setPolicy({ ...policy, sessionLimitMinutes: Number(e.target.value) })
                              }
                              className="w-full accent-sky-500 cursor-pointer"
                            />
                          </div>
                        </div>

                        {/* Whitelist aplikacji */}
                        <div className="mt-2">
                          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2">
                            Wszystkie Aplikacje Tabletu (App Whitelist)
                          </h3>
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            {apps.map((app) => (
                              <div
                                key={app.id}
                                className="flex items-center justify-between p-2.5 bg-slate-800/40 rounded-lg border border-slate-700/40 text-xs"
                              >
                                <div>
                                  <span className="font-semibold text-white block flex items-center gap-1.5">
                                    <span className="w-2 h-2 rounded-full" style={{ backgroundColor: app.accentColor }} />
                                    {app.displayName}
                                  </span>
                                  <span className="text-slate-400 text-[11px]">{app.packageName}</span>
                                </div>
                                <button
                                  onClick={() => {
                                    setApps(
                                      apps.map((a) => (a.id === app.id ? { ...a, isEnabled: !a.isEnabled } : a))
                                    );
                                    addLog('WHITELIST_CHANGE', `Zmieniono status ${app.displayName}: ${!app.isEnabled ? 'Włączona' : 'Zablokowana'}`);
                                  }}
                                  className={`px-2.5 py-1 rounded text-[11px] font-medium transition-colors ${
                                    app.isEnabled
                                      ? 'bg-sky-600/30 text-sky-300 border border-sky-500/40'
                                      : 'bg-slate-700/40 text-slate-400'
                                  }`}
                                >
                                  {app.isEnabled ? 'Dozwolona' : 'Zablokowana'}
                                </button>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    )}

                    {/* WARIANT C: CZAS WYCZERPANY (TIME_EXPIRED) */}
                    {sessionState === 'TIME_EXPIRED' && !blockedAppAttempt && (
                      <div className="flex-1 flex flex-col items-center justify-center p-8 text-center bg-slate-950/95 animate-fadeIn">
                        <div className="w-16 h-16 rounded-2xl bg-rose-600/20 border border-rose-500/30 flex items-center justify-center mb-4 text-rose-400">
                          <Clock className="w-8 h-8" />
                        </div>
                        <h2 className="text-2xl font-bold text-white mb-2">Czas na dzisiaj dobiegł końca!</h2>
                        <p className="text-sm text-slate-400 max-w-md mb-6 leading-relaxed">
                          Wykorzystano zaplanowany limit czasu ekranowego. Odłóż tablet na bok lub poproś rodzica o wpisanie kodu PIN w celu odblokowania.
                        </p>
                        <button
                          onClick={() => {
                            setPendingUnlockApp(null);
                            setPinInput('');
                            setPinErrorMessage(null);
                            setShowPinModal(true);
                          }}
                          className="px-5 py-2.5 bg-sky-600 hover:bg-sky-500 text-white rounded-xl text-sm font-semibold flex items-center gap-2 shadow-lg transition-colors"
                        >
                          <Lock className="w-4 h-4" /> Odblokuj kodem PIN rodzica
                        </button>
                      </div>
                    )}

                    {/* WARIANT D: OTWARTA DOZWOLONA APLIKACJA */}
                    {activeApp && sessionState !== 'TIME_EXPIRED' && sessionState !== 'PARENT_MODE' && !blockedAppAttempt && (
                      <div className="flex-1 flex flex-col bg-slate-900">
                        {/* Pasek aplikacji */}
                        <div className="h-10 bg-slate-800/80 px-4 flex items-center justify-between border-b border-slate-700/60">
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-semibold text-white">{activeApp.displayName}</span>
                            <span className="text-[10px] text-slate-400 font-mono">({activeApp.packageName})</span>
                          </div>
                          <button
                            onClick={() => setActiveApp(null)}
                            className="text-xs text-slate-300 hover:text-white px-2 py-0.5 rounded bg-slate-700/60"
                          >
                            Zamknij aplikację
                          </button>
                        </div>

                        {/* Wnętrze aplikacji */}
                        <div className="flex-1 p-4 flex flex-col justify-center items-center">
                          {activeApp.iconType === 'draw' && (
                            <div className="w-full h-full flex flex-col">
                              <div className="flex items-center gap-2 mb-2 pb-2 border-b border-slate-800">
                                <span className="text-xs text-slate-400">Paleta kolorów:</span>
                                {['#3B82F6', '#EF4444', '#10B981', '#F59E0B', '#8B5CF6', '#EC4899'].map((c) => (
                                  <button
                                    key={c}
                                    onClick={() => setDrawingColor(c)}
                                    className={`w-6 h-6 rounded-full border-2 ${
                                      drawingColor === c ? 'border-white scale-110' : 'border-transparent'
                                    }`}
                                    style={{ backgroundColor: c }}
                                  />
                                ))}
                                <button
                                  onClick={() => setCanvasStrokes([])}
                                  className="ml-auto text-xs text-rose-400 hover:text-rose-300"
                                >
                                  Wyczyść
                                </button>
                              </div>
                              <div
                                onMouseDown={() => (isDrawingRef.current = true)}
                                onMouseUp={() => (isDrawingRef.current = false)}
                                onMouseMove={(e) => {
                                  if (!isDrawingRef.current) return;
                                  const rect = e.currentTarget.getBoundingClientRect();
                                  setCanvasStrokes((prev) => [
                                    ...prev,
                                    {
                                      x: e.clientX - rect.left,
                                      y: e.clientY - rect.top,
                                      color: drawingColor,
                                    },
                                  ]);
                                }}
                                className="flex-1 bg-slate-950 rounded-xl relative overflow-hidden cursor-crosshair border border-slate-800"
                              >
                                {canvasStrokes.map((s, idx) => (
                                  <div
                                    key={idx}
                                    className="absolute w-3 h-3 rounded-full -translate-x-1/2 -translate-y-1/2 pointer-events-none"
                                    style={{ left: s.x, top: s.y, backgroundColor: s.color }}
                                  />
                                ))}
                                {canvasStrokes.length === 0 && (
                                  <div className="absolute inset-0 flex items-center justify-center text-xs text-slate-600 pointer-events-none">
                                    Rysuj myszką po ekranie tabletu...
                                  </div>
                                )}
                              </div>
                            </div>
                          )}

                          {activeApp.iconType === 'code' && (
                            <div className="text-center p-6 bg-slate-800/40 rounded-2xl border border-slate-700/60 max-w-md">
                              <Code2 className="w-12 h-12 text-sky-400 mx-auto mb-3" />
                              <h4 className="font-bold text-white text-base mb-1">ScratchJr — Kociak w Ruchu</h4>
                              <p className="text-xs text-slate-400 mb-4">Klikaj klocki, aby zaprogramować ruch postaci:</p>
                              <div className="h-28 bg-slate-950 rounded-xl relative overflow-hidden flex items-center border border-slate-800 mb-4">
                                <div
                                  className="absolute text-2xl transition-all duration-300"
                                  style={{ left: `${scratchPos}px` }}
                                >
                                  🐱
                                </div>
                              </div>
                              <div className="flex justify-center gap-2">
                                <button
                                  onClick={() => setScratchPos((p) => Math.max(20, p - 30))}
                                  className="px-3 py-1.5 bg-amber-600 text-white rounded-lg text-xs font-semibold"
                                >
                                  ⬅ W lewo
                                </button>
                                <button
                                  onClick={() => setScratchPos((p) => Math.min(260, p + 30))}
                                  className="px-3 py-1.5 bg-amber-600 text-white rounded-lg text-xs font-semibold"
                                >
                                  W prawo ➡
                                </button>
                                <button
                                  onClick={() => setScratchPos(100)}
                                  className="px-3 py-1.5 bg-slate-700 text-slate-300 rounded-lg text-xs"
                                >
                                  Reset
                                </button>
                              </div>
                            </div>
                          )}

                          {activeApp.iconType === 'lang' && (
                            <div className="text-center p-6 bg-slate-800/40 rounded-2xl border border-slate-700/60 max-w-md">
                              <BookOpen className="w-12 h-12 text-emerald-400 mx-auto mb-3" />
                              <h4 className="font-bold text-white text-base mb-1">Duolingo Kids — Angielski</h4>
                              <p className="text-xs text-slate-400 mb-4">Jak przetłumaczysz słowo: <strong className="text-white font-semibold">"Pies"</strong>?</p>
                              <div className="grid grid-cols-2 gap-2">
                                {['Cat', 'Dog', 'Bird', 'Elephant'].map((opt) => (
                                  <button
                                    key={opt}
                                    onClick={() => setDuoAnswer(opt)}
                                    className={`p-2.5 rounded-xl border text-xs font-semibold transition-colors ${
                                      duoAnswer === opt
                                        ? opt === 'Dog'
                                          ? 'bg-emerald-600 text-white border-emerald-500'
                                          : 'bg-rose-600 text-white border-rose-500'
                                        : 'bg-slate-800 text-slate-300 border-slate-700 hover:bg-slate-700'
                                    }`}
                                  >
                                    {opt}
                                  </button>
                                ))}
                              </div>
                              {duoAnswer && (
                                <p className={`text-xs mt-3 font-semibold ${duoAnswer === 'Dog' ? 'text-emerald-400' : 'text-rose-400'}`}>
                                  {duoAnswer === 'Dog' ? 'Świetnie! Poprawna odpowiedź!' : 'Spróbuj jeszcze raz!'}
                                </p>
                              )}
                            </div>
                          )}

                          {activeApp.iconType === 'game' && (
                            <div className="text-center p-8 bg-slate-800/40 rounded-2xl border border-slate-700/60 max-w-md">
                              <div className="w-14 h-14 rounded-2xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto mb-3">
                                <Gamepad2 className="w-8 h-8" />
                              </div>
                              <h4 className="font-bold text-white text-base mb-1">{activeApp.displayName}</h4>
                              <p className="text-xs text-slate-400 mb-4">
                                {activeApp.description} Gra zatwierdzona przez rodzica.
                              </p>
                              <div className="p-3 bg-slate-950 rounded-xl text-left font-mono text-[11px] text-slate-400 border border-slate-800">
                                <div>Kategoria: <span className="text-sky-400">{activeApp.category}</span></div>
                                <div>Ograniczenie wiekowe: <span className="text-emerald-400">{activeApp.ageRating}</span></div>
                                <div>Pozostało czasu sesji: <span className="text-white font-bold">{formatTime(remainingSeconds)}</span></div>
                              </div>
                            </div>
                          )}

                          {(activeApp.iconType === 'video' || activeApp.iconType === 'math' || activeApp.iconType === 'web') && (
                            <div className="text-center p-8 bg-slate-800/40 rounded-2xl border border-slate-700/60 max-w-md">
                              <div className="w-12 h-12 rounded-xl bg-sky-500/20 text-sky-400 flex items-center justify-center mx-auto mb-3">
                                {activeApp.iconType === 'video' ? <Tv className="w-6 h-6" /> : <BookOpen className="w-6 h-6" />}
                              </div>
                              <h4 className="font-bold text-white text-base mb-1">{activeApp.displayName}</h4>
                              <p className="text-xs text-slate-400 mb-4">
                                Symulacja działania certyfikowanej aplikacji edukacyjnej na tablecie dziecka. Czas sesji jest nieprzerwanie monitorowany.
                              </p>
                              <div className="p-3 bg-slate-950 rounded-xl text-left font-mono text-[11px] text-slate-400 border border-slate-800">
                                <div>Status: <span className="text-emerald-400">Aktywna w foreground</span></div>
                                <div>Odmierzanie: <span className="text-sky-400">SystemClock.elapsedRealtime()</span></div>
                                <div>Pozostało: <span className="text-white font-bold">{formatTime(remainingSeconds)}</span></div>
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    )}

                    {/* WARIANT E: PULPIT DZIECKA (KIDS LAUNCHER MODE) */}
                    {!activeApp && sessionState !== 'TIME_EXPIRED' && sessionState !== 'PARENT_MODE' && !blockedAppAttempt && (
                      <div className="p-6 flex-1 flex flex-col justify-between">
                        {/* Karta powitalna dziecka */}
                        <div className="flex items-center justify-between pb-3">
                          <div>
                            <h2 className="text-xl font-bold text-white tracking-tight">Kącik Dziecka</h2>
                            <p className="text-xs text-slate-400">Dozwolone aplikacje i gry</p>
                          </div>
                          <div className="flex items-center gap-2">
                            <div className={`px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1.5 ${
                              sessionState === 'WARNING'
                                ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                                : 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                            }`}>
                              <Clock className="w-3.5 h-3.5" />
                              <span>{formatTime(remainingSeconds)}</span>
                            </div>
                          </div>
                        </div>

                        {/* Siatka zatwierdzonych aplikacji oraz kafelek "Poproś o nową grę" */}
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3.5 my-auto">
                          {enabledApps.map((app) => (
                            <button
                              key={app.id}
                              onClick={() => {
                                setActiveApp(app);
                                addLog('APP_LAUNCH', `Dziecko uruchomiło aplikację: ${app.displayName}.`, 'info');
                              }}
                              className="group p-3.5 bg-slate-900/90 hover:bg-slate-800/90 rounded-2xl border border-slate-800 hover:border-slate-700 transition-all flex flex-col items-center justify-center text-center shadow-lg active:scale-95"
                            >
                              <div
                                className="w-13 h-13 rounded-2xl flex items-center justify-center text-white mb-2 shadow-inner group-hover:scale-105 transition-transform"
                                style={{ backgroundColor: app.accentColor }}
                              >
                                {app.iconType === 'video' && <Tv className="w-6 h-6" />}
                                {app.iconType === 'code' && <Code2 className="w-6 h-6" />}
                                {app.iconType === 'lang' && <BookOpen className="w-6 h-6" />}
                                {app.iconType === 'draw' && <Brush className="w-6 h-6" />}
                                {app.iconType === 'math' && <Flame className="w-6 h-6" />}
                                {app.iconType === 'game' && <Gamepad2 className="w-6 h-6" />}
                                {app.iconType === 'web' && <ExternalLink className="w-6 h-6" />}
                              </div>
                              <span className="font-semibold text-xs text-white group-hover:text-sky-300 transition-colors">
                                {app.displayName}
                              </span>
                              <span className="text-[10px] text-slate-500">{app.category}</span>
                            </button>
                          ))}

                          {/* KAFELEK: POPROŚ O NOWĄ GRĘ LUB APLIKACJĘ */}
                          <button
                            onClick={() => setShowRequestAppModal(true)}
                            className="p-3.5 bg-gradient-to-br from-slate-900 to-sky-950/40 hover:from-slate-800 hover:to-sky-900/40 rounded-2xl border border-dashed border-sky-500/40 hover:border-sky-400 transition-all flex flex-col items-center justify-center text-center shadow-lg active:scale-95 group"
                          >
                            <div className="w-13 h-13 rounded-2xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400 mb-2 group-hover:scale-105 transition-transform">
                              <PlusCircle className="w-7 h-7" />
                            </div>
                            <span className="font-semibold text-xs text-sky-300 group-hover:text-white transition-colors">
                              Poproś o grę / aplikację
                            </span>
                            <span className="text-[10px] text-slate-400">Wymaga PIN rodzica</span>
                          </button>
                        </div>

                        {/* Dolny pasek informacyjny */}
                        <div className="text-center text-[11px] text-slate-500 pt-2 border-t border-slate-900">
                          Tablet współdzielony · Profil Dziecka · Ochrona hasłem rodzica
                        </div>
                      </div>
                    )}

                    {/* OVERLAY: ANDROID RECENT APPS TASK SWITCHER */}
                    {recentsOverlayOpen && (
                      <div className="absolute inset-0 bg-slate-950/90 backdrop-blur-sm z-30 flex flex-col p-6 animate-fadeIn">
                        <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-4">
                          <span className="text-sm font-semibold text-white">Ostatnie aplikacje (Task Switcher)</span>
                          <button
                            onClick={() => setRecentsOverlayOpen(false)}
                            className="text-xs text-slate-400 hover:text-white"
                          >
                            Zamknij
                          </button>
                        </div>
                        <div className="flex-1 flex items-center justify-center gap-4 overflow-x-auto">
                          <div className="w-48 h-64 bg-slate-900 rounded-xl border border-sky-500/50 p-3 flex flex-col justify-between shadow-xl">
                            <span className="text-xs font-semibold text-sky-400">Family Control Launcher</span>
                            <div className="text-[11px] text-slate-400">Aktywny ekran główny</div>
                            <button
                              onClick={() => setRecentsOverlayOpen(false)}
                              className="w-full py-1.5 bg-sky-600 rounded text-xs font-semibold text-white"
                            >
                              Przełącz
                            </button>
                          </div>
                          <div className="w-48 h-64 bg-slate-900 rounded-xl border border-slate-800 p-3 flex flex-col justify-between opacity-70">
                            <span className="text-xs font-semibold text-slate-300">Minecraft (Zablokowana)</span>
                            <div className="text-[10px] text-rose-400">
                              Wykrywanie przez UsageStatsManager natychmiast blokuje uruchomienie.
                            </div>
                            <button
                              onClick={() => {
                                const mc = apps.find((a) => a.id === 'minecraft')!;
                                simulateLaunchUnauthorizedApp(mc);
                              }}
                              className="w-full py-1.5 bg-rose-600/30 text-rose-300 border border-rose-500/40 rounded text-xs"
                            >
                              Przetestuj blokadę
                            </button>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>

                  {/* ANDROID 13 NAVIGATION BAR (3 przyciski sprzętowe: Back, Home, Recents) */}
                  <div className="h-10 bg-slate-950 flex items-center justify-center gap-16 border-t border-slate-800/80 shrink-0">
                    {/* Back */}
                    <button
                      onClick={simulateBackPress}
                      title="Przycisk Back (Wstecz)"
                      className="p-1.5 text-slate-400 hover:text-white active:scale-90 transition-transform"
                    >
                      <svg className="w-4 h-4 fill-current" viewBox="0 0 24 24">
                        <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
                      </svg>
                    </button>
                    {/* Home */}
                    <button
                      onClick={simulateHomePress}
                      title="Przycisk Home (Ekran Główny)"
                      className="w-3.5 h-3.5 rounded-full border-2 border-slate-400 hover:border-white active:scale-90 transition-transform"
                    />
                    {/* Recent Apps */}
                    <button
                      onClick={() => setRecentsOverlayOpen(!recentsOverlayOpen)}
                      title="Przycisk Recent Apps (Ostatnie)"
                      className="w-3.5 h-3.5 border-2 border-slate-400 rounded-sm hover:border-white active:scale-90 transition-transform"
                    />
                  </div>
                </div>
              </div>

              {/* STEROWANIE TEMPEM SYMULACJI POD TABLETEM */}
              <div className="mt-4 flex flex-wrap items-center justify-center gap-3 text-xs text-slate-400">
                <span className="font-semibold text-slate-300">Tempo zegara:</span>
                {[1, 5, 20, 60].map((s) => (
                  <button
                    key={s}
                    onClick={() => setSpeedMultiplier(s)}
                    className={`px-2.5 py-1 rounded-md font-mono transition-colors ${
                      speedMultiplier === s
                        ? 'bg-sky-600 text-white font-bold'
                        : 'bg-slate-800 text-slate-400 hover:text-white'
                    }`}
                  >
                    {s}x
                  </button>
                ))}
                <div className="h-4 w-px bg-slate-800 mx-1"></div>
                <button
                  onClick={() => setIsSimulationPaused(!isSimulationPaused)}
                  className="px-2.5 py-1 bg-slate-800 hover:text-white rounded-md flex items-center gap-1"
                >
                  {isSimulationPaused ? <Play className="w-3 h-3 text-emerald-400" /> : <Pause className="w-3 h-3 text-amber-400" />}
                  {isSimulationPaused ? 'Wznów' : 'Pauza'}
                </button>
                <button
                  onClick={() => {
                    setConsumedSecondsToday(0);
                    setCurrentSessionSeconds(0);
                    setSessionState('ACTIVE');
                    addLog('TIMER_RESET', 'Zresetowano czas sesji do zera dla celów demonstracyjnych.', 'info');
                  }}
                  className="px-2.5 py-1 bg-slate-800 hover:text-white rounded-md flex items-center gap-1 text-slate-300"
                >
                  <RotateCcw className="w-3 h-3" /> Reset sesji
                </button>
              </div>
            </div>

            {/* PRAWA KOLUMNA: MACIERZ TESTÓW OBEJŚCIA I INSPEKTOR ENGINE */}
            <div className="w-full xl:w-96 border-t xl:border-t-0 xl:border-l border-slate-800 bg-slate-900/40 p-4 flex flex-col gap-4 overflow-y-auto shrink-0">
              {/* KARTA: TESTY PRÓB OBEJŚCIA SYSTEMU */}
              <div className="bg-slate-900/80 rounded-xl p-4 border border-slate-800">
                <h3 className="text-xs font-bold uppercase tracking-wider text-sky-400 mb-2 flex items-center gap-1.5">
                  <Shield className="w-3.5 h-3.5" /> Macierz Prób Obejścia (Bypass Matrix)
                </h3>
                <p className="text-[11px] text-slate-400 mb-3">
                  Przetestuj reakcję systemu na działania dziecka:
                </p>

                <div className="space-y-2">
                  {/* TC-11: Próba uruchomienia gry zablokowanej */}
                  <button
                    onClick={() => {
                      const target = apps.find((a) => !a.isEnabled) || apps[0];
                      simulateLaunchUnauthorizedApp(target);
                    }}
                    className="w-full text-left p-2 rounded-lg bg-rose-950/20 hover:bg-rose-950/40 border border-rose-800/40 text-xs flex items-center justify-between group transition-colors"
                  >
                    <div>
                      <div className="font-semibold text-rose-200 group-hover:text-white">
                        Próba włączenia zablokowanej gry (TC-11)
                      </div>
                      <div className="text-[10px] text-slate-400">Np. Minecraft / Roblox bez zgody</div>
                    </div>
                    <span className="text-[10px] font-mono text-rose-400 bg-rose-950/60 px-1.5 py-0.5 rounded border border-rose-800/40">
                      Blokuje
                    </span>
                  </button>

                  {/* NOWOŚĆ: Test ostrzeżenia 3 min (Głos + Popup) */}
                  <button
                    onClick={simulateThreeMinWarning}
                    className="w-full text-left p-2 rounded-lg bg-amber-950/20 hover:bg-amber-950/40 border border-amber-600/40 text-xs flex items-center justify-between group transition-colors"
                  >
                    <div>
                      <div className="font-semibold text-amber-200 group-hover:text-white flex items-center gap-1.5">
                        <Volume2 className="w-3.5 h-3.5 text-amber-400" />
                        Ostrzeżenie 3 min przed końcem
                      </div>
                      <div className="text-[10px] text-slate-400">Komunikat głosowy PL + popup na ekranie</div>
                    </div>
                    <span className="text-[10px] font-mono text-amber-400 bg-amber-950/60 px-1.5 py-0.5 rounded border border-amber-800/40">
                      Głos + Popup
                    </span>
                  </button>

                  <button
                    onClick={simulateClockRollback}
                    className="w-full text-left p-2 rounded-lg bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 text-xs flex items-center justify-between group transition-colors"
                  >
                    <div>
                      <div className="font-semibold text-slate-200 group-hover:text-white">
                        Cofnięcie zegara o 2h (TC-04)
                      </div>
                      <div className="text-[10px] text-slate-500">Test odporności czasu monotonicznego</div>
                    </div>
                    <span className="text-[10px] font-mono text-emerald-400 bg-emerald-950/60 px-1.5 py-0.5 rounded border border-emerald-800/40">
                      Odpiera
                    </span>
                  </button>

                  <button
                    onClick={simulateDateForward}
                    className="w-full text-left p-2 rounded-lg bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 text-xs flex items-center justify-between group transition-colors"
                  >
                    <div>
                      <div className="font-semibold text-slate-200 group-hover:text-white">
                        Przestawienie daty +1 dzień (TC-05)
                      </div>
                      <div className="text-[10px] text-slate-500">Próba wymuszenia resetu limitu</div>
                    </div>
                    <span className="text-[10px] font-mono text-emerald-400 bg-emerald-950/60 px-1.5 py-0.5 rounded border border-emerald-800/40">
                      Wykrywa
                    </span>
                  </button>

                  <button
                    onClick={simulateReboot}
                    className="w-full text-left p-2 rounded-lg bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 text-xs flex items-center justify-between group transition-colors"
                  >
                    <div>
                      <div className="font-semibold text-slate-200 group-hover:text-white">
                        Restart tabletu (TC-06)
                      </div>
                      <div className="text-[10px] text-slate-500">Weryfikacja checkpointu z bazy</div>
                    </div>
                    <span className="text-[10px] font-mono text-sky-400 bg-sky-950/60 px-1.5 py-0.5 rounded border border-sky-800/40">
                      Przywraca
                    </span>
                  </button>

                  <button
                    onClick={() => {
                      setIsOffline(!isOffline);
                      addLog(
                        'TEST_OFFLINE',
                        `Przełączono stan sieci: ${!isOffline ? 'TRYB SAMOLOTOWY (Offline)' : 'Połączono z Wi-Fi'}. Kontrola czasu działa w 100% lokalnie.`,
                        'info'
                      );
                    }}
                    className="w-full text-left p-2 rounded-lg bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 text-xs flex items-center justify-between group transition-colors"
                  >
                    <div>
                      <div className="font-semibold text-slate-200 group-hover:text-white">
                        Odcięcie internetu (TC-07)
                      </div>
                      <div className="text-[10px] text-slate-500">
                        Stan: {isOffline ? 'Tryb Samolotowy' : 'Wi-Fi aktywne'}
                      </div>
                    </div>
                    <span className="text-[10px] font-mono text-emerald-400 bg-emerald-950/60 px-1.5 py-0.5 rounded border border-emerald-800/40">
                      100% Offline
                    </span>
                  </button>

                  <button
                    onClick={simulateBruteForce}
                    className="w-full text-left p-2 rounded-lg bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 text-xs flex items-center justify-between group transition-colors"
                  >
                    <div>
                      <div className="font-semibold text-slate-200 group-hover:text-white">
                        Atak Brute Force na PIN (TC-08)
                      </div>
                      <div className="text-[10px] text-slate-500">5 błędnych prób zgadnięcia PIN</div>
                    </div>
                    <span className="text-[10px] font-mono text-amber-400 bg-amber-950/60 px-1.5 py-0.5 rounded border border-amber-800/40">
                      Blokada 60s
                    </span>
                  </button>
                </div>
              </div>

              {/* TELEMETRIA I STAN SILNIKA KOTLIN */}
              <div className="bg-slate-900/80 rounded-xl p-4 border border-slate-800">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 flex items-center justify-between">
                  <span>Stan Silnika (SessionTimerEngine)</span>
                  <span className="text-[10px] text-sky-400 font-mono">Kotlin Model</span>
                </h3>
                <div className="space-y-1.5 text-xs font-mono">
                  <div className="flex justify-between text-slate-400">
                    <span>Stan sesji:</span>
                    <strong
                      className={
                        sessionState === 'ACTIVE'
                          ? 'text-emerald-400'
                          : sessionState === 'WARNING'
                          ? 'text-amber-400'
                          : sessionState === 'TIME_EXPIRED'
                          ? 'text-rose-400'
                          : 'text-sky-400'
                      }
                    >
                      {sessionState}
                    </strong>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Dozwolonych gier:</span>
                    <span className="text-slate-200">{enabledApps.length} / {apps.length}</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Monotonic Uptime:</span>
                    <span className="text-slate-200">{formatTime(monotonicUptimeSeconds)}</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Dziś zużyto:</span>
                    <span className="text-slate-200">{formatTime(consumedSecondsToday)}</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Ostatni Checkpoint:</span>
                    <span className="text-slate-200">{formatTime(lastCheckpointSeconds)} (co 30s)</span>
                  </div>
                </div>
              </div>

              {/* DZIENNIK AUDYTU I ZDARZEŃ */}
              <div className="bg-slate-900/80 rounded-xl p-4 border border-slate-800 flex-1 flex flex-col min-h-[220px]">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2 flex items-center justify-between">
                  <span>Rejestr Audytu</span>
                  <span className="text-[10px] text-slate-500 font-normal">{tamperLogs.length} zdarzeń</span>
                </h3>
                <div className="flex-1 overflow-y-auto space-y-2 pr-1 max-h-56">
                  {tamperLogs.map((log) => (
                    <div
                      key={log.id}
                      className={`p-2 rounded border text-[11px] leading-tight ${
                        log.severity === 'alert'
                          ? 'bg-rose-950/30 border-rose-800/50 text-rose-300'
                          : log.severity === 'warning'
                          ? 'bg-amber-950/30 border-amber-800/50 text-amber-300'
                          : 'bg-slate-800/40 border-slate-700/40 text-slate-300'
                      }`}
                    >
                      <div className="flex items-center justify-between font-mono text-[10px] opacity-75 mb-0.5">
                        <span className="font-semibold">{log.eventType}</span>
                        <span>{log.timestamp}</span>
                      </div>
                      <div>{log.details}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </>
        )}

        {/* WIDOK DOKUMENTACJI: ANALIZA WYKONALNOŚCI */}
        {mainView === 'feasibility' && (
          <div className="flex-1 p-6 lg:p-10 max-w-4xl mx-auto overflow-y-auto">
            <div className="prose prose-invert max-w-none text-slate-300">
              <h1 className="text-2xl font-bold text-white mb-2">Analiza Wykonalności: Android 13+</h1>
              <p className="text-sm text-slate-400 mb-6">
                Rzetelna weryfikacja techniczna możliwości aplikacji kontroli rodzicielskiej na współdzielonym tablecie z kontem dorosłego.
              </p>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 my-6">
                <div className="p-4 bg-emerald-950/20 border border-emerald-800/50 rounded-xl">
                  <h3 className="font-bold text-emerald-300 text-sm mb-2 flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4" /> Niezawodne w Zwykłej Aplikacji (Standard App)
                  </h3>
                  <ul className="text-xs space-y-2 text-slate-300">
                    <li>· <strong>Dedykowany Kids Launcher</strong>: wyświetlanie wyłącznie dozwolonych kafelków aplikacji.</li>
                    <li>· <strong>Blokada nieautoryzowanych gier</strong>: detekcja aktywnych pakietów przez UsageStatsManager.</li>
                    <li>· <strong>Licznik sesji odporny na manipulacje</strong>: czas monotoniczny procesora (SystemClock.elapsedRealtime).</li>
                    <li>· <strong>Zapis stanu do DataStore/Room</strong>: punkty kontrolne co 30s odporne na nagły restart.</li>
                    <li>· <strong>Weryfikacja wyjścia i odblokowań PIN-em</strong>: solony hash SHA-256 z blokadą po 5 błędnych próbach.</li>
                  </ul>
                </div>

                <div className="p-4 bg-rose-950/20 border border-rose-800/50 rounded-xl">
                  <h3 className="font-bold text-rose-300 text-sm mb-2 flex items-center gap-2">
                    <XCircle className="w-4 h-4" /> Niemożliwe Bez Uprawnień Device Owner (MDM)
                  </h3>
                  <ul className="text-xs space-y-2 text-slate-300">
                    <li>· <strong>Całkowita blokada Ustawień systemowych</strong>: zwykły launcher nie zablokuje wejścia w ustawienia z paska stanu.</li>
                    <li>· <strong>Wyłączenie paska powiadomień</strong>: wymaga DevicePolicyManager.setStatusBarDisabled().</li>
                    <li>· <strong>Ochrona przed trybem awaryjnym (Safe Mode)</strong>: sprzętowy restart omija aplikacje 3rd-party.</li>
                    <li>· <strong>Zablokowanie "Wymuś zatrzymanie"</strong>: w menu aplikacji każdy użytkownik z prawami może ubić proces.</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* WIDOK DOKUMENTACJI: ADR 0001 */}
        {mainView === 'adr' && (
          <div className="flex-1 p-6 lg:p-10 max-w-4xl mx-auto overflow-y-auto">
            <div className="prose prose-invert max-w-none text-slate-300">
              <h1 className="text-2xl font-bold text-white mb-2">ADR 0001: Lokalny i Odporny Zegar Sesji</h1>
              <p className="text-sm text-slate-400 mb-6">
                Architektura odmierzania czasu offline bez podatności na manipulacje zegarem RTC i zmianę stref czasowych.
              </p>

              <div className="p-4 bg-slate-900 rounded-xl border border-slate-800 space-y-4 text-xs leading-relaxed">
                <div>
                  <h3 className="font-bold text-sky-400 text-sm mb-1">1. Problem manipulacji Wall Clock</h3>
                  <p>
                    Dzieci regularnie obchodzą systemy kontroli czasu poprzez wejście w Ustawienia daty i godziny i cofnięcie czasu o kilka godzin. Zastosowanie <code className="text-slate-200">System.currentTimeMillis()</code> powoduje natychmiastowe wydłużenie czasu gry.
                  </p>
                </div>

                <div>
                  <h3 className="font-bold text-sky-400 text-sm mb-1">2. Rozwiązanie: SystemClock.elapsedRealtime()</h3>
                  <p>
                    Używamy czasu monotonicznego dostarczanego przez jądro systemu Linux/Android. Czas ten mierzy liczbę milisekund od momentu włączenia urządzenia, wliczając deep sleep CPU. Nie zależy od strefy czasowej, sieci ani zegara RTC.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* WIDOK DOKUMENTACJI: SCENARIUSZE TESTOWE */}
        {mainView === 'tests' && (
          <div className="flex-1 p-6 lg:p-10 max-w-4xl mx-auto overflow-y-auto">
            <div className="prose prose-invert max-w-none text-slate-300">
              <h1 className="text-2xl font-bold text-white mb-2">Procedura Testów Obejścia (Bypass Matrix)</h1>
              <p className="text-sm text-slate-400 mb-6">
                Checklista manualnej weryfikacji aplikacji na fizycznym tablecie lub w symulatorze.
              </p>

              <div className="space-y-3">
                {[
                  { id: 'TC-01', title: 'Wciśnięcie Home', desc: 'Dziecko naciska Home w grze -> powrót do kącika dziecka, czas nadal leci.', status: 'Zgodne' },
                  { id: 'TC-02', title: 'Przycisk Back (Wstecz)', desc: 'Wielokrotne klikanie Wstecz -> OnBackPressedCallback zapobiega wyjściu do czarnego ekranu.', status: 'Zgodne' },
                  { id: 'TC-03', title: 'Recent Apps', desc: 'Przełączanie aplikacji -> w zwykłym launcherze Android wyświetla miniaturki, wymaga Device Owner do pełnej blokady.', status: 'Ograniczone' },
                  { id: 'TC-04', title: 'Cofnięcie zegara o 2h', desc: 'Dziecko cofa zegar w Ustawieniach -> czas monotoniczny nie ulega zmianie, brak darmowego czasu.', status: 'Zgodne' },
                  { id: 'TC-05', title: 'Przestawienie daty +1 dzień', desc: 'Próba zresetowania limitu -> silnik weryfikuje interwał monotoniczny przed resetem.', status: 'Zgodne' },
                  { id: 'TC-06', title: 'Restart tabletu (Reboot)', desc: 'Wyłączenie i włączenie zasilania -> odzyskanie zużycia z ostatniego punktu kontrolnego.', status: 'Zgodne' },
                  { id: 'TC-07', title: 'Brak internetu (Offline)', desc: 'Tryb samolotowy -> 100% funkcji limitów i blokad działa lokalnie bez chmury.', status: 'Zgodne' },
                  { id: 'TC-08', title: 'Atak Brute Force na PIN', desc: '5 błędnych prób -> 60 sekund blokady klawiatury numerycznej.', status: 'Zgodne' },
                  { id: 'TC-11', title: 'Próba włączenia zablokowanej gry', desc: 'Dziecko próbuje otworzyć niedozwoloną grę z linku lub recents -> natychmiastowe przechwycenie i ekran blokady.', status: 'Zgodne' },
                ].map((tc) => (
                  <div key={tc.id} className="p-3 bg-slate-900 rounded-lg border border-slate-800 flex items-center justify-between text-xs">
                    <div>
                      <div className="font-bold text-white flex items-center gap-2">
                        <span className="font-mono text-sky-400">{tc.id}</span>
                        <span>{tc.title}</span>
                      </div>
                      <div className="text-slate-400 mt-0.5">{tc.desc}</div>
                    </div>
                    <span
                      className={`px-2 py-0.5 rounded font-mono text-[10px] ${
                        tc.status === 'Zgodne'
                          ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-800/60'
                          : 'bg-amber-950/80 text-amber-300 border border-amber-800/60'
                      }`}
                    >
                      {tc.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </main>

      {/* MODAL: PROŚBA O NOWĄ GRĘ / APLIKACJĘ (KATALOG APLIKACJI TABLETU) */}
      {showRequestAppModal && (
        <div className="fixed inset-0 bg-slate-950/85 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fadeIn">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-lg w-full shadow-2xl flex flex-col max-h-[85vh]">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-4">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-sky-600/20 border border-sky-500/30 flex items-center justify-center text-sky-400">
                  <Gamepad2 className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-white text-base">Gry i Aplikacje na Tablecie</h3>
                  <p className="text-xs text-slate-400">Zatwierdź nową grę dla dziecka za pomocą kodu PIN</p>
                </div>
              </div>
              <button
                onClick={() => setShowRequestAppModal(false)}
                className="text-xs text-slate-400 hover:text-white px-2 py-1 rounded bg-slate-800"
              >
                Zamknij
              </button>
            </div>

            {/* Wyszukiwarka */}
            <div className="relative mb-3">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Szukaj gry lub programu na tablecie..."
                value={appSearchQuery}
                onChange={(e) => setAppSearchQuery(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-4 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-sky-500"
              />
            </div>

            {/* Lista aplikacji tabletu */}
            <div className="flex-1 overflow-y-auto space-y-2 pr-1">
              {apps
                .filter(
                  (a) =>
                    a.displayName.toLowerCase().includes(appSearchQuery.toLowerCase()) ||
                    a.category.toLowerCase().includes(appSearchQuery.toLowerCase())
                )
                .map((app) => (
                  <div
                    key={app.id}
                    className="p-3 bg-slate-800/40 rounded-xl border border-slate-800 flex items-center justify-between gap-3"
                  >
                    <div className="flex items-center gap-3">
                      <div
                        className="w-10 h-10 rounded-xl flex items-center justify-center text-white shrink-0 shadow-sm"
                        style={{ backgroundColor: app.accentColor }}
                      >
                        {app.iconType === 'game' ? <Gamepad2 className="w-5 h-5" /> : <Tv className="w-5 h-5" />}
                      </div>
                      <div>
                        <div className="text-xs font-semibold text-white flex items-center gap-1.5">
                          {app.displayName}
                          {app.ageRating && (
                            <span className="text-[9px] font-mono px-1 rounded bg-slate-700/80 text-slate-300">
                              {app.ageRating}
                            </span>
                          )}
                        </div>
                        <div className="text-[10px] text-slate-400 line-clamp-1">{app.description}</div>
                      </div>
                    </div>

                    <div className="shrink-0">
                      {app.isEnabled ? (
                        <span className="text-[10px] font-medium text-emerald-400 bg-emerald-950/60 px-2 py-1 rounded border border-emerald-800/40 flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3" /> W Kąciku
                        </span>
                      ) : (
                        <button
                          onClick={() => {
                            setPendingUnlockApp(app);
                            setShowRequestAppModal(false);
                            setPinInput('');
                            setPinErrorMessage(null);
                            setShowPinModal(true);
                          }}
                          className="px-2.5 py-1.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-xs font-semibold flex items-center gap-1 shadow-sm transition-colors"
                        >
                          <Unlock className="w-3 h-3" /> Zezwól (PIN)
                        </button>
                      )}
                    </div>
                  </div>
                ))}
            </div>

            <div className="pt-3 border-t border-slate-800 text-[11px] text-slate-400 flex items-center justify-between mt-3">
              <span>Nowe gry z Google Play instaluje rodzic w swoim profilu.</span>
              <button
                onClick={() => {
                  setShowRequestAppModal(false);
                  setPinInput('');
                  setPinErrorMessage(null);
                  setShowPinModal(true);
                }}
                className="text-sky-400 hover:underline"
              >
                Otwórz pełny panel rodzica
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: WPROWADZENIE KODU PIN RODZICA */}
      {showPinModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fadeIn">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-xs w-full shadow-2xl flex flex-col items-center">
            <div className="w-12 h-12 rounded-2xl bg-sky-600/20 border border-sky-500/30 flex items-center justify-center text-sky-400 mb-3">
              <Lock className="w-6 h-6" />
            </div>
            <h3 className="font-bold text-white text-base">
              {pendingUnlockApp ? `Zezwolenie na grę: ${pendingUnlockApp.displayName}` : 'Autoryzacja Rodzica'}
            </h3>
            <p className="text-xs text-slate-400 mb-4 text-center">
              Wprowadź kod PIN rodzica (domyślny: <span className="text-sky-300 font-mono font-bold">1234</span>)
            </p>

            {/* Stan blokady brute-force */}
            {lockoutRemainingSeconds > 0 ? (
              <div className="p-4 bg-rose-950/40 border border-rose-800/60 rounded-xl text-center mb-4">
                <AlertTriangle className="w-6 h-6 text-rose-400 mx-auto mb-1" />
                <span className="text-xs font-semibold text-rose-300 block">Klawiatura zablokowana!</span>
                <span className="text-lg font-mono font-bold text-white block my-1">
                  {lockoutRemainingSeconds}s
                </span>
                <span className="text-[10px] text-slate-400">Ochrona przed zgadywaniem PIN</span>
              </div>
            ) : (
              <>
                {/* Wskaźniki wprowadzonych cyfr */}
                <div className="flex gap-3 mb-4">
                  {[0, 1, 2, 3].map((i) => (
                    <div
                      key={i}
                      className={`w-3.5 h-3.5 rounded-full transition-all ${
                        i < pinInput.length
                          ? 'bg-sky-400 scale-110 shadow-sm shadow-sky-400/50'
                          : 'bg-slate-800 border border-slate-700'
                      }`}
                    />
                  ))}
                </div>

                {pinErrorMessage && (
                  <div className="text-rose-400 text-xs mb-3 text-center">{pinErrorMessage}</div>
                )}

                {/* Klawiatura numeryczna 3x4 */}
                <div className="grid grid-cols-3 gap-2 w-full mb-4">
                  {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', 'OK'].map((k) => (
                    <button
                      key={k}
                      onClick={() => {
                        if (k === 'C') setPinInput('');
                        else if (k === 'OK') verifyPin(pinInput);
                        else handlePinDigit(k);
                      }}
                      className="h-12 rounded-xl bg-slate-800/80 hover:bg-slate-700 text-white font-mono font-bold text-base active:scale-95 transition-all flex items-center justify-center border border-slate-700/50"
                    >
                      {k}
                    </button>
                  ))}
                </div>
              </>
            )}

            <button
              onClick={() => {
                setShowPinModal(false);
                setPendingUnlockApp(null);
                setPinInput('');
                setPinErrorMessage(null);
              }}
              className="text-xs text-slate-400 hover:text-white"
            >
              Anuluj i wróć
            </button>
          </div>
        </div>
      )}

      {/* MODAL: OSTRZEŻENIE 3 MINUTY PRZED KOŃCEM CZASU (GŁOS + POPUP) */}
      {showThreeMinWarningModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-fadeIn">
          <div className="bg-slate-900 border-2 border-amber-500 rounded-3xl p-6 max-w-sm w-full text-center shadow-2xl shadow-amber-950/60 relative">
            <div className="w-16 h-16 rounded-2xl bg-amber-500/20 border border-amber-500/40 text-amber-400 flex items-center justify-center mx-auto mb-3 animate-pulse">
              <Volume2 className="w-8 h-8" />
            </div>
            <span className="px-3 py-1 rounded-full text-[11px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40 tracking-wider">
              KOMUNIKAT GŁOSOWY & POPUP
            </span>
            <h3 className="text-xl font-bold text-white mt-3 mb-2">Za 3 minuty koniec gry!</h3>
            <p className="text-slate-300 text-sm mb-5 leading-relaxed">
              Zbliża się koniec Twojego limitu na dziś. Dokończ zadanie i zapisz stan gry, zanim ekran zostanie zablokowany.
            </p>
            <div className="flex flex-col gap-2.5">
              <button
                onClick={() => {
                  speakWarningVoice("Uwaga! Za trzy minuty koniec gry. Dokończ poziom i zapisz stan.");
                }}
                className="w-full py-2.5 px-4 bg-slate-800 hover:bg-slate-700 text-amber-300 font-medium rounded-xl text-xs flex items-center justify-center gap-2 border border-slate-700 transition-colors"
              >
                <Volume2 className="w-4 h-4 text-amber-400" /> Odtwórz ponownie komunikat głosowy
              </button>
              <button
                onClick={() => setShowThreeMinWarningModal(false)}
                className="w-full py-3 px-4 bg-amber-500 hover:bg-amber-600 text-slate-950 font-bold rounded-xl text-sm transition-colors shadow-lg shadow-amber-500/25"
              >
                Rozumiem, dokończę grę
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
