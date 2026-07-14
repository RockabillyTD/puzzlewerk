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

## Erledigt (Zyklus 12, 2026-07-13) — Wave 1 vollständig gemergt
- [x] PW-3.2 (PR #19 gemergt c2ff198, PR #20 gemergt 55f8a2c):
      Persistenzkern v1 (Envelope-Serializer, DataStore-Repositories für
      Progress/Daily/Settings, In-Memory-Fakes, isLevelUnlocked §11.2) +
      Golden-Suiten; Kover :data-Gate ≥ 70 % scharf.
      **HIGH vor Merge gefangen und behoben (3b024ce):** die
      Byte-Kappungs-Härtung hatte `InputStream.readNBytes` (erst API 33)
      eingeführt — minSdk 26 ohne Desugaring ⇒ NoSuchMethodError bei
      JEDEM Store-Read auf Android 8–12L. Ersetzt durch akkumulierende
      `read(buf,off,len)`-Schleife (Teil-Read-fest) + Regressionstests;
      `:data:lintDebug` 0 NewApi, `git grep readNBytes` leer.
      Orchestrator-verifiziert (Semantik-Äquivalenz + Grenzfälle).
- [x] PW-3.3 (PR #21 gemergt 886a826, PR #22 gemergt 6e9f640):
      App-Shell — Theme (§13.4), Navigation (ADR-008, defensiver Saver),
      Composition Root (ADR-006), HomeScreen/HomeViewModel (§12.2),
      Robolectric-Offline-Pinnung (S6/ADR-009) per Wächtertest belegt.
      code-reviewer: MERGEABLE (keine Funde); security-auditor:
      SECURITY-APPROVE (verification-metadata additiv, Manifest S5).
- Alle vier PRs vor Merge mit `main` aktualisiert (strict-Gate): 3.3-Stack
  konfliktfrei außer docs/backlog.md (Union), 3.2-Stack konfliktfrei;
  CI je Update grün nachgezogen.

- [x] PW-3.9 (PR #23 gemergt 6835e09): `campaignTier(levelNumber)` in
      :game — §11.3-Abbildung (in PW-3.8 nur im Design fixiert, Funktion
      fehlte; Wave-2-Voraussetzung, da die UI den Tier nicht selbst
      berechnen darf, ui-architektur §2). code-reviewer: MERGEABLE mit
      Handnachrechnung der Tabelle. (Nebenbei aufgeräumt: ein versehentlich
      im Primär-Worktree gelandeter Status-Commit — Ursache: Agent ohne
      isolation:worktree — non-destruktiv entflochten.)

## Erledigt (Zyklus 13, 2026-07-13) — Wave 2, Kampagnenpfad spielbar
- [x] PW-3.5a (PR #24 gemergt 62ee9a6): GameViewModel + MVI-Typen
      (GameUiState/Intent/Effect), 10 JVM-Tests, :app-Coverage 94,6 %.
      Level off-main via generate(campaignSeed/Tier); Tap→Rotate, Undo,
      Reset (Bestätigung ≥5), Gelöst→Score, recordSolved nur Kampagne;
      Brett aus MoveResult.trace (Tracer nie direkt). Bewusste
      Phase-3-Eingrenzung: kein DailyStatsRepository (Phase 4).
- [x] PW-3.6 (PR #25 gemergt 4c8a6b9): Levelauswahl — ViewModel +
      50-Kachel-Grid (gesperrt/offen/gelöst mit Sternen+Score, Kopf-Summen),
      Freischaltung via isLevelUnlocked, Tier via campaignTier (beide :game);
      Fehlerzustand+Reset (R43); A11y mehrkanalig. Größen-Ausnahme
      ~541 Zeilen (ein kohärenter Screen) dokumentiert.
- [x] PW-3.5b (PR #26 gemergt 15e1228): interaktiver Spiel-Screen —
      GameScreen, Brett-Tap (inverse Pixel→Axial in BoardCanvas), Kopfzeile,
      Dreh-Animation (~150 ms, reduce-motion-fest), Ergebnis-Overlay
      (Sterne/Punkte/Weiter/Nochmal/Zurück), Root-Game-Route,
      request-parametrierte gameViewModelFactory (ADR-006, keine Reflection).
      Behob Review-MINOR PW-3.5a (Reset-Guard bei Gelöst) + Replay-Intent
      (R32-konform). code-reviewer verifizierte detekt/lint/test selbst,
      MERGEABLE. Größen-Ausnahme ~780 Zeilen (nach Concern gesplittet).
      **Damit Kampagnenpfad Ende-zu-Ende spielbar: Home → Levelauswahl →
      Level → drehen/lösen → Overlay → Weiter.**
- Wave-2-Merges: PW-3.5a & PW-3.6 parallel entwickelt (disjunkt), 3.5a
  zuerst gemergt, 3.6 mit main aktualisiert, dann 3.5b (Root-Game-Zweig,
  additiv zum LevelSelect-Zweig aus 3.6). Alle mit isolation:worktree.

## Erledigt (Zyklus 14, 2026-07-14) — PW-3.7 gemergt, Gate-Artefakt liegt vor
- [x] PW-3.7 (PR #28 gemergt f8dd279): E2E-Smoke-Test (Home→Levelauswahl→
      Level 1 lösen per echtem Pixel-Tap durch den pointerInput-Pfad→
      Overlay→Weiter→Folgelevel + Repository-Check), Fix GameViewModel
      je Partie geschlüsselt (`viewModel(key=encodeScreen(...))` — ohne
      den Fix lädt „Weiter" nie das Folgelevel), docs/
      phase3-gate-checklist.md, Backlog-Notiz ViewModel-Scoping.
      code-reviewer: APPROVE mit eigener Verifikation — Testlauf grün,
      **Mutationsprobe** (Fix revertiert ⇒ Test schlägt fehl, fängt den
      Bug nachweislich), Level-Lösung/Score von Hand nachgerechnet
      (1500 P, 3★), Checklisten-Stichproben bestätigt. 3 NITs notiert
      (Test-Source-Set-Split, KDoc-Ergänzung Main-Dispatcher-Seam,
      VM-Key-Kopplung an Nav-Encoding — dokumentiert, nicht blockierend).
      Merge durch Branko freigegeben (2026-07-14).
- [x] Debug-APK als Phase-3-Gate-Artefakt gebaut (Tree-identisch zu
      main@f8dd279, verifiziert via `git rev-parse <sha>^{tree}`):
      `app/build/outputs/apk/debug/app-debug.apk` (~10,4 MB).
- PW-3.7-Agent-Worktree entfernt (gemergt, APK vorher gesichert).

## Erledigt (Zyklus 15, 2026-07-14) — QS-Pass gemergt, Phase 3 wartet nur noch auf das menschliche Gate
- [x] PW-3.7-QS (PR #29 gemergt 60a8fc6): unabhängiger test-engineer-Pass
      gegen §12/§13 — 14 neue deterministische Tests auf Lücken NEBEN den
      Entwickler-Tests (GameViewModelQsTest 6: R28-Undo-leer, Intents im
      Ladezustand, Reset-Schwelle beidseitig exakt ≥5, ConfirmReset-Race,
      volle Undo-Kette I10, Daily ohne Kampagnen-Persistenz;
      GameRouteQsTest 4: Level-50-Kante ohne „Weiter" §11.1, Daily ohne
      „Weiter", Speicherfehler-Meldung, Sterne-Glyphen-Semantik §13.5 via
      TalkBack-Doppeltipp-Pfad; GameRotationAnimationQsTest 4:
      Reduce-Motion §13.6 beide Skalen, Spin ~150 ms rein auf mainClock).
      Drei QS-Befunde nur dokumentiert (Backlog): „Weiter" pusht statt
      ersetzt Backstack-Top, LevelRequest.Daily vs. R37 (negative
      epochDay), rememberAnimationsEnabled-Einmallesung.
      code-reviewer: MERGEABLE mit eigener Verifikation (Testlauf grün,
      14 Testcases nachgezählt, ktlint/detekt grün, Mutationssensitivität
      stichprobenhaft geprüft, alle drei Backlog-Befunde am
      Produktionscode bestätigt); 1 MINOR (tinySolvableLevel-Fixture
      dreifach kopiert → Backlog) + 1 NIT. CI quality-gates grün.
      Merge durch Branko freigegeben (2026-07-14). QS-Worktree entfernt.

## Erledigt (Zyklus 16, 2026-07-14) — Team-Alignment für Phase 4
- [x] Phase-4-Plandokumente eingecheckt: docs/phase4-10-punkte-plan.md
      (PW-4.1–4.10, Schrittbudgets, Reihenfolge) und
      docs/schichtplan-kontextbudget.md (Kontextbudget-Modell,
      Rollen-Codex, Briefing-Schablone).
- [x] Einführungs-Ticket aus schichtplan §5 ausgeführt: sechs
      Rollen-Journale docs/journal/*.md initial aus der status.md-
      Historie befüllt; docs/digest.md erstmalig geschnitten
      (≤ 150 Zeilen); CLAUDE.md um Abschnitt „Kontext-Reihenfolge"
      ergänzt. Ab sofort gilt die Briefing-Schablone für jede
      Delegation.
- Hinweis: Phase-4-Voraussetzungen noch offen —
      docs/phase4-juice-update.md fehlt, die 18 OGG-Assets fehlen
      unter app/src/main/res/raw/; Phase-3-Gate (Branko) steht aus.

## In Arbeit (Phase 3)
- (nichts delegiert — alle Phase-3-Tickets inkl. QS-Pass sind gemergt)

## Nächste Schritte
1. **Menschliches Gate (Branko):** Debug-APK installieren
   (`app/build/outputs/apk/debug/app-debug.apk`, Tree-identisch zu
   main@f8dd279 — der QS-Merge ändert nur Test-Quellen+docs, kein
   Neubau nötig), ein Level von Anfang bis Ende spielen; Checkliste
   docs/phase3-gate-checklist.md; Spielgefühl-Feedback als Tickets zurück.
2. Nach Gate-Feedback: Phase-4-Planung (Daily-Einstieg, Feinschliff)
   inkl. der R37-Entscheidung (LevelRequest.Daily vs. negative epochDay).
3. Offene Backlog-Punkte: aus PW-3.7-QS: Weiter-als-Replace-Top,
   R37/epochDay-Entscheidung, Animations-Setting-Observer,
   Fixture-Konsolidierung; aus Wave 2: GameViewModel-Ladefehler-Pfad,
   Difficulty-Anzeige-Akzessor in :game statt ordinal+1,
   Dreh-Puffer/Undo-Animationsrichtung; aus PW-3.7: ViewModel-Scoping
   (bounded Leak + Overlay-bei-Rückkehr), Test-Source-Set-Vereinheitlichung.
   Übrige unverändert (ringIndex, KDoc-Referenzen, gitleaks-CI, CVE-Scan,
   Renovate, PGP-Trigger, Custom-Detekt-Regel).
