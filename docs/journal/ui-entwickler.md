# Journal — ui-entwickler

> Aufgabenhistorie dieser Rolle. Pflege: Orchestrator, nach jedem
> gemergten/eskalierten Ticket. Letzte 8 Tickets voll, ältere je 1 Zeile.
> Kappe: 400 Zeilen. Schrittangaben vor Phase 4: n. a. (vor Budget-Regime).

## PW-3.7 — Integration + E2E-Smoke (PR #28, gemergt 2026-07-14)
- Gebaut: E2E-Smoke (Home→Levelauswahl→Level 1 lösen per echtem
  Pixel-Tap durch den pointerInput-Pfad→Overlay→Weiter→Folgelevel +
  Repository-Check), docs/phase3-gate-checklist.md.
- Fix: GameViewModel je Partie geschlüsselt
  (`viewModel(key=encodeScreen(...))`) — ohne den Fix lädt „Weiter"
  nie das Folgelevel.
- Review: APPROVE mit Mutationsprobe (Fix revertiert ⇒ Test rot),
  Score von Hand nachgerechnet (1500 P, 3★). 3 NITs dokumentiert
  (Test-Source-Set-Split, KDoc Main-Dispatcher-Seam, VM-Key-Kopplung
  an Nav-Encoding).
- Learning: ViewModel-Scoping an Navigation ist eine Bug-Quelle —
  Backlog-Notiz (bounded Leak + Overlay-bei-Rückkehr) offen.
- Schritte: n. a.

## PW-3.5b — Spiel-Screen interaktiv (PR #26, gemergt 2026-07-13)
- Gebaut: GameScreen, Brett-Tap (inverse Pixel→Axial), Kopfzeile,
  Dreh-Animation ~150 ms (reduce-motion-fest), Ergebnis-Overlay,
  Root-Game-Route, request-parametrierte gameViewModelFactory
  (ADR-006, keine Reflection).
- Nebenbei: Review-MINOR aus PW-3.5a behoben (Reset-Guard bei Gelöst)
  + Replay-Intent R32-konform.
- Review: MERGEABLE, Reviewer verifizierte detekt/lint/test selbst.
  Größen-Ausnahme ~780 Zeilen (nach Concern gesplittet, dokumentiert).
- Schritte: n. a.

## PW-3.6 — Levelauswahl (PR #25, gemergt 2026-07-13)
- Gebaut: LevelSelectViewModel + 50-Kachel-Grid (gesperrt/offen/gelöst
  mit Sternen+Score, Kopf-Summen); Freischaltung via isLevelUnlocked,
  Tier via campaignTier (beide :game — UI rechnet nie selbst);
  Fehlerzustand+Reset (R43); A11y mehrkanalig.
- Größen-Ausnahme ~541 Zeilen (ein kohärenter Screen) dokumentiert.
- Schritte: n. a.

## PW-3.5a — GameViewModel + MVI-Typen (PR #24, gemergt 2026-07-13)
- Gebaut: GameUiState/Intent/Effect, 10 JVM-Tests, :app-Coverage
  94,6 %; Levelladen off-main; Tap→Rotate, Undo, Reset (Bestätigung
  ≥5), Gelöst→Score, recordSolved nur Kampagne; Brett aus
  MoveResult.trace (Tracer nie direkt).
- Bewusste Eingrenzung: kein DailyStatsRepository (Phase 4).
- Review-MINOR (Reset-Guard bei Gelöst) → in PW-3.5b behoben.
- Schritte: n. a.

## PW-3.3 — App-Shell (PR #21/#22, gemergt 2026-07-13)
- Gebaut: Theme (§13.4), Navigation (ADR-008, defensiver Saver),
  Composition Root (ADR-006), Home (§12.2), Robolectric-Offline-
  Pinnung (S6/ADR-009) per Wächtertest belegt.
- Review: MERGEABLE ohne Funde; Security-APPROVE. Schritte: n. a.

## PW-3.4 — HexGeometrie + BoardCanvas (PR #17/#18, gemergt 2026-07-12)
- Gebaut: BoardCanvas als reine State→Pixel-Funktion, 23 Tests.
- Befunde (Korrekturzyklus 1): 1 BLOCKER (Split-Vorwärtsreferenz),
  2 MAJOR (ordnungsabhängige Chip-Vergabe → Union-Find mit
  Chip-Garantie je Strahl; C4-Dateisplit). Re-Reviews APPROVE.
- Learning: Ordnungsabhängigkeit in Zeichenlogik ist ein Determinismus-
  Bruch — Union-Find-Muster hat sich bewährt.
- Größen-Ausnahme ~1050 Zeilen als Orchestrator-Entscheidung
  dokumentiert. Schritte: n. a.

## Offen für diese Rolle
- Phase 4, Punkte 4–8 (PW-4.4 bis PW-4.8): JuiceState-Kern, Laser-
  Rendering, Aktions-Feedback, Feuerwerk, AudioEngine — nach PW-4.1/4.2.
- Backlog: „Weiter" pusht statt ersetzt Backstack-Top; Animations-
  Setting-Einmallesung (rememberAnimationsEnabled); ViewModel-Scoping;
  Dreh-Puffer/Undo-Animationsrichtung; GameViewModel-Ladefehler-Pfad.
