# Handover-Kette Phase 4

> Neues Prozesselement ab PW-4.8: Jeder Agent hängt am Ticket-Ende einen
> Abschnitt an mit (a) Kontext (was er gebaut hat, mit Dateien und
> Stolpersteinen) und (b) Aufgaben des nächsten Agenten. Neueste Einträge
> unten anfügen, Format wie unten.

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
  zwischen zwei `pumpOnce()`-Aufrufen; danach Regain und prüfen, dass
  der Fade an der eingefrorenen Cursor-Position fortsetzt (Fade-Position
  lebt im Kern, Cursor in `StemMixerCore.cursor`).
- Robolectric-Smoke der Android-Adapter (ADR-010-Folgearbeit):
  mindestens Konstruktion + `release()` von SoundPoolSfxPlayer /
  audioTrackSinkOrNull / AudioManagerFocusRequester; MediaCodec ist
  unter Robolectric nicht real dekodierbar — nur Fehlerpfad (`null`)
  prüfbar.
- Migration-Kanten :data: SettingsSchema v1→v2 (Nicht-Objekt-Payload,
  soundEnabled mit Nicht-Boolean-Wert ⇒ beide AN, unbekannte
  v1-Felder ⇒ Korruptions-Rückfall) — Basisfälle liegen in
  DataStoreSettingsRepositoryTest.
