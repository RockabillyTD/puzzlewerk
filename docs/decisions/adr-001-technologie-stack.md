# ADR-001: Technologie-Stack und Modulschnitt

- Status: AKZEPTIERT
- Datum: 2026-07-09
- Autor: Initial-Setup (Plan docs/plan.md, vom Menschen abgenommen)

## Kontext
Das Spiel wird weitgehend autonom durch KI-Agents entwickelt. Der Stack
muss daher vollständig textbasiert, headless baubar und deterministisch
testbar sein. Zielplattform ist Android, Genre 2D-Casual-Puzzle.

## Optionen
1. Kotlin nativ + Jetpack Compose (Canvas-Rendering, eigener Loop)
2. Godot (GDScript, textbasierte Szenen)
3. Unity (C#, Editor-lastig, binäre Assets)
4. libGDX (Java/Kotlin, code-only Framework)

## Entscheidung
Option 1: Kotlin nativ mit Jetpack Compose.

Module: `:app` (UI) → `:game` (reine Kotlin-Spiellogik) / `:data`
(Persistenz) → `:core` (Basis). `:game` bleibt frei von
Android-Imports; Zufall/Zeit nur über injizierte Abstraktionen.

## Konsequenzen
- (+) Gesamtes Projekt ist Text; Agents können jede Datei lesen/schreiben.
- (+) `./gradlew check` als einziges, maschinenlesbares Qualitäts-Gate.
- (+) Spiellogik zu ~100 % JVM-testbar ohne Emulator.
- (−) Kein Engine-Komfort (Partikel, Physik) — für ein Puzzle akzeptabel.
- (−) Rendering-Performance liegt in eigener Verantwortung
  (Canvas-Disziplin, Recomposition-Hygiene).

Erst-Dependencies (mit dieser ADR freigegeben): AndroidX Core/Activity/
Lifecycle, Compose BOM + Material3, JUnit 5, Kotest, kotlinx-coroutines
(Test), Detekt, Ktlint, Kover. Jede weitere Dependency braucht ein
eigenes ADR.
