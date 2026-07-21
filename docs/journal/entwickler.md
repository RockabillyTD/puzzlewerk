# Journal — entwickler

> Aufgabenhistorie dieser Rolle. Pflege: Orchestrator, nach jedem
> gemergten/eskalierten Ticket. Letzte 8 Tickets voll, ältere je 1 Zeile.
> Kappe: 400 Zeilen. Schrittangaben vor Phase 4: n. a. (vor Budget-Regime).

## PW-4.3 — Juice-Ereignisdaten in :game (PR #33, gemergt 2026-07-20)
- Gebaut nach ADR-012: TraceResult.endpoints (BeamEndpoint,
  Ableitung nur im DefaultTracer-absorb()-Pfad; Brett-Aus ohne
  Eintrag §13.8a; Reihenfolge = Verarbeitungsreihenfolge §5.2) +
  pure Funktion juiceDelta(before, after, board) mit newlyFulfilled
  (sortiert r, dann q = Kaskadenordnung §13.9), newlyLit, fulfilled
  (L), crystalTotal (K), comboSize; before=null = Partie-Start.
- Test-First: DefaultTracerEndpointTest (8), JuiceDeltaTest (12),
  Property „newlyFulfilled ⊆ fulfilled" (300 Iter., fest geseedet).
  Fixture-Migration bewusst source-breaking (endpoints ohne Default),
  Golden-segments byte-identisch (Review-Auflage erfüllt).
- Review: MERGEABLE (beide Suiten real grün, Absorptions-
  Vollständigkeit inkl. visited-Set-Zyklen adversarial geprüft);
  1 NIT nachgezogen (deterministischer Sortier-Diskriminanztest —
  ADR-Beispiel 7.3 unterschied compareBy(r,q) nicht von (q,r)).
  Security-APPROVE (kein Android-Import, endpoints hart gedeckelt).
- Learning: „erfüllt" strikt = FULFILLED (Übersättigung zählt nicht);
  ADR-Beispiele können mutations-blind sein — Sortier-Invarianten
  brauchen einen Fall, der BEIDE Schlüsselreihenfolgen unterscheidet.
- Schritte: PW-4.3 in 3 Gate-Läufen + NIT-Nachzug (voll grün).

## PW-3.2 — Persistenzkern :data (PR #19/#20, gemergt 2026-07-13)
- Gebaut: Envelope-Serializer v1, DataStore-Repositories (Progress/
  Daily/Settings), In-Memory-Fakes, isLevelUnlocked §11.2 in :game,
  Golden-Suiten; Kover-:data-Gate ≥ 70 % scharf.
- Befund (HIGH, vor Merge gefangen): Byte-Kappungs-Härtung nutzte
  `InputStream.readNBytes` (erst API 33) — minSdk 26 ohne Desugaring
  ⇒ NoSuchMethodError bei JEDEM Store-Read auf Android 8–12L. Ersetzt
  durch akkumulierende `read(buf,off,len)`-Schleife + Regressionstests.
- Learning: JVM-Unit-Tests fangen keine NewApi-Fehler — deshalb kam
  :data:lintDebug in die Gate-Kette (PW-3.10). API-Level bei jeder
  java.*-Methode prüfen.
- Schritte: n. a.

## PW-2.7 — CLI-Demo-Runner (PR #13, 2026-07-11)
- Gebaut: `./gradlew :game:runDemo` — generiert, validiert, rendert,
  löst Level in exakt Par Zügen; Gate-Nachweis D2/D3/D5.
- Verifikation: Demo von Orchestrator UND Reviewer unabhängig
  ausgeführt. Schritte: n. a.

## PW-2.5 — DefaultLevelGenerator + ParSolver (PR #12, 2026-07-11)
- Gebaut: Generator + Solver; damit Randfälle R01–R43 komplett.
- QS: unabhängiges BFS-Orakel PASS; Minimalitätsbeweis reviewt;
  Daily-Kette Ende-zu-Ende. Auflage: §9/§16.2-Klärungen kodifizieren
  (→ PW-2.9). Schritte: n. a.

## PW-2.4 — DefaultLevelValidator (PR #10, 2026-07-11)
- Gebaut: Validator, 100 % Branch auf der Klasse; QS-Fuzzing ohne
  Befund; Tracer-Restrisiken geschlossen. Schritte: n. a.

## PW-2.3 — DefaultGameEngine + DefaultScoreCalculator (PR #8, 2026-07-11)
- Befund (QS): B1 Int-Überlauf in der Score-Formel — behoben.
  I9-Sperrkanten-Semantik vom Reviewer als design-konform bewiesen.
- Learning: Grenzwerte der Wertebereiche gehören in die eigenen Tests,
  nicht erst in den QS-Pass. Schritte: n. a.

## PW-2.2 — DefaultTracer (PR #6, 2026-07-09)
- Gebaut: Tracer, 96,6 % Branch. Befund (QS): BUG-1 still ignorierte
  runBlocking-Tests — behoben.
- Learning: Testinfrastruktur selbst testen; „grün" kann bedeuten
  „nicht ausgeführt". Schritte: n. a.

## PW-2.1 — Öffentliche :game-APIs + ADR-003 (PR #5, 2026-07-09)
- Gebaut: API-Flächen :game, SplitMix64-PRNG (ADR-003); Golden-Werte
  vom Reviewer unabhängig nachgerechnet. Schritte: n. a.

## Offen für diese Rolle
- Phase 4, Punkt 3 (PW-4.3): MoveResult.Applied um Juice-Ereignisdaten
  erweitern (neu erleuchtete Kristalle, Combo-Größe) — nach ADR PW-4.2.
- Backlog-Kandidaten: Difficulty-Anzeige-Akzessor in :game statt
  ordinal+1; R37-Entscheidung (LevelRequest.Daily vs. negative epochDay).