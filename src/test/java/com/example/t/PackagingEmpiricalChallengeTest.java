package com.example.t;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 5:
 * Standalone Windows Packaging Configuration.
 *
 * Stress-tests:
 * 1. CLI flag permutations, dry-run invariants, and invalid arguments.
 * 2. Missing JDK tools / corrupt JAVA_HOME failure mode handling.
 * 3. PE executable binary structure (MZ magic, PE signature, AMD64 architecture).
 * 4. Bundled runtime JRE zero-dependency isolation (java.dll, jvm.dll, release metadata).
 * 5. Complete module resolution matching all application dependencies.
 * 6. Standalone execution of bundled java.exe with isolated PATH and unset JAVA_HOME.
 */
public class PackagingEmpiricalChallengeTest {

    private static Path projectRoot;
    private static Path packagePs1;
    private static Path packageBat;
    private static Path distAppDir;
    private static Path distExe;
    private static Path distRuntime;
    private static Path distApp;

    @BeforeAll
    static void setUp() {
        projectRoot = Paths.get(".").toAbsolutePath().normalize();
        packagePs1 = projectRoot.resolve("package.ps1");
        packageBat = projectRoot.resolve("package.bat");
        distAppDir = projectRoot.resolve("target").resolve("dist").resolve("BookstoreBilling");
        distExe = distAppDir.resolve("BookstoreBilling.exe");
        distRuntime = distAppDir.resolve("runtime");
        distApp = distAppDir.resolve("app");
    }

    @Test
    @DisplayName("Challenge 1: package.ps1 must reject unrecognized arguments with non-zero exit code")
    void testPowerShellRejectsInvalidArguments() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-File", packagePs1.toString(),
                "-InvalidParameterXYZ"
        );
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = readProcessOutput(process);
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);

        assertTrue(finished, "Process timed out");
        assertNotEquals(0, process.exitValue(), "package.ps1 must fail on invalid parameter");
        assertTrue(output.contains("NamedParameterNotFound") || output.contains("InvalidParameterXYZ"),
                "Output should indicate parameter error: " + output);
    }

    @Test
    @DisplayName("Challenge 2: package.bat must fail gracefully when jpackage/jlink are missing from environment")
    void testBatchFailsWhenJdkToolsMissing() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe",
                "/c",
                packageBat.toString(),
                "/dry-run"
        );
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        // Sanitize environment: point JAVA_HOME to non-existent directory and strip JDK from PATH
        pb.environment().put("JAVA_HOME", "C:\\nonexistent_jdk_path_12345");
        pb.environment().put("PATH", "C:\\Windows\\System32;C:\\Windows");

        Process process = pb.start();
        String output = readProcessOutput(process);
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);

        assertTrue(finished, "Process timed out");
        assertEquals(1, process.exitValue(), "package.bat must exit with code 1 when jpackage missing");
        assertTrue(output.contains("[ERROR] jpackage tool not found"),
                "Output should contain expected error message: " + output);
    }

    @Test
    @DisplayName("Challenge 3: package.ps1 must fail gracefully when jpackage/jlink are missing from environment")
    void testPowerShellFailsWhenJdkToolsMissing() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-File", packagePs1.toString(),
                "-DryRun"
        );
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        // Sanitize environment: point JAVA_HOME to non-existent directory and strip JDK from PATH
        pb.environment().put("JAVA_HOME", "C:\\nonexistent_jdk_path_12345");
        pb.environment().put("PATH", "C:\\Windows\\System32;C:\\Windows");

        Process process = pb.start();
        String output = readProcessOutput(process);
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);

        assertTrue(finished, "Process timed out");
        assertEquals(1, process.exitValue(), "package.ps1 must exit with code 1 when jpackage missing");
        assertTrue(output.contains("jpackage tool not found") || output.contains("ERROR:"),
                "Output should report tool missing: " + output);
    }

    @Test
    @DisplayName("Challenge 4: Dry-run invariant - package.ps1 -DryRun must not execute destructive file operations")
    void testDryRunPreservesExistingFiles() throws Exception {
        // Create a sentinel marker file inside target to verify dry-run does not blow away target
        Path marker = projectRoot.resolve("target").resolve("dryrun_sentinel.tmp");
        Files.writeString(marker, "sentinel_content");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", packagePs1.toString(),
                    "-DryRun"
            );
            pb.directory(projectRoot.toFile());
            Process p = pb.start();
            p.waitFor(15, TimeUnit.SECONDS);

            assertEquals(0, p.exitValue(), "Dry-run should succeed");
            assertTrue(Files.exists(marker), "Dry-run must not delete non-target files or wipe target");
        } finally {
            Files.deleteIfExists(marker);
        }
    }

    @Test
    @DisplayName("Challenge 5: BookstoreBilling.exe must be a valid 64-bit Windows PE executable (AMD64)")
    void testExecutableBinaryHeaderAndArchitecture() throws Exception {
        assertTrue(Files.exists(distExe), "BookstoreBilling.exe must exist at " + distExe);
        long fileSize = Files.size(distExe);
        assertTrue(fileSize > 200_000, "Executable should be larger than 200KB, was: " + fileSize);

        byte[] fileBytes = Files.readAllBytes(distExe);
        assertTrue(fileBytes.length > 0x100, "Executable is too small to contain PE headers");

        // DOS Header: MZ signature (0x4D, 0x5A)
        assertEquals((byte) 0x4D, fileBytes[0], "Byte 0 must be 'M'");
        assertEquals((byte) 0x5A, fileBytes[1], "Byte 1 must be 'Z'");

        // e_lfanew: Offset to PE header at byte 0x3C (4-byte little endian integer)
        int peOffset = (fileBytes[0x3C] & 0xFF)
                | ((fileBytes[0x3D] & 0xFF) << 8)
                | ((fileBytes[0x3E] & 0xFF) << 16)
                | ((fileBytes[0x3F] & 0xFF) << 24);

        assertTrue(peOffset > 0 && peOffset + 6 < fileBytes.length, "Invalid PE header offset: " + peOffset);

        // PE signature: 'P', 'E', 0x00, 0x00
        assertEquals((byte) 'P', fileBytes[peOffset]);
        assertEquals((byte) 'E', fileBytes[peOffset + 1]);
        assertEquals((byte) 0x00, fileBytes[peOffset + 2]);
        assertEquals((byte) 0x00, fileBytes[peOffset + 3]);

        // Machine Architecture: Offset peOffset + 4 (2-byte little endian)
        // 0x8664 = IMAGE_FILE_MACHINE_AMD64 (x64)
        int machineType = (fileBytes[peOffset + 4] & 0xFF) | ((fileBytes[peOffset + 5] & 0xFF) << 8);
        assertEquals(0x8664, machineType, "Executable machine architecture must be AMD64 (0x8664)");
    }

    @Test
    @DisplayName("Challenge 6: Bundled runtime must contain zero-dependency core binaries (java.dll, jvm.dll)")
    void testBundledRuntimeCoreBinaries() throws Exception {
        assertTrue(Files.isDirectory(distRuntime), "Bundled runtime directory must exist: " + distRuntime);

        Path javaDll = distRuntime.resolve("bin").resolve("java.dll");
        assertTrue(Files.exists(javaDll), "java.dll must exist in runtime/bin");
        assertTrue(Files.size(javaDll) > 50_000, "java.dll must be non-empty");

        Path jvmDll = distRuntime.resolve("bin").resolve("server").resolve("jvm.dll");
        assertTrue(Files.exists(jvmDll), "jvm.dll must exist in runtime/bin/server");
        assertTrue(Files.size(jvmDll) > 1_000_000, "jvm.dll must be non-empty (> 1MB)");

        Path javaExe = distRuntime.resolve("bin").resolve("java.exe");
        assertTrue(Files.exists(javaExe), "java.exe must exist in runtime/bin");
    }

    @Test
    @DisplayName("Challenge 7: Bundled runtime must run standalone with isolated PATH and without JAVA_HOME")
    void testBundledRuntimeExecutesInIsolation() throws Exception {
        Path javaExe = distRuntime.resolve("bin").resolve("java.exe");
        assertTrue(Files.exists(javaExe), "Bundled java.exe must exist");

        ProcessBuilder pb = new ProcessBuilder(
                javaExe.toString(),
                "-version"
        );
        pb.directory(distAppDir.toFile());
        pb.redirectErrorStream(true);

        // Complete isolation: Remove JAVA_HOME and restrict PATH
        pb.environment().remove("JAVA_HOME");
        pb.environment().put("PATH", "C:\\Windows\\System32;C:\\Windows");

        Process process = pb.start();
        String output = readProcessOutput(process);
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);

        assertTrue(finished, "Bundled java.exe timed out");
        assertEquals(0, process.exitValue(), "Bundled java.exe must execute successfully");
        assertTrue(output.contains("21.0."), "Output must identify OpenJDK 21: " + output);
        assertTrue(output.contains("64-Bit Server VM"), "Output must confirm 64-Bit Server VM: " + output);
    }

    @Test
    @DisplayName("Challenge 8: JLink bundled modules must satisfy all required dependencies (SQLite, OpenPDF, AtlantaFX)")
    void testJlinkModulesCompleteness() throws Exception {
        Path releaseFile = distRuntime.resolve("release");
        assertTrue(Files.exists(releaseFile), "release file must exist in runtime");

        String releaseContent = Files.readString(releaseFile);
        assertTrue(releaseContent.contains("JAVA_VERSION=\"21."), "release file must indicate Java 21");

        List<String> requiredModules = Arrays.asList(
                "java.base",
                "java.desktop",
                "java.sql",
                "java.xml",
                "java.logging",
                "jdk.unsupported",
                "jdk.crypto.ec"
        );

        for (String mod : requiredModules) {
            assertTrue(releaseContent.contains(mod),
                    "Runtime must bundle module '" + mod + "' required by app dependencies");
        }
    }

    @Test
    @DisplayName("Challenge 9: All bundled JARs in app directory must be valid, readable JAR/ZIP archives")
    void testBundledJarsIntegrity() throws Exception {
        assertTrue(Files.isDirectory(distApp), "app directory must exist in dist");

        List<Path> jarFiles = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(distApp, "*.jar")) {
            for (Path jar : stream) {
                jarFiles.add(jar);
            }
        }

        assertFalse(jarFiles.isEmpty(), "app directory must contain JAR files");
        assertTrue(jarFiles.stream().anyMatch(p -> p.getFileName().toString().startsWith("t-")),
                "Main application JAR must be in app directory");
        assertTrue(jarFiles.stream().anyMatch(p -> p.getFileName().toString().contains("sqlite-jdbc")),
                "sqlite-jdbc JAR must be in app directory");
        assertTrue(jarFiles.stream().anyMatch(p -> p.getFileName().toString().contains("openpdf")),
                "openpdf JAR must be in app directory");
        assertTrue(jarFiles.stream().anyMatch(p -> p.getFileName().toString().contains("atlantafx")),
                "atlantafx JAR must be in app directory");

        // Verify each JAR can be opened and parsed as a valid zip archive
        for (Path jar : jarFiles) {
            assertTrue(Files.size(jar) > 0, "JAR " + jar.getFileName() + " must not be 0 bytes");
            try (JarFile jf = new JarFile(jar.toFile())) {
                assertNotNull(jf.getName(), "JAR file " + jar.getFileName() + " should be valid ZIP");
            }
        }
    }

    @Test
    @DisplayName("Challenge 10: BookstoreBilling.cfg must specify valid main class, app version, and classpath")
    void testConfigFileValidity() throws Exception {
        Path cfgPath = distApp.resolve("BookstoreBilling.cfg");
        assertTrue(Files.exists(cfgPath), "BookstoreBilling.cfg must exist");

        String cfg = Files.readString(cfgPath);
        assertTrue(cfg.contains("app.mainclass=com.example.t.RunFX"),
                "Configuration must specify app.mainclass=com.example.t.RunFX");
        assertTrue(cfg.contains("app.classpath=$APPDIR\\t-1.0-SNAPSHOT.jar") || cfg.contains("app.classpath=$APPDIR\\t-1.1.0.jar") || cfg.contains("app.classpath=$APPDIR\\t-1.2.0.jar"),
                "Configuration must include main jar in classpath");
        assertTrue(cfg.contains("app.classpath=$APPDIR\\sqlite-jdbc"),
                "Configuration must include sqlite-jdbc in classpath");
        assertTrue(cfg.contains("app.classpath=$APPDIR\\openpdf"),
                "Configuration must include openpdf in classpath");
    }

    @Test
    @DisplayName("Challenge 11: All packaging scripts and metadata must declare consistent version 1.2.0")
    void testPackagingScriptsVersionConsistency() throws Exception {
        String pomContent = Files.readString(projectRoot.resolve("pom.xml"));
        assertTrue(pomContent.contains("<version>1.2.0</version>"), "pom.xml must declare version 1.2.0");
        assertTrue(pomContent.contains("<app.version>1.2.0</app.version>"), "pom.xml must declare app.version 1.2.0");

        String ps1Content = Files.readString(packagePs1);
        assertTrue(ps1Content.contains("$AppVersion = \"1.2.0\""), "package.ps1 must declare AppVersion 1.2.0");

        String batContent = Files.readString(packageBat);
        assertTrue(batContent.contains("set APP_VERSION=1.2.0"), "package.bat must declare APP_VERSION 1.2.0");

        Path issPath = projectRoot.resolve("installer.iss");
        if (Files.exists(issPath)) {
            String issContent = Files.readString(issPath);
            assertTrue(issContent.contains("#define MyAppVersion \"1.2.0\""), "installer.iss must declare MyAppVersion 1.2.0");
        }
    }

    private static String readProcessOutput(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
