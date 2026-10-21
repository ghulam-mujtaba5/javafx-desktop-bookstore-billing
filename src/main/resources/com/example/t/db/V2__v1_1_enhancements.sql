-- V2__v1_1_enhancements.sql: Version 1.1 Persistence Infrastructure & Feature Enhancements

-- 1. Catalog Cache for Intelligent Online/Offline ISBN Bibliographic Enrichment
CREATE TABLE IF NOT EXISTS catalog_cache (
    isbn13 TEXT PRIMARY KEY,
    isbn10 TEXT COLLATE NOCASE,
    title TEXT NOT NULL,
    subtitle TEXT,
    authors TEXT,
    publisher TEXT,
    published_year TEXT,
    edition TEXT,
    format TEXT,
    genre TEXT,
    description TEXT,
    page_count INTEGER DEFAULT 0,
    cover_image_path TEXT,
    cover_url TEXT,
    source TEXT DEFAULT 'UNKNOWN',
    cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    raw_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_catalog_cache_isbn10 ON catalog_cache(isbn10);
CREATE INDEX IF NOT EXISTS idx_catalog_cache_title ON catalog_cache(title);
CREATE INDEX IF NOT EXISTS idx_products_isbn ON products(isbn);

-- 2. Multi-Tender Split Payments (Cash, Card, Store Credit)
CREATE TABLE IF NOT EXISTS invoice_payments (
    payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    payment_type TEXT NOT NULL,
    amount REAL NOT NULL,
    tendered_amount REAL DEFAULT 0.0,
    change_due REAL DEFAULT 0.0,
    reference_info TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invoice_payments_invoice ON invoice_payments(invoice_id);

-- 3. Daily Financial Z-Reports and Non-Resettable Cumulative Audit Ledger
CREATE TABLE IF NOT EXISTS financial_z_reports (
    z_report_id INTEGER PRIMARY KEY AUTOINCREMENT,
    z_sequence_number INTEGER UNIQUE NOT NULL,
    report_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    opening_float REAL DEFAULT 0.0,
    total_cash_sales REAL NOT NULL,
    total_card_sales REAL NOT NULL,
    total_credit_sales REAL NOT NULL,
    gross_sales REAL NOT NULL,
    total_discount REAL NOT NULL,
    taxable_sales REAL NOT NULL,
    total_tax REAL NOT NULL,
    net_sales REAL NOT NULL,
    transaction_count INTEGER NOT NULL,
    expected_drawer_cash REAL NOT NULL,
    actual_drawer_cash REAL NOT NULL,
    cash_over_short REAL NOT NULL,
    cumulative_grand_total REAL NOT NULL,
    closed_by_user TEXT DEFAULT 'admin',
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_financial_z_reports_seq ON financial_z_reports(z_sequence_number);

-- 4. Line-item notes and discounts in invoice_items
ALTER TABLE invoice_items ADD COLUMN discount_percent REAL DEFAULT 0.0;
ALTER TABLE invoice_items ADD COLUMN discount_amount REAL DEFAULT 0.0;
ALTER TABLE invoice_items ADD COLUMN notes TEXT;
