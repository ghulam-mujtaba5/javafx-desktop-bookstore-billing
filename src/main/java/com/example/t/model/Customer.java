package com.example.t.model;

import javafx.beans.property.*;

/**
 * Customer model for Bookstore & Stationery POS.
 * Supports phone number lookup, lifetime spend tracking, store credit,
 * and loyalty rewards (Loyverse & Square parity).
 */
public class Customer {

    private final IntegerProperty customerId;
    private final StringProperty name;
    private final StringProperty phone;
    private final StringProperty email;
    private final IntegerProperty loyaltyPoints;
    private final DoubleProperty storeCredit;
    private final DoubleProperty totalSpent;

    public Customer() {
        this(0, "Walk-in Customer", "", "", 0, 0.0, 0.0);
    }

    public Customer(int customerId, String name, String phone, String email,
                    int loyaltyPoints, double storeCredit, double totalSpent) {
        this.customerId = new SimpleIntegerProperty(customerId);
        this.name = new SimpleStringProperty(name != null ? name : "Walk-in Customer");
        this.phone = new SimpleStringProperty(phone != null ? phone : "");
        this.email = new SimpleStringProperty(email != null ? email : "");
        this.loyaltyPoints = new SimpleIntegerProperty(loyaltyPoints);
        this.storeCredit = new SimpleDoubleProperty(storeCredit);
        this.totalSpent = new SimpleDoubleProperty(totalSpent);
    }

    public IntegerProperty customerIdProperty() { return customerId; }
    public StringProperty nameProperty() { return name; }
    public StringProperty phoneProperty() { return phone; }
    public StringProperty emailProperty() { return email; }
    public IntegerProperty loyaltyPointsProperty() { return loyaltyPoints; }
    public DoubleProperty storeCreditProperty() { return storeCredit; }
    public DoubleProperty totalSpentProperty() { return totalSpent; }

    public int getCustomerId() { return customerId.get(); }
    public int getId() { return getCustomerId(); }
    public void setCustomerId(int id) { this.customerId.set(id); }

    public String getName() { return name.get(); }
    public void setName(String name) { this.name.set(name); }

    public String getPhone() { return phone.get(); }
    public void setPhone(String phone) { this.phone.set(phone); }

    public String getEmail() { return email.get(); }
    public void setEmail(String email) { this.email.set(email); }

    public int getLoyaltyPoints() { return loyaltyPoints.get(); }
    public void setLoyaltyPoints(int points) { this.loyaltyPoints.set(points); }

    public double getStoreCredit() { return storeCredit.get(); }
    public void setStoreCredit(double credit) { this.storeCredit.set(credit); }

    public double getTotalSpent() { return totalSpent.get(); }
    public void setTotalSpent(double spent) { this.totalSpent.set(spent); }

    @Override
    public String toString() {
        return getName() + (getPhone().isEmpty() ? "" : " (" + getPhone() + ")");
    }
}
