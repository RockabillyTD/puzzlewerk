# Game-Design — „Prisma" (Puzzlewerk, Version 1)

> STATUS: ZUR ABNAHME (Phase 1, Ticket PW-1.1) — erstellt 2026-07-09.
> Grundlage: Entscheidung für Konzept C aus docs/phase1-vorschlaege.md.
> Dieses Dokument ist die einzige Quelle der Wahrheit für die Spiellogik.
> Was hier nicht steht, existiert nicht. Bewusste Präzisierungen oder
> Abweichungen von der Konzeptvorlage sind mit „**Designentscheidung:**"
> markiert und begründet.

---

## 1. Kernkonzept

Die Spielerin dreht Spiegel und Strahlteiler auf einem Hex-Brett, um
Lichtstrahlen von Quellen zu Kristallen zu lenken. Weißes Licht lässt
sich per Prisma in Rot, Grün und Blau zerlegen; am Kristall mischen sich
eintreffende Strahlen additiv. Jeder Kristall verlangt EXAKT eine Farbe —
zu wenig ist zu wenig, zu viel („Übersättigung") ist auch falsch. Der
eine besondere Kniff: Farbmischung passiert nicht im Element, sondern am
Ziel — man denkt in „welche Komponenten müssen wo ankommen", nicht nur
in Strahlgeometrie. Es gibt keinen Timer, kein Game Over und keine
Bestrafung: gelöst ist gelöst, alles andere ist Weiterprobieren.

Zwei Modi aus einem Content-System: eine 50-Level-Kampagne und das
„Tägliche Prisma" (jeden Tag ein für alle identisches, aus dem Datum
generiertes Rätsel).

---

## 2. Brettmodell

### 2.1 Koordinatensystem

- **Hex-Raster, Axial-Koordinaten `(q, r)`**, Zellen sind
  **pointy-top**-Sechsecke (Spitze oben).
- Abgeleitete Kubik-Koordinate: `s = −q − r`.
- **Brettform:** regelmäßiges Sechseck mit Radius `R`.
  Eine Zelle `(q, r)` gehört zum Brett gdw.
  `max(|q|, |r|, |q + r|) ≤ R`.
- **Zellenzahl:** `3·R·(R+1) + 1`
  (R=2 → 19, R=3 → 37, R=4 → 61, R=5 → 91).
- **Brettgrößen:** minimal `R = 2`, maximal `R = 5`.
  Kampagne nutzt R 2–4, das Tägliche Prisma R 2–4; R = 5 ist die harte
  Engine-Obergrenze (Validierung, Tests, Maximalwerte).

### 2.2 Richtungen

Sechs Strahlrichtungen, nummeriert 0–5. Konvention: mathematischer
Winkel (x nach rechts, y nach oben), Richtung `d` entspricht dem Winkel
`d · 60°`. `d + 1 (mod 6)` ist die nächste Richtung **gegen den
Uhrzeigersinn** (in dieser mathematischen Konvention).

| d | Name | Δq | Δr | Winkel |
|---|------|----|----|--------|
| 0 | O  (Ost)       | +1 | 0  | 0°   |
| 1 | NO (Nordost)   | +1 | −1 | 60°  |
| 2 | NW (Nordwest)  | 0  | −1 | 120° |
| 3 | W  (West)      | −1 | 0  | 180° |
| 4 | SW (Südwest)   | −1 | +1 | 240° |
| 5 | SO (Südost)    | 0  | +1 | 300° |

Nachbarzelle: `neighbor((q, r), d) = (q + Δq[d], r + Δr[d])`.
Gegenrichtung: `(d + 3) mod 6`.

**Designentscheidung:** Die Konzeptvorlage nannte „45°-Schritte" für
Spiegel — das stammt aus Quadrat-Raster-Denken. Auf dem Hex-Raster gibt
es 6 Strahlrichtungen im 60°-Abstand; Spiegel und Splitter rotieren
daher in **30°-Schritten** (6 Orientierungen, siehe 4.2/4.3), alle
anderen Winkelangaben sind Vielfache von 60°. Nur so bildet jede
Reflexion wieder auf eine gültige Hex-Richtung ab (Herleitung in 4.2).

### 2.3 Zellinhalt

Jede Zelle enthält **höchstens ein Element** (Abschnitt 4) oder ist
leer. Der Brettrand (alles außerhalb des Sechsecks) absorbiert Strahlen.

### 2.4 Rendering-Hinweis (nicht logikrelevant)

Pixel-Abbildung pointy-top: `x = size · √3 · (q + r/2)`,
`y = size · 1.5 · r` (Bildschirm-y nach unten). Dadurch erscheint die
logische Rotation `+1` (mathematisch gegen den Uhrzeigersinn) auf dem
Bildschirm **im Uhrzeigersinn** — die UI animiert Taps als
Uhrzeigersinn-Drehung. Die Spiellogik kennt nur Indizes, keine Winkel.

---

## 3. Farblogik

### 3.1 Farbmodell

Farben sind Bitmasken über den additiven Primärkomponenten:
Bit 0 = Rot (1), Bit 1 = Grün (2), Bit 2 = Blau (4).

| Code | Bits (BGR) | Name | Typ | Symbol (siehe 13) |
|------|-----------|------|-----|-------------------|
| 1 | 001 | Rot     | primär   | ▲ |
| 2 | 010 | Grün    | primär   | ● |
| 3 | 011 | Gelb    | sekundär | ▲● |
| 4 | 100 | Blau    | primär   | ■ |
| 5 | 101 | Magenta | sekundär | ▲■ |
| 6 | 110 | Cyan    | sekundär | ●■ |
| 7 | 111 | Weiß    | tertiär  | ▲●■ |

Code 0 („kein Licht") ist **keine gültige Strahlfarbe** — Invariante
I8: jeder existierende Strahl hat Farbe ∈ {1..7}.

### 3.2 Mischen (additiv) = bitweises ODER

**Designentscheidung:** Mischung findet ausschließlich **am Kristall**
statt (OR aller eintreffenden Strahlfarben). Strahlen, die sich in einer
Zelle kreuzen, interagieren NICHT (Licht durchdringt Licht). Es gibt in
V1 kein Kombinierer-Element. Begründung: Das hält `trace` frei von
Strahl-Strahl-Interaktion (jeder Strahl unabhängig verfolgbar,
deterministisch, trivial testbar) und macht Mischen zu einer
Ziel-Denkaufgabe statt einer Geometrie-Fummelei.

Vollständige OR-Tabelle (symmetrisch, idempotent):

| ∨ | R | G | Gelb | B | Mag | Cyan | Weiß |
|---|---|---|------|---|-----|------|------|
| **R**    | R    | Gelb | Gelb | Mag  | Mag  | Weiß | Weiß |
| **G**    | Gelb | G    | Gelb | Cyan | Weiß | Cyan | Weiß |
| **Gelb** | Gelb | Gelb | Gelb | Weiß | Weiß | Weiß | Weiß |
| **B**    | Mag  | Cyan | Weiß | B    | Mag  | Cyan | Weiß |
| **Mag**  | Mag  | Weiß | Weiß | Mag  | Mag  | Weiß | Weiß |
| **Cyan** | Weiß | Cyan | Weiß | Cyan | Weiß | Cyan | Weiß |
| **Weiß** | Weiß | Weiß | Weiß | Weiß | Weiß | Weiß | Weiß |

Erneute Mischung derselben Komponente ändert nichts (OR ist idempotent
und monoton — einmal empfangene Komponenten verschwinden innerhalb
einer trace-Auswertung nie wieder).

### 3.3 Filtern (subtraktiv) = bitweises UND

Ein Filter der Farbe `f` wandelt Strahlfarbe `c` in `c ∧ f`.
Ist das Ergebnis 0, wird der Strahl **absorbiert** (kein Strahl der
Farbe 0). **Designentscheidung:** Filter existieren in V1 nur in den
drei **Primärfarben** — Sekundärfilter (z. B. Gelb-Filter) brächten
kaum neue Rätsel, aber doppelt so viele Fälle; Idee im Backlog.

| Strahl ↓ / Filter → | Rot | Grün | Blau |
|---|---|---|---|
| Rot     | Rot | ∅ | ∅ |
| Grün    | ∅ | Grün | ∅ |
| Blau    | ∅ | ∅ | Blau |
| Gelb    | Rot | Grün | ∅ |
| Magenta | Rot | ∅ | Blau |
| Cyan    | ∅ | Grün | Blau |
| Weiß    | Rot | Grün | Blau |

∅ = Strahl absorbiert („Filter auf falscher Farbe").

---

## 4. Elemente

Übersicht. „Drehbar" heißt: per Zug rotierbar (Abschnitt 6). Die
Drehbarkeit ist eine Eigenschaft des Elementtyps, kein per-Instanz-Flag.

| Element | Zustände | Drehbar | Wirkung (Kurzform) |
|---|---|---|---|
| Lichtquelle | Farbe ∈ {1..7}, Richtung ∈ 0..5 | nein | emittiert 1 Strahl |
| Spiegel | Orientierung m ∈ 0..5 (30°-Schritte) | **ja** | reflektiert: `d_out = (m − d_in) mod 6` |
| Splitter | Orientierung m ∈ 0..5 (30°-Schritte) | **ja** | transmittiert gerade + reflektiert Kopie |
| Prisma | — (zustandslos) | nein | zerlegt in Primärkomponenten (Dispersion) |
| Filter | Farbe ∈ {Rot, Grün, Blau} | nein | UND-Filter, richtungsunabhängig |
| Portal | Paar-ID ∈ {0, 1} | nein | teleportiert Strahl zum Zwilling, Richtung bleibt |
| Kristall | Sollfarbe ∈ {1..7} | nein | Ziel; sammelt per OR, absorbiert Strahl |
| Wand | — | nein | absorbiert Strahl |

**Designentscheidung:** In V1 sind ausschließlich Spiegel und Splitter
drehbar. Quellen sind fix (Idee „drehbare Quellen" → Backlog). Prisma
und Filter sind rotationsinvariant definiert (siehe unten), eine
Drehung hätte keine Wirkung. Das hält den Zugraum klein und den
Par-Solver schnell (9.4).

**Designentscheidung:** „Bewegliche Elemente" aus der Konzeptvorlage
sind NICHT in V1 (→ Backlog). Die fünf aktiven Elementtypen + Farbe
tragen 50 Level; Verschieben wäre ein zweiter Zugtyp mit eigenem UX-,
Solver- und Generator-Aufwand — falscher Scope für ein poliertes V1.

### 4.1 Lichtquelle

- Attribute: Position, Farbe `∈ {1..7}`, Emissionsrichtung `o ∈ 0..5`.
- Emittiert genau einen Strahl: verlässt die Quellzelle in Richtung `o`.
- Ein Strahl, der eine Quellzelle **trifft**, wird absorbiert
  (das Gehäuse ist opak) — Randfall R01.
- Pro Level 1–3 Quellen.

### 4.2 Spiegel (drehbar)

- Orientierung `m ∈ 0..5`; der Spiegelstrich liegt auf der Geraden mit
  Winkel `m · 30°` durch das Zellzentrum. Der Spiegel ist
  **beidseitig** verspiegelt.
- Reflexionsregel für einen Strahl, der die Zelle in Laufrichtung
  `d_in` betritt:

  ```
  d_out = (m − d_in) mod 6
  ```

  Herleitung: Spiegelung des Richtungsvektors (Winkel `d_in·60°`) an
  der Geraden mit Winkel `m·30°` ergibt Winkel
  `2·(m·30°) − d_in·60° = (m − d_in)·60°`. Weil Spiegelgeraden auf dem
  30°-Raster liegen, ist das Ergebnis immer eine gültige Hex-Richtung —
  deshalb 30°-Schritte (siehe 2.2).
- Sonderfälle (beide liefert die Formel von selbst, kein Sondercode):
  - **Parallel** (`m = 2·d_in mod 6` ⇒ `d_out = d_in`): Der Strahl
    läuft unverändert geradeaus weiter (er streift den Spiegel längs).
  - **Senkrecht** (`m = (2·d_in + 3) mod 6` ⇒ `d_out = (d_in+3) mod 6`):
    Rückreflexion; der Strahl läuft seinen Weg exakt zurück.
- Farbe unverändert.

Beispiele: `m=1, d_in=0 → d_out=1` (Ost-Strahl wird nach Nordost
reflektiert). `m=3, d_in=0 → d_out=3` (senkrechter Spiegel wirft
Ost-Strahl nach West zurück). `m=0, d_in=0 → d_out=0` (parallel,
Durchlauf).

### 4.3 Splitter / Strahlteiler (drehbar)

Halbdurchlässiger Spiegel. Orientierung `m ∈ 0..5` wie beim Spiegel.
Ein Strahl (`d_in`, Farbe `c`) erzeugt:

1. **Transmission:** Strahl läuft geradeaus weiter (`d_in`, Farbe `c`).
2. **Reflexion:** zusätzlicher Strahl mit `d_out = (m − d_in) mod 6`,
   Farbe `c` — **außer** wenn `d_out = d_in` (Parallelfall): dann gibt
   es nur den einen geraden Strahl (kein Duplikat).

Rückreflexion (`d_out = (d_in + 3) mod 6`) ist erlaubt: transmittierter
Strahl läuft weiter, reflektierter läuft zurück.

**Designentscheidung — Energie-Semantik:** Es gibt **keine
Energie/Abschwächung**. Beide Splitter-Ausgänge tragen die volle Farbe.
Terminierung wird nicht über Energieverlust, sondern über die endliche
Zustandsmenge der Strahlverfolgung garantiert (5.3). Begründung:
Energie-Buchhaltung würde Zyklen mit Gleitkomma- oder
Schwellwert-Entscheidungen verseuchen; die Zustandsmengen-Terminierung
ist exakt, ganzzahlig und beweisbar.

### 4.4 Prisma (nicht drehbar)

Zerlegt jeden eintreffenden Strahl in seine Primärkomponenten
(Dispersion). Für einen Strahl (`d_in`, Farbe `c`) entstehen bis zu
drei Strahlen, je vorhandener Komponente von `c`:

| Komponente | Ausgangsrichtung | Farbe |
|---|---|---|
| Rot  (falls `c ∧ 1 ≠ 0`) | `(d_in + 1) mod 6` | Rot |
| Grün (falls `c ∧ 2 ≠ 0`) | `d_in` (geradeaus) | Grün |
| Blau (falls `c ∧ 4 ≠ 0`) | `(d_in + 5) mod 6` | Blau |

- Weiß → 3 Strahlen (R, G, B). Gelb → 2 Strahlen (R, G). Ein
  **einfarbiger** Strahl wird nur „gebogen": Rot knickt um +60°, Grün
  läuft gerade, Blau knickt um −60° (Randfall R07).
- Farb-Rekombination gibt es am Prisma nicht (Mischung nur am
  Kristall, 3.2).

**Designentscheidung:** Das Prisma ist relativ zur **Laufrichtung**
definiert und daher rotationsinvariant → nicht drehbar. Das entspricht
physikalischer Dispersion, ist aus jeder Anflugrichtung konsistent und
erspart 6 Orientierungszustände ohne Rätselverlust — die Interaktion
entsteht dadurch, WELCHEN Weg man dem Strahl zum Prisma gibt.

### 4.5 Filter (nicht drehbar)

- Attribut: Farbe `f ∈ {Rot, Grün, Blau}` (nur Primärfarben, 3.3).
- Richtungsunabhängig: Strahl (`d_in`, `c`) wird zu (`d_in`, `c ∧ f`),
  läuft geradeaus weiter; bei `c ∧ f = 0` absorbiert.

### 4.6 Portal (nicht drehbar)

- Portale existieren als **Paare** mit Paar-ID (max. **2 Paare** pro
  Level). Beide Zwillinge sind eigenständige Zellen; ein Portal ohne
  Zwilling ist ein Validierungsfehler (16.2).
- Ein Strahl, der Portalzelle `A` (Paar-Zwilling `B`) in Richtung `d`
  betritt, verlässt im selben Schritt Zelle `B` in **derselben
  Richtung** `d`, gleiche Farbe. Er betritt als Nächstes
  `neighbor(B, d)`.
- Portale sind symmetrisch (funktionieren in beide Richtungen) und
  richtungsunabhängig.
- Endlos-Pendel (z. B. Strahl läuft per Spiegel zurück ins Portal)
  terminieren über die Zustandsmenge (5.3, Randfälle R14/R15).

### 4.7 Kristall (Ziel, nicht drehbar)

- Attribut: Sollfarbe `soll ∈ {1..7}`.
- Sammelt: `empfangen = OR aller Strahlfarben, die die Zelle betreten`
  (innerhalb einer trace-Auswertung, Startwert 0).
- **Absorbiert** jeden eintreffenden Strahl (nichts läuft durch einen
  Kristall hindurch).
- Zustände nach einer trace-Auswertung:
  - `empfangen = 0` → **dunkel**
  - `0 < empfangen ⊂ soll` (echte Bit-Teilmenge) → **teilerfüllt**
  - `empfangen = soll` → **erfüllt**
  - `empfangen ∧ ¬soll ≠ 0` (Fremdkomponente) → **übersättigt**
    (NICHT erfüllt, eigenes visuelles Feedback, 12.3)
- Pro Level 1–6 Kristalle.

**Designentscheidung:** Kristalle absorbieren (statt durchlässig zu
sein). Begründung: eindeutiges Feedback („dieser Strahl endet hier"),
keine Doppelzählung bei Rückreflexionen durch denselben Kristall, und
„einen Strahl mehrfach nutzen" bleibt die exklusive Rolle von Splittern
und Portalen — das schärft die Elementidentitäten.

### 4.8 Wand (nicht drehbar)

Absorbiert jeden Strahl. Dient als Hindernis und zur Brett-Formung.
Der Brettrand verhält sich wie eine Wand.

---

## 5. Strahlverfolgung `trace(board)`

### 5.1 Zustandsbegriff

Ein **Strahlzustand** ist das Tripel `(zelle, richtung, farbe)` mit der
Bedeutung „ein Strahl verlässt `zelle` in `richtung` mit `farbe`".
Segmente (für Rendering) sind die daraus folgenden Kanten
`zelle → neighbor(zelle, richtung)`.

### 5.2 Algorithmus (Pseudocode)

```
fun trace(board): TraceResult {
    queue    = FIFO<BeamState>()        // BeamState = (zelle, dir, farbe)
    visited  = Set<BeamState>()
    lit      = List<Segment>()          // beleuchtete Kanten, Renderdaten
    received = Map<Coord, Int>()        // Kristall → OR-Akkumulator, Default 0
    steps    = 0

    // Quellen in deterministischer Reihenfolge: aufsteigend nach (q, dann r)
    for (quelle in board.quellen.sortedBy(q, r))
        queue.add(BeamState(quelle.pos, quelle.richtung, quelle.farbe))

    while (queue.isNotEmpty()) {
        s = queue.removeFirst()
        if (s in visited) continue
        visited += s
        require(++steps <= MAX_TRACE_STEPS)      // 5.3; Verletzung = Bug

        next = neighbor(s.zelle, s.dir)
        if (next !in board) continue             // Brettrand absorbiert
        lit += Segment(from = s.zelle, to = next, farbe = s.farbe)

        when (element = board[next]) {
            leer        -> queue.add(next, s.dir, s.farbe)
            Wand        -> { /* absorbiert */ }
            Quelle      -> { /* absorbiert, R01 */ }
            Kristall    -> received[next] = received[next] or s.farbe
            Spiegel(m)  -> queue.add(next, (m - s.dir).mod(6), s.farbe)
                           // Parallelfall d_out == d_in läuft hierüber
                           // automatisch geradeaus weiter
            Splitter(m) -> { queue.add(next, s.dir, s.farbe)   // 1) gerade
                             dOut = (m - s.dir).mod(6)
                             if (dOut != s.dir)
                                 queue.add(next, dOut, s.farbe) } // 2) Reflex
            Prisma      -> { if (s.farbe and 1 != 0) queue.add(next, (s.dir + 1).mod(6), ROT)
                             if (s.farbe and 2 != 0) queue.add(next, s.dir, GRUEN)
                             if (s.farbe and 4 != 0) queue.add(next, (s.dir + 5).mod(6), BLAU) }
            Filter(f)   -> { c = s.farbe and f
                             if (c != 0) queue.add(next, s.dir, c) }
            Portal      -> queue.add(element.zwillingPos, s.dir, s.farbe)
        }
    }
    return TraceResult(lit, received,
        solved = board.kristalle.all { received[it.pos] == it.soll })
}
```

Enqueue-Reihenfolgen sind exakt wie notiert (Quellen sortiert; Splitter:
erst Transmission, dann Reflexion; Prisma: R, G, B). Das Ergebnis
(lit-Menge, received, solved) ist reihenfolgeunabhängig, aber die feste
Ordnung macht Traces byte-identisch reproduzierbar (Golden-Tests,
Animationsreihenfolge).

### 5.3 Terminierung und Maximalwerte

- Zustandsraum: `|Zellen| · 6 Richtungen · 7 Farben`. Beim Maximal-Brett
  R=5: `91 · 6 · 7 = 3822` Zustände.
- Jede Schleifeniteration konsumiert entweder einen bereits besuchten
  Zustand (Queue schrumpft) oder fügt genau einen neuen Zustand zu
  `visited` hinzu. Pro verarbeitetem Zustand entstehen höchstens
  3 neue Queue-Einträge (Prisma). ⇒ `trace` terminiert immer, nach
  höchstens 3822 verarbeiteten Zuständen; die Queue enthält nie mehr
  als `3 · 3822` Einträge.
- `MAX_TRACE_STEPS = 4000` (> 3822): Das Erreichen dieser Grenze ist
  per Konstruktion unmöglich und daher ein Programmierfehler — hier
  ist eine Exception korrekt (C3: Bugs sind Exceptions).
- Zyklen (Spiegel-Dreiecke, Portal-Pendel, Rückreflexionen) laufen
  höchstens einmal pro (Zelle, Richtung, Farbe) und stoppen dann am
  `visited`-Check. Bereits erzeugte Segmente bleiben beleuchtet.
- Wichtig: `visited` MUSS die Farbe enthalten — dieselbe Kante kann
  legitim von verschiedenfarbigen Strahlen durchlaufen werden (R18).

### 5.4 Lösungsbedingung

Ein Brett ist **gelöst**, wenn für JEDEN Kristall
`empfangen == soll` gilt (exakte Gleichheit der Bitmasken; fehlender
Eintrag zählt als 0). Übersättigung irgendeines Kristalls ⇒ nicht
gelöst.

---

## 6. Züge, Levelziel, Undo

### 6.1 Der Zug

- **Genau ein Zugtyp:** `Rotate(zelle)` — Tap auf ein drehbares Element
  (Spiegel oder Splitter).
- Effekt: `m → (m + 1) mod 6` (eine Stufe = 30°; UI zeigt
  Uhrzeigersinn, siehe 2.4). Zug-Zähler `+1`.
- Es gibt **keine Gegenrichtung** (kein Counter-Rotate-Zug). Wer zu
  weit dreht, dreht weiter (6 Taps = Ausgangslage, R30) oder nutzt Undo.
- Nach jedem angewandten Zug wird `trace` neu ausgewertet; ist das
  Brett gelöst, wechselt die Partie in den Zustand **Gelöst** und
  nimmt keine Züge mehr an.
- Ungültige Züge (keine Zelle mit Element, nicht drehbares Element,
  Partie bereits gelöst) ändern NICHTS — Zähler und Verlauf bleiben
  unverändert; Ergebnis ist ein `Invalid`-Wert mit Grund (kein
  Fehler-Popup, nur sanftes UI-Feedback).

Sealed-Result-Skizze (Namen sind Vorschläge, Semantik ist bindend):

```
Move           = Rotate(zelle) | Undo | Reset
MoveResult     = Applied(neuerZustand, traceResult, solved: Bool)
               | Invalid(grund: KeinElement | NichtDrehbar
                               | BereitsGeloest | VerlaufLeer)
```

### 6.2 Undo und Reset

- **Undo:** nimmt den letzten Rotate-Zug zurück (`m → (m + 5) mod 6`),
  Zähler `−1`, Verlaufseintrag entfernt. Unbegrenzt bis Verlauf leer;
  bei leerem Verlauf `Invalid(VerlaufLeer)` (R28). Kein Redo in V1.
- **Reset:** stellt den Startzustand des Levels her, Zähler = 0,
  Verlauf leer. Jederzeit vor „Gelöst" erlaubt.
- Im Zustand **Gelöst** sind Rotate, Undo und Reset gesperrt (das
  Ergebnis-Overlay bietet „Nochmal spielen" = frisches Level).

**Designentscheidung:** Undo dekrementiert den Zähler vollständig —
Experimentieren kostet nichts (Anti-Frustration, keine versteckte
Bestrafung). Der Zähler zählt damit exakt die Länge des aktuellen
Zug-Verlaufs (Invariante I6).

### 6.3 Levelziel, kein Verlieren

- **Gewonnen:** Brett gelöst (5.4) nach einem Zug.
- **Verloren existiert nicht.** Kein Zuglimit, kein Timer, kein
  Fehlversuchszähler. Der Par-Wert (7.1) ist ein Ziel, keine Grenze.
- Sonderfall: Lädt ein Level, das bereits im Startzustand gelöst ist
  (nur durch Datenfehler möglich, der Generator schließt es aus, 9.5),
  gilt es sofort als gelöst mit 0 Zügen (R31).

### 6.4 Kommutativität (wichtig für Solver und Tests)

Da der einzige Zug eine Element-Rotation ist und `trace` nur vom
Brettzustand abhängt, gilt: **Das Ergebnis hängt nur von den
End-Orientierungen ab, nicht von der Zugreihenfolge.** Die minimale
Zuganzahl zu einer Ziel-Orientierungsbelegung ist exakt
`Σ über drehbare Elemente: (m_ziel − m_start) mod 6` (Invariante I9).

---

## 7. Scoring, Par und Sterne

### 7.1 Par

`Par` = **exakte minimale Zuganzahl**, mit der das Level aus dem
Startzustand lösbar ist. Der Generator berechnet Par per
kostengeordneter Suche (9.4) und speichert ihn in der Leveldefinition.
Jedes gültige Level hat `Par ≥ 1` (9.5). Obergrenze: `Par ≤ 14` (D7).

### 7.2 Punkteformel

```
Score = 1000 + Effizienzbonus
Effizienzbonus = max(0, 500 − 50 · max(0, Züge − Par))
```

- Lösen bringt immer mindestens 1000 Punkte.
- `Züge ≤ Par` (weniger als Par ist per Definition unmöglich) ⇒
  voller Bonus 500 ⇒ **1500 Punkte**.
- Jeder Zug über Par kostet 50 Bonuspunkte; ab `Par + 10` bleibt der
  Sockel von 1000.
- Kein Zeitfaktor. **Designentscheidung:** Zeit fließt nirgends ins
  Scoring ein — das Spiel ist ein ruhiges Denkspiel; Zeitdruck wäre
  ein Barrierefreiheits- und Frustrationsproblem ohne Design-Gewinn.

Sterne:

| Bedingung | Sterne |
|---|---|
| `Züge ≤ Par` | ★★★ |
| `Züge ≤ Par + 3` | ★★ |
| gelöst | ★ |

**Designentscheidung:** Sterne und Punkte schalten NICHTS frei (kein
Grind-Gate). Kampagnen-Fortschritt hängt nur vom Lösen ab (11.2).
Gesamtpunktzahl = Summe der besten Scores je Kampagnenlevel, rein zur
Anzeige. Wiederholtes Spielen überschreibt Score/Sterne nur, wenn das
neue Ergebnis besser ist.

### 7.3 Durchgerechnetes Beispiel-Level „Drei Farben" (R = 2)

Brett Radius 2, 19 Zellen. Elemente:

| Zelle (q, r) | Element | Zustand |
|---|---|---|
| (−2, 0) | Quelle | Weiß, Richtung 0 (O) |
| (0, 0)  | Spiegel | Start-Orientierung m = 5, drehbar |
| (1, −1) | Prisma | — |
| (1, −2) | Kristall | Soll: Rot |
| (2, −2) | Kristall | Soll: Grün |
| (2, −1) | Kristall | Soll: Blau |

Lageskizze (Zeilen r = −2 … +2, `.` = leer; Q Quelle, S Spiegel,
P Prisma, R/G/B Kristalle):

```
        .   R   G        r = −2:  (0,−2) (1,−2) (2,−2)
      .   .   P   B      r = −1:  (−1,−1) (0,−1) (1,−1) (2,−1)
    Q   .   S   .   .    r =  0:  (−2,0) (−1,0) (0,0) (1,0) (2,0)
      .   .   .   .      r = +1:  (−2,1) (−1,1) (0,1) (1,1)
        .   .   .        r = +2:  (−2,2) (−1,2) (0,2)
```

**Trace im Startzustand (m = 5):** Quelle emittiert Weiß nach O:
`(−2,0) → (−1,0)` leer `→ (0,0)` Spiegel: `d_out = (5 − 0) mod 6 = 5`
(SO) `→ (0,1)` leer `→ (0,2)` leer `→ (0,3)` außerhalb ⇒ absorbiert.
Alle Kristalle dunkel, nicht gelöst.

**Zug 1** (Tap auf Spiegel): `m = 0`. Trace: am Spiegel
`d_out = (0 − 0) mod 6 = 0 = d_in` ⇒ Parallelfall, Strahl läuft
geradeaus: `→ (1,0) → (2,0) → (3,0)` außerhalb. Nicht gelöst.

**Zug 2:** `m = 1`. Trace: am Spiegel `d_out = (1 − 0) mod 6 = 1` (NO)
`→ (1,−1)` Prisma, Weiß zerfällt:
- Rot, Richtung `(1+1) mod 6 = 2` (NW) `→ (1,−2)` Kristall Rot:
  empfangen = Rot ⇒ erfüllt ✓
- Grün, Richtung `1` (NO) `→ (2,−2)` Kristall Grün ⇒ erfüllt ✓
- Blau, Richtung `(1+5) mod 6 = 0` (O) `→ (2,−1)` Kristall Blau ✓

Alle drei Kristalle exakt erfüllt ⇒ **gelöst mit 2 Zügen.**

**Par-Bestimmung:** Einziges drehbares Element ist der Spiegel; einzige
lösende Orientierung ist m = 1 (alle anderen fünf verfehlen das Prisma,
Trace-Fälle analog zu oben). Kosten von Start m = 5:
`(1 − 5) mod 6 = 2` ⇒ **Par = 2**.

**Score:** Züge = 2 = Par ⇒ `1000 + 500 = 1500`, ★★★.

**Zweites Zahlenbeispiel (abstrakt):** Level mit Par = 4, Spielerin
löst mit 7 Zügen: `Score = 1000 + max(0, 500 − 50·(7−4)) = 1000 + 350
= 1350`; Sterne: `7 ≤ 4 + 3` ⇒ ★★.

---

## 8. Determinismus-Fundament (Seeds und PRNG)

Aller Zufall im Spiel stammt aus einem 64-Bit-Seed und dem folgenden
PRNG. Damit „gleicher Seed ⇒ identisches Level" auf jedem Gerät gilt,
ist der Algorithmus hier normativ festgelegt (Implementierung in
`:core` als `RandomSource`; ADR durch den Architekten):

- **SplitMix64** (Steele/Lea/Flood), reine 64-Bit-Ganzzahlarithmetik:

```
state: Long   // = Seed bei Initialisierung
fun nextLong(): Long {
    state += 0x9E3779B97F4A7C15
    var z = state
    z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9
    z = (z xor (z ushr 27)) * 0x94D049BB133111EB
    return z xor (z ushr 31)
}
fun nextInt(bound: Int): Int = floorMod(nextLong(), bound.toLong()).toInt()
```

- `mix64(x)` bezeichnet die drei Finalizer-Zeilen (ohne Inkrement) —
  benutzt zur Seed-Ableitung (10.1, 11.1).
- `nextInt(bound)` per `floorMod` ist minimal modulo-verzerrt; bei den
  hier üblichen kleinen `bound`-Werten irrelevant und dafür trivial
  reproduzierbar. **Designentscheidung:** Reproduzierbarkeit schlägt
  perfekte Gleichverteilung.
- Der Generator (9) verwendet ausschließlich Ganzzahlarithmetik —
  keine Floats, keine Plattformabhängigkeit, keine
  HashMap-Iterationsreihenfolgen (nur sortierte/indexierte Ordnungen).

---

## 9. Level-Generator

### 9.1 Schnittstelle

```
generateLevel(seed: Long, tier: Difficulty): LevelDefinition
```

Pure Funktion: gleiche `(seed, tier)` ⇒ byte-identische
`LevelDefinition` (Invariante I2). `Difficulty` = D1…D7 (9.2).

### 9.2 Schwierigkeitsstufen

| Tier | Radius | Quellen | Kristalle | Farben im Level | erlaubte Elemente | drehbare Elemente | Par-Ziel | Deko-Wände |
|---|---|---|---|---|---|---|---|---|
| D1 | 2 | 1 | 1–2 | 1 (Weiß o. Primär) | Spiegel | 1–2 | 1–3 | 0–2 |
| D2 | 2 | 1 | 2 | ≤ 2 | + Splitter | 2–3 | 2–4 | 0–3 |
| D3 | 3 | 1–2 | 2–3 | ≤ 3 | + Prisma | 3–4 | 3–6 | 1–4 |
| D4 | 3 | 1–2 | 3 | ≤ 4 | + Filter | 4–5 | 4–7 | 2–5 |
| D5 | 3–4 | 2 | 3–4 | ≤ 5 | + Portal (1 Paar) | 5–6 | 5–9 | 2–6 |
| D6 | 4 | 2–3 | 4–5 | ≤ 6 | alle (≤ 2 Portalpaare) | 6–7 | 6–11 | 3–8 |
| D7 | 4 | 2–3 | 5–6 | ≤ 7 | alle | 7–8 | 8–14 | 3–8 |

Harte Kappen (gelten zusätzlich immer): drehbare Elemente ≤ 8,
Portalpaare ≤ 2, Quellen ≤ 3, Kristalle ≤ 6, belegte Zellen ≤ 50 % des
Bretts (die Engine verkraftet 100 %, R41 — die Kappe ist eine
Generator-Qualitätsregel).

### 9.3 Konstruktion (rückwärts von der Lösung — Lösbarkeits-Garantie)

Referenzalgorithmus „Vorwärts-Verlegung, Rückwärts-Verwürfelung".
Implementierungsdetails der Heuristik sind frei, ABER: deterministisch
(nur der PRNG aus 8, feste Iterationsordnungen) und alle
MUSS-Eigenschaften aus 9.5 sind bindend.

1. **Parameter ziehen:** Radius, Quell-/Kristall-/Elementbudgets aus
   dem Tier (per `nextInt` innerhalb der Tabellenbereiche).
2. **Quellen platzieren:** zufällige Zellen im Randring
   (`max(|q|,|r|,|q+r|) = R`), Emissionsrichtung zeigt ins Brett,
   Farbe gemäß Farbbudget.
3. **Lösungszustand konstruieren:** Wiederholt, bis das
   Routing-Element-Budget verbraucht ist: den Strahl (per `trace`) von
   der Quelle verfolgen; auf einer zufällig gewählten freien Zelle des
   aktuellen Strahlwegs das nächste Element platzieren — Spiegel und
   Splitter direkt in ihrer **Lösungsorientierung**; Prismen, Filter,
   Portalpaare gemäß Budget. Bereits platzierte Kristalle blockieren
   (absorbieren) — das Routing späterer Strahlen muss das respektieren.
4. **Kristalle setzen:** an das Ende jedes offenen Strahlwegs (letzte
   freie Zelle vor Rand/Wand) einen Kristall mit GENAU der dort
   ankommenden Farbe setzen. Treffen mehrere Strahlen dieselbe
   Endzelle, ist die Sollfarbe das OR der ankommenden Farben
   (so entstehen Misch-Kristalle). Überzählige offene Strahlenden
   (Kristall-Budget erschöpft) dürfen im Rand/in Wänden enden.
5. **Deko platzieren:** Wände und ggf. unbeteiligte Elemente NUR auf
   Zellen, die kein Strahl des Lösungs-Trace berührt; danach per
   `trace`-Vergleich verifizieren, dass sich `received` nicht geändert
   hat (sonst Deko-Kandidat verwerfen).
6. **Lösungs-Check:** `trace(lösungszustand).solved == true`, kein
   Kristall übersättigt. Sonst: Versuch verwerfen → Schritt 1
   (derselbe fortlaufende PRNG-Strom, dadurch deterministisch).
7. **Verwürfeln (Scramble):** für jedes drehbare Element `i` einen
   Offset `o_i ∈ 0..5` ziehen mit `Σ o_i` im Par-Zielbereich des Tiers;
   Startorientierung `m_start,i = (m_lösung,i − o_i) mod 6`.
   Per Konstruktion ist der Startzustand mit `Σ o_i` Zügen lösbar ⇒
   **Lösbarkeit garantiert.**
8. **Par berechnen** (9.4) und validieren (9.5). Bei Verletzung:
   zurück zu Schritt 7 (max. 20-mal), dann zu Schritt 1.

### 9.4 Par-Solver

Wegen der Kommutativität (6.4) ist keine Zugbaum-Suche nötig, nur eine
Suche über End-Orientierungsvektoren der k ≤ 8 drehbaren Elemente:

```
für kosten c = 1, 2, 3, …, 14:
    für jeden Vektor (t_1..t_k), t_i ∈ 0..5, Σ t_i = c
        (lexikografisch aufsteigend aufgezählt):
        wende t_i Stufen auf Element i an
        wenn trace(brett_mit_diesen_orientierungen).solved:
            return Par = c
```

- Startzustand gelöst (c = 0) ist vorher ausgeschlossen (9.5).
- Abbruch: keine Lösung bis `c = 14` ⇒ Level verwerfen (Konstruktion
  entartet; wegen Schritt 7 hat der Scramble aber immer eine Lösung
  mit Kosten `Σ o_i ≤ 14` hinterlegt ⇒ die Suche findet sie ⇒ Par ist
  **exakt minimal**).
- Aufwand: Vektoren mit Kosten ≤ 14 bei k = 8 sind ≲ 3·10⁵ Traces à
  < 4000 Schritte — on-device (Daily wird auf dem Gerät generiert) in
  deutlich unter einer Sekunde machbar.

### 9.5 MUSS-Eigenschaften jedes generierten Levels

(Property-Test-Grundlage; gelten für Kampagne und Daily.)

1. `trace(startzustand).solved == false` und `Par ≥ 1`
   (kein 0-Züge-Level, R35).
2. Es existiert eine Lösung; `Par` ist deren exakte Minimalzuganzahl
   und liegt im Par-Zielbereich des Tiers.
3. Im Lösungszustand ist kein Kristall übersättigt oder dunkel.
4. Jeder Kristall ist im Lösungszustand erfüllt (folgt aus 3 + solved).
5. Alle Kappen aus 9.2 eingehalten; alle Elemente auf gültigen Zellen,
   max. eines pro Zelle; Portale vollständig gepaart.
6. `generateLevel(seed, tier)` ist referenziell transparent (I2).
7. **Terminierung:** Nach 1000 verworfenen Versuchen (Schritt 1)
   relaxiert der Generator deterministisch: erst Par-Zielbereich um ±2
   erweitern, dann Elementbudget schrittweise um 1 senken. Als letzte
   Stufe wird das parametrische Fallback-Level „Spiegelweg" erzeugt
   (1 Quelle Weiß am Westrand Richtung O, 1 Spiegel im Zentrum,
   1 Kristall Weiß, Scramble-Offset aus dem PRNG, Par ∈ 1..5) — auf
   jedem Radius konstruierbar; damit ist Terminierung garantiert.

---

## 10. Tägliches Prisma

### 10.1 Seed-Ableitung aus dem Datum

```
epochDay  = LocalDate(im lokalen Gerätekalender).toEpochDay()  // Long
dailySeed = mix64(epochDay xor 0x505249534D41)  // 0x505249534D41 = ASCII "PRISMA"
tier      = D(isoDayOfWeek)                     // Mo=1 → D1 … So=7 → D7
level     = generateLevel(dailySeed, tier)
```

- `mix64` aus Abschnitt 8. Das XOR mit der PRISMA-Konstante trennt den
  Daily-Seed-Raum vom Kampagnen-Seed-Raum (11.1).
- Datum/Uhrzeit kommen ausschließlich über die injizierte `WallClock`
  (C2); `dailySeed(date)` selbst ist eine pure Funktion in `:game`.

**Designentscheidung — Zeitzone: lokales Gerätedatum** (Wordle-Modell).
Das neue Rätsel erscheint für jede Spielerin um ihre lokale
Mitternacht. Konsequenz: Reisende über Zeitzonen können ein Datum
doppelt oder verfrüht sehen — akzeptiert, denn das Spiel ist offline
(kein Server, der eine Referenzzeit vorgeben könnte), und lokale
Mitternacht ist das intuitiv erwartete Verhalten. Regeln dazu:

- Das Puzzle ist über sein **Datum (LocalDate)** identifiziert.
- Pro Datum zählt nur die **erste Lösung** für die Statistik; erneutes
  Öffnen desselben Datums zeigt das Ergebnis (kein Doppelzählen, R38).
- Datumswechsel während einer laufenden Partie: die Partie bleibt
  gültig und wird für IHR Datum gewertet (R39).
- Uhr vor 1970 (negativer epochDay): Formel funktioniert unverändert
  (Bit-Arithmetik), kein Sonderfall, kein Crash (R37).

### 10.2 Wochen-Schwierigkeitskurve

| Wochentag (ISO) | Mo | Di | Mi | Do | Fr | Sa | So |
|---|---|---|---|---|---|---|---|
| Tier | D1 | D2 | D3 | D4 | D5 | D6 | D7 |

Das Daily nutzt alle Elementtypen unabhängig vom Kampagnenfortschritt —
durch die Tier-Staffel tauchen komplexe Elemente ohnehin erst ab
Mittwoch (Prisma) bzw. Freitag (Portal) auf. Beim ersten Kontakt mit
einem unbekannten Elementtyp erscheint die Element-Infokarte (12.5).

### 10.3 Serien-Statistik (lokal, ohne Bestrafung)

Lokal gespeicherte Felder: `gespieltGesamt`, `geloestGesamt`,
`aktuelleSerie`, `laengsteSerie`, `ergebnisJeDatum`
(Datum → Züge, Par, Score, Sterne).

- Serie = Anzahl aufeinanderfolgender lokaler Kalendertage mit
  gelöstem Daily, endend am letzten gelösten Tag.
- **Keine Streak-Bestrafung:** Ein verpasster Tag setzt lediglich
  `aktuelleSerie` beim nächsten Lösen neu auf 1. Es gibt KEINE
  Verlust-Warnungen, keine „Serie gerettet!"-Mechanik, keine
  Erinnerungs-Notifications, keine Trauer-UI. `laengsteSerie` bleibt
  als positiver Rekord immer stehen. Formulierungen in der UI sind
  neutral-positiv („Längste Serie: 12").
- Kein Archiv/Nachholen vergangener Tage in V1 (→ Backlog).

---

## 11. Kampagnen-Progression (50 Level)

### 11.1 Level-Quellen

Jedes Kampagnenlevel ist definiert durch `(tier, seed)` und wird mit
demselben Generator erzeugt. Default-Seed:
`campaignSeed(n) = mix64(n xor 0x4C4556454C)` (ASCII „LEVEL"),
`n` = Levelnummer 1–50. Die Level werden als Daten
(`LevelDefinition`, Schema 16.1) im Repo eingecheckt; Branko kuratiert
in Phase 2/4, indem einzelne Seeds in der Level-Tabelle ausgetauscht
werden („kuratiert = generiert + ausgewählt"). Die eingecheckten Daten
sind maßgeblich, der Seed dient der Reproduktion.

### 11.2 Freischaltung

Level `n` ist spielbar, wenn `n ≤ höchstesGelöstesLevel + 3`
(anfangs also 1–3 offen). **Designentscheidung:** Der 3-Level-Puffer
verhindert harte Blockaden („an Level 23 festgebissen"), ohne die
Progression zu entwerten. Sterne/Score schalten nichts frei (7.2).

### 11.3 Progressionstabelle

| Level | Neu | Tier | Radius | Quellen | Kristalle | Inhaltlicher Fokus |
|---|---|---|---|---|---|---|
| 1–3 | Spiegel | D1 | 2 | 1 | 1 | Tutorial: Tap = Drehung, Reflexionsgefühl |
| 4–8 | farbige Quellen | D1–D2 | 2 | 1 | 1–2 | mehrere Spiegel, erste Farb-Sollwerte |
| 9–12 | **Splitter** | D2 | 2–3 | 1 | 2 | ein Strahl, zwei Ziele |
| 13–16 | 2. Quelle | D3 | 3 | 2 | 2–3 | **Mischung am Kristall** (R+G→Gelb) |
| 17–21 | **Prisma** | D3 | 3 | 1–2 | 3 | Weiß zerlegen, drei Ziele aus einem Strahl |
| 22–26 | Kombination | D4 | 3 | 2 | 3–4 | Prisma + Splitter, Sekundär-Kristalle |
| 27–31 | **Filter** | D4–D5 | 3 | 2 | 3–4 | Komponenten gezielt entfernen |
| 32–36 | Filterketten | D5 | 3–4 | 2 | 4 | Übersättigung vermeiden lernen |
| 37–41 | **Portal** | D5–D6 | 4 | 2 | 4–5 | nichtlokale Wege |
| 42–46 | alles | D6 | 4 | 2–3 | 5 | 2 Portalpaare, volle Elementpalette |
| 47–50 | Meisterlevel | D7 | 4 | 3 | 5–6 | Maximalkomplexität, Par 8–14 |

Element-Infokarten erscheinen beim ersten Auftreten eines Elementtyps
(Level 1, 9, 17, 27, 37 — bzw. ggf. früher im Daily, 12.5).

---

## 12. UX-Flows

### 12.1 Screen-Übersicht

```
Home ──► Levelauswahl ──► Spiel (Kampagne) ──► Ergebnis-Overlay ──► nächstes Level / zurück
  │
  ├────► Tägliches Prisma (Statistik + Start) ──► Spiel (Daily) ──► Ergebnis + Statistik
  │
  └────► Einstellungen
```

### 12.2 Home

- Buttons: **Weiter** (niedrigstes ungelöstes Kampagnenlevel),
  **Tägliches Prisma** (mit Zustands-Badge: offen / gelöst + Sterne),
  **Levelauswahl**, **Einstellungen**.
- Zustände: Erststart (kein Fortschritt → „Weiter" startet Level 1) /
  normal / alles gelöst („Weiter" führt zur Levelauswahl).

### 12.3 Spiel-Screen (Kampagne und Daily identisch)

- Kopfzeile: Levelnummer bzw. Daily-Datum, Zugzähler, Par
  („Züge 3 · Par 5").
- Mitte: Hex-Brett. Strahlen werden nach JEDEM Zug sofort aus dem
  frischen `trace` gerendert (kein „Strahl abschicken"-Knopf).
- Fußzeile: **Undo**, **Reset** (mit Bestätigung ab ≥ 5 Zügen),
  **Elemente-Lexikon**, **Zurück**.
- Kern-Interaktion: **Tap auf drehbares Element = eine Drehstufe**
  (30°, animiert ~150 ms; Eingaben während der Animation werden
  gepuffert und der Reihe nach angewandt — die Logik ist sofort
  fertig, nur die Optik läuft nach). Tap auf nicht Drehbares/Leeres:
  kurzes Wackeln + dezenter Ton, kein Zug.
- Drehbare Elemente sind visuell markiert (heller Sockelring), nicht
  drehbare wirken „eingelassen".
- Kristall-Feedback (siehe auch 13): dunkel = grau mit Soll-Symbol;
  teilerfüllt = Soll-Symbol + empfangene Komponenten klein darunter;
  erfüllt = leuchtet + Haken; übersättigt = „zu viel"-Symbol
  (Soll + durchgestrichene Fremdkomponente), kein Flackern.
- Zustände: Spielend → Gelöst (Ergebnis-Overlay: Sterne, Score,
  „Züge X · Par Y", Buttons Weiter / Nochmal / Zurück). Einen
  Verloren-Zustand gibt es nicht (6.3).

### 12.4 Levelauswahl / Daily-Screen

- Levelauswahl: 50 Kacheln; Zustände je Kachel: gesperrt (11.2) /
  offen / gelöst (Sterne + bester Score). Kopf: Gesamtsterne,
  Gesamtscore.
- Daily-Screen: heutiges Datum, Tier-Anzeige (Mo–So-Skala), Statistik
  (10.3), Button Start / Fortsetzen / Ergebnis ansehen.

### 12.5 Einstellungen & Onboarding

- Einstellungen: Sound an/aus, Haptik an/aus, **Farbsymbole an/aus
  (Default: AN)**, **Strahlmuster an/aus (Default: AN)** (beide siehe
  13), Sprache folgt System (de/en), Lizenzen/Impressum, „Fortschritt
  zurücksetzen" (doppelte Bestätigung).
- Onboarding: keine Tutorial-Videos; Element-Infokarten (statische
  Grafik + 2 Sätze) beim jeweils ersten Kontakt mit einem Elementtyp,
  jederzeit im Elemente-Lexikon nachlesbar.
- Keine Notifications, keine Wartezeiten, keine Werbung, keine Käufe
  in V1 (17).

---

## 13. Barrierefreiheit — Farbe ist NIE der einzige Kanal

Prisma ist ein Farbspiel; ohne diesen Abschnitt wäre es für einen
erheblichen Teil der Spielerschaft (Farbfehlsichtigkeit betrifft ca.
8 % der Männer) unspielbar. Diese Regeln sind Pflicht, nicht Politur:

1. **Komponenten-Symbolik (Default: aktiv):** Jede Primärkomponente
   hat ein Formsymbol: Rot = ▲ (Dreieck), Grün = ● (Kreis), Blau = ■
   (Quadrat). Mischfarben zeigen die Kombination ihrer Komponenten
   (Gelb = ▲●, Magenta = ▲■, Cyan = ●■, Weiß = ▲●■). Symbole
   erscheinen auf: Quellen (emittierte Farbe), Kristallen (Sollfarbe,
   plus Ist-Komponenten in den Zuständen teilerfüllt/übersättigt),
   Filtern (Filterfarbe).
2. **Strahlmuster:** Zusätzlich zur Farbe tragen Strahlen ein
   Linienmuster: Rot = Strichlinie, Grün = Punktlinie,
   Blau = Strich-Punkt; Mischfarben = durchgezogen mit eingebetteten
   Symbol-Chips (▲●■-Miniaturen) etwa alle 2 Zellen. Abschaltbar,
   Default AN.
3. **Zustand nie nur über Farbe:** erfüllt = Haken-Icon + Leuchtaura;
   übersättigt = eigenes Icon (durchgestrichene Fremdkomponente);
   teilerfüllt = fehlende Komponenten als leere Umriss-Symbole.
4. **Palette:** farbfehlsichtigkeits-taugliche Töne (Vorschlag, final
   in Phase 3/4 gegen WCAG-AA-Kontrast geprüft): Rot #E5484D,
   Grün #30A46C, Blau #3E63DD, Gelb #F5D90A, Magenta #D6409F,
   Cyan #00B5D8, Weiß #F0F0F3 auf dunklem Hintergrund #101418.
   Verlässlichkeitsanker bleiben die Symbole, nicht die Töne.
5. **TalkBack:** Jede Zelle hat eine contentDescription nach dem
   Muster „Spiegel, drehbar, Orientierung 2 von 6, Reihe 0, Spalte 1"
   bzw. „Kristall, benötigt Gelb (Rot und Grün), empfängt Rot". Der
   Zug per Doppeltipp funktioniert mit TalkBack vollständig.
6. **Motorik/Tempo:** Touch-Targets ≥ 48 dp (Hexzellen skalieren
   entsprechend; R=4-Bretter nutzen Pinch-Zoom + Pan); NIRGENDS
   Zeitdruck (kein Timer, kein Zeitbonus, 7.2); Animationen
   respektieren die System-Einstellung „Animationen entfernen".
7. **Fotosensitivität:** keine Blitz-/Flackereffekte; Strahlen
   leuchten statisch, Erfolgs-Animationen sind sanfte Fades < 3 Hz.

---

## 14. Invarianten (Grundlage für Property-Tests)

- **I1** `trace` terminiert für jedes gültige Brett nach ≤ 4000
  verarbeiteten Zuständen (5.3).
- **I2** `generateLevel(seed, tier)` ist deterministisch: gleiche
  Eingaben ⇒ identisches Level (byte-gleiche Serialisierung).
- **I3** Der unverwürfelte Lösungszustand jedes generierten Levels
  löst das Level (`trace(...).solved`).
- **I4** Jedes generierte Level ist in genau `Par` Zügen lösbar und in
  keiner geringeren Anzahl; `1 ≤ Par ≤ 14`.
- **I5** `Score ∈ [1000, 1500]` bei jeder Lösung; Score monoton
  fallend in der Zugzahl.
- **I6** Zugzähler == Länge des Undo-Verlaufs; nie negativ.
- **I7** `applyMove` auf einem gültigen Zustand liefert einen gültigen
  Zustand (alle Schema-Regeln aus 16.2 bleiben erfüllt); `Invalid`
  lässt den Zustand unverändert.
- **I8** Keine Strahlfarbe ist jemals 0; `received`-Werte liegen
  in 0..7.
- **I9** Reihenfolge-Unabhängigkeit: jede Zugfolge mit gleichen
  End-Orientierungen erzeugt denselben Brettzustand und dasselbe
  trace-Ergebnis (6.4).
- **I10** Undo ∘ Rotate = Identität (Zustand und Zähler).

---

## 15. RANDFALL-KATALOG

Der test-engineer testet direkt gegen diese Liste. „⇒" beschreibt das
definierte Sollverhalten.

**Strahlverfolgung**

- **R01** Strahl trifft eine Lichtquellen-Zelle ⇒ absorbiert (4.1);
  auch die eigene Quelle nach einem Rundlauf.
- **R02** Spiegel parallel zum Strahl (`m = 2·d_in mod 6`) ⇒ Strahl
  passiert unverändert geradeaus.
- **R03** Spiegel senkrecht (`m = (2·d_in + 3) mod 6`) ⇒
  Rückreflexion; Strahl läuft seinen Pfad zurück und interagiert mit
  allen Elementen erneut aus der Gegenrichtung; Terminierung via
  visited-Set.
- **R04** Geschlossener Spiegel-Zyklus (z. B. 3 Spiegel im Dreieck) ⇒
  jeder Zustand max. einmal, trace terminiert, Segmente bleiben
  beleuchtet.
- **R05** Splitter mit `d_out = d_in` (Parallelfall) ⇒ genau EIN
  Ausgangsstrahl (keine Duplikat-Kopie).
- **R06** Splitter-Kaskade (Splitter speist Splitter, auch zyklisch) ⇒
  keine Exponential-Explosion: visited begrenzt auf
  ≤ |Zellen|·6·7 Zustände (R=5: 3822).
- **R07** Prisma mit einfarbigem Strahl ⇒ genau ein Ausgangsstrahl,
  gebogen (Rot +60°, Grün 0°, Blau −60°).
- **R08** Prisma mit Sekundärfarbe (z. B. Gelb) ⇒ genau 2 Strahlen
  (R und G), kein Blau-Strahl.
- **R09** Prisma-Ausgang zeigt direkt aus dem Brett ⇒ diese Komponente
  wird randlos absorbiert, übrige Komponenten normal.
- **R10** Prisma direkt hinter Prisma ⇒ zweites Prisma erhält
  Primärfarben und biegt sie nur noch (keine weitere Zerlegung).
- **R11** Filter auf „falscher" Farbe (`c ∧ f = 0`, z. B. Rot-Strahl
  auf Blau-Filter) ⇒ Strahl absorbiert, KEIN Strahl der Farbe 0.
- **R12** Weiß durch Primärfilter ⇒ genau die Filter-Komponente
  passiert (Weiß → Rot-Filter ⇒ Rot).
- **R13** Strahl exakt in Filterfarbe ⇒ passiert unverändert.
- **R14** Portal-Schleife (Strahl kehrt per Spiegel in ein Portal
  zurück) ⇒ terminiert via visited; keine Endlosschleife.
- **R15** Portal-Zwillinge benachbart, Strahl pendelt (betritt B,
  erscheint an A, läuft wieder in B, …) ⇒ zweiter identischer Zustand
  wird verworfen, terminiert.
- **R16** Strahl verlässt Portal-Zwilling direkt ins Brett-Aus ⇒
  absorbiert.
- **R17** Zwei Strahlen kreuzen sich in einer (leeren) Zelle ⇒ keine
  Interaktion, keine Mischung, beide laufen weiter.
- **R18** Gleiche Zelle + Richtung, ANDERE Farbe ⇒ eigenständige
  Zustände (visited-Schlüssel enthält die Farbe), beide werden
  verfolgt.
- **R19** Quelle am Rand zielt direkt aus dem Brett ⇒ 0 Segmente von
  dieser Quelle, kein Fehler.
- **R20** Quelle zielt direkt auf benachbarten Kristall ⇒ Kristall
  empfängt die Quellfarbe, Strahl endet (genau 1 Segment).
- **R21** Zwei Quellen exakt aufeinander gerichtet ⇒ jeder Strahl wird
  vom gegenüberliegenden Quellgehäuse absorbiert (R01), die Segmente
  dazwischen sind beleuchtet.

**Kristalle & Farben**

- **R22** Roter + grüner Strahl treffen denselben Gelb-Kristall ⇒
  `1 or 2 = 3` ⇒ erfüllt (Mischung am Kristall).
- **R23** Kristall Soll = Rot empfängt Rot UND Blau ⇒ übersättigt ⇒
  NICHT erfüllt, Level nicht gelöst.
- **R24** Weiß-Kristall: erfüllt durch einen Weiß-Strahl ODER durch
  R+G+B aus verschiedenen Richtungen (Ergebnis identisch: 7).
- **R25** Dieselbe Farbe trifft denselben Kristall mehrfach ⇒ OR
  idempotent, Zustand wie bei einfachem Treffer.
- **R26** Kristall absorbiert: ein dahinterliegender zweiter Kristall
  bleibt dunkel (kein Durchschuss).

**Züge & Partie-Zustand**

- **R27** Tap auf leere Zelle / nicht drehbares Element (Quelle,
  Prisma, Filter, Portal, Kristall, Wand) ⇒ `Invalid`, Zähler und
  Verlauf unverändert.
- **R28** Undo bei leerem Verlauf (0 Züge) ⇒ `Invalid(VerlaufLeer)`,
  Zähler bleibt 0.
- **R29** Reset bei 0 Zügen ⇒ gültig, Zustand identisch (No-op);
  Reset nach Zügen ⇒ exakt der Startzustand (auch Orientierungen),
  Zähler 0.
- **R30** 6 Taps auf dasselbe Element ⇒ Orientierung wie vorher,
  Zähler +6 (kein automatisches Kürzen).
- **R31** Level ist bereits im geladenen Startzustand gelöst (nur durch
  Datenfehler möglich) ⇒ sofort Zustand Gelöst, Züge = 0,
  Score = 1500, ★★★.
- **R32** Rotate/Undo/Reset im Zustand Gelöst ⇒
  `Invalid(BereitsGeloest)`.
- **R33** Zugzahl weit über Par (z. B. Par 2, 60 Züge) ⇒ Score = 1000
  (Bonus-Untergrenze 0, nie negativ), ★.

**Generator & Daily**

- **R34** Gleicher Seed + Tier, zwei Generierungen (auch auf
  verschiedenen Geräten/JVMs) ⇒ byte-identische LevelDefinition (I2).
- **R35** Generator liefert NIE ein bereits gelöstes Level (Par ≥ 1);
  ein Scramble-Ergebnis identisch zum Lösungszustand wird verworfen.
- **R36** Retry-Erschöpfung (pathologischer Seed) ⇒ deterministische
  Relaxierungsleiter bis Fallback „Spiegelweg" (9.5 Nr. 7), niemals
  Endlosschleife oder Crash.
- **R37** Gerätedatum vor 1970 (negativer epochDay) ⇒ Seed-Formel
  liefert definierten Wert, Daily funktioniert.
- **R38** Zeitzonenwechsel: dasselbe LocalDate erneut erreichbar ⇒
  gleiches Puzzle, Statistik zählt nur die Erstlösung pro Datum.
- **R39** Datumswechsel (Mitternacht) während laufender Daily-Partie ⇒
  Partie bleibt gültig und wird für ihr Startdatum gewertet; das neue
  Puzzle erscheint beim nächsten Besuch des Daily-Screens.
- **R40** DST-Umstellung (23-/25-Stunden-Tag) ⇒ Puzzle-Identität hängt
  nur am LocalDate, keine Doppel-/Fehlgenerierung.
- **R41** Maximal-Brett R=5 mit 100 % belegten Zellen (synthetischer
  Testfall; der Generator erzeugt so etwas nicht) ⇒ trace bleibt
  unter MAX_TRACE_STEPS, keine Ausnahme. „Volles Brett" heißt
  zugleich: keine leere Zelle ⇒ jeder Strahl interagiert in jeder
  Zelle.
- **R42** Minimal-Level: R=2, 1 Spiegel, Par=1 ⇒ alle Systeme (Score,
  Sterne, Overlay) funktionieren am unteren Rand („letzter Zug ist
  der erste").
- **R43** Level-Datei verletzt Schema (Portal ohne Zwilling, Zelle
  außerhalb des Bretts, doppelt belegte Zelle, Farbe 0, Par < 1) ⇒
  definierter Ladefehler (sealed Result), NIE Crash (S4, 16.2).

---

## 16. Datenmodell-Skizze und Validierung

Namen sind Vorschläge (die endgültige API definiert der Architekt),
**Semantik und Wertebereiche sind bindend.**

### 16.1 Typen

```
Coord            = (q: Int, r: Int)
Color            = Int (1..7, Bitmaske; 3.1)
Direction        = Int (0..5; 2.2)
Orientation      = Int (0..5)

Element (sealed) = Quelle(farbe: Color, richtung: Direction)
                 | Spiegel(orientierung: Orientation)      // drehbar
                 | Splitter(orientierung: Orientation)     // drehbar
                 | Prisma
                 | Filter(farbe: Color /* nur 1, 2, 4 */)
                 | Portal(paarId: Int /* 0..1 */)
                 | Kristall(soll: Color)
                 | Wand

LevelDefinition  = (id, radius: Int /* 2..5 */,
                    elemente: Map<Coord, Element>, par: Int /* 1..14 */,
                    tier: Difficulty, seed: Long)
GameState        = (level: LevelDefinition,
                    orientierungen: Map<Coord, Orientation>,
                    verlauf: List<Coord>, geloest: Boolean)
TraceResult      = (segmente: List<Segment>,
                    empfangen: Map<Coord, Int /* 0..7 */>,
                    solved: Boolean)
```

### 16.2 Validierungsregeln beim Laden (Vertrauensgrenze, S4)

1. `radius ∈ 2..5`; alle Koordinaten erfüllen
   `max(|q|, |r|, |q+r|) ≤ radius`.
2. Höchstens ein Element je Zelle (Map erzwingt das strukturell; das
   Serialisierungsformat muss Duplikat-Schlüssel ablehnen).
3. ≥ 1 Quelle, ≤ 3 Quellen; ≥ 1 Kristall, ≤ 6 Kristalle;
   ≤ 8 drehbare Elemente; Portal-IDs ∈ {0, 1} und jede ID kommt
   **genau 0 oder genau 2** Mal vor.
4. Farben: Quelle/Kristall ∈ 1..7, Filter ∈ {1, 2, 4};
   Richtungen/Orientierungen ∈ 0..5; `par ∈ 1..14`.
5. Verstoß ⇒ sealed Ladefehler mit Ursache; die App zeigt eine
   definierte Fehlermeldung, stürzt nie ab (R43).

---

## 17. Nicht-Ziele (Version 1)

Bewusst NICHT enthalten (Ideen dazu: docs/backlog.md, Abschnitt
„Game-Design-Ideen"):

- keine Monetarisierung (keine Käufe, keine Werbung, keine Paywall) —
  die Geschäftsmodell-Entscheidung fällt nach Release-Erfahrung
- kein Online/Netzwerk, keine Accounts, keine Cloud-Sync, kein
  Teilen-Feature
- keine Notifications, keine Erinnerungen, keine Wartezeiten
- keine beweglichen/verschiebbaren Elemente, keine drehbaren Quellen,
  keine Sekundärfarben-Filter, kein Kombinierer-Element
- kein Daily-Archiv, kein Nachholen, keine Leaderboards
- kein Hint-/Lösungs-System (Undo + Freischalt-Puffer fangen Frust
  ab; Hints wären in V1 Scope-Creep)
- kein Level-Editor, kein Level-Teilen
- keine Zeitmessung irgendwo in Logik oder Scoring
