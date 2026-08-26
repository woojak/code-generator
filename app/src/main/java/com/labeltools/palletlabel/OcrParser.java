package com.labeltools.palletlabel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OcrParser {
    private OcrParser() {}

    public static OcrResult parse(String text, String forcedMode) {
        OcrResult r = new OcrResult();
        List<String> lines = cleanLines(text);
        String all = String.join("\n", lines);
        String lowerAll = all.toLowerCase(Locale.ROOT);

        if (forcedMode != null && !forcedMode.equalsIgnoreCase("AUTO")) {
            r.detectedType = forcedMode;
        } else if (lowerAll.contains("semifinished")) {
            r.detectedType = "SEMIFINISHED";
        } else if (lowerAll.contains("sscc") && lowerAll.contains("customer sku")) {
            r.detectedType = "PALLET LOGISTICS";
        } else if (lowerAll.contains("rituals")) {
            r.detectedType = "RITUALS CARTON";
        }

        parseArticles(lines, r);
        parseBatch(lines, r);
        parseQuantityAndEan(lines, r);
        parseMadeIn(lines, r);
        parseExpiry(lines, r);
        parsePo(lines, r);
        parseLogisticsFields(lines, r);
        parseProduct(lines, r);
        findPackageGtin(lines, r);

        if (r.article.isEmpty()) {
            r.article = !r.articleCu.isEmpty() ? r.articleCu : r.articleTu;
        }
        if (r.detectedType.equals("PALLET LOGISTICS") && !r.customerSku.isEmpty()) {
            r.article = r.customerSku;
            if (r.articleTu.isEmpty()) r.articleTu = r.customerSku;
            if (r.articleCu.isEmpty()) r.articleCu = r.customerSku;
        } else if (r.customerSku.isEmpty() && r.detectedType.equals("PALLET LOGISTICS")) {
            r.customerSku = r.article;
        }
        if (r.material.isEmpty()) r.material = r.productName;
        return r;
    }

    public static void applyBarcodeValues(OcrResult r, List<String> rawValues) {
        if (rawValues == null) return;
        for (String raw : rawValues) {
            String normalized = Gs1Utils.normalizeGtin14(raw);
            if (!normalized.isEmpty()) {
                r.packageGtin14 = normalized;
                r.mark("gtin", OcrResult.Confidence.HIGH);
                return;
            }
        }
    }

    private static List<String> cleanLines(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\\R")) {
            String s = raw.replace('\u00a0', ' ')
                    .replaceAll("\\s{2,}", " ")
                    .replaceAll("^[|:;,. ]+", "")
                    .replaceAll("[|]+$", "")
                    .trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static void parseArticles(List<String> lines, OcrResult r) {
        Pattern p = Pattern.compile("(?i)art\\s*\\.?\\s*nr\\s*\\.?\\s*(tu|cu)\\s*[:.\\-]?\\s*(\\d{5,12})");
        Pattern reversed = Pattern.compile("(?i)\\b(tu|cu)\\s+art\\s*\\.?\\s*nr\\s*\\.?\\s*[:.\\-]?\\s*(\\d{5,12})");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (!m.find()) m = reversed.matcher(line);
            if (m.find(0)) {
                String kind = m.group(1).toUpperCase(Locale.ROOT);
                String value = m.group(2);
                if (kind.equals("TU")) r.articleTu = value; else r.articleCu = value;
                r.mark("article", OcrResult.Confidence.HIGH);
            }
        }
        if (!r.articleCu.isEmpty()) r.article = r.articleCu;
        else if (!r.articleTu.isEmpty()) r.article = r.articleTu;
    }

    private static void parseBatch(List<String> lines, OcrResult r) {
        Pattern p = Pattern.compile("(?i)^.*?\\bbatch\\s*(?:code|/\\s*lot)?\\s*[:.\\-]?\\s*([A-Z0-9][A-Z0-9-]{2,19})\\s*$");
        Pattern lot = Pattern.compile("(?i)^.*?\\blot\\s*[:.\\-]?\\s*([A-Z0-9][A-Z0-9-]{2,19})\\s*$");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (!m.matches()) m = lot.matcher(line);
            if (m.matches()) {
                String value = m.group(1).trim();
                if (!value.equalsIgnoreCase("RITUALS") && !value.equalsIgnoreCase("CODE")) {
                    r.batch = value;
                    r.mark("batch", OcrResult.Confidence.HIGH);
                    return;
                }
            }
        }
    }

    private static void parseQuantityAndEan(List<String> lines, OcrResult r) {
        Pattern p = Pattern.compile("(?i)(\\d{1,5})\\s*[x×X]\\s*(\\d{12,14})");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                r.piecesPerCarton = m.group(1);
                r.unitEan = m.group(2);
                r.mark("pieces", OcrResult.Confidence.HIGH);
                String normalized = Gs1Utils.normalizeGtin14(r.unitEan);
                if (!normalized.isEmpty() && r.packageGtin14.isEmpty()) {
                    r.packageGtin14 = normalized;
                    r.mark("gtin", OcrResult.Confidence.MEDIUM);
                }
                return;
            }
        }
    }

    private static void parseMadeIn(List<String> lines, OcrResult r) {
        Pattern p = Pattern.compile("(?i)\\bmade\\s*in\\s*:?\\s*(.+)$");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                String v = m.group(1).replaceAll("[^A-Za-zÀ-ž .'-]", "").trim();
                if (v.length() >= 2 && v.length() <= 40) {
                    r.madeIn = v;
                    r.mark("madeIn", OcrResult.Confidence.HIGH);
                    return;
                }
            }
        }
    }

    private static void parseExpiry(List<String> lines, OcrResult r) {
        Pattern p = Pattern.compile("(?i)\\b(?:exp|expiry)(?:\\s*date)?(?:\\s*\\([^)]*\\))?\\s*[:.\\-]?\\s*(\\d{4}[/-]\\d{2}(?:[/-]\\d{2})?|\\d{2}[/-]\\d{2}[/-]\\d{2,4})");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                r.expiryRaw = m.group(1);
                r.mark("expiry", OcrResult.Confidence.HIGH);
                return;
            }
        }
    }

    private static void parsePo(List<String> lines, OcrResult r) {
        Pattern p = Pattern.compile("(?i)\\bpo\\s*code\\s*[:.\\-]?\\s*([A-Z0-9-]{4,30})");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                r.poCode = m.group(1);
                r.mark("po", OcrResult.Confidence.HIGH);
                return;
            }
        }
    }

    private static void parseLogisticsFields(List<String> lines, OcrResult r) {
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

            // Common OCR output keeps table headers in one line and values in the next line.
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
                    if (!g.isEmpty()) { r.packageGtin14 = g; r.mark("gtin", OcrResult.Confidence.HIGH); }
                    r.customerSku = row.group(2);
                    r.palletCount = row.group(3);
                }
            }
            if (lower.contains("expiry") && lower.contains("batch") && lower.contains("brutto") && i + 1 < lines.size()) {
                Matcher row = Pattern.compile("^(\\d{4}[/-]\\d{2})\\s+([A-Z0-9-]{3,20})\\s+([0-9]+[,.][0-9]+)$", Pattern.CASE_INSENSITIVE)
                        .matcher(lines.get(i + 1));
                if (row.find()) {
                    r.expiryRaw = row.group(1);
                    r.batch = row.group(2);
                    r.grossWeight = row.group(3);
                    r.mark("expiry", OcrResult.Confidence.HIGH);
                    r.mark("batch", OcrResult.Confidence.HIGH);
                }
            }

            if (lower.equals("material") && i + 1 < lines.size()) r.material = lines.get(i + 1);
            if (lower.equals("customer sku") && i + 1 < lines.size()) r.customerSku = Gs1Utils.digitsOnly(lines.get(i + 1));
            if (lower.equals("count") && i + 1 < lines.size()) {
                String v = Gs1Utils.digitsOnly(lines.get(i + 1));
                if (!v.isEmpty()) r.palletCount = v;
            }
        }
    }

    private static void parseProduct(List<String> lines, OcrResult r) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("the ritual of ")) {
                r.productName = cleanProduct(line);
                r.mark("product", OcrResult.Confidence.HIGH);
                if (i + 1 < lines.size() && isDescription(lines.get(i + 1))) {
                    r.description = cleanProduct(lines.get(i + 1));
                    r.mark("description", OcrResult.Confidence.MEDIUM);
                }
                return;
            }
        }

        if (r.detectedType.equals("SEMIFINISHED")) {
            for (String line : lines) {
                if (isDescription(line) && !line.toLowerCase(Locale.ROOT).contains("semifinished")) {
                    r.productName = cleanProduct(line);
                    r.mark("product", OcrResult.Confidence.MEDIUM);
                    return;
                }
            }
        }

        if (r.detectedType.equals("PALLET LOGISTICS") && !r.material.isEmpty()) {
            r.productName = r.material;
            r.mark("product", OcrResult.Confidence.HIGH);
        }
    }

    private static boolean isDescription(String line) {
        if (line == null) return false;
        String s = line.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (s.length() < 4 || s.length() > 100) return false;
        if (lower.startsWith("rituals") || lower.startsWith("art") || lower.startsWith("batch")
                || lower.startsWith("made in") || lower.startsWith("lan") || lower.startsWith("ref")
                || lower.startsWith("exp") || lower.startsWith("po code") || lower.contains("herengracht")
                || lower.contains("amsterdam") || lower.matches("^\\d+\\s*[x×X].*")) return false;
        int letters = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) letters++;
        return letters >= 4;
    }

    private static String cleanProduct(String s) {
        return s.replaceAll("(?i)\\s+GS$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static void findPackageGtin(List<String> lines, OcrResult r) {
        Pattern p = Pattern.compile("(?<!\\d)(\\d{13,14})(?!\\d)");
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("art") || lower.contains("batch") || lower.contains("ref") || lower.matches(".*\\d+\\s*[x×X].*")) continue;
            Matcher m = p.matcher(line);
            while (m.find()) {
                String normalized = Gs1Utils.normalizeGtin14(m.group(1));
                if (!normalized.isEmpty()) {
                    r.packageGtin14 = normalized;
                    r.mark("gtin", OcrResult.Confidence.MEDIUM);
                    return;
                }
            }
            String digits = Gs1Utils.digitsOnly(line);
            String normalized = Gs1Utils.normalizeGtin14(digits);
            if (!normalized.isEmpty()) {
                r.packageGtin14 = normalized;
                r.mark("gtin", OcrResult.Confidence.MEDIUM);
                return;
            }
        }
    }
}
