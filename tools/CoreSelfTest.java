import com.labeltools.palletlabel.Gs1Utils;
import com.labeltools.palletlabel.LabelData;
import com.labeltools.palletlabel.OcrParser;
import com.labeltools.palletlabel.OcrResult;

import java.util.Arrays;

public class CoreSelfTest {
    private static void eq(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected [" + expected + "] but got [" + actual + "]");
        }
    }

    private static void ok(String label, boolean value) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        testBrownCarton();
        testBrownBarcodePriority();
        testWhiteCarton();
        testSemifinished();
        testNoExpiry();
        testExpiryBarcode();
        testGs1();
        System.out.println("CoreSelfTest: ALL OK");
    }

    private static void testBrownCarton() {
        String text =
                "RITUALS...\n" +
                "The Ritual of Yozakura Body Cream 100ml\n" +
                "hydrating & nourishing body cream\n" +
                "Made in: Germany\n" +
                "Art Nr. TU: 1120728\n" +
                "Art Nr. CU: 1120728\n" +
                "100 x 8719134207286\n" +
                "LAN: 48913970\n" +
                "Batch code: 0362929\n" +
                "8 720296 066512";
        OcrResult r = OcrParser.parse(text, "RITUALS BROWN");
        eq("brown TU", "1120728", r.articleTu);
        eq("brown CU", "1120728", r.articleCu);
        eq("brown qty", "100", r.piecesPerCarton);
        eq("brown unit ean", "8719134207286", r.unitEan);
        eq("brown batch", "0362929", r.batch);
        eq("brown made in", "Germany", r.madeIn);
        eq("brown package", "08720296066512", r.packageGtin14);
        ok("brown product", r.productName.toLowerCase().contains("yozakura") && r.productName.toLowerCase().contains("100ml"));
    }

    private static void testBrownBarcodePriority() {
        String text =
                "RITUALS\n" +
                "Art Nr. TU: 1120728 Art Nr. CU: 1120728\n" +
                "100 x 8719134207286\n" +
                "Made in Germany\n" +
                "Batch code: g362929";
        OcrResult r = OcrParser.parse(text, "RITUALS BROWN");
        OcrParser.applyBarcodeValues(r, Arrays.asList("8719134207286", "8720296066512"));
        eq("corrected batch", "0362929", r.batch);
        eq("barcode package priority", "08720296066512", r.packageGtin14);
        eq("barcode source", "BARCODE_13_PACKAGE", r.gtinSource);
        ok("batch normalized", r.batchNormalized);
    }

    private static void testWhiteCarton() {
        String text =
                "RITUALS\n" +
                "The Ritual of Yozakura Pink Sugar Scrub 125g\n" +
                "Art.Nr.TU/CU 1120211\n" +
                "60 x 8719134202113\n" +
                "Made in The Netherlands\n" +
                "Batch 12733352\n" +
                "08720296062361";
        OcrResult r = OcrParser.parse(text, "RITUALS WHITE");
        eq("white TU", "1120211", r.articleTu);
        eq("white CU", "1120211", r.articleCu);
        eq("white qty", "60", r.piecesPerCarton);
        eq("white batch", "12733352", r.batch);
        eq("white package", "08720296062361", r.packageGtin14);
    }

    private static void testSemifinished() {
        String text =
                "Semifinished Ribbon Karma Small 2026\n" +
                "Art.Nr.TU/CU: 1122526\n" +
                "900 x 8719134225266\n" +
                "PO code 101-00083244-1\n" +
                "AM26\n" +
                "Made in China\n" +
                "08720296080273";
        OcrResult r = OcrParser.parse(text, "SEMIFINISHED");
        eq("semi article", "1122526", r.article);
        eq("semi qty", "900", r.piecesPerCarton);
        eq("semi batch", "AM26", r.batch);
        eq("semi PO", "101-00083244-1", r.poCode);
        eq("semi package", "08720296080273", r.packageGtin14);
    }

    private static void testNoExpiry() {
        OcrResult r = OcrParser.parse(
                "The Ritual of Yozakura Body Cream 100ml\nArt Nr. TU: 1120728\nBatch code: 0362929",
                "RITUALS BROWN");
        eq("no expiry", "", r.expiryRaw);
        LabelData d = new LabelData();
        d.contentGtin = "08720296066512";
        d.cartons = 16;
        eq("barcode no AI17", "(02)08720296066512(37)16", d.barcode1Human());
    }


    private static void testExpiryBarcode() {
        LabelData d = new LabelData();
        d.contentGtin = "08720296062361";
        d.expiryAi = "280825";
        d.cartons = 16;
        eq("barcode with AI17", "(02)08720296062361(17)280825(37)16", d.barcode1Human());
    }

    private static void testGs1() {
        ok("GTIN14 valid", Gs1Utils.isValidGtin14("08720296066512"));
        eq("GTIN13 normalize", "08720296066512", Gs1Utils.normalizeGtin14("8720296066512"));
        eq("weight AI3302", "024919", Gs1Utils.grossWeightAi3302("249,193"));
        ok("SSCC valid", Gs1Utils.isValidSscc("087109190015360945"));
        eq("SSCC next", "087109190015360952", Gs1Utils.nextSscc("087109190015360945"));
    }
}
