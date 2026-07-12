# Phase 3 — Ticket-Schnitte (Delegationsgrundlage)

> Erstellt vom Architekt (PW-3.1). Grundlage: docs/plan.md §7 Phase 3,
> docs/game-design.md §11–§13, ADR-006–009, docs/ui-architektur.md.
> Der Orchestrator formuliert daraus die vollständigen Briefings.
> Phase-3-Gate: APK auf Gerät installierbar, ein Level von Anfang bis
> Ende spielbar.

## Übersicht und Reihenfolge

```
PW-3.2 (:data + :game-Mini)   ─┐
PW-3.3 (:app App-Shell)       ─┼─ parallel (disjunkte Dateimengen)
PW-3.4 (:app Spielfeld-Canvas)─┘
        │
PW-3.5 (:app Spiel-Screen interaktiv)   ← braucht 3.2 + 3.3 + 3.4
PW-3.6 (:app Levelauswahl + Home-Verdrahtung) ← braucht 3.2 + 3.3;
        parallel zu 3.5 möglich (Merge-Punkt: Screen-Registrierung
        im Navigation-Root — sequenziell mergen)
        │
PW-3.7 (Integration + Gate-Nachweis)    ← letzter Schritt
```

Agents: PW-3.2 = entwickler; PW-3.3–3.6 = ui-entwickler;
PW-3.7 = ui-entwickler + test-engineer. Jeder PR ≤ ~400 Zeilen
produktiver Code — reißt ein Ticket die Grenze, zerlegen und melden.

---

## PW-3.2 — Persistenz-Implementierung (:data) + Progression-Helfer (:game)

- **Module:** `:data` (Schwerpunkt), `:game` (Mini-Erweiterung)
- **Design/ADRs:** ADR-007 (bindend: Envelope+Version, Entry-Arrays,
  Korruptions-/Versionssemantik), §7.2, §10.3, §11.2, §12.5;
  API-Verträge: `de.puzzlewerk.data.{PersistenceResult,progress,daily,settings}`
- **Inhalt:**
  1. DataStore-Serializer (kotlinx.serialization-JSON, Envelope v1)
     + Migrationsgerüst; DTOs und Mapper DTO ↔ Domäne (`Score` aus
     :game) inkl. Wertebereichs-Checks und explizitem Duplikat-Check.
  2. Implementierungen `DataStoreProgressRepository`,
     `DataStoreDailyStatsRepository`, `DataStoreSettingsRepository`
     gegen die vom Architekten gelieferten Interfaces (PW-3.1) —
     Interfaces sind FIX; Unklarheiten eskalieren, nicht umdeuten.
  3. In-Memory-Fakes der drei Repositories (für :app-Tests/Previews,
     ersetzt den in PW-3.1 entfernten Phase-0-Platzhalter).
  4. :game: `level/Progression.kt` mit exakt dieser API:
     ```kotlin
     /** Freischaltregel §11.2: Level n ist spielbar gdw. n ≤ höchstesGelöstes + 3. */
     public fun isLevelUnlocked(levelNumber: Int, highestSolvedLevel: Int): Boolean
     ```
     (pure Funktion; `levelNumber` außerhalb 1..50 ⇒ false;
     `highestSolvedLevel` 0 = kein Fortschritt; Unit-Tests §11.2:
     anfangs 1–3 offen).
  5. Kover-Plugin + Verify-Regel ≥ 70 % Line auf `:data` scharf
     schalten (ADR-009 — Pflichtbestandteil dieses PRs).
- **Tests:** JUnit 5 + Turbine + coroutines-test; Golden-Dateien für
  v1-Schema; Korruptions-/Versions-/Duplikat-Fälle als Werte (R43-
  analog); Streak-Semantik §10.3 (Serie, verpasster Tag, R38-Idempotenz,
  Datum rückwärts).
- **Gehört NICHT dazu:** UI, Level-Asset-Format (Phase 4), Änderungen
  an den Interfaces, Room.

## PW-3.3 — App-Shell: Composition Root, Theme, Navigation, Home, Test-Setup

- **Module:** `:app`
- **Design/ADRs:** ADR-006, ADR-008, ADR-009 (bindend inkl.
  android-all-Offline-Pinnung!), §12.1, §12.2, §13.4;
  docs/ui-architektur.md §1–§3, §6–§8
- **Inhalt:**
  1. `PuzzlewerkApplication` + `AppContainer` + ViewModel-Factory
     (ADR-006); Manifest-Eintrag (`android:name`, exported bleibt wie
     gehabt, S5).
  2. Theme: dunkle Palette §13.4, Typografie, Edge-to-Edge-Insets.
  3. Navigation: `Screen`-sealed-interface + Backstack-Holder + Saver
     + BackHandler (ADR-008), mit Unit-/Robolectric-Tests
     (Konfigurationswechsel, Prozess-Tod-Restauration, Back-Pop).
  4. `HomeScreen` + `HomeViewModel` (§12.2): Weiter /
     Tägliches Prisma (Phase 3: deaktiviert mit Badge „bald") /
     Levelauswahl / Einstellungen (Phase 3: deaktiviert); Zustände
     Erststart / normal / alles gelöst.
  5. Robolectric + Compose-Test-Setup gemäß ADR-009 inkl.
     `robolectric.offline`-Pinnung; Kover ≥ 70 % Line auf `:app`
     scharf (Pflichtbestandteil).
- **Abhängigkeiten:** keine (Fakes aus PW-3.2 nice-to-have; bis dahin
  eigene Test-Fakes im Ticket erlaubt, da Interfaces fix sind).
- **Gehört NICHT dazu:** Spielfeld, Levelauswahl-Grid, echte
  Daily-/Settings-Screens.

## PW-3.4 — Spielfeld-Rendering (statisch): BoardCanvas

- **Module:** `:app` (nur `ui/game/`, keine Berührung mit 3.3-Dateien
  außer Theme-Konsum)
- **Design:** §2.4 (Pixel-Abbildung), §12.3 (Darstellung), §13.1–§13.4
  (Symbole, Strahlmuster, Zustände, Palette);
  docs/ui-architektur.md §5
- **Inhalt:** `BoardCanvas` als reine State→Pixel-Funktion: Hex-Raster,
  alle 8 Elementtypen, Strahl-Segmente aus `TraceResult` (Farben +
  Muster §13.2), Kristallzustände (dunkel/teilerfüllt/erfüllt/
  übersättigt, §12.3), Drehbarkeits-Markierung (Sockelring).
  Fake-States als `@Preview`s (mind. Beispiel-Level §7.3).
  KEIN ViewModel, KEINE Eingabe — Input ist ein UiState-Parameter.
- **Tests:** Robolectric-Compose: Semantics je Zelle (§13.5-Muster),
  Zustandsdarstellung; Geometrie-Roundtrip (Pixel↔Axial) als
  JVM-Unit-Test.
- **Gehört NICHT dazu:** pointerInput, Animationen, GameViewModel.

## PW-3.5 — Spiel-Screen interaktiv: GameViewModel + Eingabe + Overlay

- **Module:** `:app` (`ui/game/`)
- **Design:** §6 (Züge/Undo/Reset), §12.3 (Interaktion, Overlay),
  §7 (Score im Overlay); docs/ui-architektur.md §2–§5
- **Inhalt:** `GameViewModel` (MVI: `GameUiState`/`GameIntent`/
  `GameEffect`), Levelladen über `generateLevel(campaignSeed(n), tier)`
  off-main mit Ladezustand, Tap→`Rotate` inkl. Puffer während
  Animation, Undo/Reset (Reset-Bestätigung ab ≥ 5 Zügen), Kopfzeile
  („Züge X · Par Y"), Ergebnis-Overlay (Sterne/Score/Weiter/Nochmal/
  Zurück), Speichern via `ProgressRepository.recordSolved` (nur
  Kampagne; Daily Phase 4), Invalid-Feedback als Effect.
- **Abhängigkeiten:** PW-3.2 (Repository + Fakes), PW-3.3 (Shell),
  PW-3.4 (Canvas). Voraussichtlich > 400 Zeilen ⇒ von vornherein als
  zwei PRs planen (a: ViewModel+Intents+Tests, b: Screen+Overlay+
  Verdrahtung).
- **Gehört NICHT dazu:** Daily-Wertung, Sound-Assets (Haptik/Sound nur
  als Effect-Typen + No-op-Senke).

## PW-3.6 — Levelauswahl + Home-Verdrahtung „Weiter"

- **Module:** `:app` (`ui/levelselect/`, kleine Ergänzung Home/Root)
- **Design:** §11.2/§11.3, §12.4; :game-Helfer `isLevelUnlocked`
  (PW-3.2)
- **Inhalt:** `LevelSelectViewModel` + Grid (50 Kacheln: gesperrt /
  offen / gelöst mit Sternen+Score; Kopf: Gesamtsterne/-score),
  Kachel-Tap → `Screen.Game(Campaign(n))`; Home-„Weiter" =
  niedrigstes ungelöstes Level; Fehlerzustand bei
  `PersistenceFailure` (definierte Meldung, R43-Geist).
- **Abhängigkeiten:** PW-3.2, PW-3.3. Parallel zu PW-3.5 möglich;
  Merge-Punkt Navigation-Root beachten.
- **Achtung / Blocker für Level > 3:** §11.3 nennt für die Bereiche
  4–8, 27–31 und 37–41 Tier-SPANNEN (z. B. „D1–D2") — die exakte
  Level→Tier-Zuordnung ist im Design offen und ist eine
  game-designer-Entscheidung (Eskalation ist bereits im
  PW-3.1-Abschlussbericht vermerkt). Bis zur Klärung startet die
  Levelauswahl nur eindeutige Tiers (1–3, 9–26, 32–36, 42–50);
  fürs Phase-3-Gate genügt Level 1–3.

## PW-3.7 — Integration + Phase-3-Gate-Nachweis

- **Module:** `:app` (Kleinigkeiten), Doku
- **Inhalt:** End-to-End-Smoke als Robolectric-Test (Home → Auswahl →
  Level 1 lösen → Overlay → Weiter), `assembleDebug`-APK als
  Gate-Artefakt, manuelle Checkliste fürs menschliche Gate
  (Installation, ein Level durchspielen, TalkBack-Stichprobe §13.5),
  docs/status.md-Update. Danach unabhängiger test-engineer-Durchlauf
  gegen §12/§13-Akzeptanzkriterien.
- **Abhängigkeiten:** alle vorherigen.

---

## Querschnitts-Regeln für alle Phase-3-Tickets

- Interfaces und ADR-Entscheidungen aus PW-3.1 sind fix; Abweichungs-
  bedarf ⇒ Eskalation an den Orchestrator (nie stilles Umbauen).
- Neue Dependencies sind mit ADR-007/ADR-009 abschließend freigegeben
  und bereits im Version Catalog + verification-metadata — Tickets
  fügen KEINE weiteren hinzu. Einzige erwartete Ausnahme:
  `org.robolectric:android-all-instrumented` in PW-3.3 (von ADR-009
  gedeckt; verification-metadata dort regenerieren, Kommando siehe
  docs/architektur.md).
- Vor jedem PR: volle Gate-Kette (Verbindliche Kommandos,
  docs/architektur.md).
