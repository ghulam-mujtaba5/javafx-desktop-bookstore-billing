# Explorer Fix 2 Context: Audit All Other DAOs for Regex & Case Sensitivity
Audit ProductDao, InvoiceDao, CategoryDao, SettingsDao, LegacyDataImporter, and CsvSyncService for:
- Any regex character ranges like [^0-9X-x]
- Any case-sensitive collation pitfalls when querying SKUs, barcodes, categories, or invoice numbers.
