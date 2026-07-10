# Projekt-Status — Puzzlewerk

> Wird vom Orchestrator nach jedem Arbeitszyklus gepflegt.

## Aktuelle Phase
Phase 2 — Spiellogik-Kern (gestartet 2026-07-09).
Phase 0 und Phase 1 sind durch Branko abgenommen (2026-07-09);
das Prisma-Design (docs/game-design.md, PR #4) ist normativ.

## Erledigt (Zyklus 2, 2026-07-09 nachmittags)
- [x] **CI erstmals grün auf ubuntu-latest** (Phase-0-Gate-Kriterium):
      PR #1 gemergt nach zwei diagnostizierten CI-Fehlschlägen
      (PW-0.3: Kalt-Cache-Metadaten; PW-0.5: umgebungsabhängiger
      Lint-Check OldTargetApi, begründet deaktiviert + Backlog-Prozess)
- [x] Repo öffentlich geschaltet (Entscheidung Branko, Secret-Check vorab)
- [x] Branch-Schutz für main aktiv: PR-Pflicht + Pflicht-Check
      quality-gates (strict), keine Force-Pushes/Deletes
- [x] ADR-002 Dependency-Quellen (PR #2): gradlePluginPortal bleibt,
      per Content-Filter auf Ktlint-Gradle beschränkt; S6 präzisiert;
      PGP-Roadmap mit Trigger-Kriterien
- [x] PW-0.4-impl (PR #3): Content-Filter in settings.gradle.kts —
      Security-Finding L2 endgültig geschlossen
- [x] Lint-Reports als CI-Failure-Artefakt (Diagnose-Lücke geschlossen)

## Delegationsprotokoll (Zyklus 2)
| Ticket | Agent | Ergebnis | Gates |
|---|---|---|---|
| PW-0.3 Kalt-Cache-Metadata | release-engineer | PR #1 (Teil 1) | Reviewer-APPROVE, Checksummen 3-fach verifiziert |
| PW-0.5 Lint-Fix | release-engineer | PR #1 (Teil 2) | Reviewer- + Security-APPROVE |
| PW-0.4 ADR-002 | architekt | PR #2 | Reviewer- + Security-APPROVE (Faktenbasis unabhängig geprüft) |
| PW-0.4-impl Portal-Filter | release-engineer | PR #3 | Reviewer- + Security-APPROVE, CI grün |
| PW-1.1 Prisma-Design | game-designer | in Arbeit (1 Neustart nach API-Timeout) | — |

Alle drei PRs: Merge nur bei CI grün + Reviewer-APPROVE + Security-APPROVE.

## Erledigt (Zyklus 3, 2026-07-09 abends)
- [x] PW-1.1: Prisma-Design-Dokument (PR #4, 1122+ Zeilen, 43 Randfälle,
      10 Invarianten) — Reviewer-APPROVE (Beispiel-Level von Hand
      nachgerechnet), 4 Review-Befunde eingearbeitet, drei
      Design-Entscheidungen durch Branko (lokale Zeitzone, Prisma fix,
      Sterne-Zahlen), Abnahme durch Branko, gemergt (b926364)
- [x] Meilenstein-Gates Phase 0 und Phase 1 abgenommen

## Erledigt (Zyklen 4–6, 2026-07-09 bis 2026-07-11)
- [x] PW-2.1 (PR #5): öffentliche :game-APIs + ADR-003 SplitMix64;
      Golden-Werte vom Reviewer unabhängig nachgerechnet
- [x] PW-2.2 (PR #6): DefaultTracer (96,6 % Branch) — QS-Pass fand
      BUG-1 (still ignorierte runBlocking-Tests), behoben
- [x] PW-2.6 (PR #7): ADR-004 Schichtenmodell app→data→game→core,
      CLAUDE.md-Korrektur, checkModuleGraph-Spezifikation (inkl.
      Review-Auflage Regel 5: androidx-Sperre für :game/:core)
- [x] PW-2.3 (PR #8): DefaultGameEngine + DefaultScoreCalculator —
      QS-Pass fand B1 (Int-Überlauf in Score-Formel), behoben;
      I9-Sperrkanten-Semantik vom Reviewer als design-konform bewiesen

Jeder Code-PR durchlief: Entwickler (Test-First) → unabhängiger
Test-Engineer-Angriff → Code-Review mit eigener Verifikation → CI grün.

## In Arbeit (Auftrag Branko 2026-07-11: „weiter bis Phase 2 komplett")
- [ ] PW-2.4: LevelValidator (entwickler) — schließt Tracer-Restrisiken
      (Portal ohne Zwilling, kristallloses Brett)
- [ ] PW-2.6-impl: checkModuleGraph-Gradle-Task (release-engineer,
      isolierter Worktree) — braucht Security-Review (Build-Code)

## Nächste Schritte
1. PW-2.5: Generator + Par-Solver (nach PW-2.4)
2. PW-2.7: CLI-Demo-Runner (Gate-Kriterium)
3. Phase-2-Gate an Branko: Logik komplett bewiesen, ≥ 90 % Branch
   Coverage, CLI-Demo löst ein Level
4. Backlog: gitleaks-CI-Schritt, CVE-Scan, Renovate (inkl. SDK-Bumps),
   PGP-Trigger, Custom-Detekt-Regel gegen stille @Test-Nichtausführung
