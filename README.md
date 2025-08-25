# JavaFX Desktop Bookstore Billing

<p align="left">
  <img alt="Java" src="https://img.shields.io/badge/Java-20-007396?logo=java&logoColor=white" />
  <img alt="JavaFX" src="https://img.shields.io/badge/JavaFX-20-1B73E8" />
  <img alt="Maven" src="https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white" />
  <img alt="ControlsFX" src="https://img.shields.io/badge/ControlsFX-11.1.2-2E7D32" />
</p>

Desktop billing and inventory tool for a small bookstore built with JavaFX. It supports product management, invoice generation, basic stock tracking, and history exports.

---

## 📌 Contents
- Overview
- Features
- Tech Stack
- Project Structure
- Prerequisites
- Setup & Run
- Build Options
- Data Files
- Screenshot

---

## 🧭 Overview
This application provides a simple POS-style billing workflow. It reads/writes lightweight data files and lets you generate invoices and maintain a minimal stock ledger directly from a desktop UI.

## ✨ Features
- Product/catalog management
- Create and print/save invoices
- Stock tracking with simple adjustments
- Invoice history (CSV/TXT)
- Persistent lightweight storage (text/CSV)

## 🧰 Tech Stack
- Java 20
- JavaFX 20 (`javafx-controls`, `javafx-fxml`)
- ControlsFX 11.1.2
- Maven with `javafx-maven-plugin`

## 🗂 Project Structure
```
javafx-desktop-bookstore-billing/
├─ src/
│  └─ main/
│     ├─ java/
│     │  └─ com/example/t/  # app sources
│     ├─ resources/         # FXML, CSS, icons
│     ├─ Shop_Data.txt
│     ├─ stock.txt
│     ├─ invoice_history.txt
│     ├─ invoice_history.csv
│     └─ background1.jpg
├─ pom.xml
├─ mvnw / mvnw.cmd
└─ README.md
```

## ✅ Prerequisites
- JDK 20 (matching `pom.xml` source/target)
- Maven 3.8+ (or use the provided Maven Wrapper `mvnw/mvnw.cmd`)

## ▶️ Setup & Run (Development)
On Windows PowerShell or CMD:

```bash
# run with Maven Wrapper (downloads JavaFX automatically)
mvnw.cmd clean javafx:run
```

If you have Maven installed globally:

```bash
mvn clean javafx:run
```

Main class (configured in `pom.xml`): `com.example.t/com.example.t.HelloApplication`

## 📦 Build Options
- Build a JAR (may still require JavaFX modules at runtime):
```bash
mvnw.cmd clean package
```

- Create a trimmed runtime image with the JavaFX plugin (recommended for distribution):
```bash
mvnw.cmd clean javafx:jlink
# Resulting image under: target/app/
# Launch the app binary inside that folder
```

## 🗃 Data Files
The app uses lightweight files located under `src/main/` for persistence and history:
- `Shop_Data.txt`, `stock.txt` — catalog/stock
- `invoice_history.txt`, `invoice_history.csv` — invoice history

Note: depending on your code, these may be copied or referenced relative to the working directory. Adjust paths if you package the app.

## 🖼 Screenshot
![Screenshot (1069)](https://github.com/ghulam-mujtaba5/java-semester-billing-software/assets/128301757/2a0b2cec-bfb5-45b9-88ef-5e716e96f1a4)

---

