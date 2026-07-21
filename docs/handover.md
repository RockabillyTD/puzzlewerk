# Handover-Kette Phase 4

> Jeder Agent hängt am ENDE seines Tickets einen Abschnitt an:
> (a) Kontext — was gebaut/entschieden wurde, Stolpersteine, Learnings;
> (b) Aufgaben für den nächsten Agenten. Neuester Eintrag unten.

## PW-4.4 — ui-entwickler → ui-entwickler (PW-4.5 ∥ PW-4.8) (2026-07-21)

### Kontext

Gebaut: der reine JuiceState-Partikelkern nach ADR-011/§13.13, ausschließlich
unter `app/src/main/kotlin/de/puzzlewerk/app/ui/juice/` (Achtung: `kotlin/`,
nicht `java/`). Neu bzw. wesentlich erweitert:

- **JuiceState.kt** — immutabler Frame-Snapshot `JuiceState(elapsedMillis,
  reduceMotion, levelSeed, haloPulseFactor, flashAlpha, emitters,
  pendingBursts, particles, flashRemainingMillis)` mit `JuiceState.EMPTY`;
  dazu `ParticleSnapshot` (Structure-of-Arrays), `SparkEmitter`,
  `ScheduledBurst`, `BurstKind { CRYSTAL, FIREWORK }`.
  Dokumentierte Abweichungen von der PW-4.2-Deklaration:
  (1) vier Simulationsfelder im Snapshot (`vxDp`, `vyDp`,
  `gravityDpPerSec2`, `alphaFadePerMillis`) — der pure Stepper muss
  Geschwindigkeit/Gravitation/Restlebensdauer zwischen Frames tragen;
  (2) `flashRemainingMillis` als eigener Zeitzähler, `flashAlpha` ist nur
  dessen Projektion (§13.10-Kurve bzw. Reduce-Motion-Dreieck).
- **DefaultJuiceStepper.kt** — `internal class DefaultJuiceStepper(
  factory: JuiceRandomFactory = DefaultJuiceRandomFactory()) : JuiceStepper`
  mit purem `step(state, events, dtMillis): JuiceState`. Reihenfolge pro
  Frame: Partikel integrieren (semi-implizites Euler in ParticleBuffer) →
  Flash-Restzeit abbauen → Events anwenden (ScreenEntered,
  MotionPreferenceChanged, RotateFlash, CrystalBursts, EndpointsChanged,
  Solved, Dismissed) → fällige Bursts spawnen → Endpunkt-Funken emittieren
  (globale 250-ms-Kadenz = 4/s je Emitter, §13.8a) → Snapshot einfrieren.
  Alle §13-Konstanten liegen als private Konstanten oben in der Datei
  (Funken 400 ms/60 dp/s/2 dp; Kristall P=8+nextInt(5), 600 ms, 80–160 dp/s,
  Gravitation 240; Kaskade 40 ms Versatz, Kappe ab dem 5. Burst (R45);
  Feuerwerk F=min(120, 60+12·K), 120–320 dp/s, 0,9–1,4 s, Gravitation 480;
  Flash 0,35→0/80 ms bzw. RM 0→0,15→0/400 ms; Halo-Puls 1±0,2 bei exakt
  2 Hz ab elapsed=0).
- **ParticleBuffer.kt** — veränderlicher SoA-Pool NUR im Step-Pfad
  (ADR-011: Allokationsfreiheit gilt verbindlich nur für den Draw-Pfad).
  API: `load(snapshot)`, `add(...)`, `integrate(dtMillis)` (kompaktiert
  Tote, Alpha ≤ 0), `clear()`, `toSnapshot()`. **Kapazitätsgrenze
  `MAX_PARTICLES = 512`** (Worst-Case Feuerwerk 120 + Kaskade + Funken);
  `add` verwirft ab Kapazität still — nie überschreiten, nie crashen.
- **JuiceRandom.kt** — `DefaultJuiceRandomFactory` (SplitMix64 aus :core,
  ADR-003), normative Seed-Ableitung `juiceSeed(levelSeed, moveNumber,
  emitterIndex) = mix64(mix64(mix64(levelSeed)+zugNummer)+emitterIndex)`
  und `RandomSource.nextUnit(): Float` in [0,1) aus den oberen 24 Bit.

Determinismus-Ansatz (das MUSS ein Nachfolger verstehen):

- Zufall wird AUSSCHLIESSLICH beim Spawn gezogen (Spawn-only); die
  Integration ist reine Arithmetik. Gleiche `(state, events, dt-Folge)` ⇒
  bit-identische Snapshots (Tests vergleichen per `contentEquals`, weil
  Arrays in data classes nur Referenz-equals haben).
- Pro Spawn-Quelle eine frische PRNG-Instanz aus `juiceSeed(...)` — nie
  eine geteilte Quelle, dadurch reihenfolgeunabhängig. Emitter-Indizes:
  Endpunkte = Position in `TraceResult.endpoints`; Kristall-Bursts =
  1000+Kaskadenposition; Feuerwerk = 2000.
- Endpunkt-Funken: Pro-Funke-Seed = Emitter-Seed + `spawnedCount + j`;
  `spawnedCount` wandert im `SparkEmitter` mit. Beim Kristall-Burst wird
  der P-Zug (Partikelzahl) bei der PLANUNG gezogen und beim Spawn per
  einmaligem `rng.nextInt(CRYSTAL_P_SPAN)` übersprungen — Stream-Position
  nicht verändern, sonst bricht die Determinismus-Testreihe.
- Zeitachse: `elapsedMillis` startet bei `ScreenEntered` auf 0 (Puls-
  Nullpunkt §13.8a); `ScheduledBurst.startAtMillis` ist absolut auf dieser
  Achse (Kaskadenversatz eingerechnet). Feuerwerk-Ursprung = letzter
  Kristall-Burst desselben Zugs; der Flash wird vom Feuerwerk-Start
  getriggert (auch bei 0 Partikeln, R44-Fade).

Reduce-Motion (R44): Spawns erzeugen 0 Partikel (RotateFlash no-op,
particleCount=0 bei Burst/Feuerwerk, keine Endpunkt-Funken),
`haloPulseFactor` konstant 1,0, Flash wird zum 400-ms-Fade (Peak 0,15).
Zeitpunkte/Fristen bleiben unverändert; leere Snapshots sind gültig.
`Dismissed` (R49) räumt Partikel, Bursts, Emitter und Flash vollständig.

Stolpersteine/Learnings: `JuiceStepper`/`JuiceEvent`/`EndpointSpark` waren
schon aus PW-4.2 deklariert (JuiceStepper.kt, JuiceEvent.kt) — dagegen
implementieren, nicht neu deklarieren. Gate lokal: JAVA_HOME muss gesetzt
sein (Android-Studio-JBR funktioniert).

### Aufgaben für den nächsten Agenten

- **PW-4.5 — Laser-Rendering im BoardCanvas**: Canvas-only gemäß ADR-011,
  KEIN AGSL. Einen `DefaultJuiceStepper` pro Frame treiben (Frame-Clock →
  `step`), den `JuiceState`-Snapshot in EINEM zusätzlichen Canvas-Layer
  mit `BlendMode.Plus` zeichnen; im Draw-Pfad keine Allokationen —
  indexbasiert `0 until particles.count` über die SoA-Arrays iterieren
  (nur count/xDp/yDp/sizeDp/alpha/colorArgb lesen, die Simulationsfelder
  ignorieren). Halo-Grundalpha mit `haloPulseFactor` multiplizieren,
  `flashAlpha` als additives Vollbild-Weiß. Funken-Input kommt aus den
  JuiceState-Emittern (endpoints via ADR-012).
- **PW-4.8 — AudioEngine nach ADR-010**: AudioTrack-Mixer für die 4 Stems
  (PCM vorab dekodiert, sample-exakter Loop), SoundPool für SFX; gegen das
  in PW-4.2 deklarierte AudioEngine-Interface implementieren. Läuft
  PARALLEL zu PW-4.5 — Dateien sind disjunkt (audio/ vs. ui/juice/ +
  BoardCanvas), keine gemeinsamen Berührpunkte.

## PW-4.5 — ui-entwickler → ui-entwickler (PW-4.6) (2026-07-21)

### Kontext

Gebaut: der Canvas-only-Laser-Renderpfad (§13.8a, ADR-011) plus der
withFrameNanos-Treiber des Juice-Kerns. Kein Stepper-Code angefasst.

- **BoardLaserRender.kt (neu, ui/game)** — `drawLaserBeams(beams, halo-
  PulseFactor)`: je Strahl DREI additive Halo-Ringe (12/8/5 dp, BlendMode.Plus,
  Alpha-Treppe 0,14/0,18/0,23 — Summe am Kern 0,55, radial auslaufend) in
  Strahlfarbe, darüber der weiße 3-dp-Kern (#F0F0F3, pulst NICHT), zuoberst der
  §13.2-Musterkanal in Strahlfarbe auf Kernbreite. Sekundärfarben tragen ihre
  Mischfarbe im Halo; Kreuzungen mischen additiv; Mischfarben-Chips bleiben im
  Overlay. `drawJuiceEffects(juice)`: SoA-Partikel indexbasiert (nur count/x/y/
  size/alpha/color), Flash als additives Weiß-Rect ÜBER DER BRETTFLÄCHE —
  Achtung: das ist NICHT das Vollbild aus §13.10, nur der BoardCanvas
  (Korrektur-Auflage MINOR-2, Aufgabe für PW-4.7 unten). KEINE Allokation im
  Draw-Pfad (nur Value-Klassen). Halo-Puls: Grundalpha × `haloPulseFactor` aus
  dem Snapshot.
- **ABNAHMEPFLICHTIGES Interpretations-Delta (für die PW-4.10-Gate-
  Checkliste)**: §13.8a beschreibt den Halo als KONTINUIERLICHEN radialen
  Alpha-Verlauf 55 % → 0 %. Gerendert wird eine 3-stufige Treppe aus
  konzentrischen Strichen (12/8/5 dp mit 0,14/0,18/0,23; additive Summe am
  Kern 0,55, Außenkante endet bei 0,14 → 0) — eine Canvas-Näherung, weil
  Compose keinen Quer-Gradienten über eine Strichbreite kann (Review-Verdikt
  PW-4.5: als Näherung ok, aber von Branko am Gate visuell abzunehmen).
- **JuiceFrameDriver.kt (neu, ui/juice)** — `JuiceEventQueue` (Haupt-Thread-
  Postfach, `offer`/frame-weises `drain`) + `rememberJuiceFrameState(events,
  stepper)`: Endlos-Loop über **`withInfiniteAnimationFrameNanos`** (NICHT
  rohes `withFrameNanos`, s. Stolpersteine), dt aus Frame-Nanos, **dt-Clamp
  `clampFrameDelta` auf 0..100 ms VOR `step()`** (Auflage LOW-1 aus dem
  PW-4.4-Audit: emitSparks skaliert sonst unbegrenzt nach App-Background;
  Begründung als KDoc an der Funktion). Publikations-Filter
  `rendersDifferently`: nur Puls-/Flash-/Partikel-Änderungen schreiben den
  State — unter Reduce-Motion ohne Effekte invalidiert NICHTS.
- **Einhängung** — `BoardCanvas(juice: State<JuiceState>? = null)` liest den
  Snapshot AUSSCHLIESSLICH in der Draw-Phase (`juice?.value` im Canvas-Block):
  Frame-Updates zeichnen nur neu, rekomponieren nie. `drawBoard(spec,
  haloPulseFactor)` ersetzt den alten dünnen Beam-Zeichner. Verdrahtet in
  `GameScreen.kt`/`GameBoard`: `remember { JuiceEventQueue() }` +
  `rememberJuiceFrameState`; R44 wird per
  `MotionPreferenceChanged(!animationsEnabled)` aus
  `rememberAnimationsEnabled()` in den Kern gemeldet.
- **Koordinaten-Vertrag** (KDoc an `drawJuiceEffects`): `xDp/yDp` im
  JuiceState sind dp relativ zur LINKEN OBEREN Ecke des BoardCanvas;
  gezeichnet wird mit `dp.toPx()`. Event-Erzeuger mappen Zellzentren also
  `BoardGeometry.center(coord).x / density` (Board-Ursprung = Canvas-Mitte).

Stolpersteine/Learnings:

- **Rohes `withFrameNanos` in einer Endlosschleife hängt JEDEN bestehenden
  Robolectric-Test** (per Wegwerf-Probe verifiziert: AppNotIdleException nach
  60 s — `waitForIdle` wird nie idle). `withInfiniteAnimationFrameNanos` löst
  das: die InfiniteAnimationPolicy des Compose-Test-Harness bricht den Loop
  bei `mainClock.autoAdvance` ab (E2E/GameRoute-Tests grün, Juice bleibt dort
  EMPTY), während eine manuelle Testuhr (`autoAdvance = false` **VOR**
  `setContent`, sonst ist die Coroutine schon tot) den Treiber deterministisch
  treibt — Muster in `JuiceFrameDriverTest`.
- Die Testuhr produziert nur 16-ms-Frames; ein „großes dt" ist über
  `mainClock` nicht erzeugbar — der Clamp ist deshalb als pure Funktion
  (`clampFrameDelta`) gepinnt plus Treiber-Durchleitungs-Assertion.
- detekt `LongParameterList` schlägt ab dem 6. Parameter zu (BoardCanvas:
  begründeter Suppress); ktlint verbietet EOL-Kommentare direkt unter KDoc.

### Aufgaben für den nächsten Agenten

- **PW-4.6 — Aktions-Feedback**: Ereignisdaten aus :game (`juiceDelta`/
  `MoveResult`, ADR-012) über das ViewModel als Effects zum JuiceState
  verdrahten. Einstiegspunkt ist die `JuiceEventQueue` in
  `GameScreen.kt`/`GameBoard` — für ViewModel-Effects die Queue ggf. zu
  `GameRoute`/`GameEffectHandler` hochziehen und an `GameBoard`
  durchreichen. Zu senden: `ScreenEntered(levelSeed, reduceMotion,
  endpoints)` beim Partie-Start (setzt Puls-Nullpunkt; levelSeed kommt aus
  dem ViewModel), `EndpointsChanged` nach jedem Zug
  (`TraceResult.endpoints`), `RotateFlash`, `CrystalBursts`, `Solved` sowie
  `Dismissed` bei „Nochmal"/„Weiter"/Zurück (R49). Positionen nach dem
  Koordinaten-Vertrag oben in dp mappen, Farben über `BoardColors.beam`
  (§13.4) auflösen.
- **WICHTIG (Orchestrator-Entscheidung, digest.md)**: PW-4.6 erweitert
  zusätzlich den JuiceState-Snapshot um die **§13.9-Glow-Einträge**
  (Glow-Burst: Radius 0→28 dp, Alpha 0,8→0, 250 ms, additiv) — Glow lebt
  datenseitig; das ADR-011-Delta im PR dokumentieren. Einstiegspunkte:
  `JuiceState.kt` (neues Snapshot-Feld analog `pendingBursts`),
  `DefaultJuiceStepper` (`onCrystalBursts`/`fireBursts` planen bereits die
  Burst-Zeitpunkte — Glow dort mit erzeugen), Rendering in
  `BoardLaserRender.drawJuiceEffects` (RadialGradient + BlendMode.Plus,
  ADR-011), UND der Publikations-Filter `rendersDifferently` in
  `JuiceFrameDriver.kt` muss Glow-Änderungen mit veröffentlichen, sonst
  bleibt der Glow unsichtbar.
- **PW-4.7 — Flash auf Screen-Ebene (Auflage MINOR-2 aus der PW-4.5-
  Korrekturrunde)**: §13.10 verlangt einen VOLLBILD-Flash; aktuell füllt das
  Weiß-Rect nur den BoardCanvas (Kopfzeile/Buttons bleiben unbeleuchtet).
  Entweder den Flash als Overlay im GameScreen-Root hochziehen (dasselbe
  `State<JuiceState>` lesen, nur `flashAlpha`, Draw-Phase-Read wie im
  BoardCanvas) ODER die Brett-only-Abweichung Branko explizit zur Abnahme
  vorlegen.
- Der dt-Clamp und der Sub-ms-Rest-Übertrag bleiben im Treiber
  (`consumeFrameDelta` — nicht in den Stepper verschieben);
  Emitter-/Spawn-Logik im Stepper nur konsumieren.
## PW-4.8 — ui-entwickler → test-engineer (PW-4.9) (2026-07-21)

### Kontext

Gebaut: die komplette AudioEngine nach ADR-010 (SoundPool-SFX +
AudioTrack-Mixer für die 4 Stems) plus die getrennten Settings-Schalter.

- **Vertrag/Deklarationen** `app/src/main/kotlin/de/puzzlewerk/app/audio/AudioEngine.kt`
  (aus PW-4.2, ergänzt): `StemMix.forProgress(fulfilled, crystalTotal)` —
  pure Tabelle §13.11 (Ebene 2 ab L ≥ 1, Ebene 3 ab 2·L ≥ K, Ebene 4 ab
  L ≥ max(1, K − 1)); NEU im Interface: `setHostVisible(visible)` für den
  Activity-Lebenszyklus (onStop pausiert wie Fokus-Verlust, onStart setzt
  an derselben Cursor-Position fort). Das ist eine bewusste Ergänzung des
  ADR-010-Vertrags — bei Einwänden an den Architekten eskalieren.
- **Purer Mixer-Kern** `StemMixerCore.kt`: EIN gemeinsamer Loop-Cursor
  über exakt 756 000 Frames (Modulo-Arithmetik ⇒ Synchronität und
  sample-exakter Loop per Konstruktion), lineare 250-ms-Fades je Ebene
  (Retarget mid-fade klickfrei), Duck-Envelope 50 ms → 20 % / 500 ms /
  250 ms mit klickfreiem Retrigger; `null`-Stems werden als Stille
  gemischt; Summe wird geklammert. Kein Android-Import, keine Allokation
  im Mix-Pfad.
- **Engine** `DefaultAudioEngine.kt`: 4 Adapter-Seams (StemDecoder,
  PcmSink, SfxPlayer, FocusRequester) + Mixer-Thread-Factory als
  Test-Seam. Der Mixer-Thread dekodiert die Stems beim ersten
  `enterGame` (gecacht über Partien), pumpt 20-ms-Blöcke
  (`MIX_BLOCK_FRAMES = 882`); `prepareMixer()`/`pumpOnce()` sind
  `internal` und werden in Tests direkt gepumpt (kein Thread, kein Ton).
  Fehler sind Werte auf `issues` (AssetUnavailable je Stem,
  EngineUnavailable "mixer"/"focus"/"decoder"). `setStemMix` vor
  Mixer-Aufbau wird gepuffert (pendingMix). R48: bei Musik AUS wird
  weder Senke noch Fokus angefasst; bei SFX AUS erreicht kein Aufruf
  den SoundPool.
- **Android-Adapter** `AndroidAudioAdapters.kt`: MediaExtractor/MediaCodec
  → Mono-PCM normiert auf exakt 756 000 Frames (Überhang ab-, Fehlbestand
  mit Stille aufgefüllt; falsche Sample-Rate ⇒ `null`); AudioTrack
  (USAGE_GAME/CONTENT_TYPE_MUSIC, Mono/44,1 kHz); SoundPool (SONIFICATION,
  12 Einmal-SFX + Laser-Loop 20 %, Vorladen beim App-Start via
  AppContainer/Application.onCreate); AudioFocusRequest(GAIN) +
  ACTION_AUDIO_BECOMING_NOISY als Verlust ohne Rückkehr. Alle Adapter
  fangen Plattform-Exceptions (C3).
- **PCM-Budget (gemessen an den OGG-Headern, Skript-Check der 18 Dateien):**
  alle Quellen sind MONO, 44 100 Hz, Stems exakt 756 000 Samples ⇒
  dekodiert mono 4 × 1 512 000 B ≈ 5,77 MiB statt ≈ 11,5 MiB stereo.
  Entscheidung: Mono-Pfad (CHANNEL_OUT_MONO) — verlustfrei, da die
  Quellen einkanalig sind; der ADR-010-Hörtest-Vorbehalt entfällt.
- **Settings (:data, Schema v2 + Migration + Golden):**
  `Settings.musicEnabled`/`sfxEnabled` ersetzen `soundEnabled` (beide
  Default AN). `SettingsSchema.kt` (umbenannt aus SettingsSchemaV1.kt):
  `currentVersion = 2`, Migration v1→v2 exakt nach §13.11 (AUS ⇒ beide
  AUS; AN oder Feld fehlt ⇒ beide AN); Golden-Dateien
  `settings_v1.json` (Migrations-Input) und neu `settings_v2.json`.
  Es gibt noch KEINEN Settings-Screen (Punkt 9) — die Schalter sind
  bislang nur Datenmodell.
- **Stolpersteine/Learnings:** (1) SoundPool spielt erst nach dem
  asynchronen Load — direkt nach App-Start können SFX in den ersten
  Millisekunden verschluckt werden (bewusst akzeptiert). (2) Ein
  SoundPool-Init-Fehler degradiert still (kein `issues`-Event Stufe
  "soundpool") — bei Bedarf in PW-4.9/PW-4.10 nachrüsten. (3) Die
  Choreografie (wer wann `enterGame`/`setStemMix`/`playSfx` ruft) ist
  NICHT Teil dieses Tickets — sie kommt mit PW-4.6 (Aktions-Feedback);
  aktuell konsumiert noch niemand die Engine zur Laufzeit.

### Aufgaben für den nächsten Agenten

test-engineer (PW-4.9), Einstiegspunkte:

- `FakeAudioEngine` (app/src/test/…/audio/): rein aufzeichnend
  (enterGameCalls, stemMixHistory, duckCount, playedEffects,
  laserLoopActive, hostVisibleHistory, `emitIssue(...)`) — Oberfläche
  für ViewModel-/Choreografie-Tests (R45-Ketten, R46-Folgen), sobald
  PW-4.6 verdrahtet.
- Ebenen-Schwellen beidseitig (49 %/50 %): `StemMix.forProgress` —
  StemMixProgressTest deckt K ∈ {1, 2, 5, 8} ab; ergänze weitere
  K-Werte/Property-Tests (z. B. ungerade K um die 50-%-Kante, K = 50).
- Duck + Restore: `StemMixerCore` (startDuck/duckFactor/mixInto,
  Test-Seam `loopFrames` verkleinerbar) — prüfe zusätzlich Duck WÄHREND
  eines laufenden Fades (Multiplikation, R50) und Duck-Retrigger in der
  Attack-Phase.
- Settings-Kombinationen: `DefaultAudioEngine` mit den vier Fakes aus
  DefaultAudioEngineTest (FakeDecoder/FakeSink/FakeSfxPlayer/FakeFocus +
  DormantThread-Factory; Muster dort kopierbar) — Matrix Musik×SFX,
  Wechsel zwischen Partien (enterGame nach exitGame).
- Fokus-Verlust mitten im Stem-Fade: `focus.onFocusChange(false)`
  zwischen zwei `pump()`-Aufrufen; danach Regain und prüfen, dass
  der Fade an der eingefrorenen Cursor-Position fortsetzt (Fade-Position
  lebt im Kern, Cursor in `StemMixerCore.cursor`).
- Exit/Re-Enter-Kante (Session-Token, MAJOR-1 der Korrekturrunde):
  Jedes `enterGame` erzeugt eine `DefaultAudioEngine.MixerSession`
  (eigener Sink, Mix-Puffer, Kern, `active`-Token); der Thread-Body
  prüft NUR sein Token. Einstieg: `engine.activeSession` (Test-Seam) +
  `mixerThreadFactory` fängt die Runnables — Muster im Test
  `Exit und Re-Enter waehrend des Decodes …` (DefaultAudioEngineTest,
  FakeDecoder-`onDecode`-Hook löst Exit+Re-Enter mitten im Decode aus).
  Weitere Kanten: Re-Enter während `pump()`-Backoff, Doppel-`exitGame`,
  Fokus-Callback einer bereits invalidierten Session.
- Robolectric-Smoke der Android-Adapter (ADR-010-Folgearbeit):
  mindestens Konstruktion + `release()` von SoundPoolSfxPlayer /
  audioTrackSinkOrNull / AudioManagerFocusRequester; MediaCodec ist
  unter Robolectric nicht real dekodierbar — nur Fehlerpfad (`null`)
  prüfbar.
- Migration-Kanten :data: SettingsSchema v1→v2 (Nicht-Objekt-Payload,
  soundEnabled mit Nicht-Boolean-Wert ⇒ beide AN, unbekannte
  v1-Felder ⇒ Korruptions-Rückfall) — Basisfälle liegen in
  DataStoreSettingsRepositoryTest.

### Nachtrag Korrekturrunde 2026-07-21 (Review PR #35)

- MAJOR-1: Mixer-Thread-Resurrection behoben — Session-Token-Architektur
  (`MixerSession` je `enterGame` mit eigenem Sink/Puffer/Kern/Token;
  `exitGame` invalidiert das Token statt lange zu joinen, Join-Kappe
  500 ms bleibt als Aufräum-Best-Effort). Deterministischer Race-Test s. o.
- MINOR-1: `exitGame` setzt `focusLost` zurück — Menü-SFX nach einer
  Partie mit Fokus-Verlust bleiben nicht mehr stumm.
- MINOR-2: SoundPool-Init-Fehler degradiert nicht mehr still —
  `SfxPlayer.available` + `EngineUnavailable("soundpool")` auf `issues`
  (einmal je Prozess, beim ersten `enterGame` mit aktiven Effekten).
- ADR-010 hat jetzt ein datiertes Addendum zu `setHostVisible`
  (Orchestrator-Auflage); Größen-Ausnahme 868 Zeilen genehmigt.

## PW-4.6 — ui-entwickler → ui-entwickler (PW-4.7) (2026-07-21)

### Kontext

Gebaut: die komplette Aktions-Feedback-Verdrahtung (§13.9/§13.11) — Events
aus :game fließen jetzt bis in Partikel, Glow und Ton.

- **Event-Fluss (V2, PW-4.6):** `GameViewModel.onApplied(applied, move)` ruft
  `juiceDelta(lastTrace, trace, board)` (:game, ADR-012) und emittiert EIN
  `JuiceFeedback` je Ereignis über einen zweiten Channel
  (`viewModel.juiceFeedback` — getrennt von `effects`, weil Channels nur
  einen Konsumenten je Element haben). Typen + UI-Mapping liegen in
  `ui/game/GameJuiceFeedback.kt`: `BoardEntered(levelSeed, endpoints)` beim
  Partie-Start (`move == null`), `MoveApplied(moveNumber, rotatedCell,
  newlyFulfilled, endpoints, solved)` je Zug, `EffectsDismissed` (R49).
  `GameBoard` (GameScreen.kt) ist jetzt ein `BoxWithConstraints`, baut per
  `remember` ein `JuiceEventMapping` (BoardGeometry.fit mit denselben
  Constraints wie der BoardCanvas + Density + BoardColors) und collectet
  das Feedback → `offerJuiceEvents(queue, feedback, mapping)` → Queue →
  Treiber (`rememberJuiceFrameState`) → Stepper → Renderer.
- **Solved-Kontrakt (kodifiziert als KDoc an `JuiceEvent.Solved`):** Solved
  wird im SELBEN Frame NACH `CrystalBursts` desselben Zugs eingereiht —
  `offerJuiceEvents` legt beide in einem Rutsch ab (ein `drain()` = ein
  Frame). Der Stepper verankert das jetzt hart: `CascadeInfo.moveNumber`
  (vorher tot) filtert Kaskaden fremder Züge (`takeIf { it.moveNumber ==
  event.moveNumber }`). Test: `GameJuiceFeedbackTest`.
- **Glow (§13.9, ADR-011-Feldlisten-Delta):** `JuiceState.glows:
  List<GlowBurst>` (xDp, yDp, colorArgb, ageMillis; Projektionen `radiusDp`
  0→28 dp, `alpha` 0,8→0 über `GLOW_LIFETIME_MILLIS` = 250 ms). Entsteht im
  Stepper BEIM FEUERN eines CRYSTAL-Bursts (`spawnGlow`, Kaskade = zeitlich
  gestaffelte Glows), altert in `Frame.ageGlows`, stirbt bei ≥ 250 ms;
  ScreenEntered/Dismissed räumen. Reduce-Motion ⇒ KEIN Glow-Eintrag
  (§13.12 ersetzt den Blitz durch den Aura-Fade der 13.3-Schicht — der ist
  dort HEUTE NICHT animiert; bewusste Lücke, s. Aufgaben). Gerendert in
  `drawJuiceEffects` als 3-Stufen-Kreis-Treppe (0,27/0,33/0,40 des
  Glow-Alphas, Radien 1/0,66/0,33) — Canvas-Näherung ANALOG zur
  abgenommenen Halo-Treppe statt RadialGradient-Brush, weil frische
  Brushes je Frame den allokationsfreien Draw-Pfad (ADR-011) verletzen
  würden. `rendersDifferently` veröffentlicht Glow-Frames (PW-4.5-Warnung
  eingelöst; Robolectric-Test im JuiceFrameDriverTest).
- **Audio-Verdrahtung (§13.11):** `GameAudioChoreographer` (ui/game) kapselt
  alle Engine-Aufrufe; das ViewModel hält ihn als 6. Konstruktor-Parameter
  (LongParameterList-Grenze!). `enter()` liest die Settings-Schalter
  (`settings.first()`) und ruft `enterGame`; `exit()` ⇒ `exitGame`.
  Lebenszyklus: `DisposableEffect(viewModel)` in GameRoute ruft
  `onScreenEntered`/`onScreenLeft` — NICHT `onCleared` (das partie-
  geschlüsselte ViewModel leakt bounded und würde nie/zu spät exiten); die
  Slot-Dispose-Reihenfolge garantiert exit(alt) VOR enter(neu) bei
  „Weiter". Je Applied: `rotate_tick` (nur Move.Rotate), SFX-Kette
  lit/up1/up2/up3 über `comboSize`, `beam_connect` (nur ohne neue
  Erfüllung), bei Lösung `solve_explosion` + `duckForSolve`,
  `setStemMix(StemMix.forProgress(L, K))` nur bei Mix-Änderung (R46 auch
  abwärts), `setLaserLoopActive(segments.isNotEmpty())` nur bei Wechsel.
  Ungültig ⇒ `rotate_invalid`. „Nochmal" ⇒ exit + enter + Dismissed +
  BoardEntered (R49). AppContainer: `settingsRepository` ist übergangsweise
  `FakeSettingsRepository` (Defaults AN, analog InMemoryProgressRepository,
  bis Settings-Screen Punkt 9); die Engine kommt als Lazy-Provider
  `{ audioEngineInstance }` (Container-JVM-Tests ohne Application ⇒ No-op).
- **Dreh-Blitz:** `rememberRotateFlash` (GameRotationAnimation.kt) blendet
  ein weißes Element-Overlay 0,6→0/120 ms (BoardCanvas-Param `flash`,
  additiv, Radius 0,7·cellSize); `JuiceEvent.RotateFlash` liefert dazu die
  3 deterministischen Funken aus dem Stepper. Beide nur bei
  `animationsEnabled` (reduce-motion-fest). Wackeln unverändert Compose.
- **Queue-Härtung (PW-4.5-Security-MINOR-2):** `JuiceEventQueue.offer`
  droppt still ab `MAX_PENDING_JUICE_EVENTS` = 64 (Politik wie
  ParticleBuffer; Begründung als KDoc: wächst nur bei stehendem
  Frame-Loop, Effekte sind nie tragender Kanal).
- **Stolpersteine:** (1) `viewModel.effects`/`juiceFeedback` sind Channels —
  jeder neue Konsument braucht einen EIGENEN Channel, nie doppelt
  collecten. (2) SFX feuern beim Zug-Commit, NICHT mit 40-ms-Kaskadenversatz
  (bewusste Vereinfachung: Versatz ist visuelle Stepper-Zeit; bei Bedarf in
  PW-4.7 mit Test-Clock-Delays nachrüsten). (3) `sfx_ui_tap` (Buttons/
  Overlay) und `sfx_star_n` sind NOCH UNVERDRAHTET. (4) detekt-Grenzen:
  Konstruktoren ab 7 Nicht-Default-Parametern, Funktionen ab 6 — deshalb
  Choreograph-Objekt und `JuiceEventMapping`-Bündel.

### Aufgaben für den nächsten Agenten

- **PW-4.7 — Lösungs-Feuerwerk + Sterne-Choreografie (§13.10):** Feuerwerk-
  Daten (Solved-Event, t_fw, F-Partikel) laufen bereits bis in den Stepper;
  es fehlen die Screen-Seite: Sterne fliegen einzeln mit Bounce ein
  (Compose, Start `t_fw + 120 + (n−1)·150 ms`, SFX `sfx_star_n` je Stern —
  Choreograph erweitern), Overlay-Fade ab 500 ms, **Buttons sichtbar UND
  interaktiv ≤ 600 ms** (abgenommene V3-Abweichung; Worst Case
  nachgerechnet in §13.10).
- **ÜBERNOMMENE Auflage aus PW-4.5 (MINOR-2, weiter offen):** §13.10
  verlangt einen VOLLBILD-Flash; das Weiß-Rect füllt weiterhin nur den
  BoardCanvas. Entweder als Overlay im GameScreen-Root hochziehen (dasselbe
  `State<JuiceState>`, nur `flashAlpha`, Draw-Phase-Read) ODER die
  Brett-only-Abweichung Branko explizit zur Abnahme vorlegen.
- **Solved-Event-Kontrakt einhalten:** Neue Produzenten MÜSSEN Solved im
  selben Frame nach CrystalBursts einreihen (KDoc an `JuiceEvent.Solved`;
  Referenzpfad `offerJuiceEvents`).
- **Abbruch-Kanten R49:** „Nochmal"/„Weiter"/Zurück während laufender
  Stern-/Feuerwerk-Choreografie müssen sauber abbrechen — Juice-Seite ist
  über `EffectsDismissed`/Composition-Teardown abgedeckt, die neuen
  Compose-Stern-Animationen und Stern-SFX brauchen eigene Abbruchpfade
  (kein SFX nach Dismiss).
- **Klein, falls Zeit:** §13.12-Aura-Fade unter Reduce-Motion (250 ms Fade
  der 13.3-Leuchtaura beim Erfüllungswechsel, Kristall-Render-Schicht) ist
  noch nicht animiert — nachziehen oder als Abweichung zur Abnahme geben;
  `sfx_ui_tap` bei Buttons/Overlay-Aktionen verdrahten.

### Nachtrag Korrekturrunde 2026-07-21 (Review PR #37)

- MAJOR-1 behoben: `onScreenEntered()` sendet bei laufender Partie ein
  `BoardEntered(levelSeed, endpoints)` NACH (Recreation/Wiedereintritt:
  ViewModel überlebt, Queue+JuiceState sind frisch — ohne Nachsendung
  keine Endpunkt-Funken, levelSeed 0, falscher Puls-Nullpunkt).
  BEWUSSTE, DOKUMENTIERTE ABWEICHUNG: Die Audio-Session startet bei
  Recreation per exit+enter neu (Stems ab Sample 0) — Session-Erhalt
  wäre ein eigenes Ticket; Produktentscheidung (Portrait-Lock vs.
  configChanges vs. Session-Erhalt) liegt als Backlog-Notiz (Technik)
  für die Gate-Vorlage an Branko/game-designer.
- MAJOR-2 behoben — die frühere Behauptung, Solved-Kontrakt und
  Stem-/Laser-Dedup seien testabgedeckt, stimmte nur für den
  Positivpfad. Jetzt mutationsfest (Probe: Filter/Dedup entfernt ⇒ rot):
  `Solved mit fremder zugNummer …` (DefaultJuiceStepperTest) und
  `Gleicher Fortschritt sendet setStemMix und setLaserLoopActive nicht
  erneut` (GameViewModelJuiceAudioTest; FakeAudioEngine hat dafür neu
  `laserLoopHistory`).
- MINOR-2: Dreh-Blitz hängt jetzt am SEMANTISCHEN Signal
  `GameUiState.rotatedCell` (nur `Move.Rotate`; Undo/Reset ⇒ null) —
  `rememberRotateFlash(board, rotatedCell, animationsEnabled)` nutzt
  keine Board-Diff-Heuristik mehr.
- MINOR-3: Compose-Dreh-Blitz gepinnt (Peak exakt 0,6, Ende nach
  120 ms, RM ⇒ nichts) in `GameRotationAnimationFlashTest`. MERKE für
  PW-4.9: Bei pausierter Testuhr unter Robolectric braucht state-write-
  getriebene Recomposition `runOnIdle { write }` UND je Frame
  `advanceTimeByFrame()` + `waitForIdle()` im Wechsel — jede Zutat
  allein tut NICHTS (drei Anläufe gekostet).
- NIT-3 (R31): gelöst GELADENER Startzustand löst weder
  solve_explosion noch Duck aus (`solvedByMove = justSolved &&
  move != null`).

## PW-4.7 — ui-entwickler → test-engineer (PW-4.9) (2026-07-21)

### Kontext

Gebaut: die Screen-Seite des Lösungs-Feuerwerks (§13.10) — Vollbild-Flash,
Stern-Einflüge samt SFX und die 600-ms-Overlay-Frist. Kein :game-Code
angefasst; Stepper nur minimal (Formel-Extraktion, s. u.).

- **Flash auf SCREEN-Ebene (Auflage MINOR-2 aus PW-4.5, eingelöst):**
  `JuiceEventQueue` + `rememberJuiceFrameState` sind aus `GameBoard` in den
  `GameScreen`-Root hochgezogen (GameBoard bekommt Queue und
  `State<JuiceState>` als Parameter; das Event-Mapping mit Brett-Geometrie
  bleibt in GameBoard). `SolveFlashOverlay` (GameScreen.kt, testTag
  `solveFlash`, Konstante `SOLVE_FLASH_TEST_TAG`) liegt als LETZTES Kind des
  Root-Box über Kopfzeile, Brett UND Ergebnis-Overlay und liest `flashAlpha`
  AUSSCHLIESSLICH in `drawBehind` (Draw-Phase-Read, keine Recomposition je
  Frame); kein `pointerInput` — Eingaben laufen durch (R32 sperrt das Brett).
  `BoardLaserRender.drawJuiceEffects` zeichnet KEINEN Flash mehr. Beide
  Alpha-Kurven (0,35→0/80 ms; Reduce-Motion-Dreieck 0→0,15→0/400 ms) kommen
  unverändert aus dem Stepper (`flashAlpha`-Projektion, Tests PW-4.4/4.6).
- **Sterne-Choreografie (§13.10 Nr. 5, GameStarAnimation.kt):**
  `rememberStarAppearance(startMillis, animationsEnabled, onShown)` wartet
  über ein Animatable auf der FRAME-UHR (bewusst KEIN `delay()` — das hinge
  an der echten Looper-Zeit, Learning-Kette PW-4.5/4.6), ruft `onShown` beim
  Einflug und fährt den Bounce als Keyframes 0 → 1,15 (bei 150 ms) → 1,0
  (220 ms). Start `t_fw + 120 + (n−1)·150 ms` (`starEntryStartMillis`).
  Reduce-Motion: GLEICHE Zeitpunkte, statt Bounce ein 150-ms-Fade (§13.12);
  `onShown` feuert auch dann (Audio ist von RM unberührt). Scale/Alpha sind
  PROVIDER, gelesen nur in der `graphicsLayer`-Lambda (keine Recomposition
  je Animations-Frame). TalkBack unverändert: Glyphen-Row komplett
  `clearAndSetSemantics {}`, Wertung trägt die Textzeile (PW-3.7-QS grün).
- **t_fw-Quelle:** `fireworkStartMillis(cascadeSize)` liegt jetzt internal in
  DefaultJuiceStepper.kt (einzige Formel-Quelle, `40·(min(N,5)−1)`); der
  Stepper nutzt sie für den Burst-Start, das ViewModel schreibt sie als
  `GameResult.fireworkStartMillis` in den UiState (R31/gelöst geladen ⇒ 0,
  kein lösender Zug). N kommt aus `juiceDelta.comboSize` des lösenden Zugs.
- **SFX-Fluss der Sterne:** Overlay → `GameIntent.StarShown(n)` (MVI, kein
  Extra-Kanal) → ViewModel-Guard `result != null` (fängt Nachzügler nach
  Replay) → `GameAudioChoreographer.onStarShown` → `sfx_star_1..3`. R49:
  Composition-Teardown des Overlays bricht wartende Einflüge ab — kein SFX
  nach Dismiss (Tests in GameOverlayFireworkTest/GameStarAnimationTest).
- **Overlay-Buttons (§13.10 Nr. 6 + abgenommene V3-Abweichung):**
  `rememberOverlayActionsAppearance` fährt Alpha 0→1 über 500–600 ms und
  schaltet `interactive` exakt am Fade-Ende frei. WICHTIG: Das Gate macht
  Klicks WIRKUNGSLOS statt die Buttons zu disablen — Semantik/TalkBack-Fläche
  unverändert, bestehende QS-/E2E-Tests bleiben grün (autoAdvance treibt den
  Fade zu Ende, bevor performClick wirkt). Titel/Sterne/Punkte des Overlays
  stehen ab Commit (t = 0), nur die Knöpfe faden.
- **Bewusste Abweichungen, die BLEIBEN (Gate-Abnahme):** (1) SFX-Kaskaden-
  versatz 40 ms + solve_explosion bei Zug-Commit statt t_fw — Optional-Punkt
  PW-4.7 NICHT umgesetzt, die PW-4.6-Abweichung gilt weiter. (2) §13.12-
  Aura-Fade der 13.3-Schicht weiterhin nicht animiert. (3) `sfx_ui_tap`
  weiterhin unverdrahtet. (4) Halo-/Glow-Treppen (PW-4.5/4.6) bleiben
  abnahmepflichtige Canvas-Näherungen. (5) Stern-Bounce-Ende 800 ms > 600-ms-
  Frist ist die im Design kodifizierte, abgenommene V3-Abweichung.
- **Stolpersteine:** (a) 16-ms-Frame-Raster ⇒ Fenster- statt Punkt-
  Assertions (Peak-Sampling trifft 1,15 nie exakt — Overshoot > 1,0 prüfen).
  (b) GameScreen-Tests mit exakten Intent-Listen müssen `StarShown`
  herausfiltern (autoAdvance spielt die Einflüge ab). (c) Klick-Gate testet
  sich über „Klick verpufft", NICHT über `assertIsNotEnabled`.

### Aufgaben für den nächsten Agenten

- **PW-4.9 — Abbruch-Kanten:** Navigation („Weiter"/Zurück) und Prozess-/
  Activity-Recreation WÄHREND laufender Effekte: Queue-/JuiceState-Reste,
  Stern-LaunchedEffects, Audio exit/enter-Reihenfolge. Bekannte Kante:
  Nach Recreation spielt das Overlay die Stern-Choreografie ERNEUT ab
  (ViewModel überlebt, Composition ist frisch) — bewerten/pinnen.
- **Reduce-Motion-Matrix ALLER Effektpfade** (§13.12/R44): Flash-Fade statt
  Blitz, Sterne-Fade statt Bounce (gleiche Zeitpunkte), Partikel 0, Glow
  leer, Puls statisch, Dreh-Blitz/Wackeln aus — Zeitachsen und
  Overlay-Frist konstant; Mid-Session-Umschaltung (MotionPreferenceChanged).
- **Frame-Budget-Smoke:** `step()` < 4 ms bei Volllast (512 Partikel,
  Kaskade + Feuerwerk) — ADR-011-Rückfalltür (p95 < 55 fps ⇒ Folge-ADR).
- **Audio-Kanten aus dem PW-4.8-Handover** (Fokus-Verlust R47, Schalter R48,
  Session-Token-Races) gegen die reale Choreographen-Verdrahtung.
- **Bounds-Tests ungepinnter Konstanten** aus dem Backlog-Hinweis im
  PW-4.4-Abschnitt dieses Dokuments (Speed-/Lebensdauer-Spannen etc.).

## PW-4.9 — test-engineer → release-engineer (PW-4.10) (2026-07-21)

### Kontext

Unabhängiger QS-Pass über die Punkte 3–8 (Juice + Audio) gegen §13.7–13.13
und §15 (R44–R50). **Verdikt: PASS** — der Kern hält Determinismus,
Kapazität, Reduce-Motion und die Audio-Kanten; 2 echte Bugs gefunden
(dokumentiert, NICHT gefixt, als @Ignore-Regressionstests eingecheckt).

Neue Tests (8 QS-Dateien NEBEN den Entwickler-Tests, keine Duplikate):

- **JuiceDeterminismQsTest** — 1000-Frame-Property (§13.13): gleiche Seeds
  + gleiche Event-Folge (ALLE Event-Typen inkl. Mid-Session-RM und
  Dismissed) ⇒ bit-identische JuiceState-FOLGE inkl. Glow/Emitter/Bursts;
  anderer levelSeed ⇒ andere Partikel bei identischer Zeitachse.
- **JuiceCapacityQsTest** — legaler §13-Worst-Case (K = 6 + Feuerwerk 120 +
  Emitter + Dreh-Funken, Peak ≥ 171) und Sättigungs-Missbrauch: Kappe 512
  wird exakt erreicht, NIE überschritten; Dismissed räumt die Sättigung.
- **JuiceParticleBoundsQsTest** (delegierte Review-MINORs): Speeds über
  sqrt(vx²+vy²) — Kristall 80–160, Feuerwerk 120–320, Dreh-Funken exakt 90
  mit Winkeln 30/150/270, Endpunkt-Funken exakt 60; Gravitation 240/480/0;
  Lebensdauern 600/900–1400/300/400 ms über alphaFadePerMillis; P ∈ {8…12}
  trifft beide Ränder (300 Seeds); Feuerwerks-Farbzyklus.
- **JuiceReduceMotionMatrixQsTest** — jeder Stepper-Effektpfad einzeln
  unter R44 (Dreh-Funken, Endpunkt-Emitter, Kaskaden-Plan, t_fw identisch),
  Mid-Session-Umschaltung BEIDE Richtungen (Puls-Phase bleibt auf der
  Gesamtzeitachse; Bestand läuft natürlich aus) + IST-Pin: RM-Umschaltung
  MITTEN im 80-ms-Flash wechselt hart auf die Dreieckskurve (Alpha 0,175 →
  0,03; definiert, crashtfrei — §13 regelt die Kante nicht, Gate-Bewertung).
- **StemMixerCoreQsTest** — Schwellen-Property ALLER K ∈ 1..50 beidseitig
  (inkl. K = 50: 48 % aus / 50 % an; K = 49), Duck WÄHREND laufendem Fade
  (Sample = Ton·Fade·Duck, R50), Duck-Retrigger in der Attack-Phase und
  Fade-Retarget mid-fade — beide klickfrei.
- **DefaultAudioEngineQsTest** — Settings-Matrix Musik×SFX (R48) inkl.
  Partie-Wechsel, Fokus-Verlust MITTEN im Stem-Fade (Fortsetzung
  bit-identisch zur ununterbrochenen Referenz — Fade UND Cursor frieren
  gemeinsam ein, R47), Re-Enter während pump-Backoff, Doppel-exitGame,
  Fokus-Callback invalidierter Session (IST-Pin + BUG-1-Repro).
- **GameOverlayFireworkQsTest** — IST-Pin der Recreation-Kante (Overlay
  spielt die Stern-Choreografie ERNEUT, Reihenfolge 1-2-3, kein Crash) +
  BUG-2-Repro.
- **JuiceStepFrameBudgetQsTest** — Frame-Budget-Smoke: step() bei 512
  Partikeln + Kaskade/Feuerwerk-Events, **gemessen median 54 µs / p95 83 µs**
  (Gate-Schwelle großzügig: Median < 4 ms auf dem CI-Runner);
  Allokations-Messwert (kein Gate): **~44 KiB je step()** unter Volllast —
  dominiert von der Snapshot-Kopie (9 Arrays × 512) im Step-Pfad, laut
  ADR-011 zulässig (Allokationsfreiheit gilt nur für den Draw-Pfad).

**Gefundene Bugs (Fix = Entwickler-Ticket, Tests liegen @Ignore bei):**

1. **BUG-PW4.9-1** (DefaultAudioEngine): Ein VERSPÄTETER
   Fokus-Verlust-Callback nach `exitGame` setzt das engine-globale
   `focusLost` wieder auf `true`, obwohl keine Session existiert und
   `abandon()` gerufen war ⇒ Menü-/UI-SFX stumm bis zum nächsten
   `enterGame` — die MINOR-1-Regression (PW-4.8) über die
   Callback-Schiene. Repro: `DefaultAudioEngineQsTest.Fokus-Callback einer
   invalidierten Session darf Menue-SFX nicht stummschalten` (erwartet
   [UI_TAP], ist []). Fix-Idee: Callback gegen Session-Token prüfen.
2. **BUG-PW4.9-2** (rememberStarAppearance): `animationsEnabled` ist
   LaunchedEffect-Key — der System-RM-Toggle bei offenem Overlay startet
   den Einflug neu, `onShown` feuert ein ZWEITES Mal ⇒ doppeltes
   sfx_star_n (§13.11: genau eine Meldung; §13.12: Audio von RM
   unberührt). Repro: `GameOverlayFireworkQsTest.RM-Toggle mit offenem
   Overlay darf den Stern-SFX nicht wiederholen` (shownCount 2 statt 1).
   Fix-Idee: Meldung einmalig pro Composition halten (z. B.
   `remember { mutableStateOf(shown) }`-Guard), nur die Kurve umschalten.

Nachtrag 2026-07-21: BUG-1 und BUG-2 in PW-4.9-FIX behoben (PR #40),
Repro-Tests scharf.

**Ungetestete Restrisiken:** (a) Android-Adapter real (MediaCodec-Decode,
AudioTrack-Underruns, SoundPool-Latenz) — nur JVM-Fakes, der im
PW-4.8-Handover angeregte Robolectric-Konstruktions-Smoke fehlt weiter;
(b) echte p95-fps auf Gerät (ADR-011-Rückfalltür) — JVM-Microbenchmark
ersetzt kein Geräte-Profiling; (c) Stem-Synchronität auf echter Hardware
(per Konstruktion bewiesen, nicht gemessen); (d) Pixel-/Screenshot-Ebene
der Halo-/Glow-Treppen (bewusst Gate-Abnahme durch Branko).

### Aufgaben für den nächsten Agenten

PW-4.10 (release-engineer):

- **Gate-Kette auf main** nach dem Merge dieses PRs (ktlintCheck detekt
  test koverVerify :app:lintDebug :data:lintDebug :app:assembleDebug).
- **APK-Größenbudget** mit OGG-Anteil ausweisen (18 OGGs in res/raw;
  Entscheidung music_demo_steigerung.ogg entfernen/behalten dokumentieren,
  §13.11-Asset-Bilanz).
- **Shrinker-/keep.xml-Prüfung**: res/raw-SFX/Stems überleben
  Ressourcen-Shrinking (keep.xml aus PW-4.0), Release-Build baut.
- **docs/phase4-gate-checklist.md** mit ALLEN abnahmepflichtigen Deltas
  aus den Handover-Abschnitten: Halo-Treppe (PW-4.5) + Glow-Treppe
  (PW-4.6) als Canvas-Näherungen; SFX-Timing ohne 40-ms-Kaskadenversatz +
  solve_explosion bei Zug-Commit statt t_fw (PW-4.6/4.7);
  Rotation-/Recreation-Stem-Neustart + Portrait-Lock-Produktfrage
  (PW-4.7-Korrekturrunde); 800-ms-Stern-Ende > 600-ms-Frist (abgenommene
  V3-Abweichung); §13.12-Aura-Fade nicht animiert; sfx_ui_tap
  unverdrahtet; RM-Konstanten (Flash-Dreieck 0,15/400 ms, Sterne-Fade
  150 ms); NEU aus PW-4.9: Recreation-Sterne-Replay (IST-Pin),
  RM-Umschaltung mitten im Flash (IST-Pin), BUG-PW4.9-1 und BUG-PW4.9-2
  als offene Entwickler-Tickets in die Vorlage.
- **Debug-APK-Artefakt** fürs Gate bauen und ablegen; **versionName 0.4.0**
  setzen.

## PW-4.10 — release-engineer → MENSCHLICHES GATE (Branko) (2026-07-21)

### Kontext

Phase-4-Abschluss vorbereitet; Punkte 1–9 waren gemergt (PRs #30–#40).

- **Gate-Kette auf main-Stand: GRÜN.** `checkModuleGraph ktlintCheck
  detekt test koverVerify :app:lintDebug :data:lintDebug
  :app:assembleDebug` — BUILD SUCCESSFUL (210 Tasks), keine Failures;
  zusätzlich `:app:assembleRelease` (R8 + Ressourcen-Shrinking) grün.
- **APK-Größenbudget:** Phase-3-Gate ~10,4 MB → mit allen 18 OGGs
  11.841.714 B (≈ +1,4 MB, im Erwartungskorridor +1–1,5 MB, weit unter
  der +3-MB-Eskalationsschwelle). OGG-Anteil im APK: 1.351.809 B, alle
  18 „Stored" (unkomprimiert — OGG ist vorkomprimiert, aapt packt raw
  nicht erneut). Nach Demo-Entfernung: Gate-APK 11.173.577 B
  (≈ 10,66 MiB), OGG-Anteil 684.632 B (17 Assets).
- **Demo-Asset-Entscheidung:** `music_demo_steigerung.ogg` (667.177 B ≈
  652 KiB) ENTFERNT — reines Anhör-Demo, von keinem Code referenziert
  (AudioEngine nutzt 17 Assets); Quelle bleibt über
  `tools/audio/synth.py` reproduzierbar (Orchestrator-Empfehlung).
- **keep.xml: ENTFERNT (obsolet).** Beweis per Release-Build-Analyse
  ohne keep.xml: `app/build/outputs/mapping/release/resources.txt`
  markiert alle 17 raw-Einträge „reachable: referenced from …dex"
  (R.raw-Referenzen in AndroidAudioAdapters.kt, inkl. des noch
  unverdrahteten sfx_ui_tap — SoundPool lädt es); Release-APK enthält
  17/17 OGGs (684.632 B). Der PW-4.0-Zweck (Schutz VOR der
  Code-Referenzierung) ist entfallen.
- **Version:** versionName 0.1.0 → 0.4.0 (SemVer minor), versionCode
  1 → 2.
- **Gate-Artefakt:** `app/build/outputs/apk/debug/app-debug.apk`,
  11.173.577 B, SHA-256
  `ac2384678b61cd604a483861c296d197f7971261115783f0c8c50beed5efb959`,
  gebaut aus sauberem Working Tree auf Branch-HEAD (Tree-Hash
  `26b6a289df2b5dd2417852fe17905801f1741e4f`, verifiziert via
  `git rev-parse HEAD^{tree}`). KEIN Signing, KEIN Tag, KEIN Upload —
  Tag erst nach Freigabe.
- **Checkliste:** docs/phase4-gate-checklist.md — Spieltest (VFX,
  Audio, Reduce-Motion), Abweichungen D1–D10 einzeln zur Entscheidung,
  Produktfrage Portrait-Lock vs. configChanges vs. Session-Erhalt.

### Aufgaben für den nächsten Agenten

Nächste Instanz ist das MENSCHLICHE GATE (Branko):

- Spieltest nach `docs/phase4-gate-checklist.md` auf dem Gerät
  (Artefakt-Pfad + SHA-256 oben; Installation per `adb install -r`).
- Entscheidungen: Abnahme-Deltas D1–D10 EINZELN (OK bleibt /
  Nacharbeiten-Ticket) und die Produktfrage in Abschnitt 6
  (Portrait-Lock vs. configChanges vs. Session-Erhalt).
- Nach der Abnahme: Orchestrator priorisiert den Backlog für Phase 5
  (u. a. Settings-Screen mit echten musicEnabled/sfxEnabled-Schaltern,
  Folge-Tickets aus den Delta-Entscheidungen).
