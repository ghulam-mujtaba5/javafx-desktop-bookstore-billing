package com.example.t.service;

import com.example.t.Invoice;
import com.example.t.database.DatabaseManager;
import com.example.t.model.InvoiceItem;
import com.example.t.model.RegionalPreset;
import com.example.t.model.StoreSettings;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating professional, branded PDF invoices and receipts
 * using OpenPDF 1.3.40.
 *
 * Supports multi-regional currencies, sales tax / VAT calculations,
 * and bilingual Arabic/English layouts for Gulf presets.
 */
public class PdfInvoiceService {

    private static final Color PRIMARY_COLOR = new Color(15, 23, 42);     // #0f172a
    private static final Color SECONDARY_COLOR = new Color(71, 85, 105);   // #475569
    private static final Color TABLE_HEADER_BG = new Color(241, 245, 249); // #f1f5f9
    private static final Color TOTAL_ROW_BG = new Color(226, 232, 240);    // #e2e8f0
    private static final Color BORDER_COLOR = new Color(203, 213, 225);    // #cbd5e1

    private final Path customInvoicesDir;

    static {
        try {
            FontFactory.registerDirectories();
        } catch (Exception ignored) {}
    }

    /**
     * Resolves an Arabic-supporting font available on the host system (e.g. Arial, Tahoma, Segoe UI),
     * falling back to Helvetica if unavailable.
     */
    private static Font resolveFont(float size, int style, Color color) {
        String fontName = findArabicFontName();
        if (fontName != null) {
            try {
                Font font = FontFactory.getFont(fontName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, size, style, color);
                if (font != null && font.getBaseFont() != null) {
                    return font;
                }
            } catch (Exception ignored) {}
        }

        String fallback = (style == Font.BOLD) ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA;
        return FontFactory.getFont(fallback, size, style, color);
    }

    private static String findArabicFontName() {
        String[] candidates = {"Arial", "arial", "Tahoma", "tahoma", "Segoe UI", "segoe ui", "Times New Roman"};
        for (String name : candidates) {
            if (FontFactory.isRegistered(name)) {
                return name;
            }
        }
        return null;
    }

    public PdfInvoiceService() {
        this(null);
    }

    public PdfInvoiceService(Path customInvoicesDir) {
        this.customInvoicesDir = customInvoicesDir;
    }

    /**
     * Resolves the directory where generated PDF invoices are stored.
     * Respects 'bookstore.invoices.dir' system property, AppData directory,
     * or project fallback.
     */
    public Path getInvoicesDirectory() {
        if (customInvoicesDir != null) {
            try {
                Files.createDirectories(customInvoicesDir);
            } catch (IOException ignored) {}
            return customInvoicesDir;
        }

        String sysProp = System.getProperty("bookstore.invoices.dir");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            Path p = Paths.get(sysProp.trim());
            try {
                Files.createDirectories(p);
            } catch (IOException ignored) {}
            return p;
        }

        try {
            Path appData = DatabaseManager.getDataDirectory().getParent().resolve("invoices");
            Files.createDirectories(appData);
            return appData;
        } catch (Exception e) {
            Path fallback = Paths.get("invoices");
            try {
                Files.createDirectories(fallback);
            } catch (IOException ignored) {}
            return fallback;
        }
    }

    /**
     * Generates a PDF invoice for the given invoice, items, and settings.
     *
     * @param invoice  Invoice entity
     * @param items    List of line items
     * @param settings Active store settings
     * @return Path to generated PDF
     * @throws IOException on write failure
     */
    public Path generateInvoicePdf(Invoice invoice, List<InvoiceItem> items, StoreSettings settings) throws IOException {
        String filename = "invoice_" + (invoice.getInvoiceId() > 0 ? invoice.getInvoiceId() : System.currentTimeMillis()) + ".pdf";
        Path targetPath = getInvoicesDirectory().resolve(filename);
        return generateInvoicePdf(invoice, items, settings, targetPath);
    }

    /**
     * Generates a PDF invoice saved directly to the specified destination path.
     */
    public Path generateInvoicePdf(Invoice invoice, List<InvoiceItem> items, StoreSettings settings, Path targetFile) throws IOException {
        return generateReceiptPdf(invoice, items, settings, false, targetFile);
    }

    /**
     * Generates a price-suppressed gift receipt saved to the default invoices directory.
     */
    public Path generateGiftReceipt(Invoice invoice, List<InvoiceItem> items, StoreSettings settings) throws IOException {
        String filename = "gift_receipt_" + (invoice.getInvoiceId() > 0 ? invoice.getInvoiceId() : System.currentTimeMillis()) + ".pdf";
        Path targetPath = getInvoicesDirectory().resolve(filename);
        return generateGiftReceipt(invoice, items, settings, false, targetPath);
    }

    /**
     * Generates a price-suppressed gift receipt saved directly to the specified destination path.
     */
    public Path generateGiftReceipt(Invoice invoice, List<InvoiceItem> items, Path targetFile) throws IOException {
        return generateGiftReceipt(invoice, items, StoreSettings.defaultSettings(), false, targetFile);
    }

    /**
     * Generates a price-suppressed gift receipt PDF (either A4 or 80mm thermal).
     * Suppresses unit prices, line totals, subtotal, discounts, tax, and grand totals.
     * Renders store header, invoice #, Code-128 barcode, date, item descriptions, quantities,
     * and a 30-day exchange policy notice.
     */
    public Path generateGiftReceipt(Invoice invoice, List<InvoiceItem> items, StoreSettings settings,
                                    boolean thermal80mm, Path targetFile) throws IOException {
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice cannot be null");
        }
        if (settings == null) {
            settings = StoreSettings.defaultSettings();
        }

        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }

        Rectangle pageSize = thermal80mm
                ? new Rectangle(226.77f, 600f) // 80mm width in points
                : PageSize.A4;

        float margin = thermal80mm ? 12f : 36f;
        Document document = new Document(pageSize, margin, margin, margin, margin);

        try (FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            RegionalPreset preset = RegionalPreset.fromString(settings.getRegionPreset());
            boolean isBilingual = preset.isBilingualArabic() || "BILINGUAL_AR_EN".equalsIgnoreCase(settings.getReceiptLanguage());

            // Fonts
            Font fontTitle = resolveFont(thermal80mm ? 13f : 18f, Font.BOLD, PRIMARY_COLOR);
            Font fontSubtitle = resolveFont(thermal80mm ? 8f : 10f, Font.NORMAL, SECONDARY_COLOR);
            Font fontHeader = resolveFont(thermal80mm ? 8f : 10f, Font.BOLD, PRIMARY_COLOR);
            Font fontBody = resolveFont(thermal80mm ? 8f : 9.5f, Font.NORMAL, PRIMARY_COLOR);
            Font fontBold = resolveFont(thermal80mm ? 8.5f : 10f, Font.BOLD, PRIMARY_COLOR);

            // 1. Store Header
            Paragraph storeName = new Paragraph(settings.getStoreName(), fontTitle);
            storeName.setAlignment(Element.ALIGN_CENTER);
            document.add(storeName);

            if (settings.getStoreAddress() != null && !settings.getStoreAddress().trim().isEmpty()) {
                Paragraph storeAddr = new Paragraph(settings.getStoreAddress(), fontSubtitle);
                storeAddr.setAlignment(Element.ALIGN_CENTER);
                document.add(storeAddr);
            }

            if (settings.getStorePhone() != null && !settings.getStorePhone().trim().isEmpty()) {
                Paragraph storePhone = new Paragraph("Phone: " + settings.getStorePhone(), fontSubtitle);
                storePhone.setAlignment(Element.ALIGN_CENTER);
                document.add(storePhone);
            }

            // Gift Receipt Title
            String giftTitleText = isBilingual ? "GIFT RECEIPT / إيصال هدية" : "GIFT RECEIPT";
            Paragraph pTitle = new Paragraph(giftTitleText, fontBold);
            pTitle.setAlignment(Element.ALIGN_CENTER);
            pTitle.setSpacingBefore(thermal80mm ? 6f : 10f);
            pTitle.setSpacingAfter(thermal80mm ? 4f : 8f);
            document.add(pTitle);

            // 2. Invoice Meta Table
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(thermal80mm ? 6f : 10f);
            metaTable.setWidths(new float[]{1f, 1f});

            String invNum = "INV-" + (invoice.getInvoiceId() > 0 ? invoice.getInvoiceId() : invoice.getOrderNum());
            String dateStr = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String customer = (invoice.getCustomerName() != null && !invoice.getCustomerName().trim().isEmpty())
                    ? invoice.getCustomerName() : "Walk-in Customer";

            PdfPCell cellLeft = new PdfPCell();
            cellLeft.setBorder(Rectangle.NO_BORDER);
            cellLeft.addElement(new Paragraph("Invoice #: " + invNum, fontBold));
            cellLeft.addElement(new Paragraph("Date: " + dateStr, fontSubtitle));

            PdfPCell cellRight = new PdfPCell();
            cellRight.setBorder(Rectangle.NO_BORDER);
            cellRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellRight.addElement(new Paragraph("Customer: " + customer, fontBold));

            metaTable.addCell(cellLeft);
            metaTable.addCell(cellRight);
            document.add(metaTable);

            // 3. Code-128 Barcode of Invoice Number
            try {
                Barcode128 barcode = new Barcode128();
                barcode.setCode(invNum);
                barcode.setCodeType(Barcode128.CODE128);
                barcode.setFont(null);
                com.lowagie.text.Image barcodeImage = barcode.createImageWithBarcode(writer.getDirectContent(), null, null);
                barcodeImage.setAlignment(Element.ALIGN_CENTER);
                barcodeImage.scalePercent(thermal80mm ? 90f : 110f);
                barcodeImage.setSpacingBefore(thermal80mm ? 4f : 8f);
                barcodeImage.setSpacingAfter(thermal80mm ? 6f : 10f);
                document.add(barcodeImage);
            } catch (Exception ignored) {}

            // 4. Line Items Table (Price-Suppressed: 3 columns only)
            PdfPTable itemsTable = new PdfPTable(3);
            itemsTable.setWidthPercentage(100);
            itemsTable.setSpacingAfter(thermal80mm ? 8f : 14f);
            itemsTable.setWidths(new float[]{4.2f, 2.2f, 1.2f});

            String colItem = isBilingual ? "Item / الصنف" : "Item Description";
            String colSku = isBilingual ? "SKU / الرمز" : "SKU";
            String colQty = isBilingual ? "Qty / الكمية" : "Qty";

            addTableHeaderCell(itemsTable, colItem, fontHeader, Element.ALIGN_LEFT);
            addTableHeaderCell(itemsTable, colSku, fontHeader, Element.ALIGN_LEFT);
            addTableHeaderCell(itemsTable, colQty, fontHeader, Element.ALIGN_CENTER);

            if (items != null) {
                for (InvoiceItem item : items) {
                    String desc = item.getProductName();
                    if (item.getNotes() != null && !item.getNotes().trim().isEmpty()) {
                        desc += " (" + item.getNotes() + ")";
                    }
                    addTableCell(itemsTable, desc, fontBody, Element.ALIGN_LEFT);
                    addTableCell(itemsTable, item.getSku(), fontBody, Element.ALIGN_LEFT);
                    addTableCell(itemsTable, String.valueOf(item.getQuantity()), fontBody, Element.ALIGN_CENTER);
                }
            }
            document.add(itemsTable);

            // 5. Gift Policy Notice (NO financial totals table)
            Paragraph pPolicy = new Paragraph("Gift Receipt: Exchangeable within 30 days with receipt.\nItems must be in original condition.", fontBold);
            pPolicy.setAlignment(Element.ALIGN_CENTER);
            pPolicy.setSpacingBefore(thermal80mm ? 8f : 14f);
            document.add(pPolicy);

            Paragraph pSystem = new Paragraph("Generated by Bookstore & Stationery POS • " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), fontSubtitle);
            pSystem.setAlignment(Element.ALIGN_CENTER);
            pSystem.setSpacingBefore(6);
            document.add(pSystem);

            document.close();
            writer.close();
        } catch (DocumentException e) {
            throw new IOException("OpenPDF Document error: " + e.getMessage(), e);
        }

        return targetFile;
    }

    /**
     * Generates either an A4 invoice or an 80mm thermal receipt PDF.
     */
    public Path generateReceiptPdf(Invoice invoice, List<InvoiceItem> items, StoreSettings settings,
                                  boolean thermal80mm, Path targetFile) throws IOException {
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice cannot be null");
        }
        if (settings == null) {
            settings = StoreSettings.defaultSettings();
        }

        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }

        Rectangle pageSize = thermal80mm
                ? new Rectangle(226.77f, 600f) // 80mm width in points
                : PageSize.A4;

        float margin = thermal80mm ? 12f : 36f;
        Document document = new Document(pageSize, margin, margin, margin, margin);

        try (FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            RegionalPreset preset = RegionalPreset.fromString(settings.getRegionPreset());
            boolean isBilingual = preset.isBilingualArabic() || "BILINGUAL_AR_EN".equalsIgnoreCase(settings.getReceiptLanguage());
            String currency = settings.getCurrencySymbol();
            if (currency == null || currency.trim().isEmpty()) {
                currency = settings.getCurrencyCode();
            }

            // Fonts
            Font fontTitle = resolveFont(thermal80mm ? 13f : 18f, Font.BOLD, PRIMARY_COLOR);
            Font fontSubtitle = resolveFont(thermal80mm ? 8f : 10f, Font.NORMAL, SECONDARY_COLOR);
            Font fontHeader = resolveFont(thermal80mm ? 8f : 10f, Font.BOLD, PRIMARY_COLOR);
            Font fontBody = resolveFont(thermal80mm ? 8f : 9.5f, Font.NORMAL, PRIMARY_COLOR);
            Font fontBold = resolveFont(thermal80mm ? 8.5f : 10f, Font.BOLD, PRIMARY_COLOR);
            Font fontGrandTotal = resolveFont(thermal80mm ? 11f : 14f, Font.BOLD, PRIMARY_COLOR);

            // 1. Store Header
            Paragraph storeName = new Paragraph(settings.getStoreName(), fontTitle);
            storeName.setAlignment(Element.ALIGN_CENTER);
            document.add(storeName);

            if (settings.getStoreAddress() != null && !settings.getStoreAddress().trim().isEmpty()) {
                Paragraph storeAddr = new Paragraph(settings.getStoreAddress(), fontSubtitle);
                storeAddr.setAlignment(Element.ALIGN_CENTER);
                document.add(storeAddr);
            }

            if (settings.getStorePhone() != null && !settings.getStorePhone().trim().isEmpty()) {
                Paragraph storePhone = new Paragraph("Phone: " + settings.getStorePhone(), fontSubtitle);
                storePhone.setAlignment(Element.ALIGN_CENTER);
                document.add(storePhone);
            }

            // Invoice Title
            String invoiceTitleText = isBilingual ? "TAX INVOICE / فاتورة ضريبية" : "TAX INVOICE";
            Paragraph invTitle = new Paragraph(invoiceTitleText, fontBold);
            invTitle.setAlignment(Element.ALIGN_CENTER);
            invTitle.setSpacingBefore(thermal80mm ? 6f : 10f);
            invTitle.setSpacingAfter(thermal80mm ? 6f : 12f);
            document.add(invTitle);

            // 2. Invoice Meta Table (2 columns)
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(thermal80mm ? 8f : 14f);
            metaTable.setWidths(new float[]{1f, 1f});

            String invNum = "INV-" + (invoice.getInvoiceId() > 0 ? invoice.getInvoiceId() : invoice.getOrderNum());
            String dateStr = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String customer = (invoice.getCustomerName() != null && !invoice.getCustomerName().trim().isEmpty())
                    ? invoice.getCustomerName() : "Walk-in Customer";

            PdfPCell cellLeft = new PdfPCell();
            cellLeft.setBorder(Rectangle.NO_BORDER);
            cellLeft.addElement(new Paragraph("Invoice #: " + invNum, fontBold));
            cellLeft.addElement(new Paragraph("Date: " + dateStr, fontSubtitle));
            cellLeft.addElement(new Paragraph("Payment: Cash / Card", fontSubtitle));

            PdfPCell cellRight = new PdfPCell();
            cellRight.setBorder(Rectangle.NO_BORDER);
            cellRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellRight.addElement(new Paragraph("Customer: " + customer, fontBold));
            cellRight.addElement(new Paragraph("Region: " + settings.getRegionPreset() + " (" + settings.getCurrencyCode() + ")", fontSubtitle));

            metaTable.addCell(cellLeft);
            metaTable.addCell(cellRight);
            document.add(metaTable);

            // 3. Line Items Table
            PdfPTable itemsTable = new PdfPTable(5);
            itemsTable.setWidthPercentage(100);
            itemsTable.setSpacingAfter(thermal80mm ? 8f : 14f);
            itemsTable.setWidths(new float[]{3.2f, 1.8f, 1.0f, 1.5f, 1.8f});

            String colItem = isBilingual ? "Item / الصنف" : "Item Description";
            String colSku = isBilingual ? "SKU / الرمز" : "SKU";
            String colQty = isBilingual ? "Qty / الكمية" : "Qty";
            String colPrice = isBilingual ? "Price / السعر" : "Unit Price";
            String colTotal = isBilingual ? "Total / المجموع" : "Line Total";

            addTableHeaderCell(itemsTable, colItem, fontHeader, Element.ALIGN_LEFT);
            addTableHeaderCell(itemsTable, colSku, fontHeader, Element.ALIGN_LEFT);
            addTableHeaderCell(itemsTable, colQty, fontHeader, Element.ALIGN_CENTER);
            addTableHeaderCell(itemsTable, colPrice, fontHeader, Element.ALIGN_RIGHT);
            addTableHeaderCell(itemsTable, colTotal, fontHeader, Element.ALIGN_RIGHT);

            double calculatedSubtotal = 0.0;
            if (items != null && !items.isEmpty()) {
                for (InvoiceItem item : items) {
                    addTableCell(itemsTable, item.getProductName(), fontBody, Element.ALIGN_LEFT);
                    addTableCell(itemsTable, item.getSku() != null ? item.getSku() : "-", fontSubtitle, Element.ALIGN_LEFT);
                    addTableCell(itemsTable, String.valueOf(item.getQuantity()), fontBody, Element.ALIGN_CENTER);
                    addTableCell(itemsTable, formatMoney(currency, item.getUnitPrice()), fontBody, Element.ALIGN_RIGHT);
                    addTableCell(itemsTable, formatMoney(currency, item.getLineTotal()), fontBody, Element.ALIGN_RIGHT);
                    calculatedSubtotal += item.getLineTotal();
                }
            } else if (invoice.getProducts() != null && !invoice.getProducts().isEmpty()) {
                for (var p : invoice.getProducts()) {
                    double lineTot = BillingService.roundMoney(p.getPrice() * p.getQuantity());
                    addTableCell(itemsTable, p.getProductName(), fontBody, Element.ALIGN_LEFT);
                    addTableCell(itemsTable, p.getSku() != null ? p.getSku() : "-", fontSubtitle, Element.ALIGN_LEFT);
                    addTableCell(itemsTable, String.valueOf(p.getQuantity()), fontBody, Element.ALIGN_CENTER);
                    addTableCell(itemsTable, formatMoney(currency, p.getPrice()), fontBody, Element.ALIGN_RIGHT);
                    addTableCell(itemsTable, formatMoney(currency, lineTot), fontBody, Element.ALIGN_RIGHT);
                    calculatedSubtotal += lineTot;
                }
            } else {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No line items recorded", fontSubtitle));
                emptyCell.setColspan(5);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setPadding(8);
                emptyCell.setBorderColor(BORDER_COLOR);
                itemsTable.addCell(emptyCell);
            }
            document.add(itemsTable);

            // 4. Financial Calculation Summary Table
            double subtotal = invoice.getOrderTotal() > 0 ? invoice.getOrderTotal() : calculatedSubtotal;
            double discPercent = invoice.getDiscountPercentage();
            double discAmount = invoice.getDiscount() > 0 ? invoice.getDiscount() : BillingService.roundMoney(subtotal * (discPercent / 100.0));
            double taxable = BillingService.roundMoney(subtotal - discAmount);
            double taxRate = settings.getTaxRate();
            double taxAmount = BillingService.roundMoney(taxable * (taxRate / 100.0));
            double netTotal = invoice.getNetPrice() > 0 ? invoice.getNetPrice() : BillingService.roundMoney(taxable + taxAmount);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(thermal80mm ? 100 : 55);
            summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            summaryTable.setSpacingAfter(thermal80mm ? 10f : 20f);
            summaryTable.setWidths(new float[]{2f, 1.5f});

            addSummaryRow(summaryTable, isBilingual ? "Subtotal / المجموع الفرعي:" : "Subtotal:", formatMoney(currency, subtotal), fontBody);
            if (discPercent > 0 || discAmount > 0) {
                String discLabel = isBilingual ? "Discount / الخصم (" + discPercent + "%):" : "Discount (" + discPercent + "%):";
                addSummaryRow(summaryTable, discLabel, "-" + formatMoney(currency, discAmount), fontBody);
                addSummaryRow(summaryTable, isBilingual ? "Taxable Amount / المبلغ الخاضع:" : "Taxable Amount:", formatMoney(currency, taxable), fontBody);
            }

            String taxLabel = settings.getTaxName() + " (" + taxRate + "%):";
            if (isBilingual) {
                taxLabel = "VAT / ضريبة القيمة المضافة (" + taxRate + "%):";
            }
            addSummaryRow(summaryTable, taxLabel, formatMoney(currency, taxAmount), fontBody);

            // Grand Total Row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase(isBilingual ? "Grand Total / المجموع الكلي:" : "Grand Total:", fontGrandTotal));
            totalLabelCell.setBackgroundColor(TOTAL_ROW_BG);
            totalLabelCell.setPadding(6);
            totalLabelCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
            totalLabelCell.setBorderColor(BORDER_COLOR);

            PdfPCell totalValueCell = new PdfPCell(new Phrase(formatMoney(currency, netTotal), fontGrandTotal));
            totalValueCell.setBackgroundColor(TOTAL_ROW_BG);
            totalValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValueCell.setPadding(6);
            totalValueCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
            totalValueCell.setBorderColor(BORDER_COLOR);

            summaryTable.addCell(totalLabelCell);
            summaryTable.addCell(totalValueCell);

            document.add(summaryTable);

            // 5. Footer Notes
            Paragraph footer = new Paragraph();
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(thermal80mm ? 8f : 16f);

            String thankYou = isBilingual
                    ? "Thank you for your visit! / شكراً لزيارتكم!"
                    : "Thank you for shopping with us!";
            Paragraph pThankYou = new Paragraph(thankYou, fontBold);
            pThankYou.setAlignment(Element.ALIGN_CENTER);
            document.add(pThankYou);

            Paragraph pReturn = new Paragraph("Returns & exchanges accepted within 7 days with valid receipt.\nAll items must be in original condition.", fontSubtitle);
            pReturn.setAlignment(Element.ALIGN_CENTER);
            pReturn.setSpacingBefore(4);
            document.add(pReturn);

            Paragraph pSystem = new Paragraph("Generated by Bookstore & Stationery POS • " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), fontSubtitle);
            pSystem.setAlignment(Element.ALIGN_CENTER);
            pSystem.setSpacingBefore(6);
            document.add(pSystem);

            document.close();
            writer.close();
        } catch (DocumentException e) {
            throw new IOException("OpenPDF Document error: " + e.getMessage(), e);
        }

        return targetFile;
    }

    private void addTableHeaderCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(TABLE_HEADER_BG);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(3);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String formatMoney(String currency, double amount) {
        return String.format("%s %.2f", currency, amount);
    }
}
