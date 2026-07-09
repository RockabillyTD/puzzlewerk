# Projekt-Status — Puzzlewerk

> Wird vom Orchestrator nach jedem Arbeitszyklus gepflegt.

## Aktuelle Phase
Phase 0 — Fundament (Gerüst erstellt, Erst-Verifikation ausstehend)

## Erledigt
- [x] Verzeichnis- und Modulstruktur (:app/:game/:data/:core)
- [x] CLAUDE.md, 8 Agent-Definitionen, Orchestrator-Briefing
- [x] Gradle-Konfiguration (Version Catalog, Detekt, Ktlint, Kover)
- [x] Platzhalter-Spiellogik mit Unit- und Property-Tests
- [x] CI-Workflow (.github/workflows/ci.yml)
- [x] ADR-001 (Technologie-Stack)

## Offen (Phase 0 — Abschlusskriterien)
- [ ] Erster vollständiger Lauf: `./gradlew ktlintCheck detekt test koverVerify :app:lintDebug :app:assembleDebug`
      (im Setup-Container waren die Maven-Repositories nicht erreichbar;
      der erste Lauf passiert lokal oder im CI. Kleinere Konfigurations-
      korrekturen dabei sind normal und erwartbar.)
- [ ] Gradle Dependency Verification erzeugen:
      `./gradlew --write-verification-metadata sha256` (Regel S6)
- [ ] Wrapper-Checksumme pinnen: `distributionSha256Sum` in
      gradle-wrapper.properties ergänzen
- [ ] GitHub-Actions auf Commit-SHAs pinnen (Release-Engineer-Regel 4)
- [ ] Branch-Schutz für main einrichten (PR-Pflicht + CI-Pflicht)

## Blockiert
- (nichts)

## Nächste Schritte
1. Phase-0-Abschlusskriterien abarbeiten (siehe oben)
2. Gate: Mensch nimmt Fundament ab
3. Phase 1 starten: game-designer füllt docs/game-design.md
