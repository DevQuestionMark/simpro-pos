package com.questionmark.simpropos.repository;

import com.questionmark.simpropos.model.BusinessPartnerDto;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class BusinessPartnerRepository {

    private final DataSource dataSource;

    @Inject
    public BusinessPartnerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<BusinessPartnerDto> findById(long id) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, code, name FROM business_partners WHERE id = ? AND is_active = 1")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<BusinessPartnerDto> findCustomers() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, code, name FROM business_partners " +
                 "WHERE card_type IN ('CUSTOMER','BOTH') AND is_active = 1 ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<BusinessPartnerDto> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    /**
     * Inserts a new CUSTOMER business partner inside a single transaction.
     * Code is auto-generated as CUS-NNNNN (based on count of existing CUS- codes).
     */
    public BusinessPartnerDto insertCustomer(String name, String phone,
                                             String email, String address) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Generate code atomically inside the transaction
                String code;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM business_partners WHERE code LIKE 'CUS-%'");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    code = "CUS-" + String.format("%05d", rs.getInt(1) + 1);
                }

                String sql = "INSERT INTO business_partners " +
                    "(code, name, card_type, phone, email, address, is_active, created_at, updated_at) " +
                    "VALUES (?, ?, 'CUSTOMER', ?, ?, ?, 1, NOW(), NOW())";

                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, code);
                    ps.setString(2, name.trim());
                    ps.setString(3, blank2null(phone));
                    ps.setString(4, blank2null(email));
                    ps.setString(5, blank2null(address));
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        conn.commit();
                        return new BusinessPartnerDto(keys.getLong(1), code, name.trim());
                    }
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static String blank2null(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private BusinessPartnerDto map(ResultSet rs) throws SQLException {
        return new BusinessPartnerDto(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name")
        );
    }
}
