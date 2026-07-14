# Digest — Projekt-Gesamtlage Puzzlewerk

> Komprimierte Gesamthistorie für Agenten-Schichten (Segment C).
> Pflege: Orchestrator, jeder Zyklus. Kappe: 150 Zeilen.
> Agenten lesen NUR dieses Dokument + ihr Rollen-Journal —
> status.md/backlog.md liest nur der Orchestrator.

## Phase & nächstes Gate (Stand 2026-07-14, Zyklus 15)
- Phase 3 (Spielbarer Prototyp): alle Tickets inkl. QS-Pass gemergt
  (PR #29 zuletzt). Kampagnenpfad Ende-zu-Ende spielbar.
- **Nächstes Gate: menschliche Abnahme durch Branko** — Debug-APK
  (app/build/outputs/apk/debug/app-debug.apk, tree-identisch zu
  main@f8dd279) spielen, Checkliste docs/phase3-gate-checklist.md.
- Phase 4 „Juice-Update" ist geplant (docs/phase4-10-punkte-plan.md,
  10 Punkte PW-4.1–4.10) und startet NACH dem Gate. Voraussetzungen
  noch offen: docs/phase4-juice-update.md fehlt im Repo, die 18
  OGG-Assets fehlen unter app/src/main/res/raw/.
- Ab Phase 4 gilt das Schrittbudget- und Kontextregime aus
  docs/schichtplan-kontextbudget.md (Budgetzeile in jedem Ticket).

## ADR-Index (docs/decisions/)
- ADR-001 Technologie-Stack: Kotlin, Compose, Gradle Kotlin-DSL.
- ADR-002 Dependency-Quellen: google()+mavenCentral(), Portal nur
  content-gefiltert (Ktlint); PGP-Roadmap mit Triggern.
- ADR-003 SplitMix64-PRNG als deterministische RandomSource.
- ADR-004 Schichtenmodell app → data → game → core; :game/:core ohne
  Android/androidx; checkModuleGraph erzwingt es im Gate.
- ADR-005 Google-Namensraum-Sperre (Android-Koordinaten).
- ADR-006 DI: manueller AppContainer + ViewModel-Factories, keine
  Reflection, kein Framework.
- ADR-007 Persistenz: DataStore + kotlinx.serialization-JSON,
  Envelope v1, definierte Korruptions-/Versionssemantik.
- ADR-008 Navigation: eigener Backstack-Holder + Saver (keine
  Navigation-Compose-Dependency).
- ADR-009 UI-Test-Stack: Robolectric + Compose-Test, android-all
  offline gepinnt (S6); Kover-Gates :app/:data ≥ 70 %.

## Modul-Landkarte
- :core — Basistypen, RandomSource/WallClock-Abstraktionen (pure JVM).
- :game — komplette Spiellogik: Engine, Tracer, Validator, Generator,
  ParSolver, Score, Progression (isLevelUnlocked, campaignTier);
  Coverage-Gate ≥ 90 % Branch; explicit-API-Mode; CLI-Demo :game:runDemo.
- :data — DataStore-Repositories Progress/Daily/Settings (Envelope v1)
  + In-Memory-Fakes; Golden-Tests fürs v1-Schema.
- :app — Compose-UI (MVI): Theme, Navigation, Home, Levelauswahl,
  GameScreen mit BoardCanvas; AppContainer als Composition Root.
- Design-Quelle: docs/game-design.md (normativ, R01–R43, I1–I10);
  UI-Regeln: docs/ui-architektur.md; Gate-Kommandos: docs/architektur.md.
- Gate-Kette: checkModuleGraph ktlintCheck detekt test koverVerify
  :app:lintDebug :data:lintDebug :app:assembleDebug.

## Top-10-Backlog (Auswahl, Details docs/backlog.md — nur Orchestrator)
1. „Weiter" pusht statt ersetzt Backstack-Top (QS-Befund PW-3.7-QS).
2. R37-Entscheidung: LevelRequest.Daily vs. negative epochDay.
3. rememberAnimationsEnabled: Einmallesung statt Observer.
4. Fixture-Konsolidierung (tinySolvableLevel 3× kopiert).
5. GameViewModel-Ladefehler-Pfad (definierte Fehler-UI).
6. Difficulty-Anzeige-Akzessor in :game statt ordinal+1 in der UI.
7. ViewModel-Scoping: bounded Leak + Overlay-bei-Rückkehr.
8. Dreh-Puffer/Undo-Animationsrichtung.
9. Test-Source-Set-Vereinheitlichung (:app).
10. Infra: gitleaks-CI, CVE-Scan, Renovate, PGP-Trigger,
    Custom-Detekt-Regel, ringIndex, KDoc-Referenzen.

## Letzte 5 Merges
- PR #29 PW-3.7-QS: 14 QS-Tests gegen §12/§13, 3 Befunde → Backlog.
- PR #28 PW-3.7: E2E-Smoke + Gate-Checkliste + ViewModel-Key-Fix.
- PR #27 PW-3.10: :data:lintDebug in Gate-Kette + CI (NewApi-Blindfleck).
- PR #26 PW-3.5b: interaktiver Spiel-Screen (Eingabe, Overlay, DI).
- PR #25 PW-3.6: Levelauswahl (ViewModel, Grid, Root-Verdrahtung).
