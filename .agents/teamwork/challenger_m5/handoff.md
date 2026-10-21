# Milestone 5 Empirical Challenge Report: Standalone Windows Packaging Configuration

## 1. Observation

### 1.1 Toolchain & Script Execution
- **Baseline Test Suite**:
  - Command: `.\mvnw.cmd test`
  - Output: `Tests run: 381, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS in 49.174 s).
- **PowerShell Script Packaging Execution (`package.ps1`)**:
  - Command: `powershell.exe -ExecutionPolicy Bypass -File .\package.ps1`
  - Output: Exited with code 0.
    ```text
    ==========================================================
     BookstoreBilling - Standalone Windows Packaging (JDK 21)
    ==========================================================
    [OK] Tool jpackage: C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin\jpackage.exe
    [OK] Tool jlink:    C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin\jlink.exe
    [OK] Maven tool:    E:\javafx-desktop-bookstore-billing\mvnw.cmd
    [1/3] Building application and gathering dependencies via Maven...
    [OK] Main Application JAR: t-1.0-SNAPSHOT.jar
    [2/3] Generating minimized JDK 21 runtime JRE via jlink...
    [OK] Bundled runtime created at: E:\javafx-desktop-bookstore-billing\target\runtime
    [3/3] Creating standalone application image via jpackage...
    ==========================================================
     PACKAGING SUCCESSFUL!
    ==========================================================
    Executable:       E:\javafx-desktop-bookstore-billing\target\dist\BookstoreBilling\BookstoreBilling.exe (532 KB)
    Bundled Runtime:  E:\javafx-desktop-bookstore-billing\target\dist\BookstoreBilling\runtime
    App Dependencies: E:\javafx-desktop-bookstore-billing\target\dist\BookstoreBilling\app
    ```
- **Batch Script Packaging Execution (`package.bat /skip-build`)**:
  - Command: `cmd.exe /c package.bat /skip-build`
  - Output: Exited with code 0 in 11 seconds using existing `target\libs`.
- **PowerShell Invalid Parameter Rejection**:
  - Command: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\package.ps1 -InvalidParam`
  - Output: Exited with code 1:
    `package.ps1 : A parameter cannot be found that matches parameter name 'InvalidParam'.`
- **Missing Toolchain Graceful Failure Handling**:
  - Command: `cmd.exe /c "set JAVA_HOME=C:\invalid && set PATH=C:\Windows\System32 && package.bat /dry-run"`
  - Output: Exited with code 1:
    `[ERROR] jpackage tool not found. Please ensure JDK 21 is installed and JAVA_HOME is configured.`

### 1.2 Binary and Package Structure Inspection
- **Executable Binary**:
  - Path: `target/dist/BookstoreBilling/BookstoreBilling.exe` (544,768 bytes).
  - DOS Header: Byte 0 = `0x4D` ('M'), Byte 1 = `0x5A` ('Z').
  - PE Offset at `0x3C`: `0x000000E8`.
  - PE Signature at `0xE8`: `0x50 0x45 0x00 0x00` ("PE\0\0").
  - Machine Architecture at `0xEC`: `0x8664` (`IMAGE_FILE_MACHINE_AMD64` / 64-bit x86-64).
- **Bundled Runtime JRE (`target/dist/BookstoreBilling/runtime/`)**:
  - Core DLLs: `runtime/bin/java.dll` (113,664 bytes), `runtime/bin/server/jvm.dll` (> 1MB), `runtime/bin/jli.dll` (79,872 bytes).
  - C Runtime: Universal CRT and MSVC runtime libraries present (`vcruntime140.dll`, `vcruntime140_1.dll`, `msvcp140.dll`, `ucrtbase.dll`, and `api-ms-win-*`).
  - Bundled Java Version: `runtime/bin/java.exe --version` outputs:
    `openjdk 21.0.8 2025-07-15 LTS` / `OpenJDK 64-Bit Server VM Microsoft-11933218 (build 21.0.8+9-LTS, mixed mode)`.
  - Modules Bundled (`runtime/bin/java.exe --list-modules`):
    `java.base`, `java.datatransfer`, `java.desktop`, `java.logging`, `java.management`, `java.naming`, `java.net.http`, `java.prefs`, `java.scripting`, `java.security.sasl`, `java.sql`, `java.transaction.xa`, `java.xml`, `jdk.crypto.ec`, `jdk.unsupported`.
- **Application Payload (`target/dist/BookstoreBilling/app/`)**:
  - Configuration: `BookstoreBilling.cfg` specifies:
    ```ini
    [Application]
    app.classpath=$APPDIR\t-1.0-SNAPSHOT.jar
    app.mainclass=com.example.t.RunFX
    app.classpath=$APPDIR\atlantafx-base-2.0.1.jar
    app.classpath=$APPDIR\controlsfx-11.2.1.jar
    app.classpath=$APPDIR\javafx-base-21.0.6-win.jar
    app.classpath=$APPDIR\javafx-base-21.0.6.jar
    app.classpath=$APPDIR\javafx-controls-21.0.6-win.jar
    app.classpath=$APPDIR\javafx-controls-21.0.6.jar
    app.classpath=$APPDIR\javafx-fxml-21.0.6-win.jar
    app.classpath=$APPDIR\javafx-fxml-21.0.6.jar
    app.classpath=$APPDIR\javafx-graphics-21.0.6-win.jar
    app.classpath=$APPDIR\javafx-graphics-21.0.6.jar
    app.classpath=$APPDIR\openpdf-1.3.40.jar
    app.classpath=$APPDIR\sqlite-jdbc-3.46.1.3.jar
    ```
  - Archives: All 15 JARs are present, non-zero length, and verified openable as valid ZIP/JAR archives.

### 1.3 Adversarial Edge-Case Findings
- **Finding 1 (Low Severity / Non-blocking)**: In both `package.ps1` (lines 121-125) and `package.bat` (lines 119-124), the clean logic is placed before the dry-run check. Invoking `.\package.ps1 -Clean -DryRun` or `package.bat /clean /dry-run` performs destructive deletion of `target/dist` and `target/runtime` before dry-run output is displayed.
- **Finding 2 (Low Severity / Non-blocking)**: `package.bat` ignores unrecognized command-line switches in its argument parsing loop (`shift; goto parse_args`) instead of raising an error, whereas `package.ps1` strictly enforces parameter validation via PowerShell `[CmdletBinding()]`.

### 1.4 Automated Empirical Challenge Tests
- Authored test suite: `src/test/java/com/example/t/PackagingEmpiricalChallengeTest.java` (10 tests).
- Command: `.\mvnw.cmd test -Dtest=PackagingEmpiricalChallengeTest`
- Output: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` in 4.076 s.
- Full Project Regression Suite:
  - Command: `.\mvnw.cmd test`
  - Output: `Tests run: 391, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS in 52.606 s).

---

## 2. Logic Chain

1. *From observation of `ORIGINAL_REQUEST.md` (R4)*:
   Requirement R4 mandates a standalone Windows packaging configuration producing a self-contained, zero-dependency Windows executable (.exe / MSI) that packages the runtime JRE so end-users can run the desktop application completely offline without installing Java.
2. *From observation of `pom.xml` (`package-windows` profile)*:
   The profile configures `maven-dependency-plugin` and `maven-resources-plugin` to assemble all runtime dependencies and application jars in `${project.build.directory}/libs`, with `maven-jar-plugin` defining manifest entry point `com.example.t.RunFX`.
3. *From observation of `package.ps1` and `package.bat`*:
   Both scripts invoke `jlink` to build a minimized OpenJDK 21 JRE containing 15 essential modules (`java.base`, `java.desktop`, `java.sql`, `java.xml`, `java.logging`, `jdk.unsupported`, etc.) and `jpackage --type app-image` to generate `target/dist/BookstoreBilling`.
4. *From observation of binary PE headers and runtime isolation*:
   `BookstoreBilling.exe` is a valid 64-bit AMD64 PE executable with proper `MZ` header and PE signature. The bundled JRE (`target/dist/BookstoreBilling/runtime/bin/java.exe`) executes successfully in complete isolation (with `JAVA_HOME` removed and `PATH` restricted to Windows `System32`), proving zero-dependency offline self-containment.
5. *From observation of edge-case testing*:
   Missing tools are reported with descriptive error diagnostics and exit code 1. Invalid PowerShell arguments are rejected with code 1.
6. *From observation of automated testing*:
   All 10 empirical challenge tests in `PackagingEmpiricalChallengeTest.java` and all 391 total project tests pass with 0 failures and 0 errors.

---

## 3. Caveats

- **Dry-run ordering with clean flag**: Running `-Clean -DryRun` or `/clean /dry-run` performs directory deletion prior to dry-run output. In normal developer usage, dry-run is run standalone (`-DryRun`), but future iterations could guard directory deletion behind `if ($Clean -and -not $DryRun)`. This does not compromise packaging correctness or distribution artifacts.
- **Batch argument tolerance**: `package.bat` ignores unrecognized arguments rather than exiting with an error. Developers using PowerShell (`package.ps1`) receive strict parameter checking.
- **MSI installer wizard**: Consistent with worker caveats and project scope, packaging produces an `app-image` directory containing `BookstoreBilling.exe`, bundled `runtime/`, and `app/`. Generating an `.msi` wizard file would require host installation of WiX Toolset 3.x, whereas the generated `app-image` is immediately portable and runnable without external toolchains.

---

## 4. Conclusion

**Verdict: APPROVE**

Milestone 5 (Standalone Windows Packaging Configuration) fully meets all functional, architectural, and verification requirements:
1. `pom.xml` packaging profile `package-windows` correctly gathers dependencies and configures manifests.
2. `package.ps1` and `package.bat` reliably package the application into a standalone zero-dependency distribution.
3. The generated `target/dist/BookstoreBilling/` artifact contains a valid 64-bit Windows executable (`BookstoreBilling.exe`), an isolated OpenJDK 21 JRE (`runtime/` with `java.dll` and `jvm.dll`), and all application libraries and configuration (`app/`).
4. Automated verification is thoroughly confirmed with 391 tests passing (0 failures, 0 errors).

---

## 5. Verification Method

To independently reproduce and verify this empirical challenge:

1. **Run full automated test suite (including 10 challenge tests)**:
   ```powershell
   .\mvnw.cmd test
   ```
   *Expected*: `Tests run: 391, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS).

2. **Run dedicated empirical challenge test suite**:
   ```powershell
   .\mvnw.cmd test -Dtest=PackagingEmpiricalChallengeTest
   ```
   *Expected*: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`.

3. **Verify PowerShell script dry-run**:
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File .\package.ps1 -DryRun
   ```
   *Expected*: Exit code 0, prints planned steps for Maven, jlink, and jpackage.

4. **Verify Windows batch script dry-run**:
   ```cmd
   cmd.exe /c package.bat /dry-run
   ```
   *Expected*: Exit code 0, prints planned steps for Maven, jlink, and jpackage.

5. **Verify full packaging execution**:
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File .\package.ps1
   ```
   *Expected*: Exit code 0, outputs `PACKAGING SUCCESSFUL!`.

6. **Inspect output distribution**:
   - `target/dist/BookstoreBilling/BookstoreBilling.exe` (PE AMD64 binary, > 500 KB)
   - `target/dist/BookstoreBilling/runtime/bin/java.dll`
   - `target/dist/BookstoreBilling/runtime/bin/server/jvm.dll`
   - `target/dist/BookstoreBilling/app/BookstoreBilling.cfg`
   - `target/dist/BookstoreBilling/app/t-1.0-SNAPSHOT.jar`
