package com.questionmark.simpropos;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.questionmark.simpropos.config.AppConfig;
import com.questionmark.simpropos.config.AppModule;
import com.questionmark.simpropos.db.DatabaseMigration;
import com.questionmark.simpropos.session.AppSession;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;

public class PosApplication extends Application {

    private Injector injector;

    @Override
    public void start(Stage primaryStage) {
        try {
            injector = Guice.createInjector(new AppModule());

            AppConfig config = injector.getInstance(AppConfig.class);
            AppSession session = injector.getInstance(AppSession.class);
            session.initFromConfig(
                config.warehouseId(), config.defaultBpId(),
                config.outletId(),    config.terminalId()
            );

            // verify DB connectivity and run migrations
            try (Connection conn = injector.getInstance(DataSource.class).getConnection()) {
                // connection verified
            }
            injector.getInstance(DatabaseMigration.class).run();

            showLogin(primaryStage);

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                "Gagal memulai aplikasi:\n" + e.getMessage()).showAndWait();
            Platform.exit();
        }
    }

    private void showLogin(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            PosApplication.class.getResource("login.fxml"));
        loader.setControllerFactory(injector::getInstance);
        stage.setScene(new Scene(loader.load(), 420, 500));
        stage.setTitle("Simpro POS — Login");
        stage.setResizable(false);
        stage.show();
    }

    @Override
    public void stop() {
        // HikariCP closes cleanly when the DataSource is a HikariDataSource;
        // Guice singleton lifecycle handles it via JVM shutdown.
    }
}
