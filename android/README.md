# Family Control — Aplikacja Android (Etap 0 & PoC)

Lokalny system kontroli czasu ekranowego na współdzielonym tablecie z systemem Android 13+ z kontem dorosłego.

## Wymagania systemowe
- Android Studio Hedgehog (2023.1.1) lub nowszy (Ladybug / Iguana)
- JDK 17
- Urządzenie z Androidem 13 (API 33) lub nowszym (Samsung Galaxy Tab, Lenovo Tab, Pixel Tablet lub emulator)

## Budowanie i Instalacja

### 1. Kompilacja pliku APK debugowania
```bash
cd android
./gradlew assembleDebug
```
Wygenerowany plik APK znajdziesz w:
`android/app/build/outputs/apk/debug/app-debug.apk`

### 2. Instalacja na podłączonym tablecie przez ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Konfiguracja uprawnień na tablecie
Po zainstalowaniu aplikacji:
1. Uruchom aplikację na tablecie.
2. Nadaj uprawnienie **Dostęp do danych o użyciu** (`Ustawienia` -> `Aplikacje` -> `Dostęp specjalny` -> `Dostęp do danych o użyciu` -> zaznacz **Family Control**).
3. Ustaw Family Control jako domyślną aplikację ekranu głównego (`Ustawienia` -> `Aplikacje` -> `Aplikacje domyślne` -> `Aplikacja ekranu głównego` -> wybierz **Family Control**).
4. Domyślny kod PIN rodzica to `1234` (kod ratunkowy: `88990011`).

## Uruchamianie testów jednostkowych
```bash
cd android
./gradlew test
```
Testy weryfikują m.in.:
- Niezależność czasu monotonicznego od manipulacji zegarem czasu rzeczywistego (Wall Clock)
- Płynne przejścia stanów `ACTIVE` -> `WARNING` -> `TIME_EXPIRED`
- Ochronę przed atakiem brute force na PIN (blokada po 5 próbach)
- Odtwarzanie stanu z checkpointu po restarcie tabletu
