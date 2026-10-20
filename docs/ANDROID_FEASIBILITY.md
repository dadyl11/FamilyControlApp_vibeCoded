# Analiza Wykonalności Technicznej: Family Control na Androidzie 13+
**Wersja dokumentu**: 2.0 (Etap 0 — PoC)  
**Data**: 2026-10-19  
**Platforma docelowa**: Urządzenia z systemem Android 13 (API 33), Android 14 (API 34) i Android 15 (API 35) (np. Samsung Galaxy Tab A/S, Lenovo Tab M/P, Google Pixel Tablet).  
**Scenariusz podstawowy**: Tablet domowy współdzielony między dorosłym a dzieckiem, na którym zalogowane pozostaje konto Google dorosłego.

---

## 1. Wprowadzenie i Ramy Prawne/Systemowe

Zarządzanie czasem i aplikacjami na współczesnym Androidzie (13+) jest obwarowane rygorystycznymi restrykcjami bezpieczeństwa (Scoped Storage, ograniczenia uruchamiania aktywności z tła — *Background Activity Launches*, ochrona przed nakładkami ekranowymi *Overlay/Tapjacking*, polityki Google Play wobec `AccessibilityService`).

W architekturze Androida istnieją dwa fundamentalnie różne poziomy kontroli:
1. **Tryb Zwykłej Aplikacji (Standard App / Custom Home Launcher)**: Aplikacja instalowana ze sklepu lub pliku APK, bez uprawnień administracyjnych Device Owner.
2. **Tryb Zarządzany (Device Owner / Enterprise MDM / Dedicated Kiosk)**: Aplikacja zarejestrowana jako właściciel urządzenia podczas początkowej konfiguracji (przez `dpm set-device-owner` przez ADB lub kod QR / NFC przy czystym urządzeniu).

Celem niniejszego dokumentu jest rzetelne i bezkompromisowe rozdzielenie tego, co jest możliwe do osiągnięcia na współdzielonym tablecie z kontem dorosłego bez usuwania danych, a co wymaga trybu Device Owner.

---

## 2. Macierz Możliwości i Poziomów Ochrony

| Obszar kontroli | Poziom w Zwykłej Aplikacji (Custom Launcher + UsageStats) | Poziom w Device Owner (Lock Task Mode) | Realna odporność na obejście w PoC |
| :--- | :--- | :--- | :--- |
| **Lista dozwolonych aplikacji** | **Wysoki (Niezawodna)**: Pulpit dziecka wyświetla wyłącznie skróty dozwolonych pakietów zdefiniowanych przez rodzica. | **Pełny**: Tylko dozwolone pakiety mogą być w ogóle uruchomione. | Dziecko nie widzi innych aplikacji na pulpicie. Próba uruchomienia przez powiadomienia wymaga zablokowania paska stanu. |
| **Licznik czasu sesji i dnia** | **Wysoki (Niezawodna offline)**: Odmierzanie za pomocą czasu monotonicznego `SystemClock.elapsedRealtime()`. | **Wysoki (Niezawodna offline)**. | Odporne na manipulację zegarem systemowym (`Wall Clock`). |
| **Wyjście do pulpitu dorosłego** | **Średni (Wymaga PIN)**: Kliknięcie przycisku "Tryb Rodzica" otwiera bezpieczny dialog PIN. | **Pełny**: Brak możliwości wyjścia bez PINu. | Przycisk Home kieruje z powrotem do Launchera dziecka, chyba że dziecko zmieni domyślną aplikację Home w Ustawieniach. |
| **Blokada Ustawień systemowych (`com.android.settings`)** | **Ograniczony**: Zwykła aplikacja nie może wyłączyć aplikacji Ustawienia; może jedynie przechwytywać jej otwarcie i wymuszać powrót do launchera. | **Pełny**: Całkowita blokada `com.android.settings` i paska szybkich skrótów. | Jeśli dziecko dostanie się do Ustawień (np. przez pasek stanu lub skrót asystenta), może zmienić domyślny launcher lub odinstalować aplikację. |
| **Ochrona przed przyciskiem Recent Apps (Ostatnie)** | **Częściowa**: Przechwytywanie utraty fokusu okna (`onWindowFocusChanged`) i natychmiastowe przywracanie launchera. | **Pełna**: Recent Apps wyłączone sprzętowo w Lock Task Mode. | Na zwykłym Androidzie przycisk Recents pokazuje miniatury innych aplikacji. |
| **Przerwanie działania (Wymuszone zatrzymanie / Safe Mode)** | **Brak ochrony**: Każda zwykła aplikacja może zostać wymuszenie zatrzymana w menu Informacje o aplikacji. | **Pełna**: System uniemożliwia wyłączenie, wymuszenie zatrzymania i odinstalowanie. | Dziecko znające Androida może uruchomić tablet w trybie awaryjnym (Safe Mode) lub wyczyścić dane aplikacji. |
| **Wznowienie po upływie czasu (`TIME_EXPIRED`)** | **Umiarkowany**: Od Androida 10/13 tło nie może bezwarunkowo wywoływać `startActivity`. Wymaga `FullScreenIntent` (powiadomienie alarmowe) lub powrotu przez wciśnięcie Home. | **Pełny**: DPM natychmiastowo zamyka przypięte zadanie (`stopLockTask`). | Gdy czas upływa podczas gry dziecka, powiadomienie pełnoekranowe przesłania grę. |

---

## 3. Szczegółowa Analiza Mechanizmów Systemowych

### 3.1. Dedykowany Launcher (`android.intent.category.HOME`)
- **Jak to działa**: Aplikacja deklaruje w manifeście filtry:
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.HOME" />
      <category android:name="android.intent.category.DEFAULT" />
  </intent-filter>
  ```
- **Zalety**: Każde wciśnięcie przycisku Home lub gest powrotu do ekranu głównego zawsze ląduje w aplikacji Family Control. Na pulpicie widoczne są tylko zatwierdzone kafelki.
- **Ograniczenia**: Android wymaga, aby użytkownik sam wybrał domyślny ekran główny. Jeśli dziecko wejdzie do Ustawień systemowych -> Aplikacje domyślne, może przełączyć launcher z powrotem na domyślny launcher producenta (One UI Home, Lenovo Launcher, Pixel Launcher).

### 3.2. Czas Monotoniczny vs Czas Rzeczywisty
- **Problem**: Dzieci powszechnie obchodzą blokady czasu poprzez wejście w Ustawienia -> Data i Czas i cofnięcie zegara o 3 godziny lub zmianę strefy czasowej.
- **Rozwiązanie architektoniczne**:
  - `System.currentTimeMillis()` **NIE MOŻE** być używane do odliczania sesji.
  - Zamiast tego używamy `SystemClock.elapsedRealtime()`, który zwraca czas w milisekundach od uruchomienia urządzenia, wliczając czas uśpienia głębokiego (deep sleep).
  - W DataStore zapisywany jest stan zużytych sekund (`consumedSecondsToday`) oraz ostatni znany znacznik monotoniczny.
  - Okresowe punkty kontrolne (co 30 sekund) gwarantują, że nawet nagły restart nie kasuje zużytego czasu.

### 3.3. Restrykcje Background Activity Launch (BAL) na Androidzie 13+
- **Problem**: Na Androidzie 13+ usługa w tle nie może samoczynnie wywołać `context.startActivity(intent)` do wyświetlenia ekranu blokady, jeśli aplikacja nie znajduje się na pierwszym planie.
- **Rozwiązanie w PoC**:
  1. Zastosowanie usługi na pierwszym planie (`Foreground Service`) z trwałym powiadomieniem pokazującym pozostały czas.
  2. Po wyczerpaniu limitu usługa wysyła powiadomienie o wysokim priorytecie z `setFullScreenIntent(pendingIntent, true)` (tak jak budzik/połączenie przychodzące).
  3. Gdy dziecko spróbuje powrócić do launchera (np. klikając Home), launcher natychmiast blokuje start i wyświetla stan `TIME_EXPIRED`.

### 3.4. Dostęp do Statystyk Użycia (`UsageStatsManager`)
- Wymaga specjalnego uprawnienia `android.permission.PACKAGE_USAGE_STATS` (nadawanego manualnie w Ustawieniach dostępu do danych o użyciu).
- Pozwala monitorować, który pakiet jest aktualnie na pierwszym planie bez używania inwazyjnego i zakazanego przez Google Play `AccessibilityService`.

### 3.5. Próba Użycia Innej Aplikacji lub Gry (Niedozwolonej / Nowej)
Co dzieje się, gdy dziecko chce skorzystać z gry lub programu spoza zatwierdzonej listy:
1. **Domyślna izolacja w pulpicie (Brak App Drawera dorosłego)**:
   - Dziecko nie ma dostępu do pełnej listy aplikacji tabletu (nie widzi aplikacji bankowych dorosłego, poczty Gmail, komunikatorów ani gier dla starszych).
   - Widoczne są wyłącznie kafelki zatwierdzone przez rodzica na białej liście (`AllowedApp`).
2. **Próba uruchomienia niedozwolonej aplikacji drogą okrężną (link, asystent, powiadomienie)**:
   - `SessionMonitorService` z uprawnieniem `PACKAGE_USAGE_STATS` stale analizuje zdarzenia `UsageEvents.Event.ACTIVITY_RESUMED`.
   - W momencie wykrycia pakietu, którego nie ma w `policy.allowedPackages`:
     - System wysyła Intent powrotu do ekranu głównego Family Control (`ACTION_MAIN + CATEGORY_HOME`),
     - Wyświetlany jest ekran blokady z komunikatem: *"Aplikacja zablokowana. Poproś rodzica o zatwierdzenie"*.
3. **Procedura "Poproś o nową aplikację" (Request & Whitelist Flow)**:
   - W pulpicie dziecka dostępny jest dedykowany kafelek `[+] Poproś o nową aplikację`.
   - Dziecko może zgłosić chęć zagrania w grę już zainstalowaną na tablecie lub poprosić o zainstalowanie nowej.
   - Odblokowanie wymaga podania kodu PIN rodzica na miejscu:
     - Rodzic wprowadza PIN,
     - Wybiera aplikację z listy wszystkich zainstalowanych pakietów (`packageManager.getInstalledApplications`),
     - Włącza dla niej dostęp dla dziecka jednym przełącznikiem bez opuszczania trybu współdzielonego.
4. **Ochrona przed niekontrolowanymi zakupami w Google Play**:
   - Sklep Google Play (`com.android.vending`) jest domyślnie ukryty przed dzieckiem.
   - Dodatkowo zaleca się włączenie standardowej opcji Sklepu Play: *"Wymagaj uwierzytelnienia przy każdym zakupie"* (odcisk palca / hasło konta dorosłego).

---

## 4. Ograniczenia Niemożliwe do Ominięcia bez Device Owner

Poniższe funkcje **NIE SĄ MOŻLIWE** do zagwarantowania w 100% w zwykłej aplikacji bez uprawnień właściciela urządzenia:

1. **Całkowita blokada Ustawień systemowych**:
   Zwykły proces nie może odmówić systemowi otwarcia aplikacji `com.android.settings`. Może jedynie po fakcie wykryć zmianę pakietu i spróbować wywołać powrót.
2. **Blokada paska powiadomień i szybkich ustawień**:
   Dziecko może rozwinąć belkę z góry ekranu, kliknąć ikonę zębatki i wejść do konfiguracji systemu.
3. **Odinstalowanie aplikacji / Tryb awaryjny (Safe Mode)**:
   Uruchomienie tabletu z wciśniętym klawiszem głośności włącza Safe Mode, w którym aplikacje firm trzecich nie działają.
4. **Wymuszone zatrzymanie (Force Stop)**:
   W menu aplikacji każdy może kliknąć "Wymuś zatrzymanie", co natychmiast ubija procesy w tle i usługi Foreground Service.

---

## 5. Podsumowanie i Rekomendacja dla PoC

Dla współdzielonego tabletu z kontem dorosłego (gdzie nie chcemy resetować urządzenia do fabrycznych):
- **Wariant wybrany (PoC)**: Dedykowany launcher dla dziecka + monitor monotoniczny + bezpieczny PIN rodzica + FullScreenIntent po upływie czasu. Daje to wysoki komfort codziennej kontroli dla dzieci w wieku 4–11 lat, które nie posiadają zaawansowanej wiedzy o trybie awaryjnym Androida.
- **Wariant zaawansowany (Opcjonalny dla chętnych)**: Możliwość aktywacji trybu Device Owner bez czyszczenia danych za pomocą komendy ADB:
  ```bash
  adb shell dpm set-device-owner com.familycontrol.app/.receiver.FamilyDeviceAdminReceiver
  ```
  (Działa, jeśli na tablecie nie ma skonfigurowanych kont, lub przez odpowiedni profil zarządzany).
