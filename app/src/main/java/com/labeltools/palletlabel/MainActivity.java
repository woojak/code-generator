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
    private EditText gtinEdit, cartonsEdit, piecesEdit, expiryEdit, batchEdit, articleEdit, ssccEdit, madeInEdit;
    private TextView totalText, photoStatus, gs1Status;
    private ImageView preview;
    private LinearLayout advancedContainer, gs1Container;
    private Button advancedToggle, gs1Toggle;

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
        version.setText("v1.1 • offline • GS1-128 • PDF 105 × 148 mm");
        version.setTextSize(13);
        version.setTextColor(Color.rgb(90, 90, 90));
        version.setPadding(0, dp(2), 0, dp(8));
        root.addView(version);

        TextView flow = new TextView(this);
        flow.setText("1. Zrób zdjęcie  →  2. Sprawdź dane  →  3. Ustaw paletę  →  4. Udostępnij PDF");
        flow.setTextSize(14);
        flow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        flow.setTextColor(Color.rgb(35, 35, 35));
        flow.setPadding(dp(12), dp(10), dp(12), dp(10));
        flow.setBackground(rounded(Color.WHITE, 12, Color.rgb(220, 222, 226)));
        root.addView(flow, marginTop(matchWrap(), dp(4)));

        LinearLayout scanCard = card(
                "1. Skan etykiety kartonu",
                "Najpierw zrób zdjęcie. OCR spróbuje odczytać nazwę, Article, Batch, ilość, GTIN i kraj produkcji.");

        LinearLayout photoRow = horizontal();
        Button camera = primaryButton("Zrób zdjęcie");
        camera.setOnClickListener(v -> takePhoto());
        Button gallery = secondaryButton("Wybierz zdjęcie");
        gallery.setOnClickListener(v -> choosePhoto());
        photoRow.addView(camera, weight());
        photoRow.addView(gallery, weightMargin());
        scanCard.addView(photoRow);

        photoStatus = new TextView(this);
        photoStatus.setText("Brak nowego skanu. Możesz też wpisać dane ręcznie.");
        photoStatus.setTextSize(13);
        photoStatus.setTextColor(Color.rgb(55, 55, 55));
        photoStatus.setPadding(dp(10), dp(9), dp(10), dp(9));
        photoStatus.setBackground(rounded(
                Color.rgb(246, 247, 249), 10, Color.rgb(226, 228, 232)));
        scanCard.addView(photoStatus, marginTop(matchWrap(), gap));

        Button showOcr = secondaryButton("Pokaż surowy odczyt OCR");
        showOcr.setOnClickListener(v -> showOcrText());
        scanCard.addView(showOcr, marginTop(matchWrap(), dp(8)));
        root.addView(scanCard, marginTop(matchWrap(), gap));

        LinearLayout productCard = card(
                "2. Sprawdź dane produktu",
                "Pola znalezione na zdjęciu są uzupełniane automatycznie. Przed wygenerowaniem etykiety możesz je poprawić.");

        productEdit = field(productCard, "Nazwa produktu", "RC SCRUB YOZAKURA", false);
        productEdit.setSingleLine(false);
        productEdit.setMaxLines(2);
        productEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        LinearLayout articleQtyRow = horizontal();

        LinearLayout articleCol = verticalWeight();
        articleCol.addView(smallLabel("Article TU / CU"));
        articleEdit = edit("1120211", true);
        articleCol.addView(articleEdit, matchWrap());

        LinearLayout pcsCol = verticalWeight();
        pcsCol.addView(smallLabel("Sztuk / karton"));
        piecesEdit = edit("60", true);
        pcsCol.addView(piecesEdit, matchWrap());

        articleQtyRow.addView(articleCol, weight());
        articleQtyRow.addView(pcsCol, weightMargin());
        productCard.addView(articleQtyRow);

        batchEdit = field(productCard, "Batch / Lot", "12342064", false);
        gtinEdit = field(productCard, "GTIN-14 / CONTENT", "08720296062361", true);
        madeInEdit = field(productCard, "Made in (informacyjnie)", "", false);

        advancedToggle = secondaryButton("Pokaż pola zaawansowane");
        productCard.addView(advancedToggle, marginTop(matchWrap(), dp(8)));

        advancedContainer = new LinearLayout(this);
        advancedContainer.setOrientation(LinearLayout.VERTICAL);
        advancedContainer.setVisibility(View.GONE);
        advancedContainer.setPadding(0, dp(8), 0, 0);

        packArticleEdit = field(
                advancedContainer, "Linia opakowanie / artykuł", "125G1120211", false);
        referenceEdit = field(
                advancedContainer, "Numer / REF (góra lewa)", "1501333", false);
        topRightSmallEdit = field(
                advancedContainer, "Pole prawe małe", "NLVL", false);
        routeEdit = field(
                advancedContainer, "Pole prawe duże", "91/NR", false);
        productCard.addView(advancedContainer);

        advancedToggle.setOnClickListener(v -> {
            boolean open = advancedContainer.getVisibility() == View.VISIBLE;
            advancedContainer.setVisibility(open ? View.GONE : View.VISIBLE);
            advancedToggle.setText(
                    open ? "Pokaż pola zaawansowane" : "Ukryj pola zaawansowane");
        });

        root.addView(productCard, marginTop(matchWrap(), gap));

        LinearLayout palletCard = card(
                "3. Dane palety",
                "Ustaw liczbę kartonów. SSCC zmieniaj tylko dla nowej palety; przy reprintcie zostaw ten sam numer.");

        cartonsEdit = field(
                palletCard, "Liczba kartonów na palecie", "16", true);

        totalText = new TextView(this);
        totalText.setTextSize(18);
        totalText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        totalText.setTextColor(Color.rgb(30, 30, 30));
        totalText.setPadding(dp(10), dp(8), dp(10), dp(8));
        totalText.setBackground(rounded(
                Color.rgb(246, 247, 249), 10, Color.rgb(226, 228, 232)));
        palletCard.addView(totalText, marginTop(matchWrap(), dp(4)));

        expiryEdit = field(
                palletCard, "Expiry (DD/MM/YY)", defaultExpiry(), false);

        Button plusTwo = secondaryButton("Ustaw dziś + 2 lata");
        plusTwo.setOnClickListener(v -> expiryEdit.setText(defaultExpiry()));
        palletCard.addView(plusTwo);

        ssccEdit = field(
                palletCard, "SSCC (18 cyfr)", DEFAULT_OLD_SSCC, true);

        Button nextSscc = primaryButton("Nowy SSCC");
        nextSscc.setOnClickListener(v -> nextSscc());
        palletCard.addView(nextSscc);

        TextView ssccInfo = new TextView(this);
        ssccInfo.setText(
                "Nowy SSCC = +1 do numeru seryjnego i nowa cyfra kontrolna GS1. "
                        + "Numer jest zapisywany tylko na tym telefonie.");
        ssccInfo.setTextSize(12);
        ssccInfo.setTextColor(Color.rgb(95, 95, 95));
        ssccInfo.setPadding(0, dp(8), 0, 0);
        palletCard.addView(ssccInfo);

        attachTotalWatcher(cartonsEdit);
        attachTotalWatcher(piecesEdit);
        root.addView(palletCard, marginTop(matchWrap(), gap));

        LinearLayout outputCard = card(
                "4. Podgląd i wysyłka",
                "Najwygodniej: wygeneruj etykietę i użyj „Udostępnij PDF”, "
                        + "a następnie wybierz firmową pocztę.");

        Button generate = primaryButton("Generuj / odśwież podgląd");
        generate.setOnClickListener(v -> generatePreview(true));
        outputCard.addView(generate);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setPadding(0, dp(8), 0, dp(8));
        outputCard.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

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

        gs1Status = new TextView(this);
        gs1Status.setTextSize(12);
        gs1Status.setTypeface(Typeface.MONOSPACE);
        gs1Status.setTextIsSelectable(true);
        gs1Status.setTextColor(Color.rgb(45, 45, 45));
        gs1Status.setPadding(dp(8), dp(8), dp(8), dp(8));
        gs1Status.setBackground(rounded(
                Color.rgb(246, 247, 249), 8, Color.rgb(226, 228, 232)));
        gs1Container.addView(gs1Status);
        outputCard.addView(gs1Container);

        gs1Toggle.setOnClickListener(v -> {
            boolean open = gs1Container.getVisibility() == View.VISIBLE;
            gs1Container.setVisibility(open ? View.GONE : View.VISIBLE);
            gs1Toggle.setText(
                    open ? "Pokaż szczegóły GS1" : "Ukryj szczegóły GS1");
        });

        Button saveValues = secondaryButton(
                "Zapisz obecne dane jako domyślne");
        saveValues.setOnClickListener(v -> {
            saveForm();
            toast("Dane zapisane lokalnie.");
        });
        outputCard.addView(saveValues, marginTop(matchWrap(), dp(8)));

        root.addView(outputCard, marginTop(matchWrap(), gap));
        return scroll;
    }

    private LinearLayout card(String titleText, String subtitleText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(
                Color.WHITE, 14, Color.rgb(220, 222, 226)));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(19);
        title.setTextColor(Color.rgb(25, 25, 25));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);

        if (subtitleText != null && !subtitleText.isEmpty()) {
            TextView subtitle = new TextView(this);
            subtitle.setText(subtitleText);
            subtitle.setTextSize(13);
            subtitle.setTextColor(Color.rgb(92, 92, 92));
            subtitle.setPadding(0, dp(3), 0, dp(10));
            card.addView(subtitle);
        }
        return card;
    }

    private GradientDrawable rounded(
            int fillColor, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView smallLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Color.rgb(55, 55, 55));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private EditText field(
            LinearLayout parent, String label, String def, boolean numeric) {
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
        e.setTextColor(Color.rgb(25, 25, 25));
        e.setHintTextColor(Color.rgb(145, 145, 145));
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setBackground(rounded(
                Color.rgb(250, 250, 251), 8, Color.rgb(215, 217, 221)));
        e.setInputType(
                numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
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
        b.setBackground(rounded(
                Color.rgb(35, 35, 35), 10, Color.rgb(35, 35, 35)));
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = button(text);
        b.setTextColor(Color.rgb(35, 35, 35));
        b.setBackground(rounded(
                Color.rgb(238, 239, 241), 10, Color.rgb(215, 217, 221)));
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
        return new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightMargin() {
        LinearLayout.LayoutParams p = weight();
        p.leftMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams marginTop(
            LinearLayout.LayoutParams p, int top) {
        p.topMargin = top;
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String defaultExpiry() {
        return LocalDate.now().plusYears(2)
                .format(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US));
    }

    private void attachTotalWatcher(EditText e) {
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                updateTotal();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateTotal() {
        int c = parseInt(
                cartonsEdit == null ? "0" : cartonsEdit.getText().toString());
        int p = parseInt(
                piecesEdit == null ? "0" : piecesEdit.getText().toString());

        if (totalText != null) {
            totalText.setText(
                    c + " kart. × " + p + " szt. = "
                            + (c * p) + " szt. łącznie");
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "images");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(
                    dir, "capture_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);

            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            i.addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQ_CAMERA && pendingCameraUri != null) {
            analyzeImage(pendingCameraUri);
        } else if (requestCode == REQ_GALLERY
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            analyzeImage(uri);
        } else if (requestCode == REQ_SAVE_PDF
                && data != null
                && data.getData() != null
                && pendingPdf != null) {
            try (OutputStream out =
                         getContentResolver().openOutputStream(data.getData())) {
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
                        found.add(
                                "OCR error: " + e.getClass().getSimpleName());
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
                        found.add(
                                "Barcode error: "
                                        + e.getClass().getSimpleName());
                        pending[0]--;
                        if (pending[0] == 0) finishImageAnalysis(found);
                    });
        } catch (Exception e) {
            photoStatus.setText(
                    "Błąd analizy zdjęcia: " + e.getMessage());
        }
    }

    private void applyOcrText(String text, List<String> found) {
        String article = firstGroup(
                text,
                "(?i)art\\s*\\.?\\s*nr\\s*\\.?\\s*tu\\s*[:\\-]?\\s*(\\d{5,12})");

        if (article.isEmpty()) {
            article = firstGroup(
                    text,
                    "(?i)art\\s*\\.?\\s*nr\\s*\\.?\\s*cu\\s*[:\\-]?\\s*(\\d{5,12})");
        }

        // Some labels reverse the order: "TU Art.Nr." / "CU Art.Nr.".
        if (article.isEmpty()) {
            article = firstGroup(
                    text,
                    "(?i)\\btu\\s+art\\s*\\.?\\s*nr\\s*\\.?\\s*[:\\-]?\\s*(\\d{5,12})");
        }

        if (article.isEmpty()) {
            article = firstGroup(
                    text,
                    "(?i)\\bcu\\s+art\\s*\\.?\\s*nr\\s*\\.?\\s*[:\\-]?\\s*(\\d{5,12})");
        }

        if (article.isEmpty()) {
            article = firstGroup(
                    text,
                    "(?i)internal\\s+article\\s+nr\\s*[:\\-]?\\s*(\\d{5,12})");
        }

        if (!article.isEmpty()) {
            articleEdit.setText(article);
            found.add("Article " + article);
        }

        String batch = firstGroup(
                text,
                "(?i)\\bbatch\\s*(?:code|/\\s*lot)?\\s*[:\\-]?\\s*([A-Z0-9-]{4,20})");

        if (batch.isEmpty()) {
            batch = firstGroup(
                    text,
                    "(?i)\\blot\\s*[:\\-]?\\s*([A-Z0-9-]{4,20})");
        }

        if (!batch.isEmpty()) {
            batchEdit.setText(batch);
            found.add("Batch " + batch);
        }

        Matcher qtyGtin = Pattern.compile(
                "(?i)\\b(\\d{1,4})\\s*[x×]\\s*(\\d{13,14})\\b")
                .matcher(text);

        if (qtyGtin.find()) {
            String pieces = qtyGtin.group(1);
            piecesEdit.setText(pieces);
            found.add(pieces + " szt./karton");

            String normalized = normalizeGtin(qtyGtin.group(2));
            if (!normalized.isEmpty()) {
                gtinEdit.setText(normalized);
                found.add("GTIN " + normalized + " (tekst)");
            }
        }

        if (!containsField(found, "GTIN ")) {
            String fromText = findGtinInText(text);
            if (!fromText.isEmpty()) {
                gtinEdit.setText(fromText);
                found.add("GTIN " + fromText + " (OCR)");
            }
        }

        String product = extractProductName(text);
        if (!product.isEmpty()) {
            // v1.0 only changed the name if the field was empty.
            // Saved/default data therefore blocked a correct OCR result.
            // v1.1 replaces it whenever a confident product line is found.
            productEdit.setText(product);
            found.add("nazwa produktu");

            if (!article.isEmpty()) {
                String autoPack =
                        buildPackArticleLine(product, article);
                if (!autoPack.isEmpty()) {
                    packArticleEdit.setText(autoPack);
                }
            }
        }

        String madeIn = extractMadeIn(text);
        if (!madeIn.isEmpty()) {
            madeInEdit.setText(madeIn);
            found.add("Made in " + madeIn);
        }

        updateTotal();
    }

    private boolean containsField(
            List<String> found, String prefix) {
        for (String s : found) {
            if (s.startsWith(prefix)) return true;
        }
        return false;
    }

    private String findGtinInText(String text) {
        Matcher m = Pattern.compile(
                "(?<!\\d)(\\d{13,14})(?!\\d)")
                .matcher(text);

        while (m.find()) {
            String normalized = normalizeGtin(m.group(1));
            if (!normalized.isEmpty()) return normalized;
        }

        // Some carton labels print digits with spaces:
        // e.g. "8 720296 066512".
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            String lower = line.toLowerCase(Locale.ROOT);

            if (lower.contains("art")
                    || lower.contains("batch")
                    || lower.contains("lan")
                    || lower.contains("ref")) {
                continue;
            }

            String digits = Gs1Utils.digitsOnly(line);
            if (digits.length() == 13 || digits.length() == 14) {
                String normalized = normalizeGtin(digits);
                if (!normalized.isEmpty()) return normalized;
            }
        }

        return "";
    }

    private String normalizeGtin(String value) {
        String digits = Gs1Utils.digitsOnly(value);
        if (digits.length() == 13) digits = "0" + digits;

        if (digits.length() == 14
                && Gs1Utils.isValidGtin14(digits)) {
            return digits;
        }
        return "";
    }

    private String extractProductName(String text) {
        String[] rawLines = text.split("\\R");
        List<String> lines = new ArrayList<>();

        for (String raw : rawLines) {
            String line = cleanupLine(raw);
            if (!line.isEmpty()) lines.add(line);
        }

        // Strongest signal on the warehouse cartons:
        // "The Ritual of ...".
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String lower = line.toLowerCase(Locale.ROOT);

            if (lower.contains("the ritual of ")) {
                String candidate = removeTrailingNoise(line);

                // Some labels split the actual product:
                // "The Ritual of Ayurveda" / "Hair and Body mist".
                if (i + 1 < lines.size()) {
                    String next =
                            removeTrailingNoise(lines.get(i + 1));

                    boolean likelyContinuation =
                            looksLikeCollectionOnly(candidate)
                                    || (candidate.length() < 65
                                    && startsWithUppercaseLetter(next));

                    if (likelyContinuation
                            && isDescriptiveProductLine(next)) {
                        candidate = candidate + " " + next;
                    }
                }
                return candidate;
            }
        }

        // Fallback: first descriptive line under the RITUALS logo.
        for (int i = 0; i < lines.size(); i++) {
            String lower =
                    lines.get(i).toLowerCase(Locale.ROOT);

            if (lower.contains("rituals")) {
                for (int j = i + 1;
                     j < Math.min(lines.size(), i + 4);
                     j++) {
                    String candidate =
                            removeTrailingNoise(lines.get(j));

                    if (isDescriptiveProductLine(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return "";
    }

    private String cleanupLine(String value) {
        if (value == null) return "";

        return value
                .replace('\u00a0', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String removeTrailingNoise(String value) {
        return cleanupLine(value)
                .replaceAll("(?i)\\s+GS$", "")
                .replaceAll("\\s+[|]+$", "")
                .trim();
    }

    private boolean looksLikeCollectionOnly(String value) {
        String normalized = value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (!normalized.startsWith("the ritual of ")) {
            return false;
        }

        String[] words = normalized.split(" ");
        return words.length <= 5;
    }

    private boolean startsWithUppercaseLetter(String value) {
        String line = cleanupLine(value);

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (Character.isLetter(c)) {
                return Character.isUpperCase(c);
            }
        }

        return false;
    }

    private boolean isDescriptiveProductLine(String value) {
        if (value == null) return false;

        String line = cleanupLine(value);
        if (line.length() < 4 || line.length() > 90) {
            return false;
        }

        String lower = line.toLowerCase(Locale.ROOT);

        if (lower.startsWith("rituals")
                || lower.startsWith("art")
                || lower.startsWith("batch")
                || lower.startsWith("made in")
                || lower.startsWith("lan")
                || lower.startsWith("ref")
                || lower.startsWith("expiry")
                || lower.startsWith("net:")
                || lower.contains("herengracht")
                || lower.contains("amsterdam")) {
            return false;
        }

        if (lower.matches("^\\d+\\s*[x×].*")) {
            return false;
        }

        int letters = 0;
        for (int i = 0; i < line.length(); i++) {
            if (Character.isLetter(line.charAt(i))) {
                letters++;
            }
        }
        return letters >= 4;
    }

    private String extractMadeIn(String text) {
        for (String raw : text.split("\\R")) {
            String line = cleanupLine(raw);
            Matcher m = Pattern.compile(
                    "(?i)\\bmade\\s*in\\s*:?\\s*(.+)$")
                    .matcher(line);

            if (m.find()) {
                String country = cleanupLine(m.group(1))
                        .replaceAll("[^A-Za-zÀ-ž .'-]", "")
                        .trim();

                if (country.length() >= 2
                        && country.length() <= 35) {
                    return country;
                }
            }
        }
        return "";
    }

    private String buildPackArticleLine(
            String product, String article) {
        Matcher size = Pattern.compile(
                "(?i)\\b(\\d{1,4})\\s*(g|ml)\\b")
                .matcher(product);

        if (!size.find()) return "";

        return size.group(1)
                + size.group(2).toUpperCase(Locale.ROOT)
                + article;
    }

    private void applyBarcodes(
            List<Barcode> codes, List<String> found) {
        String fallback = "";

        for (Barcode b : codes) {
            String raw = b.getRawValue();
            if (raw == null) continue;

            String normalized = normalizeGtin(raw);
            if (normalized.isEmpty()) continue;

            String digits = Gs1Utils.digitsOnly(raw);

            if (digits.length() == 14) {
                gtinEdit.setText(normalized);
                found.add(
                        "GTIN " + normalized + " (barcode)");
                return;
            }

            if (fallback.isEmpty()) {
                fallback = normalized;
            }
        }

        if (!fallback.isEmpty()) {
            gtinEdit.setText(fallback);
            found.add(
                    "GTIN " + fallback
                            + " (EAN-13 → GTIN-14)");
        } else if (!codes.isEmpty()) {
            found.add(
                    "barcode znaleziony, ale bez pewnego GTIN");
        }
    }

    private String firstGroup(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private void finishImageAnalysis(List<String> found) {
        if (found.isEmpty()) {
            photoStatus.setText(
                    "Zdjęcie odczytane, ale nie znaleziono pewnych pól. "
                            + "Sprawdź dane ręcznie.");
        } else {
            photoStatus.setText(
                    "Odczytano: " + String.join(" • ", found));
        }

        saveForm();
        generatePreview(false);
    }

    private void showOcrText() {
        new AlertDialog.Builder(this)
                .setTitle("Ostatni odczyt OCR")
                .setMessage(
                        lastOcrText.isEmpty()
                                ? "Brak odczytu w tej sesji."
                                : lastOcrText)
                .setPositiveButton("OK", null)
                .show();
    }

    private void nextSscc() {
        try {
            String next =
                    Gs1Utils.nextSscc(
                            ssccEdit.getText().toString());

            ssccEdit.setText(next);
            prefs.edit()
                    .putString("sscc", next)
                    .apply();

            toast("Nowy SSCC: " + next);
            generatePreview(false);
        } catch (Exception e) {
            toast(
                    "Nieprawidłowy poprzedni SSCC: "
                            + e.getMessage());
        }
    }

    private LabelData readData() throws Exception {
        LabelData d = new LabelData();

        d.reference = clean(referenceEdit);
        d.topRightSmall =
                clean(topRightSmallEdit)
                        .toUpperCase(Locale.ROOT);
        d.topRightLarge = clean(routeEdit);
        d.productLine =
                clean(productEdit)
                        .toUpperCase(Locale.ROOT);
        d.packArticleLine =
                clean(packArticleEdit)
                        .toUpperCase(Locale.ROOT);
        d.madeIn = clean(madeInEdit);

        d.contentGtin =
                Gs1Utils.digitsOnly(clean(gtinEdit));
        d.cartons = parseInt(clean(cartonsEdit));
        d.piecesPerCarton = parseInt(clean(piecesEdit));
        d.batch =
                clean(batchEdit)
                        .toUpperCase(Locale.ROOT);
        d.article =
                clean(articleEdit)
                        .toUpperCase(Locale.ROOT);
        d.sscc =
                Gs1Utils.digitsOnly(clean(ssccEdit));
        d.expiryDisplay = clean(expiryEdit);

        if (d.productLine.isEmpty()) {
            throw new Exception("Wpisz nazwę produktu.");
        }

        if (!Gs1Utils.isValidGtin14(d.contentGtin)) {
            throw new Exception(
                    "GTIN musi mieć prawidłowe 14 cyfr (GTIN-14).");
        }

        if (d.cartons < 1 || d.cartons > 99999999) {
            throw new Exception(
                    "Liczba kartonów musi być większa od 0.");
        }

        if (d.piecesPerCarton < 1) {
            throw new Exception(
                    "Sztuk/karton musi być większe od 0.");
        }

        if (d.batch.isEmpty() || d.batch.length() > 20) {
            throw new Exception(
                    "Batch AI (10) musi mieć 1–20 znaków.");
        }

        if (d.article.isEmpty() || d.article.length() > 30) {
            throw new Exception(
                    "Article AI (240) musi mieć 1–30 znaków.");
        }

        if (!asciiOnly(d.batch)
                || !asciiOnly(d.article)) {
            throw new Exception(
                    "Batch i Article muszą używać znaków ASCII "
                            + "obsługiwanych przez Code 128.");
        }

        if (!Gs1Utils.isValidSscc(d.sscc)) {
            throw new Exception(
                    "SSCC musi mieć 18 cyfr i prawidłową "
                            + "cyfrę kontrolną.");
        }

        try {
            LocalDate date = LocalDate.parse(
                    d.expiryDisplay,
                    DateTimeFormatter.ofPattern(
                            "dd/MM/yy", Locale.US));

            d.expiryAi = date.format(
                    DateTimeFormatter.ofPattern(
                            "yyMMdd", Locale.US));
        } catch (DateTimeParseException e) {
            throw new Exception(
                    "Expiry wpisz jako DD/MM/YY, np. 25/08/28.");
        }

        return d;
    }

    private boolean asciiOnly(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) return false;
        }
        return true;
    }

    private String clean(EditText e) {
        return e.getText().toString().trim();
    }

    private void generatePreview(boolean notify) {
        try {
            updateTotal();
            LabelData d = readData();

            Bitmap bmp =
                    LabelRenderer.renderPreview(d);
            preview.setImageBitmap(bmp);

            gs1Status.setText(
                    "GS1-128:\n"
                            + d.barcode1Human()
                            + "\n"
                            + d.barcode2Human()
                            + "   [FNC1 po Batch]"
                            + "\n"
                            + d.barcode3Human()
                            + "\n\nRazem: "
                            + d.totalPieces()
                            + " szt.");

            saveForm();

            if (notify) {
                toast("Etykieta wygenerowana.");
            }
        } catch (Exception e) {
            if (gs1Status != null) {
                gs1Status.setText(
                        "Błąd: " + e.getMessage());
            }

            if (notify) {
                toast(e.getMessage());
            }
        }
    }

    private void savePdf() {
        try {
            LabelData d = readData();
            pendingPdf =
                    LabelRenderer.renderPdf(d);
            saveForm();

            Intent i =
                    new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            i.putExtra(
                    Intent.EXTRA_TITLE, fileName(d));
            startActivityForResult(i, REQ_SAVE_PDF);
        } catch (Exception e) {
            toast(
                    "Nie można utworzyć PDF: "
                            + e.getMessage());
        }
    }

    private void sharePdf() {
        try {
            LabelData d = readData();
            byte[] pdf =
                    LabelRenderer.renderPdf(d);

            File f =
                    new File(
                            getCacheDir(), fileName(d));

            try (FileOutputStream out =
                         new FileOutputStream(f)) {
                out.write(pdf);
            }

            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    f);

            Intent share =
                    new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(
                    Intent.EXTRA_STREAM, uri);
            share.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Pallet label - "
                            + d.article
                            + " - Batch "
                            + d.batch);
            share.putExtra(
                    Intent.EXTRA_TEXT,
                    "Pallet label attached.\n"
                            + "Product: "
                            + d.productLine
                            + "\nArticle: "
                            + d.article
                            + "\nBatch: "
                            + d.batch
                            + "\nSSCC: "
                            + d.sscc);
            share.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(
                    Intent.createChooser(
                            share,
                            "Udostępnij etykietę PDF"));
        } catch (Exception e) {
            toast(
                    "Nie można udostępnić PDF: "
                            + e.getMessage());
        }
    }

    private String fileName(LabelData d) {
        return "Pallet_"
                + safe(d.article)
                + "_Batch_"
                + safe(d.batch)
                + "_SSCC_"
                + d.sscc
                + ".pdf";
    }

    private String safe(String s) {
        return s.replaceAll(
                "[^A-Za-z0-9._-]", "_");
    }

    private void saveForm() {
        if (referenceEdit == null) return;

        prefs.edit()
                .putString(
                        "reference", clean(referenceEdit))
                .putString(
                        "topRightSmall",
                        clean(topRightSmallEdit))
                .putString(
                        "route", clean(routeEdit))
                .putString(
                        "product", clean(productEdit))
                .putString(
                        "pack", clean(packArticleEdit))
                .putString(
                        "madeIn", clean(madeInEdit))
                .putString(
                        "gtin", clean(gtinEdit))
                .putString(
                        "cartons", clean(cartonsEdit))
                .putString(
                        "pieces", clean(piecesEdit))
                .putString(
                        "expiry", clean(expiryEdit))
                .putString(
                        "batch", clean(batchEdit))
                .putString(
                        "article", clean(articleEdit))
                .putString(
                        "sscc", clean(ssccEdit))
                .apply();
    }

    private void loadForm() {
        referenceEdit.setText(
                prefs.getString(
                        "reference", "1501333"));
        topRightSmallEdit.setText(
                prefs.getString(
                        "topRightSmall", "NLVL"));
        routeEdit.setText(
                prefs.getString(
                        "route", "91/NR"));
        productEdit.setText(
                prefs.getString(
                        "product", "RC SCRUB YOZAKURA"));
        packArticleEdit.setText(
                prefs.getString(
                        "pack", "125G1120211"));
        madeInEdit.setText(
                prefs.getString(
                        "madeIn", ""));
        gtinEdit.setText(
                prefs.getString(
                        "gtin", "08720296062361"));
        cartonsEdit.setText(
                prefs.getString(
                        "cartons", "16"));
        piecesEdit.setText(
                prefs.getString(
                        "pieces", "60"));
        expiryEdit.setText(
                prefs.getString(
                        "expiry", defaultExpiry()));
        batchEdit.setText(
                prefs.getString(
                        "batch", "12342064"));
        articleEdit.setText(
                prefs.getString(
                        "article", "1120211"));
        ssccEdit.setText(
                prefs.getString(
                        "sscc", DEFAULT_OLD_SSCC));
    }

    private void toast(String s) {
        Toast.makeText(
                this, s, Toast.LENGTH_LONG)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveForm();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            textRecognizer.close();
        } catch (Exception ignored) {}

        try {
            barcodeScanner.close();
        } catch (Exception ignored) {}
    }
}
