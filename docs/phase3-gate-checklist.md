# Phase-3-Gate — manuelle Checkliste (menschliches Gate)

> Prüfer: Branko. Grundlage: docs/phase3-tickets.md (PW-3.7),
> docs/game-design.md §12/§13. Kriterium des Gates: APK auf dem Gerät
> installierbar, EIN Level von Anfang bis Ende spielbar.
> Automatisierter Nachweis liegt bei: E2E-Smoke
> `app/src/test/kotlin/de/puzzlewerk/app/E2eSmokeTest.kt`
> (läuft in `./gradlew :app:testDebugUnitTest`).

## 1. Installation

Artefakt (Debug-Build, aus `./gradlew :app:assembleDebug`):

```
app/build/outputs/apk/debug/app-debug.apk
```

- [ ] APK aufs Gerät bringen — per Kabel:
      `adb install -r app/build/outputs/apk/debug/app-debug.apk`
      (oder Datei aufs Gerät kopieren und dort öffnen;
      „Unbekannte Quellen" für den Installer einmalig erlauben)
- [ ] App startet ohne Absturz, Home-Screen erscheint
      (dunkles Theme, Titel „Puzzlewerk", Buttons Weiter /
      Tägliches Prisma „bald" / Levelauswahl / Einstellungen)

## 2. Ein Level von Anfang bis Ende (§12.2–§12.3)

- [ ] Home → „Levelauswahl": Grid mit 50 Kacheln; anfangs sind
      genau Level 1–3 spielbar, der Rest gesperrt (§11.2)
- [ ] Kachel „Level 1" → Spiel-Screen lädt (kurzer Ladezustand
      ist ok, §9.4); Kopfzeile zeigt „Züge 0 · Par …"
- [ ] Tap auf ein drehbares Element (heller Sockelring):
      dreht eine Stufe (~150 ms animiert), Zugzähler +1,
      Strahlen aktualisieren sich SOFORT
- [ ] Tap auf Leeres/nicht Drehbares: kurzes Wackeln, KEIN Zug
- [ ] „Zug zurücknehmen" nimmt den letzten Zug zurück
- [ ] Level lösen → Ergebnis-Overlay: „Gelöst!", Sterne
      (Glyphen UND Text „x von 3 Sternen"), Punkte, „Züge X · Par Y"
- [ ] Overlay „Weiter" → Level 2 lädt als FRISCHE Partie
      (Züge 0, kein Overlay)
- [ ] Zurück zur Levelauswahl: Level 1 zeigt Sterne + Score,
      Level 4 ist jetzt zusätzlich freigeschaltet (gelöst+3, §11.2)
- [ ] System-Zurück: Spiel → Levelauswahl → Home → App verlassen
      (kein Hängen, kein Absturz)

## 3. TalkBack-Stichprobe (§13.5)

TalkBack einschalten (Einstellungen → Bedienungshilfen → TalkBack).

- [ ] Zellen des Spielfelds sind einzeln fokussierbar und werden
      nach dem Muster angesagt, z. B. „Spiegel, drehbar,
      Orientierung 2 von 6, Reihe 0, Spalte 1" bzw.
      „Kristall, benötigt Gelb (Rot und Grün), empfängt Rot, …"
- [ ] Doppeltipp auf eine drehbare Zelle führt den Zug aus
      (Zugzähler erhöht sich, Ansage der neuen Orientierung
      beim erneuten Fokussieren)
- [ ] Buttons (Weiter, Levelauswahl, Undo, Reset, Overlay-Aktionen)
      haben sprechende Ansagen; das Ergebnis-Overlay wird als
      zusammenhängender Block vorgelesen (Sterne als Text, nicht
      als Glyphen)

## 4. Spielgefühl-Feedback (Freitext)

Bitte kurz notieren — fließt in den Art-Direction-/Feinschliff-Pass
(Phase 4) ein:

- Drehen (Ansprechverhalten, Animationstempo ~150 ms): …
- Lesbarkeit Brett/Strahlen/Symbole (auch bei Sonnenlicht): …
- Wackel-Feedback bei ungültigem Tap (zu stark/zu schwach): …
- Overlay/Übergang zum nächsten Level: …
- Sonstiges (was nervt, was fehlt): …

## Ergebnis

- [ ] Gate BESTANDEN (alle Pflichtpunkte 1–3 abgehakt)
- Datum / Gerät / Android-Version: …
