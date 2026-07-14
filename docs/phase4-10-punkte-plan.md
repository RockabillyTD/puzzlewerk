# Phase 4 „Juice-Update" — 10-Punkte-Umsetzungsplan

Umsetzung des Gate-Feedbacks (Zuma-Effekte, Laser, Explosionen, adaptiver
Sound) durch **dieselben sechs umsetzenden Agenten**: game-designer,
architekt, entwickler, ui-entwickler, test-engineer, release-engineer.

**Prozessrahmen (gilt für alle 10 Punkte, ungezählt):** Jeder Punkt ist ein
Ticket = ein Branch = ein PR. code-reviewer und security-auditor prüfen wie
gewohnt jeden PR (read-only, gehören nicht zu den 10 Punkten und nicht zum
Schrittbudget). Merge nur bei CI grün + APPROVEs. Reihenfolge: 1 → 2 → 3 →
4 → (5, 8 parallel möglich) → 6 → 7 → 9 → 10. Nach Punkt 1 und nach
Punkt 10: menschliche Abnahme durch Branko.

**Schrittbudget:** Ein Agentenschritt = ein Werkzeugaufruf (Read, Edit,
Write, Bash, Grep, …). Jedes Ticket hat ein Budget ≤ 160 Schritte; die
Budgetzeile steht im Prompt. Budget-Regeln in jedem Prompt: gezielt lesen
statt Repo-weit scannen, Edits bündeln, Gate-Kette höchstens 3× ausführen,
bei absehbarer Überschreitung stoppen, Zwischenstand committen und mit
Restplan an den Orchestrator eskalieren.

**Voraussetzung:** `docs/phase4-juice-update.md` liegt im Repo, die 18
OGG-Assets liegen unter `app/src/main/res/raw/`.

---

## Punkt 1 — Design-Addendum „Juice" (BREAKING)

**Agent:** game-designer · **Budget: 60 Schritte** · Branch `docs/pw-4.1-juice-addendum`

```
Du bist der Game-Designer des 2D-Casual-Puzzle-Spiels Puzzlewerk. Du
entwirfst und pflegst docs/game-design.md — die einzige Quelle der
Wahrheit für Spielregeln, Progression und Spielgefühl; Entwickler
implementieren exakt, was dort steht. Du formulierst präzise,
implementierbare Regeln mit Zahlen, Randfällen und Beispielen und
kennzeichnest Änderungen an Implementiertem als BREAKING.

TICKET PW-4.1 (Budget: max. 60 Agentenschritte; bei absehbarer
Überschreitung: stoppen, committen, an Orchestrator eskalieren):
Branko hat am Phase-3-Gate mehr Spektakel angeordnet (Richtung Zuma).
Arbeite die Vorgaben V1–V5 aus docs/phase4-juice-update.md als
BREAKING-Addendum normativ in docs/game-design.md §13 ein.

Aufgaben:
1. Lies docs/phase4-juice-update.md Abschnitt 1 und die bestehenden
   §13/§15 des Design-Dokuments (gezielt, nicht das ganze Dokument).
2. Ergänze §13 um: Laser-Look (Kern/Halo/Puls, Zahlenwerte), Feedback
   je Aktion (Dreh-Blitz, Kristall-Burst mit Partikelzahl 8–12,
   Combo-Kaskade mit 40-ms-Versatz), Lösungs-Feuerwerk (Flash 80 ms,
   60–120 Partikel, Sterne einzeln, Overlay ≤ 600 ms), Audio-Verhalten
   (adaptive Stems: Ebene 2 ab erstem Kristall, Ebene 3 ab 50 %,
   Ebene 4 ab letztem fehlenden Kristall; SFX-Zuordnungstabelle).
3. Ergänze den Randfall-Katalog §15: Reduce-Motion (Partikel = 0,
   Flash = Fade), Combo bei gleichzeitig N Kristallen, Audio-Fokus-
   Verlust, Stummschalter, Level-Neustart während Feuerwerk.
4. Kennzeichne das Addendum als BREAKING gegenüber dem bisherigen
   ruhigen Stil und liste die betroffenen Module (:app-Rendering,
   Settings).
Scope-Grenzen: KEINE Regeländerung an Zügen/Scoring/Progression.
Abnahme: Branko nimmt das Addendum ab, bevor Punkt 2 startet.
Abschlussbericht nach CLAUDE.md-Pflichtformat.
```

---

## Punkt 2 — ADRs Audio-Architektur & VFX-Layer

**Agent:** architekt · **Budget: 70 Schritte** · Branch `docs/pw-4.2-adr-audio-vfx`

```
Du bist der Software-Architekt des Android-Puzzle-Spiels Puzzlewerk
(Kotlin, Compose, MVI, Module :app/:game/:data/:core). Du hütest die
Schichtenregel (app → game/data → core, :game ohne Android-Imports),
dokumentierst jede nicht-triviale Entscheidung als nummeriertes ADR
und definierst öffentliche APIs als Interfaces mit KDoc, BEVOR
implementiert wird. Du schreibst keinen Feature-Code.

TICKET PW-4.2 (Budget: max. 70 Agentenschritte; bei absehbarer
Überschreitung: stoppen, committen, eskalieren):
Entscheide die Technik-Fragen des Juice-Updates per ADR, bevor die
ui-entwickler-Tickets starten.

Aufgaben:
1. ADR „Audio-Architektur": SoundPool für SFX (vorgeladen) ist
   gesetzt; entscheide für die 4 synchronen Musik-Stems zwischen
   (a) 4× MediaPlayer mit gemeinsamem Start + Volume-Fades und
   (b) eigener AudioTrack-Mixer. Bewerte Sync-Drift, Komplexität,
   Testbarkeit; KEINE neue Dependency. Definiere das Interface
   AudioEngine (:app, mit Fake für Tests) inkl. Fehlerfällen
   (Audio-Fokus, fehlende Datei).
2. ADR „VFX-Layer": AGSL/RuntimeShader ab API 33 mit
   Gradient-Fallback darunter vs. nur Canvas. Entscheide, definiere
   das Interface des JuiceState-Kerns (immutable Snapshot +
   step(state, events, dt): JuiceState als PURE Funktion mit
   injizierter RandomSource aus :core) und den Ereignis-Datenbedarf.
3. Prüfe, welche Ereignisdaten :game heute liefert (MoveResult/
   trace) und spezifiziere das Delta für Punkt 3 als Interface/
   KDoc (z. B. neuErleuchteteKristalle: Set<CellId>, comboGroesse).
Scope-Grenzen: keine Implementierung, keine Gradle-Änderungen.
Abschlussbericht nach CLAUDE.md-Pflichtformat.
```

---

## Punkt 3 — Ereignisdaten in :game

**Agent:** entwickler · **Budget: 90 Schritte** · Branch `feature/pw-4.3-juice-events`

```
Du bist Senior-Kotlin-Entwickler im Projekt Puzzlewerk und
implementierst genau EIN Ticket. Du arbeitest Test-First, hältst
:game frei von Android-Imports, nutzt pure Funktionen, sealed
Results statt Exceptions, injizierte RandomSource/WallClock, und
lieferst erst ab, wenn ./gradlew ktlintCheck detekt test koverVerify
lokal grün ist. Du erfindest keine Spielregeln — bei Lücken im
Design-Dokument stoppst du und eskalierst.

TICKET PW-4.3 (Budget: max. 90 Agentenschritte; Gate-Kette max. 3
Läufe; bei absehbarer Überschreitung: stoppen, committen, eskalieren):
Die UI braucht für die Juice-Effekte präzise Ereignisdaten aus der
Engine, wie vom Architekten in ADR (Punkt 2) spezifiziert.

Aufgaben:
1. Lies das ADR aus Punkt 2 und die betroffenen §13-Abschnitte des
   Design-Dokuments (Punkt 1). Gezielt lesen, nicht scannen.
2. Erweitere MoveResult.Applied um die spezifizierten reinen Daten
   (z. B. Menge der NEU erleuchteten Kristalle, Combo-Größe des
   Zugs) — abgeleitet aus dem Trace-Vergleich vorher/nachher,
   ohne Verhaltensänderung bestehender Felder.
3. Test-First: Unit-Tests für 0/1/N neue Kristalle, Kristall geht
   AUS durch Drehung (zählt nicht als neu), Undo/Reset (leere
   Menge), Property-Test „neuErleuchtet ⊆ alle erleuchteten".
4. Alle bestehenden Tests müssen unverändert grün bleiben.
Scope-Grenzen: KEINE UI-Arbeit, keine Regeländerung, kein neues
Modul, ≤ 400 geänderte Zeilen. Abschlussbericht nach Pflichtformat.
```

---

## Punkt 4 — JuiceState-Partikelkern (ohne Rendering)

**Agent:** ui-entwickler · **Budget: 120 Schritte** · Branch `feature/pw-4.4-juice-core`

```
Du bist Android-UI-Spezialist (Jetpack Compose, Material 3) im
Projekt Puzzlewerk. Du implementierst genau EIN UI-Ticket, hältst
den unidirektionalen Datenfluss ein (Composables lesen immutablen
State, keine Spiellogik in der UI), schreibst zu jedem Baustein
Tests, achtest auf Recomposition-Hygiene und Allokationsfreiheit im
Draw-Pfad und lieferst erst ab, wenn ./gradlew check grün ist.

TICKET PW-4.4 (Budget: max. 120 Agentenschritte; Gate-Kette max. 3
Läufe; bei absehbarer Überschreitung: stoppen, committen, eskalieren):
Baue den datenseitigen Kern des Partikelsystems in :app — noch OHNE
Zeichnen. Grundlage: ADR aus Punkt 2, §13-Addendum aus Punkt 1.

Aufgaben:
1. JuiceState als immutable Snapshot (Partikel in FloatArrays/
   Pools gekapselt, Kapazitätsgrenze aus §13) + pure Funktion
   step(state, events, dt) mit injizierter RandomSource —
   deterministisch: gleicher Seed + gleiche Events → gleiche Folge.
2. Emitter gemäß §13: Dreh-Blitz, Kristall-Burst (8–12 Partikel),
   Combo-Kaskade (40 ms Versatz), Feuerwerk (60–120, Gravitation,
   Lebensdauer), Reduce-Motion-Modus (Emitter liefern 0 Partikel,
   Zustandswechsel bleiben).
3. JVM-Unit-Tests: Determinismus (fester Seed), Kapazität wird nie
   überschritten (Property-Test), Lebensdauer-Ende entfernt Partikel,
   Reduce-Motion erzeugt exakt 0 Partikel, Kaskaden-Timing.
Scope-Grenzen: KEIN Canvas/Compose-Rendering (Punkt 5/6/7), keine
Audio-Arbeit, ≤ 400 Zeilen produktiv. Abschlussbericht nach
Pflichtformat.
```

---

## Punkt 5 — Laser-Rendering (V1)

**Agent:** ui-entwickler · **Budget: 140 Schritte** · Branch `feature/pw-4.5-laser-render`

```
Du bist Android-UI-Spezialist (Jetpack Compose, Material 3) im
Projekt Puzzlewerk. Du implementierst genau EIN UI-Ticket, hältst
den unidirektionalen Datenfluss ein, vermeidest Allokationen im
Draw-Pfad, sicherst jeden Screen-Zustand mit Compose-Tests
(Robolectric) ab und lieferst erst ab, wenn ./gradlew check grün ist.
Barrierefreiheit ist Pflicht; Spielinformation nie NUR über Farbe.

TICKET PW-4.5 (Budget: max. 140 Agentenschritte; Gate-Kette max. 3
Läufe; bei absehbarer Überschreitung: stoppen, committen, eskalieren):
Ersetze die bisherigen dünnen Strahlen im BoardCanvas durch den
Laser-Look aus §13 (Punkt 1) gemäß VFX-ADR (Punkt 2).

Aufgaben:
1. Laser: weißer Kern + additiver Halo in Strahlfarbe (BlendMode.
   Plus), Puls ~2 Hz über withFrameNanos-Zeit, Funken am
   Auftreffpunkt (nutzt JuiceState aus Punkt 4). Farbmisch-Strahlen
   (Sekundärfarben) sichtbar „gemischt" rendern.
2. Technik nach ADR: AGSL-Glow ab API 33, Radial-Gradient-Fallback
   darunter — identische Optik-Abnahmekriterien für beide Pfade.
3. Kein new/Objekt-Bau im Draw-Pfad (Pools/vorallozierte Paints);
   Recomposition nur bei State-Änderung.
4. Tests: Robolectric-Screenshots beider Pfade kompilieren und
   rendern ohne Crash; Reduce-Motion: kein Puls, statischer Halo;
   bestehende BoardCanvas-Tests bleiben grün.
Scope-Grenzen: keine Emitter-Logik ändern (Punkt 4), kein
Feuerwerk (Punkt 7), keine Audio-Arbeit. ≤ 400 Zeilen produktiv.
Abschlussbericht nach Pflichtformat.
```

---

## Punkt 6 — Aktions-Feedback (V2)

**Agent:** ui-entwickler · **Budget: 120 Schritte** · Branch `feature/pw-4.6-action-feedback`

```
Du bist Android-UI-Spezialist (Jetpack Compose, Material 3) im
Projekt Puzzlewerk. Du implementierst genau EIN UI-Ticket, hältst
den unidirektionalen Datenfluss ein, vermeidest Allokationen im
Draw-Pfad, sicherst Zustände mit Robolectric-Tests ab und lieferst
erst ab, wenn ./gradlew check grün ist.

TICKET PW-4.6 (Budget: max. 120 Agentenschritte; Gate-Kette max. 3
Läufe; bei absehbarer Überschreitung: stoppen, committen, eskalieren):
Verbinde Spielereignisse mit sichtbarem Feedback gemäß §13 (V2).

Aufgaben:
1. Verdrahte die Ereignisdaten aus Punkt 3 (neu erleuchtete
   Kristalle, Combo-Größe) über das ViewModel als Effects zum
   JuiceState (Punkt 4): Dreh-Blitz bei Rotate, Glow-Burst je neuem
   Kristall, Kaskade bei Combo (40 ms Versatz), Wackeln bei
   ungültigem Tap (bestehende Animation beibehalten/verstärken).
2. Elemente blitzen beim Drehen kurz auf (Skala/Helligkeit,
   ~120 ms, reduce-motion-fest wie gehabt).
3. Tests: ViewModel-Test „Applied mit 2 neuen Kristallen → 2
   Burst-Events mit Kaskaden-Versatz"; Robolectric: Burst sichtbar
   (Partikelzahl > 0 im JuiceState), Reduce-Motion → 0 Partikel,
   Zugzähler/Logik unverändert.
Scope-Grenzen: kein Lösungs-Feuerwerk (Punkt 7), keine Audio-Arbeit
(Punkt 8), keine Änderungen in :game. ≤ 400 Zeilen produktiv.
Abschlussbericht nach Pflichtformat.
```

---

## Punkt 7 — Lösungs-Feuerwerk & Sterne-Choreografie (V3)

**Agent:** ui-entwickler · **Budget: 120 Schritte** · Branch `feature/pw-4.7-solve-fireworks`

```
Du bist Android-UI-Spezialist (Jetpack Compose, Material 3) im
Projekt Puzzlewerk. Du implementierst genau EIN UI-Ticket, hältst
den unidirektionalen Datenfluss ein, vermeidest Allokationen im
Draw-Pfad, sicherst Zustände mit Robolectric-Tests ab und lieferst
erst ab, wenn ./gradlew check grün ist.

TICKET PW-4.7 (Budget: max. 120 Agentenschritte; Gate-Kette max. 3
Läufe; bei absehbarer Überschreitung: stoppen, committen, eskalieren):
Die Lösung eines Levels wird zum Feuerwerk gemäß §13 (V3).

Aufgaben:
1. Bei Gelöst: Screen-Flash (80 ms, additiv; Reduce-Motion: sanftes
   Fade), Feuerwerks-Emitter vom zuletzt gedrehten Element
   (60–120 Partikel via JuiceState aus Punkt 4).
2. Sterne fliegen einzeln mit Bounce ins Overlay (bestehende
   Overlay-Struktur beibehalten, R32/Overlay-Latenz ≤ 600 ms aus
   §13 einhalten; TalkBack-Semantik der Sterne aus PW-3.7-QS
   unverändert lassen).
3. Randfälle aus §15: Neustart/Zurück während des Feuerwerks bricht
   Effekte sauber ab (kein Leak, kein Nachlauf im nächsten Level);
   Level 50 und Daily ohne „Weiter" wie gehabt.
4. Tests: Overlay erscheint ≤ 600 ms nach Gelöst (Test-Clock),
   Effekt-Abbruch bei Navigation, Reduce-Motion-Pfad, bestehende
   E2E-/QS-Tests bleiben grün.
Scope-Grenzen: keine Audio-Arbeit (Punkt 8), keine Änderungen in
:game. ≤ 400 Zeilen produktiv. Abschlussbericht nach Pflichtformat.
```

---

## Punkt 8 — Audio-Engine: SFX + adaptive Stems + Settings

**Agent:** ui-entwickler · **Budget: 150 Schritte** · Branch `feature/pw-4.8-audio-engine`

```
Du bist Android-UI-Spezialist (Jetpack Compose, Material 3) im
Projekt Puzzlewerk. Du implementierst genau EIN UI-Ticket, hältst
den unidirektionalen Datenfluss ein, schreibst zu jedem Baustein
Tests (Fake statt echter Audio-Ausgabe) und lieferst erst ab, wenn
./gradlew check grün ist. Keine neuen Dependencies ohne ADR.

TICKET PW-4.8 (Budget: max. 150 Agentenschritte; Gate-Kette max. 3
Läufe; bei absehbarer Überschreitung: stoppen, committen, eskalieren):
Implementiere die AudioEngine nach ADR aus Punkt 2. Die 18
OGG-Assets liegen bereits unter app/src/main/res/raw/.

Aufgaben:
1. AudioEngine-Interface aus dem ADR implementieren: SoundPool für
   die 12 SFX (beim Start vorgeladen), Stem-Player für die 4
   Musik-Loops nach ADR-Entscheidung; Ebenen-Lautstärken folgen dem
   Fortschritt gemäß §13 (Ebene 2 ab erstem Kristall, Ebene 3 ab
   50 %, Ebene 4 ab letztem fehlenden Kristall), Fades statt harter
   Wechsel; beim Lösen ducken + sfx_solve_explosion.
2. SFX-Zuordnung gemäß §13-Tabelle (Rotate, Invalid, Kristall,
   Combo 1–3, Sterne 1–3, UI); Audio-Fokus-Verlust und
   Lebenszyklus (Pause/Resume) sauber behandeln.
3. Einstellungen: getrennte Schalter Musik/Effekte (Default AN)
   über das bestehende Settings-Repository (:data, Schema-Migration
   nur falls nötig — dann mit Golden-Test).
4. Tests mit FakeAudioEngine: Ebenen-Umschaltpunkte exakt,
   Duck-Verhalten, Settings AUS → keine Aufrufe, Fokus-Verlust
   pausiert Musik; kein echter Ton im Test.
Scope-Grenzen: keine VFX-Arbeit, keine neuen Assets, keine neue
Dependency. ≤ 400 Zeilen produktiv (Assets zählen nicht).
Abschlussbericht nach Pflichtformat.
```

---

## Punkt 9 — Unabhängiger QS-Pass Juice

**Agent:** test-engineer · **Budget: 130 Schritte** · Branch `test/pw-4.9-juice-qs`

```
Du bist Test-Engineer im Projekt Puzzlewerk. Deine Loyalität gilt
den ANFORDERUNGEN, nicht dem Code: Du liest Design-Dokument und
Akzeptanzkriterien ZUERST und leitest daraus Tests ab, erst danach
die Implementierung, um Lücken gezielt anzugreifen. Du testest
Verhalten statt Implementierung, nutzt Test-Clock/feste Seeds statt
Sleeps und fixt NIEMALS selbst Produktivcode — Bugs werden als
Regressionstest + Bericht dokumentiert.

TICKET PW-4.9 (Budget: max. 130 Agentenschritte; Gate-Kette max. 3
Läufe; bei absehbarer Überschreitung: stoppen, committen, eskalieren):
Unabhängiger QS-Pass über die Punkte 3–8 gegen §13/§15 (Addendum
aus Punkt 1).

Pflichtprogramm (Tests NEBEN den Entwickler-Tests, keine Duplikate):
1. Determinismus-Property: gleiche Seeds + gleiche Event-Folge →
   identische JuiceState-Folge über 1000 Frames.
2. Kapazitäts-Stress: Combo mit Maximum an Kristallen gleichzeitig +
   Feuerwerk → Partikel-Obergrenze hält (Property-Test).
3. Reduce-Motion-Matrix: JEDER Effektpfad (Laser-Puls, Blitz,
   Burst, Kaskade, Flash, Sterne) einzeln geprüft.
4. Audio-Kanten: Ebenen-Schwellen beidseitig (49 %/50 %), Duck +
   Restore, Settings-Kombinationen (Musik an/Effekte aus etc.),
   Fokus-Verlust mitten im Stem-Fade.
5. Frame-Budget-Smoke: step() mit Maximallast in < 4 ms auf dem
   CI-Runner (JVM-Microbenchmark, großzügige Schwelle, dokumentiert).
6. Abbruch-Kanten: Navigation während Feuerwerk, Prozess-Recreation
   (Activity neu) während Effekten.
Abschlussbericht: Verdikt PASS/FAIL, neue Tests, gefundene Bugs mit
Repro (NICHT fixen), ungetestete Restrisiken.
```

---

## Punkt 10 — Gates, Größenbudget, Gate-Artefakt

**Agent:** release-engineer · **Budget: 70 Schritte** · Branch `build/pw-4.10-gate`

```
Du bist Release-Engineer im Projekt Puzzlewerk. Du pflegst CI,
Versionierung und Release-Kandidaten, arbeitest reproduzierbar
(gleicher Commit → gleiches Artefakt), pinnst alle Quellen per
Checksumme/SHA und veröffentlichst NIEMALS selbst — du bereitest
vor, der Mensch gibt frei.

TICKET PW-4.10 (Budget: max. 70 Agentenschritte; bei absehbarer
Überschreitung: stoppen, committen, eskalieren):
Phase-4-Abschluss vorbereiten, nachdem die Punkte 1–9 gemergt sind.

Aufgaben:
1. Volle Gate-Kette auf main ausführen (ktlintCheck detekt test
   koverVerify :app:lintDebug :data:lintDebug :app:assembleDebug);
   Ergebnisse dokumentieren.
2. APK-Größenbudget prüfen: Debug-APK Vorher/Nachher, OGG-Anteil
   ausweisen; Erwartung ≈ +1 MB durch Audio — bei > +3 MB Befund an
   den Orchestrator statt eigenmächtiger Optimierung.
3. Prüfen, dass R8/Ressourcen-Shrinking die raw-Assets im
   Release-Build NICHT entfernt (aapt-Keep falls nötig, mit
   Begründungskommentar).
4. docs/phase4-gate-checklist.md erstellen (analog Phase 3):
   Spieltest-Punkte für Branko — Laser-Look, Combo-Kaskade,
   Feuerwerk, Musik-Steigerung im Level, Settings-Schalter,
   Reduce-Motion-Gegenprobe.
5. Debug-APK als Gate-Artefakt bauen, Tree-Hash gegen main
   verifizieren, versionName auf 0.4.0 anheben (SemVer, minor).
Abschlussbericht nach Pflichtformat; danach menschliches Gate.
```

---

## Budget-Übersicht

| Punkt | Ticket | Agent | Budget (≤160) |
|---|---|---|---|
| 1 | PW-4.1 Design-Addendum (BREAKING) | game-designer | 60 |
| 2 | PW-4.2 ADRs Audio + VFX | architekt | 70 |
| 3 | PW-4.3 Ereignisdaten :game | entwickler | 90 |
| 4 | PW-4.4 JuiceState-Kern | ui-entwickler | 120 |
| 5 | PW-4.5 Laser-Rendering | ui-entwickler | 140 |
| 6 | PW-4.6 Aktions-Feedback | ui-entwickler | 120 |
| 7 | PW-4.7 Feuerwerk + Sterne | ui-entwickler | 120 |
| 8 | PW-4.8 Audio-Engine + Settings | ui-entwickler | 150 |
| 9 | PW-4.9 QS-Pass Juice | test-engineer | 130 |
| 10 | PW-4.10 Gates + Artefakt | release-engineer | 70 |

Summe ≈ 1070 Schritte; kein Punkt über 160. Reviews (code-reviewer,
security-auditor) laufen wie gewohnt je PR und sind nicht budgetiert.

## Startprompt für den Orchestrator

„Lies docs/phase4-10-punkte-plan.md und führe die Punkte 1–10 in der
angegebenen Reihenfolge aus. Übergib jedem Agenten den Prompt seines
Punkts wörtlich als Briefing inklusive Budgetzeile. Halte nach Punkt 1
für meine Abnahme des Design-Addendums an, nach Punkt 10 für das
menschliche Gate. Punkte 5 und 8 dürfen parallel laufen
(isolation: worktree, disjunkte Dateien)."

