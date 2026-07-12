# ADR-008: Navigation — eigener sealed-Screen-Backstack statt Compose Navigation

- Status: AKZEPTIERT
- Datum: 2026-07-11
- Autor: architect (Ticket PW-3.1, Phase-3-Fundament)
- Bezug: ADR-006 (manuelle DI — dieselbe Abwägungslogik), ADR-001
  (Stack)

## Kontext

Die App hat laut docs/game-design.md §12.1 genau fünf Screens in einem
flachen, statischen Graphen: Home → {Levelauswahl, Daily-Screen,
Einstellungen} und von dort in den einen Spiel-Screen. Es gibt keine
Deeplinks (S1/S5: eine nicht-exportierte Launcher-Activity, keine
Intent-Filter), keine verschachtelten Graphen, keine Bottom-Navigation,
keine Prozess-übergreifenden Navigationsziele. Zu behandeln sind:
Zurück-Navigation (inkl. Predictive Back, ab targetSdk 35 relevant,
siehe Backlog-Eintrag targetSdk 36) und Zustandserhalt über
Konfigurationswechsel/Prozess-Tod (`rememberSaveable`).

## Optionen

1. **Jetpack Compose Navigation** (`androidx.navigation:
   navigation-compose`). Der Standard für große Apps: typisierte Routen
   (2.8+ mit kotlinx.serialization), Deeplinks, Transitions,
   SavedState-Integration. Kosten: neue Dependency-Familie
   (navigation-compose, -runtime, -common + transitive), C8/S6-Prozess
   (verification-metadata, Security-Review, Renovate), Routen-
   Serialisierung und eine zweite Navigations-Semantik, die Agents und
   Reviewer beherrschen müssen. Der Hauptnutzen — Deeplinks und große
   Graphen — hat in dieser App per Design KEINEN Abnehmer.
2. **Eigener Backstack als `List<Screen>`** über einem
   `sealed interface Screen`. Navigation = Listenoperation (push/pop)
   in einem kleinen State-Holder im App-Root; System-Back über den
   vorhandenen `BackHandler` (androidx.activity, bereits Dependency);
   Persistenz über `rememberSaveable` mit einem eigenen `Saver`
   (Screens sind kleine Wertobjekte). Predictive Back: der
   `BackHandler`/`onBackPressedDispatcher`-Pfad der aktuellen
   androidx.activity unterstützt den Systemhandshake; die animierte
   Vorschau (Predictive-Back-Animation zwischen Screens) ist damit
   nicht gratis — für V1 akzeptiert (System-Zurück funktioniert
   korrekt, Animation ist Politur).

## Entscheidung

**Option 2: eigener sealed-Screen-Backstack, keine neue Dependency.**
Für fünf statische Screens ist Compose Navigation Infrastruktur ohne
Problem — dieselbe C8-Abwägung wie bei DI (ADR-006). Die gesamte
Lösung ist < 50 Zeilen, vollständig compilergeprüft (`when` über
`sealed` ist exhaustiv: ein neuer Screen erzwingt Behandlung an jeder
relevanten Stelle) und trivial unit-testbar.

Verbindliche Form (Details in docs/ui-architektur.md):

```kotlin
sealed interface Screen {                    // :app, ui/navigation
    data object Home : Screen
    data object LevelSelect : Screen
    data class Game(val request: LevelRequest) : Screen
    data object Daily : Screen               // Phase 4
    data object Settings : Screen            // Phase 4
}
```

- Der Backstack lebt als `rememberSaveable`-State (eigener `Saver`) im
  Wurzel-Composable; Root ist immer `Home`.
- `BackHandler(enabled = backstack.size > 1)` poppt; auf `Home` greift
  das System-Default (App verlassen).
- Screen-Wechsel sind Intents an den Root-State-Holder — kein
  Composable navigiert „selbst", ViewModels kennen den Backstack nicht
  (sie emittieren Effects wie `NavigateToNextLevel`, die der Root
  übersetzt).

Revisions-Trigger (neues ADR): Deeplinks/App-Shortcuts, verschachtelte
Graphen oder mehr als ~8 Screens; ebenso, falls Predictive-Back-
Screen-Animationen zur Produktanforderung werden (dann Neubewertung
von Navigation Compose, das dies eingebaut liefert).

## Konsequenzen

- (+) 0 neue Dependencies; Navigationslogik ist gewöhnlicher,
  exhaustiv geprüfter Kotlin-Code mit Unit-Tests.
- (+) Kein Routen-String/Serialisierungs-Layer; Argumente (z. B.
  Levelnummer) sind typisierte Konstruktorfelder des Screen-Objekts.
- (−) SavedState-Handling (Saver für den Backstack) ist Eigencode —
  klein, aber im UI-Shell-Ticket mit Tests zu belegen
  (Konfigurationswechsel + Prozess-Tod-Restauration).
- (−) Keine Predictive-Back-Vorschauanimation zwischen Screens in V1
  (System-Back funktioniert vollständig; Politur ggf. Phase 4+).
- (−) Sollte die App wachsen (Trigger oben), ist eine spätere Migration
  auf Navigation Compose mechanisch (Screens sind bereits typisierte
  Ziele), aber nicht kostenlos.
