package com.example.t.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

/**
 * Category data model for organizing bookstore and stationery inventory.
 */
public class Category {

    private final IntegerProperty categoryId;
    private final StringProperty name;
    private final StringProperty description;

    public Category() {
        this(0, "", "");
    }

    public Category(int categoryId, String name, String description) {
        this.categoryId = new SimpleIntegerProperty(categoryId);
        this.name = new SimpleStringProperty(name != null ? name : "");
        this.description = new SimpleStringProperty(description != null ? description : "");
    }

    public Category(String name, String description) {
        this(0, name, description);
    }

    public int getCategoryId() {
        return categoryId.get();
    }

    public IntegerProperty categoryIdProperty() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId.set(categoryId);
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name != null ? name : "");
    }

    public String getDescription() {
        return description.get();
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public void setDescription(String description) {
        this.description.set(description != null ? description : "");
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return getCategoryId() == category.getCategoryId() &&
                Objects.equals(getName(), category.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCategoryId(), getName());
    }
}
