package com.questionmark.simpropos.ui.main;

import com.google.inject.Injector;
import com.questionmark.simpropos.PosApplication;
import com.questionmark.simpropos.config.AppConfig;
import com.questionmark.simpropos.model.ShiftDto;
import com.questionmark.simpropos.repository.ShiftRepository;
import com.questionmark.simpropos.session.AppSession;
import com.questionmark.simpropos.ui.shift.ShiftCloseDialog;
import com.questionmark.simpropos.ui.shift.ShiftOpenDialog;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;

public class MainController {

    @FXML private Label     statusLabel;
    @FXML private StackPane centerPane;

    private final AppSession      session;
    private final Injector        injector;
    private final ShiftRepository shiftRepo;
    private final AppConfig       config;

    private static final DecimalFormat NUM_FMT = new DecimalFormat("#,##0");

    @Inject
    public MainController(AppSession session, Injector injector,
                          ShiftRepository shiftRepo, AppConfig config) {
        this.session   = session;
        this.injector  = injector;
        this.shiftRepo = shiftRepo;
        this.config    = config;
    }

    @FXML
    public void initialize() {
        updateStatusLabel();

        // Load checkout screen
        try {
            FXMLLoader loader = new FXMLLoader(
                PosApplication.class.getResource("checkout.fxml"));
            loader.setControllerFactory(injector::getInstance);
            centerPane.getChildren().add(loader.load());
        } catch (IOException e) {
            throw new RuntimeException("Gagal memuat layar kasir", e);
        }

        // Check / open shift after scene is shown (only when shift feature is on)
        if (config.shiftEnabled()) Platform.runLater(this::ensureShiftOpen);
    }

    // ── Shift lifecycle ────────────────────────────────────────────────────

    private void ensureShiftOpen() {
        try {
            shiftRepo.findOpenShift(session.getOutletId(), session.getTerminalId())
                .ifPresentOrElse(
                    shift -> {
                        session.setActiveShift(shift);
                        updateStatusLabel();
                    },
                    () -> ShiftOpenDialog.open().ifPresentOrElse(
                        this::openShift,
                        () -> {
                            Alert warn = new Alert(Alert.AlertType.WARNING,
                                "Shift belum dibuka.\nTransaksi tidak dapat disimpan ke database.");
                            warn.setHeaderText(null);
                            warn.showAndWait();
                        }
                    )
                );
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Gagal memeriksa shift:\n" + e.getMessage())
                .showAndWait();
        }
    }

    private void openShift(BigDecimal openingFloat) {
        try {
            ShiftDto shift = shiftRepo.open(
                session.getOutletId(), session.getTerminalId(),
                session.getUserId(), openingFloat);
            session.setActiveShift(shift);
            updateStatusLabel();
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Gagal membuka shift:\n" + e.getMessage())
                .showAndWait();
        }
    }

    @FXML
    private void onCloseShift() {
        if (!config.shiftEnabled()) {
            new Alert(Alert.AlertType.INFORMATION, "Fitur shift tidak aktif.\nAktifkan pos.shift_enabled=true di pos.properties.").showAndWait();
            return;
        }
        if (!session.hasActiveShift()) {
            new Alert(Alert.AlertType.WARNING, "Tidak ada shift yang aktif.").showAndWait();
            return;
        }
        ShiftDto shift = session.getActiveShift();
        BigDecimal cashSales;
        try {
            cashSales = shiftRepo.sumCashReceiptsSince(shift.openedAt());
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Gagal menghitung kas:\n" + e.getMessage())
                .showAndWait();
            return;
        }

        BigDecimal expected = shift.openingFloat().add(cashSales);

        ShiftCloseDialog.show(shift, expected).ifPresent(counted -> {
            try {
                shiftRepo.close(shift.id(), counted, expected);
                session.clearShift();
                updateStatusLabel();

                BigDecimal variance = counted.subtract(expected);
                String sign = variance.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                new Alert(Alert.AlertType.INFORMATION,
                    "Shift #" + shift.id() + " ditutup.\n\n"
                    + "Kas Diharapkan : " + formatRp(expected) + "\n"
                    + "Kas Dihitung   : " + formatRp(counted) + "\n"
                    + "Selisih        : " + sign + formatRp(variance)
                ).showAndWait();

                // Offer to open the next shift immediately
                ensureShiftOpen();

            } catch (SQLException e) {
                new Alert(Alert.AlertType.ERROR, "Gagal menutup shift:\n" + e.getMessage())
                    .showAndWait();
            }
        });
    }

    // ── Logout ─────────────────────────────────────────────────────────────

    @FXML
    private void onLogout() throws IOException {
        session.logout();
        session.clearShift();
        FXMLLoader loader = new FXMLLoader(
            PosApplication.class.getResource("login.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        stage.setScene(new Scene(loader.load(), 420, 500));
        stage.setTitle("Simpro POS — Login");
        stage.setResizable(false);
        stage.setMaximized(false);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void updateStatusLabel() {
        String shiftInfo = session.hasActiveShift()
            ? "  |  Shift #" + session.getActiveShift().id()
            : "  |  Shift: BELUM DIBUKA";
        statusLabel.setText(
            "Login sebagai: " + session.getUserName()
            + "  |  Gudang: " + session.getWarehouseId()
            + "  |  Terminal: " + session.getTerminalId()
            + shiftInfo
        );
    }

    private static String formatRp(BigDecimal amount) {
        return "Rp " + NUM_FMT.format(amount.setScale(0, RoundingMode.HALF_UP).longValue());
    }
}
