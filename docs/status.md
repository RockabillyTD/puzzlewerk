# Projekt-Status — Puzzlewerk

> Wird vom Orchestrator nach jedem Arbeitszyklus gepflegt.

## Aktuelle Phase
Phase 0 — Fundament (Arbeit abgeschlossen, Meilenstein-Abnahme durch den Menschen ausstehend)

## Erledigt
- [x] Verzeichnis- und Modulstruktur (:app/:game/:data/:core)
- [x] CLAUDE.md, 8 Agent-Definitionen, Orchestrator-Briefing
- [x] Gradle-Konfiguration (Version Catalog, Detekt, Ktlint, Kover)
- [x] Platzhalter-Spiellogik mit Unit- und Property-Tests
- [x] CI-Workflow (.github/workflows/ci.yml)
- [x] ADR-001 (Technologie-Stack)
- [x] Erster vollständiger Gate-Lauf grün: `./gradlew ktlintCheck detekt test koverVerify :app:lintDebug :app:assembleDebug`
      (Ticket PW-0.1: .editorconfig-Fix, Ktlint-Formatierung, AGP 8.13.2,
      compileSdk/targetSdk 36, 7 Android-Lint-Fixes ohne Suppressions)
- [x] Gradle Dependency Verification aktiv (SHA-256, 544 Komponenten,
      inkl. Linux-aapt2 für den CI-Runner) — Regel S6 (Ticket PW-0.2)
- [x] Wrapper-Checksumme gepinnt (distributionSha256Sum, Gradle 8.14.3)
- [x] GitHub-Actions auf dereferenzierte Commit-SHAs gepinnt
- [x] Backup-/Device-Transfer-Härtung: alle fünf Domains ausgeschlossen (Regel S2)

## Delegationsprotokoll (Zyklus 2026-07-09)
| Ticket | Agent | Ergebnis | Gates |
|---|---|---|---|
| PW-0.1 Quality-Gate-Erstlauf | release-engineer | Branch fix/pw-0.1-erstlauf, 4 Commits | Gate-Kette grün (vom Orchestrator verifiziert) |
| PW-0.2 Supply-Chain-Härtung | release-engineer | Branch fix/pw-0.2-supply-chain, 10 Commits (inkl. Zyklus 2) | Gate-Kette grün |
| Review PW-0.1+0.2 | code-reviewer | Zyklus 1: CHANGES_REQUESTED (1 MAJOR); Zyklus 2: **APPROVE** | eigene Verifikation der Gate-Kette + aller Pins |
| Security-Audit PW-0.1+0.2 | security-auditor | **APPROVE** (M1-Auflage erfüllt, L1 behoben, L2 → Backlog) | Checksummen unabhängig gegen Originalquellen verifiziert |

Merge nach main: `b87c747` (CI grün lokal + Reviewer-APPROVE + Security-APPROVE).

## Blockiert / wartet auf den Menschen
- [ ] **Meilenstein-Gate Phase 0:** Abnahme des Fundaments durch Branko
      (Regeln gelesen und abgesegnet, `./gradlew check` grün bestätigt)
- [ ] **CI-Erstlauf auf GitHub:** Remote origin (github.com/RockabillyTD/puzzlewerk)
      wurde während des Zyklus angelegt; main muss gepusht werden, damit die
      CI erstmals läuft
- [ ] **Branch-Schutz für main** (PR-Pflicht + CI-Pflicht) in den
      GitHub-Repo-Einstellungen — nur mit Repo-Admin-Rechten möglich

## Nächste Schritte
1. Mensch: Phase-0-Gate abnehmen, Branch-Schutz einrichten, CI-Erstlauf bestätigen
2. Phase 1 starten: game-designer erstellt docs/game-design.md
   (Kernmechanik, 30–50 Level-Progression, Scoring, UX-Flows, Randfall-Katalog)
3. Backlog-Punkte für Architekt: gradlePluginPortal vs. S6 (ADR),
   PGP-Signatur-Verifikation als S6-Ausbaustufe
