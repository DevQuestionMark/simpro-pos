package com.questionmark.simpropos.repository;

import com.questionmark.simpropos.model.ItemDto;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class ItemRepository {

    private static final String SQL_BY_CODE = """
        SELECT im.id, im.item_code, im.item_name, im.uom, im.price, im.image,
               COALESCE(iw.on_hand_qty, 0) AS on_hand_qty
        FROM   item_master im
        LEFT JOIN item_warehouse iw
               ON iw.item_id = im.id AND iw.warehouse_id = ?
        WHERE  im.item_code = ? AND im.is_active = 1
        """;

    private static final String SQL_ALL = """
        SELECT im.id, im.item_code, im.item_name, im.uom, im.price, im.image,
               COALESCE(iw.on_hand_qty, 0) AS on_hand_qty
        FROM   item_master im
        LEFT JOIN item_warehouse iw
               ON iw.item_id = im.id AND iw.warehouse_id = ?
        WHERE  im.is_active = 1
        ORDER  BY im.item_name
        LIMIT  500
        """;

    private static final String SQL_SEARCH = """
        SELECT im.id, im.item_code, im.item_name, im.uom, im.price, im.image,
               COALESCE(iw.on_hand_qty, 0) AS on_hand_qty
        FROM   item_master im
        LEFT JOIN item_warehouse iw
               ON iw.item_id = im.id AND iw.warehouse_id = ?
        WHERE  im.is_active = 1
          AND  (im.item_code LIKE ? OR im.item_name LIKE ?)
        ORDER  BY im.item_name
        LIMIT  100
        """;

    private final DataSource dataSource;

    @Inject
    public ItemRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Exact match on item_code (used for barcode/QR scanner input). */
    public Optional<ItemDto> findByCode(String itemCode, int warehouseId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_BY_CODE)) {
            ps.setInt(1, warehouseId);
            ps.setString(2, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Wildcard search by item_code or item_name. */
    public List<ItemDto> search(String query, int warehouseId) throws SQLException {
        String like = "%" + query + "%";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SEARCH)) {
            ps.setInt(1, warehouseId);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                List<ItemDto> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    /** Returns all active items for the given warehouse (up to 500). */
    public List<ItemDto> findAll(int warehouseId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ALL)) {
            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ItemDto> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    private ItemDto map(ResultSet rs) throws SQLException {
        return new ItemDto(
            rs.getLong("id"),
            rs.getString("item_code"),
            rs.getString("item_name"),
            rs.getString("uom"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("on_hand_qty"),
            rs.getString("image")
        );
    }
}
