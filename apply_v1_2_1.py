#!/usr/bin/env python3
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path.cwd()

MAIN = ROOT / "app/src/main/java/com/labeltools/palletlabel/MainActivity.java"
OCR = ROOT / "app/src/main/java/com/labeltools/palletlabel/OcrParser.java"
GRADLE = ROOT / "app/build.gradle"
WORKFLOW = ROOT / ".github/workflows/build-apk.yml"

for p in (MAIN, OCR, GRADLE, WORKFLOW):
    if not p.exists():
        print(f"ERROR: nie znaleziono {p}")
        print("Uruchom skrypt w katalogu głównym repozytorium code-generator.")
        sys.exit(1)

def replace_once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: oczekiwano 1 dopasowania, znaleziono {n}")
    return text.replace(old, new, 1)

def regex_replace_once(text, pattern, replacement, label, flags=0):
    new_text, n = re.subn(pattern, replacement, text, count=1, flags=flags)
    if n != 1:
        raise RuntimeError(f"{label}: oczekiwano 1 dopasowania, znaleziono {n}")
    return new_text

# ----------------------------------------------------------------------
# MainActivity.java
# ----------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

main = replace_once(
    main,
    'version.setText("v1.2 • multi-OCR • 2 szablony PDF • offline");',
    'version.setText("v1.2.1 • safe scan reset • multi-OCR • offline");',
    "MainActivity version"
)

if "migrateTo121();" not in main:
    main = replace_once(
        main,
        'prefs = getSharedPreferences(PREFS, MODE_PRIVATE);\n',
        'prefs = getSharedPreferences(PREFS, MODE_PRIVATE);\n        migrateTo121();\n',
        "MainActivity migrate call"
    )

# Use a new cache namespace so polluted cache data from v1.2 is ignored.
main = main.replace('String prefix = "cache_" + r.article + "_";',
                    'String prefix = "cache_v2_" + r.article + "_";')
main = main.replace('String prefix = "cache_" + article + "_";',
                    'String prefix = "cache_v2_" + article + "_";')

new_apply = r'''    private void applyOcrResult(OcrResult r) {
        // v1.2.1: a confirmed scan starts a NEW product dataset.
        // Never mix missing OCR fields with values from the previous carton.
        clearProductSpecificFieldsForNewScan();

        productEdit.setText(r.productName);
        descriptionEdit.setText(r.description);

        String resolvedTu = r.articleTu;
        String resolvedCu = r.articleCu;
        if (resolvedTu.isEmpty() && resolvedCu.isEmpty() && !r.article.isEmpty()) {
            resolvedTu = r.article;
            resolvedCu = r.article;
        } else {
            if (resolvedTu.isEmpty() && !r.article.isEmpty()) resolvedTu = r.article;
            if (resolvedCu.isEmpty() && !r.article.isEmpty()) resolvedCu = r.article;
        }
        articleTuEdit.setText(resolvedTu);
        articleCuEdit.setText(resolvedCu);

        batchEdit.setText(r.batch);
        piecesEdit.setText(r.piecesPerCarton);
        gtinEdit.setText(r.packageGtin14);
        madeInEdit.setText(r.madeIn);
        poCodeEdit.setText(r.poCode);

        if (!r.sscc.isEmpty()) ssccEdit.setText(r.sscc);
        if (!r.palletCount.isEmpty()) cartonsEdit.setText(r.palletCount);

        grossWeightEdit.setText(r.grossWeight);
        materialEdit.setText(r.material);
        customerSkuEdit.setText(r.customerSku);
        logisticsArticleEdit.setText(r.logisticsArticle);

        // Apply OCR expiry only when it contains an unambiguous full date.
        // YYYY/MM is intentionally NOT guessed.
        String parsedExpiry = normalizeOcrExpiry(r.expiryRaw);
        if (!parsedExpiry.isEmpty()) expiryEdit.setText(parsedExpiry);

        String mainArticle = mainArticle();
        String autoPack = buildPackArticleLine(productEdit.getText().toString(), mainArticle);
        packArticleEdit.setText(autoPack);

        if (r.detectedType.equals("PALLET LOGISTICS")) {
            if (customerSkuEdit.getText().toString().trim().isEmpty()) customerSkuEdit.setText(mainArticle);
            if (materialEdit.getText().toString().trim().isEmpty()) materialEdit.setText(productEdit.getText().toString());
        }

        // Do not leave a preview/PDF from the previous product visible when
        // the new scan is incomplete and cannot yet generate a valid label.
        preview.setImageDrawable(null);
        pendingPdf = null;
        if (gs1Status != null) gs1Status.setText("");

        photoStatus.setText("Dane z nowego skanu zastosowane. Brakujące pola pozostawiono puste — nie użyto danych z poprzedniego kartonu.");
        saveForm();
        if (!mainArticle.isEmpty()) saveProductCache();
        updateTotal();
        generatePreview(false);
    }

    private void clearProductSpecificFieldsForNewScan() {
        productEdit.setText("");
        descriptionEdit.setText("");
        articleTuEdit.setText("");
        articleCuEdit.setText("");
        piecesEdit.setText("");
        batchEdit.setText("");
        gtinEdit.setText("");
        madeInEdit.setText("");
        poCodeEdit.setText("");
        packArticleEdit.setText("");

        // Hidden logistics fields are also product-specific.
        logisticsArticleEdit.setText("");
        materialEdit.setText("");
        customerSkuEdit.setText("");
        grossWeightEdit.setText("");
        dataMatrixEdit.setText("");
    }

    private String normalizeOcrExpiry(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace('-', '/');
        if (s.isEmpty()) return "";

        DateTimeFormatter out = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US);
        String[] patterns = new String[]{"dd/MM/yy", "dd/MM/yyyy", "yyyy/MM/dd"};
        for (String pattern : patterns) {
            try {
                LocalDate d = LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern, Locale.US));
                return d.format(out);
            } catch (DateTimeParseException ignored) {}
        }
        return "";
    }

'''
main = regex_replace_once(
    main,
    r'    private void applyOcrResult\(OcrResult r\) \{.*?\n    \}\n\n(?=    private String buildPackArticleLine)',
    new_apply,
    "MainActivity applyOcrResult",
    flags=re.S
)

migration_method = r'''    private void migrateTo121() {
        if (prefs.getInt("dataModelVersion", 0) >= 121) return;

        // v1.2 could save a mixed product when OCR missed Article/name.
        // Clear only product-specific current-form data once.
        // Pallet settings (COUNT, expiry, SSCC) and template constants stay.
        prefs.edit()
                .putString("product", "")
                .putString("description", "")
                .putString("articleTu", "")
                .putString("articleCu", "")
                .putString("pieces", "")
                .putString("batch", "")
                .putString("gtin", "")
                .putString("madeIn", "")
                .putString("poCode", "")
                .putString("pack", "")
                .putString("logArticle", "")
                .putString("material", "")
                .putString("customerSku", "")
                .putString("grossWeight", "")
                .putString("dataMatrix", "")
                .putInt("dataModelVersion", 121)
                .apply();
    }

'''
if "private void migrateTo121()" not in main:
    main = main.replace("    private void loadForm() {\n", migration_method + "    private void loadForm() {\n", 1)

load_defaults = {
    'prefs.getString("product", "RC SCRUB YOZAKURA")': 'prefs.getString("product", "")',
    'prefs.getString("articleTu", "1120211")': 'prefs.getString("articleTu", "")',
    'prefs.getString("articleCu", "1120211")': 'prefs.getString("articleCu", "")',
    'prefs.getString("pieces", "60")': 'prefs.getString("pieces", "")',
    'prefs.getString("batch", "12342064")': 'prefs.getString("batch", "")',
    'prefs.getString("gtin", "08720296062361")': 'prefs.getString("gtin", "")',
    'prefs.getString("pack", "125G1120211")': 'prefs.getString("pack", "")',
}
for old, new in load_defaults.items():
    if old in main:
        main = main.replace(old, new, 1)

MAIN.write_text(main, encoding="utf-8")

# ----------------------------------------------------------------------
# OcrParser.java
# ----------------------------------------------------------------------
ocr = OCR.read_text(encoding="utf-8")

new_barcode = r'''    public static void applyBarcodeValues(OcrResult r, List<String> rawValues) {
        if (rawValues == null) return;

        // Prefer a real 14-digit package GTIN over an EAN-13 from the unit.
        // This prevents a scanned unit EAN from overwriting a package GTIN
        // already read from the carton text/barcode.
        for (String raw : rawValues) {
            String digits = Gs1Utils.digitsOnly(raw);
            if (digits.length() == 14 && Gs1Utils.isValidGtin14(digits)) {
                r.packageGtin14 = digits;
                r.mark("gtin", OcrResult.Confidence.HIGH);
                return;
            }
        }

        // A 13-digit code is only a fallback when no package GTIN was found.
        if (!r.packageGtin14.isEmpty()) return;
        for (String raw : rawValues) {
            String normalized = Gs1Utils.normalizeGtin14(raw);
            if (!normalized.isEmpty()) {
                r.packageGtin14 = normalized;
                r.mark("gtin", OcrResult.Confidence.MEDIUM);
                return;
            }
        }
    }

'''
ocr = regex_replace_once(
    ocr,
    r'    public static void applyBarcodeValues\(OcrResult r, List<String> rawValues\) \{.*?\n    \}\n\n(?=    private static List<String> cleanLines)',
    new_barcode,
    "OcrParser applyBarcodeValues",
    flags=re.S
)

new_articles = r'''    private static void parseArticles(List<String> lines, OcrResult r) {
        Pattern inline = Pattern.compile(
                "(?i)art\\s*\\.?\\s*nr\\s*\\.?\\s*(tu\\s*/\\s*cu|cu\\s*/\\s*tu|tu|cu)\\s*[:.\\-]?\\s*(\\d{5,12})");
        Pattern reversed = Pattern.compile(
                "(?i)\\b(tu\\s*/\\s*cu|cu\\s*/\\s*tu|tu|cu)\\s+art\\s*\\.?\\s*nr\\s*\\.?\\s*[:.\\-]?\\s*(\\d{5,12})");
        Pattern labelOnly = Pattern.compile(
                "(?i)^.*art\\s*\\.?\\s*nr\\s*\\.?\\s*(tu\\s*/\\s*cu|cu\\s*/\\s*tu|tu|cu)\\s*[:.\\-]?\\s*$");
        Pattern valueOnly = Pattern.compile("^\\d{5,12}$");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String kind = "";
            String value = "";

            Matcher m = inline.matcher(line);
            if (m.find()) {
                kind = m.group(1);
                value = m.group(2);
            } else {
                m = reversed.matcher(line);
                if (m.find()) {
                    kind = m.group(1);
                    value = m.group(2);
                } else {
                    m = labelOnly.matcher(line);
                    if (m.matches() && i + 1 < lines.size()) {
                        Matcher next = valueOnly.matcher(lines.get(i + 1).trim());
                        if (next.matches()) {
                            kind = m.group(1);
                            value = next.group();
                        }
                    }
                }
            }

            if (!value.isEmpty()) {
                applyArticleValue(r, kind, value);
                r.mark("article", OcrResult.Confidence.HIGH);
            }
        }

        if (!r.articleCu.isEmpty()) r.article = r.articleCu;
        else if (!r.articleTu.isEmpty()) r.article = r.articleTu;
    }

    private static void applyArticleValue(OcrResult r, String kindRaw, String value) {
        String kind = kindRaw.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (kind.contains("/")) {
            r.articleTu = value;
            r.articleCu = value;
        } else if (kind.equals("TU")) {
            r.articleTu = value;
        } else if (kind.equals("CU")) {
            r.articleCu = value;
        }
    }

'''
ocr = regex_replace_once(
    ocr,
    r'    private static void parseArticles\(List<String> lines, OcrResult r\) \{.*?\n    \}\n\n(?=    private static void parseBatch)',
    new_articles,
    "OcrParser parseArticles",
    flags=re.S
)

new_batch = r'''    private static void parseBatch(List<String> lines, OcrResult r) {
        Pattern inline = Pattern.compile(
                "(?i)^.*?\\bbatch\\s*(?:code|/\\s*lot)?\\s*[:.\\-]?\\s*([A-Z0-9][A-Z0-9-]{2,19})\\s*$");
        Pattern lot = Pattern.compile(
                "(?i)^.*?\\blot\\s*[:.\\-]?\\s*([A-Z0-9][A-Z0-9-]{2,19})\\s*$");
        Pattern labelOnly = Pattern.compile(
                "(?i)^.*?\\b(?:batch(?:\\s*code|\\s*/\\s*lot)?|lot)\\s*[:.\\-]?\\s*$");
        Pattern token = Pattern.compile("(?i)^[A-Z0-9][A-Z0-9-]{2,19}$");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = inline.matcher(line);
            if (!m.matches()) m = lot.matcher(line);

            if (m.matches()) {
                String value = m.group(1).trim();
                if (isUsableBatch(value)) {
                    r.batch = value;
                    r.mark("batch", OcrResult.Confidence.HIGH);
                    return;
                }
            }

            // OCR often puts "Batch" and the value on separate lines.
            if (labelOnly.matcher(line).matches() && i + 1 < lines.size()) {
                String value = lines.get(i + 1).trim();
                if (token.matcher(value).matches() && isUsableBatch(value)) {
                    r.batch = value;
                    r.mark("batch", OcrResult.Confidence.HIGH);
                    return;
                }
            }
        }

        // Semifinished labels sometimes expose a short alphanumeric batch
        // such as AM26/FYR628 without a clean "Batch:" line.
        if (r.detectedType.equals("SEMIFINISHED")) {
            Pattern semiBatch = Pattern.compile("(?i)^(?:[A-Z]{1,6}\\d{2,8}|\\d{2,8}[A-Z]{1,6})$");
            for (String line : lines) {
                String value = line.trim();
                if (semiBatch.matcher(value).matches() && isUsableBatch(value)) {
                    r.batch = value;
                    r.mark("batch", OcrResult.Confidence.MEDIUM);
                    return;
                }
            }
        }
    }

    private static boolean isUsableBatch(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.length() < 3 || v.length() > 20) return false;
        String u = v.toUpperCase(Locale.ROOT);
        return !u.equals("RITUALS")
                && !u.equals("CODE")
                && !u.equals("BATCH")
                && !u.equals("LOT")
                && !u.equals("SEMIFINISHED");
    }

'''
ocr = regex_replace_once(
    ocr,
    r'    private static void parseBatch\(List<String> lines, OcrResult r\) \{.*?\n    \}\n\n(?=    private static void parseQuantityAndEan)',
    new_batch,
    "OcrParser parseBatch",
    flags=re.S
)

OCR.write_text(ocr, encoding="utf-8")

# ----------------------------------------------------------------------
# Version + artifact name
# ----------------------------------------------------------------------
gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+12\b", "versionCode 13", gradle, count=1)
gradle = re.sub(r"versionName\s+'1\.2\.0'", "versionName '1.2.1'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

workflow = WORKFLOW.read_text(encoding="utf-8")
workflow = workflow.replace("Pallet-Label-Generator-v1.2-debug",
                            "Pallet-Label-Generator-v1.2.1-debug")
WORKFLOW.write_text(workflow, encoding="utf-8")

print("v1.2.1 FIX zastosowany.")
print()
print("Najważniejsze zmiany:")
print("- nowy skan czyści dane poprzedniego produktu przed zastosowaniem OCR")
print("- brakujące pola pozostają puste zamiast dziedziczyć stare wartości")
print("- stary podgląd/PDF jest kasowany po zatwierdzeniu nowego skanu")
print("- cache v1.2 jest ignorowany (nowy namespace cache_v2_)")
print("- Art.Nr.TU/CU jest poprawnie rozpoznawany i ustawia TU + CU")
print("- Batch obsługuje osobną linię oraz AM26/FYR628 dla Semifinished")
print("- 14-cyfrowy package GTIN ma pierwszeństwo przed EAN-13 produktu")
print("- wersja aplikacji: 1.2.1 / versionCode 13")
print()
print("Sprawdzam format diff...")
try:
    subprocess.run(["git", "diff", "--check"], check=True)
    print("git diff --check: OK")
except Exception as e:
    print("UWAGA: git diff --check nie przeszedł:", e)

print()
print("Następnie wykonaj:")
print('  git add -A')
print('  git commit -m "Pallet Label Generator v1.2.1 OCR isolation fix"')
print('  git push')
