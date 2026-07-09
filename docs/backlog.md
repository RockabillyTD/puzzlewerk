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
      (`verify-signatures=true` + trusted-keys); PW-0.2 pinnt bislang
      nur SHA-256-Checksummen
- [ ] Merkregel aus PW-0.3: verification-metadata.xml IMMER mit
      `--refresh-dependencies --write-verification-metadata sha256 <Gate-Tasks>`
      regenerieren — mit warmem lokalem Cache fehlen sonst
      .pom-/.module-Varianten, die nur ein kalter CI-Cache auflöst.
      Kandidat für docs/architektur.md (Verbindliche Kommandos)
- [ ] targetSdk 36 wurde in PW-0.1 gesetzt, weil AGP 8.13.2 sonst den
      Lint-Error OldTargetApi wirft und C7 Baselines verbietet.
      Konsequenzen für Phase 3 einplanen: Edge-to-Edge ist ab
      targetSdk 35+ erzwungen (Insets sauber behandeln), Predictive
      Back muss unterstützt und getestet werden
- [ ] Architekt: `gradlePluginPortal()` in settings.gradle.kts
      (pluginManagement) kollidiert mit dem S6-Wortlaut („nur google() +
      mavenCentral()") — per ADR klären: Portal entfernen oder S6
      präzisieren (Security-Befund L2, PW-0.2)
- [ ] Konsist- oder ArchUnit-Tests, die die Schichtenregel maschinell
      erzwingen (statt nur per Review)
- [ ] Robolectric + Compose-UI-Test-Setup für :app (ab Phase 3 nötig)
- [x] gradlew hat mit PW-0.1 das Executable-Bit verloren (Windows-Checkout,
      Mode 100755 → 100644). Vor dem Linux-CI-Lauf (PW-0.2) per
      `git update-index --chmod=+x gradlew` wiederherstellen, sonst
      schlägt `./gradlew` im CI fehl — erledigt in PW-0.2

## Produkt
- (leer — Ideen des game-designers landen hier)
