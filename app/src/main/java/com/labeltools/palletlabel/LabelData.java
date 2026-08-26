package com.labeltools.palletlabel;

public class LabelData {
    public String reference = "";
    public String topRightSmall = "";
    public String topRightLarge = "";
    public String productLine = "";
    public String description = "";
    public String packArticleLine = "";
    public String madeIn = "";
    public String contentGtin = "";
    public int cartons = 16;
    public int piecesPerCarton = 0;
    public String expiryDisplay = "";
    public String expiryAi = "";
    public String batch = "";
    public String article = "";
    public String articleTu = "";
    public String articleCu = "";
    public String sscc = "";

    public String logisticsArticle = "";
    public String material = "";
    public String customerSku = "";
    public String grossWeight = "";
    public String poCode = "";
    public String dataMatrixPayload = "";
    public String shipperLine1 = "Firma";
    public String shipperLine2 = "Mann & Schröder GmbH";
    public String shipperLine3 = "Bahnhofstraße 14";
    public String shipperLine4 = "74936 Siegelsbach";
    public String shipperLine5 = "Deutschland";

    public int totalPieces() {
        return cartons * piecesPerCarton;
    }

    public String barcode1Human() {
        StringBuilder s = new StringBuilder("(02)").append(contentGtin);
        if (expiryAi != null && !expiryAi.isEmpty()) s.append("(17)").append(expiryAi);
        s.append("(37)").append(cartons);
        return s.toString();
    }

    public String barcode2Human() {
        return "(10)" + batch + "(240)" + article;
    }

    public String barcode3Human() {
        return "(00)" + sscc;
    }
}
