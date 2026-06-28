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
import java.time.LocalDate;

@Singleton
public class IncomingPaymentRepository {

    @Inject
    public IncomingPaymentRepository() {}

    /**
     * Generates the next payment doc_num using the same logic as Laravel:
     * 'PAY-{year}-{count_this_year + 1}' zero-padded to 5 digits.
     * Must be called inside the finalize transaction (conn has autoCommit=false).
     */
    public String generateDocNum(Connection conn, int year) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM incoming_payments WHERE YEAR(created_at) = ?")) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return "PAY-" + year + "-" + String.format("%05d", rs.getInt(1) + 1);
            }
        }
    }

    /**
     * Inserts one row into incoming_payments. Caller owns the transaction.
     * paymentMethod must be 'CASH', 'TRANSFER', or 'CARD'.
     */
    public long insert(Connection conn,
                       String     docNum,
                       LocalDate  docDate,
                       long       invoiceId,
                       long       bpId,
                       BigDecimal amount,
                       String     paymentMethod,
                       String     remarks,
                       long       createdBy) throws SQLException {

        String sql = """
            INSERT INTO incoming_payments
              (doc_num, doc_date, invoice_id, bp_id, amount,
               payment_method, remarks, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, docNum);
            ps.setDate(2, Date.valueOf(docDate));
            ps.setLong(3, invoiceId);
            ps.setLong(4, bpId);
            ps.setBigDecimal(5, amount);
            ps.setString(6, paymentMethod);
            ps.setString(7, remarks);
            ps.setLong(8, createdBy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }
}
