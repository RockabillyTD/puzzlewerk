# Journal — architekt

> Aufgabenhistorie dieser Rolle. Pflege: Orchestrator, nach jedem
> gemergten/eskalierten Ticket. Letzte 8 Tickets voll, ältere je 1 Zeile.
> Kappe: 400 Zeilen. Schrittangaben vor Phase 4: n. a. (vor Budget-Regime).

## PW-3.1 — Phase-3-Fundament (PR #15, gemergt 2026-07-12)
- Entschieden: ADR-006 (manueller AppContainer statt DI-Framework),
  ADR-007 (DataStore + kotlinx.serialization, Envelope v1), ADR-008
  (eigener Backstack-Holder), ADR-009 (Robolectric-Stack inkl.
  S6-Auflage android-all-Offline-Pinnung).
- Geliefert: :data-Repository-API v1 (PersistenceResult, progress/
  daily/settings), docs/ui-architektur.md, docs/phase3-tickets.md
  (Schnitte PW-3.2–3.7 mit Agent-Zuordnung).
- Review: APPROVE (kompakt, stichprobenbasiert nach Abbruch eines
  überlangen Erst-Reviews); Security-APPROVE (Dependencies additiv).
- Learning: Interfaces VOR Implementierung hat sich ausgezahlt — die
  Wave-1-Tickets liefen parallel ohne API-Konflikte. Eskalation zu
  §11.3-Tier-Spannen im Abschlussbericht führte zu PW-3.8.
- Schritte: n. a.

## PW-2.8 — ADR-005 Google-Namensraum-Sperre (PR #11, 2026-07-11)
- Entschieden: Android-Koordinaten-Sperre; Security-Finding L3
  (Präfix-Lücke) damit geschlossen.
- Schritte: n. a.

## PW-2.6 — ADR-004 Schichtenmodell (PR #7, 2026-07-11)
- Entschieden: Abhängigkeitsrichtung app → data → game → core;
  CLAUDE.md korrigiert; checkModuleGraph spezifiziert.
- Review-Auflage: Regel 5 (androidx-Sperre für :game/:core) —
  eingearbeitet; Umsetzung als eigenes Ticket PW-2.6-impl (PR #9).
- Learning: Spezifikation und Implementierung eines Gates trennen —
  beide PRs blieben klein und unabhängig verifizierbar.
- Schritte: n. a.

## PW-0.4 — ADR-002 Dependency-Quellen (PR #2, 2026-07-09)
- Entschieden: gradlePluginPortal bleibt, per Content-Filter auf
  Ktlint-Gradle beschränkt; S6 präzisiert; PGP-Roadmap mit
  Trigger-Kriterien.
- Review: Reviewer- + Security-APPROVE (Faktenbasis unabhängig geprüft).
- Schritte: n. a.

## Offen für diese Rolle
- Phase 4, Punkt 2 (PW-4.2): ADRs Audio-Architektur (MediaPlayer-Stems
  vs. AudioTrack-Mixer) + VFX-Layer (AGSL/RuntimeShader ab API 33 vs.
  Canvas) + AudioEngine-/JuiceState-Interfaces — startet nach Abnahme
  von PW-4.1.