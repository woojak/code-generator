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
import java.util.Map;

public final class LabelRenderer {
    public static final float LABEL_W_MM = 105f;
    public static final float LABEL_H_MM = 148f;
    private static final char FNC1 = '\u00f1';

    private LabelRenderer() {}

    public static Bitmap renderPreview(LabelData d) throws Exception {
        int width = 1240;
        int height = Math.round(width * LABEL_H_MM / LABEL_W_MM);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        render(canvas, width, height, d);
        return bmp;
    }

    public static byte[] renderPdf(LabelData d) throws Exception {
        int pageW = Math.round(LABEL_W_MM * 72f / 25.4f);
        int pageH = Math.round(LABEL_H_MM * 72f / 25.4f);
        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, 1).create();
        PdfDocument.Page page = pdf.startPage(info);
        render(page.getCanvas(), pageW, pageH, d);
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

        text(c, p, d.reference, 3f, 10f, 11.6f, sx, sy, false);
        text(c, p, d.topRightSmall, 84.5f, 5.8f, 3.9f, sx, sy, false);
        text(c, p, d.topRightLarge, 80f, 13f, 7.3f, sx, sy, false);

        fitText(c, p, d.productLine, 3f, 20.1f, 58f, 4.2f, 2.4f, sx, sy, false);
        fitText(c, p, d.packArticleLine, 63f, 20.1f, 39f, 4f, 2.5f, sx, sy, false);
        line(c, p, 2.5f, 22.2f, 102.5f, 22.2f, 0.22f, sx, sy);

        heading(c, p, "CONTENT", 3f, 27f, sx, sy);
        heading(c, p, "COUNT", 63f, 27f, sx, sy);
        text(c, p, d.contentGtin, 3f, 34.6f, 6.9f, sx, sy, false);
        text(c, p, String.valueOf(d.cartons), 63f, 34.6f, 6.9f, sx, sy, false);

        heading(c, p, "EXPIRY ( DD/MM/YY )", 3f, 42.3f, sx, sy);
        heading(c, p, "BATCH / LOT", 63f, 42.3f, sx, sy);
        text(c, p, d.expiryDisplay, 3f, 49f, 6.1f, sx, sy, false);
        fitText(c, p, d.batch, 63f, 49f, 39f, 6.1f, 3.5f, sx, sy, false);

        heading(c, p, "INTERNAL ARTICLE NR", 3f, 56.2f, sx, sy);
        text(c, p, d.article, 3f, 62.5f, 5.9f, sx, sy, false);

        heading(c, p, "SSCC", 3f, 67.2f, sx, sy);
        text(c, p, Gs1Utils.spacedSscc(d.sscc), 3f, 72f, 4.7f, sx, sy, false);
        line(c, p, 2.5f, 74.2f, 102.5f, 74.2f, 0.22f, sx, sy);

        String raw1 = "02" + d.contentGtin + (d.expiryAi.isEmpty() ? "" : "17" + d.expiryAi) + "37" + d.cartons;
        String raw2 = "10" + d.batch + FNC1 + "240" + d.article;
        String raw3 = "00" + d.sscc;

        drawBarcode(c, raw1, 4f, 77.2f, 97f, 18f, sx, sy);
        text(c, p, d.barcode1Human(), 4f, 98f, 2.8f, sx, sy, true);
        drawBarcode(c, raw2, 4f, 102f, 97f, 18f, sx, sy);
        text(c, p, d.barcode2Human(), 4f, 122.8f, 2.8f, sx, sy, true);
        drawBarcode(c, raw3, 4f, 126.8f, 97f, 16.7f, sx, sy);
        text(c, p, d.barcode3Human(), 4f, 146f, 2.8f, sx, sy, true);
    }

    private static void heading(Canvas c, Paint p, String s, float x, float y, float sx, float sy) {
        text(c, p, s, x, y, 3f, sx, sy, false);
    }

    private static void fitText(Canvas c, Paint p, String s, float xMm, float yMm, float maxWidthMm,
                                float startSizeMm, float minSizeMm, float sx, float sy, boolean normal) {
        float size = startSizeMm;
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, normal ? Typeface.NORMAL : Typeface.BOLD));
        while (size > minSizeMm) {
            p.setTextSize(size * sy);
            if (p.measureText(s == null ? "" : s) <= maxWidthMm * sx) break;
            size -= 0.15f;
        }
        text(c, p, s, xMm, yMm, size, sx, sy, normal);
    }

    private static void text(Canvas c, Paint p, String s, float xMm, float yMm, float sizeMm,
                             float sx, float sy, boolean normal) {
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, normal ? Typeface.NORMAL : Typeface.BOLD));
        p.setTextSize(sizeMm * sy);
        c.drawText(s == null ? "" : s, xMm * sx, yMm * sy, p);
    }

    private static void line(Canvas c, Paint p, float x1, float y1, float x2, float y2,
                             float strokeMm, float sx, float sy) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, strokeMm * sy));
        c.drawLine(x1 * sx, y1 * sy, x2 * sx, y2 * sy, p);
        p.setStyle(Paint.Style.FILL);
    }

    public static void drawBarcode(Canvas c, String raw, float xMm, float yMm, float wMm, float hMm,
                                   float sx, float sy) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.GS1_FORMAT, true);
        BitMatrix matrix = new MultiFormatWriter().encode(raw, BarcodeFormat.CODE_128, 1200, 180, hints);
        drawMatrix(c, matrix, xMm, yMm, wMm, hMm, sx, sy);
    }

    public static void drawMatrix(Canvas c, BitMatrix matrix, float xMm, float yMm, float wMm, float hMm,
                                  float sx, float sy) {
        Bitmap bmp = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
        int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
        int pos = 0;
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                pixels[pos++] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        bmp.setPixels(pixels, 0, matrix.getWidth(), 0, 0, matrix.getWidth(), matrix.getHeight());
        Paint bp = new Paint();
        bp.setFilterBitmap(false);
        RectF dst = new RectF(xMm * sx, yMm * sy, (xMm + wMm) * sx, (yMm + hMm) * sy);
        c.drawBitmap(bmp, null, dst, bp);
        bmp.recycle();
    }
}
