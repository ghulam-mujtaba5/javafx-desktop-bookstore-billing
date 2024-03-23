module com.example.t {
    requires javafx.controls;
    requires javafx.fxml;



    opens com.example.t to javafx.fxml;

    exports com.example.t;

}