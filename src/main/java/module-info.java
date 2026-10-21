module com.example.t {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires atlantafx.base;
    requires java.sql;
    requires java.desktop;
    requires com.github.librepdf.openpdf;
    requires java.net.http;

    opens com.example.t to javafx.fxml, javafx.base;
    opens com.example.t.model to javafx.fxml, javafx.base;
    opens com.example.t.controller to javafx.fxml, javafx.base;

    exports com.example.t;
    exports com.example.t.controller;
    exports com.example.t.model;
    exports com.example.t.database;
    exports com.example.t.dao;
    exports com.example.t.service;
    exports com.example.t.service.isbn;
}