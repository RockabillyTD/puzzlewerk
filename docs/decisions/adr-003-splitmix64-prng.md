# ADR-003: SplitMix64 als normatives PRNG hinter `RandomSource` (:core)

- Status: AKZEPTIERT
- Datum: 2026-07-09
- Autor: architect (Ticket PW-2.1; Anlass: docs/game-design.md §8 und
  Backlog-Eintrag „SplitMix64-ADR für :core.RandomSource" aus PW-1.1)
- Bezug: ADR-001 (Modulschnitt, `:core` als Heimat der Abstraktionen)

## Kontext

Das Game-Design (docs/game-design.md, normativ) verlangt geräte- und
versionsübergreifende Seed-Identität: gleicher Seed ⇒ byte-identisches
Level (Invariante I2, Randfall R34). Das Tägliche Prisma wird on-device
aus dem Datum generiert (§10.1) — zwei Geräte MÜSSEN am selben Tag
dasselbe Rätsel erzeugen, für immer, auch über App-, Kotlin- und
Android-Updates hinweg. §8 legt dafür den Algorithmus fest:
**SplitMix64** (Steele/Lea/Flood) mit reiner 64-Bit-Ganzzahlarithmetik,
`nextInt(bound)` per `floorMod` sowie der Finalizer `mix64` für die
Seed-Ableitung von Daily (§10.1) und Kampagne (§11.1).

Der Phase-0-Platzhalter `SeededRandom` in `:core` delegiert an
`kotlin.random.Random(seed)` und erfüllt diese Anforderung NICHT.

## Optionen

1. **`kotlin.random.Random(seed)` behalten.** Die Kotlin-Dokumentation
   garantiert die Sequenz ausdrücklich nur innerhalb derselben
   Kotlin-Version („the returned generator can differ between Kotlin
   versions"). Ein Kotlin-Update könnte still alle Daily-Puzzles und
   eingecheckten Kampagnen-Seeds verändern — disqualifiziert.
2. **`java.util.Random`.** Der LCG-Algorithmus ist zwar im Javadoc
   exakt spezifiziert (versionsstabil), aber es ist ein anderer
   Algorithmus als der normativ in §8 festgelegte; schwache
   statistische Qualität (48-Bit-LCG); `nextInt(bound)` weicht von der
   normativen floorMod-Regel ab. Erfüllt das Design nicht.
3. **`java.util.SplittableRandom`.** Intern SplitMix64-basiert, aber
   der konkrete Algorithmus ist Implementierungsdetail, nicht
   API-Vertrag (kann sich je JDK ändern); `nextInt(bound)` nutzt
   Rejection-Sampling statt floorMod; Zwischenschritte (state, mix64)
   sind nicht zugreifbar, die Seed-Ableitung §10.1/§11.1 braucht aber
   genau `mix64`. Erfüllt das Design nicht.
4. **Eigene SplitMix64-Implementierung in `:core`** (~20 Zeilen, exakt
   der Pseudocode aus §8, inklusive öffentlichem `mix64`).

## Entscheidung

**Option 4.** `:core` erhält `SplitMix64Random : RandomSource` als
normative Implementierung sowie die öffentliche pure Funktion
`mix64(Long): Long`. Das Interface `RandomSource` wird um
`nextLong(): Long` erweitert (der Generator §9 und die Fallback-Regel
§9.5/7 brauchen beide Ebenen). Der Platzhalter `SeededRandom`
(kotlin.random) wird ersatzlos entfernt.

Verbindliche Details (aus §8 übernommen, hier nur fixiert):

- Zustand: ein `Long`; `nextLong()` = Weyl-Inkrement
  `0x9E3779B97F4A7C15` + mix64-Finalizer. Die Hex-Konstanten werden als
  vorzeichenbehaftete Long-Literale notiert (`-0x61C8864680B583EBL`,
  `-0x40A7B892E31B1A47L`, `-0x6B2FB644ECCEEE15L`) — bit-identisch zu
  den unsigned Werten.
- `nextInt(bound)` = `floorMod(nextLong(), bound)`; der minimale
  Modulo-Bias ist per Designentscheidung §8 akzeptiert
  (Reproduzierbarkeit schlägt perfekte Gleichverteilung).
- Golden-Tests pinnen die Sequenz auf publizierte Referenzwerte
  (Seed 0 ⇒ `0xE220A8397B1DCDAF`, `0x6E789E6AA1B965F4`, …), damit jede
  künftige Refaktorierung eine Sequenzänderung sofort sichtbar macht.

## Konsequenzen

- (+) Seed-Identität ist vertraglich UND testbar garantiert; unabhängig
  von Kotlin-/JDK-Version, Gerät und ART/JVM.
- (+) Keine neue Dependency (C8): ~20 Zeilen eigener, golden-getesteter
  Code statt einer Library.
- (+) `mix64` steht `:game` für die pure Seed-Ableitung (§10.1, §11.1)
  zur Verfügung; `:game` bleibt frei von Zufalls-Eigenbau.
- (+) Statistische Qualität für Levelgenerierung mehr als ausreichend
  (SplitMix64 besteht BigCrush; kryptographische Ansprüche bestehen
  nicht und werden nicht erhoben).
- (−) Eigenpflege der ~20 Zeilen inkl. Golden-Tests (bewusst gering
  gehalten; der Algorithmus ist final und ändert sich nie — jede
  Änderung wäre ein Design-Bruch).
- (−) `SplitMix64Random` ist zustandsbehaftet (`var state`) — bewusste,
  dokumentierte Ausnahme von „val > var"; die Determinismus-Garantie
  hängt am Seed, nicht an Immutability.
- Folgearbeit: Platzhalter `SeededRandom` + Tests entfernt (dieser PR);
  Generator (PW-2.5) und Daily (PW-2.6) nutzen ausschließlich diese
  Implementierung.
