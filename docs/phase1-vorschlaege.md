> Entscheidung: Konzept C — Prisma, 2026-07-09
> (Kopie der Entscheidungsvorlage phase1-spielkonzepte.md, inhaltlich
> unverändert; ausgearbeitet in docs/game-design.md.)

# Phase 1 — Drei Spielkonzepte für Puzzlewerk

Entscheidungsvorlage für das Meilenstein-Gate. Alle drei Konzepte sind auf
den vorhandenen Stack zugeschnitten (Kotlin, Compose Canvas, :game als pures
Kotlin-Modul, Seed-deterministisch, offline, keine Dark Patterns) und als
Version 1 mit 30–50 Levels umsetzbar.

---

## Konzept A — „Glimmer" (Calm Game: Glühwürmchen & Taschenlampe)

### Elevator Pitch
Eine stille Nachtlandschaft. Du hältst den Finger auf den Bildschirm — dort
leuchtet deine Taschenlampe. Glühwürmchen fühlen sich vom Licht angezogen,
aber zu grelles, hektisches Leuchten verschreckt sie. Führe den Schwarm
behutsam zu seinem Zuhause, vorbei an Wind, Wasser und Dunkelfallen.

### Kernmechanik
- **Ein-Finger-Steuerung:** Der Lichtkegel folgt dem Finger (Position +
  Haltedauer = Helligkeit). Loslassen dimmt das Licht langsam ab.
- **Schwarmverhalten:** Jedes Glühwürmchen folgt drei einfachen, puren
  Regeln (Anziehung zum Licht, Abstand zum Nachbarn, Trägheit). Der Schwarm
  ist damit ein deterministischer Zustandsautomat — perfekt testbar.
- **Spannung ohne Stress:** Bewegst du das Licht zu schnell, reißt der
  Schwarm; hältst du es zu lange an einer Stelle, schlafen die Tiere ein.
  Der „Puzzle-Anteil" liegt im Routenlesen: Wo führe ich den Schwarm lang,
  in welcher Reihenfolge sammle ich versprengte Tiere ein?
- **Level-Ziel:** Mindestens X von Y Glühwürmchen erreichen die Laterne.
  Kein Timer, kein Game Over — misslungene Level kosten nichts außer einem
  neuen Versuch.

### Progression (30–50 Level)
Neue Landschaftselemente statt höherem Tempo: Windströme (tragen den Schwarm
ab), Teiche (Licht spiegelt und lockt in die falsche Richtung), Laternenketten
(Zwischenstationen), scheue Einzelgänger (brauchen sehr schwaches Licht),
Nachtvögel (meiden Lichtkegel — sanftes „Gegner"-Element ohne Bestrafung).

### Architektur-Fit
Sehr gut, mit einer Besonderheit: Die Schwarm-Simulation ist ein
kontinuierlicher Tick (`step(state, input, dt): GameState`) statt diskreter
Züge — die MVI-Architektur trägt das problemlos, Property-Tests der
Invarianten („kein Glühwürmchen verlässt die Weltgrenzen", „gleicher Seed +
gleiche Eingaben = gleicher Verlauf") sind sogar besonders wertvoll.
Rendering (weiche Glows, Partikel) ist die Hauptarbeit in :app.

### Markt-Einschätzung
Calm/Cozy Games haben ein treues Publikum und exzellente Bewertungen, aber
eher schwache Direktumsätze. Stärken: Featuring-Potenzial im Play Store
(„Schöne Spiele", „Entspannung"), starke Optik für Screenshots/Trailer.
Realistisches Modell: Premium (2–4 €) oder kostenlos mit einmaligem
„Unterstützer-Kauf". Eher ein Portfolio- und Reputationsspiel als ein
Umsatzbringer.

**Risiko:** Das Spielgefühl steht und fällt mit Feintuning der
Schwarm-Parameter — dafür braucht es viele Spieltest-Iterationen von dir
(Agents können Korrektheit beweisen, aber nicht „fühlt sich gut an").

---

## Konzept B — „Ping" (Echolot-Parcours)

### Elevator Pitch
Absolute Dunkelheit. Ein Tipp auf den Bildschirm sendet einen Sonar-Impuls:
Für zwei Sekunden zeichnen Lichtringe die Umrisse von Wänden, Fallen und dem
Ausgang — dann versinkt alles wieder in Schwarz. Präge dir den Weg ein und
gehe ihn im Gedächtnis. Ein Spiel über Erinnern, nicht über Reflexe.

### Kernmechanik
- **Zwei Aktionen:** Tippen = Ping (begrenzte Anzahl pro Level!), Wischen/
  Halten = Figur bewegt sich in Blickrichtung. Bewegung ist gemächlich,
  Kollisionen sind sanft (zurückgleiten statt Tod).
- **Der Kern-Tradeoff:** Pings sind Ressource UND Risiko: Jeder Ping zeigt
  die Karte, weckt aber „Lauscher" — träge Kreaturen, die sich zur letzten
  Ping-Quelle bewegen. Wer viel pingt, sieht viel, wird aber verfolgt.
- **Puzzle-Anteil:** Level sind Labyrinthe mit beweglichen Elementen
  (Plattformen, rotierende Wände), deren Rhythmus man aus mehreren Pings
  erschließen muss. Score = wenige Pings + wenige Kollisionen.

### Progression (30–50 Level)
Kapitel mit je neuem Element: Echo-Materialien (Metall klingt weiter,
Moos schluckt den Ping), Einweg-Membranen, Lauscher-Varianten, am Ende
„Blind Runs" (Bonuslevel: ein einziger Ping am Start).

### Architektur-Fit
Exzellent — das testbarste der drei Konzepte: Grid-/Vektorwelt, diskrete
Sichtbarkeitsberechnung (Raycast vom Ping-Punkt), deterministische
Lauscher-KI. Die gesamte Logik inklusive „was zeigt ein Ping an Position P"
ist eine pure Funktion. Rendering ist bewusst minimalistisch (Ringe,
Silhouetten) — wenig Asset-Aufwand, starker Stil.

### Markt-Einschätzung
Klarer Hook („das Spiel, das man im Dunkeln spielt"), sehr gut in 30
Sekunden erklärbar — ideal für Shorts/TikTok-Clips, was heute der
wichtigste Discovery-Kanal für Indie-Mobile ist. Nische, aber mit
Viral-Potenzial. Premium oder Free mit Level-Pack-Kauf.

**Risiken:** Barrierefreiheit braucht Sorgfalt (das Spiel ist fast reine
Visuik + Timing; eine Vibrations-/Audio-Spur für Pings wäre Pflicht, Ton
verstößt aber nicht gegen unsere Offline-/Datensparsamkeits-Regeln).
Frustbalance: „im Dunkeln laufen" darf nie wie Strafe wirken — die
Sanft-Kollisionen sind dafür zentral.

---

## Konzept C — „Prisma" (mein Vorschlag: kommerziell orientiert)

### Elevator Pitch
Ein Licht-Routing-Puzzle mit täglichem Rätsel: Drehe Spiegel, Prismen und
Filter auf einem Hex-Raster, damit der Lichtstrahl alle Kristalle in ihrer
Farbe trifft. Weißes Licht lässt sich in Rot/Grün/Blau aufspalten und wieder
mischen — aus drei simplen Regeln entsteht große Kombinationstiefe.
Thematisch schließt es den Kreis: Alle drei Kandidaten sind „Licht-Spiele" —
eine mögliche Studio-Identität.

### Kernmechanik
- **Ein Zug = eine Drehung** eines Elements (Spiegel 45°-Schritte, Prisma,
  Farbfilter, Splitter, Portal). Der Strahl wird nach jedem Zug komplett
  neu berechnet — pure Funktion `trace(board): Set<LitCrystal>`.
- **Farblogik als Tiefenmechanik:** Rot+Grün=Gelb usw.; Kristalle verlangen
  exakte Farben. Späte Level verlangen, einen Strahl mehrfach zu nutzen.
- **Zwei Spielmodi aus demselben Content-System:**
  1. **Kampagne:** 50 handkuratierte Level (kuratiert = vom Level-Generator
     erzeugt, von dir ausgewählt),
  2. **Tägliches Prisma:** jeden Tag ein Level für alle Spieler gleich
     (Seed = Datum — unsere Seed-Architektur liefert das gratis!), mit
     lokaler Serien-Statistik. Sanft, ohne Streak-Bestrafung (Dark-Pattern-
     Regel des Game-Designers bleibt gewahrt).

### Progression
Kampagne schaltet Elementtypen frei (Spiegel → Splitter → Prisma → Filter →
Portale → bewegliche Elemente); das tägliche Rätsel mischt alle
freigeschalteten Elemente mit wachsender Wochen-Schwierigkeit (Mo leicht →
So schwer, wie bei Kreuzworträtseln etabliert).

### Architektur-Fit
Perfekt — als einziges Konzept ist es ein lupenreines Zug-Puzzle und passt
1:1 auf das vorhandene `GameEngine`-Muster (`applyMove`, `MoveResult`).
Strahlverfolgung auf dem Hex-Raster ist zu 100 % property-testbar
(„jeder Strahl terminiert", „Energie-Erhaltung bei Splittern", „Lösung des
Generators löst das Level"). Der Level-Generator mit Lösbarkeits-Beweis ist
das anspruchsvollste Teilstück — und genau die Art Aufgabe, die das
Agent-Team mit Tests wasserdicht machen kann.

### Markt-Einschätzung
Das kommerziell belastbarste der drei: Puzzle ist das verlässlichste
Casual-Genre im Play Store, das Daily-Format ist der stärkste bekannte
Retention-Mechanismus ohne Dark Patterns (Wordle-Prinzip: ein Rätsel pro
Tag, alle spielen dasselbe, man redet darüber), und die Monetarisierung ist
mit unseren Regeln vereinbar: Kampagne teilweise gratis, Vollversion +
Level-Packs als Einmalkauf, Daily für immer kostenlos als Zugpferd.
Farb-Licht-Optik gibt starke Store-Screenshots.

**Risiko:** Konkurrenz — Laser-/Spiegel-Puzzles existieren viele. Die
Differenzierung muss aus Farbmischung + Daily-Format + Politur kommen,
nicht aus der Grundidee.

---

## Vergleich

| Kriterium | A Glimmer | B Ping | C Prisma |
|---|---|---|---|
| Passung zur bestehenden Architektur | gut (Tick-Simulation) | sehr gut (diskrete Logik) | perfekt (Zug-Puzzle) |
| Testbarkeit durch Agents | hoch | sehr hoch | sehr hoch |
| Aufwand bis spielbarer Prototyp | mittel–hoch (Feintuning!) | mittel | mittel |
| Anteil „Gefühls-Tuning" durch dich | sehr hoch | mittel | niedrig |
| Verkaufspotenzial | niedrig–mittel | mittel (Viral-Chance) | am höchsten |
| Featuring-/Award-Potenzial | am höchsten | hoch | mittel |
| Content-Nachschub nach Release | neue Level teuer | neue Level mittel | quasi gratis (Generator + Daily) |

## Empfehlung

Wenn Verkaufen das Primärziel ist: **C (Prisma)** — es nutzt die Stärken
des Agent-Setups maximal aus (reine Logik, Generator, Daily aus Seeds) und
hat das robusteste Geschäftsmodell. **B (Ping)** ist die beste Wahl, wenn
dir ein unverwechselbares Profil wichtiger ist als planbarer Umsatz — und
es ist das technisch dankbarste. **A (Glimmer)** ist das schönste, aber
riskanteste: viel handwerkliches Spielgefühl, das Agents dir nicht abnehmen
können — als zweites Projekt nach einem gelernten ersten Release wäre es
besser aufgehoben.

Hybrid-Gedanke, falls du dich schwer entscheiden kannst: C bauen, aber mit
der Atmosphäre von A (Nacht, Glühen, ruhige Musik statt greller
Puzzle-Optik). „Calm Puzzle mit Daily-Rätsel" ist eine Kombination, die
beide Welten mitnimmt.

## Nächster Schritt (Prozess)

Konzept wählen (oder Elemente kombinieren) → dann dem Orchestrator geben:
„Phase 1 starten: game-designer soll docs/game-design.md für Konzept <X>
ausarbeiten. Diese Entscheidungsvorlage liegt unter docs/phase1-vorschlaege.md."
Der game-designer verfeinert dann Regeln, Zahlen und den Randfall-Katalog —
und du nimmst das Dokument ab, bevor Code entsteht.
