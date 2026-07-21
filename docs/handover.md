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
  size/alpha/color), Vollbild-Flash als additives Weiß-Rect. KEINE Allokation im
  Draw-Pfad (nur Value-Klassen). Halo-Puls: Grundalpha × `haloPulseFactor` aus
  dem Snapshot.
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
- Der dt-Clamp bleibt im Treiber (nicht in den Stepper verschieben);
  Emitter-/Spawn-Logik im Stepper nur konsumieren.
