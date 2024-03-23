package com.example.t;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
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
        tableView.getColumns().addAll(nameColumn, quantityColumn);

        VBox vbox = new VBox(tableView);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(20));

        Scene scene = new Scene(vbox, 400, 300);
        stage.setScene(scene);
        stage.show();
    }


    public void updateOrders(ObservableList<Order> orders) {
        tableView.setItems(orders);
    }
}
