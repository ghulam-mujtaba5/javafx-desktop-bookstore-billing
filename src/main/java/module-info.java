module com.example.t {
    requires transitive javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;

    opens com.example.t to javafx.fxml;
    exports com.example.t;
}