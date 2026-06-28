# Simpro POS

Aplikasi Point of Sale desktop berbasis JavaFX yang terhubung langsung ke database MySQL [Simpro ERP](https://simpro.rifkysaputram.net). Transaksi penjualan tunai ditulis secara atomic ke tabel `ar_invoices`, `ar_invoice_lines`, dan `incoming_payments`.

## Persyaratan

| Kebutuhan | Versi |
|-----------|-------|
| Java (JDK) | 21 atau lebih baru |
| Maven | 3.8+ (atau gunakan `mvnw` yang sudah disertakan) |
| MySQL | 8.0+ (database Simpro ERP) |
| Akses jaringan | IP komputer harus di-grant ke MySQL server |

## Konfigurasi

Buat file `pos.properties` di direktori kerja (folder tempat app dijalankan). File ini **tidak ikut Git** (sudah ada di `.gitignore`) karena memuat kredensial.

```properties
# ── Database ──────────────────────────────────────────────────────────
db.url=jdbc:mysql://103.103.20.170:3306/simpro?serverTimezone=Asia/Jakarta&useSSL=false&allowPublicKeyRetrieval=true
db.user=simpro
db.pass=Qwerty123#

# ── POS identity ──────────────────────────────────────────────────────
pos.outlet_id=1
pos.terminal_id=1
pos.warehouse_id=1
pos.default_bp_id=2          # id business_partner untuk pelanggan walk-in

# ── Fitur ─────────────────────────────────────────────────────────────
pos.shift_enabled=false       # true = aktifkan manajemen shift kasir
pos.storage_url=https://simpro.rifkysaputram.net/storage   # URL base gambar item

# ── Pajak ─────────────────────────────────────────────────────────────
tax.ppn_rate=11               # persentase PPN (angka saja, tanpa %)
tax.ppn_inclusive=false       # false = eksklusif (ditambah ke subtotal)
                              # true  = inklusif (sudah termasuk di harga)
```


## Menjalankan Aplikasi

### Development (langsung dari source)

```bash
./mvnw javafx:run
```

### Build & jalankan JAR

```bash
./mvnw package -DskipTests

# Jalankan
java -jar target/simpro-pos-1.0-SNAPSHOT.jar
```

> Pastikan `pos.properties` berada di direktori yang sama saat app dijalankan.

## Skema Database Tambahan

App akan otomatis membuat tabel `pos_shift` saat pertama kali dijalankan (DDL dijalankan oleh `DatabaseMigration` saat startup). Tidak ada migrasi manual yang perlu dilakukan.

Tabel yang **dibaca** dari ERP (harus sudah ada):

| Tabel | Keterangan |
|-------|------------|
| `users` | Login kasir (username + bcrypt password) |
| `item_master` | Daftar item / produk |
| `item_warehouse` | Stok per gudang |
| `business_partners` | Data pelanggan |
| `warehouses` | Data gudang |

Tabel yang **ditulis** oleh POS:

| Tabel | Keterangan |
|-------|------------|
| `ar_invoices` | Header invoice penjualan |
| `ar_invoice_lines` | Detail baris invoice |
| `incoming_payments` | Pembayaran |
| `item_warehouse` | Dekremen stok (`on_hand_qty`) |
| `business_partners` | Pelanggan baru (dari fitur "+ Baru") |
| `pos_shift` | Data shift kasir (jika diaktifkan) |

## Fitur

- Login dengan akun ERP (username + bcrypt password)
- Grid item dengan gambar, pencarian real-time, dan scan barcode
- Keranjang dengan kontrol qty inline (tambah / kurang / hapus)
- Pilih atau buat pelanggan baru langsung dari POS
- Metode pembayaran: Tunai, QRIS, Transfer
- Hitung kembalian untuk pembayaran tunai
- PPN eksklusif atau inklusif (dikonfigurasi via `pos.properties`)
- Manajemen shift kasir opsional (aktifkan via `pos.shift_enabled=true`)
- Transaksi atomic — invoice, pembayaran, dan stok diproses dalam satu DB transaction

## Struktur Proyek

```
src/main/java/com/questionmark/simpropos/
├── config/          # Konfigurasi Guice DI & pembaca pos.properties
├── db/              # DatabaseMigration (DDL pos_shift)
├── model/           # DTO & model (ItemDto, CartLine, dll)
├── repository/      # Akses database (JDBC)
├── service/         # SaleService — logika transaksi atomic
├── session/         # AppSession — state login & shift
└── ui/
    ├── checkout/    # Layar utama POS
    ├── customer/    # Dialog buat pelanggan baru
    ├── login/       # Layar login
    ├── main/        # Shell utama (topbar + status)
    ├── payment/     # Dialog pembayaran
    └── shift/       # Dialog buka/tutup shift
```

## Teknologi

- **JavaFX 21** — UI desktop
- **Guice 6** — Dependency injection
- **HikariCP 5** — Connection pool
- **MySQL Connector/J 8** — Driver database
- **jBCrypt** — Verifikasi password Laravel bcrypt (`$2y$`)
