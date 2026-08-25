package com.labeltools.palletlabel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 1001;
    private static final int REQ_GALLERY = 1002;
    private static final int REQ_SAVE_PDF = 1003;

    private static final String PREFS = "pallet_label_prefs";
    private static final String DEFAULT_OLD_SSCC = "087109190015360945";

    private EditText referenceEdit, topRightSmallEdit, routeEdit, productEdit, packArticleEdit;
    private EditText gtinEdit, cartonsEdit, piecesEdit, expiryEdit, batchEdit, articleEdit, ssccEdit;
    private TextView totalText, photoStatus, gs1Status;
    private ImageView preview;

    private SharedPreferences prefs;
    private TextRecognizer textRecognizer;
    private BarcodeScanner barcodeScanner;
    private Uri pendingCameraUri;
    private byte[] pendingPdf;
    private String lastOcrText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        setContentView(buildUi());
        loadForm();
        updateTotal();
        generatePreview(false);
    }

    private View buildUi() {
        int pad = dp(16);
        int gap = dp(8);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Pallet Label Generator");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Offline • GS1-128 • PDF 105 × 148 mm\nBez połączenia z drukarką i bez Business Central");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, gap / 2, 0, gap * 2);
        root.addView(subtitle);

        root.addView(section("1. Zdjęcie / odczyt danych"));
        LinearLayout photoRow = horizontal();
        Button camera = button("ZRÓB ZDJĘCIE");
        camera.setOnClickListener(v -> takePhoto());
        Button gallery = button("WYBIERZ FOTO");
        gallery.setOnClickListener(v -> choosePhoto());
        photoRow.addView(camera, weight());
        photoRow.addView(gallery, weightMargin());
        root.addView(photoRow);

        photoStatus = new TextView(this);
        photoStatus.setText("Możesz też wypełnić wszystkie pola ręcznie.");
        photoStatus.setTextSize(13);
        photoStatus.setPadding(0, gap, 0, gap);
        root.addView(photoStatus);

        Button showOcr = button("POKAŻ OSTATNI ODCZYT OCR");
        showOcr.setOnClickListener(v -> showOcrText());
        root.addView(showOcr);

        root.addView(section("2. Dane etykiety"));
        referenceEdit = field(root, "Numer / REF (góra lewa)", "1501333", false);
        topRightSmallEdit = field(root, "Pole prawe małe (np. NLVL)", "NLVL", false);
        routeEdit = field(root, "Pole prawe duże (np. 91/NR)", "91/NR", false);
        productEdit = field(root, "Linia produktu", "RC SCRUB YOZAKURA", false);
        packArticleEdit = field(root, "Linia opakowanie / artykuł", "125G1120211", false);
        gtinEdit = field(root, "CONTENT / GTIN-14", "08720296062361", true);

        LinearLayout qtyRow = horizontal();
        LinearLayout col1 = verticalWeight();
        col1.addView(smallLabel("COUNT / kartony"));
        cartonsEdit = edit("16", true);
        cartonsEdit.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) updateTotal(); });
        col1.addView(cartonsEdit);
        LinearLayout col2 = verticalWeight();
        col2.addView(smallLabel("Sztuk / karton"));
        piecesEdit = edit("60", true);
        piecesEdit.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) updateTotal(); });
        col2.addView(piecesEdit);
        qtyRow.addView(col1, weight());
        qtyRow.addView(col2, weightMargin());
        root.addView(qtyRow);

        totalText = new TextView(this);
        totalText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        totalText.setPadding(0, gap / 2, 0, gap);
        root.addView(totalText);

        expiryEdit = field(root, "EXPIRY (DD/MM/YY)", defaultExpiry(), false);
        Button plusTwo = button("USTAW DZISIAJ + 2 LATA");
        plusTwo.setOnClickListener(v -> expiryEdit.setText(defaultExpiry()));
        root.addView(plusTwo);

        batchEdit = field(root, "BATCH / LOT – AI (10)", "12342064", false);
        articleEdit = field(root, "INTERNAL ARTICLE NR – AI (240)", "1120211", false);

        root.addView(section("3. SSCC"));
        ssccEdit = field(root, "SSCC (18 cyfr)", DEFAULT_OLD_SSCC, true);
        Button nextSscc = button("NOWY SSCC (+1 + NOWA CYFRA KONTROLNA)");
        nextSscc.setOnClickListener(v -> nextSscc());
        root.addView(nextSscc);

        TextView ssccInfo = new TextView(this);
        ssccInfo.setText("SSCC jest przechowywany tylko na tym telefonie. Przy nowej palecie naciśnij NOWY SSCC. Przy reprintcie pozostaw ten sam numer.");
        ssccInfo.setTextSize(12);
        ssccInfo.setPadding(0, gap, 0, gap);
        root.addView(ssccInfo);

        root.addView(section("4. Podgląd i PDF"));
        Button generate = button("GENERUJ / ODŚWIEŻ ETYKIETĘ");
        generate.setOnClickListener(v -> generatePreview(true));
        root.addView(generate);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setPadding(0, gap, 0, gap);
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        gs1Status = new TextView(this);
        gs1Status.setTextSize(12);
        gs1Status.setTypeface(Typeface.MONOSPACE);
        gs1Status.setTextIsSelectable(true);
        gs1Status.setPadding(0, gap, 0, gap);
        root.addView(gs1Status);

        LinearLayout outRow = horizontal();
        Button save = button("ZAPISZ PDF");
        save.setOnClickListener(v -> savePdf());
        Button share = button("UDOSTĘPNIJ PDF");
        share.setOnClickListener(v -> sharePdf());
        outRow.addView(save, weight());
        outRow.addView(share, weightMargin());
        root.addView(outRow);

        Button saveValues = button("ZAPISZ OBECNE DANE JAKO DOMYŚLNE");
        saveValues.setOnClickListener(v -> { saveForm(); toast("Dane zapisane lokalnie."); });
        root.addView(saveValues, marginTop(matchWrap(), gap));

        return scroll;
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private TextView smallLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText field(LinearLayout parent, String label, String def, boolean numeric) {
        parent.addView(smallLabel(label));
        EditText e = edit(def, numeric);
        parent.addView(e, matchWrap());
        return e;
    }

    private EditText edit(String def, boolean numeric) {
        EditText e = new EditText(this);
        e.setText(def);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout verticalWeight() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightMargin() {
        LinearLayout.LayoutParams p = weight();
        p.leftMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams marginTop(LinearLayout.LayoutParams p, int top) {
        p.topMargin = top;
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String defaultExpiry() {
        return LocalDate.now().plusYears(2).format(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US));
    }

    private void updateTotal() {
        int c = parseInt(cartonsEdit == null ? "0" : cartonsEdit.getText().toString());
        int p = parseInt(piecesEdit == null ? "0" : piecesEdit.getText().toString());
        if (totalText != null) totalText.setText("Razem: " + (c * p) + " szt.");
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "images");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast("Nie udało się otworzyć aparatu: " + e.getMessage());
        }
    }

    private void choosePhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQ_CAMERA && pendingCameraUri != null) {
            analyzeImage(pendingCameraUri);
        } else if (requestCode == REQ_GALLERY && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            analyzeImage(uri);
        } else if (requestCode == REQ_SAVE_PDF && data != null && data.getData() != null && pendingPdf != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new Exception("No output stream");
                out.write(pendingPdf);
                out.flush();
                toast("PDF zapisany.");
            } catch (Exception e) {
                toast("Błąd zapisu PDF: " + e.getMessage());
            }
        }
    }

    private void analyzeImage(Uri uri) {
        photoStatus.setText("Analizuję zdjęcie: OCR + barcode...");
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            final int[] pending = {2};
            final List<String> found = new ArrayList<>();

            textRecognizer.process(image)
                    .addOnSuccessListener(text -> {
                        lastOcrText = text.getText();
                        applyOcrText(lastOcrText, found);
                        pending[0]--;
                        if (pending[0] == 0) finishImageAnalysis(found);
                    })
                    .addOnFailureListener(e -> {
                        found.add("OCR error: " + e.getClass().getSimpleName());
                        pending[0]--;
                        if (pending[0] == 0) finishImageAnalysis(found);
                    });

            barcodeScanner.process(image)
                    .addOnSuccessListener(codes -> {
                        applyBarcodes(codes, found);
                        pending[0]--;
                        if (pending[0] == 0) finishImageAnalysis(found);
                    })
                    .addOnFailureListener(e -> {
                        found.add("Barcode error: " + e.getClass().getSimpleName());
                        pending[0]--;
                        if (pending[0] == 0) finishImageAnalysis(found);
                    });
        } catch (Exception e) {
            photoStatus.setText("Błąd analizy zdjęcia: " + e.getMessage());
        }
    }

    private void applyOcrText(String text, List<String> found) {
        String article = firstGroup(text, "(?i)(?:art\\.?\\s*nr(?:\\.?\\s*(?:tu|cu))?|internal\\s+article\\s+nr)\\s*[:\\-]?\\s*(\\d{5,12})");
        if (!article.isEmpty()) {
            articleEdit.setText(article);
            found.add("article=" + article);
        }

        String batch = firstGroup(text, "(?i)(?:batch\\s*(?:code|/\\s*lot)?|batch|lot)\\s*[:\\-]?\\s*([A-Z0-9-]{4,20})");
        if (!batch.isEmpty()) {
            batchEdit.setText(batch);
            found.add("batch=" + batch);
        }

        String pieces = firstGroup(text, "(?i)\\b(\\d{1,4})\\s*[x×]\\s*\\d{8,14}\\b");
        if (!pieces.isEmpty()) {
            piecesEdit.setText(pieces);
            found.add("pcs/carton=" + pieces);
        }

        Matcher m = Pattern.compile("\\b(\\d{14})\\b").matcher(text);
        while (m.find()) {
            String candidate = m.group(1);
            if (Gs1Utils.isValidGtin14(candidate)) {
                gtinEdit.setText(candidate);
                found.add("GTIN14=" + candidate + " (OCR)");
                break;
            }
        }

        // Helpful best-effort extraction for the visible product name. It never changes the field
        // if no clear Ritual/Rituals product line is present.
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.length() >= 8 && trimmed.length() <= 70 && trimmed.toLowerCase(Locale.ROOT).contains("ritual")) {
                if (productEdit.getText().toString().trim().isEmpty()) productEdit.setText(trimmed.toUpperCase(Locale.ROOT));
                break;
            }
        }
        updateTotal();
    }

    private void applyBarcodes(List<Barcode> codes, List<String> found) {
        for (Barcode b : codes) {
            String raw = b.getRawValue();
            if (raw == null) continue;
            String digits = Gs1Utils.digitsOnly(raw);
            if (digits.length() == 14 && Gs1Utils.isValidGtin14(digits)) {
                gtinEdit.setText(digits);
                found.add("GTIN14=" + digits + " (barcode)");
                return; // prefer the first valid GTIN-14 barcode
            }
        }
        if (!codes.isEmpty()) found.add("barcodes=" + codes.size() + " (no valid GTIN-14 auto-selected)");
    }

    private String firstGroup(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private void finishImageAnalysis(List<String> found) {
        if (found.isEmpty()) photoStatus.setText("Zdjęcie odczytane, ale nie znaleziono pewnych pól. Uzupełnij ręcznie.");
        else photoStatus.setText("Odczytano: " + String.join(" • ", found));
        saveForm();
        generatePreview(false);
    }

    private void showOcrText() {
        new AlertDialog.Builder(this)
                .setTitle("Ostatni odczyt OCR")
                .setMessage(lastOcrText.isEmpty() ? "Brak odczytu w tej sesji." : lastOcrText)
                .setPositiveButton("OK", null)
                .show();
    }

    private void nextSscc() {
        try {
            String next = Gs1Utils.nextSscc(ssccEdit.getText().toString());
            ssccEdit.setText(next);
            prefs.edit().putString("sscc", next).apply();
            toast("Nowy SSCC: " + next);
            generatePreview(false);
        } catch (Exception e) {
            toast("Nieprawidłowy poprzedni SSCC: " + e.getMessage());
        }
    }

    private LabelData readData() throws Exception {
        LabelData d = new LabelData();
        d.reference = clean(referenceEdit);
        d.topRightSmall = clean(topRightSmallEdit).toUpperCase(Locale.ROOT);
        d.topRightLarge = clean(routeEdit);
        d.productLine = clean(productEdit).toUpperCase(Locale.ROOT);
        d.packArticleLine = clean(packArticleEdit).toUpperCase(Locale.ROOT);
        d.contentGtin = Gs1Utils.digitsOnly(clean(gtinEdit));
        d.cartons = parseInt(clean(cartonsEdit));
        d.piecesPerCarton = parseInt(clean(piecesEdit));
        d.batch = clean(batchEdit).toUpperCase(Locale.ROOT);
        d.article = clean(articleEdit).toUpperCase(Locale.ROOT);
        d.sscc = Gs1Utils.digitsOnly(clean(ssccEdit));
        d.expiryDisplay = clean(expiryEdit);

        if (!Gs1Utils.isValidGtin14(d.contentGtin)) throw new Exception("CONTENT musi być prawidłowym GTIN-14 (z cyfrą kontrolną).");
        if (d.cartons < 1 || d.cartons > 99999999) throw new Exception("COUNT musi być większy od 0.");
        if (d.piecesPerCarton < 1) throw new Exception("Sztuk/karton musi być większe od 0.");
        if (d.batch.isEmpty() || d.batch.length() > 20) throw new Exception("Batch AI (10) musi mieć 1–20 znaków.");
        if (d.article.isEmpty() || d.article.length() > 30) throw new Exception("Article AI (240) musi mieć 1–30 znaków.");
        if (!asciiOnly(d.batch) || !asciiOnly(d.article)) throw new Exception("Batch i Article muszą używać znaków ASCII obsługiwanych przez Code 128.");
        if (!Gs1Utils.isValidSscc(d.sscc)) throw new Exception("SSCC musi mieć 18 cyfr i prawidłową cyfrę kontrolną.");

        try {
            LocalDate date = LocalDate.parse(d.expiryDisplay, DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US));
            d.expiryAi = date.format(DateTimeFormatter.ofPattern("yyMMdd", Locale.US));
        } catch (DateTimeParseException e) {
            throw new Exception("EXPIRY wpisz jako DD/MM/YY, np. 25/08/28.");
        }
        return d;
    }

    private boolean asciiOnly(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 127) return false;
        return true;
    }

    private String clean(EditText e) {
        return e.getText().toString().trim();
    }

    private void generatePreview(boolean notify) {
        try {
            updateTotal();
            LabelData d = readData();
            Bitmap bmp = LabelRenderer.renderPreview(d);
            preview.setImageBitmap(bmp);
            gs1Status.setText(
                    "GS1-128:\n" + d.barcode1Human() +
                    "\n" + d.barcode2Human() + "   [FNC1 separator after batch]" +
                    "\n" + d.barcode3Human() +
                    "\n\nTotal: " + d.totalPieces() + " pcs");
            saveForm();
            if (notify) toast("Etykieta wygenerowana.");
        } catch (Exception e) {
            gs1Status.setText("Błąd: " + e.getMessage());
            if (notify) toast(e.getMessage());
        }
    }

    private void savePdf() {
        try {
            LabelData d = readData();
            pendingPdf = LabelRenderer.renderPdf(d);
            saveForm();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_TITLE, fileName(d));
            startActivityForResult(i, REQ_SAVE_PDF);
        } catch (Exception e) {
            toast("Nie można utworzyć PDF: " + e.getMessage());
        }
    }

    private void sharePdf() {
        try {
            LabelData d = readData();
            byte[] pdf = LabelRenderer.renderPdf(d);
            File f = new File(getCacheDir(), fileName(d));
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(pdf);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Udostępnij etykietę PDF"));
        } catch (Exception e) {
            toast("Nie można udostępnić PDF: " + e.getMessage());
        }
    }

    private String fileName(LabelData d) {
        return "PalletLabel_" + safe(d.article) + "_" + safe(d.batch) + "_" + d.sscc + ".pdf";
    }

    private String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void saveForm() {
        if (referenceEdit == null) return;
        prefs.edit()
                .putString("reference", clean(referenceEdit))
                .putString("topRightSmall", clean(topRightSmallEdit))
                .putString("route", clean(routeEdit))
                .putString("product", clean(productEdit))
                .putString("pack", clean(packArticleEdit))
                .putString("gtin", clean(gtinEdit))
                .putString("cartons", clean(cartonsEdit))
                .putString("pieces", clean(piecesEdit))
                .putString("expiry", clean(expiryEdit))
                .putString("batch", clean(batchEdit))
                .putString("article", clean(articleEdit))
                .putString("sscc", clean(ssccEdit))
                .apply();
    }

    private void loadForm() {
        referenceEdit.setText(prefs.getString("reference", "1501333"));
        topRightSmallEdit.setText(prefs.getString("topRightSmall", "NLVL"));
        routeEdit.setText(prefs.getString("route", "91/NR"));
        productEdit.setText(prefs.getString("product", "RC SCRUB YOZAKURA"));
        packArticleEdit.setText(prefs.getString("pack", "125G1120211"));
        gtinEdit.setText(prefs.getString("gtin", "08720296062361"));
        cartonsEdit.setText(prefs.getString("cartons", "16"));
        piecesEdit.setText(prefs.getString("pieces", "60"));
        expiryEdit.setText(prefs.getString("expiry", defaultExpiry()));
        batchEdit.setText(prefs.getString("batch", "12342064"));
        articleEdit.setText(prefs.getString("article", "1120211"));
        ssccEdit.setText(prefs.getString("sscc", DEFAULT_OLD_SSCC));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveForm();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { textRecognizer.close(); } catch (Exception ignored) {}
        try { barcodeScanner.close(); } catch (Exception ignored) {}
    }
}
