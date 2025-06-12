package com.example.t;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

public class ViewOrderScreen {
    private TableView<Order> tableView;

    public void show() {
        Stage stage = new Stage(); // Create a new Stage

        stage.setTitle("View Order");

        tableView = new TableView<>();
        tableView.setPrefWidth(400);
        TableColumn<Order, String> nameColumn = new TableColumn<>("Product Name");
        TableColumn<Order, Integer> quantityColumn = new TableColumn<>("Product Quantity");
        // Suppress type safety warning for varargs TableColumn
        @SuppressWarnings("unchecked")
        TableColumn<Order, ?>[] columns = new TableColumn[] {nameColumn, quantityColumn};
        tableView.getColumns().addAll(columns);

        // Modern overlay with background
        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
        javafx.scene.image.Image backgroundImage = new javafx.scene.image.Image(com.example.t.FilePathManager.getBackgroundImagePath());
        javafx.scene.layout.BackgroundImage background = new javafx.scene.layout.BackgroundImage(
                backgroundImage,
                javafx.scene.layout.BackgroundRepeat.NO_REPEAT,
                javafx.scene.layout.BackgroundRepeat.NO_REPEAT,
                javafx.scene.layout.BackgroundPosition.CENTER,
                new javafx.scene.layout.BackgroundSize(1.0, 1.0, true, true, false, false)
        );
        root.setBackground(new javafx.scene.layout.Background(background));

        javafx.scene.layout.VBox overlay = new javafx.scene.layout.VBox(24);
        overlay.setAlignment(Pos.CENTER);
        overlay.setMaxWidth(500);
        overlay.setStyle("-fx-background-color: rgba(255,255,255,0.92); -fx-background-radius: 18px; -fx-padding: 30 30 30 30;");

        javafx.scene.control.Label title = new javafx.scene.control.Label("View Orders");
        title.getStyleClass().add("title-label");

        overlay.getChildren().addAll(title, tableView);
        root.getChildren().add(overlay);

        Scene scene = new Scene(root, 500, 350);
        scene.getStylesheets().add(getClass().getResource("/com/example/t/modern-theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }


    public void updateOrders(ObservableList<Order> orders) {
        tableView.setItems(orders);
    }
}
