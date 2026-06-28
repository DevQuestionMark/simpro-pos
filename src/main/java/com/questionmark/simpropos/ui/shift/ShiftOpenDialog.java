package com.questionmark.simpropos.ui.shift;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Dialog untuk membuka shift baru.
 * Cashier memasukkan modal awal (opening float) kas.
 * Panggil {@code ShiftOpenDialog.show()} → {@code Optional<BigDecimal>}.
 */
public final class ShiftOpenDialog extends Dialog<BigDecimal> {

    private final TextField floatField = new TextField("0");

    private ShiftOpenDialog() {
        setTitle("Buka Shift");
        setHeaderText(null);
        setResizable(false);

        ButtonType okType = new ButtonType("Buka Shift", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Button okBtn = (Button) getDialogPane().lookupButton(okType);
        okBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;");

        Label header = new Label("Buka Shift Kasir");
        header.setFont(Font.font("System", FontWeight.BOLD, 16));

        Label hint = new Label("Masukkan jumlah uang kas awal di laci kasir.\nKosongkan atau isi 0 jika tidak ada.");
        hint.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12;");
        hint.setWrapText(true);

        floatField.setPromptText("Contoh: 500000");
        floatField.setFont(Font.font(14));
        floatField.setStyle("-fx-padding: 8; -fx-background-radius: 4;");
        floatField.setPrefWidth(260);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12;");
        errorLabel.setVisible(false);

        floatField.textProperty().addListener((obs, ov, nv) -> {
            boolean valid = isValid(nv.trim());
            okBtn.setDisable(!valid);
            errorLabel.setVisible(!valid && !nv.trim().isEmpty());
            if (!valid && !nv.trim().isEmpty())
                errorLabel.setText("Masukkan angka yang valid (tanpa titik/koma)");
        });

        VBox root = new VBox(10, header, new Separator(), hint,
                             new Label("Modal Awal (Rp)"), floatField, errorLabel);
        root.setPadding(new Insets(4, 0, 4, 0));
        root.setPrefWidth(300);

        getDialogPane().setContent(root);
        getDialogPane().setPadding(new Insets(20));

        setResultConverter(btn -> {
            if (btn != okType) return null;
            String raw = floatField.getText().trim().replace(",", "");
            try { return raw.isEmpty() ? BigDecimal.ZERO : new BigDecimal(raw); }
            catch (NumberFormatException e) { return BigDecimal.ZERO; }
        });
    }

    private static boolean isValid(String s) {
        if (s.isEmpty() || s.equals("0")) return true;
        try { new BigDecimal(s.replace(",", "")); return true; }
        catch (NumberFormatException e) { return false; }
    }

    public static Optional<BigDecimal> open() {
        return new ShiftOpenDialog().showAndWait();
    }
}
