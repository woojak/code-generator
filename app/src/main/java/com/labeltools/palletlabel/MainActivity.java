package com.labeltools.palletlabel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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

    private EditText productEdit, descriptionEdit, articleTuEdit, articleCuEdit, piecesEdit, batchEdit, gtinEdit, madeInEdit;
    private EditText cartonsEdit, expiryEdit, ssccEdit;
    private EditText packArticleEdit, referenceEdit, topRightSmallEdit, routeEdit, poCodeEdit;
    private EditText logisticsArticleEdit, materialEdit, customerSkuEdit, grossWeightEdit, dataMatrixEdit;
    private EditText shipper1Edit, shipper2Edit, shipper3Edit, shipper4Edit, shipper5Edit;
    private TextView totalText, photoStatus, gs1Status, detectedTypeText;
    private ImageView preview;
    private Spinner scanModeSpinner, outputTemplateSpinner;
    private LinearLayout advancedContainer, logisticsContainer, gs1Container;
    private Button advancedToggle, gs1Toggle;

    private SharedPreferences prefs;
    private TextRecognizer textRecognizer;
    private BarcodeScanner barcodeScanner;
    private Uri pendingCameraUri;
    private byte[] pendingPdf;
    private String lastOcrText = "";

    private static class ScanSession {
        int pending = 2;
        String text = "";
        List<String> barcodeValues = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        migrateTo121();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_ITF, Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_DATA_MATRIX)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        setContentView(buildUi());
        loadForm();
        updateTotal();
        updateOutputTemplateUi();
        generatePreview(false);
    }

    private View buildUi() {
        int pad = dp(14);
        int gap = dp(10);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, dp(28));
        root.setBackgroundColor(Color.rgb(244, 245, 247));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Pallet Label Generator");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(24, 24, 24));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView version = new TextView(this);
        version.setText("v1.2.1 • safe scan reset • multi-OCR • offline");
        version.setTextSize(13);
        version.setTextColor(Color.rgb(90, 90, 90));
        version.setPadding(0, dp(2), 0, dp(8));
        root.addView(version);

        TextView flow = new TextView(this);
        flow.setText("1. Skan  →  2. Potwierdź odczyt  →  3. Paleta  →  4. PDF / mail");
        flow.setTextSize(14);
        flow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        flow.setPadding(dp(12), dp(10), dp(12), dp(10));
        flow.setBackground(rounded(Color.WHITE, 12, Color.rgb(220, 222, 226)));
        root.addView(flow);

        LinearLayout scanCard = card("1. Skan etykiety", "AUTO rozpoznaje zwykłe kartony Rituals, Semifinished i etykietę logistyczną. Możesz też wymusić typ.");
        scanModeSpinner = spinner(new String[]{"AUTO", "RITUALS WHITE", "RITUALS BROWN", "SEMIFINISHED", "PALLET LOGISTICS"});
        scanCard.addView(smallLabel("Tryb odczytu"));
        scanCard.addView(scanModeSpinner, matchWrap());

        LinearLayout photoRow = horizontal();
        Button camera = primaryButton("Zrób zdjęcie");
        camera.setOnClickListener(v -> takePhoto());
        Button gallery = secondaryButton("Wybierz zdjęcie");
        gallery.setOnClickListener(v -> choosePhoto());
        photoRow.addView(camera, weight());
        photoRow.addView(gallery, weightMargin());
        scanCard.addView(photoRow, marginTop(matchWrap(), dp(8)));

        detectedTypeText = infoBox("Typ: jeszcze nie rozpoznano");
        scanCard.addView(detectedTypeText, marginTop(matchWrap(), dp(8)));
        photoStatus = infoBox("Brak nowego skanu.");
        scanCard.addView(photoStatus, marginTop(matchWrap(), dp(6)));

        Button showOcr = secondaryButton("Pokaż surowy OCR");
        showOcr.setOnClickListener(v -> showOcrText());
        scanCard.addView(showOcr, marginTop(matchWrap(), dp(8)));
        root.addView(scanCard, marginTop(matchWrap(), gap));

        LinearLayout productCard = card("2. Dane produktu", "Po skanie najpierw zobaczysz ekran weryfikacji. Dane trafiają tutaj dopiero po wybraniu „Użyj tych danych”.");
        productEdit = field(productCard, "Nazwa produktu / materiał", "RC SCRUB YOZAKURA", false);
        productEdit.setSingleLine(false);
        productEdit.setMaxLines(2);
        descriptionEdit = field(productCard, "Opis produktu", "", false);
        descriptionEdit.setSingleLine(false);
        descriptionEdit.setMaxLines(2);

        LinearLayout artRow = horizontal();
        LinearLayout tuCol = verticalWeight();
        tuCol.addView(smallLabel("Article TU"));
        articleTuEdit = edit("1120211", true);
        tuCol.addView(articleTuEdit, matchWrap());
        LinearLayout cuCol = verticalWeight();
        cuCol.addView(smallLabel("Article CU"));
        articleCuEdit = edit("1120211", true);
        cuCol.addView(articleCuEdit, matchWrap());
        artRow.addView(tuCol, weight());
        artRow.addView(cuCol, weightMargin());
        productCard.addView(artRow);

        piecesEdit = field(productCard, "Sztuk / karton", "60", true);
        batchEdit = field(productCard, "Batch / Lot", "12342064", false);
        gtinEdit = field(productCard, "CONTENT / GTIN-14", "08720296062361", true);
        madeInEdit = field(productCard, "Made in", "", false);

        advancedToggle = secondaryButton("Pokaż pola dodatkowe");
        productCard.addView(advancedToggle, marginTop(matchWrap(), dp(8)));
        advancedContainer = new LinearLayout(this);
        advancedContainer.setOrientation(LinearLayout.VERTICAL);
        advancedContainer.setVisibility(View.GONE);
        poCodeEdit = field(advancedContainer, "PO code (Semifinished)", "", false);
        packArticleEdit = field(advancedContainer, "Linia opakowanie / artykuł", "125G1120211", false);
        referenceEdit = field(advancedContainer, "REF / numer góra lewa", "1501333", false);
        topRightSmallEdit = field(advancedContainer, "Pole prawe małe", "NLVL", false);
        routeEdit = field(advancedContainer, "Pole prawe duże", "91/NR", false);
        productCard.addView(advancedContainer);
        advancedToggle.setOnClickListener(v -> {
            boolean open = advancedContainer.getVisibility() == View.VISIBLE;
            advancedContainer.setVisibility(open ? View.GONE : View.VISIBLE);
            advancedToggle.setText(open ? "Pokaż pola dodatkowe" : "Ukryj pola dodatkowe");
        });
        root.addView(productCard, marginTop(matchWrap(), gap));

        LinearLayout palletCard = card("3. Paleta i szablon wydruku", "Standard = obecna etykieta z 3 GS1-128. Logistics = drugi wzór z SSCC / MATERIAL / CUSTOMER SKU / wagą.");
        palletCard.addView(smallLabel("Szablon PDF"));
        outputTemplateSpinner = spinner(new String[]{"STANDARD", "LOGISTICS (BETA)"});
        outputTemplateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateOutputTemplateUi(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        palletCard.addView(outputTemplateSpinner, matchWrap());

        cartonsEdit = field(palletCard, "COUNT / liczba kartonów na palecie", "16", true);
        totalText = infoBox("");
        totalText.setTextSize(18);
        totalText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        palletCard.addView(totalText, marginTop(matchWrap(), dp(4)));
        attachTotalWatcher(cartonsEdit);
        attachTotalWatcher(piecesEdit);

        expiryEdit = field(palletCard, "Expiry dla GS1 (DD/MM/YY)", defaultExpiry(), false);
        Button plusTwo = secondaryButton("Ustaw dziś + 2 lata");
        plusTwo.setOnClickListener(v -> expiryEdit.setText(defaultExpiry()));
        palletCard.addView(plusTwo);
        ssccEdit = field(palletCard, "SSCC (18 cyfr)", DEFAULT_OLD_SSCC, true);
        Button nextSscc = primaryButton("Nowy SSCC");
        nextSscc.setOnClickListener(v -> nextSscc());
        palletCard.addView(nextSscc);

        logisticsContainer = new LinearLayout(this);
        logisticsContainer.setOrientation(LinearLayout.VERTICAL);
        logisticsContainer.setPadding(0, dp(8), 0, 0);
        logisticsArticleEdit = field(logisticsContainer, "ARTICLE (logistics)", "", false);
        materialEdit = field(logisticsContainer, "MATERIAL", "", false);
        customerSkuEdit = field(logisticsContainer, "CUSTOMER SKU", "", false);
        grossWeightEdit = field(logisticsContainer, "Brutto pallet weight (kg)", "", false);
        dataMatrixEdit = field(logisticsContainer, "2D payload (opcjonalny – nie jest zgadywany)", "", false);
        logisticsContainer.addView(infoBox("Kod 2D jest generowany tylko wtedy, gdy podasz jego dokładny payload. Bez payloadu aplikacja zostawia oznaczone miejsce – niczego nie zgaduje."));
        shipper1Edit = field(logisticsContainer, "Shipper line 1", "Firma", false);
        shipper2Edit = field(logisticsContainer, "Shipper line 2", "Mann & Schröder GmbH", false);
        shipper3Edit = field(logisticsContainer, "Shipper line 3", "Bahnhofstraße 14", false);
        shipper4Edit = field(logisticsContainer, "Shipper line 4", "74936 Siegelsbach", false);
        shipper5Edit = field(logisticsContainer, "Shipper line 5", "Deutschland", false);
        palletCard.addView(logisticsContainer);
        root.addView(palletCard, marginTop(matchWrap(), gap));

        LinearLayout outputCard = card("4. Podgląd i wysyłka", "Generuj, sprawdź, a potem udostępnij PDF do firmowej poczty.");
        Button generate = primaryButton("Generuj / odśwież podgląd");
        generate.setOnClickListener(v -> generatePreview(true));
        outputCard.addView(generate);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setPadding(0, dp(8), 0, dp(8));
        outputCard.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Button share = primaryButton("Udostępnij PDF");
        share.setOnClickListener(v -> sharePdf());
        outputCard.addView(share);
        Button save = secondaryButton("Zapisz PDF");
        save.setOnClickListener(v -> savePdf());
        outputCard.addView(save, marginTop(matchWrap(), dp(8)));

        gs1Toggle = secondaryButton("Pokaż szczegóły GS1");
        outputCard.addView(gs1Toggle, marginTop(matchWrap(), dp(8)));
        gs1Container = new LinearLayout(this);
        gs1Container.setOrientation(LinearLayout.VERTICAL);
        gs1Container.setVisibility(View.GONE);
        gs1Status = infoBox("");
        gs1Status.setTypeface(Typeface.MONOSPACE);
        gs1Status.setTextIsSelectable(true);
        gs1Container.addView(gs1Status);
        outputCard.addView(gs1Container);
        gs1Toggle.setOnClickListener(v -> {
            boolean open = gs1Container.getVisibility() == View.VISIBLE;
            gs1Container.setVisibility(open ? View.GONE : View.VISIBLE);
            gs1Toggle.setText(open ? "Pokaż szczegóły GS1" : "Ukryj szczegóły GS1");
        });

        Button saveDefaults = secondaryButton("Zapisz obecne dane jako domyślne");
        saveDefaults.setOnClickListener(v -> { saveForm(); saveProductCache(); toast("Dane zapisane lokalnie."); });
        outputCard.addView(saveDefaults, marginTop(matchWrap(), dp(8)));
        root.addView(outputCard, marginTop(matchWrap(), gap));
        return scroll;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        return s;
    }

    private LinearLayout card(String titleText, String subtitleText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.WHITE, 14, Color.rgb(220, 222, 226)));
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(25, 25, 25));
        card.addView(title);
        TextView sub = new TextView(this);
        sub.setText(subtitleText);
        sub.setTextSize(13);
        sub.setTextColor(Color.rgb(92, 92, 92));
        sub.setPadding(0, dp(3), 0, dp(10));
        card.addView(sub);
        return card;
    }

    private TextView infoBox(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Color.rgb(55, 55, 55));
        t.setPadding(dp(10), dp(9), dp(10), dp(9));
        t.setBackground(rounded(Color.rgb(246,247,249), 10, Color.rgb(226,228,232)));
        return t;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView smallLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(8), 0, dp(2));
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
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setBackground(rounded(Color.rgb(250,250,251), 8, Color.rgb(215,217,221)));
        e.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        b.setTextSize(15);
        return b;
    }

    private Button primaryButton(String text) {
        Button b = button(text);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(Color.rgb(35,35,35), 10, Color.rgb(35,35,35)));
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = button(text);
        b.setTextColor(Color.rgb(35,35,35));
        b.setBackground(rounded(Color.rgb(238,239,241), 10, Color.rgb(215,217,221)));
        return b;
    }

    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout verticalWeight() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); }
    private LinearLayout.LayoutParams weightMargin() { LinearLayout.LayoutParams p = weight(); p.leftMargin = dp(8); return p; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams marginTop(LinearLayout.LayoutParams p, int top) { p.topMargin = top; return p; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String defaultExpiry() { return LocalDate.now().plusYears(2).format(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US)); }

    private void attachTotalWatcher(EditText e) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateTotal(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void updateTotal() {
        int c = parseInt(cartonsEdit == null ? "0" : cartonsEdit.getText().toString());
        int p = parseInt(piecesEdit == null ? "0" : piecesEdit.getText().toString());
        if (totalText != null) totalText.setText(c + " kart. × " + p + " szt. = " + (c * p) + " szt. łącznie");
    }

    private int parseInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }

    private void updateOutputTemplateUi() {
        if (logisticsContainer == null || outputTemplateSpinner == null) return;
        logisticsContainer.setVisibility(isLogistics() ? View.VISIBLE : View.GONE);
    }

    private boolean isLogistics() {
        return outputTemplateSpinner != null && outputTemplateSpinner.getSelectedItemPosition() == 1;
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
        } catch (Exception e) { toast("Nie udało się otworzyć aparatu: " + e.getMessage()); }
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
        if (requestCode == REQ_CAMERA && pendingCameraUri != null) analyzeImage(pendingCameraUri);
        else if (requestCode == REQ_GALLERY && data != null && data.getData() != null) analyzeImage(data.getData());
        else if (requestCode == REQ_SAVE_PDF && data != null && data.getData() != null && pendingPdf != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new Exception("No output stream");
                out.write(pendingPdf); out.flush(); toast("PDF zapisany.");
            } catch (Exception e) { toast("Błąd zapisu PDF: " + e.getMessage()); }
        }
    }

    private void analyzeImage(Uri uri) {
        photoStatus.setText("Analizuję: OCR + barcode...");
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            ScanSession session = new ScanSession();
            textRecognizer.process(image)
                    .addOnSuccessListener(text -> { session.text = text.getText(); lastOcrText = session.text; scanPartDone(session); })
                    .addOnFailureListener(e -> { session.text = ""; scanPartDone(session); });
            barcodeScanner.process(image)
                    .addOnSuccessListener(codes -> {
                        for (Barcode b : codes) if (b.getRawValue() != null) session.barcodeValues.add(b.getRawValue());
                        scanPartDone(session);
                    })
                    .addOnFailureListener(e -> scanPartDone(session));
        } catch (Exception e) { photoStatus.setText("Błąd analizy zdjęcia: " + e.getMessage()); }
    }

    private synchronized void scanPartDone(ScanSession session) {
        session.pending--;
        if (session.pending != 0) return;
        String forced = scanModeSpinner.getSelectedItem().toString();
        OcrResult result = OcrParser.parse(session.text, forced);
        OcrParser.applyBarcodeValues(result, session.barcodeValues);
        enrichFromCache(result);
        showVerification(result);
    }

    private void enrichFromCache(OcrResult r) {
        if (r.article.isEmpty()) return;
        String prefix = "cache_v2_" + r.article + "_";
        if (r.productName.isEmpty()) r.productName = prefs.getString(prefix + "product", "");
        if (r.description.isEmpty()) r.description = prefs.getString(prefix + "description", "");
        if (r.packageGtin14.isEmpty()) r.packageGtin14 = prefs.getString(prefix + "gtin", "");
        if (r.piecesPerCarton.isEmpty()) r.piecesPerCarton = prefs.getString(prefix + "pieces", "");
        if (r.madeIn.isEmpty()) r.madeIn = prefs.getString(prefix + "madeIn", "");
        if (!r.productName.isEmpty()) r.cacheUsed = true;
    }

    private void showVerification(OcrResult r) {
        detectedTypeText.setText("Typ: " + r.detectedType);
        StringBuilder sb = new StringBuilder();
        sb.append("Typ: ").append(r.detectedType).append("\n\n");
        addSummary(sb, "Nazwa", r.productName, r.confidenceOf("product"));
        addSummary(sb, "Opis", r.description, r.confidenceOf("description"));
        addSummary(sb, "Article TU", r.articleTu, r.confidenceOf("article"));
        addSummary(sb, "Article CU", r.articleCu, r.confidenceOf("article"));
        addSummary(sb, "Batch", r.batch, r.confidenceOf("batch"));
        addSummary(sb, "Szt./karton", r.piecesPerCarton, r.confidenceOf("pieces"));
        addSummary(sb, "GTIN-14", r.packageGtin14, r.confidenceOf("gtin"));
        addSummary(sb, "Made in", r.madeIn, r.confidenceOf("madeIn"));
        addSummary(sb, "EXP z kartonu", r.expiryRaw, r.confidenceOf("expiry"));
        addSummary(sb, "ARTICLE logistics", r.logisticsArticle, r.confidenceOf("article"));
        addSummary(sb, "PO code", r.poCode, r.confidenceOf("po"));
        if (r.cacheUsed) sb.append("\nℹ Część brakujących danych uzupełniono z historii tego Article.");
        if (!r.hasAnyData()) sb.append("\n⚠ Nie znaleziono pewnych danych. Możesz anulować i poprawić zdjęcie.");

        new AlertDialog.Builder(this)
                .setTitle("Sprawdź odczyt OCR")
                .setMessage(sb.toString())
                .setNegativeButton("ANULUJ", null)
                .setPositiveButton("UŻYJ TYCH DANYCH", (d, which) -> applyOcrResult(r))
                .show();
    }

    private void addSummary(StringBuilder sb, String label, String value, OcrResult.Confidence c) {
        if (value == null || value.isEmpty()) { sb.append("○ ").append(label).append(": —\n"); return; }
        String icon = c == OcrResult.Confidence.HIGH ? "✓" : c == OcrResult.Confidence.MEDIUM ? "~" : "?";
        sb.append(icon).append(" ").append(label).append(": ").append(value).append("\n");
    }

    private void applyOcrResult(OcrResult r) {
        // v1.2.1: a confirmed scan starts a NEW product dataset.
        // Never mix missing OCR fields with values from the previous carton.
        clearProductSpecificFieldsForNewScan();

        productEdit.setText(r.productName);
        descriptionEdit.setText(r.description);

        String resolvedTu = r.articleTu;
        String resolvedCu = r.articleCu;
        if (resolvedTu.isEmpty() && resolvedCu.isEmpty() && !r.article.isEmpty()) {
            resolvedTu = r.article;
            resolvedCu = r.article;
        } else {
            if (resolvedTu.isEmpty() && !r.article.isEmpty()) resolvedTu = r.article;
            if (resolvedCu.isEmpty() && !r.article.isEmpty()) resolvedCu = r.article;
        }
        articleTuEdit.setText(resolvedTu);
        articleCuEdit.setText(resolvedCu);

        batchEdit.setText(r.batch);
        piecesEdit.setText(r.piecesPerCarton);
        gtinEdit.setText(r.packageGtin14);
        madeInEdit.setText(r.madeIn);
        poCodeEdit.setText(r.poCode);

        if (!r.sscc.isEmpty()) ssccEdit.setText(r.sscc);
        if (!r.palletCount.isEmpty()) cartonsEdit.setText(r.palletCount);

        grossWeightEdit.setText(r.grossWeight);
        materialEdit.setText(r.material);
        customerSkuEdit.setText(r.customerSku);
        logisticsArticleEdit.setText(r.logisticsArticle);

        // Apply OCR expiry only when it contains an unambiguous full date.
        // YYYY/MM is intentionally NOT guessed.
        String parsedExpiry = normalizeOcrExpiry(r.expiryRaw);
        if (!parsedExpiry.isEmpty()) expiryEdit.setText(parsedExpiry);

        String mainArticle = mainArticle();
        String autoPack = buildPackArticleLine(productEdit.getText().toString(), mainArticle);
        packArticleEdit.setText(autoPack);

        if (r.detectedType.equals("PALLET LOGISTICS")) {
            if (customerSkuEdit.getText().toString().trim().isEmpty()) customerSkuEdit.setText(mainArticle);
            if (materialEdit.getText().toString().trim().isEmpty()) materialEdit.setText(productEdit.getText().toString());
        }

        // Do not leave a preview/PDF from the previous product visible when
        // the new scan is incomplete and cannot yet generate a valid label.
        preview.setImageDrawable(null);
        pendingPdf = null;
        if (gs1Status != null) gs1Status.setText("");

        photoStatus.setText("Dane z nowego skanu zastosowane. Brakujące pola pozostawiono puste — nie użyto danych z poprzedniego kartonu.");
        saveForm();
        if (!mainArticle.isEmpty()) saveProductCache();
        updateTotal();
        generatePreview(false);
    }

    private void clearProductSpecificFieldsForNewScan() {
        productEdit.setText("");
        descriptionEdit.setText("");
        articleTuEdit.setText("");
        articleCuEdit.setText("");
        piecesEdit.setText("");
        batchEdit.setText("");
        gtinEdit.setText("");
        madeInEdit.setText("");
        poCodeEdit.setText("");
        packArticleEdit.setText("");

        // Hidden logistics fields are also product-specific.
        logisticsArticleEdit.setText("");
        materialEdit.setText("");
        customerSkuEdit.setText("");
        grossWeightEdit.setText("");
        dataMatrixEdit.setText("");
    }

    private String normalizeOcrExpiry(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace('-', '/');
        if (s.isEmpty()) return "";

        DateTimeFormatter out = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US);
        String[] patterns = new String[]{"dd/MM/yy", "dd/MM/yyyy", "yyyy/MM/dd"};
        for (String pattern : patterns) {
            try {
                LocalDate d = LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern, Locale.US));
                return d.format(out);
            } catch (DateTimeParseException ignored) {}
        }
        return "";
    }

    private String buildPackArticleLine(String product, String article) {
        Matcher m = Pattern.compile("(?i)\\b(\\d{1,4})\\s*(g|ml)\\b").matcher(product == null ? "" : product);
        if (!m.find() || article == null || article.isEmpty()) return "";
        return m.group(1) + m.group(2).toUpperCase(Locale.ROOT) + article;
    }

    private void showOcrText() {
        new AlertDialog.Builder(this).setTitle("Ostatni surowy OCR")
                .setMessage(lastOcrText.isEmpty() ? "Brak odczytu w tej sesji." : lastOcrText)
                .setPositiveButton("OK", null).show();
    }

    private void nextSscc() {
        try {
            String next = Gs1Utils.nextSscc(ssccEdit.getText().toString());
            ssccEdit.setText(next); prefs.edit().putString("sscc", next).apply();
            toast("Nowy SSCC: " + next); generatePreview(false);
        } catch (Exception e) { toast("Nieprawidłowy poprzedni SSCC: " + e.getMessage()); }
    }

    private String mainArticle() {
        String cu = clean(articleCuEdit);
        return cu.isEmpty() ? clean(articleTuEdit) : cu;
    }

    private LabelData readData() throws Exception {
        LabelData d = new LabelData();
        d.reference = clean(referenceEdit);
        d.topRightSmall = clean(topRightSmallEdit).toUpperCase(Locale.ROOT);
        d.topRightLarge = clean(routeEdit);
        d.productLine = clean(productEdit).toUpperCase(Locale.ROOT);
        d.description = clean(descriptionEdit);
        d.packArticleLine = clean(packArticleEdit).toUpperCase(Locale.ROOT);
        d.madeIn = clean(madeInEdit);
        d.articleTu = clean(articleTuEdit);
        d.articleCu = clean(articleCuEdit);
        d.article = mainArticle();
        d.contentGtin = Gs1Utils.digitsOnly(clean(gtinEdit));
        d.piecesPerCarton = parseInt(clean(piecesEdit));
        d.cartons = parseInt(clean(cartonsEdit));
        d.batch = clean(batchEdit).toUpperCase(Locale.ROOT);
        d.sscc = Gs1Utils.digitsOnly(clean(ssccEdit));
        d.expiryDisplay = clean(expiryEdit);
        d.poCode = clean(poCodeEdit);
        d.logisticsArticle = clean(logisticsArticleEdit);
        d.material = clean(materialEdit);
        d.customerSku = clean(customerSkuEdit);
        d.grossWeight = clean(grossWeightEdit);
        d.dataMatrixPayload = clean(dataMatrixEdit);
        d.shipperLine1 = clean(shipper1Edit); d.shipperLine2 = clean(shipper2Edit); d.shipperLine3 = clean(shipper3Edit);
        d.shipperLine4 = clean(shipper4Edit); d.shipperLine5 = clean(shipper5Edit);

        if (d.productLine.isEmpty()) throw new Exception("Wpisz nazwę produktu.");
        if (!Gs1Utils.isValidGtin14(d.contentGtin)) throw new Exception("CONTENT musi być prawidłowym GTIN-14.");
        if (d.cartons < 1) throw new Exception("COUNT musi być większy od 0.");
        if (!isLogistics() && d.piecesPerCarton < 1) throw new Exception("Sztuk/karton musi być większe od 0.");
        if (d.batch.isEmpty() || d.batch.length() > 20) throw new Exception("Batch musi mieć 1–20 znaków.");
        if (d.article.isEmpty() || d.article.length() > 30) throw new Exception("Article TU/CU jest wymagany.");
        if (!asciiOnly(d.batch) || !asciiOnly(d.article)) throw new Exception("Batch i Article muszą używać znaków ASCII.");
        if (!Gs1Utils.isValidSscc(d.sscc)) throw new Exception("SSCC musi mieć 18 cyfr i prawidłową cyfrę kontrolną.");
        try {
            LocalDate date = LocalDate.parse(d.expiryDisplay, DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US));
            d.expiryAi = date.format(DateTimeFormatter.ofPattern("yyMMdd", Locale.US));
        } catch (DateTimeParseException e) { throw new Exception("Expiry wpisz jako DD/MM/YY, np. 25/08/28."); }
        if (isLogistics()) {
            if (d.customerSku.isEmpty()) d.customerSku = d.article;
            if (d.material.isEmpty()) d.material = d.productLine;
            if (d.grossWeight.isEmpty()) throw new Exception("Dla Logistics wpisz brutto pallet weight.");
        }
        return d;
    }

    private boolean asciiOnly(String s) { for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 127) return false; return true; }
    private String clean(EditText e) { return e == null ? "" : e.getText().toString().trim(); }

    private void generatePreview(boolean notify) {
        try {
            updateTotal();
            LabelData d = readData();
            Bitmap bmp = isLogistics() ? LogisticsLabelRenderer.renderPreview(d) : LabelRenderer.renderPreview(d);
            preview.setImageBitmap(bmp);
            if (isLogistics()) {
                gs1Status.setText("LOGISTICS GS1-128:\n(02)" + d.contentGtin + "(17)" + d.expiryAi + "(37)" + d.cartons + "(10)" + d.batch
                        + "\n(00)" + d.sscc + "(3302)[waga]\n2D: " + (d.dataMatrixPayload.isEmpty() ? "nie ustawiono payloadu" : "payload ręczny"));
            } else {
                gs1Status.setText("GS1-128:\n" + d.barcode1Human() + "\n" + d.barcode2Human() + " [FNC1 po Batch]\n" + d.barcode3Human()
                        + "\n\nRazem: " + d.totalPieces() + " szt.");
            }
            saveForm(); saveProductCache();
            if (notify) toast("Etykieta wygenerowana.");
        } catch (Exception e) { if (gs1Status != null) gs1Status.setText("Błąd: " + e.getMessage()); if (notify) toast(e.getMessage()); }
    }

    private byte[] renderPdf(LabelData d) throws Exception {
        return isLogistics() ? LogisticsLabelRenderer.renderPdf(d) : LabelRenderer.renderPdf(d);
    }

    private void savePdf() {
        try {
            LabelData d = readData(); pendingPdf = renderPdf(d); saveForm(); saveProductCache();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/pdf"); i.putExtra(Intent.EXTRA_TITLE, fileName(d));
            startActivityForResult(i, REQ_SAVE_PDF);
        } catch (Exception e) { toast("Nie można utworzyć PDF: " + e.getMessage()); }
    }

    private void sharePdf() {
        try {
            LabelData d = readData(); byte[] pdf = renderPdf(d);
            File f = new File(getCacheDir(), fileName(d));
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(pdf); }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf"); share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, (isLogistics() ? "Logistics pallet label - " : "Pallet label - ") + d.article + " - Batch " + d.batch);
            share.putExtra(Intent.EXTRA_TEXT, "Pallet label attached.\nProduct: " + d.productLine + "\nArticle: " + d.article + "\nBatch: " + d.batch + "\nSSCC: " + d.sscc);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            saveForm(); saveProductCache();
            startActivity(Intent.createChooser(share, "Udostępnij etykietę PDF"));
        } catch (Exception e) { toast("Nie można udostępnić PDF: " + e.getMessage()); }
    }

    private String fileName(LabelData d) {
        return (isLogistics() ? "Logistics_" : "Pallet_") + safe(d.article) + "_Batch_" + safe(d.batch) + "_SSCC_" + d.sscc + ".pdf";
    }
    private String safe(String s) { return s.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private void saveProductCache() {
        String article = mainArticle();
        if (article.isEmpty()) return;
        String prefix = "cache_v2_" + article + "_";
        prefs.edit().putString(prefix + "product", clean(productEdit)).putString(prefix + "description", clean(descriptionEdit))
                .putString(prefix + "gtin", clean(gtinEdit)).putString(prefix + "pieces", clean(piecesEdit)).putString(prefix + "madeIn", clean(madeInEdit)).apply();
    }

    private void saveForm() {
        if (productEdit == null) return;
        prefs.edit()
                .putString("product", clean(productEdit)).putString("description", clean(descriptionEdit))
                .putString("articleTu", clean(articleTuEdit)).putString("articleCu", clean(articleCuEdit))
                .putString("pieces", clean(piecesEdit)).putString("batch", clean(batchEdit)).putString("gtin", clean(gtinEdit)).putString("madeIn", clean(madeInEdit))
                .putString("cartons", clean(cartonsEdit)).putString("expiry", clean(expiryEdit)).putString("sscc", clean(ssccEdit))
                .putString("poCode", clean(poCodeEdit)).putString("pack", clean(packArticleEdit)).putString("reference", clean(referenceEdit))
                .putString("topRightSmall", clean(topRightSmallEdit)).putString("route", clean(routeEdit))
                .putString("logArticle", clean(logisticsArticleEdit)).putString("material", clean(materialEdit)).putString("customerSku", clean(customerSkuEdit))
                .putString("grossWeight", clean(grossWeightEdit)).putString("dataMatrix", clean(dataMatrixEdit))
                .putString("ship1", clean(shipper1Edit)).putString("ship2", clean(shipper2Edit)).putString("ship3", clean(shipper3Edit))
                .putString("ship4", clean(shipper4Edit)).putString("ship5", clean(shipper5Edit))
                .putInt("outputTemplate", outputTemplateSpinner == null ? 0 : outputTemplateSpinner.getSelectedItemPosition())
                .apply();
    }

    private void migrateTo121() {
        if (prefs.getInt("dataModelVersion", 0) >= 121) return;

        // v1.2 could save a mixed product when OCR missed Article/name.
        // Clear only product-specific current-form data once.
        // Pallet settings (COUNT, expiry, SSCC) and template constants stay.
        prefs.edit()
                .putString("product", "")
                .putString("description", "")
                .putString("articleTu", "")
                .putString("articleCu", "")
                .putString("pieces", "")
                .putString("batch", "")
                .putString("gtin", "")
                .putString("madeIn", "")
                .putString("poCode", "")
                .putString("pack", "")
                .putString("logArticle", "")
                .putString("material", "")
                .putString("customerSku", "")
                .putString("grossWeight", "")
                .putString("dataMatrix", "")
                .putInt("dataModelVersion", 121)
                .apply();
    }

    private void loadForm() {
        productEdit.setText(prefs.getString("product", ""));
        descriptionEdit.setText(prefs.getString("description", ""));
        articleTuEdit.setText(prefs.getString("articleTu", ""));
        articleCuEdit.setText(prefs.getString("articleCu", ""));
        piecesEdit.setText(prefs.getString("pieces", ""));
        batchEdit.setText(prefs.getString("batch", ""));
        gtinEdit.setText(prefs.getString("gtin", ""));
        madeInEdit.setText(prefs.getString("madeIn", ""));
        cartonsEdit.setText(prefs.getString("cartons", "16"));
        expiryEdit.setText(prefs.getString("expiry", defaultExpiry()));
        ssccEdit.setText(prefs.getString("sscc", DEFAULT_OLD_SSCC));
        poCodeEdit.setText(prefs.getString("poCode", ""));
        packArticleEdit.setText(prefs.getString("pack", ""));
        referenceEdit.setText(prefs.getString("reference", "1501333"));
        topRightSmallEdit.setText(prefs.getString("topRightSmall", "NLVL"));
        routeEdit.setText(prefs.getString("route", "91/NR"));
        logisticsArticleEdit.setText(prefs.getString("logArticle", ""));
        materialEdit.setText(prefs.getString("material", ""));
        customerSkuEdit.setText(prefs.getString("customerSku", ""));
        grossWeightEdit.setText(prefs.getString("grossWeight", ""));
        dataMatrixEdit.setText(prefs.getString("dataMatrix", ""));
        shipper1Edit.setText(prefs.getString("ship1", "Firma"));
        shipper2Edit.setText(prefs.getString("ship2", "Mann & Schröder GmbH"));
        shipper3Edit.setText(prefs.getString("ship3", "Bahnhofstraße 14"));
        shipper4Edit.setText(prefs.getString("ship4", "74936 Siegelsbach"));
        shipper5Edit.setText(prefs.getString("ship5", "Deutschland"));
        outputTemplateSpinner.setSelection(prefs.getInt("outputTemplate", 0));
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override protected void onPause() { super.onPause(); saveForm(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        try { textRecognizer.close(); } catch (Exception ignored) {}
        try { barcodeScanner.close(); } catch (Exception ignored) {}
    }
}
