# Journal — ui-entwickler

> Aufgabenhistorie dieser Rolle. Pflege: Orchestrator, nach jedem
> gemergten/eskalierten Ticket. Letzte 8 Tickets voll, ältere je 1 Zeile.
> Kappe: 400 Zeilen. Schrittangaben vor Phase 4: n. a. (vor Budget-Regime).

## PW-4.6 — Aktions-Feedback V2 + Glow + Audio-Choreo (PR #37, gemergt 2026-07-21)
- Gebaut: Event-Verdrahtung GameViewModel→juiceDelta→JuiceFeedback-
  Channel→offerJuiceEvents→Queue (Kappe 64, Silent-Drop);
  §13.9-Glow als JuiceState-Erweiterung (0→28 dp, 0,8→0, 250 ms,
  ADR-011-Delta im PR); Dreh-Blitz 0,6→0/120 ms am semantischen
  Signal rotatedCell; GameAudioChoreographer (enterGame/exitGame via
  DisposableEffect, SFX-Ketten R45, setStemMix nur bei Änderung);
  Solved-Kontrakt kodifiziert (nach CrystalBursts, moveNumber-Filter).
- Review-Zyklus: CHANGES-REQUIRED → Korrektur → MERGEABLE. MAJOR-1
  Session-Verlust bei Recreation (BoardEntered-Nachsendung mit Seed/
  Endpoints); MAJOR-2 mutationsblinde Kontrakte → Negativtests, vom
  Reviewer per eigener Mutationsprobe bestätigt. Security-APPROVE
  (LOW: enter/exit-Race erst bei echter DataStore-Verdrahtung →
  Auflage fürs Settings-Ticket). Größen-Ausnahme ~700 Z. genehmigt;
  Prozess-Auflage: Orchestrator-Scope-Nachschübe künftig als
  eigenes Mini-Ticket.
- Bewusste Abweichungen dokumentiert: SFX beim Zug-Commit statt
  40-ms-Kaskade (PW-4.7-Option/Abnahme); Stem-Neustart bei Rotation
  (Produktfrage Portrait-Lock im Backlog, vor Gate an Branko).
- Learning: Robolectric + pausierte Testuhr: runOnIdle{write} +
  advanceTimeByFrame()+waitForIdle() im Wechsel — einzeln bewegt nichts.
- Schritte: ~120/140 + Korrektur ~45/50.

## PW-4.8 — AudioEngine SFX + Stems + Settings (PR #35, gemergt 2026-07-21)
- Gebaut: StemMixerCore (purer Mixer, EIN Loop-Cursor über 756 000
  Frames, Fades/Duck klickfrei), DefaultAudioEngine (Session-Token-
  Architektur, 4 Adapter-Seams, Fehler als Werte auf issues),
  AndroidAudioAdapters (MediaCodec→Mono-PCM, AudioTrack, SoundPool 13
  SFX, Fokus + BECOMING_NOISY), StemMix.forProgress (§13.11 exakt),
  Settings-Schema v2 (musicEnabled/sfxEnabled + Migration + Golden).
  PCM-Entscheidung: mono ≈ 5,77 MiB (Quellen einkanalig, verlustfrei).
- Review-Zyklus: CHANGES-REQUIRED → Korrekturrunde → MERGEABLE.
  MAJOR-1 Mixer-Thread-Resurrection (geteiltes running-Flag) → Session-
  Token je enterGame, deterministischer Race-Test über die Thread-
  Factory-Seam. MINOR: focusLost-Reset in exitGame (R47), soundpool-
  Issue-Event. Security-APPROVE (M1 = derselbe Race). Größen-Ausnahme
  868 Zeilen genehmigt (ADR-010-Plattform-Glue, kein Split).
- ADR-010 trägt jetzt ein Addendum: setHostVisible + Lifecycle-Glue.
- Learning: geteilte @Volatile-Flags sind KEIN Sessionsbegriff — bei
  Thread-Lebenszyklen immer Session-Token je Start vergeben.
- Schritte: ~150/150 + Korrektur ~40/60.

## PW-4.5 — Laser-Rendering Canvas-only (PR #36, gemergt 2026-07-21)
- Gebaut: BoardLaserRender (Kern 3 dp weiß + Halo-Ringe 12/8/5 dp,
  Alpha-Treppe 0,55→0, BlendMode.Plus, Puls nur Halo), JuiceFrameDriver
  (rememberJuiceFrameState + JuiceEventQueue, dt-Clamp 0..100 ms,
  Publikations-Filter rendersDifferently), Verdrahtung GameBoard/R44.
  270 produktive Zeilen, Gate Lauf 1 grün.
- Review-Zyklus: CHANGES-REQUIRED → Korrekturrunde → MERGEABLE.
  MAJOR-1 dt-Trunkierung (Sub-ms-Rest verworfen ⇒ Uhr ~4 % zu langsam,
  Puls 1,92 statt 2,0 Hz) → consumeFrameDelta mit Rest-Übertrag,
  mutationssensitive Drift-Tests (60/120 Hz, Clamp, Anomalie).
- Auflagen dokumentiert: Halo-Treppe = abnahmepflichtiges Delta
  (PW-4.10-Checkliste); Flash nur Brettfläche ⇒ PW-4.7 zieht auf
  Vollbild hoch. Detekt: LongParameterList.ignoreDefaultParameters
  statt Suppress (ParticleBuffer-Suppress bleibt begründet).
- Learning: withInfiniteAnimationFrameNanos statt withFrameNanos —
  rohes withFrameNanos hängt JEDEN Robolectric-Test (AppNotIdle);
  Testuhren mit glatten 16 ms verstecken Trunkierungsdrift — immer
  echte Frame-Dauern (16.666.667 ns) testen.
- Schritte: ~35/140 + Korrektur ~18/40.

## PW-4.4 — JuiceState-Partikelkern (PR #34, gemergt 2026-07-21)
- Gebaut: DefaultJuiceStepper (pure step() nach ADR-011), ParticleBuffer
  (SoA-Pool, MAX 512, Silent-Drop), JuiceRandom (mix64-Seed-Kette,
  spawn-only), JuiceState-Snapshot-Erweiterung; 16 JVM-Tests
  (Determinismus über alle Spawn-Pfade, Kapazitäts-Property 400 Frames,
  R44-Matrix, Kaskaden-Timing 40 ms, F-Formel 72/96/120 gepinnt).
- Review MERGEABLE: §13-Zahlen komplett nachgerechnet; Größen-Ausnahme
  513 Zeilen genehmigt (kohärente Testeinheit). 1 MAJOR = Kontrakt-
  Lücke aus ADR-011 (Glow-Burst fehlt im Snapshot) → Entscheidung:
  JuiceState-Erweiterung in PW-4.6. Security-APPROVE; LOW: emitSparks
  ohne dt-Kappe → dt-Clamp im Aufrufer (PW-4.5). MINORs im Backlog.
- Learning: Stream-Alignment Planung↔Spawn (P-Zug beim Spawn
  überspringen) ist DIE Determinismus-Falle des Seed-Ketten-Musters —
  im Handover dokumentiert. Achtung: Quellpfad ist app/src/main/kotlin/
  (nicht java/).
- NEU ab diesem Ticket: Handover-Regime — jeder Agent hängt am
  Ticket-Ende Kontext + Aufgaben des Nächsten an docs/handover.md an.
- Schritte: Implementierung Zyklus 20 (n. dok.) + Abschluss 20/50.

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
