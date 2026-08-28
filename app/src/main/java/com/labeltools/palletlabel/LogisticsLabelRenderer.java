package com.labeltools.palletlabel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class LogisticsLabelRenderer {
    public static final float LABEL_W_MM = 105f;
    public static final float LABEL_H_MM = 148f;
    private static final char FNC1 = '\u00f1';

    private LogisticsLabelRenderer() {}

    public static Bitmap renderPreview(LabelData d) throws Exception {
        int width = 1240;
        int height = Math.round(width * LABEL_H_MM / LABEL_W_MM);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        render(new Canvas(bmp), width, height, d);
        return bmp;
    }

    public static byte[] renderPdf(LabelData d) throws Exception {
        int w = Math.round(LABEL_W_MM * 72f / 25.4f);
        int h = Math.round(LABEL_H_MM * 72f / 25.4f);
        PdfDocument pdf = new PdfDocument();
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(w, h, 1).create());
        render(page.getCanvas(), w, h, d);
        pdf.finishPage(page);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.writeTo(out);
        pdf.close();
        return out.toByteArray();
    }

    private static void render(Canvas c, int width, int height, LabelData d) throws Exception {
        c.drawColor(Color.WHITE);
        float sx = width / LABEL_W_MM;
        float sy = height / LABEL_H_MM;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);

        box(c, p, 2, 2, 101, 39, sx, sy);
        text(c, p, "Shipper:", 5, 8, 3.2f, sx, sy, true);
        text(c, p, d.shipperLine1, 5, 13, 3.3f, sx, sy, false);
        text(c, p, d.shipperLine2, 5, 18, 3.3f, sx, sy, false);
        text(c, p, d.shipperLine3, 5, 23, 3.3f, sx, sy, false);
        text(c, p, d.shipperLine4, 5, 28, 3.3f, sx, sy, false);
        text(c, p, d.shipperLine5, 5, 33, 3.3f, sx, sy, false);

        box(c, p, 2, 39, 101, 52, sx, sy);
        text(c, p, "SSCC", 4, 44, 3f, sx, sy, true);
        text(c, p, Gs1Utils.spacedSscc(d.sscc), 4, 50, 4.3f, sx, sy, false);

        box(c, p, 2, 52, 101, 66, sx, sy);
        text(c, p, "ARTICLE", 4, 57, 2.8f, sx, sy, true);
        text(c, p, safe(d.logisticsArticle), 4, 64, 4.2f, sx, sy, false);
        text(c, p, "MATERIAL", 34, 57, 2.8f, sx, sy, true);
        fit(c, p, safe(d.material), 34, 64, 65, 4.0f, 2.5f, sx, sy);

        box(c, p, 2, 66, 101, 80, sx, sy);
        text(c, p, "CONTENT", 4, 71, 2.8f, sx, sy, true);
        text(c, p, d.contentGtin, 4, 78, 4.2f, sx, sy, false);
        text(c, p, "CUSTOMER SKU", 39, 71, 2.8f, sx, sy, true);
        text(c, p, safe(d.customerSku), 39, 78, 4.2f, sx, sy, false);
        text(c, p, "COUNT", 82, 71, 2.8f, sx, sy, true);
        text(c, p, String.valueOf(d.cartons), 82, 78, 4.2f, sx, sy, false);

        box(c, p, 2, 80, 101, 94, sx, sy);
        text(c, p, "EXPIRY (YYYY/MM)", 4, 85, 2.6f, sx, sy, true);
        text(c, p, displayMonth(d.expiryDisplay), 4, 92, 4.2f, sx, sy, false);
        text(c, p, "BATCH", 42, 85, 2.8f, sx, sy, true);
        text(c, p, d.batch, 42, 92, 4.2f, sx, sy, false);
        text(c, p, "BRUTTO PALLET WEIGHT", 70, 85, 2.3f, sx, sy, true);
        text(c, p, d.grossWeight, 76, 92, 4.0f, sx, sy, false);

        String raw1 = "02" + d.contentGtin + (d.expiryAi.isEmpty() ? "" : "17" + d.expiryAi) + "37" + d.cartons + FNC1 + "10" + d.batch;
        String raw2 = "00" + d.sscc + "3302" + grossAi3302(d.grossWeight);
        LabelRenderer.drawBarcode(c, raw1, 8, 98, 82, 15, sx, sy);
        text(c, p, d.barcode1Human() + "(10)" + d.batch, 28, 116, 2.2f, sx, sy, true);
        LabelRenderer.drawBarcode(c, raw2, 8, 121, 82, 15, sx, sy);
        text(c, p, "(00)" + d.sscc + "(3302)" + grossAi3302(d.grossWeight), 38, 139, 2.2f, sx, sy, true);

        // The 2D payload is deliberately never guessed. It is generated only when supplied manually.
        if (d.dataMatrixPayload != null && !d.dataMatrixPayload.trim().isEmpty()) {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix dm = new MultiFormatWriter().encode(d.dataMatrixPayload, BarcodeFormat.DATA_MATRIX, 280, 280, hints);
            LabelRenderer.drawMatrix(c, dm, 72, 4, 27, 27, sx, sy);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, .3f * sy));
            c.drawRect(new RectF(72 * sx, 4 * sy, 99 * sx, 31 * sy), p);
            p.setStyle(Paint.Style.FILL);
            text(c, p, "2D CODE", 79, 16, 3.2f, sx, sy, true);
            text(c, p, "payload not set", 76, 21, 2.2f, sx, sy, true);
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String displayMonth(String ddMMyy) {
        try {
            String[] p = ddMMyy.split("/");
            if (p.length == 3) return "20" + p[2] + "/" + p[1];
        } catch (Exception ignored) {}
        return ddMMyy;
    }

    private static String grossAi3302(String weight) {
        return Gs1Utils.grossWeightAi3302(weight);
    }

    private static void fit(Canvas c, Paint p, String s, float x, float y, float maxW,
                            float start, float min, float sx, float sy) {
        float size = start;
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        while (size > min) {
            p.setTextSize(size * sy);
            if (p.measureText(s) <= maxW * sx) break;
            size -= .15f;
        }
        text(c, p, s, x, y, size, sx, sy, false);
    }

    private static void text(Canvas c, Paint p, String s, float x, float y, float size,
                             float sx, float sy, boolean normal) {
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, normal ? Typeface.NORMAL : Typeface.BOLD));
        p.setTextSize(size * sy);
        c.drawText(s == null ? "" : s, x * sx, y * sy, p);
    }

    private static void box(Canvas c, Paint p, float x1, float y1, float x2, float y2, float sx, float sy) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, .25f * sy));
        c.drawRect(new RectF(x1 * sx, y1 * sy, x2 * sx, y2 * sy), p);
        p.setStyle(Paint.Style.FILL);
    }
}
