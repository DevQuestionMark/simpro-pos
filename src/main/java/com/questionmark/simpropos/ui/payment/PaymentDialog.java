package com.questionmark.simpropos.ui.payment;

import com.questionmark.simpropos.model.PaymentResult;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Optional;

/**
 * Modal tender dialog.  Call {@code PaymentDialog.show(grandTotal)} to open it.
 *
 * Methods:
 *   CASH     — customer pays cash; enter the tendered amount; change is computed.
 *   QRIS     — customer scans QR (offline confirm); stored as TRANSFER in DB.
 *   Transfer — bank transfer; stored as TRANSFER in DB.
 *
 * Returns empty Optional if the user cancels.
 */
public final class PaymentDialog extends Dialog<PaymentResult> {

    private static final DecimalFormat NUM_FMT = new DecimalFormat("#,##0");

    private final BigDecimal grandTotal;

    // CASH section
    private final TextField  tenderedField = new TextField();
    private final Label      changeLabel   = new Label("Kembalian:  Rp 0");
    private final Label      errorLabel    = new Label();

    // dynamic sections
    private final VBox  cashSection     = new VBox(8);
    private final Label nonCashInfo     = new Label();

    private final ToggleGroup methodGroup = new ToggleGroup();

    private PaymentDialog(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;

        setTitle("Pembayaran");
        setHeaderText(null);
        setResizable(false);

        ButtonType okType = new ButtonType("Proses Bayar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Button okBtn = (Button) getDialogPane().lookupButton(okType);
        okBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; " +
                       "-fx-font-weight: bold; -fx-font-size: 13;");

        buildContent(okBtn);

        setResultConverter(btn -> {
            if (btn != okType) return null;
            return buildResult();
        });
    }

    // ── Layout ─────────────────────────────────────────────────────────────

    private void buildContent(Button okBtn) {
        // Grand total display
        Label totalHdr = new Label("Total yang Harus Dibayar");
        totalHdr.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12;");

        Label totalAmt = new Label(formatRp(grandTotal));
        totalAmt.setFont(Font.font("System", FontWeight.BOLD, 28));
        totalAmt.setStyle("-fx-text-fill: #16a34a;");

        // Method toggle row
        RadioButton cashRb     = radioBtn("Cash",      "CASH");
        RadioButton qrisRb     = radioBtn("QRIS",      "QRIS");
        RadioButton transferRb = radioBtn("Transfer",  "TRANSFER");
        cashRb.setSelected(true);

        HBox methodRow = new HBox(16, cashRb, qrisRb, transferRb);
        methodRow.setAlignment(Pos.CENTER_LEFT);

        // Cash section
        tenderedField.setPromptText("Nominal yang diterima");
        tenderedField.setFont(Font.font(14));
        tenderedField.setStyle("-fx-padding: 8; -fx-background-radius: 4;");
        tenderedField.setPrefWidth(240);

        changeLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1d4ed8;");
        errorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12;");
        errorLabel.setVisible(false);

        cashSection.getChildren().addAll(
            new Label("Uang Diterima"),
            tenderedField,
            changeLabel,
            errorLabel
        );

        // Non-cash section
        nonCashInfo.setStyle("-fx-text-fill: #374151; -fx-font-size: 13;");
        nonCashInfo.setWrapText(true);
        nonCashInfo.setVisible(false);
        nonCashInfo.setManaged(false);

        // Wire method selection
        methodGroup.selectedToggleProperty().addListener((obs, ov, nv) -> {
            if (nv == null) return;
            String m = (String) nv.getUserData();
            boolean isCash = "CASH".equals(m);
            cashSection.setVisible(isCash);
            cashSection.setManaged(isCash);
            nonCashInfo.setVisible(!isCash);
            nonCashInfo.setManaged(!isCash);
            if (!isCash) {
                nonCashInfo.setText("Konfirmasi penerimaan pembayaran " +
                    ("QRIS".equals(m) ? "via QRIS" : "via Transfer Bank") +
                    "\nsebesar  " + formatRp(grandTotal));
                okBtn.setDisable(false);
                errorLabel.setVisible(false);
            } else {
                revalidateCash(okBtn);
            }
        });

        // Wire tendered field
        ChangeListener<String> tenderedListener = (obs, ov, nv) -> revalidateCash(okBtn);
        tenderedField.textProperty().addListener(tenderedListener);

        // Initial state: CASH selected, OK disabled until amount entered
        okBtn.setDisable(true);

        // Root layout
        VBox root = new VBox(12,
            totalHdr, totalAmt,
            new Separator(),
            new Label("Metode Pembayaran"), methodRow,
            new Separator(),
            cashSection, nonCashInfo
        );
        root.setPadding(new Insets(4, 0, 4, 0));
        root.setPrefWidth(340);

        getDialogPane().setContent(root);
        getDialogPane().setPadding(new Insets(20));
    }

    private RadioButton radioBtn(String label, String userData) {
        RadioButton rb = new RadioButton(label);
        rb.setToggleGroup(methodGroup);
        rb.setUserData(userData);
        rb.setFont(Font.font(13));
        return rb;
    }

    // ── Validation ─────────────────────────────────────────────────────────

    private void revalidateCash(Button okBtn) {
        String raw = tenderedField.getText().trim().replace(",", "").replace(".", "");
        if (raw.isEmpty()) {
            changeLabel.setText("Kembalian:  —");
            errorLabel.setVisible(false);
            okBtn.setDisable(true);
            return;
        }
        try {
            BigDecimal tendered = new BigDecimal(raw);
            BigDecimal change   = tendered.subtract(grandTotal);
            if (change.compareTo(BigDecimal.ZERO) < 0) {
                changeLabel.setText("Kembalian:  —");
                errorLabel.setText("Uang kurang  " + formatRp(change.negate()));
                errorLabel.setVisible(true);
                okBtn.setDisable(true);
            } else {
                changeLabel.setText("Kembalian:  " + formatRp(change));
                errorLabel.setVisible(false);
                okBtn.setDisable(false);
            }
        } catch (NumberFormatException e) {
            changeLabel.setText("Kembalian:  —");
            errorLabel.setText("Masukkan angka yang valid");
            errorLabel.setVisible(true);
            okBtn.setDisable(true);
        }
    }

    // ── Build result ───────────────────────────────────────────────────────

    private PaymentResult buildResult() {
        String selected = (String) methodGroup.getSelectedToggle().getUserData();
        if ("CASH".equals(selected)) {
            BigDecimal tendered = new BigDecimal(
                tenderedField.getText().trim().replace(",", "").replace(".", ""));
            BigDecimal change   = tendered.subtract(grandTotal).setScale(2, RoundingMode.HALF_UP);
            return new PaymentResult("CASH", "Cash", tendered, change);
        } else if ("QRIS".equals(selected)) {
            return new PaymentResult("TRANSFER", "QRIS", grandTotal, BigDecimal.ZERO);
        } else {
            return new PaymentResult("TRANSFER", "Transfer", grandTotal, BigDecimal.ZERO);
        }
    }

    // ── Public factory ─────────────────────────────────────────────────────

    public static Optional<PaymentResult> show(BigDecimal grandTotal) {
        return new PaymentDialog(grandTotal).showAndWait();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String formatRp(BigDecimal amount) {
        return "Rp " + NUM_FMT.format(amount.setScale(0, RoundingMode.HALF_UP).longValue());
    }
}
