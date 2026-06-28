package com.questionmark.simpropos.ui.shift;

import com.questionmark.simpropos.model.ShiftDto;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Dialog penutupan shift.
 * Menampilkan ringkasan kas shift dan meminta kasir memasukkan
 * jumlah uang yang dihitung fisik.
 *
 * Panggil {@code ShiftCloseDialog.show(shift, expectedCash)}.
 */
public final class ShiftCloseDialog extends Dialog<BigDecimal> {

    private static final DecimalFormat        NUM_FMT = new DecimalFormat("#,##0");
    private static final DateTimeFormatter    DT_FMT  =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TextField  countedField = new TextField();
    private final Label      varianceLabel = new Label();

    private ShiftCloseDialog(ShiftDto shift, BigDecimal expectedCash) {
        setTitle("Tutup Shift");
        setHeaderText(null);
        setResizable(false);

        ButtonType okType = new ButtonType("Tutup Shift", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Button okBtn = (Button) getDialogPane().lookupButton(okType);
        okBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold;");
        okBtn.setDisable(true);

        Label header = new Label("Penutupan Shift #" + shift.id());
        header.setFont(Font.font("System", FontWeight.BOLD, 15));

        // Shift info grid
        VBox infoBox = new VBox(6,
            infoRow("Waktu Buka",    shift.openedAt().format(DT_FMT)),
            infoRow("Modal Awal",    formatRp(shift.openingFloat())),
            infoRow("Kas Diharapkan", formatRp(expectedCash))
        );
        infoBox.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 6; -fx-padding: 10;");

        // Counted cash input
        countedField.setPromptText("Masukkan jumlah uang yang dihitung");
        countedField.setFont(Font.font(14));
        countedField.setStyle("-fx-padding: 8; -fx-background-radius: 4;");
        countedField.setPrefWidth(300);

        varianceLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        countedField.textProperty().addListener((obs, ov, nv) -> {
            String raw = nv.trim().replace(",", "");
            if (raw.isEmpty()) {
                varianceLabel.setText("");
                okBtn.setDisable(true);
                return;
            }
            try {
                BigDecimal counted  = new BigDecimal(raw);
                BigDecimal variance = counted.subtract(expectedCash);
                boolean    surplus  = variance.compareTo(BigDecimal.ZERO) >= 0;
                varianceLabel.setText((surplus ? "Lebih: +" : "Kurang: ") + formatRp(variance.abs()));
                varianceLabel.setStyle("-fx-text-fill: " + (surplus ? "#16a34a" : "#dc2626") + ";");
                okBtn.setDisable(false);
            } catch (NumberFormatException e) {
                varianceLabel.setText("Angka tidak valid");
                varianceLabel.setStyle("-fx-text-fill: #dc2626;");
                okBtn.setDisable(true);
            }
        });

        VBox root = new VBox(12,
            header, new Separator(),
            infoBox,
            new Label("Uang Kas Dihitung (Rp)"),
            countedField,
            varianceLabel
        );
        root.setPadding(new Insets(4, 0, 4, 0));
        root.setPrefWidth(340);

        getDialogPane().setContent(root);
        getDialogPane().setPadding(new Insets(20));

        setResultConverter(btn -> {
            if (btn != okType) return null;
            try {
                return new BigDecimal(countedField.getText().trim().replace(",", ""))
                    .setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) { return BigDecimal.ZERO; }
        });
    }

    private static HBox infoRow(String label, String value) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12;");
        lbl.setPrefWidth(130);
        Label val = new Label(value);
        val.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");
        Region spacer = new Region();
        HBox row = new HBox(spacer, lbl, val);
        return row;
    }

    private static String formatRp(BigDecimal amount) {
        return "Rp " + NUM_FMT.format(amount.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    public static Optional<BigDecimal> show(ShiftDto shift, BigDecimal expectedCash) {
        return new ShiftCloseDialog(shift, expectedCash).showAndWait();
    }
}
