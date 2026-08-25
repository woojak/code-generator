package com.labeltools.palletlabel;

public class LabelData {
    public String reference = "1501333";
    public String topRightSmall = "NLVL";
    public String topRightLarge = "91/NR";
    public String productLine = "RC SCRUB YOZAKURA";
    public String packArticleLine = "125G1120211";
    public String contentGtin = "08720296062361";
    public int cartons = 16;
    public int piecesPerCarton = 60;
    public String expiryDisplay = "25/08/28";
    public String expiryAi = "280825";
    public String batch = "12342064";
    public String article = "1120211";
    public String sscc = "087109190015360952";

    public int totalPieces() {
        return cartons * piecesPerCarton;
    }

    public String barcode1Human() {
        return "(02)" + contentGtin + "(17)" + expiryAi + "(37)" + cartons;
    }

    public String barcode2Human() {
        return "(10)" + batch + "(240)" + article;
    }

    public String barcode3Human() {
        return "(00)" + sscc;
    }
}
