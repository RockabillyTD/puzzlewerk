# Phase 4 — „Juice-Update": Gate-Feedback Branko (Phase-3-Abnahme)

> Menschliches Gate-Ergebnis: Kampagnenpfad funktioniert, aber das Spiel
> fühlt sich zu langweilig an. Zielrichtung: Zuma-artige Effektdichte —
> Farbe, Explosionen, Laserstrahlen, steigernder Sound. Dieses Dokument
> übersetzt das Feedback in Design-Vorgaben + Tickets. Es ändert NICHTS
> an der Spiellogik (:game bleibt unberührt) — Juice ist eine reine
> Präsentationsschicht in :app.

## 0. Gelieferte Audio-Assets (fertig, lizenzfrei, selbst-synthetisiert)

Ordner `res-raw/` → nach `app/src/main/res/raw/` kopieren. Alle OGG
(Vorbis q4), 44,1 kHz, deterministisch generiert (Generator-Skript
`synth.py` kann ins Repo unter `tools/audio/` übernommen werden —
Assets sind damit reproduzierbar, Regel „gleicher Commit → gleiches
Artefakt" gilt auch für Sound).

**Musik — adaptiv in 4 Stems (alle exakt 17,14 s, 112 BPM, 8 Takte,
d-Moll-Pentatonik, verlustfrei übereinanderlegbar):**

| Datei | Ebene | Charakter |
|---|---|---|
| music_stem1_urig | Basis | Rahmentrommeln, Shaker, tiefer Drone — das „Urige" |
| music_stem2_kalimba | +1 | Kalimba-Motiv (Karplus-Strong), Holz-Perkussion |
| music_stem3_bass | +2 | Kick 4-on-the-floor + Synth-Bass — Antrieb |
| music_stem4_modern | +3 | 16tel-Synth-Arpeggio + Pad — das „Moderne" |
| music_demo_steigerung | Demo | 68 s: Stems treten nacheinander ein (zum Anhören) |

**Adaptive Logik (Ticket PW-4.2):** Stems laufen synchron in Dauerschleife;
Lautstärke der Ebenen folgt dem Spielfortschritt im Level — z. B.
Ebene 2 ab erstem gelittenen Kristall, Ebene 3 ab 50 % Kristallen,
Ebene 4 sobald nur noch 1 Kristall fehlt. Beim Lösen: alle Ebenen kurz
ducken → sfx_solve_explosion. Steigerung entsteht damit ohne einen
einzigen harten Musikwechsel.

**SFX (12):** sfx_rotate_tick (Drehung), sfx_rotate_invalid (Wackeln),
sfx_beam_connect (Strahl erreicht Ziel), sfx_crystal_lit (Kristall an),
sfx_combo_up1..3 (mehrere Kristalle in einem Zug, aufsteigend),
sfx_solve_explosion (Lösung: Boom + Funkenregen), sfx_star_1..3
(Sterne-Einblendung, aufsteigende Glocken), sfx_laser_loop
(loopbarer Strahl-Hum, leise), sfx_ui_tap.

## 1. Design-Vorgaben VFX (Addendum zu §13 game-design.md — BREAKING

   für den bisherigen „ruhigen" Stil, von Branko am Gate angeordnet)

**V1 — Strahlen sind Laser.** Kern hell (weiß), Halo in Strahlfarbe
(additiv), leichtes Pulsieren (~2 Hz), Partikel-Funken an
Auftreffpunkten. Farbmischung bleibt physikalisch lesbar (R/G/B →
Sekundärfarben leuchten sichtbar „gemischt").

**V2 — Jede Aktion antwortet sichtbar.** Drehung: Element blitzt auf,
2–3 Funken. Kristall geht an: radialer Glow-Burst + 8–12 Partikel in
Kristallfarbe. Mehrere Kristalle in einem Zug: Bursts kaskadieren mit
40 ms Versatz (Combo-Gefühl) + sfx_combo_upN.

**V3 — Lösung ist ein Feuerwerk.** Screen-Flash (80 ms, additiv),
Partikel-Explosion vom letzten Element (60–120 Partikel, additiv,
Gravitation), Sterne fliegen einzeln mit Bounce ein (je sfx_star_N),
danach erst Overlay-Buttons (Overlay-Latenz ≤ 600 ms, R32 unberührt).

**V4 — Hochauflösend = prozedural.** KEINE vorgerenderten Sprite-PNGs:
Alle Effekte werden vektoriell/prozedural im Canvas gezeichnet
(auflösungsunabhängig auf jedem Display scharf, APK bleibt klein).
Ab API 33 optional AGSL-Shader für Glow (RuntimeShader), darunter
Fallback auf Radial-Gradients — gleiche Optik, andere Technik.

**V5 — Respekt-Grenzen bleiben.** Reduce-Motion (§13.6): Partikelzahl
→ 0, Flash → sanftes Fade, Animationszeiten wie bisher. Sound: eigener
Einstellungs-Schalter (Musik / Effekte getrennt), Default AN, respektiert
Audio-Focus und Stummschalter. Keine Vibration ohne Opt-in.

## 2. Technische Leitplanken (Architektur unverändert)

- :game bleibt unberührt — Juice hängt ausschließlich an bereits
  vorhandenen Ereignissen aus MoveResult/trace (Kristall-Delta,
  Combo-Größe, Gelöst). Falls ein Ereignis-Detail fehlt (z. B. „welche
  Kristalle NEU an"), liefert :game es als reines Datum nach (eigenes
  Mini-Ticket, mit Tests).
- Partikelsystem: ein `JuiceState` (immutable Snapshot je Frame) +
  `step(state, dt)` als pure Funktion in :app — damit unit-testbar
  (deterministisch mit SeededRandom). Zeichnen in EINEM Canvas-Layer,
  BlendMode.Plus; keine Allokationen im Draw-Pfad (Objekt-Pools,
  FloatArrays), Ziel 60 fps auf Mittelklasse-Gerät.
- Audio: SoundPool für SFX (alle beim App-Start vorgeladen),
  MediaPlayer/ExoPlayer-frei — 4 synchrone MediaPlayer für Stems sind
  fehleranfällig; stattdessen AudioTrack-Mixer ODER 1 MediaPlayer je
  Stem mit gemeinsamem Start + Volume-Fades (einfacher, für 17-s-Loops
  ausreichend; Architekt entscheidet per ADR). KEINE neue Dependency
  nötig — Framework-APIs reichen.

## 3. Ticket-Zuschnitt (Vorschlag für den Orchestrator)

| Ticket | Agent | Inhalt | Hinweis |
|---|---|---|---|
| PW-4.1 | game-designer | §13-Addendum (V1–V5) normativ einarbeiten, Randfälle ergänzen (Reduce-Motion, Combo-Kaskade) | BREAKING-Kennzeichnung, Abnahme Branko |
| PW-4.2 | architekt | ADR Audio-Architektur (Stem-Player vs. AudioTrack), ADR VFX-Layer (AGSL + Fallback), Ereignis-API-Bedarf an :game | vor Implementierung |
| PW-4.3 | entwickler | Falls nötig: Kristall-Delta/Combo-Größe als Datum in MoveResult (+Tests) | klein |
| PW-4.4 | ui-entwickler | Partikelsystem + Laser-Rendering (V1, V2) inkl. JuiceState-Unit-Tests + Reduce-Motion-Tests | größtes Ticket, ggf. splitten |
| PW-4.5 | ui-entwickler | Lösungs-Feuerwerk + Sterne-Choreografie (V3) | nach 4.4 |
| PW-4.6 | ui-entwickler | Audio-Engine: SoundPool-SFX + adaptive Stems, Settings-Schalter | Assets liegen vor |
| PW-4.7 | test-engineer | QS-Pass: Determinismus JuiceState, Reduce-Motion, Audio-Settings, Performance-Smoke (Frame-Budget-Messung) | unabhängig |
| PW-4.8 | release-engineer | APK-Größen-Budget prüfen (~+1 MB durch OGGs ok), Gate-Artefakt | Abschluss |

Empfohlene Reihenfolge: 4.1 → (Abnahme) → 4.2 → 4.3 → 4.4/4.6 parallel
(disjunkte Dateien) → 4.5 → 4.7 → 4.8 → menschliches Gate: erneuter
Spieltest durch Branko.

## 4. Startprompt für die Claude-Code-Session

„Phase-3-Gate-Feedback liegt vor: docs/phase4-juice-update.md
(dieses Dokument, ins Repo legen). Audio-Assets liegen unter
app/src/main/res/raw/ bereit. Starte Phase 4 gemäß Ticket-Zuschnitt
Abschnitt 3: zuerst PW-4.1 (game-designer, BREAKING-Addendum §13),
dann nach meiner Abnahme weiter."
