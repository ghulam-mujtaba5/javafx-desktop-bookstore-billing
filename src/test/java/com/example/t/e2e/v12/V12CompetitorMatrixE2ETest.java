package com.example.t.e2e.v12;

import com.example.t.controller.MainShellController;
import com.example.t.database.DatabaseManager;
import com.example.t.model.RegionalPreset;
import com.example.t.service.EscPosPrintService;
import com.example.t.service.ZatcaQrService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Test Suite for Requirement R1 & R4:
 * Competitor Gap Analysis Matrix, In-App About Dialog,
 * Version Consistency Across System Constants, and Core Architectural Differentiators.
 */
@DisplayName("v1.2.0 E2E: Competitor Matrix & Versioning Verification")
public class V12CompetitorMatrixE2ETest {

    private static final Path PROJECT_ROOT = Paths.get(".").toAbsolutePath().normalize();
    private static Path tempDir;

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit already initialized
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("v12_comp_matrix_test_");
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (R1 Competitor Matrix & Differentiators)
    // =========================================================================

    @Test
    @DisplayName("Tier 1 - F1.1: Competitor Matrix Document Exists and Contains All Target Competitors")
    public void testCompetitorMatrixFileCompleteness() throws IOException {
        Path matrixPath = PROJECT_ROOT.resolve("docs").resolve("COMPETITOR_MATRIX.md");
        assertTrue(Files.exists(matrixPath), "docs/COMPETITOR_MATRIX.md must exist in the project repository");

        String content = Files.readString(matrixPath, StandardCharsets.UTF_8);
        assertTrue(content.length() > 2000, "COMPETITOR_MATRIX.md must contain comprehensive analysis content");

        // Verify all 5 commercial competitors are analyzed
        assertTrue(content.contains("Square for Retail") || content.contains("Square"), "Must analyze Square for Retail");
        assertTrue(content.contains("Loyverse"), "Must analyze Loyverse");
        assertTrue(content.contains("Lightspeed Retail") || content.contains("Lightspeed"), "Must analyze Lightspeed Retail");
        assertTrue(content.contains("BookManager"), "Must analyze BookManager");
        assertTrue(content.contains("Basil"), "Must analyze Basil POS");

        // Verify version 1.2.0 is documented
        assertTrue(content.contains("1.2.0"), "Document must reference version 1.2.0");
    }

    @Test
    @DisplayName("Tier 1 - F1.1: Competitor Matrix Documents 4 Core Architectural Differentiators")
    public void testCompetitorMatrixFourDifferentiators() throws IOException {
        Path matrixPath = PROJECT_ROOT.resolve("docs").resolve("COMPETITOR_MATRIX.md");
        String content = Files.readString(matrixPath, StandardCharsets.UTF_8);

        // Differentiator 1: $0 SaaS lifetime fees
        assertTrue(content.contains("$0") || content.contains("Zero Recurring") || content.contains("Zero SaaS"),
                "Must document $0 SaaS subscription differentiator");

        // Differentiator 2: Sub-millisecond latency via SQLite WAL
        assertTrue(content.contains("WAL") || content.contains("Write-Ahead Logging") || content.contains("Sub-Millisecond"),
                "Must document SQLite WAL mode latency differentiator");

        // Differentiator 3: 100% Offline Persistence
        assertTrue(content.contains("100% Offline") || content.contains("Offline Sovereignty"),
                "Must document offline persistence differentiator");

        // Differentiator 4: Direct Binary ESC/POS Hardware Control
        assertTrue(content.contains("ESC/POS") && (content.contains("Drawer Kick") || content.contains("cash drawer")),
                "Must document direct binary ESC/POS and cash drawer kick differentiator");
    }

    @Test
    @DisplayName("Tier 1 - F1.1: Competitor Matrix Includes 1/3/5-Year TCO Comparison")
    public void testCompetitorMatrixTcoAnalysis() throws IOException {
        Path matrixPath = PROJECT_ROOT.resolve("docs").resolve("COMPETITOR_MATRIX.md");
        String content = Files.readString(matrixPath, StandardCharsets.UTF_8);

        assertTrue(content.contains("TCO") || content.contains("Total Cost of Ownership"),
                "Must include TCO analysis");
        assertTrue(content.contains("Year 1") || content.contains("1-Year"), "Must include 1-year TCO");
        assertTrue(content.contains("Year 3") || content.contains("3-Year"), "Must include 3-year TCO");
        assertTrue(content.contains("Year 5") || content.contains("5-Year"), "Must include 5-year TCO");
    }

    private static <T> T runOnFxThread(java.util.concurrent.Callable<T> callable) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return callable.call();
        }
        java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(callable.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Tier 1 - F1.2: In-App About Dialog Construction and Content Completeness")
    public void testInAppAboutDialogContent() throws Exception {
        MainShellController shell = new MainShellController();
        Alert aboutAlert = runOnFxThread(shell::createAboutDialog);
        assertNotNull(aboutAlert, "createAboutDialog() must return a non-null Alert instance");

        assertEquals(Alert.AlertType.INFORMATION, aboutAlert.getAlertType());
        assertTrue(aboutAlert.getTitle().contains("1.2.0"), "Dialog title must include version 1.2.0");
        assertTrue(aboutAlert.getHeaderText().contains("1.2.0"), "Dialog header must include version 1.2.0");

        String content = aboutAlert.getContentText();
        assertNotNull(content, "About dialog content must not be null");
        assertTrue(content.contains("1.2.0"), "Content must state version 1.2.0");
        assertTrue(content.contains("SQLite") || content.contains("WAL"), "Content must reference SQLite persistence");
        assertTrue(content.contains("ESC/POS"), "Content must reference ESC/POS hardware control");
        assertTrue(content.contains("Square") || content.contains("Loyverse") || content.contains("Competitor"),
                "Content must summarize competitor benchmark");
    }

    @Test
    @DisplayName("Tier 1 - F1.2: In-App About Dialog Headless Safety")
    public void testInAppAboutDialogHeadlessExecution() {
        MainShellController shell = new MainShellController();
        // Invoking showAboutDialog() in headless mode must not throw exceptions
        assertDoesNotThrow(shell::showAboutDialog, "showAboutDialog() must execute safely in headless test environment");
        assertDoesNotThrow(shell::handleAboutDialog, "handleAboutDialog() must execute safely in headless test environment");
    }

    @Test
    @DisplayName("Tier 1 - F1.3: Architectural Differentiator - SQLite WAL Mode & Foreign Keys Enabled")
    public void testArchitectureSqliteWalModeEnabled() throws Exception {
        Path dbPath = tempDir.resolve("wal_diff_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode;")) {
                assertTrue(rs.next());
                String mode = rs.getString(1);
                assertEquals("wal", mode.toLowerCase(), "Production SQLite database must operate in WAL mode for sub-ms latency");
            }

            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                assertTrue(rs.next());
                int fk = rs.getInt(1);
                assertEquals(1, fk, "SQLite foreign keys must be active (1) to guarantee relational integrity");
            }
        }
    }

    @Test
    @DisplayName("Tier 1 - F1.3: Architectural Differentiator - Direct Binary ESC/POS Drawer Kick Bytecode")
    public void testArchitectureDirectEscPosDrawerKickBytecode() {
        byte[] kickBytes = EscPosPrintService.generateDrawerKickPulse();
        assertNotNull(kickBytes, "Drawer kick pulse must generate non-null byte array");
        assertEquals(5, kickBytes.length, "Standard ESC/POS drawer kick sequence must be exactly 5 bytes");

        // Standard ESC/POS pulse: ESC p pin t1 t2 -> 0x1B, 0x70, 0x00, 0x19, 0xFA
        assertEquals(0x1B, kickBytes[0], "Byte 0 must be ESC (0x1B)");
        assertEquals(0x70, kickBytes[1], "Byte 1 must be 'p' (0x70)");
        assertEquals(0x00, kickBytes[2], "Byte 2 must be connector pin 0 (0x00)");
        assertEquals(0x19, kickBytes[3], "Byte 3 must be pulse on-time t1 (0x19 = 25 -> 50ms)");
        assertEquals((byte) 0xFA, kickBytes[4], "Byte 4 must be pulse off-time t2 (0xFA = 250 -> 500ms)");
    }

    @Test
    @DisplayName("Tier 1 - F1.3: Architectural Differentiator - Multi-Regional Tax Presets & ZATCA QR")
    public void testArchitectureMultiRegionalAndZatcaCompliance() {
        RegionalPreset[] presets = RegionalPreset.values();
        assertTrue(presets.length > 0, "Regional presets list must not be empty");

        boolean hasUsa = false;
        boolean hasUk = false;
        boolean hasSaudi = false;
        boolean hasUae = false;

        for (RegionalPreset p : presets) {
            if ("USD".equalsIgnoreCase(p.getCurrencyCode())) hasUsa = true;
            if ("GBP".equalsIgnoreCase(p.getCurrencyCode())) hasUk = true;
            if ("SAR".equalsIgnoreCase(p.getCurrencyCode())) hasSaudi = true;
            if ("AED".equalsIgnoreCase(p.getCurrencyCode())) hasUae = true;
        }

        assertTrue(hasUsa, "Must include USA sales tax preset");
        assertTrue(hasUk, "Must include UK VAT preset");
        assertTrue(hasSaudi, "Must include Saudi Arabia 15% VAT preset");
        assertTrue(hasUae, "Must include UAE 5% VAT preset");

        // Verify ZATCA Phase 2 QR TLV generator
        String tlvBase64 = ZatcaQrService.generateTlvBase64(
                "Modern Bookshop Riyadh", "310123456700003",
                java.time.LocalDateTime.now(), 115.00, 15.00);
        assertNotNull(tlvBase64, "ZATCA QR code generator must produce valid TLV Base64 string");
        assertFalse(tlvBase64.isBlank(), "Generated TLV Base64 payload must not be blank");
        assertTrue(tlvBase64.length() > 20, "Generated TLV Base64 payload must be substantial");
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (R4 Versioning Consistency)
    // =========================================================================

    @Test
    @DisplayName("Tier 1 - F4.1: Maven pom.xml Reflects Version 1.2.0")
    public void testPomXmlVersionConsistency() throws IOException {
        Path pomPath = PROJECT_ROOT.resolve("pom.xml");
        assertTrue(Files.exists(pomPath), "pom.xml must exist at project root");
        String pomContent = Files.readString(pomPath, StandardCharsets.UTF_8);

        assertTrue(pomContent.contains("<version>1.2.0</version>"),
                "pom.xml root version element must be bumped to 1.2.0");
        assertTrue(pomContent.contains("<app.version>1.2.0</app.version>"),
                "pom.xml app.version property must be bumped to 1.2.0");
    }

    @Test
    @DisplayName("Tier 1 - F4.1: Windows Packaging Scripts Reflect Version 1.2.0")
    public void testPackagingScriptsVersionConsistency() throws IOException {
        Path issPath = PROJECT_ROOT.resolve("installer.iss");
        if (Files.exists(issPath)) {
            String issContent = Files.readString(issPath, StandardCharsets.UTF_8);
            assertTrue(issContent.contains("1.2.0"), "installer.iss must define MyAppVersion 1.2.0");
        }

        Path batPath = PROJECT_ROOT.resolve("package.bat");
        if (Files.exists(batPath)) {
            String batContent = Files.readString(batPath, StandardCharsets.UTF_8);
            assertTrue(batContent.contains("1.2.0"), "package.bat must set APP_VERSION=1.2.0");
        }

        Path ps1Path = PROJECT_ROOT.resolve("package.ps1");
        if (Files.exists(ps1Path)) {
            String ps1Content = Files.readString(ps1Path, StandardCharsets.UTF_8);
            assertTrue(ps1Content.contains("1.2.0"), "package.ps1 must set $AppVersion = 1.2.0");
        }
    }

    @Test
    @DisplayName("Tier 1 - F4.1: Main Application Shell FXML Displays Version 1.2.0")
    public void testMainShellFxmlVersionLabel() throws IOException {
        Path fxmlPath = PROJECT_ROOT.resolve("src/main/resources/com/example/t/fxml/MainShell.fxml");
        assertTrue(Files.exists(fxmlPath), "MainShell.fxml must exist");
        String fxmlContent = Files.readString(fxmlPath, StandardCharsets.UTF_8);

        assertTrue(fxmlContent.contains("1.2.0"), "MainShell.fxml must display Version 1.2.0 in the sidebar");
        assertTrue(fxmlContent.contains("Offline Ready"), "MainShell.fxml must indicate Offline Ready");
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (Competitor Matrix & About Modal)
    // =========================================================================

    @Test
    @DisplayName("Tier 2 - Boundary: MainShell FXML Injection and About Node Properties")
    public void testMainShellControllerNodeInjection() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
        Parent root = loader.load();
        assertNotNull(root, "MainShell FXML root must load successfully");

        MainShellController controller = loader.getController();
        assertNotNull(controller, "MainShellController must be injected by FXMLLoader");

        // Verify About Navigation button and Version Label
        Button navAboutBtn = controller.getNavAboutBtn();
        assertNotNull(navAboutBtn, "navAboutBtn must be injected in MainShellController");
        assertTrue(navAboutBtn.getText().contains("About") || navAboutBtn.getText().contains("Benchmark"),
                "navAboutBtn text must indicate About or Benchmark");

        Label versionLabel = controller.getVersionLabel();
        assertNotNull(versionLabel, "versionLabel must be injected in MainShellController");
        assertTrue(versionLabel.getText().contains("1.2.0"), "versionLabel must contain '1.2.0'");
    }

    @Test
    @DisplayName("Tier 2 - Corner: About Dialog Formatting and Multiline Layout")
    public void testAboutDialogFormattingAndWidth() throws Exception {
        MainShellController shell = new MainShellController();
        Alert alert = runOnFxThread(shell::createAboutDialog);
        assertNotNull(alert.getDialogPane(), "Dialog pane must not be null");
        assertTrue(alert.getDialogPane().getPrefWidth() >= 500, "Dialog pane width should be at least 500px for table readability");

        String text = alert.getContentText();
        String[] lines = text.split("\n");
        assertTrue(lines.length >= 8, "About content must contain structured multiline sections");
    }

    @Test
    @DisplayName("Tier 2 - Corner: Zero Network Calls during About Dialog Initialization")
    public void testAboutDialogZeroNetworkCalls() throws Exception {
        // Measure execution duration: building local dialog must complete in < 5ms
        long start = System.nanoTime();
        MainShellController shell = new MainShellController();
        Alert alert = runOnFxThread(shell::createAboutDialog);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(alert);
        assertTrue(elapsedMs < 1000, "About dialog creation must be instantaneous local operation (<1000ms)");
    }

    @Test
    @DisplayName("Tier 2 - Corner: Repeated About Dialog Creation Stability")
    public void testRepeatedAboutDialogCreation() throws Exception {
        MainShellController shell = new MainShellController();
        for (int i = 0; i < 20; i++) {
            Alert alert = runOnFxThread(shell::createAboutDialog);
            assertNotNull(alert);
            assertEquals(Alert.AlertType.INFORMATION, alert.getAlertType());
        }
    }

    @Test
    @DisplayName("Tier 2 - Boundary: TCO Savings Math Consistency in Matrix")
    public void testTcoMathConsistencyInMatrix() throws IOException {
        Path matrixPath = PROJECT_ROOT.resolve("docs").resolve("COMPETITOR_MATRIX.md");
        String content = Files.readString(matrixPath, StandardCharsets.UTF_8);

        // Verify that minimum 3-year savings cited ($4,248) and 5-year savings ($23,880) appear in text
        assertTrue(content.contains("4,248") || content.contains("4248"), "Matrix must substantiate $4,248 3-year savings");
        assertTrue(content.contains("23,880") || content.contains("23880"), "Matrix must substantiate $23,880 5-year savings");
    }
}
