# Pallet Label Generator v1.1

Offline Android application for preparing GS1 pallet labels.

## v1.1

This release is focused on the real carton-label variants used in the warehouse.

### OCR improvements

- Product name now overwrites old/default data when a confident `The Ritual of ...` line is found.
- Recognizes names split over two lines, for example:
  - `The Ritual of Ayurveda`
  - `Hair and Body mist`
- Better handling of `Art.Nr.TU`, `Art Nr. TU`, `Art.Nr.CU` and spacing variants.
- Better `Batch code` / `Batch Code` / `Lot` recognition.
- Reads quantities written as `12 x GTIN`, `25 X GTIN`, `60 x GTIN`, `100 x GTIN`, etc.
- Accepts GTIN-13 / EAN-13 and safely normalizes it to GTIN-14 by adding the leading zero only when the GS1 check digit is valid.
- Tries barcode scan first / in parallel and falls back to visible OCR digits.
- Reads `Made in ...` as an informational field.
- Tries to build the pack/article line automatically from a detected size such as `125g`, `100ml`, `140g` plus Article.

### UI improvements

The long technical form was reorganized into four clear steps:

1. Scan carton label.
2. Check product data.
3. Set pallet data / SSCC.
4. Preview and share PDF.

Less frequently used fields (`REF`, top-right fields, pack/article line) are hidden under **Advanced fields** by default.

GS1 technical details are also collapsed by default.

### PDF / sharing

- PDF size remains 105 × 148 mm.
- GS1-128 remains generated mathematically using Code 128 + FNC1.
- Long product names are automatically scaled to fit the label.
- `Share PDF` is the main output action.
- Email/share subject is prefilled as:
  `Pallet label - ARTICLE - Batch BATCH`
- File name format:
  `Pallet_ARTICLE_Batch_BATCH_SSCC_SSCC.pdf`
- No direct printer or corporate-network connection is used.

## SSCC warning

The SSCC counter is stored only on this phone. For a reprint, keep the same SSCC.
For a new pallet, use **New SSCC** once.

## Build

GitHub Actions builds `app-debug.apk` on every push to `main` / `master`.
The artifact is named `Pallet-Label-Generator-v1.1-debug`.
