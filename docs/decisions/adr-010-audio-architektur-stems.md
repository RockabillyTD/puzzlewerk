# ADR-010: Audio-Architektur — AudioTrack-Mixer für die 4 Musik-Stems, SoundPool für SFX

- Status: AKZEPTIERT
- Datum: 2026-07-17
- Autor: architect (Ticket PW-4.2; Anlass: Juice-Addendum
  docs/game-design.md §13.11 + R46–R48, docs/phase4-juice-update.md §2)
- Bezug: ADR-004 (Schichtenmodell — Audio ist reine :app-Schicht),
  ADR-006 (manueller AppContainer: AudioEngine wird dort verdrahtet),
  ADR-002/C8 (keine neue Dependency)

## Kontext

Das abgenommene Juice-Addendum macht adaptives Audio normativ
(§13.11): 4 Musik-Stems (`music_stem1_urig` … `music_stem4_modern`,
je 17,14 s Loop, 112 BPM, OGG in `:app` res/raw) starten beim Betreten
des Spiel-Screens synchron, loopen endlos und werden ausschließlich
über ihre Lautstärke gesteuert (Fades 250 ms linear, Ducking beim
Lösen 50 ms → 20 %, 500 ms halten, 250 ms zurück).

Harte, normative Invarianten:

- **Synchronität:** „Die 4 Stems sind zu KEINEM Zeitpunkt gegeneinander
  verschoben" (§13.11) — ausdrücklich als Invariante markiert, die
  Player-Architektur hat sie zu garantieren.
- **R47:** Audio-Fokus-Verlust ⇒ alle 4 Stems pausieren GEMEINSAM,
  keine neuen SFX; bei Rückkehr gemeinsamer Wiedereinstieg — weiterhin
  ohne Versatz.
- **R48:** Lautlos-Modus wird nie umgangen; Schalter Musik/Soundeffekte
  AUS ⇒ zugehörige Player werden NICHT gestartet.
- **R46:** Stem-Lautstärken folgen dem aktuellen trace ohne Hysterese,
  auch abwärts.

Gesetzt (nicht Teil dieser Entscheidung): **SoundPool für die 13 SFX**,
alle beim App-Start vorgeladen; `sfx_laser_loop` läuft als
SoundPool-Loop mit 20 % Lautstärke. Keine neue Dependency (C8):
ExoPlayer/Media3 ist ausgeschlossen, es stehen nur Framework-APIs zur
Wahl. Zu entscheiden ist die Stem-Wiedergabe.

## Optionen

1. **4× MediaPlayer mit gemeinsamem Start + `setVolume`-Fades.**
   Wenig Code, aber die Synchronitäts-Invariante ist damit nicht
   garantierbar, nur approximierbar:
   - *Start-Versatz:* `start()` durchläuft je Player eine eigene
     asynchrone Pipeline; die Startlatenzen streuen geräteabhängig im
     einstelligen bis zweistelligen Millisekundenbereich. Es gibt keine
     API für einen sample-genauen Gruppenstart.
   - *Loop-Drift:* `setLooping(true)` ist nicht garantiert gapless;
     die Lücke am Loop-Punkt fällt pro Player/Decoder-Lauf verschieden
     aus. Der relative Versatz wächst damit potenziell alle 17,14 s —
     bei minutenlangen Partien hörbar (Phasing/Flanging zwischen den
     Ebenen, die musikalisch exakt übereinanderliegen müssen).
   - *Pause/Resume (R47):* jeder Fokus-Wechsel würfelt über vier
     unabhängige `pause()`/`start()`-Latenzen einen NEUEN Versatz.
   - *Keine Korrekturmöglichkeit:* `getCurrentPosition()`/`seekTo()`
     arbeiten in ms-Auflösung und codec-abhängig ungenau — eine
     Nachregelung würde selbst hörbare Sprünge erzeugen.
   - *Testbarkeit:* MediaPlayer ist auf der JVM nicht instanziierbar;
     Robolectric-Shadows modellieren weder Drift noch Fades. Die
     Fade-/Duck-Logik hinge an Timern — genau die Sorte flaky Tests,
     die ADR-009 vermeiden will.
2. **Eigener AudioTrack-Mixer** (Framework-APIs: MediaExtractor +
   MediaCodec zum einmaligen Dekodieren, EIN AudioTrack als Senke):
   - Beim Betreten des Spiel-Screens (bzw. vorab beim App-Start) werden
     die 4 OGGs einmalig zu PCM dekodiert (4 ShortArrays im Speicher).
   - Ein Mixer-Thread schreibt Blöcke (~20 ms) in EINEN AudioTrack:
     `out[n] = Σᵢ stemᵢ[(cursor+n) mod lenᵢ] · volᵢ(t) · duck(t)`.
     Loopen ist Index-Arithmetik — sample-exakt, für immer; alle vier
     Ebenen teilen denselben Cursor und dieselbe Clock.
   - Pause/Resume = `AudioTrack.pause()/play()` auf EINEM Stream —
     R47 ist strukturell erfüllt, ein Versatz ist konstruktionsbedingt
     unmöglich (es gibt nur einen Stream).
   - Lautstärke-Rampen (250 ms) und Duck-Envelope (50/500/250) werden
     pro Sample/Block linear interpoliert — klickfrei und exakt.
   - Aufwand: grob 300 Zeilen (Decoder ~80, purer Mixer-Kern ~80,
     Thread/Senke ~100, Fokus-Glue). Kein neuer Gradle-Eintrag.
3. **1× MediaPlayer mit vorgemischter Summendatei je Ebenen-Kombination.**
   Bei 4 Ebenen mit kontinuierlichen Fades und Ducking bräuchte es
   vorgerenderte Kombinationen bzw. Crossfades zwischen Dateien —
   kombinatorisch (bis zu 8 sinnvolle Zustände × Übergänge), Assets
   vervielfachen sich, weiche 250-ms-Fades zwischen Dateien landen
   wieder bei Mehrfach-Playern. Verfehlt §13.11 (kontinuierliche
   Lautstärkesteuerung je Ebene). Disqualifiziert.

## Entscheidung

**Option 2: eigener AudioTrack-Mixer für die 4 Stems.** SoundPool für
SFX bleibt wie gesetzt. Die Synchronitäts-Invariante aus §13.11 ist
normativ und nicht verhandelbar; Option 1 kann sie prinzipbedingt nur
annähern, Option 2 erfüllt sie durch Konstruktion (ein Stream, eine
Clock, Loop per Modulo). Dass docs/phase4-juice-update.md §2 Option 1
als „einfacher, für 17-s-Loops ausreichend" anbietet, wird bewusst
verworfen: „ausreichend" gilt dort nur für die Loop-Länge, nicht für
die (im Addendum später verschärfte) Nie-Versatz-Invariante und den
R47-Wiedereinstieg.

Verbindliche Eckpunkte der Umsetzung (PW-4.8, Audio-Engine-Punkt des
10-Punkte-Plans):

- **Schichtung für Testbarkeit:** Der Mixer-Kern (Mischformel, Rampen,
  Duck-Envelope, Loop-Cursor) ist eine pure, allokationsfreie Funktion
  über ShortArrays/Floats ohne jeden Android-Import — bit-exakt auf
  der JVM testbar. Android-gebunden sind nur zwei dünne Adapter:
  Decoder (MediaExtractor/MediaCodec → ShortArray) und PCM-Senke
  (AudioTrack). Beide stehen hinter internen Interfaces und werden im
  Test durch Fakes ersetzt.
- **AudioAttributes:** `USAGE_GAME` + `CONTENT_TYPE_MUSIC` für den
  Track, `CONTENT_TYPE_SONIFICATION` für SoundPool-SFX; niemals
  Alarm-/Ring-Streams (R48, Lautlos-Modus wird respektiert).
- **Audio-Fokus:** `AudioFocusRequest(AUDIOFOCUS_GAIN)` wird erst beim
  Start der STEMS angefordert — im SFX-only-Betrieb (`musicEnabled ==
  false`) wird KEIN Fokus angefordert, kurze SFX dürfen fremde Musik
  nicht verdrängen; jeder Verlust (auch transient, inkl. „Kopfhörer getrennt" via
  ACTION_AUDIO_BECOMING_NOISY) pausiert den Mixer-Track und unterdrückt
  neue SFX; Fokus-Rückkehr setzt den Track an exakt derselben
  Cursor-Position fort (R47). Kein eigenes Ducking fremder Apps.
- **Speicherbudget:** 4 Stems à 17,14 s dekodiert (44,1 kHz, 16 Bit,
  stereo) ≈ 12 MB Heap; bei Mono-Downmix ≈ 6 MB. PW-4.8 misst und darf
  auf Mono downmixen, WENN die Stems ohnehin mono-artig sind (Hörtest
  gegen music_demo_steigerung.ogg); die Entscheidung ist dort zu
  dokumentieren. 12 MB sind auf der minSdk-26-Geräteklasse vertretbar.
- **Fehlerfälle sind Werte, keine Exceptions (C3):** siehe
  AudioEngine-Vertrag unten. Audio ist nie der einzige Feedback-Kanal
  (§13/R48) — JEDER Audio-Fehler degradiert zu Stille plus Issue-Event,
  niemals zu Crash oder blockierter Spiellogik.

### Vertrag `AudioEngine` (:app)

Die Schnittstelle liegt als kompilierfähige Deklaration in
`app/src/main/kotlin/de/puzzlewerk/app/audio/AudioEngine.kt` (dieser
PR, ohne Implementierung; Sichtbarkeit `internal` — sie wird nur
innerhalb von :app konsumiert, C6). Kurzfassung des Vertrags:

- `enterGame(musicEnabled, sfxEnabled)` — Betreten des Spiel-Screens:
  fordert (nur bei aktiver Musik) den Audio-Fokus an und startet die
  4 Stems synchron ab Sample 0. `musicEnabled == false` ⇒
  Mixer/AudioTrack wird NICHT erzeugt, kein Fokus-Request (R48);
  `sfxEnabled == false` ⇒ SFX-Aufrufe sind No-ops.
- `exitGame()` — R49: Stems stoppen, laufende SFX dürfen ausklingen.
- `setStemMix(StemMix)` — Ziel-Lautstärken (0..1) je Ebene; die Engine
  fadet linear über 250 ms (§13.11). Werte kommen aus der puren
  Zuordnung Erfüllungsstand → Ebenen (Tabelle §13.11; implementiert
  und getestet in PW-4.8 als `StemMix.forProgress(fulfilled, total)`).
- `duckForSolve()` — startet die Duck-Envelope 50 ms → 20 %, 500 ms
  halten, 250 ms zurück; multipliziert sich mit den Ebenen-Lautstärken.
- `playSfx(SoundEffect)` — einer der 12 Einmal-Effekte (Enum);
  während Fokus-Verlust und bei `sfxEnabled == false` No-op.
- `setLaserLoopActive(Boolean)` — `sfx_laser_loop` (20 %) an/aus,
  solange ≥ 1 Strahlsegment sichtbar ist.
- `issues: Flow<AudioIssue>` — beobachtbare Fehler-/Zustandsereignisse:
  `FocusLost`, `FocusRegained`, `AssetUnavailable(resourceName)`
  (fehlende/korrupte Datei ⇒ betroffene Quelle bleibt stumm, Rest
  spielt weiter), `EngineUnavailable(stage)` (AudioTrack-/Decoder-
  Initialisierung fehlgeschlagen ⇒ Musik für diese Session aus).
- `release()` — gibt alle Audio-Ressourcen frei (App-Ende/Container).

**Fake für Tests:** `FakeAudioEngine` (Test-Quellset :app, geliefert
mit PW-4.8, genutzt dort und im QS-Pass PW-4.9) implementiert das Interface rein
aufzeichnend: Listen der `playSfx`-Aufrufe, Historie der `StemMix`-
Sollwerte, Duck-Zähler, aktueller Laser-Loop-Zustand, manuell
emittierbare `issues`. Damit sind ViewModel-/Choreografie-Tests
(SFX-Ketten R45, Stem-Folgen R46/R50, Fokus-Verhalten R47) ohne
Robolectric-Audio möglich.

## Konsequenzen

- (+) Die normative Sync-Invariante (§13.11) und R47 sind durch
  Konstruktion erfüllt statt durch Hoffnung auf Geräteverhalten;
  es gibt keinen Drift-Korrektur-Code, weil es keinen Drift gibt.
- (+) Fades/Ducking sind sample-genau und als pure Funktion bit-exakt
  testbar; die Testoberfläche passt zum bestehenden Fake-Ansatz
  (ADR-006/009).
- (+) Keine neue Dependency (C8); nur Framework-APIs ab API 26.
- (−) ~300 Zeilen Eigenbau inkl. Decoder-Adapter — mehr Erstaufwand
  als Option 1; Pflege liegt bei uns. Bewusst akzeptiert: der Kern ist
  klein, pur und golden-testbar.
- (−) ≈ 12 MB (stereo) bzw. ≈ 6 MB (mono) Heap für dekodiertes PCM,
  dauerhaft während des Spiel-Screens; Messung und ggf. Mono-Downmix
  in PW-4.8.
- (−) MediaCodec-Dekodierung ist gerätevariabel ⇒ Dekodier-Fehler sind
  als `AssetUnavailable`/`EngineUnavailable` erstklassige, getestete
  Pfade (nie Crash).
- Folgearbeit: PW-4.8 implementiert Engine + Fake + `StemMix.forProgress`
  + Settings-Schalter (Migration §13.11); PW-4.9 testet R45–R48/R50
  gegen den Fake und einen Robolectric-Smoke der Adapter.
  (Ticket-Nummern: 10-Punkte-Plan docs/phase4-10-punkte-plan.md — das
  ältere Schema aus docs/phase4-juice-update.md §3 gilt nicht mehr.)
## Addendum (2026-07-21, PW-4.8-Review): Vertragsmethode `setHostVisible`

Der `AudioEngine`-Vertrag wird um `setHostVisible(visible: Boolean)`
ergänzt, verdrahtet als Lebenszyklus-Glue in `MainActivity.onStart` /
`onStop`. Begründung: Ein Home-Press entzieht den Audio-Fokus nicht
zwingend (kein anderer Player übernimmt) — ohne die Methode liefe die
Stem-Musik im Hintergrund weiter, gegen den Geist von R48 („Respekt-
Grenzen", §13.11). Semantik: `false` pausiert den Mixer-Track auf dem
R47-Pfad (gemeinsame Pause an derselben Cursor-Position), `true` setzt
versatzfrei fort, sofern der Fokus nicht verloren ist; ohne laufende
Stems ein No-op. Abgesegnet durch Review + Orchestrator (Korrekturrunde
PW-4.8); die Architektur-Entscheidung dieses ADRs bleibt unverändert.
