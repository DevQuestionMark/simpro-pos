package com.questionmark.simpropos.db;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Singleton
public class DatabaseMigration {

    private static final String CREATE_POS_SHIFT = """
        CREATE TABLE IF NOT EXISTS `pos_shift` (
          `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
          `outlet_id`      int NOT NULL,
          `terminal_id`    int NOT NULL,
          `cashier_id`     bigint unsigned NOT NULL,
          `opened_at`      datetime NOT NULL,
          `opening_float`  decimal(18,2) NOT NULL DEFAULT '0.00',
          `closed_at`      datetime DEFAULT NULL,
          `counted_cash`   decimal(18,2) DEFAULT NULL,
          `expected_cash`  decimal(18,2) DEFAULT NULL,
          `variance`       decimal(18,2) DEFAULT NULL,
          `status`         enum('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
          PRIMARY KEY (`id`),
          KEY `pos_shift_cashier_id` (`cashier_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private final DataSource dataSource;

    @Inject
    public DatabaseMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void run() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(CREATE_POS_SHIFT);
        }
    }
}
