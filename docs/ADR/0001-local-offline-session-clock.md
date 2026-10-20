# ADR 0001: Lokalny i Odporny na Manipulacje Zegar Sesji (Offline Monotonic Clock)

## Status
Zatwierdzony (Etap 0 — PoC)

## Kontekst
Jednym z głównych problemów aplikacji kontroli rodzicielskiej na Androidzie jest zależność od czasu sieciowego lub podatność na manipulacje lokalnym zegarem czasu rzeczywistego (Wall Clock). Dzieci często omijają limity poprzez:
1. Odłączenie Wi-Fi (brak możliwości synchronizacji z serwerem).
2. Zmianę daty/godziny w Ustawieniach Androida wstecz o kilka godzin.
3. Restartowanie urządzenia w nadziei na wyzerowanie licznika sesji.

Aplikacja musi działać w 100% offline, precyzyjnie naliczać czas spędzony w dozwolonych grach oraz uniemożliwiać resetowanie czasu przez proste sztuczki systemowe.

## Decyzja Architektoniczna

1. **Czas Monotoniczny do odmierzania sesji (`SystemClock.elapsedRealtime()`)**:
   - Wykorzystujemy wyłącznie czas monotoniczny procesora, który odmierza milisekundy od momentu włączenia urządzenia, wliczając stany uśpienia CPU (`sleep`).
   - Czas monotoniczny nie podlega modyfikacji przez użytkownika, strefy czasowe, synchronizację NTP ani ręczną zmianę w Ustawieniach.

2. **Cykliczne Punkty Kontrolne (Checkpoints)**:
   - Co 30 sekund aktywności, a także natychmiast przy zdarzeniach `onPause()` i `onStop()`, silnik zapisuje skonsumowane sekundy do trwałego magazynu (DataStore/Room).
   - Zapis zawiera: `consumedSecondsToday`, `lastBootElapsedRealtime`, `calendarDayHash`.

3. **Wykrywanie Restartu Urządzenia (Reboot Detection)**:
   - Przy uruchomieniu aplikacji porównujemy bieżący `elapsedRealtime()` z zapisanym. Jeśli bieżący jest mniejszy niż poprzedni, wiemy że nastąpił restart urządzenia.
   - Po restarcie skonsumowany czas z danego dnia NIE JEST zerowany — wznawiamy odliczanie od zapisanego punktu kontrolnego.

4. **Wykrywanie Zmiany Dnia Kalendarzowego**:
   - Do resetu limitu dziennego (o północy) używamy daty kalendarzowej urządzenia.
   - Aby zapobiec oszustwu polegającemu na przestawieniu daty o dzień w przód (by zresetować limit), silnik wymaga upływu co najmniej 12 godzin monotonicznego czasu pracy lub potwierdzenia synchronizacji czasu po wykryciu połączenia sieciowego. Jeśli wykryto anomalny skok daty bez upływu czasu monotonicznego, oznaczana jest flaga ostrzegawcza.

## Konsekwencje

### Pozytywne:
- Aplikacja w pełni działa w trybie samolotowym, w podróży, bez dostępu do Internetu.
- Przestawienie zegara o 3 godziny w tył nie daje dziecku ani minuty dodatkowego czasu.
- Bezpieczeństwo danych i brak utraty stanu po nagłym wyłączeniu baterii (maksymalna utrata to 30 sekund między checkpointami).

### Negatywne / Ograniczenia:
- Jeśli urządzenie zostanie wyłączone na całą noc (bateria padnie przed północą), po włączeniu rano przy braku sieci system polega na zegarze RTC płyty głównej do rozpoznania nowego dnia.
