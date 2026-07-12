# UI-Architektur — :app (verbindlich ab Phase 3)

Verbindliche Konventionen für alle UI-Arbeit in `:app`. Gepflegt vom
Architekt-Agent; Grundlage: docs/architektur.md, docs/game-design.md
§12–§13, ADR-006 (DI), ADR-007 (Persistenz), ADR-008 (Navigation),
ADR-009 (UI-Tests). Der code-reviewer prüft PRs gegen dieses Dokument.

## 1. Paketlayout in :app

```
de.puzzlewerk.app
├── PuzzlewerkApplication.kt      Application, hält AppContainer
├── MainActivity.kt               einzige Activity (S5)
├── di/AppContainer.kt            Composition Root (ADR-006)
├── ui/
│   ├── theme/                    Farben (§13.4), Typografie, Theme
│   ├── navigation/               Screen (sealed), Backstack-Holder (ADR-008)
│   ├── home/                     HomeScreen + HomeViewModel + MVI-Typen
│   ├── levelselect/              LevelSelectScreen + ViewModel + MVI-Typen
│   ├── game/                     GameScreen, BoardCanvas, GameViewModel …
│   ├── daily/                    (Phase 4)
│   └── settings/                 (Phase 4)
```

Ein Screen-Paket enthält: `XxxScreen.kt` (Composables),
`XxxViewModel.kt`, `XxxUiState.kt` (UiState + Intent + Effect in einer
Datei, solange ≤ 300 Zeilen, C4).

## 2. MVI-Verdrahtung (Muster, verbindliche Namen)

Je Screen exakt diese Typen und Signaturen:

```kotlin
data class GameUiState(…)              // immutable; einzige Wahrheit des Screens
sealed interface GameIntent            // Nutzerabsichten: TapCell(coord), Undo, Reset, …
sealed interface GameEffect            // Einmal-Ereignisse: Haptic, PlaySound, NavigateNext, …

class GameViewModel(…) : ViewModel() {
    val state: StateFlow<GameUiState>          // via MutableStateFlow.asStateFlow()
    val effects: Flow<GameEffect>              // Channel(BUFFERED).receiveAsFlow()
    fun onIntent(intent: GameIntent)           // EINZIGER Eingang
}
```

Regeln:

- **Unidirektional ohne Ausnahme:** Composables lesen `state`
  (`collectAsStateWithLifecycle`) und rufen `onIntent` — sonst nichts.
  Kein `remember` für Spieldaten; `remember` nur für rein Visuelles
  (Animations-Progress, vorberechnete Geometrie).
- **Effects sind einmalige Seiteneffekte** (Haptik, Sound, Navigation).
  Alles, was Zustand ist, gehört in den UiState — im Zweifel UiState.
- **UiState ist dumm:** vorformatierte, render-fertige Daten. Ableitungen
  aus dem Domänenzustand macht das ViewModel (bzw. `:game`), nie das
  Composable.
- **Keine Spiellogik in :app** (CLAUDE.md): fehlt in `:game` etwas
  (z. B. eine Regel-Funktion), wird eskaliert, nicht nachgebaut.
  Konkret liegt in `:game`: jede Zug-/Trace-/Score-Semantik, die
  Freischaltregel §11.2, die Level→(seed, tier)-Abbildung.

## 3. ViewModel-Zuschnitt

| ViewModel | Screen | Kern-Abhängigkeiten (Konstruktor) | Phase |
|---|---|---|---|
| `GameViewModel` | Spiel (Kampagne UND Daily, §12.3 identisch) | `GameEngine`, `LevelGenerator`, `ScoreCalculator`, `ProgressRepository`, `DailyStatsRepository`, `CoroutineDispatcher` | 3 |
| `HomeViewModel` | Home (§12.2) | `ProgressRepository`, (`DailyStatsRepository` ab Phase 4) | 3 |
| `LevelSelectViewModel` | Levelauswahl (§12.4) | `ProgressRepository` | 3 |
| `DailyViewModel` | Daily-Screen (§12.4) | `DailyStatsRepository`, `WallClock` | 4 |
| `SettingsViewModel` | Einstellungen (§12.5) | `SettingsRepository`, `ProgressRepository`, `DailyStatsRepository` (Reset) | 4 |

- ViewModels sind konstruktor-injiziert (ADR-006) und rein
  JVM-testbar: keine Android-Typen außer `androidx.lifecycle.ViewModel`
  selbst; `CoroutineDispatcher` wird injiziert (Default:
  `Dispatchers.Default`), Tests nutzen `StandardTestDispatcher`.
- Welche Partie `GameViewModel` spielt, bestimmt `Screen.Game(request)`
  (ADR-008): `LevelRequest.Campaign(levelNumber)` bzw.
  `LevelRequest.Daily(epochDay)` — Übergabe über die ViewModel-Factory,
  keine globale Ablage.
- Levelgenerierung (Generator + Par-Solver, bis ~1 s, §9.4) läuft im
  ViewModel auf dem injizierten Dispatcher — nie auf Main; der UiState
  hat dafür einen Ladezustand.

## 4. Wo lebt der Tracer?

**Nirgends in :app.** `DefaultGameEngine` wertet `trace` selbst bei
`newGame` und jedem `applyMove` aus; `MoveResult.Applied` enthält das
frische `TraceResult` (§12.3: Strahlen nach JEDEM Zug sofort).
Verbindlich:

- Composables und ViewModels rufen NIEMALS `Tracer` direkt auf.
- Das ViewModel übersetzt `MoveResult` → `GameUiState` (Segmente,
  Kristallzustände, Zugzähler, Gelöst-Overlay). `MoveResult.Invalid`
  wird zu einem Effect (Wackeln/Ton, §12.3) — der State bleibt gleich.

## 5. Canvas-Rendering (Spielfeld)

Ein einziges `BoardCanvas`-Composable zeichnet das Brett aus dem
UiState (§3.3 plan.md). Verbindliche Regeln:

1. **Keine Allokationen im Draw-Pfad.** Im `DrawScope` (bzw.
   `Canvas {}`-Lambda) werden keine Objekte erzeugt: keine `Path`-,
   `List`-, `Brush`-, `String`-Neuanlagen pro Frame. Wiederverwendbare
   `Path`-/Paint-Objekte und die Hex-Geometrie (Eckpunkte,
   Zellzentren nach §2.4: `x = size·√3·(q + r/2)`, `y = size·1.5·r`)
   werden EINMAL je (Brettradius, Canvas-Größe) berechnet und
   `remember`t; Strahl-Segmente werden beim State-Update in
   Zeichenlisten übersetzt, nicht beim Zeichnen.
2. **Keine Logik im Draw-Pfad:** kein Trace, keine Farbmischung, keine
   Regelauswertung — nur Abbildung vorberechneter Daten auf Pixel.
3. **Eingabe:** `pointerInput` übersetzt die Tap-Position über die
   inverse Pixel-Abbildung (+ Rundung auf Axial-Koordinaten) in
   `GameIntent.TapCell(HexCoord)`. Die Entscheidung „drehbar oder
   nicht" trifft die Engine (R27), nicht der Eingabecode.
4. **Stabilität:** UiState-Typen sind `data class`es aus immutablen
   Strukturen; abgeleitete Werte über `derivedStateOf`. Recomposition-
   Hygiene ist Review-Gegenstand (ui-entwickler-Regel 5).
5. **Animationen** (Dreh-Animation ~150 ms, §12.3): Compose
   `Animatable`; Eingaben während der Animation werden gepuffert und
   der Reihe nach angewandt — die Logik ist sofort fertig, nur die
   Optik läuft nach. System-Einstellung „Animationen entfernen"
   respektieren (§13.6).

## 6. Navigation

Eigener sealed-Screen-Backstack, KEINE Navigation-Dependency —
Begründung und verbindliche Form in **ADR-008**. Kurzfassung:
`List<Screen>` als `rememberSaveable`-State im Wurzel-Composable,
`BackHandler` poppt, ViewModels emittieren Navigations-Effects, nur
der Root navigiert.

## 7. Composition Root (DI)

Manuelle Konstruktor-Injektion — Begründung und Struktur in
**ADR-006**. Kurzfassung: `PuzzlewerkApplication` → `AppContainer`
(einzige Stelle, die Produktions-Implementierungen baut) →
ViewModel-Factory. Previews und Tests bauen ihre Objekte direkt mit
Fakes; Konstruktion von Produktionsimplementierungen außerhalb des
Containers ist ein Review-MAJOR.

## 8. Pflichten aus Design und Plattform (Checkliste je Screen-PR)

- **Barrierefreiheit (§13, Pflicht):** contentDescription je Zelle nach
  §13.5-Muster; Touch-Targets ≥ 48 dp; Zustand nie nur über Farbe
  (Symbole §13.1 sind Teil des Spielfeld-Renderings, nicht optionale
  Politur); Kontraste WCAG AA gegen die Palette §13.4.
- **Texte:** ausschließlich `strings.xml` (values = Deutsch,
  values-en = Englisch). Keine Hardcoded Strings, auch nicht in
  contentDescriptions.
- **Edge-to-Edge/Insets:** targetSdk 36 erzwingt Edge-to-Edge —
  Insets über `Modifier.safeDrawingPadding()`/`windowInsetsPadding`
  behandeln; kein Inhalt unter Systemleisten verlieren (Backlog
  targetSdk-Eintrag).
- **Preview:** jeder Screen hat `@Preview` mit Fake-State (ohne
  ViewModel/Container).
- **Tests:** Umfang und Werkzeuge gemäß ADR-009 (ViewModel = JVM-Test,
  Screen = Robolectric-Compose-Test).
- **dp/sp statt px;** Landscape und kleine Displays nicht kaputt
  (R = 4-Bretter: Pinch-Zoom + Pan erst mit Phase 4 relevant, §13.6).
