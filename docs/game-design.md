# Game-Design — Puzzlewerk

> STATUS: ENTWURF — Phase 1 noch nicht gestartet.
> Dieses Dokument wird vom game-designer-Agent gefüllt und vom Menschen
> abgenommen. Bis zur Abnahme darf KEINE Spiellogik implementiert werden,
> die über das Phase-0-Gerüst hinausgeht.

## Ausfüll-Struktur (vom game-designer zu vervollständigen)

### 1. Kernkonzept
Ein Absatz: Was macht die Spielerin, warum macht es Spaß, was ist der
eine besondere Kniff?

### 2. Spielfeld und Zustände
Exakte Definition von Brett, Elementen und allen Zuständen (inkl.
Datenmodell-Skizze, Wertebereiche, Invarianten).

### 3. Züge und Regeln
Jeder erlaubte Zug als präzise Regel: Vorbedingung → Effekt →
Folgezustand. Mit durchgerechnetem Beispiel.

### 4. Scoring
Punkteformeln mit konkreten Zahlen und mindestens zwei durchgerechneten
Beispielen.

### 5. Level und Progression
Level-Parameter, Schwierigkeitskurve für 30–50 Levels,
Freischalt-Logik. Level-Generierung deterministisch aus Seed.

### 6. Gewinn-/Verlustbedingungen
Exakte Bedingungen inkl. aller Randfälle.

### 7. Randfall-Katalog
Nummerierte Liste ALLER Randfälle (der test-engineer testet direkt
gegen diese Liste): volles Brett, letzter Zug, gleichzeitige
Kombinationen, Min/Max-Werte, …

### 8. UX-Flows
Screen-Übersicht (Start → Levelwahl → Spiel → Ergebnis), Zustände pro
Screen, Übergänge.

### 9. Nicht-Ziele (Version 1)
Was bewusst NICHT enthalten ist (Monetarisierung, Online-Features, …).
