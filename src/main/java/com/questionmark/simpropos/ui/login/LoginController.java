package com.questionmark.simpropos.ui.login;

import com.google.inject.Injector;
import com.questionmark.simpropos.PosApplication;
import com.questionmark.simpropos.session.AppSession;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    private final DataSource dataSource;
    private final AppSession session;
    private final Injector   injector;

    @Inject
    public LoginController(DataSource dataSource, AppSession session, Injector injector) {
        this.dataSource = dataSource;
        this.session    = session;
        this.injector   = injector;
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username dan password wajib diisi.");
            return;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, password, role, is_active FROM users WHERE username = ?")) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    showError("Username atau password salah.");
                    return;
                }
                if (rs.getInt("is_active") == 0) {
                    showError("Akun tidak aktif.");
                    return;
                }
                // Laravel stores hashes with $2y$ prefix; jbcrypt expects $2a$
                String hash = rs.getString("password").replace("$2y$", "$2a$");
                if (!BCrypt.checkpw(password, hash)) {
                    showError("Username atau password salah.");
                    return;
                }
                session.login(rs.getLong("id"), rs.getString("name"), rs.getString("role"));
            }

        } catch (SQLException e) {
            showError("Database error: " + e.getMessage());
            return;
        }

        try {
            navigateToMain();
        } catch (IOException e) {
            showError("Gagal membuka halaman utama: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void navigateToMain() throws IOException {
        FXMLLoader loader = new FXMLLoader(
            PosApplication.class.getResource("main.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setScene(new Scene(loader.load()));
        stage.setTitle("Simpro POS — " + session.getUserName());
        stage.setResizable(true);
        stage.setMaximized(true);
    }
}
