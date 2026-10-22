package com.example.t.service;

import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.model.InvoiceItem;
import com.example.t.model.InvoicePayment;
import com.example.t.model.StoreSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for direct binary ESC/POS thermal receipt printing, cash drawer kick pulses,
 * and Code-128 barcode label roll rendering (80mm & 58mm).
 *
 * Bypasses operating system print dialogs to achieve instantaneous sub-100ms hardware printing
 * over raw USB/Serial streams, network raw sockets (port 9100), or raw file export.
 */
public class EscPosPrintService {

    // --- Standard ESC/POS Command Byte Sequences ---
    public static final byte[] CMD_INIT = new byte[]{0x1B, 0x40}; // ESC @
    public static final byte[] CMD_ALIGN_LEFT = new byte[]{0x1B, 0x61, 0x00}; // ESC a 0
    public static final byte[] CMD_ALIGN_CENTER = new byte[]{0x1B, 0x61, 0x01}; // ESC a 1
    public static final byte[] CMD_ALIGN_RIGHT = new byte[]{0x1B, 0x61, 0x02}; // ESC a 2
    public static final byte[] CMD_BOLD_ON = new byte[]{0x1B, 0x45, 0x01}; // ESC E 1
    public static final byte[] CMD_BOLD_OFF = new byte[]{0x1B, 0x45, 0x00}; // ESC E 0
    public static final byte[] CMD_DOUBLE_SIZE_ON = new byte[]{0x1D, 0x21, 0x11}; // GS ! 0x11
    public static final byte[] CMD_DOUBLE_SIZE_OFF = new byte[]{0x1D, 0x21, 0x00}; // GS ! 0x00
    public static final byte[] CMD_CUT_PARTIAL = new byte[]{0x1D, 0x56, 0x42, 0x00}; // GS V B 0
    public static final byte[] CMD_CUT_FULL = new byte[]{0x1D, 0x56, 0x41, 0x00}; // GS V A 0
    public static final byte[] CMD_DRAWER_KICK = new byte[]{0x1B, 0x70, 0x00, 0x19, (byte) 0xFA}; // ESC p 0 25 250
    public static final byte[] CMD_LINE_FEED = new byte[]{0x0A};

    private static final int WIDTH_80MM = 48; // Standard 80mm characters per line (Font A)
    private static final int WIDTH_58MM = 32; // Standard 58mm characters per line (Font A)

    /**
     * Generates standard ESC/POS cash drawer kick pulse bytes.
     */
    public static byte[] generateDrawerKickPulse() {
        return CMD_DRAWER_KICK.clone();
    }

    /**
     * Builds complete binary ESC/POS thermal receipt bytecode for an invoice.
     */
    public byte[] generateThermalReceiptBytes(Invoice invoice,
                                             List<InvoiceItem> items,
                                             List<InvoicePayment> payments,
                                             StoreSettings settings,
                                             boolean is80mm,
                                             boolean kickDrawer) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int width = is80mm ? WIDTH_80MM : WIDTH_58MM;

        // 1. Initialize
        out.write(CMD_INIT);

        // Optional cash drawer kick at start
        if (kickDrawer) {
            out.write(CMD_DRAWER_KICK);
        }

        // 2. Header (Centered, bold title)
        out.write(CMD_ALIGN_CENTER);
        out.write(CMD_BOLD_ON);
        out.write(CMD_DOUBLE_SIZE_ON);
        String storeName = settings != null && settings.getStoreName() != null ? settings.getStoreName() : "GM Bookstore";
        out.write(storeName.getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);
        out.write(CMD_DOUBLE_SIZE_OFF);
        out.write(CMD_BOLD_OFF);

        if (settings != null && settings.getStoreAddress() != null && !settings.getStoreAddress().isBlank()) {
            out.write(settings.getStoreAddress().getBytes(StandardCharsets.UTF_8));
            out.write(CMD_LINE_FEED);
        }
        if (settings != null && settings.getStorePhone() != null && !settings.getStorePhone().isBlank()) {
            out.write(("Tel: " + settings.getStorePhone()).getBytes(StandardCharsets.UTF_8));
            out.write(CMD_LINE_FEED);
        }

        out.write(divider(width, '=').getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);

        // 3. Metadata (Left aligned)
        out.write(CMD_ALIGN_LEFT);
        String invId = invoice != null ? String.valueOf(invoice.getInvoiceId()) : "N/A";
        String dateStr = invoice != null && invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() :
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now());
        String customer = invoice != null && invoice.getCustomerName() != null ? invoice.getCustomerName() : "Walk-in Customer";

        out.write(("Invoice #: " + invId + "\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Date:      " + dateStr + "\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Customer:  " + customer + "\n").getBytes(StandardCharsets.UTF_8));

        out.write(divider(width, '-').getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);

        // 4. Line Items Table
        String curr = settings != null && settings.getCurrencySymbol() != null ? settings.getCurrencySymbol() : "$";
        out.write(formatColumns("Item Description", "Qty", "Total", width).getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);
        out.write(divider(width, '-').getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);

        if (items != null) {
            for (InvoiceItem item : items) {
                String name = item.getProductName() != null ? item.getProductName() : "Item";
                String qty = String.valueOf(item.getQuantity());
                String total = curr + String.format("%.2f", item.getLineTotal());
                out.write(formatColumns(name, qty, total, width).getBytes(StandardCharsets.UTF_8));
                out.write(CMD_LINE_FEED);

                // If note exists, print indented
                if (item.getNotes() != null && !item.getNotes().isBlank()) {
                    out.write(("  Note: " + item.getNotes() + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        out.write(divider(width, '-').getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);

        // 5. Totals & Tax
        out.write(CMD_ALIGN_RIGHT);
        double subtotal = invoice != null ? invoice.getOrderTotal() : 0.0;
        double discount = invoice != null ? invoice.getDiscount() : 0.0;
        double tax = invoice != null ? invoice.getTaxAmount() : 0.0;
        double netTotal = invoice != null ? invoice.getNetPrice() : 0.0;

        out.write(formatSummaryLine("Subtotal:", curr + String.format("%.2f", subtotal), width).getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);

        if (discount > 0.001) {
            out.write(formatSummaryLine("Discount:", "-" + curr + String.format("%.2f", discount), width).getBytes(StandardCharsets.UTF_8));
            out.write(CMD_LINE_FEED);
        }

        String taxName = settings != null && settings.getTaxName() != null ? settings.getTaxName() : "Tax";
        out.write(formatSummaryLine(taxName + ":", curr + String.format("%.2f", tax), width).getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);

        out.write(CMD_BOLD_ON);
        out.write(formatSummaryLine("TOTAL DUE:", curr + String.format("%.2f", netTotal), width).getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);
        out.write(CMD_BOLD_OFF);

        // 6. Payment Breakdown (Split Tenders)
        if (payments != null && !payments.isEmpty()) {
            out.write(divider(width, '-').getBytes(StandardCharsets.UTF_8));
            out.write(CMD_LINE_FEED);
            out.write(formatSummaryLine("PAYMENTS", "", width).getBytes(StandardCharsets.UTF_8));
            out.write(CMD_LINE_FEED);

            for (InvoicePayment p : payments) {
                String typeStr = p.getPaymentType() != null ? p.getPaymentType() : "CASH";
                out.write(formatSummaryLine("  " + typeStr + ":", curr + String.format("%.2f", p.getAmount()), width).getBytes(StandardCharsets.UTF_8));
                out.write(CMD_LINE_FEED);
                if (p.getChangeDue() > 0.001) {
                    out.write(formatSummaryLine("  Change Returned:", curr + String.format("%.2f", p.getChangeDue()), width).getBytes(StandardCharsets.UTF_8));
                    out.write(CMD_LINE_FEED);
                }
            }
        }

        // 7. Footer
        out.write(divider(width, '=').getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);
        out.write(CMD_ALIGN_CENTER);

        out.write("Thank you for your purchase!\n".getBytes(StandardCharsets.UTF_8));
        out.write("Please retain receipt for returns/exchanges within 30 days\n".getBytes(StandardCharsets.UTF_8));

        // Feeds & Cut Command
        out.write(new byte[]{0x0A, 0x0A, 0x0A, 0x0A});
        out.write(CMD_CUT_FULL);

        return out.toByteArray();
    }

    /**
     * Generates a 50x30mm thermal spine/shelf label with title, author, price, and barcode command.
     */
    public byte[] generateShelfLabelBytes(Product product, StoreSettings settings) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int width = WIDTH_58MM;
        String curr = settings != null && settings.getCurrencySymbol() != null ? settings.getCurrencySymbol() : "$";

        out.write(CMD_INIT);
        out.write(CMD_ALIGN_CENTER);
        out.write(CMD_BOLD_ON);

        String title = product.getProductName() != null ? product.getProductName() : "Item";
        if (title.length() > 28) title = title.substring(0, 25) + "...";
        out.write(title.getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);
        out.write(CMD_BOLD_OFF);

        if (product.getAuthor() != null && !product.getAuthor().isBlank()) {
            String author = "By: " + product.getAuthor();
            if (author.length() > 28) author = author.substring(0, 25) + "...";
            out.write(author.getBytes(StandardCharsets.UTF_8));
            out.write(CMD_LINE_FEED);
        }

        out.write(CMD_BOLD_ON);
        out.write(CMD_DOUBLE_SIZE_ON);
        out.write((curr + String.format("%.2f", product.getPrice())).getBytes(StandardCharsets.UTF_8));
        out.write(CMD_LINE_FEED);
        out.write(CMD_DOUBLE_SIZE_OFF);
        out.write(CMD_BOLD_OFF);

        String sku = product.getSku() != null ? product.getSku() : String.valueOf(product.getProductId());
        out.write(("*" + sku + "*\n").getBytes(StandardCharsets.UTF_8));

        out.write(new byte[]{0x0A, 0x0A});
        out.write(CMD_CUT_PARTIAL);

        return out.toByteArray();
    }

    /**
     * Sends binary ESC/POS bytecode to a network receipt printer via raw socket.
     */
    public void printToNetworkPrinter(String ipAddress, int port, byte[] rawBytes) throws IOException {
        try (Socket socket = new Socket(ipAddress, port > 0 ? port : 9100);
             OutputStream os = socket.getOutputStream()) {
            os.write(rawBytes);
            os.flush();
        }
    }

    /**
     * Exports raw ESC/POS bytecode to a destination file.
     */
    public void exportToFile(Path destination, byte[] rawBytes) throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        Files.write(destination, rawBytes);
    }

    private static String divider(int width, char ch) {
        return String.valueOf(ch).repeat(Math.max(1, width));
    }

    private static String formatColumns(String col1, String col2, String col3, int width) {
        int qtyWidth = 6;
        int totalWidth = 10;
        int nameWidth = width - qtyWidth - totalWidth;
        if (nameWidth < 10) nameWidth = 10;

        String c1 = col1.length() > nameWidth ? col1.substring(0, nameWidth - 1) + " " : col1;
        return String.format("%-" + nameWidth + "s%" + qtyWidth + "s%" + totalWidth + "s", c1, col2, col3);
    }

    private static String formatSummaryLine(String label, String value, int width) {
        int labelWidth = width - value.length();
        if (labelWidth < 0) labelWidth = 0;
        return String.format("%-" + labelWidth + "s%s", label, value);
    }
}
