package com.example.t;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.util.List;

public class ViewOrderScreen {
    @FXML private TableView<Order> orderTable;
    @FXML private TableColumn<Order, Integer> colOrderId;
    @FXML private TableColumn<Order, String> colCustomerName;
    @FXML private TableColumn<Order, String> colDate;
    @FXML private TableColumn<Order, Double> colTotal;
    @FXML private Button refreshButton;
    @FXML private Label messageLabel;

    private ObservableList<Order> data;

    @FXML
    public void initialize() {
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colCustomerName.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("orderDate"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));

        List<Order> orderList = Order.readOrdersFromFile();
        data = FXCollections.observableArrayList(orderList);
        orderTable.setItems(data);
        orderTable.setPlaceholder(new Label("No orders available"));

        refreshButton.setOnAction(e -> refreshOrders());
    }

    private void refreshOrders() {
        List<Order> orderList = Order.readOrdersFromFile();
        data.setAll(orderList);
        messageLabel.setText("Order list refreshed.");
    }
}
