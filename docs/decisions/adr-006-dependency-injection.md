# ADR-006: Dependency Injection — manuelle Konstruktor-Injektion statt Framework

- Status: AKZEPTIERT
- Datum: 2026-07-11
- Autor: architect (Ticket PW-3.1, Phase-3-Fundament)
- Bezug: ADR-001 (Stack; DI dort offen gelassen — docs/plan.md §2 nennt
  „Koin oder Hilt" als unentschiedenen Vorschlag), ADR-004
  (Schichtenmodell), ADR-005 (Sperrliste: `com.google.` in :game/:core —
  beträfe Hilt/Dagger dort ohnehin)

## Kontext

Phase 3 verdrahtet erstmals alle Schichten: ViewModels in `:app`
brauchen `GameEngine`, `LevelGenerator`, `Tracer`, `ScoreCalculator`
(alles `:game`), die Repositories aus `:data` sowie `WallClock` und
`RandomSource` aus `:core`. Die Frage ist, WIE diese Objektgraphen
entstehen.

Der tatsächliche Umfang des Graphen (V1, vollständig):

- **genau eine Activity** (`MainActivity`, Regel S5), keine Services,
  keine Receiver, keine WorkManager-Worker;
- **~5 ViewModels** (Game, Home, LevelSelect, Daily, Settings — siehe
  docs/ui-architektur.md);
- **3 Repositories** (`:data`), 4–5 zustandslose `:game`-Defaults,
  2 `:core`-Objekte;
- **keine Scopes** außer „App-Singleton" und „ViewModel-Lebensdauer";
- **kein Multibinding, keine Qualifier, keine Assisted Injection**
  (Parameter wie die Levelnummer laufen als Intent/Argument, nicht als
  Konstruktor-Injection).

Alle Klassen nutzen bereits Konstruktor-Injektion als Stil (Regel C2:
`WallClock`/`RandomSource` werden injiziert) — entschieden wird hier nur
der MECHANISMUS, der die Konstruktoren aufruft.

Prozesskosten jeder neuen Dependency in diesem Projekt (C8/S6):
Version-Catalog-Eintrag + ADR, Security-Review durch den
security-auditor, Pflege der `verification-metadata.xml` (jede
transitive Komponente wird einzeln SHA-256-gepinnt), Renovate-Rauschen,
und bei Codegen zusätzlich Build-Zeit und eine weitere Fehlerklasse in
CI-Reports, die Agents interpretieren müssen.

## Optionen

1. **Hilt** (`com.google.dagger:hilt-android` + Compiler via KSP).
   Compile-time-validiert und Android-integriert, aber: zieht Dagger,
   javapoet, KSP-Prozessor und das Hilt-Gradle-Plugin (Bytecode-
   Transformation der Activity-Basisklasse!) — die mit Abstand größte
   transitive und Build-Fläche der drei Optionen. KSP müsste als
   weiteres Build-Plugin gepinnt und versionssynchron zu Kotlin gehalten
   werden. Der Nutzen (Validierung großer Graphen, Scope-Verwaltung über
   viele Komponenten) hat hier keinen Abnehmer: Es gibt einen einzigen
   Scope-Übergang (App → ViewModel). Zudem liegt `com.google.dagger` im
   per ADR-005 für `:game`/`:core` gesperrten Namensraum — dort dürfte
   Hilt nie hin; Fakes für `:game`-Tests blieben ohnehin manuell.
2. **Koin** (`io.insert-koin:koin-androidx-compose`). Leichter als Hilt
   (kein Codegen), aber Laufzeit-DI: Fehler im Graphen erscheinen erst
   beim Start bzw. in einem extra zu pflegenden `checkModules`-Test —
   eine schwächere Garantie, als sie der Kotlin-Compiler bei
   Konstruktoraufrufen gratis gibt. Kostet trotzdem den vollen
   C8/S6-Prozess (mehrere Artefakte: core, android, compose) und führt
   eine DSL ein, die jeder Agent zusätzlich zu den Projektregeln lernen
   und der code-reviewer prüfen muss (Service-Locator-Missbrauch,
   `get()`-Aufrufe in Composables, …).
3. **Manuelle Konstruktor-Injektion** (Composition Root). Eine Klasse
   `AppContainer` in `:app` instanziiert den gesamten Graphen; die
   einzige Activity erreicht ihn über die `Application`-Instanz;
   ViewModels entstehen über eine gemeinsame
   `ViewModelProvider.Factory`. 0 Dependencies, 0 Codegen, 0 Laufzeit-
   magie; der Compiler validiert den Graphen vollständig; „ein fehlender
   Parameter" ist ein gewöhnlicher Kotlin-Fehler. Kosten: ~50–80 Zeilen
   Verdrahtungscode, die bei jedem neuen Objekt von Hand wachsen, und
   Disziplin, dass NUR die Composition Root konstruiert (Review-Regel).

## Entscheidung

**Option 3: manuelle Konstruktor-Injektion mit einer Composition Root
in `:app`.** Ein DI-Framework ist bei einer Activity, fünf ViewModels
und einem einstufigen Scope-Modell Infrastruktur ohne Problem — nach
der C8-Faustregel („Können wir das in 50 Zeilen selbst schreiben?") ist
die Antwort hier ein klares Ja, und zwar wörtlich.

Verbindliche Struktur (Details in docs/ui-architektur.md):

- `PuzzlewerkApplication : Application` hält genau eine
  `AppContainer`-Instanz (App-Singleton-Scope).
- `AppContainer` ist die EINZIGE Stelle, die Produktions-
  implementierungen (`DefaultGameEngine`, `DefaultLevelGenerator`,
  DataStore-Repositories, `SystemWallClock`, …) konstruiert. Er
  exponiert ausschließlich die Interfaces.
- ViewModels erhalten ihre Abhängigkeiten per Konstruktor über eine
  Factory (`viewModel(factory = …)`); Composables kennen weder
  Container noch Factory-Interna (State-Hoisting, docs/ui-architektur.md).
- Tests umgehen den Container vollständig: Sie rufen Konstruktoren
  direkt mit Fakes auf — genau wie in `:game`/`:data` bereits üblich.

Revisions-Trigger (neues ADR, das dieses referenziert): mehr als ein
Scope-Übergang (z. B. Feature-Module mit eigener Lebensdauer), mehr als
~10 ViewModels, oder wiederkehrende Review-Befunde über Konstruktion
außerhalb der Composition Root. Erst dann kauft ein Framework echten
Mehrwert.

## Konsequenzen

- (+) 0 neue Dependencies/Plugins, keine verification-metadata-Pflege,
  kein Security-Review-Aufwand, keine Codegen-Fehlerklasse in CI.
- (+) Vollständige Compile-Time-Sicherheit des Objektgraphen durch den
  Kotlin-Compiler; für Agents die am einfachsten nachvollziehbare Form
  („folge dem Konstruktor").
- (+) Testbarkeit unverändert maximal: alles ist ein Interface mit
  Konstruktorparameter.
- (−) Verdrahtung wächst manuell; jede neue Abhängigkeit ist eine
  Zeile im `AppContainer` (bewusst: sichtbar im Diff statt implizit in
  einer Annotation).
- (−) Kein automatisches Scoping — bei heutiger App-Größe gibt es
  nichts zu scopen; Trigger für Neubewertung sind oben definiert.
- Review-Regel für den code-reviewer: Konstruktion von Produktions-
  implementierungen außerhalb von `AppContainer` (oder Previews/Tests)
  ist ein MAJOR-Befund.
