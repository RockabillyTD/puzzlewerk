# Journal — test-engineer

> Aufgabenhistorie dieser Rolle. Pflege: Orchestrator, nach jedem
> gemergten/eskalierten Ticket. Letzte 8 Tickets voll, ältere je 1 Zeile.
> Kappe: 400 Zeilen. Schrittangaben vor Phase 4: n. a. (vor Budget-Regime).

## PW-3.7-QS — QS-Pass gegen §12/§13 (PR #29, gemergt 2026-07-14)
- Verdikt: PASS mit 3 dokumentierten Befunden (nicht gefixt, Backlog):
  „Weiter" pusht statt ersetzt Backstack-Top; LevelRequest.Daily vs.
  R37 (negative epochDay); rememberAnimationsEnabled-Einmallesung.
- Gebaut: 14 neue deterministische Tests NEBEN den Entwickler-Tests —
  GameViewModelQsTest 6 (R28-Undo-leer, Intents im Ladezustand,
  Reset-Schwelle beidseitig exakt ≥5, ConfirmReset-Race, volle
  Undo-Kette I10, Daily ohne Kampagnen-Persistenz); GameRouteQsTest 4
  (Level-50-Kante ohne „Weiter" §11.1, Daily ohne „Weiter",
  Speicherfehler-Meldung, Sterne-Glyphen §13.5 via TalkBack-Pfad);
  GameRotationAnimationQsTest 4 (Reduce-Motion §13.6 beide Skalen,
  Spin ~150 ms rein auf mainClock).
- Review: MERGEABLE; 1 MINOR (tinySolvableLevel-Fixture dreifach
  kopiert → Backlog Fixture-Konsolidierung) + 1 NIT.
- Learning: Schwellen IMMER beidseitig testen (≥5-Reset: 4 und 5) —
  gleicher Angriff gilt für die Audio-Ebenen in Phase 4 (49 %/50 %).
- Schritte: n. a.

## Phase-2-QS-Pässe (Zyklen 4–9, 2026-07-09 bis 2026-07-11)
- PW-2.2-QS: BUG-1 gefunden — still ignorierte runBlocking-Tests
  (Tests liefen nie); als Regressionstest + Bericht, Fix durch
  Entwickler.
- PW-2.3-QS: B1 gefunden — Int-Überlauf in der Score-Formel bei
  Grenzwerten; Repro dokumentiert, Fix durch Entwickler.
- PW-2.4-QS: Fuzzing des Validators ohne Befund; Tracer-Restrisiken
  benannt und geschlossen.
- PW-2.5-QS: unabhängiges BFS-Orakel gegen Generator/ParSolver — PASS;
  Daily-Kette Ende-zu-Ende geprüft.
- Learning: Die wirksamsten Angriffe waren Orakel-Vergleich und
  Grenzwerte — nicht Coverage-Jagd. Invarianten I1–I10 als
  Property-Tests mit festen Seeds etabliert.
- Schritte: n. a.

## Offen für diese Rolle
- Phase 4, Punkt 9 (PW-4.9): unabhängiger QS-Pass Juice — Determinismus-
  Property (1000 Frames), Kapazitäts-Stress, Reduce-Motion-Matrix,
  Audio-Kanten (49 %/50 %, Duck, Fokus-Verlust), Frame-Budget-Smoke,
  Abbruch-Kanten. Startet nach PW-4.3–4.8.
