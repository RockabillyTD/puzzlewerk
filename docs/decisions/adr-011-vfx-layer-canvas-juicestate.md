# ADR-011: VFX-Layer — Canvas-only-Rendering und der pure JuiceState-Kern

- Status: AKZEPTIERT
- Datum: 2026-07-17
- Autor: architect (Ticket PW-4.2; Anlass: Juice-Addendum
  docs/game-design.md §13.8a–13.10, §13.12, §13.13, R44/R45/R49,
  docs/phase4-juice-update.md §2)
- Bezug: ADR-003 (SplitMix64/`mix64` in :core — Pflicht-PRNG des
  Juice-Layers per §13.13), ADR-004 (Juice ist reine :app-Schicht),
  ADR-009 (JVM-Testbarkeit als Gate-Anforderung),
  ADR-012 (Ereignisdaten aus :game)

## Kontext

§13.12 verlangt prozedurales Rendering (keine Effekt-PNGs) und stellt
die Technikfrage explizit an dieses ADR: „Ab API 33 optional
AGSL-Shader (RuntimeShader) für Glow, darunter Fallback auf
Radial-Gradients — gleiche Optik, andere Technik (Entscheidung: ADR
PW-4.2)."

Randbedingungen:

- **minSdk = 26** (gradle/libs.versions.toml): ein Gradient-Pfad muss
  in JEDEM Fall existieren und die volle normative Optik liefern —
  AGSL wäre immer nur ein ZWEITER Pfad für API-33+-Geräte.
- **„Gleiche Optik"** (§13.12) macht den Zweitpfad zur permanenten
  Paritätspflicht: jede visuelle Änderung wäre doppelt zu bauen und
  doppelt abzunehmen.
- **Gate läuft auf der JVM** (ADR-009): Robolectric rendert software;
  `RuntimeShader` ist hardware-beschleunigtem Rendering vorbehalten —
  ein AGSL-Pfad wäre in unserer Gate-Kette schlicht nicht ausführbar,
  seine Parität nicht automatisiert prüfbar.
- Der normative Effektumfang (§13.8a–13.10) besteht aus: Halo
  (radialer Alpha-Verlauf, additiv), Puls (Alpha-Modulation 2 Hz),
  Punkt-Funken 2 dp, Glow-Bursts (Radius/Alpha-Rampe), Vollbild-Flash
  und ≤ 120 Feuerwerk-Partikeln + lokale Funken — alles mit
  RadialGradient-Shadern + `BlendMode.Plus` in einem Layer darstellbar.
  Kein normativer Effekt braucht Per-Pixel-Programme.
- §13.13 macht den Determinismus zur C2-Pflicht: JuiceState als
  unveränderlicher Snapshot, `step` pur, SplitMix64 (ADR-003), Seed
  `juiceSeed = mix(levelSeed, zugNummer, emitterIndex)` — die konkrete
  mix-Funktion definiert dieses ADR.

## Optionen

1. **AGSL/RuntimeShader ab API 33 + Radial-Gradient-Fallback < 33.**
   Hübscherer Glow theoretisch möglich, GPU-Offload des Glow-Passes.
   Kosten: zwei dauerhaft paritätisch zu haltende Renderpfade; der
   AGSL-Pfad ist im JVM-Gate nicht ausführbar (Paritäts- und
   Regressionstests nur manuell/Emulator); Shader-Kompilierung und
   Treiber-Varianz früher API-33-Geräte als zusätzliche Fehlerquelle.
   Nutzen fraglich: die normative Optik ist vollständig ohne AGSL
   definiert — der Shader dürfte gar nicht anders aussehen.
2. **Canvas-only: RadialGradient + `BlendMode.Plus`, ein Pfad für
   API 26+.** Ein Renderpfad, eine Optik, auf jedem Gerät identisch
   und im JVM-Gate zumindest strukturell absicherbar (Render-Smoke +
   JuiceState-Goldens; Grenzen s. Konsequenzen). Performance-Profil:
   Kostentreiber ist additiver Overdraw und Partikelzahl (≤ 120 + Funken
   — Kappen sind normativ); mit gecachten Gradient-Shadern pro Farbe,
   einem einzigen `saveLayer` und allokationsfreiem Draw-Pfad
   (FloatArray-Snapshot, s. u.) klar im 60-fps-Budget der
   Mittelklasse. Kein API-Gating, kein Paritätsproblem.

## Entscheidung

**Option 2: Canvas-only.** Der Laser-/Partikel-Look wird ausschließlich
mit Canvas-Mitteln (RadialGradient, `BlendMode.Plus`, ein Layer)
gerendert, identisch auf API 26–36. AGSL wird NICHT eingeführt.

Absicherung statt Zweitpfad: Das Zeichnen ist strikt vom Zustand
getrennt (`JuiceRenderer` liest NUR den `JuiceState`-Snapshot). Sollte
die Frame-Budget-Messung in PW-4.9 auf dem Referenzgerät p95 < 55 fps
zeigen, kann ein Folge-ADR einen AGSL-Pfad HINTER dieser Naht
nachrüsten, ohne Zustand, Events oder Tests anzufassen. Bis dahin gilt:
ein Pfad ist der beste Paritätstest.

### JuiceState-Kern (öffentlicher Vertrag, :app)

Kompilierfähige Deklarationen liegen in
`app/src/main/kotlin/de/puzzlewerk/app/ui/juice/` (dieser PR, ohne
Implementierung; Implementierung: PW-4.4 (Kern ohne Rendering),
Rendering/Choreografie: PW-4.5–4.7). Eckpunkte:

- **Snapshot:** `JuiceState` ist unveränderlich und enthält alles, was
  der Renderer braucht: Zeit seit Screen-Betreten (Puls-Phase §13.8a,
  Nullpunkt = Betreten), Reduce-Motion-Flag, aktive Funken-Emitter,
  Partikel als Structure-of-Arrays-Snapshot (`ParticleSnapshot` mit
  FloatArrays für x/y/Größe/Alpha und IntArray für ARGB — der Renderer
  iteriert Index-basiert ohne jede Allokation), geplante Kaskaden-Bursts
  (§13.9), Flash-Zustand (§13.10). WICHTIG: Wegen der Array-Felder gilt
  Werte-Gleichheit NICHT über `equals`; Tests vergleichen Feldweise per
  `contentEquals` (KDoc am Typ).
- **Reine Übergangsfunktion:** `JuiceStepper.step(state, events, dtMillis)
  : JuiceState` — pur, ohne Uhr-, Android- oder IO-Zugriff. Gleiches
  `(state, events, dt)` ⇒ bit-identischer Folgezustand (§13.13;
  Testoberfläche des test-engineers: feste dt-Folgen). `dtMillis` ist
  der Frame-Delta in Millisekunden (Long), geliefert von
  `withFrameNanos` im UI-Code.
- **PRNG-Disziplin:** Zufall wird ausschließlich beim ENTSTEHEN eines
  Emitters/Partikels gezogen (Spawn-Zeitpunkt), nie während der
  Integration; die Integration selbst ist deterministische Arithmetik.
  Injektion über `JuiceRandomFactory.create(seed): RandomSource`
  (Default in der Implementierung: `SplitMix64Random` aus :core;
  Tests können zählende/fixierte Quellen injizieren).
- **Seed-Funktion (normativ, löst den Auftrag aus §13.13 ein):**

  `juiceSeed(levelSeed, zugNummer, emitterIndex) =
  mix64(mix64(mix64(levelSeed) + zugNummer) + emitterIndex)`

  mit `mix64` aus :core (ADR-003). Verkettete Finalizer-Anwendung —
  kollisionsarm, pur, versionsstabil, ohne neue Konstanten.
- **Emitter-Indizes (normativ):**
  | Emitter | `emitterIndex` | `zugNummer` |
  |---|---|---|
  | Auftreff-Funken (§13.8a) | `i` = Position in `TraceResult.endpoints` (0-basiert, ADR-012) | Zug, der den trace erzeugte (0 = Partie-Start) |
  | Kristall-Burst (§13.9) | `1000 + k`, k = Position in der sortierten `newlyFulfilled`-Liste (ADR-012) | auslösender Zug |
  | Feuerwerk (§13.10) | `2000` | lösender Zug |

  Die Namensräume kollidieren strukturell nicht: Endpunkt-Indizes
  bleiben < 1000 (Board.MAX_RADIUS = 5 ⇒ höchstens 91 Zellen, die Zahl
  absorbierter Strahlen liegt weit darunter) und k ≤ 5 (K ≤ 6, harte
  Kappe §9.2). Dreh-Funken (§13.9) ziehen KEINEN Zufall — Winkel
  30°/150°/270° relativ zur neuen Orientierung, Betrag und Lebensdauer
  sind fix.
- **Ereignisse (`JuiceEvent`, sealed):** vom GameScreen/ViewModel aus
  `MoveResult` + `JuiceDelta` (ADR-012) übersetzt; Positionen bereits
  in dp-Brettkoordinaten gemappt (der Kern kennt keine Hex-Geometrie),
  Farben bereits als ARGB aufgelöst (Palette §13.4):
  - `ScreenEntered(levelSeed, reduceMotion, endpoints)` — Puls-Nullpunkt,
    initiale Funken-Emitter.
  - `MotionPreferenceChanged(reduceMotion)` — R44 kann mid-session
    umschalten; Folge-Spawns haben Partikelzahl 0, lebende Partikel
    laufen aus.
  - `RotateFlash(cell-Position, orientierungsGrad)` — 3 deterministische
    Funken; das weiße 120-ms-Element-Overlay und das Wackeln bleiben
    Compose-Animationen (s. Abgrenzung unten).
  - `CrystalBursts(zugNummer, bursts)` — sortierte Liste (r, dann q;
    Sortierung kommt aus ADR-012), Kaskaden-Versatz 40 ms + Kappe ab
    dem 5. Burst übernimmt der Stepper (§13.9/R45).
  - `EndpointsChanged(zugNummer, endpoints)` — ersetzt die Menge der
    kontinuierlichen Funken-Emitter nach jedem Zug (4 Funken/s je
    Emitter, §13.8a).
  - `Solved(zugNummer, kristallzahl, paletteArgb)` — Feuerwerk-
    Zeitachse §13.10: t_fw-Berechnung, Flash 80 ms, F = min(120,
    60 + 12·K) Partikel, Farben zyklisch. Den Ursprung (zuletzt
    geborstener Kristall) leitet der Stepper aus der `CrystalBursts`-
    Kaskade DESSELBEN Zugs ab — ein lösender Zug erfüllt immer ≥ 1
    Kristall neu, das Ereignispaar tritt also stets gemeinsam auf
    (kein eigenes Index-Feld; deckungsgleich mit `JuiceEvent.Solved`).
  - `Dismissed` — R49: Partikel-Layer und Flash werden SOFORT
    verworfen („Nochmal"/„Weiter"/Zurück).
- **Abgrenzung (bewusst NICHT im JuiceState):** Stern-Einflug samt
  Bounce, Overlay-Fade (500/600 ms) und das bestehende
  Wackeln/Dreh-Timing aus §12.3 bleiben gewöhnliche
  Compose-Animationen im GameScreen — sie sind Widget-, nicht
  Partikel-Choreografie, und über den Compose-Test-Clock bereits
  deterministisch testbar (ADR-009). SFX-Auslösung (§13.9–§13.11)
  liegt beim ViewModel/Choreografen gegen `AudioEngine` (ADR-010),
  nicht im JuiceState — der Kern bleibt rein visuell.
- **Reduce-Motion (R44):** wirkt im Stepper (Partikelzahl 0, Flash →
  Fade-Parameter, Puls statisch); Zeitpunkte, Overlay-Frist und Audio
  unverändert. Leere Partikel-Snapshots sind gültiger Zustand (keine
  Division durch Partikelzahl o. Ä.).

## Konsequenzen

- (+) Ein einziger, auf JEDEM unterstützten Gerät identischer
  Renderpfad. Im bestehenden JVM-Gate abgesichert werden: strukturelle
  Render-Smoke-Tests (zeichnet ohne Crash, auch bei 0 Partikeln) und
  deterministische JuiceState-Goldens; Pixel-/Screenshot-Vergleiche
  erfordern eigenes Tooling und damit ein eigenes ADR (C8, Auslassung
  aus ADR-009) — die Optik nimmt Branko am Gate visuell ab.
- (+) Determinismus-Pflicht §13.13 ist als Vertrag fixiert (Seed-
  Funktion, Emitter-Indizes, Spawn-only-PRNG) — die Punkte PW-4.4–4.7
  und PW-4.9 arbeiten gegen dieselben Deklarationen.
- (+) Kein API-33-Sonderpfad, keine Shader-Kompilierung, keine
  Treiber-Varianz; R44/R49 sind reine Zustandslogik und damit trivial
  testbar.
- (−) Kein Per-Pixel-Bloom: der Glow ist auf Gradient-Optik begrenzt.
  Akzeptiert — die normative Optik verlangt nicht mehr, und Branko
  bewertet das Ergebnis am Gate ohnehin visuell.
- (−) Additiver Overdraw bleibt der Performance-Risikopunkt;
  Gegenmaßnahmen (Shader-Cache, ein Layer, SoA-Snapshot, Partikel-
  Kappen) sind oben verbindlich; Messpflicht in PW-4.9 mit
  Eskalationskriterium p95 < 55 fps → Folge-ADR (AGSL hinter der
  Renderer-Naht).
- (−) `JuiceState`-Snapshots werden pro Frame neu erzeugt (Immutability
  §13.13 schlägt Pooling im Step-Pfad); Allokationsfreiheit gilt
  verbindlich NUR für den Draw-Pfad. Bewusster Trade-off.
- **Plan-Überholung (10-Punkte-Plan, Punkt 5):** Der Prompt in
  docs/phase4-10-punkte-plan.md verlangt wörtlich „AGSL-Glow ab API 33,
  Radial-Gradient-Fallback darunter" und „Robolectric-Screenshots
  beider Pfade". Diese Vorgaben sind durch dieses ADR ÜBERHOLT: §13.12
  des Design-Dokuments delegiert die Technikentscheidung ausdrücklich
  an dieses ADR, und die Entscheidung lautet EIN Pfad, Canvas-only.
  Der Orchestrator passt den Punkt-5-Prompt beim Delegieren an
  (PW-4.5 implementiert nur den Canvas-Pfad; „beide Pfade"-Tests
  entfallen).
- Folgearbeit: PW-4.3 liefert die Ereignisdaten (ADR-012); PW-4.4
  implementiert den Stepper-Kern inkl. Determinismus-Tests (ohne
  Rendering); PW-4.5 Laser-Rendering; PW-4.6 Aktions-Feedback-
  Verdrahtung; PW-4.7 Feuerwerk/Sterne; PW-4.9 misst das Frame-Budget.
  (Ticket-Nummern: 10-Punkte-Plan — das ältere Schema aus
  docs/phase4-juice-update.md §3 gilt nicht mehr.)