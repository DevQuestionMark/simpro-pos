package com.questionmark.simpropos.repository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Singleton
public class StockRepository {

    @Inject
    public StockRepository() {}

    /**
     * Returns current on_hand_qty for an item at a warehouse.
     * Used for pre-transaction stock check (informational, not a lock).
     */
    public BigDecimal getOnHand(Connection conn, long itemId, int warehouseId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT on_hand_qty FROM item_warehouse WHERE item_id = ? AND warehouse_id = ?")) {
            ps.setLong(1, itemId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("on_hand_qty") : BigDecimal.ZERO;
            }
        }
    }

    /**
     * Decrements on_hand_qty. Must be called inside the finalize transaction.
     * Matches Laravel's: ItemWarehouse::where(...)->decrement('on_hand_qty', qty)
     */
    public void decrement(Connection conn, long itemId, int warehouseId, BigDecimal qty)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE item_warehouse SET on_hand_qty = on_hand_qty - ?, updated_at = NOW() " +
                "WHERE item_id = ? AND warehouse_id = ?")) {
            ps.setBigDecimal(1, qty);
            ps.setLong(2, itemId);
            ps.setInt(3, warehouseId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException(
                    "No item_warehouse row for item_id=" + itemId + " warehouse_id=" + warehouseId);
            }
        }
    }

    /**
     * Increments on_hand_qty (used for void/refund — out of scope for Phase 1).
     */
    public void increment(Connection conn, long itemId, int warehouseId, BigDecimal qty)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE item_warehouse SET on_hand_qty = on_hand_qty + ?, updated_at = NOW() " +
                "WHERE item_id = ? AND warehouse_id = ?")) {
            ps.setBigDecimal(1, qty);
            ps.setLong(2, itemId);
            ps.setInt(3, warehouseId);
            ps.executeUpdate();
        }
    }
}
