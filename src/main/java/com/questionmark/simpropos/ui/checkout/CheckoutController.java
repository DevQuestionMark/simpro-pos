package com.questionmark.simpropos.ui.checkout;

import com.questionmark.simpropos.config.AppConfig;
import com.questionmark.simpropos.model.BusinessPartnerDto;
import com.questionmark.simpropos.model.CartLine;
import com.questionmark.simpropos.model.ItemDto;
import com.questionmark.simpropos.model.PaymentResult;
import com.questionmark.simpropos.repository.BusinessPartnerRepository;
import com.questionmark.simpropos.repository.ItemRepository;
import com.questionmark.simpropos.service.SaleService;
import com.questionmark.simpropos.session.AppSession;
import com.questionmark.simpropos.ui.customer.CreateCustomerDialog;
import com.questionmark.simpropos.ui.payment.PaymentDialog;
import com.questionmark.simpropos.util.ReceiptExporter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.Observable;
import java.io.IOException;
import java.nio.file.Path;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class CheckoutController {

    // ── FXML fields ─────────────────────────────────────────────────────────
    @FXML private TextField  searchField;
    @FXML private Label      errorLabel;
    @FXML private FlowPane   cardsPane;
    @FXML private ScrollPane cartScroll;
    @FXML private VBox       cartItemsPane;
    @FXML private Label      customerLabel;
    @FXML private Label      subtotalLabel;
    @FXML private Label      ppnRateLabel;
    @FXML private Label      ppnLabel;
    @FXML private Label      grandTotalLabel;
    @FXML private Button     payButton;

    // ── Dependencies ─────────────────────────────────────────────────────────
    private final AppSession                session;
    private final AppConfig                 config;
    private final ItemRepository            itemRepo;
    private final BusinessPartnerRepository bpRepo;
    private final SaleService               saleService;

    // ── State ─────────────────────────────────────────────────────────────────
    private List<ItemDto> allItems = new ArrayList<>();

    private final ObservableList<CartLine> cart = FXCollections.observableArrayList(
        (CartLine line) -> new Observable[]{ line.qtyProperty(), line.unitPriceProperty() }
    );

    private long   activeBpId;
    private String activeBpName = "Walk-in";

    private static final DecimalFormat NUM_FMT = new DecimalFormat("#,##0");

    // ── Constructor ───────────────────────────────────────────────────────────
    @Inject
    public CheckoutController(AppSession session, AppConfig config,
                              ItemRepository itemRepo, BusinessPartnerRepository bpRepo,
                              SaleService saleService) {
        this.session     = session;
        this.config      = config;
        this.itemRepo    = itemRepo;
        this.bpRepo      = bpRepo;
        this.saleService = saleService;
    }

    // ── Initialize ────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        // Load default BP name
        activeBpId = session.getDefaultBpId();
        try {
            bpRepo.findById(activeBpId).ifPresent(bp -> {
                activeBpName = bp.name();
                customerLabel.setText(bp.name());
            });
        } catch (SQLException ignored) {}

        ppnRateLabel.setText("PPN (" + config.ppnRate().stripTrailingZeros().toPlainString() + "%)");

        // Tampilkan indikator loading sementara item dimuat di background
        Label loadingLabel = new Label("Memuat item...");
        loadingLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13;");
        cardsPane.getChildren().add(loadingLabel);

        // ── Background Thread: muat item dari DB agar UI tidak freeze ─────
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

        // Cart listener → rebuild rows + totals
        cart.addListener((ListChangeListener<CartLine>) c -> {
            rebuildCartPane();
            recalcTotals();
        });
        recalcTotals();

        // Search field: live filter on type, barcode scan on Enter
        searchField.textProperty().addListener((obs, ov, nv) -> filterCards(nv));
        searchField.setOnKeyPressed(evt -> {
            if (evt.getCode() == KeyCode.ENTER) onScanEnter();
        });

        Platform.runLater(searchField::requestFocus);
    }

    // ── Scanner / search ──────────────────────────────────────────────────────

    private void onScanEnter() {
        String code = searchField.getText().trim();
        if (code.isEmpty()) return;
        try {
            itemRepo.findByCode(code, session.getWarehouseId())
                    .ifPresentOrElse(item -> {
                        addToCart(item);
                        searchField.clear();
                    }, () -> showError("Item tidak ditemukan: " + code));
        } catch (SQLException e) {
            showError("DB error: " + e.getMessage());
        }
    }

    // ── Item cards ────────────────────────────────────────────────────────────

    private void filterCards(String query) {
        cardsPane.getChildren().clear();
        List<ItemDto> filtered = query == null || query.isBlank() ? allItems :
            allItems.stream()
                .filter(i -> i.itemCode().toLowerCase().contains(query.toLowerCase()) ||
                             i.itemName().toLowerCase().contains(query.toLowerCase()))
                .toList();

        if (filtered.isEmpty()) {
            Label empty = new Label("Tidak ada item ditemukan");
            empty.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13;");
            cardsPane.getChildren().add(empty);
        } else {
            filtered.forEach(item -> cardsPane.getChildren().add(buildCard(item)));
        }
    }

    private VBox buildCard(ItemDto item) {
        // Image area
        Node imgArea = buildImageArea(item);

        // Name
        Label nameLabel = new Label(item.itemName());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(140);
        nameLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        nameLabel.setTextAlignment(TextAlignment.LEFT);

        // Code
        Label codeLabel = new Label(item.itemCode());
        codeLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #94a3b8;");

        // Price
        Label priceLabel = new Label(formatRp(item.price()));
        priceLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #E11C24;");

        VBox info = new VBox(3, nameLabel, codeLabel, priceLabel);
        info.setPadding(new Insets(8, 10, 10, 10));

        VBox card = new VBox(0, imgArea, info);
        card.setPrefWidth(160);
        card.setMaxWidth(160);
        card.getStyleClass().add("item-card");
        card.setOnMouseClicked(e -> addToCart(item));

        return card;
    }

    private Node buildImageArea(ItemDto item) {
        StackPane area = new StackPane();
        area.setPrefSize(160, 100);
        area.setMinSize(160, 100);
        area.setMaxSize(160, 100);
        area.setStyle("-fx-background-color: #eef1f5; -fx-background-radius: 12 12 0 0;");

        String imgPath = item.image();
        if (imgPath != null && !imgPath.isBlank() && !config.storageUrl().isBlank()) {
            String url = config.storageUrl() + "/" + imgPath;
            Image img = new Image(url, 160, 100, false, true, true);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(160);
            iv.setFitHeight(100);
            iv.setPreserveRatio(false);
            img.errorProperty().addListener((obs, ov, err) -> {
                if (err) iv.setVisible(false);
            });
            area.getChildren().add(iv);
        } else {
            // Placeholder: first letter of item name
            Label placeholder = new Label(item.itemName().substring(0, 1).toUpperCase());
            placeholder.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #cbd5e1;");
            area.getChildren().add(placeholder);
        }
        return area;
    }

    // ── Cart operations ───────────────────────────────────────────────────────

    private void addToCart(ItemDto item) {
        for (CartLine line : cart) {
            if (line.getItemId() == item.id()) {
                line.setQty(line.getQty() + 1);
                rebuildCartPane();
                recalcTotals();
                return;
            }
        }
        cart.add(new CartLine(item));
    }

    @FXML
    private void onClearCart() {
        if (cart.isEmpty()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Hapus semua item dari keranjang?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> cart.clear());
    }

    private void rebuildCartPane() {
        cartItemsPane.getChildren().clear();
        if (cart.isEmpty()) {
            Label empty = new Label("Keranjang kosong");
            empty.setStyle("-fx-text-fill: #475569; -fx-font-size: 12;");
            empty.setPadding(new Insets(8, 0, 0, 0));
            cartItemsPane.getChildren().add(empty);
            return;
        }
        for (CartLine line : cart) {
            cartItemsPane.getChildren().add(buildCartRow(line));
        }
        // Scroll to bottom so newly added items are visible
        Platform.runLater(() -> cartScroll.setVvalue(1.0));
    }

    private HBox buildCartRow(CartLine line) {
        Label nameLabel = new Label(line.getItemName());
        nameLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12;");
        nameLabel.setMaxWidth(80);
        nameLabel.setMinWidth(80);
        nameLabel.setWrapText(false);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Button minusBtn = qtyBtn("−");
        minusBtn.setOnAction(e -> {
            if (line.getQty() > 1) {
                line.setQty(line.getQty() - 1);
            } else {
                cart.remove(line);
                return;
            }
            rebuildCartPane();
            recalcTotals();
        });

        Label qtyLabel = new Label(String.valueOf(line.getQty()));
        qtyLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold;");
        qtyLabel.setMinWidth(22);
        qtyLabel.setAlignment(Pos.CENTER);

        Button plusBtn = qtyBtn("+");
        plusBtn.setOnAction(e -> {
            line.setQty(line.getQty() + 1);
            rebuildCartPane();
            recalcTotals();
        });

        Label totalLabel = new Label(formatRp(line.lineTotal()));
        totalLabel.setStyle("-fx-text-fill: #fca5a5; -fx-font-size: 12; -fx-font-weight: bold;");
        totalLabel.setMinWidth(75);
        totalLabel.setAlignment(Pos.CENTER_RIGHT);

        Button removeBtn = new Button("×");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b;" +
                           "-fx-font-size: 14; -fx-padding: 0 4; -fx-cursor: hand;");
        removeBtn.setOnMouseEntered(e -> removeBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #ef4444;" +
            "-fx-font-size: 14; -fx-padding: 0 4; -fx-cursor: hand;"));
        removeBtn.setOnMouseExited(e -> removeBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #64748b;" +
            "-fx-font-size: 14; -fx-padding: 0 4; -fx-cursor: hand;"));
        removeBtn.setOnAction(e -> cart.remove(line));

        HBox row = new HBox(6, nameLabel, minusBtn, qtyLabel, plusBtn, totalLabel, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 0, 5, 0));
        row.setStyle("-fx-border-color: #24405f; -fx-border-width: 0 0 1 0;");
        return row;
    }

    private static Button qtyBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #24405f; -fx-text-fill: white;" +
                     "-fx-font-size: 13; -fx-padding: 2 8; -fx-background-radius: 6;" +
                     "-fx-cursor: hand; -fx-min-width: 28;");
        return btn;
    }

    // ── Customer create / pick ────────────────────────────────────────────────

    @FXML
    private void onNewCustomer() {
        CreateCustomerDialog.open(bpRepo).ifPresent(bp -> {
            activeBpId   = bp.id();
            activeBpName = bp.name();
            customerLabel.setText(bp.name());
        });
    }

    @FXML
    private void onChangeBp() {
        List<BusinessPartnerDto> customers;
        try {
            customers = bpRepo.findCustomers();
        } catch (SQLException e) {
            showError("Gagal memuat pelanggan: " + e.getMessage());
            return;
        }

        Dialog<BusinessPartnerDto> dialog = new Dialog<>();
        dialog.setTitle("Pilih Pelanggan");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ListView<BusinessPartnerDto> listView = new ListView<>();
        listView.getItems().setAll(customers);
        listView.setPrefSize(400, 300);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(BusinessPartnerDto bp, boolean empty) {
                super.updateItem(bp, empty);
                setText(empty || bp == null ? null : bp.code() + "  —  " + bp.name());
            }
        });

        dialog.getDialogPane().setContent(listView);
        dialog.setResultConverter(btn ->
            btn == ButtonType.OK ? listView.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().filter(bp -> bp != null).ifPresent(bp -> {
            activeBpId   = bp.id();
            activeBpName = bp.name();
            customerLabel.setText(bp.name());
        });
    }

    // ── Payment ───────────────────────────────────────────────────────────────

    @FXML
    private void onPay() {
        if (cart.isEmpty()) return;
        BigDecimal grandTotal = computeGrandTotal();

        PaymentDialog.show(grandTotal).ifPresent(payment -> {
            payButton.setDisable(true);
            List<CartLine> snapshot = List.copyOf(cart);
            try {
                String remarks = "QRIS".equals(payment.displayMethod()) ? "QRIS" : "";
                SaleService.SaleResult sale = saleService.post(
                    snapshot, activeBpId, grandTotal,
                    payment.dbMethod(), remarks,
                    session.getUserId(), session.getWarehouseId());
                showReceipt(sale, payment, grandTotal, snapshot);
                cart.clear();
            } catch (java.sql.SQLException e) {
                new Alert(Alert.AlertType.ERROR,
                    "Transaksi gagal:\n" + e.getMessage()).showAndWait();
            } finally {
                payButton.setDisable(cart.isEmpty());
                searchField.requestFocus();
            }
        });
    }

    private void showReceipt(SaleService.SaleResult sale,
                             PaymentResult payment, BigDecimal grandTotal,
                             List<CartLine> lines) {
        // Hitung komponen untuk ekspor
        BigDecimal subtotal = lines.stream()
            .map(CartLine::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rate = config.ppnRate().divide(BigDecimal.valueOf(100));
        BigDecimal ppn  = config.ppnInclusive()
            ? subtotal.multiply(rate).divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP)
            : subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        // ── OutputStream: ekspor struk ke file TXT ────────────────────────
        String savedPath = "";
        try {
            Path file = ReceiptExporter.export(
                sale.invoiceDocNum(), sale.paymentDocNum(),
                activeBpName, payment.displayMethod(),
                lines, subtotal, ppn, grandTotal,
                payment.tendered(), payment.change());
            savedPath = "\n\nStruk disimpan: " + file.toAbsolutePath();
        } catch (IOException e) {
            savedPath = "\n(Gagal menyimpan struk: " + e.getMessage() + ")";
        }

        String change = payment.change().compareTo(BigDecimal.ZERO) > 0
            ? "\nKembalian   : " + formatRp(payment.change()) : "";
        String msg = String.format(
            "TRANSAKSI BERHASIL\n\n" +
            "No. Invoice : %s\nNo. Bayar   : %s\n" +
            "Pelanggan   : %s\nMetode      : %s\n" +
            "Total       : %s\nDiterima    : %s%s%s",
            sale.invoiceDocNum(), sale.paymentDocNum(),
            activeBpName, payment.displayMethod(),
            formatRp(grandTotal), formatRp(payment.tendered()), change, savedPath);

        Alert receipt = new Alert(Alert.AlertType.INFORMATION, msg);
        receipt.setTitle("Struk");
        receipt.setHeaderText(null);
        receipt.showAndWait();
    }

    // ── Totals ────────────────────────────────────────────────────────────────

    private void recalcTotals() {
        BigDecimal subtotal = cart.stream()
            .map(CartLine::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rate = config.ppnRate().divide(BigDecimal.valueOf(100));
        BigDecimal ppn, grandTotal;

        if (config.ppnInclusive()) {
            ppn       = subtotal.multiply(rate)
                            .divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
            grandTotal = subtotal;
        } else {
            ppn       = subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            grandTotal = subtotal.add(ppn);
        }

        subtotalLabel.setText(formatRp(subtotal));
        ppnLabel.setText(formatRp(ppn));
        grandTotalLabel.setText(formatRp(grandTotal));
        payButton.setDisable(cart.isEmpty());
    }

    private BigDecimal computeGrandTotal() {
        BigDecimal subtotal = cart.stream()
            .map(CartLine::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rate = config.ppnRate().divide(BigDecimal.valueOf(100));
        if (config.ppnInclusive()) return subtotal;
        return subtotal.add(subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String formatRp(BigDecimal amount) {
        return "Rp " + NUM_FMT.format(amount.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
        pause.play();
    }

    public ObservableList<CartLine> getCart()        { return cart; }
    public long                     getActiveBpId()   { return activeBpId; }
    public String                   getActiveBpName() { return activeBpName; }
}
