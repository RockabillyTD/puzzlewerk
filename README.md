# Puzzlewerk

Ein 2D-Casual-Puzzle-Spiel für Android — entwickelt durch ein
Multi-Agent-Team auf Basis von Claude (Fable 5) mit menschlichen
Abnahme-Gates.

## Schnellstart

```bash
# Vollständige Qualitätsprüfung (Pflicht vor jedem PR)
./gradlew ktlintCheck detekt test koverVerify

# Debug-Build
./gradlew :app:assembleDebug
```

Voraussetzungen: JDK 17+, Android SDK (Android Studio) mit
`local.properties` bzw. `ANDROID_HOME`.

## Multi-Agent-Entwicklung

Die Entwicklung läuft orchestriert: Eine Fable-5-Hauptsession
(Briefing: `docs/orchestrator-briefing.md`) delegiert Tickets an
spezialisierte Subagents in `.claude/agents/`. Grundregeln für alle
Agents stehen in `CLAUDE.md`.

| Agent | Rolle | Schreibrechte |
|---|---|---|
| game-designer | Spielregeln, Level-Design (docs/game-design.md) | Docs |
| architekt | ADRs, Modul-APIs, Dependency-Entscheidungen | Docs + Gerüste |
| entwickler | Spiellogik/Persistenz (:game, :data, :core) | Code |
| ui-entwickler | Compose-UI, ViewModels (:app) | Code |
| test-engineer | Unabhängige Tests gegen Anforderungen | Testcode |
| code-reviewer | PR-Review (Architektur, Clean Code) | keine (read-only) |
| security-auditor | Security-Review, Gesamt-Audits | keine (read-only) |
| release-engineer | CI/CD, Versionierung, Release-Kandidaten | Build/CI |

## Projektstruktur

```
app/    Android-App: Compose-UI, ViewModels, Navigation
game/   Spiellogik — reines Kotlin/JVM-Modul, kein Android-Import
data/   Persistenz (Repositories; Room/DataStore ab Phase 3)
core/   Basis-Abstraktionen (RandomSource, WallClock)
docs/   Plan, Architektur, Game-Design, ADRs, Status, Backlog
```

## Zentrale Dokumente

- `docs/plan.md` — Gesamtplan (Architektur, Regeln, Roadmap)
- `docs/architektur.md` — Schichtenmodell, Regeln C1–C8 / S1–S8, Gates
- `docs/game-design.md` — Spielregeln (Phase 1, einzige Wahrheitsquelle)
- `docs/status.md` — aktueller Stand und nächste Schritte
