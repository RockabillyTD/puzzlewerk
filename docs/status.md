# Projekt-Status — Puzzlewerk

> Wird vom Orchestrator nach jedem Arbeitszyklus gepflegt.

## Aktuelle Phase
Phase 1 — Game-Design (in Arbeit); Phase 0 technisch komplett, formale
Meilenstein-Abnahme durch Branko ausstehend

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

## In Arbeit
- [ ] PW-1.1: docs/game-design.md für Konzept C „Prisma" (game-designer);
      Konzeptwahl durch Branko am 2026-07-09 (phase1-spielkonzepte.md)

## Blockiert / wartet auf den Menschen
- [ ] Formale Phase-0-Abnahme (alle technischen Kriterien erfüllt:
      lokale Gates grün, CI grün, Regeln dokumentiert)
- [ ] Phase-1-Gate: Abnahme des Prisma-Design-Dokuments (sobald PW-1.1
      vorliegt; Iterationen erwünscht)

## Nächste Schritte
1. PW-1.1 abschließen → Design-Review → Abnahme durch Branko
2. Nach Design-Abnahme: Phase 2 planen (Architekt definiert :game-APIs)
3. Backlog: gitleaks-CI-Schritt, CVE-Scan, Renovate (inkl. SDK-Bumps),
   jährlicher targetSdk-Bump-Prozess, PGP-Trigger beobachten
