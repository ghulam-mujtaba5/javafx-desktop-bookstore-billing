## 2026-10-20T06:15:52Z
[Message] timestamp=2026-10-20T06:15:52Z sender=12ab0fc1-3462-46a0-a5f2-75a160f827df priority=MESSAGE_PRIORITY_HIGH content=You are worker_m5 for Milestone 5 (Standalone Windows Packaging Configuration) in e:\javafx-desktop-bookstore-billing.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m5
Your parent is: orchestrator_gen2 (conversation ID: 12ab0fc1-3462-46a0-a5f2-75a160f827df)

MANDATORY FIRST STEP: Read the authoritative requirements at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
and the project architecture at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your write ownership:
- pom.xml (add dedicated Windows packaging profile)
- package.ps1 (PowerShell packaging script)
- package.bat (Windows batch packaging script)
- src/test/java/com/example/t/PackagingConfigurationTest.java (automated tests verifying packaging configuration)

Your task:
1. Check JDK 21 tools available in path or JAVA_HOME (jpackage, jlink).
2. Configure a dedicated packaging profile in pom.xml (e.g. `package-windows`) that configures maven-dependency-plugin or maven-jar-plugin to prepare the application JAR and runtime dependencies.
3. Provide robust, tested packaging scripts (`package.ps1` and `package.bat`):
   - Builds project JAR and gathers runtime dependencies into target/libs/ or target/modules/.
   - Invokes jpackage (or hybrid jlink + jpackage) with `--type app-image`, `--name BookstoreBilling`, `--main-jar`, `--main-class com.example.t.RunFX`, bundling a custom/minimized JDK 21 runtime JRE so end-users can run the app offline without Java installed.
   - Outputs to `target/dist/BookstoreBilling/` containing `BookstoreBilling.exe`, bundled `runtime/` JRE, and app modules/jars.
   - Includes graceful error handling and dry-run/verification switches.
4. Add automated test class `src/test/java/com/example/t/PackagingConfigurationTest.java` that tests:
   - Packaging profile presence and configuration in pom.xml
   - Existence and executable syntax of package.ps1 and package.bat
   - Validation of main launcher class (`com.example.t.RunFX`), app name (`BookstoreBilling`), and runtime packaging parameters.
5. Execute the packaging script or verify jpackage packaging dry-run, and run `.\mvnw.cmd test` to ensure all tests pass.
6. Write your handoff report to: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m5\handoff.md.
7. Send completion message to parent when done.
