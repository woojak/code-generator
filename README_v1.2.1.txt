Pallet Label Generator v1.2.1 FIX
======================================

Cel:
- naprawić mieszanie danych z poprzedniego skanu,
- poprawić Art.Nr.TU/CU,
- poprawić Batch na etykietach Semifinished,
- nie nadpisywać package GTIN przez EAN-13 produktu,
- wyczyścić/odseparować zanieczyszczony cache z v1.2.

Jak użyć w GitHub Codespaces:
1. Wgraj ten ZIP do katalogu głównego repo.
2. Rozpakuj:
   unzip -o Pallet_Label_Generator_v1.2.1_FIX.zip
3. Uruchom:
   python3 apply_v1_2_1.py
4. Sprawdź:
   git diff --check
   git diff --stat
5. Commit + push:
   git add -A
   git commit -m "Pallet Label Generator v1.2.1 OCR isolation fix"
   git push

Po push GitHub Actions powinien automatycznie uruchomić build.
Nie trzeba konfigurować lokalnego Android SDK.
