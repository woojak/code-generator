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
        int width = 1240; // ~300 dpi at 105 mm
        int height = Math.round(width * LABEL_H_MM / LABEL_W_MM);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        render(canvas, width, height, d);
        return bmp;
    }

    public static byte[] renderPdf(LabelData d) throws Exception {
        // Android PdfDocument uses points. 105 x 148 mm ~= 298 x 420 pt.
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
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        text(c, p, d.reference, 3.0f, 10.0f, 11.6f, sx, sy, false);
        text(c, p, d.topRightSmall, 84.5f, 5.8f, 3.9f, sx, sy, false);
        text(c, p, d.topRightLarge, 80.0f, 13.0f, 7.3f, sx, sy, false);

        text(c, p, d.productLine, 3.0f, 20.1f, 4.2f, sx, sy, false);
        text(c, p, d.packArticleLine, 47.0f, 20.1f, 4.0f, sx, sy, false);
        line(c, p, 2.5f, 22.2f, 102.5f, 22.2f, 0.22f, sx, sy);

        heading(c, p, "CONTENT", 3.0f, 27.0f, sx, sy);
        heading(c, p, "COUNT", 63.0f, 27.0f, sx, sy);
        text(c, p, d.contentGtin, 3.0f, 34.6f, 6.9f, sx, sy, false);
        text(c, p, String.valueOf(d.cartons), 63.0f, 34.6f, 6.9f, sx, sy, false);

        heading(c, p, "EXPIRY ( DD/MM/YY )", 3.0f, 42.3f, sx, sy);
        heading(c, p, "BATCH / LOT", 63.0f, 42.3f, sx, sy);
        text(c, p, d.expiryDisplay, 3.0f, 49.0f, 6.1f, sx, sy, false);
        text(c, p, d.batch, 63.0f, 49.0f, 6.1f, sx, sy, false);

        heading(c, p, "INTERNAL ARTICLE NR", 3.0f, 56.2f, sx, sy);
        text(c, p, d.article, 3.0f, 62.5f, 5.9f, sx, sy, false);

        heading(c, p, "SSCC", 3.0f, 67.2f, sx, sy);
        text(c, p, Gs1Utils.spacedSscc(d.sscc), 3.0f, 72.0f, 4.7f, sx, sy, false);
        line(c, p, 2.5f, 74.2f, 102.5f, 74.2f, 0.22f, sx, sy);

        // Large barcode area, intentionally close to the original sticker proportions.
        String raw1 = "02" + d.contentGtin + "17" + d.expiryAi + "37" + d.cartons;
        String raw2 = "10" + d.batch + FNC1 + "240" + d.article;
        String raw3 = "00" + d.sscc;

        drawBarcode(c, raw1, 4.0f, 77.2f, 97.0f, 18.0f, sx, sy);
        text(c, p, d.barcode1Human(), 4.0f, 98.0f, 2.8f, sx, sy, true);

        drawBarcode(c, raw2, 4.0f, 102.0f, 97.0f, 18.0f, sx, sy);
        text(c, p, d.barcode2Human(), 4.0f, 122.8f, 2.8f, sx, sy, true);

        drawBarcode(c, raw3, 4.0f, 126.8f, 97.0f, 16.7f, sx, sy);
        text(c, p, d.barcode3Human(), 4.0f, 146.0f, 2.8f, sx, sy, true);
    }

    private static void heading(Canvas c, Paint p, String s, float x, float y, float sx, float sy) {
        text(c, p, s, x, y, 3.0f, sx, sy, false);
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

    private static void drawBarcode(Canvas c, String raw, float xMm, float yMm, float wMm, float hMm,
                                    float sx, float sy) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.GS1_FORMAT, true);
        BitMatrix matrix = new MultiFormatWriter().encode(raw, BarcodeFormat.CODE_128, 1200, 180, hints);
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
