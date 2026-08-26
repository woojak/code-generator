package com.labeltools.palletlabel;

import java.util.LinkedHashMap;
import java.util.Map;

public class OcrResult {
    public enum Confidence { HIGH, MEDIUM, LOW }

    public String detectedType = "UNKNOWN";
    public String productName = "";
    public String description = "";
    public String articleTu = "";
    public String articleCu = "";
    public String article = "";
    public String batch = "";
    public String piecesPerCarton = "";
    public String unitEan = "";
    public String packageGtin14 = "";
    public String madeIn = "";
    public String expiryRaw = "";
    public String poCode = "";
    public String logisticsArticle = "";
    public String material = "";
    public String customerSku = "";
    public String palletCount = "";
    public String sscc = "";
    public String grossWeight = "";
    public boolean cacheUsed = false;

    private final Map<String, Confidence> confidence = new LinkedHashMap<>();

    public void mark(String key, Confidence value) {
        confidence.put(key, value);
    }

    public Confidence confidenceOf(String key) {
        Confidence c = confidence.get(key);
        return c == null ? Confidence.LOW : c;
    }

    public boolean hasAnyData() {
        return !(productName.isEmpty() && article.isEmpty() && batch.isEmpty()
                && packageGtin14.isEmpty() && piecesPerCarton.isEmpty()
                && customerSku.isEmpty() && sscc.isEmpty());
    }
}
