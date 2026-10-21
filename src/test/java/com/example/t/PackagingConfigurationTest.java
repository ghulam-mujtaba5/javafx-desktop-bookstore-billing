package com.example.t;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated tests verifying standalone Windows packaging configuration:
 * - Dedicated packaging profile in pom.xml (package-windows)
 * - Packaging scripts presence and executable syntax (package.ps1 and package.bat)
 * - Validation of main launcher class (com.example.t.RunFX), app name (BookstoreBilling),
 *   and runtime packaging parameters.
 */
public class PackagingConfigurationTest {

    private static Path projectRoot;
    private static Path pomXmlPath;
    private static Path packagePs1Path;
    private static Path packageBatPath;
    private static Path iconPath;

    @BeforeAll
    static void setUp() {
        projectRoot = Paths.get(".").toAbsolutePath().normalize();
        pomXmlPath = projectRoot.resolve("pom.xml");
        packagePs1Path = projectRoot.resolve("package.ps1");
        packageBatPath = projectRoot.resolve("package.bat");
        iconPath = projectRoot.resolve("icon.ico");
    }

    @Test
    @DisplayName("pom.xml must contain the 'package-windows' profile with expected properties")
    void testPomXmlContainsPackageWindowsProfile() throws Exception {
        assertTrue(Files.exists(pomXmlPath), "pom.xml must exist at project root");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomXmlPath.toFile());
        doc.getDocumentElement().normalize();

        NodeList profiles = doc.getElementsByTagName("profile");
        boolean foundWindowsProfile = false;
        String appName = null;
        String mainClass = null;

        for (int i = 0; i < profiles.getLength(); i++) {
            Element profile = (Element) profiles.item(i);
            NodeList idNodes = profile.getElementsByTagName("id");
            if (idNodes.getLength() > 0 && "package-windows".equals(idNodes.item(0).getTextContent().trim())) {
                foundWindowsProfile = true;

                NodeList appNameNodes = profile.getElementsByTagName("app.name");
                if (appNameNodes.getLength() > 0) {
                    appName = appNameNodes.item(0).getTextContent().trim();
                }

                NodeList mainClassNodes = profile.getElementsByTagName("app.main.class");
                if (mainClassNodes.getLength() > 0) {
                    mainClass = mainClassNodes.item(0).getTextContent().trim();
                }
                break;
            }
        }

        assertTrue(foundWindowsProfile, "Profile 'package-windows' must be defined in pom.xml");
        assertEquals("BookstoreBilling", appName, "app.name property must be 'BookstoreBilling'");
        assertEquals("com.example.t.RunFX", mainClass, "app.main.class property must be 'com.example.t.RunFX'");
    }

    @Test
    @DisplayName("pom.xml package-windows profile must configure maven-dependency-plugin and maven-jar-plugin")
    void testPomXmlConfiguresDependencyAndJarPlugins() throws Exception {
        String pomContent = Files.readString(pomXmlPath);
        assertTrue(pomContent.contains("<id>package-windows</id>"), "pom.xml must contain package-windows profile id");
        assertTrue(pomContent.contains("maven-dependency-plugin"), "Profile must configure maven-dependency-plugin");
        assertTrue(pomContent.contains("copy-dependencies"), "Profile must bind copy-dependencies goal");
        assertTrue(pomContent.contains("<outputDirectory>${project.build.directory}/libs</outputDirectory>"),
                "copy-dependencies must output to target/libs");
        assertTrue(pomContent.contains("<includeScope>runtime</includeScope>"),
                "copy-dependencies must include runtime dependencies");
        assertTrue(pomContent.contains("maven-jar-plugin"), "Profile must configure maven-jar-plugin");
        assertTrue(pomContent.contains("<mainClass>com.example.t.RunFX</mainClass>"),
                "maven-jar-plugin must specify com.example.t.RunFX as mainClass");
    }

    @Test
    @DisplayName("Packaging scripts (package.ps1, package.bat) and icon.ico must exist")
    void testScriptsExistAndAreNonEmpty() throws Exception {
        assertTrue(Files.exists(packagePs1Path), "package.ps1 must exist at project root");
        assertTrue(Files.size(packagePs1Path) > 100, "package.ps1 must not be empty");

        assertTrue(Files.exists(packageBatPath), "package.bat must exist at project root");
        assertTrue(Files.size(packageBatPath) > 100, "package.bat must not be empty");

        assertTrue(Files.exists(iconPath), "icon.ico must exist at project root");
        assertTrue(Files.size(iconPath) > 0, "icon.ico must not be empty");
    }

    @Test
    @DisplayName("Main launcher class com.example.t.RunFX must exist with public static void main")
    void testMainLauncherClassContract() throws Exception {
        Class<?> runFxClass = Class.forName("com.example.t.RunFX");
        assertNotNull(runFxClass, "com.example.t.RunFX class must be loadable");

        Method mainMethod = runFxClass.getMethod("main", String[].class);
        assertNotNull(mainMethod, "main(String[] args) method must exist");

        int modifiers = mainMethod.getModifiers();
        assertTrue(Modifier.isPublic(modifiers), "main method must be public");
        assertTrue(Modifier.isStatic(modifiers), "main method must be static");
        assertEquals(void.class, mainMethod.getReturnType(), "main method must return void");
    }

    @Test
    @DisplayName("package.ps1 -DryRun must execute cleanly with exit code 0 and valid output")
    void testPowerShellScriptDryRun() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-ExecutionPolicy", "Bypass",
                "-File", packagePs1Path.toString(),
                "-DryRun"
        );
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
            }
        }

        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        assertTrue(finished, "package.ps1 -DryRun timed out after 15 seconds");
        assertEquals(0, process.exitValue(), "package.ps1 -DryRun must exit with code 0");

        String fullOutput = String.join("\n", outputLines);
        assertTrue(fullOutput.contains("BookstoreBilling"), "Output must mention BookstoreBilling");
        assertTrue(fullOutput.contains("com.example.t.RunFX"), "Output must mention main class com.example.t.RunFX");
        assertTrue(fullOutput.contains("DRY RUN MODE ENABLED"), "Output must acknowledge dry run mode");
        assertTrue(fullOutput.contains("package-windows"), "Output must reference package-windows profile");
        assertTrue(fullOutput.contains("Dry-run validation successful"), "Output must confirm dry run success");
    }

    @Test
    @DisplayName("package.bat /dry-run must execute cleanly with exit code 0 and valid output")
    void testBatchScriptDryRun() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe",
                "/c",
                packageBatPath.toString(),
                "/dry-run"
        );
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
            }
        }

        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        assertTrue(finished, "package.bat /dry-run timed out after 15 seconds");
        assertEquals(0, process.exitValue(), "package.bat /dry-run must exit with code 0");

        String fullOutput = String.join("\n", outputLines);
        assertTrue(fullOutput.contains("BookstoreBilling"), "Output must mention BookstoreBilling");
        assertTrue(fullOutput.contains("com.example.t.RunFX"), "Output must mention main class com.example.t.RunFX");
        assertTrue(fullOutput.contains("DRY RUN MODE ENABLED"), "Output must acknowledge dry run mode");
        assertTrue(fullOutput.contains("Dry-run validation successful"), "Output must confirm dry run success");
    }

    @Test
    @DisplayName("package.bat --help must display usage with exit code 0")
    void testBatchScriptHelp() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe",
                "/c",
                packageBatPath.toString(),
                "--help"
        );
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
            }
        }

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        assertTrue(finished, "package.bat --help timed out");
        assertEquals(0, process.exitValue(), "package.bat --help must exit with code 0");

        String fullOutput = String.join("\n", outputLines);
        assertTrue(fullOutput.contains("Usage: package.bat"), "Output must show usage information");
        assertTrue(fullOutput.contains("/dry-run"), "Output must mention /dry-run option");
    }

    @Test
    @DisplayName("Packaging scripts must configure essential jlink modules and jpackage arguments")
    void testScriptPackagingParametersConsistency() throws Exception {
        String ps1Content = Files.readString(packagePs1Path);
        String batContent = Files.readString(packageBatPath);

        String[] requiredModules = {
                "java.base",
                "java.desktop",
                "java.sql",
                "java.xml",
                "java.logging",
                "jdk.unsupported"
        };

        for (String mod : requiredModules) {
            assertTrue(ps1Content.contains(mod), "package.ps1 must include jlink module: " + mod);
            assertTrue(batContent.contains(mod), "package.bat must include jlink module: " + mod);
        }

        // Verify jpackage flags
        assertTrue(ps1Content.contains("--type") && ps1Content.contains("app-image"),
                "package.ps1 must use --type app-image");
        assertTrue(batContent.contains("--type") && batContent.contains("app-image"),
                "package.bat must use --type app-image");

        assertTrue(ps1Content.contains("--main-class") && ps1Content.contains("com.example.t.RunFX"),
                "package.ps1 must configure --main-class com.example.t.RunFX");
        assertTrue(batContent.contains("--main-class") && batContent.contains("com.example.t.RunFX"),
                "package.bat must configure --main-class com.example.t.RunFX");

        assertTrue(ps1Content.contains("--runtime-image"), "package.ps1 must link bundled runtime JRE");
        assertTrue(batContent.contains("--runtime-image"), "package.bat must link bundled runtime JRE");
    }

    @Test
    @DisplayName("Packaged distribution artifacts (if generated in target/dist) must have valid PE header and JRE")
    void testPackagedDistributionArtifactsIfPresent() throws Exception {
        Path distAppDir = projectRoot.resolve("target").resolve("dist").resolve("BookstoreBilling");
        if (!Files.exists(distAppDir)) {
            // If dist has not been created yet, this test gracefully passes
            return;
        }

        Path exePath = distAppDir.resolve("BookstoreBilling.exe");
        assertTrue(Files.exists(exePath), "BookstoreBilling.exe must exist in dist directory");
        assertTrue(Files.size(exePath) > 100_000, "BookstoreBilling.exe size should be > 100KB");

        // Validate PE executable format (first two bytes 'M' and 'Z')
        byte[] header = new byte[2];
        try (var is = Files.newInputStream(exePath)) {
            int read = is.read(header);
            assertEquals(2, read, "Should read 2 header bytes");
            assertEquals((byte) 0x4D, header[0], "First byte of executable must be 'M'");
            assertEquals((byte) 0x5A, header[1], "Second byte of executable must be 'Z'");
        }

        // Validate bundled runtime JRE directory
        Path runtimeDir = distAppDir.resolve("runtime");
        assertTrue(Files.isDirectory(runtimeDir), "runtime directory must exist in app image");
        assertTrue(Files.isDirectory(runtimeDir.resolve("bin")), "runtime/bin directory must exist");
        assertTrue(Files.isDirectory(runtimeDir.resolve("lib")), "runtime/lib directory must exist");

        // Validate app directory containing jars and cfg
        Path appDir = distAppDir.resolve("app");
        assertTrue(Files.isDirectory(appDir), "app directory must exist in app image");
        Path cfgPath = appDir.resolve("BookstoreBilling.cfg");
        assertTrue(Files.exists(cfgPath), "BookstoreBilling.cfg must exist in app directory");

        String cfgContent = Files.readString(cfgPath);
        assertTrue(cfgContent.contains("app.mainclass=com.example.t.RunFX"),
                "BookstoreBilling.cfg must configure app.mainclass=com.example.t.RunFX");
    }
}
