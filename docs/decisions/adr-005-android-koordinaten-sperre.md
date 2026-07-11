# ADR-005: Regel-5-Sperrliste um den Google-Namensraum erweitert

- Status: AKZEPTIERT
- Datum: 2026-07-11
- Autor: architect (Ticket PW-2.8; Anlass: Security-Finding L3 aus dem
  PW-2.6-impl-Review, PR #9)
- Bezug: ergänzt ADR-004 (Schichtenmodell, Regel 5 der
  checkModuleGraph-Spezifikation). ADR-004 bleibt unverändert gültig;
  dieses ADR verschärft ausschließlich die Koordinaten-Sperrliste.

## Kontext

ADR-004, Regel 5, sperrt externe Maven-Koordinaten mit den
Gruppen-Präfixen `androidx.` und `com.android` für `:game`/`:core`
(Invariante I-M4, Koordinaten-Hälfte). Der security-auditor hat im
PW-2.6-impl-Review (PR #9, APPROVE, Finding L3/LOW) eine Lücke
gemeldet: Das historische Stub-JAR **`com.google.android:android`**
(android.jar-Stub auf Maven Central, plain JAR, letzte Version
4.1.1.4 von 2012) passiert beide Präfixe. Da es ein reines JAR ist,
scheitert es — anders als AAR-Artefakte wie `material` oder `gms` —
auch NICHT von selbst am JVM-Modul: Mit dieser Koordinate wären
`android.*`-Imports in `:game` kompilierbar, während `checkModuleGraph`
grün bliebe. Genau das soll I-M4 ausschließen.

Kompensationen existieren (Dependency Verification müsste das Artefakt
erst aufnehmen, C8-ADR-Pflicht für jede neue Dependency, PR-Review),
daher LOW — aber die Sperrliste soll den Vektor selbst schließen.
Da ADRs unveränderlich sind (Grundregel 2), erfolgt die Verschärfung
als neues ADR statt als Änderung an ADR-004.

## Optionen

1. **Nur `com.google.android` ergänzen.** Schließt exakt L3 (inkl.
   Geschwister wie `com.google.android:annotations`, ebenfalls plain
   JAR). Lässt aber weitere Google-Android-Gruppen offen
   (`com.google.firebase`, `com.google.mlkit`, `com.google.gms`,
   `com.google.ads.*`, …) — teils mit JVM-konsumierbaren JARs (z. B.
   `firebase-encoders`). Die Liste der Google-Android-Untergruppen ist
   ein bewegliches Ziel; Enumeration erzeugt Pflegeaufwand und
   wiederholte Restlücken.
2. **Enumerieren: `com.google.android` + `com.google.firebase`
   (+ ggf. weitere).** Gleiche Grundschwäche wie Option 1, nur mit
   mehr Einträgen: Jede neue Google-Gruppe erfordert erneut ein ADR,
   die Lücke besteht bis dahin.
3. **Pauschal `com.google.` sperren (gesamter Google-Namensraum).**
   Deckt L3 und alle heutigen wie künftigen Google-Android-Gruppen mit
   EINEM Präfix ab. Bewusste false positives: auch JVM-reine
   Google-Bibliotheken (Guava, Gson, Truth, Dagger, Protobuf,
   `com.google.code.findbugs`) sind in `:game`/`:core` gesperrt.

## Entscheidung

**Option 3.** Die Sperr-Präfixliste aus ADR-004, Regel 5, wird für
`:game`/`:core` erweitert auf:

> `androidx.` · `com.android` · `com.google.`

Begründung der pauschalen Sperre (restriktiv nach C8):

- `:game`/`:core` haben KEINEN legitimen Bedarf an irgendeinem
  Google-Artefakt. Der Stack ist per ADR-001/ADR-003 festgelegt
  (Kotlin-Stdlib, kotlinx, JUnit, Kotest); Guava/Gson & Co. sind durch
  Kotlin-Bordmittel bzw. kotlinx.serialization abgedeckt und wären
  ohnehin C8-ablehnungswürdig.
- False positives sind billig und reversibel: Jede neue Dependency
  braucht sowieso ein ADR (C8) — ein solches ADR kann eine punktuelle,
  begründete Ausnahme von dieser Sperre gleich mitentscheiden (neues
  ADR referenziert dieses, analog zur Ausnahme-Regel in ADR-004).
- Ein Präfix statt einer wachsenden Enumeration: kein Pflegeaufwand,
  keine Restlücken bei neuen Google-Gruppen.

Der Präfix endet bewusst mit Punkt (`com.google.`), damit die fremde
Legacy-Hosting-Gruppe `com.googlecode.*` (Google-Code-Hosting für
Dritt-Projekte, keine Google-Artefakte) nicht mitgesperrt wird. Die
verwaiste Alt-Gruppe `com.google` (ohne Untergruppe, letzte Artefakte
~2009) bleibt damit formal ungesperrt — akzeptiert, da dort keine
Android-Stubs liegen und C8 sie ohnehin aussortiert.

Geltungsbereich unverändert: Die Sperre gilt NUR für `:game`/`:core`
in allen Konfigurationen inklusive Testquellen. `:app` und `:data`
sind nicht betroffen (`:app` nutzt legitim androidx/Material).

### Umsetzung

Im Root-`build.gradle.kts` wird die bisher inline geprüfte
Präfix-Bedingung als benannte Liste `forbiddenCoordinatePrefixes`
geführt (Architektur-Gerüst-Artefakt, Änderung nur per ADR) und um
`com.google.` ergänzt; Fehlermeldungen referenzieren ADR-004/ADR-005.
Abnahme analog ADR-004: Negativprobe mit temporärem
`implementation("com.google.android:android:4.1.1.4")` in
`game/build.gradle.kts` ⇒ `checkModuleGraph` rot mit
Koordinaten-Meldung; Probe wird protokolliert, nicht committet.

## Konsequenzen

- (+) L3 geschlossen: Das android.jar-Stub-JAR und jede andere
  Google-Koordinate sind in `:game`/`:core` maschinell gesperrt,
  I-M4 hält auch gegen den plain-JAR-Vektor.
- (+) Zukunftssicher ohne Pflege: neue Google-Gruppen sind automatisch
  erfasst.
- (−) JVM-reine Google-Bibliotheken (Guava, Gson, Truth, Dagger, …)
  sind in `:game`/`:core` mitgesperrt — bewusst; Ausweg ist ein neues
  ADR mit punktueller Ausnahme, nie eine stille Listenänderung.
- (−) Unverändert geprüft werden nur DEKLARIERTE Koordinaten, nicht
  transitiv aufgelöste (Grenze aus ADR-004): Ein Nicht-Google-Artefakt
  könnte theoretisch ein Google-JAR transitiv ziehen. Kompensation
  bleibt die Dependency Verification (ADR-002), die jedes aufgelöste
  Artefakt einzeln pinnt — ein transitives Google-JAR fiele dort als
  neuer verification-metadata-Eintrag im Review auf.
- Kein Handlungsbedarf in docs/architektur.md: Die Präfixliste ist
  dort nicht dupliziert; normativ sind ADR-004 + dieses ADR sowie der
  Task selbst.
