# Backlog — Puzzlewerk

> Sammelstelle für Ideen und Verbesserungen außerhalb des aktuellen
> Auftrags. Jeder Agent darf hier ergänzen; der Orchestrator priorisiert.

## Technik
- [ ] gitleaks als CI-Schritt ergänzen (Regel S7); bis dahin prüft der
      security-auditor manuell je PR
- [ ] OWASP Dependency-Check oder `gradle-versions-plugin` + CVE-Scan
      in CI aufnehmen (Regel S6) — braucht ADR
- [ ] Renovate/Dependabot für Dependency-Updates konfigurieren
- [ ] Dependency Verification auf PGP-Signaturen erweitern
      (`verify-signatures=true` + trusted-keys) — Zeitpunkt und
      Trigger-Kriterien in ADR-002 entschieden (bewusst verschoben;
      spätestens vor dem ersten an Endnutzer verteilten Release-Build)
- [ ] Merkregel aus PW-0.3: verification-metadata.xml IMMER mit
      `--refresh-dependencies --write-verification-metadata sha256 <Gate-Tasks>`
      regenerieren — mit warmem lokalem Cache fehlen sonst
      .pom-/.module-Varianten, die nur ein kalter CI-Cache auflöst.
      Kandidat für docs/architektur.md (Verbindliche Kommandos)
- [ ] Release-Engineer: Umsetzungsticket PW-0.4-impl aus ADR-002 —
      `gradlePluginPortal()` in settings.gradle.kts per Content-Filter
      auf `org.jlleitschuh.gradle(.ktlint)` beschränken; exakter
      Code-Block steht im ADR. Bei jedem Ktlint-Gradle-Update prüfen,
      ob das Plugin inzwischen auf Maven Central liegt
      (Rückbau-Trigger: Portal dann ganz entfernen, neues ADR)
- [ ] targetSdk 36 wurde in PW-0.1 gesetzt, weil AGP 8.13.2 sonst den
      Lint-Error OldTargetApi wirft und C7 Baselines verbietet.
      Konsequenzen für Phase 3 einplanen: Edge-to-Edge ist ab
      targetSdk 35+ erzwungen (Insets sauber behandeln), Predictive
      Back muss unterstützt und getestet werden
- [ ] Jährlicher targetSdk-Bump als fester Prozess (PW-0.5): Lint-Check
      OldTargetApi ist in app/build.gradle.kts deaktiviert, weil er
      umgebungsabhängig ist (Lint liest die "neueste" API aus den im
      Runner-SDK installierten Platforms; ubuntu-latest rot, lokal grün
      bei identischem Commit — CI-Run 29031584439). Ersatzmechanismus:
      compileSdk/targetSdk gemeinsam mit AGP-Update pro Android-Release
      per ADR anheben (Trigger: Play-Policy-Deadline, üblicherweise
      31. August); ExpiredTargetSdkVersion bleibt als harter Gate aktiv
- [x] Architekt: `gradlePluginPortal()` in settings.gradle.kts
      (pluginManagement) kollidiert mit dem S6-Wortlaut („nur google() +
      mavenCentral()") — entschieden in ADR-002 (PW-0.4): Portal bleibt,
      per Content-Filter auf Ktlint-Gradle beschränkt, S6 präzisiert;
      Umsetzung siehe Ticket PW-0.4-impl oben (Security-Befund L2
      geschlossen)
- [ ] Konsist- oder ArchUnit-Tests, die die Schichtenregel maschinell
      erzwingen (statt nur per Review)
- [ ] Robolectric + Compose-UI-Test-Setup für :app (ab Phase 3 nötig)
- [x] gradlew hat mit PW-0.1 das Executable-Bit verloren (Windows-Checkout,
      Mode 100755 → 100644). Vor dem Linux-CI-Lauf (PW-0.2) per
      `git update-index --chmod=+x gradlew` wiederherstellen, sonst
      schlägt `./gradlew` im CI fehl — erledigt in PW-0.2

## Produkt
- (leer — Ideen des game-designers landen hier)
