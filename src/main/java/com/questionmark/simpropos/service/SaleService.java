package com.questionmark.simpropos.service;

import com.questionmark.simpropos.model.CartLine;
import com.questionmark.simpropos.repository.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Executes a POS cash sale inside a single JDBC transaction.
 *
 * Sequence (mirrors Laravel's posting logic):
 *   1. ar_invoices INSERT          (status=OPEN, so_id=NULL)
 *   2. ar_invoice_lines INSERT     (one row per cart line)
 *   3. item_warehouse UPDATE -qty  (stock decrement, one row per cart line)
 *   4. incoming_payments INSERT
 *   5. ar_invoices UPDATE          (status=PAID, paid_amount=total)
 *
 * Any failure causes a full rollback; caller receives the original SQLException.
 */
@Singleton
public class SaleService {

    public record SaleResult(String invoiceDocNum, String paymentDocNum) {}

    private final DataSource                  ds;
    private final ArInvoiceRepository         invoiceRepo;
    private final ArInvoiceLineRepository     lineRepo;
    private final IncomingPaymentRepository   paymentRepo;
    private final StockRepository             stockRepo;

    @Inject
    public SaleService(DataSource ds,
                       ArInvoiceRepository invoiceRepo,
                       ArInvoiceLineRepository lineRepo,
                       IncomingPaymentRepository paymentRepo,
                       StockRepository stockRepo) {
        this.ds          = ds;
        this.invoiceRepo = invoiceRepo;
        this.lineRepo    = lineRepo;
        this.paymentRepo = paymentRepo;
        this.stockRepo   = stockRepo;
    }

    /**
     * @param lines         Cart lines (snapshot taken before calling)
     * @param bpId          Business partner id
     * @param grandTotal    Amount to bill and record as paid
     * @param paymentMethod 'CASH' or 'TRANSFER' (QRIS maps to TRANSFER)
     * @param remarks       Optional remarks stored in incoming_payments (may be null)
     * @param createdBy     Logged-in user id
     * @param warehouseId   Outlet warehouse
     */
    public SaleResult post(List<CartLine> lines,
                           long           bpId,
                           BigDecimal     grandTotal,
                           String         paymentMethod,
                           String         remarks,
                           long           createdBy,
                           int            warehouseId) throws SQLException {

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int       year  = LocalDate.now().getYear();
                LocalDate today = LocalDate.now();

                // 1 — invoice header
                String invNum = invoiceRepo.generateDocNum(conn, year);
                long   invId  = invoiceRepo.insert(conn, invNum, today,
                                                   bpId, warehouseId, grandTotal, createdBy);

                // 2+3 — lines and stock
                for (CartLine line : lines) {
                    BigDecimal qty = BigDecimal.valueOf(line.getQty());
                    lineRepo.insert(conn, invId, line.getItemId(), qty, line.getUnitPrice());
                    stockRepo.decrement(conn, line.getItemId(), warehouseId, qty);
                }

                // 4 — payment record
                String payNum = paymentRepo.generateDocNum(conn, year);
                paymentRepo.insert(conn, payNum, today, invId, bpId, grandTotal,
                                   paymentMethod, remarks == null ? "" : remarks, createdBy);

                // 5 — mark invoice paid
                invoiceRepo.markPaid(conn, invId, grandTotal);

                conn.commit();
                return new SaleResult(invNum, payNum);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
