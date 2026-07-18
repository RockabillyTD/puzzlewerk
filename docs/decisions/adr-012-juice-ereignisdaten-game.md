# ADR-012: Juice-Ereignisdaten aus :game — `juiceDelta` als pure Funktion + `TraceResult.endpoints`

- Status: AKZEPTIERT
- Datum: 2026-07-17
- Autor: architect (Ticket PW-4.2, Aufgabe 3; Anlass: Juice-Addendum
  docs/game-design.md §13.8–§13.11, docs/phase4-juice-update.md §2)
- Bezug: ADR-004 (Schichtenmodell: :game liefert Daten, kennt aber
  keine Präsentation), ADR-011 (Abnehmer der Daten), Umsetzung durch
  Mini-Ticket PW-4.3 (entwickler)

## Kontext

Das Addendum verbietet, fehlende Präsentationsdaten „still" in :app
nachzubauen: „Fehlt der Präsentation ein Datum […], wird es als eigenes
Mini-Ticket in :game nachgeliefert" (§13.8). Dieses ADR inventarisiert
den Ist-Stand und spezifiziert das Delta, das PW-4.3 implementiert.

**Was :game heute liefert** (game/src/main/kotlin/…):

- `MoveResult.Applied(state: GameState, trace: TraceResult)` — frischer
  trace nach JEDEM angewandten Zug; `state.solved`, `state.moveCount`.
- `TraceResult(segments: List<Segment>, received: Map<HexCoord,
  LightColor>, solved: Boolean)` — `received` enthält NUR getroffene
  Kristalle (fehlender Eintrag = dunkel), Werte 1..7 (I8);
  `segments` in normativer, reproduzierbarer Reihenfolge (§5.2).
- `Element.Crystal(required: LightColor)` über `state.currentBoard()`
  (Soll-Farben, K = Kristallzahl abzählbar).
- Top-Level-Funktion `crystalFill(required, received)` (Package
  `de.puzzlewerk.game.color`) — pure Erfüllungs-Klassifikation
  (DARK/PARTIAL/FULFILLED/OVERSATURATED).

**Was der Juice-Layer zusätzlich braucht** (Bedarf aus §13.8a–13.11):

1. **„Neu erfüllt"-Menge, sortiert** (§13.9: Bursts aufsteigend r,
   dann q; R45/R46): erfordert den Vergleich trace-VORHER vs.
   trace-NACHHER — `Applied` trägt nur den Nachher-trace.
2. **„Erstmals Licht ohne neue Erfüllung"** (`sfx_beam_connect`,
   §13.9): ebenfalls ein Vorher/Nachher-Vergleich von `received`.
3. **L und K für die Stem-Tabelle** (§13.11): L = aktuell erfüllte
   Kristalle, K = Kristallzahl des Levels.
4. **Strahl-Endpunkte** (§13.8a: Funken-Emitter an jeder On-Board-
   Absorption; Brett-Aus emittiert NICHT): aus `TraceResult` zwar
   konstruktiv ableitbar — je Segment-Zielzelle die lokalen
   Absorptionsregeln anwenden (Wand/Quelle/Kristall absorbieren immer,
   Filter genau bei nicht passierender Farbe, vgl. DefaultTracer) —,
   aber NICHT ohne diese Absorptions-/Filtersemantik in :app zu
   duplizieren: Spielregel-Nachbau außerhalb des 90-%-Branch-Gates von
   :game und ein zweiter Ort, der bei jeder Element-Erweiterung
   mitzupflegen wäre. Genau das verbietet §13.8.

## Optionen

1. **:app rechnet alles selbst** (hält den Vorher-trace, vergleicht,
   klassifiziert via `CrystalFill`). Verletzt die §13.8-Anweisung,
   dupliziert Erfüllungs-/Sortier-Semantik außerhalb des
   90-%-Coverage-Gates von :game — und müsste für Bedarf 4 zusätzlich
   die Absorptions-/Filterregeln des Tracers nachbauen (s. Kontext:
   ableitbar nur um den Preis der Semantik-Duplikation).
2. **`MoveResult.Applied` um ein Delta-Feld erweitern.** Die Engine
   müsste den Vorher-trace kennen: entweder je Zug ZWEIMAL tracen
   (Vorher-Brett erneut) oder `GameState` um den trace erweitern —
   größerer Eingriff in Engine-Vertrag, I-Invarianten-Tests und
   Golden-Fixtures; zudem bliebe der `newGame`-Fall (es gibt kein
   Vorher) ein Sonderfall im Kerntyp. Unverhältnismäßig, da der
   Aufrufer (GameViewModel) den Vorher-trace ohnehin in der Hand hat.
3. **Zwei additive, minimale :game-Änderungen:**
   a) `TraceResult` erhält `endpoints` (nur der Tracer kennt sie);
   b) eine pure, freie Funktion `juiceDelta(before, after, board)`
   berechnet den Vorher/Nachher-Vergleich — der Aufrufer reicht die
   beiden traces, die er bereits besitzt.

## Entscheidung

**Option 3.** Engine-Vertrag (`GameEngine`, `MoveResult`, `GameState`)
bleibt unangetastet; Scope-Grenze §13.8 („:game und :core bleiben
unberührt" bis auf nachgelieferte reine Daten) wird minimal-invasiv
eingelöst. Spezifikation für PW-4.3 (Namen englisch wie im Bestand,
KDoc deutsch; Package `de.puzzlewerk.game.trace`):

### a) Erweiterung `TraceResult` (additiv)

```kotlin
/**
 * On-Board-Absorptionspunkt eines Strahls (§13.8a) — Renderdatum für
 * die Auftreff-Funken. Ein Eintrag JE absorbiertem Strahl: enden zwei
 * Strahlen in derselben Zelle (z. B. R und G am Gelb-Kristall), gibt
 * es zwei Einträge mit je eigener Strahlfarbe.
 *
 * @property cell Zelle der Absorption (Wand, Quelle, Kristall oder
 *   Filter). Brett-Aus-Absorptionen erzeugen KEINEN Eintrag (§13.8a —
 *   bewusste Optik-Ausnahme zu §4.8; die trace-Logik bleibt unberührt).
 * @property color Farbe des absorbierten Strahls beim Auftreffen,
 *   nie 0 (I8).
 */
public data class BeamEndpoint(
    val cell: HexCoord,
    val color: LightColor,
)
```

`TraceResult` erhält als neue letzte Eigenschaft
`val endpoints: List<BeamEndpoint>` — Reihenfolge = normative
Strahl-Verarbeitungsreihenfolge (§5.2, dieselbe wie `segments`), damit
byte-identisch reproduzierbar; sie ist zugleich die Emitter-Index-Basis
aus ADR-011. Golden-/Beispieltests (u. a. §13.8a-Beispiel Level 7.3,
Zug 2: genau 3 Endpunkte an den Kristallen; Startzustand: 0 Endpunkte)
sind in PW-4.3 zu ergänzen; bestehende `TraceResult`-Konstruktionen in
Tests werden um das Feld erweitert (source-breaking, akzeptiert).

### b) Neue pure Funktion `juiceDelta`

```kotlin
/**
 * Vorher/Nachher-Vergleich zweier trace-Ergebnisse desselben Levels —
 * das Ereignis-Datum des Juice-Layers (§13.9–§13.11). Pure Funktion:
 * kein Zustand, keine Seiteneffekte; „erfüllt" heißt exakt
 * [CrystalFill.FULFILLED] (Übersättigung zählt NICHT als erfüllt).
 *
 * @param before trace VOR dem Zug; `null` beim Partie-Start (dann sind
 *   [JuiceDelta.newlyFulfilled] und [JuiceDelta.newlyLit] leer — das
 *   Laden eines Levels löst keine Bursts aus, auch im R31-Fall nicht).
 * @param after frischer trace NACH dem Zug ([MoveResult.Applied.trace]).
 * @param board effektives Brett des NEUEN Zustands
 *   ([GameState.currentBoard]) — Quelle der Soll-Farben und von K.
 */
public fun juiceDelta(
    before: TraceResult?,
    after: TraceResult,
    board: Board,
): JuiceDelta

/**
 * @property newlyFulfilled Kristalle, die NACH dem Zug erfüllt sind und
 *   VORHER nicht (R46: nach Undo erneut möglich) — sortiert aufsteigend
 *   nach (r, dann q), exakt die Burst-/SFX-Kaskadenreihenfolge §13.9.
 * @property newlyLit Kristalle, deren `received` von „kein Eintrag"
 *   auf ≥ 1 Komponente wechselt (§13.9 sfx_beam_connect-Bedingung:
 *   `newlyLit.isNotEmpty() && newlyFulfilled.isEmpty()`).
 * @property fulfilled ALLE nach dem Zug erfüllten Kristalle — L der
 *   Stem-Tabelle §13.11 ist `fulfilled.size`, auch abwärts nach
 *   Undo/Reset (R46, keine Hysterese).
 * @property crystalTotal K = Kristallzahl des Levels (§13.11, §13.10).
 */
public data class JuiceDelta(
    val newlyFulfilled: List<HexCoord>,
    val newlyLit: Set<HexCoord>,
    val fulfilled: Set<HexCoord>,
    val crystalTotal: Int,
) {
    /** Combo-Größe N des Zugs (§13.9): Anzahl neu erfüllter Kristalle. */
    public val comboSize: Int get() = newlyFulfilled.size
}
```

Nutzung in :app (informativ): GameViewModel hält den letzten
`TraceResult`, ruft nach jedem `Applied` `juiceDelta(prev, new, board)`
auf und übersetzt in `JuiceEvent`s (ADR-011) bzw. `StemMix`/SFX
(ADR-010). Soll-Farben je Burst liest :app über
`board.elements` (`Element.Crystal.required`).

## Konsequenzen

- (+) Erfüllungs-, Sortier- und Endpunkt-Semantik liegen vollständig
  in :game unter dem 90-%-Branch-Gate; :app enthält keine duplizierte
  Spielsemantik (§13.8 eingehalten, backlogfreundlich — vgl.
  Backlog Nr. 6 „Akzessor statt UI-Logik").
- (+) Kein Engine-/GameState-Eingriff: keine doppelten trace-Läufe,
  keine Fixture-Migration der I-Invarianten-Tests; `before = null`
  macht den Partie-Start explizit statt zum Sonderfall im Kerntyp.
- (+) Alles pur und explizit-API-konform — direkt property-/golden-
  testbar (R45: Sortierung; R46: Undo-Wiedererfüllung; §13.8a-Beispiele).
- (−) `TraceResult`-Konstruktor ändert sich (source-breaking für
  bestehende Tests/Fixtures) — bewusst akzeptiert, additiv, einmalig.
- (−) Der Tracer muss Absorptionen künftig als Endpunkte ausweisen —
  kleine, aber echte Tracer-Änderung; Golden-Segments dürfen sich dabei
  NICHT ändern (Prüfkriterium im PW-4.3-Review).
- (−) :app trägt die Verantwortung, den Vorher-trace korrekt
  mitzuführen (bei Undo/Reset/„Nochmal" der jeweils aktuelle Stand,
  R46/R49) — Choreografie-Tests in PW-4.6 und im QS-Pass PW-4.9
  decken das ab (Ticket-Nummern: 10-Punkte-Plan).
- Folgearbeit: PW-4.3 implementiert a) + b) mit Tests (inkl.
  §13.8a-/§13.9-Beispiele Level 7.3 und R50-Fälle K ≤ 2); erst danach
  arbeiten die abhängigen Punkte gegen reale Daten (PW-4.6
  Feedback-Verdrahtung, PW-4.8 Stem-Steuerung).