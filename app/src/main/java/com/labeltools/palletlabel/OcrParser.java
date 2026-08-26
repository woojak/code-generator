package com.labeltools.palletlabel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OcrParser {
    private OcrParser() {}

    public static OcrResult parse(String text, String forcedMode) {
        OcrResult r = new OcrResult();
        List<String> lines = cleanLines(text);
        String all = String.join("\n", lines);
        String flat = all.replace('\n', ' ');
        String lowerAll = all.toLowerCase(Locale.ROOT);

        r.detectedType = detectType(lowerAll, forcedMode);

        parseArticles(lines, flat, r);
        parseQuantityAndEan(lines, flat, r);
        parseBatch(lines, flat, r);
        parseMadeIn(lines, flat, r);
        parseExpiry(lines, flat, r);
        parsePo(lines, flat, r);
        parseLogisticsFields(lines, r);
        parseProduct(lines, r);
        findPackageGtin(lines, r);

        if (r.article.isEmpty()) {
            r.article = !r.articleCu.isEmpty() ? r.articleCu : r.articleTu;
        }
        if ("PALLET LOGISTICS".equals(r.detectedType) && !r.customerSku.isEmpty()) {
            r.article = r.customerSku;
            if (r.articleTu.isEmpty()) r.articleTu = r.customerSku;
            if (r.articleCu.isEmpty()) r.articleCu = r.customerSku;
        } else if ("PALLET LOGISTICS".equals(r.detectedType) && r.customerSku.isEmpty()) {
            r.customerSku = r.article;
        }
        if (r.material.isEmpty()) r.material = r.productName;

        if (r.packageGtin14.isEmpty() && !r.unitEan.isEmpty()) {
            String derived = Gs1Utils.normalizeGtin14(r.unitEan);
            if (!derived.isEmpty()) {
                r.packageGtin14 = derived;
                r.gtinSource = "UNIT_EAN_DERIVED";
                r.mark("gtin", OcrResult.Confidence.MEDIUM);
                r.warn("GTIN-14 utworzono z EAN produktu. Sprawdź kod kartonu przed wydrukiem.");
            }
        }

        return r;
    }

    private static String detectType(String lowerAll, String forcedMode) {
        if (forcedMode != null && !forcedMode.trim().isEmpty() && !"AUTO".equalsIgnoreCase(forcedMode)) {
            return forcedMode.trim().toUpperCase(Locale.ROOT);
        }
        if (lowerAll.contains("semifinished")) return "SEMIFINISHED";
        if (lowerAll.contains("sscc") && (lowerAll.contains("customer sku") || lowerAll.contains("material"))) {
            return "PALLET LOGISTICS";
        }
        if (lowerAll.contains("rituals") || lowerAll.contains("ritual of") || lowerAll.contains("art nr")) {
            return "RITUALS CARTON";
        }
        return "UNKNOWN";
    }

    public static void applyBarcodeValues(OcrResult r, List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) return;

        List<String> valid14 = new ArrayList<>();
        List<String> valid13 = new ArrayList<>();
        for (String raw : rawValues) {
            String d = Gs1Utils.digitsOnly(raw);
            if (d.isEmpty()) continue;
            if (!r.barcodeCandidates.contains(d)) r.barcodeCandidates.add(d);
            if (d.length() == 14 && Gs1Utils.isValidGtin14(d)) valid14.add(d);
            else if (d.length() == 13 && Gs1Utils.isValidGtin13(d)) valid13.add(d);
        }

        if (!valid14.isEmpty()) {
            r.packageGtin14 = valid14.get(0);
            r.gtinSource = "BARCODE_14";
            r.mark("gtin", OcrResult.Confidence.HIGH);
            return;
        }

        String unit = Gs1Utils.digitsOnly(r.unitEan);
        for (String d : valid13) {
            if (!d.equals(unit)) {
                r.packageGtin14 = "0" + d;
                r.gtinSource = "BARCODE_13_PACKAGE";
                r.mark("gtin", OcrResult.Confidence.HIGH);
                return;
            }
        }

        if (!valid13.isEmpty() && (r.packageGtin14.isEmpty() || "UNIT_EAN_DERIVED".equals(r.gtinSource))) {
            String d = valid13.get(0);
            r.packageGtin14 = "0" + d;
            if (d.equals(unit)) {
                r.gtinSource = "UNIT_EAN_DERIVED";
                r.mark("gtin", OcrResult.Confidence.MEDIUM);
                r.warn("Skaner znalazł tylko EAN produktu. Brak pewnego kodu kartonu.");
            } else {
                r.gtinSource = "BARCODE_13_PACKAGE";
                r.mark("gtin", OcrResult.Confidence.HIGH);
            }
        }
    }

    private static List<String> cleanLines(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\\R")) {
            String s = raw.replace('\u00a0', ' ')
                    .replace('—', '-')
                    .replace('–', '-')
                    .replaceAll("\\s{2,}", " ")
                    .replaceAll("^[|:;,. ]+", "")
                    .replaceAll("[|]+$", "")
                    .trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static void parseArticles(List<String> lines, String flat, OcrResult r) {
        Pattern inline = Pattern.compile(
                "(?i)art\\s*\\.?\\s*nr\\s*\\.?\\s*(tu\\s*/\\s*cu|cu\\s*/\\s*tu|tu|cu)\\s*[:.\\-]?\\s*([0-9OQDISBL|]{5,16})");
        for (String line : lines) {
            Matcher m = inline.matcher(line);
            while (m.find()) applyArticle(r, m.group(1), m.group(2), OcrResult.Confidence.HIGH);
        }

        Matcher flatMatcher = inline.matcher(flat);
        while (flatMatcher.find()) {
            applyArticle(r, flatMatcher.group(1), flatMatcher.group(2), OcrResult.Confidence.MEDIUM);
        }

        Pattern labelOnly = Pattern.compile(
                "(?i)^.*art\\s*\\.?\\s*nr\\s*\\.?\\s*(tu\\s*/\\s*cu|cu\\s*/\\s*tu|tu|cu)\\s*[:.\\-]?\\s*$");
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = labelOnly.matcher(lines.get(i));
            if (!m.matches()) continue;
            for (int j = i + 1; j < Math.min(lines.size(), i + 3); j++) {
                String value = normalizeNumericToken(lines.get(j));
                if (value.matches("\\d{5,12}")) {
                    applyArticle(r, m.group(1), value, OcrResult.Confidence.MEDIUM);
                    break;
                }
            }
        }

        if (!r.articleCu.isEmpty()) r.article = r.articleCu;
        else if (!r.articleTu.isEmpty()) r.article = r.articleTu;
    }

    private static void applyArticle(OcrResult r, String kindRaw, String rawValue, OcrResult.Confidence confidence) {
        String value = normalizeNumericToken(rawValue);
        if (!value.matches("\\d{5,12}")) return;
        String kind = kindRaw.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (kind.contains("/")) {
            r.articleTu = value;
            r.articleCu = value;
        } else if ("TU".equals(kind)) {
            r.articleTu = value;
        } else if ("CU".equals(kind)) {
            r.articleCu = value;
        }
        r.article = !r.articleCu.isEmpty() ? r.articleCu : r.articleTu;
        r.mark("article", confidence);
    }

    private static void parseQuantityAndEan(List<String> lines, String flat, OcrResult r) {
        Pattern p = Pattern.compile("(?i)(\\d{1,5})\\s*[x×X]\\s*([0-9OQDISB|][0-9OQDISBL| ]{11,20})");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (!m.find()) continue;
            String qty = Gs1Utils.digitsOnly(m.group(1));
            String ean = normalizeNumericToken(m.group(2));
            if (ean.length() > 14) ean = ean.substring(0, 14);
            if (!qty.isEmpty() && (Gs1Utils.isValidGtin13(ean) || Gs1Utils.isValidGtin14(ean))) {
                r.piecesPerCarton = qty;
                r.unitEan = ean;
                r.mark("pieces", OcrResult.Confidence.HIGH);
                return;
            }
        }

        Pattern compact = Pattern.compile("(?i)(\\d{1,5})\\s*[x×X]\\s*([0-9OQDISB|]{13,14})(?![0-9OQDISBL|])");
        Matcher fm = compact.matcher(flat);
        if (fm.find()) {
            String ean = normalizeNumericToken(fm.group(2));
            if (Gs1Utils.isValidGtin13(ean) || Gs1Utils.isValidGtin14(ean)) {
                r.piecesPerCarton = Gs1Utils.digitsOnly(fm.group(1));
                r.unitEan = ean;
                r.mark("pieces", OcrResult.Confidence.MEDIUM);
            }
        }
    }

    private static void parseBatch(List<String> lines, String flat, OcrResult r) {
        Pattern p = Pattern.compile("(?i)\\b(?:batch(?:\\s*code)?|lot)\\s*[:.\\-]?\\s*([A-Za-z0-9][A-Za-z0-9-]{2,19})");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                setBatch(r, m.group(1), OcrResult.Confidence.HIGH);
                if (!r.batch.isEmpty()) return;
            }
        }

        Matcher fm = p.matcher(flat);
        if (fm.find()) {
            setBatch(r, fm.group(1), OcrResult.Confidence.MEDIUM);
            if (!r.batch.isEmpty()) return;
        }

        Pattern labelOnly = Pattern.compile("(?i)^.*\\b(?:batch(?:\\s*code)?|lot)\\s*[:.\\-]?\\s*$");
        for (int i = 0; i < lines.size(); i++) {
            if (!labelOnly.matcher(lines.get(i)).matches()) continue;
            if (i + 1 < lines.size()) {
                setBatch(r, lines.get(i + 1), OcrResult.Confidence.MEDIUM);
                if (!r.batch.isEmpty()) return;
            }
        }

        if ("SEMIFINISHED".equals(r.detectedType)) {
            Set<String> excluded = new HashSet<>();
            addIfNotEmpty(excluded, r.article);
            addIfNotEmpty(excluded, r.articleTu);
            addIfNotEmpty(excluded, r.articleCu);
            addIfNotEmpty(excluded, r.unitEan);
            addIfNotEmpty(excluded, r.poCode);
            Pattern semi = Pattern.compile("(?i)^(?:[A-Z]{1,6}\\d{2,8}|\\d{2,8}[A-Z]{1,6})$");
            for (String line : lines) {
                String v = line.trim().replaceAll("[^A-Za-z0-9-]", "");
                if (semi.matcher(v).matches() && !excluded.contains(v) && isUsableBatch(v)) {
                    r.batch = v.toUpperCase(Locale.ROOT);
                    r.mark("batch", OcrResult.Confidence.MEDIUM);
                    return;
                }
            }
        }
    }

    private static void setBatch(OcrResult r, String raw, OcrResult.Confidence confidence) {
        if (raw == null) return;
        String v = raw.trim().replaceAll("^[^A-Za-z0-9]+|[^A-Za-z0-9-]+$", "");
        if (!isUsableBatch(v)) return;

        if (v.matches("(?i)^[GOQ][0-9]{5,12}$")) {
            v = "0" + v.substring(1);
            r.batchNormalized = true;
            confidence = OcrResult.Confidence.MEDIUM;
            r.warn("Batch miał typowy błąd OCR na pierwszym znaku i został poprawiony do " + v + ".");
        }
        r.batch = v.toUpperCase(Locale.ROOT);
        r.mark("batch", confidence);
    }

    private static boolean isUsableBatch(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.length() < 3 || v.length() > 20) return false;
        String u = v.toUpperCase(Locale.ROOT);
        return !u.equals("RITUALS") && !u.equals("CODE") && !u.equals("BATCH")
                && !u.equals("LOT") && !u.equals("SEMIFINISHED") && !u.equals("ARTICLE");
    }

    private static void parseMadeIn(List<String> lines, String flat, OcrResult r) {
        Pattern p = Pattern.compile("(?i)\\bmade\\s*in\\s*[:.\\-]?\\s*([A-Za-zÀ-ž][A-Za-zÀ-ž .'-]{1,39})");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                r.madeIn = cleanCountry(m.group(1));
                r.mark("madeIn", OcrResult.Confidence.HIGH);
                return;
            }
        }
        Matcher fm = p.matcher(flat);
        if (fm.find()) {
            String country = cleanCountry(fm.group(1));
            if (!country.isEmpty()) {
                r.madeIn = country;
                r.mark("madeIn", OcrResult.Confidence.MEDIUM);
            }
        }
    }

    private static String cleanCountry(String s) {
        String v = s.replaceAll("(?i)\\b(?:art|batch|lan|ref|rituals).*", "")
                .replaceAll("\\s{2,}", " ").trim();
        return v.length() > 40 ? v.substring(0, 40).trim() : v;
    }

    private static void parseExpiry(List<String> lines, String flat, OcrResult r) {
        Pattern p = Pattern.compile(
                "(?i)\\b(?:exp|expiry)(?:\\s*date)?(?:\\s*\\([^)]*\\))?\\s*[:.\\-]?\\s*(\\d{4}[/-]\\d{2}(?:[/-]\\d{2})?|\\d{2}[/-]\\d{2}[/-]\\d{2,4})");
        Matcher m = p.matcher(flat);
        if (m.find()) {
            r.expiryRaw = m.group(1);
            r.mark("expiry", OcrResult.Confidence.HIGH);
        }
    }

    private static void parsePo(List<String> lines, String flat, OcrResult r) {
        Pattern p = Pattern.compile("(?i)\\bpo\\s*(?:code)?\\s*[:.\\-]?\\s*([A-Z0-9-]{4,30})");
        Matcher m = p.matcher(flat);
        if (m.find()) {
            r.poCode = m.group(1).toUpperCase(Locale.ROOT);
            r.mark("po", OcrResult.Confidence.HIGH);
        }
    }

    private static void parseLogisticsFields(List<String> lines, OcrResult r) {
        if (!"PALLET LOGISTICS".equals(r.detectedType)) return;

        Pattern ssccP = Pattern.compile("(?<!\\d)(\\d(?:[ ]?\\d){17})(?!\\d)");
        Pattern countP = Pattern.compile("(?i)^count\\s*[:.\\-]?\\s*(\\d{1,6})$");
        Pattern grossP = Pattern.compile("(?i).*brutto\\s+pallet\\s+weight.*?([0-9]+[,.][0-9]+)");
        Pattern skuP = Pattern.compile("(?i).*customer\\s+sku\\s*[:.\\-]?\\s*(\\d{5,15})");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String lower = line.toLowerCase(Locale.ROOT);
            Matcher sm = ssccP.matcher(line);
            if (sm.find()) {
                String d = Gs1Utils.digitsOnly(sm.group(1));
                if (Gs1Utils.isValidSscc(d)) {
                    r.sscc = d;
                    r.mark("sscc", OcrResult.Confidence.HIGH);
                }
            }
            Matcher cm = countP.matcher(line);
            if (cm.find()) r.palletCount = cm.group(1);
            Matcher gm = grossP.matcher(line);
            if (gm.find()) r.grossWeight = gm.group(1);
            Matcher km = skuP.matcher(line);
            if (km.find()) r.customerSku = km.group(1);

            if (lower.contains("article") && lower.contains("material") && i + 1 < lines.size()) {
                Matcher row = Pattern.compile("^(\\d{5,12})\\s+(.+)$").matcher(lines.get(i + 1));
                if (row.find()) {
                    r.logisticsArticle = row.group(1);
                    r.material = row.group(2).trim();
                    r.mark("article", OcrResult.Confidence.HIGH);
                    r.mark("product", OcrResult.Confidence.HIGH);
                }
            }
            if (lower.contains("content") && lower.contains("customer sku") && lower.contains("count") && i + 1 < lines.size()) {
                Matcher row = Pattern.compile("^(\\d{13,14})\\s+(\\d{5,15})\\s+(\\d{1,6})$").matcher(lines.get(i + 1));
                if (row.find()) {
                    String g = Gs1Utils.normalizeGtin14(row.group(1));
                    if (!g.isEmpty()) {
                        r.packageGtin14 = g;
                        r.gtinSource = "LOGISTICS_CONTENT";
                        r.mark("gtin", OcrResult.Confidence.HIGH);
                    }
                    r.customerSku = row.group(2);
                    r.palletCount = row.group(3);
                }
            }
            if (lower.contains("expiry") && lower.contains("batch") && lower.contains("brutto") && i + 1 < lines.size()) {
                Matcher row = Pattern.compile("^(\\d{4}[/-]\\d{2}(?:[/-]\\d{2})?)\\s+([A-Z0-9-]{3,20})\\s+([0-9]+[,.][0-9]+)$",
                        Pattern.CASE_INSENSITIVE).matcher(lines.get(i + 1));
                if (row.find()) {
                    r.expiryRaw = row.group(1);
                    r.batch = row.group(2).toUpperCase(Locale.ROOT);
                    r.grossWeight = row.group(3);
                    r.mark("expiry", OcrResult.Confidence.HIGH);
                    r.mark("batch", OcrResult.Confidence.HIGH);
                }
            }
        }
    }

    private static void parseProduct(List<String> lines, OcrResult r) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String normalized = line.toLowerCase(Locale.ROOT).replaceAll("[._-]+", " ");
            if (normalized.contains("the ritual of ") || normalized.contains("ritual of ")) {
                r.productName = cleanProduct(line);
                r.mark("product", OcrResult.Confidence.HIGH);
                if (i + 1 < lines.size() && isDescription(lines.get(i + 1))) {
                    r.description = cleanProduct(lines.get(i + 1));
                    r.mark("description", OcrResult.Confidence.MEDIUM);
                }
                return;
            }
        }

        if ("RITUALS BROWN".equals(r.detectedType) || "RITUALS CARTON".equals(r.detectedType)
                || "RITUALS WHITE".equals(r.detectedType)) {
            int best = -1;
            int bestScore = 0;
            for (int i = 0; i < lines.size(); i++) {
                int score = productCandidateScore(lines.get(i));
                if (score > bestScore) {
                    best = i;
                    bestScore = score;
                }
            }
            if (best >= 0 && bestScore >= 3) {
                r.productName = cleanProduct(lines.get(best));
                r.mark("product", OcrResult.Confidence.MEDIUM);
                if (best + 1 < lines.size() && isDescription(lines.get(best + 1))) {
                    r.description = cleanProduct(lines.get(best + 1));
                    r.mark("description", OcrResult.Confidence.MEDIUM);
                }
                return;
            }
        }

        if ("SEMIFINISHED".equals(r.detectedType)) {
            for (String line : lines) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("semifinished")) {
                    String cleaned = cleanProduct(line.replaceAll("(?i)semifinished\\s*[:.-]?", ""));
                    if (isDescription(cleaned)) {
                        r.productName = cleaned;
                        r.mark("product", OcrResult.Confidence.MEDIUM);
                        return;
                    }
                }
            }
            for (String line : lines) {
                if (isDescription(line) && !line.toLowerCase(Locale.ROOT).contains("semifinished")) {
                    r.productName = cleanProduct(line);
                    r.mark("product", OcrResult.Confidence.MEDIUM);
                    return;
                }
            }
        }

        if ("PALLET LOGISTICS".equals(r.detectedType) && !r.material.isEmpty()) {
            r.productName = r.material;
            r.mark("product", OcrResult.Confidence.HIGH);
        }
    }

    private static int productCandidateScore(String line) {
        if (!isDescription(line)) return 0;
        String lower = line.toLowerCase(Locale.ROOT);
        int score = 0;
        String[] keywords = {
                "body", "cream", "scrub", "shower", "gel", "candle", "mask", "mist", "paste",
                "foaming", "sugar", "overnight", "hand", "foot", "hair", "ribbon", "scented",
                "yozakura", "ayurveda", "jing", "mehr", "karma", "ritual"
        };
        for (String k : keywords) if (lower.contains(k)) score++;
        if (lower.matches(".*\\b\\d{1,4}\\s*(ml|g)\\b.*")) score += 2;
        if (lower.split("\\s+").length >= 3) score++;
        return score;
    }

    private static boolean isDescription(String line) {
        if (line == null) return false;
        String s = line.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (s.length() < 4 || s.length() > 110) return false;
        if (lower.startsWith("rituals") || lower.startsWith("art") || lower.startsWith("batch")
                || lower.startsWith("made in") || lower.startsWith("lan") || lower.startsWith("ref")
                || lower.startsWith("exp") || lower.startsWith("po ") || lower.startsWith("content")
                || lower.startsWith("count") || lower.startsWith("sscc") || lower.contains("herengracht")
                || lower.contains("amsterdam") || lower.matches("^\\d+\\s*[x×X].*")) return false;
        int letters = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) letters++;
        return letters >= 4;
    }

    private static String cleanProduct(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)^rituals\\s*[.:_-]*\\s*", "")
                .replaceAll("(?i)\\s+GS$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static void findPackageGtin(List<String> lines, OcrResult r) {
        String unit = Gs1Utils.digitsOnly(r.unitEan);
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("art") || lower.contains("batch") || lower.contains("ref")
                    || lower.contains("lan") || lower.matches(".*\\d+\\s*[x×X].*")) continue;

            if (isMostlyNumericHri(line)) {
                String d = normalizeNumericToken(line);
                if (d.length() == 13 && Gs1Utils.isValidGtin13(d) && !d.equals(unit)) {
                    r.packageGtin14 = "0" + d;
                    r.gtinSource = "OCR_PACKAGE_HRI";
                    r.mark("gtin", OcrResult.Confidence.MEDIUM);
                    return;
                }
                if (d.length() == 14 && Gs1Utils.isValidGtin14(d) && !d.equals(unit)) {
                    r.packageGtin14 = d;
                    r.gtinSource = "OCR_PACKAGE_HRI";
                    r.mark("gtin", OcrResult.Confidence.MEDIUM);
                    return;
                }
            }

            Matcher m = Pattern.compile("(?<!\\d)(\\d{13,14})(?!\\d)").matcher(line);
            while (m.find()) {
                String candidate = m.group(1);
                String normalized = Gs1Utils.normalizeGtin14(candidate);
                if (!normalized.isEmpty() && !candidate.equals(unit)) {
                    r.packageGtin14 = normalized;
                    r.gtinSource = "OCR_PACKAGE_HRI";
                    r.mark("gtin", OcrResult.Confidence.MEDIUM);
                    return;
                }
            }
        }
    }

    private static boolean isMostlyNumericHri(String line) {
        if (line == null || line.isEmpty()) return false;
        int digitish = 0;
        int otherLetters = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isDigit(c) || "OoQqDIiLl|SsBb".indexOf(c) >= 0) {
                digitish++;
            } else if (Character.isLetter(c)) {
                otherLetters++;
            }
        }
        return digitish >= 10 && otherLetters == 0;
    }

    private static String normalizeNumericToken(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) b.append(c);
            else {
                switch (c) {
                    case 'O': case 'o': case 'Q': case 'q': case 'D': b.append('0'); break;
                    case 'I': case 'i': case 'L': case 'l': case '|': b.append('1'); break;
                    case 'S': case 's': b.append('5'); break;
                    case 'B': case 'b': b.append('8'); break;
                    default: break;
                }
            }
        }
        return b.toString();
    }

    private static void addIfNotEmpty(Set<String> set, String value) {
        if (value != null && !value.isEmpty()) set.add(value);
    }
}
