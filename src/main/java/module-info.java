module com.questionmark.simpropos {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;

    requires java.sql;
    requires com.zaxxer.hikari;
    requires mysql.connector.j;
    requires com.google.guice;
    requires javax.inject;
    requires jbcrypt;

    opens com.questionmark.simpropos to javafx.fxml;
    opens com.questionmark.simpropos.config to com.google.guice;
    opens com.questionmark.simpropos.session to com.google.guice;
    opens com.questionmark.simpropos.db to com.google.guice;
    opens com.questionmark.simpropos.repository to com.google.guice;
    opens com.questionmark.simpropos.ui.login to javafx.fxml, com.google.guice;
    opens com.questionmark.simpropos.ui.main to javafx.fxml, com.google.guice;
    opens com.questionmark.simpropos.ui.checkout to javafx.fxml, com.google.guice;
    opens com.questionmark.simpropos.service to com.google.guice;

    exports com.questionmark.simpropos;
}
