# Pallet Label Generator v1.0

Offline Android application for preparing GS1 pallet labels similar to the reference warehouse sticker.

## Main features

- Manual pallet-label form.
- Take a photo or select an existing photo.
- On-device OCR (ML Kit) for article / batch / quantity and visible label text.
- On-device barcode scan (ML Kit) to detect GTIN-14 / ITF / Code 128 / EAN values from the photo.
- Local SSCC sequence: increments the previous 17-digit SSCC body and recalculates the GS1 Mod-10 check digit.
- Default expiry: current device date + 2 years.
- GS1-128 generated with real Code 128 + FNC1, not an AI-drawn barcode.
- Correct separator between variable AI (10) Batch and AI (240) Article.
- Label preview closely based on the original sticker proportions, with a compact information area and large barcode area.
- Save as 105 x 148 mm PDF.
- Share generated PDF from Android.
- No direct printer/network access.

## Default sample

The first launch contains the Yozakura example used during development. Every field is editable.

## SSCC warning

The SSCC counter is stored only on this phone. Do not run independent counters on multiple phones unless the last used SSCC is synchronized manually.

## Build

GitHub Actions builds `app-debug.apk` on every push to `main` / `master`.
