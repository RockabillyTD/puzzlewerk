# Phase-4-Gate — manuelle Checkliste (menschliches Gate)

> Prüfer: Branko. Grundlage: docs/phase4-juice-update.md,
> docs/game-design.md §13.7–§13.13 / §15 (R44–R50),
> ADR-010 (Audio), ADR-011 (Canvas-VFX).
> Kriterium des Gates: Das „Juice-Update" fühlt sich auf dem Gerät
> richtig an (Richtung „Zuma", Phase-3-Feedback) UND die unten
> gelisteten Abweichungen sind einzeln entschieden.
> Automatisierter Nachweis liegt bei: QS-Pass PW-4.9 (PASS, 31 Tests,
> step() median 54 µs / p95 83 µs auf JVM) + volle Gate-Kette grün.

## 1. Installation

Artefakt (Debug-Build, `./gradlew :app:assembleDebug`, versionName 0.4.0):

```
app/build/outputs/apk/debug/app-debug.apk
SHA-256: ac2384678b61cd604a483861c296d197f7971261115783f0c8c50beed5efb959
```

- [ ] APK aufs Gerät: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] App startet ohne Absturz; Einstellungen → App-Info zeigt Version 0.4.0

## 2. Spieltest — visuelle Effekte (Ton an, Animationen an)

- [ ] **Laser-Look (§13.7):** Strahlen haben hellen Kern + farbigen
      Halo und pulsieren sichtbar (langsames Atmen, kein Flackern);
      Lesbarkeit der Farben bleibt erhalten
- [ ] **Dreh-Blitz (§13.9):** Tap auf drehbares Element → kurzer
      weißer Blitz auf dem Element (~120 ms) + 3 kleine Funken
- [ ] **Kristall-Glow (§13.9):** Kristall wird erfüllt → Glow-Burst
      am Kristall (aufblühender Kreis, ~250 ms) + Partikel
- [ ] **Combo-Kaskade (§13.9):** Ein Zug erfüllt MEHRERE Kristalle →
      Glows/Bursts zeitlich gestaffelt (Kaskade, 40-ms-Versatz),
      SFX-Kette steigert sich hörbar (lit → up1 → up2 → up3)
- [ ] **Lösungs-Feuerwerk (§13.10):** Level lösen → Feuerwerk-Partikel
      + VOLLBILD-Flash (ganzer Screen, nicht nur das Brett, ~80 ms)
- [ ] **Sterne-Einflug (§13.10):** Sterne fliegen im Ergebnis-Overlay
      einzeln mit Bounce ein (Überschwingen), je Stern ein
      aufsteigender Klang (sfx_star_1..3); Overlay-Buttons erscheinen
      per Fade und sind danach bedienbar

## 3. Spieltest — Audio (§13.11, R46–R50)

- [ ] **Musik-Steigerung im Level:** Musik startet ruhig (Ebene 1);
      mit wachsendem Fortschritt (erfüllte Kristalle) kommen hörbar
      Ebenen dazu (Kalimba → Bass → Modern, bis Ebene 4); bei Undo
      wird die Musik wieder dünner (R46 abwärts); Übergänge als
      Fade, ohne Knacken; Stems bleiben synchron
- [ ] **Duck beim Lösen (R50):** Beim Lösen duckt die Musik kurz weg
      (solve_explosion tritt hervor) und kommt weich zurück
- [ ] **SFX-Stichprobe:** rotate_tick (Drehung), rotate_invalid
      (Wackel-Tap), beam_connect (Strahl trifft neu, ohne Erfüllung),
      laser_loop nur solange Strahlen liegen
- [ ] **Fokus-Verlust (R47):** Home-Taste während Musik → Ton stoppt;
      zurück in die App → Musik läuft an alter Stelle weiter
- [ ] **Settings-Schalter (R48):** HINWEIS — es gibt noch KEINEN
      Settings-Screen; musicEnabled/sfxEnabled sind In-Memory-Defaults
      (beide AN, FakeSettingsRepository bis Punkt 9/Phase 5). Am Gerät
      nur prüfbar: Ton ist überhaupt an. Schalter-Matrix ist per Test
      abgedeckt (DefaultAudioEngineQsTest)

## 4. Reduce-Motion-Gegenprobe (§13.12, R44)

System-Einstellung „Animationen entfernen/reduzieren" aktivieren
(Bedienungshilfen), dann dieselben Aktionen wiederholen:

- [ ] Keine Partikel, kein Glow-Burst, kein Dreh-Blitz, kein Wackeln;
      Laser-Puls steht statisch
- [ ] Lösung: statt hartem Blitz ein sanfter Dreiecks-Fade;
      Sterne erscheinen per Fade statt Bounce — zu den GLEICHEN
      Zeitpunkten, Overlay-Frist unverändert
- [ ] Audio ist von Reduce-Motion UNBERÜHRT (Musik, SFX, Sterne-Klänge
      kommen weiterhin, §13.12)
- [ ] Umschalten MITTEN in der Partie crasht nicht; laufende Partikel
      laufen natürlich aus

## 5. Abnahmepflichtige Abweichungen (einzeln entscheiden)

Jede Zeile bitte mit **OK (bleibt)** oder **NACHARBEITEN (Ticket)**
markieren. Quellen: Handover-Abschnitte PW-4.5–PW-4.9 in docs/handover.md.

- [ ] **D1 — Halo-/Glow-Treppe statt Verlauf (PW-4.5/4.6):**
      Laser-Halo und Kristall-Glow sind 3-Stufen-Kreis-Treppen
      (Canvas-Näherung) statt echter Radial-Verläufe — frische
      Gradient-Brushes je Frame würden den allokationsfreien
      Draw-Pfad (ADR-011) verletzen. Sichtbar ggf. als leichte
      Stufung an den Rändern.
- [ ] **D2 — SFX ohne 40-ms-Kaskadenversatz (PW-4.6/4.7):**
      Die Kombo-SFX-Kette feuert komplett beim Zug-Commit, nicht im
      40-ms-Raster der visuellen Kaskade; solve_explosion kommt beim
      Zug-Commit statt zum Feuerwerk-Start t_fw.
- [ ] **D3 — Stem-Neustart bei Rotation/Recreation (PW-4.6-Korrektur):**
      Nach Bildschirmdrehung/Recreation startet die Audio-Session neu
      (exit+enter, Stems ab Sample 0) — Session-Erhalt wäre ein
      eigenes Ticket. Siehe auch Produktfrage in Abschnitt 6.
- [ ] **D4 — Stern-Bounce endet bei 800 ms > 600-ms-Frist:**
      Im Design kodifizierte, am 2026-07-16 abgenommene V3-Abweichung
      (§13.10) — hier nur GEGENPROBE am Gerät: fühlt es sich gut an?
- [ ] **D5 — Reduce-Motion-Konstantenwahl (PW-4.7):** §13.12 lässt die
      Kurven offen; gewählt: Flash-Dreieck Peak 0,15 über 400 ms,
      Sterne-Fade 150 ms. Gefühlsprüfung unter Reduce-Motion.
- [ ] **D6 — Recreation wiederholt Sterne-Choreo + Blitz (PW-4.9
      IST-Pin):** Drehen/Recreation bei offenem Ergebnis-Overlay
      spielt die Stern-Choreografie ERNEUT ab (Reihenfolge 1-2-3,
      kein Crash, seit PW-4.9-FIX ohne SFX-Doppelung).
- [ ] **D7 — Bounce-Neustart bei RM-Toggle (PW-4.9/FIX):**
      System-Reduce-Motion-Umschaltung bei offenem Overlay startet den
      Stern-EINFLUG visuell neu (Kurvenwechsel); der Stern-SFX kommt
      seit PW-4.9-FIX garantiert nur EINMAL.
- [ ] **D8 — RM-Umschaltung mitten im Flash (PW-4.9 IST-Pin):**
      Wechsel während des 80-ms-Blitzes springt hart auf die
      Dreieckskurve (Alpha 0,175 → 0,03) — definiert, crashfrei;
      §13 regelt die Kante nicht.
- [ ] **D9 — §13.12-Aura-Fade nicht animiert (PW-4.6/4.7):** Unter
      Reduce-Motion ersetzt KEIN 250-ms-Fade der Kristall-Leuchtaura
      den Glow — der Erfüllungswechsel schaltet hart um.
- [ ] **D10 — sfx_ui_tap unverdrahtet (PW-4.6/4.7):** Der UI-Tap-Klang
      liegt als Asset bereit und wird geladen, ist aber an keinen
      Button/Overlay-Tap angeschlossen.

Bereits behoben (keine Entscheidung nötig, nur Kenntnisnahme):
BUG-PW4.9-1 (Fokus-Callback nach exitGame schaltete Menü-SFX stumm) und
BUG-PW4.9-2 (RM-Toggle doppelte den Stern-SFX) — gefixt in PR #40,
Repro-Tests scharf.

## 6. Produktfrage (Entscheidung Branko/game-designer)

- [ ] **Bildschirmdrehung:** Aktuell startet bei Rotation die
      Activity-Recreation die Audio-Session neu (D3) und wiederholt
      die Sterne-Choreo (D6). Optionen:
      **(a) Portrait-Lock** (Manifest, einfachste Lösung — Puzzle-Grid
      ist portrait-orientiert),
      **(b) configChanges selbst behandeln** (kein Recreate, gegen
      Android-Empfehlung),
      **(c) Session-Erhalt bauen** (Audio-/Choreo-Zustand
      recreation-fest machen, eigenes Ticket, größter Aufwand).
      Entscheidung: …

## 7. Spielgefühl-Feedback (Freitext)

Referenz ist das Phase-3-Feedback („zu langweilig, Richtung Zuma"):

- Trifft das Juice-Update die Richtung? Was fehlt noch: …
- Laser/Puls (zu ruhig/zu nervös): …
- Partikel-/Feuerwerksmenge (zu wenig/zu viel): …
- Musik-Steigerung (Dramaturgie, Lautstärke-Balance Musik/SFX): …
- Sonstiges: …

## Ergebnis

- [ ] Gate BESTANDEN (Abschnitte 1–4 abgehakt, D1–D10 + Produktfrage
      entschieden)
- Datum / Gerät / Android-Version: …

Nach der Abnahme: Orchestrator priorisiert den Backlog für Phase 5
(u. a. Settings-Screen mit echten Schaltern, Entscheidungs-Folgen aus
D1–D10/Produktfrage).
