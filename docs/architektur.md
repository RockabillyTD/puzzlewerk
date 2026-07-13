# Architektur — Projekt Puzzlewerk

Verbindliche Architektur-Referenz für alle Agents. Änderungen nur durch
den Architekt-Agent per ADR (docs/decisions/).

## Schichtenmodell

Vier Gradle-Module, Abhängigkeiten zeigen ausschließlich nach unten
(präzisiert in ADR-004):

```
:app   (UI: Compose-Screens, ViewModels, Navigation, Theme)
  │ nutzt :data, :game, :core
:data  (Daten: Repositories, DataStore + kotlinx.serialization, ADR-007)
  │ nutzt :game, :core — mappt DTOs auf :game-Domänentypen,
  │ validiert Eingelesenes mit der :game-Validierungslogik (S4)
:game  (Domain: Spiellogik — REINES Kotlin/JVM-Modul, KEIN Android-Import)
  │ nutzt :core
:core  (Basis: RandomSource, WallClock, gemeinsame Utilities)
```

- `:game` ist das Herzstück: alle Spielregeln als pure Functions,
  vollständig JVM-testbar, deterministisch über injizierte Seeds.
- Erlaubte Kanten (abschließende Whitelist, ADR-004):
  `:app → :data, :game, :core` · `:data → :game, :core` ·
  `:game → :core` · `:core → nichts`. Jede neue Kante und jedes neue
  Modul braucht ein neues ADR.
- Invarianten (ADR-004): `:game` referenziert NIEMALS `:data` oder
  `:app`; `:core` referenziert kein anderes Modul; `:game`/`:core`
  bleiben ohne Android-Plugin. Maschinelle Prüfung: Gradle-Task
  `checkModuleGraph` (Spezifikation in ADR-004, Koordinaten-Sperrliste
  erweitert in ADR-005).
- Muster: MVI mit unidirektionalem Datenfluss. Composables lesen einen
  immutablen `UiState` (StateFlow im ViewModel) und senden Intents;
  Seiteneffekte laufen als explizite Effects. Verbindliche
  UI-Konventionen (Namen, ViewModel-Zuschnitt, Canvas-Regeln):
  **docs/ui-architektur.md**.

## Dependency Injection (ADR-006)

Manuelle Konstruktor-Injektion, KEIN DI-Framework (kein Koin, kein
Hilt). Composition Root ist `AppContainer` in `:app`
(`PuzzlewerkApplication` hält die eine Instanz); ViewModels entstehen
über eine gemeinsame Factory. Nur der Container konstruiert
Produktions-Implementierungen; Tests/Previews bauen direkt mit Fakes.

## Persistenz (ADR-007)

Typisierter `androidx.datastore` + kotlinx.serialization-JSON, KEIN
Room, KEIN Protobuf. Drei Stores (Fortschritt, Daily-Statistik,
Einstellungen), je versioniertes Envelope-Schema
(`{"version": N, "payload": …}`) mit verketteten, golden-getesteten
Migrationen. Korruption/Versionskonflikt sind Werte
(`PersistenceFailure`), nie Crash, nie stilles Überschreiben (Ausnahme
Settings → Defaults). Maps werden als Entry-Arrays serialisiert;
Duplikat-Schlüssel prüft der Mapper explizit (§16.2/2). JSON strikt:
`ignoreUnknownKeys = false` (S4).

## Clean-Code-Regeln (C1–C8)

- **C1 Immutability zuerst:** `val` statt `var`, `data class` + `copy()`,
  `List` statt `MutableList` in öffentlichen APIs.
- **C2 Pure Spiellogik:** Funktionen in `:game` ohne Seiteneffekte; Zeit
  und Zufall nur über injizierte `WallClock`/`RandomSource`.
- **C3 Fehler als Werte:** erwartbare Fehler als sealed Ergebnistypen;
  Exceptions sind Programmierfehler, keine Kontrollflüsse.
- **C4 Kleine Einheiten:** Funktionen ≤ 30 Zeilen, Dateien ≤ 300 Zeilen,
  Composables ≤ 100 Zeilen, zyklomatische Komplexität ≤ 10 (Detekt).
- **C5 Selbsterklärende Namen:** Kommentare erklären *warum*, nie *was*.
  Kein toter Code, kein TODO ohne Issue-Referenz.
- **C6 Öffentliche API dokumentiert:** KDoc auf allen public-Deklarationen
  in `:game`/`:core` (Explicit-API-Mode aktiv); sonst `internal`/`private`.
- **C7 Keine Warnungen:** `allWarningsAsErrors = true`; keine
  Detekt-/Lint-Baselines.
- **C8 Abhängigkeits-Hygiene:** neue Dependencies nur über das Version
  Catalog, nur mit ADR und Security-Prüfung.

## Sicherheitsregeln (S1–S8)

- **S1 Datensparsamkeit:** keine personenbezogenen Daten, KEINE
  Permissions in Version 1. Neue Permission ⇒ ADR + menschliche Freigabe.
- **S2 Sichere Speicherung:** nur App-Sandbox (DataStore, ADR-007),
  `allowBackup` bewusst konfiguriert, nichts auf External Storage.
- **S3 Keine dynamische Code-Ausführung:** kein DexClassLoader, kein
  WebView, keine ausführbaren Assets. Level-Definitionen sind reine Daten.
- **S4 Eingabe-Validierung an Vertrauensgrenzen:** alles von außerhalb
  des Prozesses (Dateien, Intents, Spielstände) strikt validieren
  (Schema, Wertebereiche, Größenlimits). Korrupte Eingabe ⇒ definierter
  Fehler, nie Crash.
- **S5 Komponenten nicht exportieren:** `android:exported="false"` für
  alles außer der Launcher-Activity.
- **S6 Lieferketten-Sicherheit:** Gradle Dependency Verification
  (SHA-256-Pinning, deckt auch den Plugin-Classpath ab), CVE-Scan im
  CI, Version-Pinning. Quellen: `google()` + `mavenCentral()` für alle
  Dependencies und Plugins; `gradlePluginPortal()` ausschließlich in
  `pluginManagement` und nur für per Content-Filter freigegebene
  Plugins ohne Publikation auf den Hauptquellen (derzeit einzig
  Ktlint-Gradle). Details, Rückbau-Trigger und PGP-Roadmap: ADR-002.
  Kein Test-Task lädt zur Laufzeit ungeprüfte Artefakte nach
  (Robolectric-Offline-Auflage: ADR-009).
- **S7 Keine Secrets im Repository:** Keys/Passwörter nur lokal
  (git-ignoriert) oder als CI-Secrets; gitleaks-Scan je PR.
- **S8 Release-Härtung:** R8 aktiv, `debuggable=false`, Release-Logging
  nur ≥ WARN und niemals Nutzerdaten.

## Quality Gates (Merge-Voraussetzungen)

| Gate | Werkzeug | Schwelle |
|---|---|---|
| Build ohne Warnungen | Gradle | `allWarningsAsErrors` |
| Formatierung | Ktlint | 0 Verstöße |
| Statische Analyse | Detekt | 0 Verstöße, keine Baseline |
| Unit-Tests | JUnit/Kotest | 100 % bestanden |
| UI-Tests | Robolectric/Compose (ADR-009) | 100 % bestanden |
| Coverage `:game` | Kover | ≥ 90 % |
| Coverage `:core` | Kover | ≥ 85 % |
| Coverage `:data` | Kover | ≥ 70 % Line — scharf ab PW-3.2 (ADR-009) |
| Coverage `:app` | Kover | ≥ 70 % Line — scharf ab PW-3.3 (ADR-009) |
| Android Lint | AGP | `warningsAsErrors` |
| Review | code-reviewer | APPROVE |
| Security (falls einschlägig) | security-auditor | APPROVE |

## Verbindliche Kommandos

- Vollständige lokale Prüfung: `./gradlew checkModuleGraph ktlintCheck detekt test koverVerify`
- Vor jedem PR zusätzlich: `./gradlew :data:lintDebug :app:lintDebug :app:assembleDebug`
  (`:data:lintDebug` schließt die NewApi-Gate-Lücke aus PW-3.10:
  Java-9+-APIs oberhalb von minSdk in :data-Hauptquellen sieht kein
  JVM-Unit-Test — nur Android Lint)
- Nach JEDER Dependency-Änderung (Merkregel aus PW-0.3/PW-2.3 —
  mit warmem lokalem Cache fehlen sonst .pom-/.module-Varianten, die
  nur ein kalter CI-Cache auflöst):
  `./gradlew --refresh-dependencies --write-verification-metadata sha256 checkModuleGraph ktlintCheck detekt test koverVerify :data:lintDebug :app:lintDebug :app:assembleDebug`
