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

---

## Elemen Java yang Diimplementasikan

### 1. Stream — InputStream & OutputStream

**InputStream** digunakan saat aplikasi startup untuk membaca file konfigurasi `pos.properties`:

```java
// AppConfig.java
Path path = Path.of("pos.properties");
try (InputStream is = Files.newInputStream(path)) {
    props.load(is);
}
```

**OutputStream** digunakan oleh `ReceiptExporter` untuk menulis struk transaksi ke file `.txt` menggunakan `FileOutputStream` dan `PrintStream`:

```java
// ReceiptExporter.java
Path file = dir.resolve(invoiceNum + ".txt");

try (FileOutputStream fos = new FileOutputStream(file.toFile());
     PrintStream      ps  = new PrintStream(fos, true, StandardCharsets.UTF_8)) {

    ps.println(center("SIMPRO POS", WIDTH));
    ps.println("No. Invoice  : " + invoiceNum);
    ps.println("Pelanggan    : " + bpName);
    // ...
    ps.printf("%-20s %s%n", "TOTAL", formatRp(grandTotal));
}
```

Setiap transaksi berhasil, struk otomatis disimpan ke folder `receipts/INV-YYYY-NNNNN.txt`.

---

### 2. Connection — MySQL via JDBC

Koneksi ke database MySQL dikelola oleh **HikariCP** (connection pool) dan diakses melalui `javax.sql.DataSource`. Setiap repository membuka `Connection` eksplisit dari pool:

```java
// AppModule.java — konfigurasi pool koneksi
HikariConfig hc = new HikariConfig();
hc.setJdbcUrl(config.dbUrl());   // jdbc:mysql://103.103.20.170:3306/simpro
hc.setUsername(config.dbUser());
hc.setPassword(config.dbPass());
hc.setMaximumPoolSize(5);
return new HikariDataSource(hc);
```

```java
// Contoh penggunaan Connection di repository (ItemRepository.java)
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(SQL_ALL)) {
    ps.setInt(1, warehouseId);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(map(rs));
    }
}
```

Transaksi penjualan menggunakan `Connection` dengan `autoCommit=false` untuk memastikan atomicity — jika salah satu langkah gagal, seluruh transaksi di-rollback:

```java
// SaleService.java — transaksi atomic
conn.setAutoCommit(false);
try {
    // 1. Buat invoice
    // 2. Tulis baris invoice + kurangi stok
    // 3. Buat pembayaran
    // 4. Tandai invoice lunas
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
    throw e;
}
```

---

### 3. Thread

**ClockThread** — subclass `Thread` yang berjalan di background untuk memperbarui jam real-time di status bar setiap detik. Nama thread ditampilkan sebagai **badge hijau** di status bar selama thread aktif, dan hilang saat logout.

Tampilan di status bar:
```
Login sebagai: admin  |  Gudang: 1  |  Terminal: 1    [● clock-thread]  |  08:45:23
```

```java
// MainController.java
private class ClockThread extends Thread {

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");
    private volatile boolean running = true;

    ClockThread() {
        setName("clock-thread");
        setDaemon(true); // mati otomatis saat app ditutup
    }

    @Override
    public void run() {
        // Tampilkan nama thread di badge UI saat mulai berjalan
        String badge = "● " + Thread.currentThread().getName();
        Platform.runLater(() -> threadBadge.setText(badge));

        while (running) {
            String time = LocalTime.now().format(TIME_FMT);
            // Update UI harus dilakukan di FX Application Thread
            Platform.runLater(() -> clockLabel.setText(time));
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Hapus badge saat thread berhenti (logout)
        Platform.runLater(() -> threadBadge.setText(""));
    }

    void stopClock() { running = false; interrupt(); }
}
```

**item-loader-thread** — Thread background untuk memuat daftar item dari database tanpa membekukan UI:

```java
// CheckoutController.java
Thread itemLoaderThread = new Thread(() -> {
    try {
        List<ItemDto> items = itemRepo.findAll(session.getWarehouseId());
        // Kembali ke FX Application Thread untuk update UI
        Platform.runLater(() -> {
            allItems = items;
            filterCards("");
        });
    } catch (SQLException e) {
        Platform.runLater(() -> showError("Gagal memuat item: " + e.getMessage()));
    }
}, "item-loader-thread");
itemLoaderThread.setDaemon(true);
itemLoaderThread.start();
```

---

### 4. Images — JavaFX ImageView

**Logo aplikasi** ditampilkan di layar login dan top bar menggunakan `ImageView` dengan file PNG yang dibundle dalam JAR:

```xml
<!-- login.fxml -->
<ImageView fitHeight="72" preserveRatio="true">
    <image><Image url="@images/logo.png"/></image>
</ImageView>

<!-- main.fxml (top bar) -->
<ImageView fitHeight="30" preserveRatio="true">
    <image><Image url="@images/logo-light.png"/></image>
</ImageView>
```

**Gambar produk** pada setiap kartu item dimuat secara asinkron dari URL storage (`backgroundLoading=true`) sehingga UI tidak terblokir. Jika gambar gagal dimuat, ditampilkan placeholder huruf pertama nama item:

```java
// CheckoutController.java
String url = config.storageUrl() + "/" + imgPath;
Image img = new Image(url, 160, 100, false, true, /* backgroundLoading= */ true);
ImageView iv = new ImageView(img);
iv.setFitWidth(160);
iv.setFitHeight(100);

// Sembunyikan ImageView jika gambar gagal dimuat
img.errorProperty().addListener((obs, ov, err) -> {
    if (err) iv.setVisible(false);
});
```
