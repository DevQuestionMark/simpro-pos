package com.questionmark.simpropos.repository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Singleton
public class ArInvoiceLineRepository {

    @Inject
    public ArInvoiceLineRepository() {}

    /** Inserts one line into ar_invoice_lines. Caller owns the transaction. */
    public void insert(Connection conn,
                       long       invoiceId,
                       long       itemId,
                       BigDecimal qty,
                       BigDecimal unitPrice) throws SQLException {

        BigDecimal lineTotal = qty.multiply(unitPrice);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ar_invoice_lines (invoice_id, item_id, qty, unit_price, line_total) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, invoiceId);
            ps.setLong(2, itemId);
            ps.setBigDecimal(3, qty);
            ps.setBigDecimal(4, unitPrice);
            ps.setBigDecimal(5, lineTotal);
            ps.executeUpdate();
        }
    }
}
