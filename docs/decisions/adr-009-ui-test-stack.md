# ADR-009: UI-Test-Stack — Robolectric + Compose-UI-Tests auf der JVM

- Status: AKZEPTIERT
- Datum: 2026-07-11
- Autor: architect (Ticket PW-3.1, Phase-3-Fundament; löst den
  Backlog-Punkt „Robolectric + Compose-UI-Test-Setup für :app")
- Bezug: ADR-001 (Stack: „Compose Testing + Robolectric" bereits als
  Zielbild benannt), ADR-002 (Dependency Verification / S6), ADR-007
  (Turbine/Flow-Tests auch für :data relevant)

## Kontext

Phase 3 bringt die ersten Screens; die Quality Gates verlangen
UI-Tests „Robolectric/Compose, 100 % bestanden" und Coverage-Gates
für `:app`/`:data` (docs/plan.md §4.3: ≥ 70 % Line). Der Prozess ist
agent-getrieben: Tests müssen headless, deterministisch und ohne
Emulator laufen (`./gradlew check`).

**Ist-Zustand und Lücke:** Kover ist derzeit NUR auf `:game` (90) und
`:core` (85) angewandt; `:app` und `:data` haben weder Kover-Plugin
noch Verify-Regel — die 70-%-Gates aus plan.md §4.3 sind also noch
nicht scharf. `:app` hat außerdem keinerlei Test-Dependencies.
Ein sofortiges Scharfschalten würde `check` auf dem leeren Gerüst rot
machen (0 % Coverage auf Bestandscode) — die Aktivierung gehört daher
in die Tickets, die die ersten Tests bringen (siehe unten).

## Optionen

1. **Nur Instrumented Tests (Espresso auf Emulator/Gerät).** Für
   Agents unbrauchbar: Emulator-Infrastruktur in CI, langsam, flaky.
2. **Kein UI-Test-Framework, nur ViewModel-JVM-Tests.** ViewModels
   sind pure Kotlin und brauchen tatsächlich kein Robolectric — aber
   die Gate-Anforderung „UI-Tests" und die ui-entwickler-Regel 6
   (Compose-Tests je Screen) blieben unerfüllbar; Semantics/
   Interaktion (Tap→Intent, contentDescription §13.5) wären ungetestet.
3. **Robolectric + Compose-UI-Test (JVM).** `createComposeRule` läuft
   unter Robolectric headless; Semantics-Assertions und
   Interaktionstests ohne Emulator. Der von ADR-001/plan.md
   vorgezeichnete Weg.

## Entscheidung

**Option 3.** Neue Test-Dependencies (nur Test-Konfigurationen; Quellen
google()/mavenCentral; Security-Review folgt je Prozess):

| Koordinate | Quelle | Konfiguration | Zweck |
|---|---|---|---|
| `junit:junit:4.13.2` | mavenCentral | `:app` testImplementation | Robolectric ist ein JUnit4-Runner (JUnit 5 bleibt Standard in :game/:core/:data) |
| `org.robolectric:robolectric:4.15.1` | mavenCentral | `:app` testImplementation | Android-Framework auf der JVM |
| `androidx.compose.ui:ui-test-junit4` (BOM-verwaltet) | google() | `:app` testImplementation | `createComposeRule`, Semantics-/Interaktions-API |
| `androidx.compose.ui:ui-test-manifest` (BOM-verwaltet) | google() | `:app` debugImplementation | Test-Activity im Debug-Manifest |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` | mavenCentral | `:app`/`:data` testImplementation | `runTest`, TestDispatcher (bereits durch ADR-001 „kotlinx-coroutines (Test)" gedeckt, hier nur Katalog-Nachtrag) |
| `app.cash.turbine:turbine:1.2.0` | mavenCentral | `:app`/`:data` testImplementation | Flow-Assertions (plan.md §2-Stacktabelle; winzig, transitiv nur coroutines) |

`:app`-Testkonfiguration: `testOptions.unitTests.isIncludeAndroidResources = true`
(Robolectric-Ressourcenzugriff). Robolectric-Tests pinnen den
Emulations-SDK explizit per `@Config(sdk = [35])`, bis Robolectric
SDK 36 stabil unterstützt (Determinismus statt „neuester" SDK).

### S6-Auflage: Robolectric android-all-Artefakte (verbindlich)

Robolectric lädt seine `android-all-instrumented`-JARs standardmäßig
ZUR TESTLAUFZEIT selbst von Maven Central — am Gradle-Dependency-
Mechanismus und damit an der Dependency Verification (ADR-002, S6)
vorbei. Das ist in diesem Projekt nicht akzeptabel. Auflage für das
Umsetzungsticket (PW-3.3): Offline-Betrieb konfigurieren — die
benötigte `org.robolectric:android-all-instrumented:<sdk>`-Koordinate
als eigene Gradle-Konfiguration deklarieren (damit gepinnt in der
verification-metadata) und den Tests
`robolectric.offline=true` + `robolectric.dependency.dir` auf die
aufgelösten Dateien setzen. Kein Test-Task darf zur Laufzeit
ungeprüfte Artefakte nachladen.

### Testumfang Phase 3 (verbindlich für die UI-Tickets)

1. **ViewModel-/State-Holder-Tests: reine JVM-Tests OHNE Robolectric**
   (TestDispatcher + Turbine + Fakes). Robolectric nur, wo echtes
   Android-API/Compose-Runtime nötig ist — es ist der teurere Weg.
2. **Je Screen mindestens:** Rendering der Zustandsklassen (leer /
   laufend / gelöst; Fehlerzustand bei Persistenz-Failure) und die
   zentralen Interaktionen als Semantics-Tests (Tap auf Zelle ⇒
   Rotate-Intent; Undo/Reset; Kachel-Tap in der Levelauswahl).
   Assertions bevorzugt über Semantics/contentDescription — das testet
   §13.5 (TalkBack) gleich mit.
3. **Kein Screenshot-/Pixel-Testing in Phase 3** (Werkzeugentscheidung
   wäre ein eigenes ADR; Nutzen vor dem Art-Direction-Pass aus
   Phase 4 gering).
4. **Flaky-Verbot** (test-engineer-Regeln): keine Sleeps, `mainClock`/
   TestDispatcher statt echter Zeit, feste Seeds.

### Kover-Gates :app/:data — Schärfungsplan

| Modul | Gate (plan.md §4.3) | Aktivierung |
|---|---|---|
| `:data` | ≥ 70 % Line | Ticket PW-3.2 (Persistenz-Implementierung bringt die Tests mit; Kover-Plugin + Verify-Regel im selben PR) |
| `:app` | ≥ 70 % Line | Ticket PW-3.3 (App-Shell bringt Robolectric-Setup + erste Tests; Kover-Plugin + Verify-Regel im selben PR) |

Bis dahin gilt die Lücke als benannt und terminiert; PRs der beiden
Tickets, die das Gate NICHT scharf schalten, weist der code-reviewer
zurück (dieses ADR ist die Referenz). docs/architektur.md
(Quality-Gates-Tabelle) wird mit PW-3.1 entsprechend ergänzt.

## Konsequenzen

- (+) UI-Tests laufen headless in `./gradlew check`; kein Emulator,
  keine CI-Änderung nötig (bestehender `test`-Schritt deckt sie ab).
- (+) Coverage-Gates für alle vier Module haben einen konkreten,
  überprüfbaren Aktivierungsplan.
- (−) Robolectric ist ein großes Test-Artefakt mit eigener
  Android-Emulationsschicht (nur testRuntime; Release-APK unberührt).
  Die android-all-Pinnung kostet einmalig Konfigurationsaufwand —
  dafür bleibt S6 ohne Ausnahme.
- (−) Zwei JUnit-Welten im Repo (JUnit 5 in :game/:core/:data,
  JUnit 4 nur in :app für Robolectric) — bewusst lokal begrenzt;
  Merkregel im Test-Setup dokumentieren.
- (−) Robolectric hinkt neuen Android-SDKs hinterher (`@Config(sdk=[35])`
  bei targetSdk 36); beim jährlichen targetSdk-Bump (Backlog PW-0.5)
  ist die Robolectric-Version mitzuprüfen.
