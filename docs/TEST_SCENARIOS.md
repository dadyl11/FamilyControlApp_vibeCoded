# Scenariusze Testowe Obejścia Zabezpieczeń i Poprawności (Bypass Matrix)

Ten dokument opisuje procedurę weryfikacji i testów odporności aplikacji Family Control na próby manipulacji przez dziecko na współdzielonym tablecie.

---

## Tabela Scenariuszy Testowych

| ID | Scenariusz / Wektor ataku | Działanie dziecka | Oczekiwane zachowanie (PoC) | Wynik testu |
| :--- | :--- | :--- | :--- | :--- |
| **TC-01** | **Wciśnięcie przycisku Home** | Dziecko gra w dozwoloną grę i naciska przycisk Home na pasku nawigacji. | Aplikacja powraca do pulpitu Family Control (Kids Launcher). Pozostały czas sesji jest nadal widoczny. | **ZGODNE** |
| **TC-02** | **Przycisk Back (Wstecz)** | Dziecko wielokrotnie wciska przycisk Wstecz na ekranie launchera. | Klawisz wstecz jest zablokowany (`onBackPressed` jest przechwycone), launcher nie zamyka się do czarnego ekranu. | **ZGODNE** |
| **TC-03** | **Przycisk Recent Apps (Ostatnie)** | Dziecko otwiera listę uruchomionych aplikacji, by przełączyć się na niedozwoloną aplikację. | W zwykłej aplikacji system wyświetla listę miniaturek. Jeśli dziecko kliknie nieautoryzowaną aplikację, launcher wykrywa utratę fokusu i po upływie czasu zgłasza ostrzeżenie. (W trybie Device Owner lista Recents jest całkowicie wyłączona). | **Częściowe (zgodnie ze specyfikacją)** |
| **TC-04** | **Cofnięcie zegara systemowego** | Dziecko wchodzi w Ustawienia i cofa zegar o 2 godziny wstecz. | Licznik sesji oparty o `SystemClock.elapsedRealtime()` nie cofa się. Pozostały czas ubywa w normalnym tempie. | **ZGODNE** |
| **TC-05** | **Przestawienie daty na kolejny dzień** | Dziecko przestawia datę o 1 dzień w przód, licząc na nowy limit dzienny. | Silnik wykrywa niezgodność upływu czasu monotonicznego ze skokiem daty (brak minimalnego interwału) i blokuje fałszywy reset. | **ZGODNE** |
| **TC-06** | **Restart tabletu (Reboot)** | Dziecko wyłącza i włącza tablet, licząc na skasowanie zużytych minut. | Po restarcie system odpala aplikację (jako domyślny launcher). Skonsumowany czas jest wczytywany z ostatniego punktu kontrolnego (DataStore). | **ZGODNE** |
| **TC-07** | **Brak połączenia z siecią (Offline)** | Dziecko włącza tryb samolotowy lub wyłącza Wi-Fi. | Wszystkie funkcje kontroli czasu, limit sesji, blokada PIN oraz przejścia stanów `WARNING` / `TIME_EXPIRED` działają bez zmian w 100% lokalnie. | **ZGODNE** |
| **TC-08** | **Atak Brute Force na PIN rodzica** | Dziecko próbuje losowo zgadywać 4-cyfrowy PIN rodzica. | Po 5 nieudanych próbach klawiatura PIN jest blokowana na 60 sekund, a czas blokady rośnie wykładniczo. Informacja o nieudanych próbach trafia do rejestru audytu. | **ZGODNE** |
| **TC-09** | **Upłynięcie czasu podczas gry** | Dziecko gra w dozwoloną grę do ostatniej sekundy limitu. | Na 5 min przed końcem pojawia się ostrzeżenie dźwiękowo-wizualne (`WARNING`). Po upływie czasu gra zostaje przesłonięta ekranem `TIME_EXPIRED`. | **ZGODNE** |
| **TC-10** | **Wymuszone zatrzymanie w Ustawieniach** | Dziecko wchodzi w Ustawienia -> Aplikacje -> Family Control -> Wymuś zatrzymanie. | W zwykłym trybie Android pozwala zatrzymać aplikację. Jednak przy ponownym naciśnięciu Home launcher uruchamia się ponownie z zachowanym stanem z bazy. (Pełna ochrona przed zatrzymaniem wymaga Device Owner). | **Udokumentowane ograniczenie platformy** |
| **TC-11** | **Próba uruchomienia innej aplikacji / prośba o grę** | Dziecko próbuje otworzyć niedozwoloną grę (np. przez link, powiadomienie) lub klika kafelek prośby o nową aplikację. | Niedozwolony pakiet jest natychmiast przechwytywany i blokowany. Kafelek prośby otwiera modal autoryzacji PIN dla rodzica z listą aplikacji do zatwierdzenia na miejscu. | **ZGODNE** |

---

## Instrukcja Przeprowadzenia Testu na Fizycznym Tablecie

1. Zainstaluj plik APK na tablecie z Androidem 13+.
2. Podczas pierwszego uruchomienia ustaw kod PIN rodzica (np. `1234`).
3. Wybierz Family Control jako domyślną aplikację ekranu głównego (Ustawienia -> Aplikacje domyślne -> Aplikacja ekranu głównego).
4. Nadaj uprawnienie "Dostęp do danych o użyciu" (`PACKAGE_USAGE_STATS`).
5. Ustaw limit sesji na 2 minuty do celów testowych.
6. Uruchom dozwoloną grę. Zweryfikuj pojawienie się ostrzeżenia po 1 minucie i ekranu blokady po 2 minutach.
7. Spróbuj cofnąć czas zegarka w tablecie — potwierdź brak resetu licznika.
