# POS for `simpro` — JavaFX (Phase 1 plan)

## Objective
Build a **standalone JavaFX point-of-sale app** that posts cash sales **directly into the existing `simpro` MySQL database**, as A/R invoices + incoming payments, reusing the ERP's existing schema and posting behavior. Single terminal, single outlet, always-online. No offline mode.

This file covers the first slice only: **discovery → foundation → data layer → checkout → atomic finalize** — the point at which a cashier can ring up a sale and it persists correctly. Receipt printing, shift open/close UI, void/refund, and reports are a later plan.

---

## Ground rules (do not violate)
- The `simpro` DB is **owned by a Laravel ERP**. We are writing into it directly, so the JavaFX posting logic **must match what Laravel does** — same tables, same columns, same status values, same stock effect. Drift = corrupted ERP data.
- **Reuse existing sales tables.** A cash sale = `ar_invoices` + `ar_invoice_lines` + `incoming_payments`. **Do NOT create parallel `pos_sales` tables** — the ERP would not see those sales.
- **Do NOT invent column names.** Every column used in an insert/update must come from `SHOW CREATE TABLE` and/or the Laravel models. If unsure, stop and inspect — never assume.
- The only **new** table is `pos_shift` (cash session). Nothing else gets created.
- **Finalize is one transaction.** All inserts + the stock decrement commit together or roll back together. All relevant tables are InnoDB, so this is safe.
- **Don't touch procurement.** Read `item_master` / `item_warehouse` only. Do not write to PO / GRPO / GRR / stock-counting tables or their Laravel code.
- No offline store, no sync, no multi-terminal, no multi-outlet. Anything pointing that way is out of scope.

---

## Stack & conventions
- **Java 21 (LTS)**, **JavaFX 21**, **Maven**. (Adjust if the existing JavaFX project differs — match it.)
- Architecture: **MVC + Service + Repository**, **Guice** for DI, **FXML** views.
- **Plain JDBC** repositories via `mysql-connector-j` (no ORM) so we keep explicit control of the finalize transaction. **HikariCP** for pooling.
- Config in `pos.properties` (gitignored):
  ```
  db.url, db.user, db.pass
  pos.outlet_id, pos.terminal_id, pos.warehouse_id
  pos.default_bp_id        # walk-in customer
  tax.ppn_rate=0.11        # confirm 11% vs 12% bracket
  tax.ppn_inclusive=true   # retail prices include PPN
  ```
- Money as `BigDecimal` everywhere. No `double` for amounts.

---

## Phase 0 — Discovery & posting parity (DO THIS FIRST, before writing app code)
The correctness of everything below depends on this. Output a short `POSTING_PARITY.md` and stop for review before Phase 4 implementation.

- [ ] Run `SHOW CREATE TABLE` for: `item_master`, `item_warehouse`, `ar_invoices`, `ar_invoice_lines`, `incoming_payments`, `users`, `business_partners`, `warehouses`, `sales_orders`, `sales_order_lines`. Record columns, types, keys, FKs, nullability, defaults.
- [ ] In the Laravel repo, find the code that **posts a sale** (controller/service/action/model observers for AR invoice + incoming payment). Document the **exact** insert sequence and every column value it sets — totals, tax, posting/due dates, status flags, BP balance updates.
- [ ] Determine whether `ar_invoices` can be created **standalone** or requires a `sales_orders` parent. Mirror whichever Laravel does for a direct sale.
- [ ] Document **how stock is decremented** on a sale: does Laravel mutate an on-hand column on `item_warehouse`, write a movement row, recalc committed/available? Reproduce that exactly. (No dedicated stock-movement table appears in the schema, so confirm whether it's a column update.)
- [ ] Document the **document-numbering scheme** for `ar_invoices` (and `incoming_payments`): is it `AUTO_INCREMENT`, a Laravel-side counter, a per-series doc_num? POS must use the **same** mechanism, generated **inside** the finalize transaction.
- [ ] Confirm the **password hashing** on `users` (Laravel = bcrypt) so login can verify against it.

**Acceptance:** `POSTING_PARITY.md` describes a sequence that, for one cash sale, produces a row-set **identical** to one created through the Laravel UI.

---

## Phase 1 — Foundation
- [ ] Maven project, JavaFX + Guice + HikariCP + mysql-connector-j + a bcrypt lib.
- [ ] Guice modules; `DataSource` built from `pos.properties`.
- [ ] App shell window + navigation.
- [ ] **Cashier login** against `users` (verify bcrypt hash). Hold the logged-in cashier + `terminal_id`/`outlet_id` in session.

**Acceptance:** App launches, an existing ERP user logs in, DB connectivity verified on startup.

---

## Phase 2 — Data layer
- [ ] Read repositories: `ItemRepository` (lookup by **barcode**, search by code/name, return selling price + UOM + tax flag from `item_master`), `WarehouseRepository`, `BusinessPartnerRepository` (resolve walk-in `default_bp_id`).
- [ ] Write repositories: `ArInvoiceRepository`, `ArInvoiceLineRepository`, `IncomingPaymentRepository`, and a `StockRepository` whose decrement method follows `POSTING_PARITY.md` **exactly**. These expose methods that take an open `Connection` (so finalize controls the transaction).
- [ ] **New table** `pos_shift` migration: `id, outlet_id, terminal_id, cashier_id, opened_at, opening_float, closed_at, counted_cash, expected_cash, variance, status`. Add `ShiftRepository`.

**Acceptance:** Query an item by barcode → correct price/UOM. `pos_shift` migration applies cleanly. No sales written yet.

---

## Phase 3 — Checkout screen
- [ ] FXML checkout view: **scan/search field** (HID scanner = text input ending in Enter; just keep this field focused — no driver), cart `TableView` (item, qty, unit price, line discount, line total), editable qty, remove line, clear cart, hold/recall.
- [ ] Live totals: **subtotal → discount → PPN → grand total**, honoring `tax.ppn_inclusive` (back out PPN from the price when inclusive; add it when exclusive).
- [ ] Default customer = walk-in BP. Optional BP picker.

**Acceptance:** Build a multi-line cart; PPN math is correct for both inclusive and exclusive config. Still no DB writes.

---

## Phase 4 — Payment & atomic finalize
- [ ] Tender dialog: **cash** (+ change calc) and **QRIS-manual** (mark paid, store ref). Multi-tender allowed.
- [ ] **Finalize = single JDBC transaction** (`setAutoCommit(false)`):
  1. Generate the invoice document number using the **same** mechanism as Laravel (counter row via `SELECT ... FOR UPDATE` if applicable), inside this transaction.
  2. Insert `ar_invoices` header + `ar_invoice_lines` (per `POSTING_PARITY.md`).
  3. Insert `incoming_payments` for each tender.
  4. Apply the **stock decrement** exactly as documented.
  5. Link the sale to the open `pos_shift`.
  6. Commit. On **any** exception → `rollback()` and surface the error; never leave partial rows.

**Acceptance:**
- A POS sale produces a row-set **identical** to the same sale entered in the Laravel UI (compare directly).
- Stock is reduced **once**, matching Laravel's effect.
- Killing the app mid-finalize leaves **zero** partial rows (fully atomic).

---

## Out of scope (separate plan.md)
Receipt / ESC-POS printing + cash-drawer kick, shift open/close UI and X/Z reports, void/refund (stock reversal), daily sales reports, multi-terminal, multi-outlet, offline-first, live QRIS/EDC integration, price lists / promos, loyalty.

## Assumptions to confirm
- PPN **inclusive** at a configurable rate — confirm 11% vs the 12% bracket for your goods.
- A walk-in business partner exists (or seed one) for `default_bp_id`.
- Java 21 / JavaFX 21 / Maven — change to match the existing JavaFX project if it differs.
- Cash sale posts as a **standalone A/R invoice** (Phase 0 confirms whether a `sales_orders` parent is required).
