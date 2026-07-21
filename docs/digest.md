# Digest — Projekt-Gesamtlage Puzzlewerk

> Komprimierte Gesamthistorie für Agenten-Schichten (Segment C).
> Pflege: Orchestrator, jeder Zyklus. Kappe: 150 Zeilen.
> Agenten lesen NUR dieses Dokument + ihr Rollen-Journal —
> status.md/backlog.md liest nur der Orchestrator.

## Phase & nächstes Gate (Stand 2026-07-21, Zyklus 25)
- Phase 3 abgenommen: Gate-Feedback Branko = „zu langweilig, Richtung
  Zuma" → Phase 4 „Juice-Update" läuft (docs/phase4-10-punkte-plan.md,
  PW-4.1–4.10; Feedback-Vorlage: docs/phase4-juice-update.md).
- Gemergt: PR #30 PW-4.0 (Juice-Doc + 18 OGGs in :app res/raw +
  keep.xml + synth.py) und PR #31 PW-4.1 (BREAKING-Addendum
  §13.7–13.13 + R44–R50, **ABGENOMMEN durch Branko 2026-07-16** inkl.
  V3-Abweichung und aller vorgelegten Designentscheidungen).
- Gemergt: PR #32 PW-4.2 — ADR-010 (AudioTrack-Mixer für Stems),
  ADR-011 (Canvas-only-VFX, überholt die Punkt-5-Planprosa „AGSL"),
  ADR-012 (TraceResult.endpoints + juiceDelta) + AudioEngine-/
  JuiceState-Deklarationen in :app.
- Gemergt: PR #33 PW-4.3 — TraceResult.endpoints (im Tracer) +
  juiceDelta() in :game (Ereignisdaten für den Juice-Layer).
- Gemergt: PR #34 PW-4.4 — JuiceState-Partikelkern (DefaultJuiceStepper,
  ParticleBuffer MAX 512, mix64-Seed-Kette), deterministisch, 16 Tests.
- Gemergt: PR #35 PW-4.8 — AudioEngine (Session-Token-Mixer, SoundPool,
  StemMix.forProgress §13.11, Settings v2 musicEnabled/sfxEnabled).
  ADR-010 hat ein Addendum: setHostVisible + Lifecycle-Glue.
- Gemergt: PR #36 PW-4.5 — Laser-Rendering Canvas-only (Kern+Halo+Puls,
  JuiceFrameDriver mit dt-Clamp + driftfreiem Rest-Übertrag).
- Gemergt: PR #37 PW-4.6 — Aktions-Feedback (Events→Juice+Audio,
  §13.9-Glow im JuiceState = ADR-011-Delta, Solved-Kontrakt,
  Queue-Kappe 64). Abweichungen dokumentiert: SFX ohne 40-ms-Versatz,
  Stem-Neustart bei Rotation (Produktfrage im Backlog).
- Gemergt: PR #38 PW-4.7 — Vollbild-Flash (Screen-Root), Sterne-
  Choreografie. Punkte 1–8 komplett.
- Gemergt: PR #39 PW-4.9 — QS-Pass PASS (31 Tests, step() p95 83 µs,
  2 Bugs gefunden) + PR #40 PW-4.9-FIX (Fokus-Session-Guard,
  Stern-SFX-Einmaligkeit; Repro-Tests scharf).
- In Arbeit: PW-4.10 (release-engineer) — Gate-Kette auf main,
  APK-Größenbudget, Shrinker-Prüfung, phase4-gate-checklist.md
  (inkl. aller Abnahme-Deltas), Debug-APK, versionName 0.4.0.
  DANACH: menschliches Gate Branko.
- NEU: Handover-Regime — jeder Agent hängt am Ticket-Ende an
  docs/handover.md an: (a) Kontext, (b) Aufgaben des nächsten Agenten.
  Nachfolger lesen den eigenen Handover-Abschnitt ihres Vorgängers.
- Entscheidung 2026-07-21: §13.9-Glow-Burst lebt datenseitig im
  JuiceState — Snapshot-Erweiterung kommt in PW-4.6 (ADR-011-Delta).
- **Nächstes Gate: menschliche Abnahme nach Punkt 10** (Gate-Artefakt
  + Spieltest); Reihenfolge bis dahin: 2 → 3 → 4 → (5, 8 parallel)
  → 6 → 7 → 9 → 10.
- Es gilt das Schrittbudget- und Kontextregime aus
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
- ADR-010 Audio: AudioTrack-Mixer für 4 Stems (PCM vorab dekodiert,
  sample-exakter Loop), SoundPool für SFX; AudioEngine-Interface.
- ADR-011 VFX: Canvas-only (kein AGSL), JuiceState + pure step();
  Seed-Kette mix64; Rückfalltür p95 < 55 fps ⇒ Folge-ADR.
- ADR-012 Ereignisdaten: TraceResult.endpoints (im Tracer) +
  juiceDelta(before, after, board) — Delta-Spez für PW-4.3.

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
- PR #40 PW-4.9-FIX: Fokus-Session-Guard + Stern-SFX-Einmaligkeit.
- PR #39 PW-4.9: QS-Pass Juice — PASS, 31 Tests, 2 Bugs dokumentiert.
- PR #38 PW-4.7: Vollbild-Flash + Sterne-Choreografie (349 Z., clean).
- PR #37 PW-4.6: Aktions-Feedback + Glow + Audio-Choreo (~700 Z., Ausn.).
- PR #36 PW-4.5: Laser-Rendering Canvas-only + JuiceFrameDriver.
