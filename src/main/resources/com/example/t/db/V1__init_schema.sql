-- V1__init_schema.sql: 6-Table Normalized SQLite Schema for Bookstore & Stationery POS

PRAGMA foreign_keys = ON;

-- 1. Schema Migrations Audit Table
CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    description TEXT,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Categories Table
CREATE TABLE IF NOT EXISTS categories (
    category_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT
);

-- 3. Products Table (Unified Books & Stationery Catalog)
CREATE TABLE IF NOT EXISTS products (
    product_id INTEGER PRIMARY KEY AUTOINCREMENT,
    sku TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    category_id INTEGER REFERENCES categories(category_id) ON DELETE SET NULL,
    price REAL NOT NULL,
    purchase_price REAL DEFAULT 0.0,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    min_stock_threshold INTEGER DEFAULT 5,
    author TEXT,
    publisher TEXT,
    isbn TEXT,
    status TEXT DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Invoices Table
CREATE TABLE IF NOT EXISTS invoices (
    invoice_id INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_number TEXT UNIQUE NOT NULL,
    customer_name TEXT DEFAULT 'Walk-in Customer',
    customer_phone TEXT,
    invoice_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    subtotal REAL NOT NULL,
    discount_percent REAL DEFAULT 0.0,
    discount_amount REAL DEFAULT 0.0,
    tax_rate REAL DEFAULT 0.0,
    tax_amount REAL DEFAULT 0.0,
    net_total REAL NOT NULL,
    cash_tendered REAL NOT NULL,
    change_due REAL NOT NULL,
    payment_method TEXT DEFAULT 'CASH',
    currency_code TEXT DEFAULT 'USD',
    region TEXT DEFAULT 'USA'
);

-- 5. Invoice Items Table
CREATE TABLE IF NOT EXISTS invoice_items (
    item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL,
    unit_price REAL NOT NULL,
    line_total REAL NOT NULL
);

-- 6. Store Settings Singleton Table
CREATE TABLE IF NOT EXISTS store_settings (
    setting_id INTEGER PRIMARY KEY CHECK (setting_id = 1),
    store_name TEXT NOT NULL,
    store_address TEXT,
    store_phone TEXT,
    region_preset TEXT NOT NULL DEFAULT 'USA',
    currency_code TEXT NOT NULL DEFAULT 'USD',
    currency_symbol TEXT NOT NULL DEFAULT '$',
    tax_name TEXT NOT NULL DEFAULT 'Sales Tax',
    tax_rate REAL NOT NULL DEFAULT 0.0,
    receipt_language TEXT NOT NULL DEFAULT 'EN',
    admin_password_hash TEXT NOT NULL
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_invoices_number ON invoices(invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoices_date ON invoices(invoice_date);
CREATE INDEX IF NOT EXISTS idx_invoices_customer ON invoices(customer_name);
CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON invoice_items(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_items_product ON invoice_items(product_id);
