package com.questionmark.simpropos.ui.customer;

import com.questionmark.simpropos.model.BusinessPartnerDto;
import com.questionmark.simpropos.repository.BusinessPartnerRepository;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Dialog untuk membuat pelanggan baru langsung dari POS.
 * Memasukkan baris ke business_partners dengan card_type='CUSTOMER'.
 *
 * Panggil {@code CreateCustomerDialog.open(bpRepo)} → {@code Optional<BusinessPartnerDto>}.
 */
public final class CreateCustomerDialog extends Dialog<BusinessPartnerDto> {

    private final TextField nameField    = new TextField();
    private final TextField phoneField   = new TextField();
    private final TextField emailField   = new TextField();
    private final TextArea  addressArea  = new TextArea();

    private final BusinessPartnerRepository bpRepo;

    private CreateCustomerDialog(BusinessPartnerRepository bpRepo) {
        this.bpRepo = bpRepo;

        setTitle("Pelanggan Baru");
        setHeaderText("Tambah Pelanggan Baru");
        setResizable(false);

        ButtonType saveType = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Button saveBtn = (Button) getDialogPane().lookupButton(saveType);
        saveBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setDisable(true);

        // Enable Save only when Nama is filled
        nameField.textProperty().addListener((obs, ov, nv) ->
            saveBtn.setDisable(nv.trim().isEmpty()));

        // Layout
        nameField.setPromptText("Wajib diisi");
        phoneField.setPromptText("Opsional");
        emailField.setPromptText("Opsional");
        addressArea.setPromptText("Opsional");
        addressArea.setPrefRowCount(3);
        addressArea.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 4, 0));

        grid.add(new Label("Nama *"),   0, 0);
        grid.add(nameField,             1, 0);
        grid.add(new Label("Telepon"),  0, 1);
        grid.add(phoneField,            1, 1);
        grid.add(new Label("Email"),    0, 2);
        grid.add(emailField,            1, 2);
        grid.add(new Label("Alamat"),   0, 3);
        grid.add(addressArea,           1, 3);

        GridPane.setHgrow(nameField,   Priority.ALWAYS);
        GridPane.setHgrow(phoneField,  Priority.ALWAYS);
        GridPane.setHgrow(emailField,  Priority.ALWAYS);
        GridPane.setHgrow(addressArea, Priority.ALWAYS);

        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(400);
        getDialogPane().setPadding(new Insets(20));

        setResultConverter(btn -> {
            if (btn != saveType) return null;
            try {
                return bpRepo.insertCustomer(
                    nameField.getText(),
                    phoneField.getText(),
                    emailField.getText(),
                    addressArea.getText()
                );
            } catch (SQLException e) {
                new Alert(Alert.AlertType.ERROR,
                    "Gagal menyimpan pelanggan:\n" + e.getMessage()).showAndWait();
                return null;
            }
        });
    }

    public static Optional<BusinessPartnerDto> open(BusinessPartnerRepository bpRepo) {
        return new CreateCustomerDialog(bpRepo).showAndWait();
    }
}
