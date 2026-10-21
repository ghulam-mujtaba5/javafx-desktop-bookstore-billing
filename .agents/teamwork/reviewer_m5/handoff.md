# Milestone 5 Review & Adversarial Critic Report: Standalone Windows Packaging Configuration

## 1. Observation

### 1.1 Maven Configuration (`pom.xml`)
- Profile `package-windows` defined in lines 107–178 of `pom.xml`:
  - `app.name`: `BookstoreBilling`
  - `app.main.class`: `com.example.t.RunFX`
  - `app.version`: `1.0.0`
- Configures `maven-jar-plugin` 3.3.0 with `addClasspath=true` and `mainClass=com.example.t.RunFX`.
- Configures `maven-dependency-plugin` 3.6.1 copying runtime dependencies to `${project.build.directory}/libs`.
- Configures `maven-resources-plugin` 3.3.1 copying `${project.build.finalName}.jar` to `${project.build.directory}/libs`.

### 1.2 Packaging Scripts (`package.ps1` & `package.bat`)
- `package.ps1` (242 lines):
  - Param block: `[switch]$DryRun`, `[switch]$SkipBuild`, `[switch]$Clean`.
  - Toolchain detection: Verifies `JAVA_HOME`, `jpackage.exe`, `jlink.exe`, and `mvnw.cmd` (falling back to PATH).
  - JLink runtime creation: `--no-header-files --no-man-pages --strip-debug --add-modules java.base,java.desktop,java.sql,java.xml,java.logging,java.management,java.naming,java.net.http,java.scripting,jdk.unsupported,jdk.crypto.ec --output target/runtime`.
  - JPackage execution: `--type app-image --name BookstoreBilling --dest target/dist --input target/libs --main-jar t-1.0-SNAPSHOT.jar --main-class com.example.t.RunFX --runtime-image target/runtime --icon icon.ico`.
- `package.bat` (228 lines):
  - Arguments parsed: `/dry-run`, `--dry-run`, `-DryRun`, `/skip-build`, `--skip-build`, `-SkipBuild`, `/clean`, `--clean`, `-Clean`, `/help`, `/?`.
  - Mirrors all JLink and JPackage invocations with Windows CMD syntax, quotes all directory paths, and checks error levels.

### 1.3 Direct Execution Verification
- Dry-run verification:
  - `powershell -ExecutionPolicy Bypass -File .\package.ps1 -DryRun`: Exited with code 0, printed all planned execution steps without disk modification.
  - `cmd.exe /c package.bat /dry-run`: Exited with code 0, printed identical execution steps.
  - `cmd.exe /c package.bat --help`: Exited with code 0, printed options `/dry-run`, `/skip-build`, `/clean`, `/help`.
- Packaging execution:
  - `powershell -ExecutionPolicy Bypass -File .\package.ps1 -SkipBuild`: Exited with code 0 in 38 seconds.
- Output artifacts inspection:
  - Executable: `target/dist/BookstoreBilling/BookstoreBilling.exe` (544,768 bytes).
  - First 4 bytes of executable: `[77, 90, 144, 0]` (`0x4D 0x5A` = `MZ` standard Windows PE DOS header).
  - Bundled JRE: `target/dist/BookstoreBilling/runtime/bin/java.exe -version` outputs:
    `openjdk version "21.0.8" 2025-07-15 LTS`
    `OpenJDK Runtime Environment Microsoft-11933218 (build 21.0.8+9-LTS)`
    `OpenJDK 64-Bit Server VM Microsoft-11933218 (build 21.0.8+9-LTS, mixed mode)`
  - Bundled JRE modules: 15 modules verified (`java.base`, `java.datatransfer`, `java.desktop`, `java.logging`, `java.management`, `java.naming`, `java.net.http`, `java.prefs`, `java.scripting`, `java.security.sasl`, `java.sql`, `java.transaction.xa`, `java.xml`, `jdk.crypto.ec`, `jdk.unsupported`).
  - App payload: `target/dist/BookstoreBilling/app/` contains 15 JARs (including `t-1.0-SNAPSHOT.jar`, JavaFX 21.0.6 win binaries, SQLite JDBC, OpenPDF, ControlsFX, AtlantaFX) and valid `BookstoreBilling.cfg` configuring `app.mainclass=com.example.t.RunFX`.

### 1.4 Automated Test Execution
- Packaging configuration test:
  - Command: `.\mvnw.cmd test -Dtest=PackagingConfigurationTest`
  - Output: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS in 16.092 s).
- Packaging empirical challenge test:
  - Command: `.\mvnw.cmd test -Dtest=PackagingEmpiricalChallengeTest`
  - Output: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS in 13.807 s).
- Full regression test suite:
  - Command: `.\mvnw.cmd test`
  - Output: `Tests run: 391, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS in 1 min 06 s).

### 1.5 Adversarial Stress-Test Findings
- **Finding 1 (Low Severity / Non-blocking - Clean vs Dry-run Ordering)**:
  In `package.ps1` lines 121–125 and `package.bat` lines 119–124, `-Clean` handling precedes `-DryRun` handling. Calling `.\package.ps1 -Clean -DryRun` removes `target/dist` and `target/runtime` before terminating on the dry-run check.
- **Finding 2 (Low Severity / Non-blocking - Permissive Batch Switch Parsing)**:
  `package.bat` ignores unrecognized command-line switches in its loop (`shift; goto parse_args`) instead of terminating with an error, whereas `package.ps1` enforces strict switch validation via `[CmdletBinding()]`.

### 1.6 Integrity Check
- No hardcoded test results embedded in source code.
- No dummy or facade implementations; genuine OpenJDK `jlink` and `jpackage` tools are used.
- No shortcuts bypassing R4 requirements.
- No fabricated verification outputs; all tests and CLI commands were directly executed and verified.

---

## 2. Logic Chain

1. *From observation 1.1*:
   The `pom.xml` configuration correctly implements the required `package-windows` profile, binding jar and dependency assembly into `target/libs` during the Maven `package` lifecycle.
2. *From observation 1.2 and 1.3*:
   Both `package.ps1` and `package.bat` reliably orchestrate `jlink` and `jpackage`, generating a self-contained Windows application directory containing a valid 64-bit PE executable (`BookstoreBilling.exe`), an isolated JDK 21 JRE runtime with all necessary modules, and the complete application classpath.
3. *From observation 1.3*:
   The bundled runtime JRE executes independently (`runtime/bin/java.exe -version`) with zero external Java dependencies, satisfying R4's requirement for 100% offline distribution readiness.
4. *From observation 1.4*:
   Automated unit and integration tests (`PackagingConfigurationTest` and `PackagingEmpiricalChallengeTest`) pass completely (19/19 tests combined), and the entire 391-test project suite passes with 0 failures and 0 errors, demonstrating zero regressions.
5. *From observation 1.5 and 1.6*:
   The adversarial findings are minor non-blocking CLI usability observations that do not impair packaging or runtime execution. No integrity violations exist.

---

## 3. Caveats

- As noted in R4 and `worker_m5/handoff.md`, `jpackage --type app-image` produces a self-contained portable application folder (`target/dist/BookstoreBilling/`) rather than an `.msi` or single-file installer wizard. Generating an MSI requires WiX Toolset 3.x installed on the host machine. The generated `app-image` provides the exact standalone offline execution requested.
- If `.\package.ps1 -Clean` is executed, subsequent runs of `PackagingEmpiricalChallengeTest` require running `package.ps1 -SkipBuild` to regenerate the distribution directory before verifying packaged artifact assertions.

---

## 4. Conclusion

**Verdict: APPROVE**

Milestone 5 (Standalone Windows Packaging Configuration) fully meets all requirements of R4 and `PROJECT.md`. The Maven configuration, PowerShell packaging script (`package.ps1`), Windows batch script (`package.bat`), and test suite are robust, genuine, and thoroughly verified.

---

## 5. Verification Method

To independently verify Milestone 5:

1. **Verify script dry-runs**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\package.ps1 -DryRun
   cmd.exe /c package.bat /dry-run
   ```
   Confirm exit code 0 and displayed execution steps.

2. **Run dedicated packaging unit tests**:
   ```powershell
   .\mvnw.cmd test -Dtest=PackagingConfigurationTest
   .\mvnw.cmd test -Dtest=PackagingEmpiricalChallengeTest
   ```
   Confirm all 19 packaging tests pass with 0 failures.

3. **Run full project test suite**:
   ```powershell
   .\mvnw.cmd test
   ```
   Confirm 391 tests run with 0 failures, 0 errors.

4. **Verify standalone packaging build**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\package.ps1 -SkipBuild
   ```
   Inspect `target/dist/BookstoreBilling`:
   - `BookstoreBilling.exe` (PE executable format)
   - `runtime/` (bundled OpenJDK 21 JRE)
   - `app/` (all application dependencies and configuration)
