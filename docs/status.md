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

## Erledigt (Zyklen 7–9, 2026-07-11)
- [x] PW-2.4 (PR #10): DefaultLevelValidator — 100 % Branch auf der
      Klasse, QS-Fuzzing ohne Befund, Tracer-Restrisiken geschlossen
- [x] PW-2.6-impl (PR #9): checkModuleGraph in check + CI, beide
      Negativproben doppelt reproduziert; ADR-004-Auflagenkette zu
- [x] PW-2.8 (PR #11): ADR-005 Google-Namensraum-Sperre — Security-L3
      geschlossen
- [x] PW-2.5 (PR #12): DefaultLevelGenerator + ParSolver — QS mit
      unabhängigem BFS-Orakel PASS, Minimalitätsbeweis reviewt,
      Daily-Kette Ende-zu-Ende; damit R01–R43 komplett
- [x] PW-2.7 (PR #13): CLI-Demo-Runner — Gate-Nachweis, Demo von
      Orchestrator UND Reviewer unabhängig ausgeführt (D2/D3/D5)
- [x] PW-2.9 (PR #14): §9/§16.2-Präzisierungen kodifiziert (kein
      BREAKING; Kodifizierungs-Auflage aus PR #12 vor Frist erledigt)

## Phase-2-Gate-Nachweise (Stand main nach PR #14)
- Volle Gate-Kette grün: checkModuleGraph ktlintCheck detekt test
  koverVerify :app:lintDebug :app:assembleDebug (Exit 0)
- :game Coverage: **95,9 % Branch (374/390), 99,3 % Line (739/744)**
  — Gate ≥ 90 % Branch erfüllt
- ~300 Tests in :game (Unit, Property mit festen Seeds, Golden,
  adversariale QS-Suiten); Invarianten I1–I10 als Property-Tests
- CLI-Demo (`./gradlew :game:runDemo`) generiert, validiert, rendert
  und löst Level in exakt Par Zügen (mehrfach unabhängig verifiziert)
- 14 PRs, jeder mit: CI grün + Reviewer-APPROVE (+ Security-APPROVE wo
  einschlägig) + bei Logik-PRs unabhängiger Test-Engineer-Pass
- QS-Funde des Prozesses: BUG-1 (still ignorierte Tests), B1
  (Int-Überlauf Score), L3 (Präfix-Lücke) — alle behoben

## Aktuelle Phase (aktualisiert)
Phase 3 — Spielbarer Prototyp (gestartet 2026-07-11).
**Phase 2 durch Branko abgenommen am 2026-07-11.**
Gate Phase 3: APK auf Brankos Gerät installierbar, ein Level von
Anfang bis Ende spielbar; Spielgefühl-Feedback fließt als Tickets zurück.

## Erledigt (Zyklus 10, 2026-07-12)
- [x] PW-3.1 (PR #15, gemergt 51fd169): ADR-006 (manueller AppContainer),
      ADR-007 (DataStore + kotlinx.serialization, Envelope v1), ADR-008
      (eigener Backstack-Holder), ADR-009 (Robolectric-Stack inkl.
      S6-Auflage android-all-Offline-Pinnung); :data-Repository-API v1
      (PersistenceResult, progress/daily/settings); docs/ui-architektur.md;
      docs/phase3-tickets.md (Schnitte PW-3.2–3.7)
      — Gate-Kette lokal grün, CI grün, Reviewer-APPROVE (kompakt,
      stichprobenbasiert nach Abbruch eines überlangen Erst-Reviews),
      Security-APPROVE (Dependencies/Metadata additiv, Scopes korrekt)

## Delegationsprotokoll (Zyklus 10)
| Ticket | Agent | Ergebnis | Gates |
|---|---|---|---|
| PW-3.1 Fundament | architekt | PR #15 gemergt | CI grün + Reviewer- + Security-APPROVE |
| PW-3.2 Persistenz :data | entwickler | in Arbeit (Worktree) | — |
| PW-3.3 App-Shell :app | ui-entwickler | in Arbeit (Worktree) | — |
| PW-3.4 BoardCanvas :app | ui-entwickler | in Arbeit (Worktree) | — |
| PW-3.8 Design-Präzisierung §11.3/§12.5 | game-designer | in Arbeit (Worktree) | — |

## Erledigt (Zyklus 11, 2026-07-12)
- [x] PW-3.8 (PR #16, gemergt 719e8c0): §11.3 campaignTier(n) exakt
      fixiert (D1:1–6, D2:7–12, D3:13–21, D4:22–29, D5:30–39, D6:40–46,
      D7:47–50) + §12.5 Reset als zwei getrennte Aktionen inkl.
      kodifiziertem Daily-Reset-Randfall — Reviewer-APPROVE mit
      Handnachrechnung, MINOR per game-designer-Entscheidung gepatcht
- [x] PW-3.4 (PR #17 + #18, gemergt 3314041/8d712b9): HexGeometrie +
      BoardCanvas komplett — Korrekturzyklus 1 behob 1 BLOCKER
      (Split-Vorwärtsreferenz), 2 MAJOR (ordnungsabhängige Chip-Vergabe →
      Union-Find mit Chip-Garantie je Strahl; C4-Dateisplit); 23 Tests,
      Re-Reviews APPROVE. Größen-Ausnahme (~1050 Zeilen, PR #18) als
      Orchestrator-Entscheidung dokumentiert → Offenlegung im Phasenbericht

## In Arbeit (Phase 3)
- [ ] PW-3.2 (entwickler): GELIEFERT (2 Branches, Kover :data 97,8 %,
      90 Tests; PR #19 Code+Security-APPROVE, PR #20 APPROVE) —
      Härtungspaket vor Merge läuft (Security-MEDIUM Byte-Kappung,
      Golden-Fixture-Konsistenz, Streak-Invariante)
- [ ] PW-3.3 (ui-entwickler): GELIEFERT (2 Branches, Kover :app 95,8 %,
      43 Tests, Offline-Pinnung per Negativtest belegt) — PR #21 + #22
      offen, 2 Code-Reviews + Security-Audit laufen

## Nächste Schritte
1. Wave-1-PRs: je Review (code-reviewer; PW-3.3 zusätzlich
   security-auditor wegen android-all + verification-metadata) → Merge
2. Wave 2: PW-3.5 (zwei PRs: ViewModel, dann Screen/Overlay) und
   PW-3.6 (Levelauswahl) — Merge-Punkt Navigation-Root sequenziell
3. PW-3.7: E2E-Smoke, assembleDebug-APK als Gate-Artefakt, Checkliste,
   unabhängiger test-engineer-Pass → menschliches Gate (Branko)
4. Offene Backlog-Punkte unverändert (ringIndex, KDoc-Referenzen,
   gitleaks-CI, CVE-Scan, Renovate, PGP-Trigger, Custom-Detekt-Regel)
