# ADR-004: Schichtenmodell präzisiert — app → data → game → core

- Status: AKZEPTIERT
- Datum: 2026-07-10
- Autor: architect (Ticket PW-2.6; Anlass: Eskalation aus PW-2.1,
  Review-Auflagen des code-reviewers aus dem PW-2.1-Review)
- Bezug: ADR-001 (Modulschnitt); ersetzt das Schaubild in
  docs/architektur.md „Schichtenmodell" und die Kurzfassung in CLAUDE.md
  („app → game/data → core")

## Kontext

Seit Phase 0 deklariert `data/build.gradle.kts` die Kante
`implementation(project(":game"))`. Das Schaubild in docs/architektur.md
und die CLAUDE.md-Kurzfassung („app → game/data → core") kennen diese
Kante nicht — sie stellen `:game` und `:data` als Geschwister dar, die
beide nur `:core` kennen. Build und Dokumentation widersprechen sich
(Backlog-Eskalation aus PW-2.1).

Die Kante ist fachlich begründet: Die Phase-3-Persistenz in `:data`
(Level-/Spielstand-Serialisierung, Repositories) braucht die
Domänentypen aus `:game` (`LevelDefinition`, `LevelValidator`, …), um
DTOs auf Domänentypen abzubilden und eingelesene Daten an der
Vertrauensgrenze mit der EINEN normativen Validierungslogik zu prüfen
(Regel S4) — Validierung darf nicht in `:data` dupliziert werden.

Der code-reviewer hat den Vorschlag „Schaubild korrigieren, nicht den
Build" unterstützt, mit zwei Auflagen: (a) die Gegenrichtung als
explizite Invariante festschreiben, (b) die maschinelle Erzwingung der
Schichtenregel (bestehender Backlog-Punkt „ArchUnit/Konsist") als
konkretes Umsetzungsticket spezifizieren, inklusive restriktiver
Dependency-Bewertung nach C8.

## Optionen

1. **Schaubild korrigieren: app → data → game → core.** `:data` darf
   `:game` kennen (Datenschicht implementiert domänennahe
   Repository-Schnittstellen und mappt DTOs ↔ Domänentypen). `:game`
   bleibt unverändert unabhängig — es deklariert keinerlei Kante zu
   `:data`, der Compiler kann dort also gar nichts aus `:data` sehen.
   Entspricht dem Clean-Architecture-Standard (Data hängt von Domain
   ab, nie umgekehrt). Kein Build-Umbau.
2. **Kante `:data → :game` kappen, DTO-Mapping in `:app`.** `:data`
   bekäme ein eigenes, paralleles Typuniversum (DTOs ohne Bezug zu
   den Domänentypen); `:app` müsste jede Persistenz-Operation mappen.
   Konsequenzen: Duplikation der Modellstruktur mit Drift-Risiko, die
   S4-Validierung (Schema + Wertebereiche via `LevelValidator`) läge im
   Android-Modul `:app` statt JVM-testbar in `:data`, ViewModels
   übernähmen Mapping-Verantwortung (Verstoß gegen kleine Einheiten,
   C4). Der „Gewinn" — ein von `:game` unabhängiges `:data` — hat
   keinen Abnehmer: Niemand nutzt `:data` ohne die Domäne.
3. **Eigenes Mapping-Modul** (z. B. `:data-mapping`, kennt `:game` und
   `:data`). Löst das Duplikationsproblem aus Option 2, kostet aber ein
   fünftes Modul samt Build-Overhead und wirft die Frage auf, wo
   Repository-Schnittstellen leben. Faktisch wäre dieses Modul genau
   das, was `:data` heute schon ist — YAGNI in einem
   Vier-Modul-Projekt.

## Entscheidung

**Option 1.** Das Schaubild und die Regel-Texte werden auf die reale,
gewollte Richtung korrigiert: **app → data → game → core**. Der Build
bleibt unverändert.

### Erlaubte Modul-Abhängigkeiten (Whitelist, abschließend)

| Modul | darf abhängen von |
|---|---|
| `:app` | `:data`, `:game`, `:core` |
| `:data` | `:game`, `:core` |
| `:game` | `:core` |
| `:core` | — (nichts) |

Jede neue Kante oder jedes neue Modul erfordert ein neues ADR, das
dieses referenziert.

### Invarianten (Review-Auflage a, nicht verhandelbar)

- **I-M1:** `:game` referenziert NIEMALS `:data` oder `:app` — in
  keiner Konfiguration, auch nicht in Testquellen.
- **I-M2:** `:core` referenziert NIEMALS ein anderes Projektmodul.
- **I-M3:** `:data` referenziert NIEMALS `:app`.
- **I-M4:** `:game` und `:core` bleiben reine Kotlin/JVM-Module ohne
  Android-Plugin und ohne Android-Abhängigkeiten (bekräftigt ADR-001).

### Maschinelle Erzwingung (Review-Auflage b): Bewertung nach C8

Zentrale Beobachtung: Quellcode kann die Modul-Whitelist nicht
verletzen, weil verbotene Module gar nicht auf dem Compile-Classpath
liegen — ein `import de.puzzlewerk.data.*` in `:game` ist heute schon
ein Compile-Fehler. Der einzige Mutationsvektor ist eine Änderung an
einer `build.gradle.kts` (neue `project(...)`-Kante oder
Android-Plugin auf `:game`/`:core`). Genau dagegen richtet sich die
Prüfung.

- **Konsist** (`com.lemonappdev:konsist`): quellcodebasiert, zieht
  transitiv `kotlin-compiler-embeddable` (zweistellige MB, große
  Angriffs-/Wartungsfläche für Dependency Verification nach S6). Sein
  Mehrwert liegt bei Intra-Modul-Regeln (Paket-Layering, Namens-
  konventionen) — die brauchen wir derzeit nicht; Modul-Kanten prüft
  es über Imports, die der Compiler ohnehin verhindert.
- **ArchUnit**: bytecode-basiert, Java-zentrisch; bräuchte einen
  Test-Standort, der ALLE Module auf dem Classpath hat — das schüfe
  ironischerweise selbst neue Kanten oder ein Extra-Modul. Prüft
  ebenfalls primär, was Gradle strukturell schon garantiert.
- **Eigenlösung**: ein Gradle-Task im Root-Build (~40 Zeilen), der den
  Projektgraphen gegen die Whitelist-Tabelle oben prüft. Null neue
  Dependencies, prüft exakt den realen Mutationsvektor
  (build.gradle.kts), schlägt auch bei UNBEKANNTEN Modulen fehl (neues
  Modul erzwingt bewusste Whitelist-Erweiterung = ADR-Pflicht).

**Empfehlung/Entscheidung: Eigenlösung.** Konsist/ArchUnit werden NICHT
aufgenommen (C8: große transitive Abhängigkeitsmenge gegen ein Problem,
das 40 Zeilen lösen). Sollten später Intra-Modul-Regeln nötig werden
(z. B. Paket-Layering in `:app` ab Phase 3), ist das ein neues ADR mit
neuer Abwägung.

### Umsetzungsticket PW-2.6-impl (Spezifikation, verbindlich)

- **Werkzeug:** eigener Gradle-Task `checkModuleGraph` im
  Root-`build.gradle.kts` (Umzug in ein `build-logic`-Modul erst, wenn
  eines existiert). Keine neue Dependency.
- **Regeln:**
  1. Whitelist als `Map<String, Set<String>>` exakt gemäß Tabelle oben;
     geprüft werden ALLE Konfigurationen jedes Subprojekts auf
     `ProjectDependency`-Einträge (deckt `implementation`, `api`,
     `compileOnly`, `runtimeOnly` sowie Test- und
     Debug-/Release-Varianten ab → I-M1 bis I-M3).
  2. Subprojekt ohne Whitelist-Eintrag ⇒ Fehler („neues Modul braucht
     ADR + Whitelist-Erweiterung").
  3. `:game` und `:core` dürfen weder `com.android.application` noch
     `com.android.library` anwenden (I-M4).
  4. Fehlermeldung nennt die verbotene Kante als
     `«modul» → «ziel» (Konfiguration «name»)` und verweist auf
     ADR-004.
- **Verankerung:** Task hängt am Root-`check`
  (`tasks.named("check") { dependsOn(...) }`) und wird zusätzlich in
  die „Verbindlichen Kommandos" in docs/architektur.md sowie den
  CI-Workflow aufgenommen.
- **Abnahme:** (1) Task grün auf aktuellem Graph; (2) Negativprobe:
  temporär eingefügtes `implementation(project(":data"))` in
  `game/build.gradle.kts` lässt den Task mit der spezifizierten
  Meldung fehlschlagen (Probe dokumentieren, Kante wieder entfernen);
  (3) `./gradlew check` gesamt grün.

## Konsequenzen

- (+) Dokumentation und Build stimmen wieder überein; die
  Phase-3-Persistenz kann Repository-APIs direkt gegen `:game`-Typen
  definieren und `LevelValidator` an der Vertrauensgrenze nutzen (S4),
  ohne Modell-Duplikation.
- (+) Das Schutzgut bleibt unangetastet: `:game` ist weiterhin pur,
  Android-frei und von `:data`/`:app` unabhängig — jetzt als explizite
  Invariante I-M1/I-M4 statt nur implizit.
- (+) Keine neue Dependency (C8); die Erzwingung ist als konkretes
  Ticket PW-2.6-impl spezifiziert und ohne Architektur-Rückfragen
  umsetzbar.
- (−) `:data` ist nicht mehr isoliert von `:game` kompilierbar —
  bewusste Kopplung Daten → Domäne (Clean-Architecture-Standard).
- (−) CLAUDE.md-Kurzfassung musste angepasst werden (bindet alle
  Agents); der neue Wortlaut ist in diesem ADR fixiert:
  „Abhängigkeitsrichtung: app → data → game → core (ADR-004). Nie
  andersherum."
- (−) docs/plan.md §3.1 und die Agent-Definition
  `.claude/agents/architekt.md` tragen noch den alten Wortlaut
  („app → game/data → core"). plan.md ist historisches, vom Menschen
  abgenommenes Planungsdokument und wird nicht angefasst; normativ ist
  docs/architektur.md. Die Anpassung der Agent-Definition liegt beim
  Orchestrator (Backlog-Notiz).
- Folgearbeit: PW-2.6-impl (siehe Spezifikation oben; ersetzt den
  Backlog-Punkt „Konsist- oder ArchUnit-Tests").
