# T8306 Printer Test

Minimalna aplikacja diagnostyczna Android dla drukarki **Printronix T8306 / T8000** w sieci LAN.

Domyślna drukarka z testowanego stanowiska:

- nazwa widoczna na panelu: `P_100077`
- IP: `10.20.140.50`
- port: `9100`
- tryb: `ETHERNET / PGL / LP+`

## Funkcje v0.1

1. **TEST CONNECTION** – otwiera TCP do podanego `IP:port`, po czym zamyka połączenie. Nic nie wysyła do drukarki.
2. **PRINT PGL TEST LABEL** – po dodatkowym potwierdzeniu wysyła mały testowy formularz PGL.
3. **FIND PORT 9100 PRINTERS (/24)** – opcjonalnie sprawdza wyłącznie TCP/9100 w lokalnej podsieci telefonu `/24`. Skan nie uruchamia się automatycznie.
4. **COPY LOG** – kopiuje log diagnostyczny do schowka.

## Kompilacja przez GitHub Actions

1. Utwórz nowe repozytorium GitHub.
2. Wgraj całą zawartość tego projektu do głównego katalogu repo.
3. Zrób commit/push do `main` albo `master`.
4. Wejdź w **Actions → Build Android APK**.
5. Po zakończeniu builda pobierz artifact **T8306-Printer-Test-debug**.
6. W środku będzie `app-debug.apk`.

Workflow sam ustawia JDK 17, Gradle 8.9 i Android SDK 35, więc repozytorium nie wymaga plików Gradle Wrapper.

## Pierwszy test w pracy

Najpierw podłącz telefon do firmowego Wi‑Fi/LAN, z którego drukarka może być osiągalna.

1. Uruchom APK.
2. Zostaw `10.20.140.50` i `9100`.
3. Kliknij **TEST CONNECTION**.
4. Jeśli pojawi się `STATUS: ONLINE`, dopiero wtedy użyj **PRINT PGL TEST LABEL**.
5. Jeśli połączenie nie działa, skopiuj log przyciskiem **COPY LOG**.

## Ważne

- Aplikacja nie zmienia ustawień konfiguracji drukarki. Test PGL tworzy/odświeża formularz roboczy `ANDROIDTEST` i wykonuje go jeden raz.
- Skan sieci jest ręczny i ograniczony do portu 9100 w lokalnym `/24`.
- Używaj funkcji skanowania tylko jeśli jest to dozwolone w sieci firmowej.
- Test PGL zakłada domyślny znak sterujący PGL `~` (SFCC 126). To jest fabryczna wartość T8000; jeśli zakład ma zmienioną konfigurację PGL, połączenie TCP może działać, a etykieta PGL może się nie wydrukować.

## Następny etap po potwierdzeniu drukowania

Po udanym teście można rozbudować projekt o:

- formularz danych palety,
- generator SSCC + GS1 Mod-10,
- GS1-128 `(02)(17)(37)`, `(10)(240)` i `(00)`,
- szablon wyglądu stickera podobny do firmowego,
- podgląd,
- bezpośrednie drukowanie PGL do T8306,
- opcjonalnie aparat/OCR do odczytu danych z kartonu.
