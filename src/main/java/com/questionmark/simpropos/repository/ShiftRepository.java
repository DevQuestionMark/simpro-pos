package com.questionmark.simpropos.repository;

import com.questionmark.simpropos.model.ShiftDto;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;



@Singleton
public class ShiftRepository {

    private final DataSource dataSource;

    @Inject
    public ShiftRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ShiftDto open(int outletId, int terminalId, long cashierId, BigDecimal openingFloat)
            throws SQLException {
        String sql = """
            INSERT INTO pos_shift
              (outlet_id, terminal_id, cashier_id, opened_at, opening_float, status)
            VALUES (?, ?, ?, NOW(), ?, 'OPEN')
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, outletId);
            ps.setInt(2, terminalId);
            ps.setLong(3, cashierId);
            ps.setBigDecimal(4, openingFloat);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return findById(keys.getLong(1)).orElseThrow();
            }
        }
    }

    public Optional<ShiftDto> findOpenShift(int outletId, int terminalId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM pos_shift WHERE outlet_id = ? AND terminal_id = ? AND status = 'OPEN' LIMIT 1")) {
            ps.setInt(1, outletId);
            ps.setInt(2, terminalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public void close(long shiftId, BigDecimal countedCash, BigDecimal expectedCash)
            throws SQLException {
        BigDecimal variance = countedCash.subtract(expectedCash);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE pos_shift SET closed_at = NOW(), counted_cash = ?, expected_cash = ?, " +
                 "variance = ?, status = 'CLOSED' WHERE id = ?")) {
            ps.setBigDecimal(1, countedCash);
            ps.setBigDecimal(2, expectedCash);
            ps.setBigDecimal(3, variance);
            ps.setLong(4, shiftId);
            ps.executeUpdate();
        }
    }

    /** Total CASH incoming_payments received since the shift was opened. */
    public BigDecimal sumCashReceiptsSince(LocalDateTime since) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COALESCE(SUM(amount), 0) FROM incoming_payments " +
                 "WHERE payment_method = 'CASH' AND created_at >= ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private Optional<ShiftDto> findById(long id) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM pos_shift WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private ShiftDto map(ResultSet rs) throws SQLException {
        Timestamp closedTs      = rs.getTimestamp("closed_at");
        BigDecimal countedCash  = rs.getBigDecimal("counted_cash");
        BigDecimal expectedCash = rs.getBigDecimal("expected_cash");
        BigDecimal variance     = rs.getBigDecimal("variance");
        return new ShiftDto(
            rs.getLong("id"),
            rs.getInt("outlet_id"),
            rs.getInt("terminal_id"),
            rs.getLong("cashier_id"),
            rs.getTimestamp("opened_at").toLocalDateTime(),
            rs.getBigDecimal("opening_float"),
            closedTs != null ? closedTs.toLocalDateTime() : null,
            countedCash,
            expectedCash,
            variance,
            rs.getString("status")
        );
    }
}
