# Pallet Label Generator v1.2

Offline Android application for reading warehouse carton labels and generating pallet-label PDFs.

## v1.2 changes

- Multi-layout OCR with AUTO mode and manual modes:
  - RITUALS WHITE
  - RITUALS BROWN
  - SEMIFINISHED
  - PALLET LOGISTICS
- OCR verification dialog before scanned values overwrite the form.
- Separate Article TU and Article CU.
- Robust line-based Batch parser (prevents values such as `RITUALS` being selected as Batch).
- Product name + separate product description.
- Reads `Made in`, `EXP`, `PO code`, quantity × EAN and logistics-table fields.
- Barcode result has priority for packaging GTIN-14; text fallback remains available.
- Local product cache by Article. It can restore product name / description / GTIN / pieces / country; Batch is never cached.
- Two output templates:
  - STANDARD: current 3 × GS1-128 pallet label.
  - LOGISTICS (BETA): SSCC / ARTICLE / MATERIAL / CONTENT / CUSTOMER SKU / COUNT / EXPIRY / BATCH / gross pallet weight + 2 GS1-128.
- 2D code on Logistics is NEVER guessed. Data Matrix is generated only if the exact payload is entered manually; otherwise a clearly marked placeholder is printed.
- Share PDF remains the main output workflow.
- GitHub Actions artifact renamed to `Pallet-Label-Generator-v1.2-debug`.
- `actions/setup-java` updated to v5.

## Important

The exact 2D payload/specification from the logistics reference label is not known yet. v1.2 deliberately does not fabricate it.

## Build

GitHub Actions builds `app-debug.apk` on push to `main` / `master`.
