package com.questionmark.simpropos.util;

import com.questionmark.simpropos.model.CartLine;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Menulis struk transaksi ke file TXT menggunakan FileOutputStream dan PrintStream
 * (demonstrasi penggunaan OutputStream / Stream dalam Java).
 *
 * File disimpan di folder "receipts/" relatif terhadap direktori kerja aplikasi.
 */
public class ReceiptExporter {

    private static final DecimalFormat NUM_FMT      = new DecimalFormat("#,##0");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int WIDTH = 40;

    /**
     * Tulis struk ke file menggunakan FileOutputStream → PrintStream.
     *
     * @return Path file yang berhasil ditulis
     */
    public static Path export(
            String invoiceNum,
            String paymentNum,
            String bpName,
            String paymentMethod,
            List<CartLine> lines,
            BigDecimal subtotal,
            BigDecimal ppn,
            BigDecimal grandTotal,
            BigDecimal tendered,
            BigDecimal change) throws IOException {

        // Buat folder receipts/ jika belum ada
        Path dir = Path.of("receipts");
        Files.createDirectories(dir);

        Path file = dir.resolve(invoiceNum + ".txt");

        // ── OutputStream: tulis struk ke file ──────────────────────────────
        try (FileOutputStream fos    = new FileOutputStream(file.toFile());
             PrintStream      ps     = new PrintStream(fos, true, StandardCharsets.UTF_8)) {

            ps.println(center("SIMPRO POS", WIDTH));
            ps.println(center("Struk Penjualan", WIDTH));
            ps.println(repeat("=", WIDTH));

            ps.println("No. Invoice  : " + invoiceNum);
            ps.println("No. Bayar    : " + paymentNum);
            ps.println("Waktu        : " + LocalDateTime.now().format(DT_FMT));
            ps.println("Pelanggan    : " + bpName);
            ps.println("Metode       : " + paymentMethod);
            ps.println(repeat("-", WIDTH));

            // Detail item
            for (CartLine line : lines) {
                String name = truncate(line.getItemName(), 22);
                String qty  = line.getQty() + " x " + formatRp(line.getUnitPrice());
                String tot  = formatRp(line.lineTotal());

                ps.println(name);
                ps.printf("  %-24s %s%n", qty, tot);
            }

            ps.println(repeat("-", WIDTH));
            ps.printf("%-20s %s%n", "Subtotal",   formatRp(subtotal));
            ps.printf("%-20s %s%n", "PPN",        formatRp(ppn));
            ps.println(repeat("=", WIDTH));
            ps.printf("%-20s %s%n", "TOTAL",      formatRp(grandTotal));
            ps.printf("%-20s %s%n", "Diterima",   formatRp(tendered));

            if (change.compareTo(BigDecimal.ZERO) > 0) {
                ps.printf("%-20s %s%n", "Kembalian", formatRp(change));
            }

            ps.println(repeat("=", WIDTH));
            ps.println(center("Terima kasih!", WIDTH));
            ps.println(center("simpro.rifkysaputram.net", WIDTH));
        }

        return file;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String formatRp(BigDecimal v) {
        return "Rp " + NUM_FMT.format(v.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static String repeat(String ch, int n) {
        return ch.repeat(n);
    }

    private static String center(String text, int width) {
        int pad = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(pad) + text;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
