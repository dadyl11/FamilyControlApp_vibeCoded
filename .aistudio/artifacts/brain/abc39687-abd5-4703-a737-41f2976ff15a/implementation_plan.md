# Family Control — Plan Realizacji PoC & Etapu 0 (v2.0)

System kontroli czasu ekranowego dla dzieci na współdzielonym tablecie z systemem Android (wersje 13+) z kontem dorosłego. Realizacja obejmuje analizę techniczną wykonalności, architekturę aplikacji natywnej Android w Kotlin/Jetpack Compose (`/android`), bezpieczny mechanizm odliczania czasu offline oraz interaktywny symulator PoC z panelem testów prób obejścia systemu.

> [!IMPORTANT]
> **Kluczowe decyzje projektowe zatwierdzone przez użytkownika**:
> - **Platforma docelowa**: Android 13+ (np. Samsung Galaxy Tab, Lenovo Tab, Pixel Tablet) na tablecie współdzielonym z dorosłym.
> - **Mechanizm kontroli w PoC**: Dedykowany launcher dla dziecka (Kids Kiosk Launcher) z autoryzacją wyjścia bezpiecznym kodem PIN rodzica.
> - **Struktura repozytorium**: Kompletny projekt Android w `/android` (Gradle Kotlin DSL, Jetpack Compose, Room/DataStore) oraz interaktywny symulator PoC w przeglądarce umożliwiający natychmiastowe testowanie logiki, stanów sesji i prób obejścia.
> - **Brak chmury w Etapie 0**: Zgodnie z wytycznymi backend Firebase, zdalny panel rodzica, Google TV oraz FunBox 7 są wyłączone z PoC i zostaną wdrożone dopiero po zatwierdzeniu wyników testów wykonalności.

---

### 1. Przegląd i Koncepcja PoC

- **Problem użytkownika**: Na współdzielonym tablecie domowym zalogowany jest dorosły (konto Google, poczta, płatności, przeglądarka). Dziecko powinno mieć dostęp wyłącznie do wybranych aplikacji (np. gry edukacyjne, odtwarzacz wideo, rysowanie) z rygorystycznym limitem czasu sesji i limitu dziennego, bez ryzyka przypadkowego kliknięcia w nieautoryzowane aplikacje czy zakupy.
- **Poziom ochrony w trybie standardowym (bez Device Owner)**: 
  - Aplikacja działa jako alternatywny ekran główny (Home / Launcher) z uprawnieniem do sprawdzania statystyk użycia (`PACKAGE_USAGE_STATS`).
  - Wyjście do pulpitu dorosłego wymaga podania 4- lub 6-cyfrowego kodu PIN (haszowanego z solą Argon2/PBKDF2 z licznikiem błędnych prób i blokadą czasową).
  - Wyczerpanie limitu blokuje dostęp do dozwolonych aplikacji i wymusza powrót do ekranu blokady (`TIME_EXPIRED`).
  - Uczciwe zakomunikowanie ograniczeń systemu: zwykła aplikacja ze sklepu nie zablokuje fizycznego restartu do Recovery czy odinstalowania w Safe Mode, dopóki tablet nie zostanie skonfigurowany w trybie Device Owner (zarządzanym przez MDM).

---

### 2. Architektura Systemu i Komponenty

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       INTERFEJS RODZICA (Parent Mode)                       │
│  - Konfiguracja PIN z procedurą odzyskiwania                                │
│  - Wybór dozwolonych aplikacji (Package Whitelist)                         │
│  - Limity dzienne i sesyjne (Dni powszednie vs Weekendy)                   │
│  - Podgląd statystyk użycia offline (Local DataStore / Room)                │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ PIN Auth & Transition
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                    TRYB DZIECKA (Kids Launcher Mode)                        │
│  - Kafelki zatwierdzonych aplikacji                                         │
│  - Zegar pozostałego czasu sesji i dnia (Monotoniczny czas w pamięci)       │
│  - Stany: ACTIVE ──► WARNING (np. < 5 min) ──► TIME_EXPIRED                 │
│  - Ochrona przed manipulacją zegarem (SystemClock.elapsedRealtime)         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Launch & Monitor Loop
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                 ENGINE MONITORINGU CZASU & STANU OFFLINE                    │
│  - SessionTimerManager: rejestracja cyklu życia (onPause/onResume)         │
│  - Trwałe punkty kontrolne (checkpoints co 30s) w DataStore/Room            │
│  - Wykrywanie cofnięcia zegara czasu rzeczywistego (Wall Clock Tampering)  │
│  - Obsługa prób obejścia (Home, Back, Recents, Force Stop, Offline)         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 3. Analiza Wykonalności Technicznej (`docs/ANDROID_FEASIBILITY.md`)

Dokument zawierać będzie rzetelną macierz możliwości i ograniczeń na Androidzie 13+:

| Funkcjonalność | Wykonalność w zwykłej aplikacji (Standard App) | Wykonalność w trybie Device Owner (MDM / Kiosk) | Ryzyko / Droga obejścia w PoC |
| :--- | :--- | :--- | :--- |
| **Dedykowany ekran dozwolonych aplikacji** | **Niezawodna**: Domyślny Launcher (`CATEGORY_HOME`). | **Pełna**: Lock Task Mode bez paska nawigacji. | Dziecko może spróbować zmienić domyślny launcher w Ustawieniach (wymaga zablokowania dostępu do `com.android.settings`). |
| **Licznik czasu sesji i dnia** | **Niezawodna offline**: `SystemClock.elapsedRealtime()`. | **Niezawodna offline**. | Dziecko zmienia czas systemowy w Ustawieniach (czas monotoniczny jest odporny na zmianę zegara). |
| **Powrót do blokady po upływie czasu** | **Możliwa z ograniczeniami**: Wymuszenie `bringToFront()` launchera lub pełnoekranowy `Activity`. | **Pełna**: Zamknięcie przypiętej aplikacji i powrót do ekranu blokady. | Android 13+ ogranicza uruchamianie Activity z tła (`BAL - Background Activity Launch restrictions`). Wymaga powiadomienia pełnoekranowego (`FullScreenIntent`) lub powrotu do Launchera. |
| **Blokada paska powiadomień i Ustawień** | **Częściowa**: Przechwytywanie utraty fokusu okna (`onWindowFocusChanged`). | **Pełna**: Blokada paska stanu przez `DevicePolicyManager.setStatusBarDisabled()`. | Rozwinięcie paska powiadomień i wejście w Ustawienia szybkich skrótów. |
| **Ochrona przed wymuszonym zatrzymaniem i restartem** | **Niemożliwa w zwykłej aplikacji**: Rodzic/dziecko z uprawnieniami może kliknąć "Wymuś zatrzymanie". | **Pełna**: Aplikacja nie może zostać zatrzymana ani odinstalowana. | Po restarcie tablet powraca do domyślnego Launchera (działa `BOOT_COMPLETED`). |

---

### 4. Zakres Implementacji w Repozytorium

1. **Dokumentacja techniczna**:
   - `docs/ANDROID_FEASIBILITY.md`: Pełne omówienie ograniczeń Android 13+, matryca uprawnień, instrukcja konfiguracji Device Owner przez ADB dla chętnych (`adb shell dpm set-device-owner ...`) oraz poziom ochrony zwykłego launchera.
   - `docs/ADR/0001-local-offline-session-clock.md`: Decyzja architektoniczna dotycząca odporności licznika na zmiany czasu i restarty.
   - `docs/TEST_SCENARIOS.md`: 10 scenariuszy testów obejścia (Home, Back, Recents, Zmiana czasu, Utrata zasilania, Wymuszone zatrzymanie, Brak sieci).

2. **Aplikacja Android w `/android`**:
   - Standardowa struktura Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`).
   - `AndroidManifest.xml` z deklaracją launchera (`ACTION_MAIN`, `CATEGORY_HOME`, `CATEGORY_DEFAULT`).
   - Warstwa Domain: `SessionTimerEngine`, `PolicyEvaluator`, `PinVerifier` (z bezpiecznym PBKDF2/SHA-256 + sól i licznik błędów), `AppWhitelistRepository`.
   - Warstwa Data: `RoomDatabase` dla historii sesji i `DataStore` dla polityk i weryfikatora PIN.
   - Warstwa UI (Jetpack Compose):
     - `KidsHomeScreen`: Siatka dozwolonych kafelków, pasek pozostałego czasu, animowany stan ostrzeżenia.
     - `TimeExpiredScreen`: Ekran blokady z wezwaniem do zakończenia sesji lub wprowadzenia PIN rodzica.
     - `ParentConfigScreen`: Ustawienia limitów, dodawanie/usuwanie aplikacji z listy dozwolonych, resetowanie PIN, statystyki.
     - `PinPadDialog`: Klawiatura numeryczna z maskowaniem, blokadą po 5 błędnych próbach na 60 sekund.

3. **Interaktywny Symulator PoC w Przeglądarce (`src/`)**:
   - Wierny symulator tabletu z Androidem 13+ z przełącznikiem trybów (Kids Mode / Parent Mode).
   - Silnik czasu rzeczywistego (symulacja czasu rzeczywistego i przyspieszonego 10x do testów).
   - Panel testów obejścia systemu ("Bypass & Tamper Matrix"):
     - Przycisk "Symuluj zmianę czasu systemowego w przód o 2h"
     - Przycisk "Symuluj wciśnięcie Home / Recent Apps"
     - Przycisk "Symuluj restart tabletu (Reboot)"
     - Przycisk "Symuluj odcięcie internetu (Offline test)"
     - Przycisk "Wielokrotne błędne próby PIN"
   - Podgląd stanu silnika: `ACTIVE`, `WARNING`, `TIME_EXPIRED`, `PARENT_MODE`, aktualne checkpointy zapisu.

---

### 5. Plan Weryfikacji i Testów

- **Kompilacja i Linting**: Sprawdzenie zgodności kodu TypeScript w symulatorze oraz struktury kodu Kotlin w `/android`.
- **Weryfikacja scenariuszy obejścia**:
  1. *Test monotoniczności*: Manipulacja zegarem systemowym nie dodaje ani nie odejmuje czasu dziecku.
  2. *Test restartu*: Po wznowieniu stan odczytywany jest z ostatniego checkpointu bez utraty informacji o zużyciu limitu.
  3. *Test PIN-u*: Blokada po 5 nieudanych próbach na 60 sekund zapobiega odgadnięciu przez dziecko.
  4. *Test przejścia stanów*: Automatyczne płynne przejście z `ACTIVE` do `WARNING` na 5 minut przed końcem, a następnie do `TIME_EXPIRED`.
