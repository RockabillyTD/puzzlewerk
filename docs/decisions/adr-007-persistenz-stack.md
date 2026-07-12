# ADR-007: Persistenz — typisierter DataStore + kotlinx.serialization, kein Room

- Status: AKZEPTIERT
- Datum: 2026-07-11
- Autor: architect (Ticket PW-3.1, Phase-3-Fundament)
- Bezug: ADR-001 (Stack; docs/plan.md §2 nennt „Room + DataStore" als
  Vorschlag), ADR-004 (:data → :game — Repositories nutzen
  Domänentypen direkt, Validierung an der Vertrauensgrenze via
  `LevelValidator`), ADR-002 (Dependency Verification / S6)

## Kontext

Phase 3 braucht echte Persistenz in `:data`. Zu speichern ist laut
docs/game-design.md ausschließlich:

1. **Kampagnenfortschritt** (§7.2, §11, §12.4): je gelöstem Level
   bester Score + Sterne — maximal 50 Einträge.
2. **Daily-Serienstatistik** (§10.3): 4 Zähler + eine Map
   Datum → (Züge, Par, Score, Sterne) — wächst um max. 1 Eintrag/Tag.
3. **Einstellungen** (§12.5): 4 Booleans (Sound, Haptik, Farbsymbole,
   Strahlmuster).

Es gibt keine Relationen, keine Queries über Teilmengen, keine
Volltextsuche, keine großen Datenmengen, keinen Mehrprozess-Zugriff.
Nicht-Anforderungen per Design: kein Cloud-Sync, kein Export (§17).

Harte Anforderungen: Korrupte oder versionsfremde Daten führen zu einem
definierten Fehler als Wert, nie zu Crash oder stillem Datenverlust
(S4, R43, C3). Ein Migrations-/Versionierungs-Pfad muss von Anfang an
existieren. Das Serialisierungsformat MUSS Duplikat-Schlüssel ablehnen
(§16.2/2 — die `Map` erzwingt Eindeutigkeit nur strukturell; die
Grenze Datei → Objekt muss sie prüfen).

## Optionen

1. **Room (+ DataStore für Einstellungen).** Industrie-Standard für
   relationale Daten — die es hier nicht gibt. Kosten: KSP-Prozessor
   als zusätzliches, kotlin-versionsgekoppeltes Build-Plugin,
   `room-runtime`/`room-ktx`/`sqlite`-Artefakte (große transitive
   Fläche in der verification-metadata), SQL-Schema + Room-eigene
   Migrationsklassen, eigene Testinfrastruktur (in-memory-DB,
   Robolectric für DAO-Tests). Für ≤ 50 Zeilen-Datensätze ist das ein
   Datenbankserver für einen Einkaufszettel.
2. **Preferences DataStore.** Schlüssel-Wert ohne Schema: die
   strukturierten Daten (Map Level→Bestwert, Map Datum→Ergebnis)
   müssten als JSON-Strings IN Preferences gelegt werden —
   Serialisierung bräuchte es also trotzdem, plus einen typlosen
   Umweg. Keine Versionierungs-Story.
3. **Proto DataStore.** Typisiert und binär, aber: protobuf-Compiler
   als weiteres Build-Plugin + `protobuf-javalite`-Runtime, `.proto`-
   Schemadateien als zweite Sprache im Repo, und die Artefakte liegen
   im per ADR-005 für `:game`/`:core` gesperrten `com.google.`-
   Namensraum (in `:data` zwar erlaubt, aber unnötig).
4. **Nur kotlinx.serialization + eigene Dateien.** Kleinste
   Dependency-Menge, aber die heiklen Eigenschaften — atomare Writes
   (Temp-Datei + Rename + fsync), Konsistenz bei parallelen Coroutines,
   definiertes Verhalten bei Prozess-Tod mitten im Write — müssten
   selbst gebaut und selbst bewiesen werden. Das sind keine „50 Zeilen
   eigener Code", sondern genau die Fehlerklasse, die man nicht selbst
   debuggen will.
5. **Typisierter DataStore (`androidx.datastore:datastore`) mit
   kotlinx.serialization-JSON als `Serializer<T>`.** DataStore liefert
   die harten Garantien (atomare Writes, `updateData` transaktional,
   `Flow`-Beobachtung, definierter Korruptions-Hook); das Schema
   definieren wir selbst als versionierte Kotlin-DTOs mit
   kotlinx.serialization. Kein Codegen, kein zusätzliches Build-Plugin
   außer dem Kotlin-eigenen serialization-Plugin (versionsgleich mit
   Kotlin, kein Fremd-Release-Zyklus). kotlinx.serialization wird
   ohnehin gebraucht: docs/plan.md S4 schreibt es für die
   Level-Validierung an Vertrauensgrenzen fest, und die eingecheckten
   Level-Definitionen (§11.1, Phase 4) brauchen dasselbe Format.

## Entscheidung

**Option 5: typisierter DataStore + kotlinx.serialization (JSON), kein
Room, kein Protobuf, kein Preferences DataStore.** Drei getrennte
Stores (`progress`, `daily_stats`, `settings`) im App-Sandbox-
Verzeichnis (S2), je Store ein versioniertes DTO-Schema in `:data`.

Neue Dependencies (Version Catalog; Security-Review folgt je Prozess):

| Koordinate | Quelle | Zweck |
|---|---|---|
| Plugin `org.jetbrains.kotlin.plugin.serialization` (Version = Kotlin) | mavenCentral | Compiler-Plugin für `@Serializable` in `:data` |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` | mavenCentral | JSON-Codec (JetBrains, aktiv gepflegt; transitiv nur `serialization-core` + stdlib) |
| `androidx.datastore:datastore:1.1.7` | google() | atomare, beobachtbare Dateipersistenz (transitiv u. a. `datastore-core`, `okio` — Square, aktiv gepflegt) |

Geltungsbereich: NUR `:data` (und transitiv `:app`). `:game`/`:core`
bleiben serialisierungsfrei — Domänentypen tragen keine
`@Serializable`-Annotationen; `:data` mappt DTOs ↔ Domänentypen
(ADR-004).

### Schema-Versionierung und Migration (verbindlich)

- Jede Store-Datei ist ein JSON-Envelope:
  `{ "version": <Int ≥ 1>, "payload": { … } }`. Der Envelope wird
  zuerst dekodiert, dann das Payload gemäß Version.
- `version` startet bei 1. Jede Schema-Änderung inkrementiert sie und
  liefert im selben PR eine reine Migrationsfunktion
  `payload_vN → payload_vN+1`; Migrationen werden verkettet
  (v1→v2→…→aktuell) und sind unit-getestet mit eingefrorenen
  v-N-Beispieldateien (Golden-Dateien in `data/src/test/resources`).
- `version` > unterstützte Version (Downgrade nach App-Update-Rollback)
  ⇒ `PersistenceFailure.UnsupportedVersion` als Wert — kein Crash,
  kein Überschreiben.
- Nicht dekodierbar (kaputtes JSON, Schema-Verstoß, Wertebereichs-
  Verstoß beim Mapping) ⇒ `PersistenceFailure.Corrupted` als Wert.
  Für Fortschritt und Daily-Statistik gibt es KEIN stilles Ersetzen
  (kein `ReplaceFileCorruptionHandler`, der Nutzerdaten wegwirft) —
  die UI zeigt einen definierten Fehler und bietet explizit Zurücksetzen
  an. Einzige Ausnahme: der **Settings**-Store fällt bei Korruption auf
  die Defaults zurück (4 Booleans, kein schützenswerter Bestand) —
  dokumentiert im `SettingsRepository`-KDoc.
- JSON-Konfiguration an der Vertrauensgrenze (S4):
  `ignoreUnknownKeys = false`, keine Lenient-Modi. Wertebereiche prüft
  der DTO→Domäne-Mapper (bzw. `LevelValidator` für Level).

### Duplikat-Schlüssel (§16.2/2, verbindlich für ALLE Formate)

kotlinx.serialization dedupliziert JSON-Objektschlüssel beim Einlesen
stillschweigend (letzter gewinnt) — damit wäre §16.2/2 verletzt.
Deshalb gilt für jedes Persistenz- und Level-Format dieses Projekts:

> **Keine fachlichen Daten als dynamische JSON-Objektschlüssel.**
> Maps werden als Array von Einträgen serialisiert (z. B.
> `[{"level": 3, "score": 1450, "stars": 2}, …]`,
> `[{"q": 0, "r": 0, "element": …}, …]`). Der Mapper prüft die
> Eindeutigkeit der Schlüsselfelder explizit und meldet Duplikate als
> `Corrupted` (bzw. als Validierungs-Verstoß beim Level-Laden) —
> der Duplikat-Check ist damit testbarer Code statt undefiniertes
> Parserverhalten.

### Level-Daten (Abgrenzung)

Phase 3 lädt Kampagnenlevel NICHT aus Dateien, sondern erzeugt sie
deterministisch on-device via `generateLevel(campaignSeed(n), tier)`
(§11.1: solange nicht kuratiert wurde, sind Generat und eingecheckte
Daten identisch). Das eingecheckte Level-Asset-Format (Phase 4,
Kuratierung) verwendet dieselben Regeln dieses ADRs (Envelope +
Version, Entry-Arrays statt Objektschlüssel, `ignoreUnknownKeys=false`)
und läuft beim Laden durch den `LevelValidator` (§16.2, ADR-004); die
zugehörige `LevelRepository`-API definiert der Architekt mit dem
Phase-4-Ticket.

## Konsequenzen

- (+) Kleinstmögliche Dependency-Fläche für die harten Garantien:
  kein KSP, kein SQL, kein Protobuf; das serialization-Plugin folgt
  exakt der Kotlin-Version.
- (+) Ein Serialisierungs-Stack für Spielstände UND (ab Phase 4)
  Level-Assets; S4-Regeln an einer Stelle definiert.
- (+) Migrations-Pfad ist von Version 1 an explizit, testbar und
  golden-gepinnt; Korruption/Versionskonflikt sind API-Werte
  (siehe `PersistenceFailure` in `:data`).
- (−) Kein Query-Komfort — bei ≤ 50 + ~365/Jahr Einträgen irrelevant;
  alles wird als Ganzes gelesen/geschrieben (DataStore-Modell).
  Revisions-Trigger für Room (neues ADR): relationale Anforderungen
  oder Datenmengen, bei denen Ganzdatei-Writes messbar stören.
- (−) `okio` kommt transitiv über DataStore in die
  verification-metadata (bekannte, aktiv gepflegte Bibliothek).
- (−) JSON ist geringfügig größer als Protobuf — bei < 100 KB
  Gesamtdaten bedeutungslos, dafür menschen- und agentenlesbar
  (Debugging, Golden-Dateien im Test).
