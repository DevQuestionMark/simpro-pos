package com.questionmark.simpropos.repository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;

@Singleton
public class ArInvoiceRepository {

    @Inject
    public ArInvoiceRepository() {}

    /**
     * Generates the next invoice doc_num using the same logic as Laravel:
     * 'INV-{year}-{count_this_year + 1}' zero-padded to 5 digits.
     * Must be called inside the finalize transaction (conn has autoCommit=false).
     */
    public String generateDocNum(Connection conn, int year) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM ar_invoices WHERE YEAR(created_at) = ?")) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return "INV-" + year + "-" + String.format("%05d", rs.getInt(1) + 1);
            }
        }
    }

    /**
     * Inserts an ar_invoices row. Returns the generated id.
     * Caller owns the transaction.
     */
    public long insert(Connection conn,
                       String    docNum,
                       LocalDate docDate,
                       long      bpId,
                       int       warehouseId,
                       BigDecimal total,
                       long      createdBy) throws SQLException {

        String sql = """
            INSERT INTO ar_invoices
              (doc_num, doc_date, so_id, bp_id, warehouse_id,
               status, total, paid_amount, created_by, created_at, updated_at)
            VALUES (?, ?, NULL, ?, ?, 'OPEN', ?, 0, ?, NOW(), NOW())
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, docNum);
            ps.setDate(2, Date.valueOf(docDate));
            ps.setLong(3, bpId);
            ps.setInt(4, warehouseId);
            ps.setBigDecimal(5, total);
            ps.setLong(6, createdBy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /**
     * Marks the invoice as PAID and updates paid_amount.
     * Caller owns the transaction.
     */
    public void markPaid(Connection conn, long invoiceId, BigDecimal paidAmount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ar_invoices SET paid_amount = ?, status = 'PAID', updated_at = NOW() WHERE id = ?")) {
            ps.setBigDecimal(1, paidAmount);
            ps.setLong(2, invoiceId);
            ps.executeUpdate();
        }
    }
}
