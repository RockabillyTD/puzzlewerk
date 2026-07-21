# Journal — release-engineer

> Aufgabenhistorie dieser Rolle. Pflege: Orchestrator, nach jedem
> gemergten/eskalierten Ticket. Letzte 8 Tickets voll, ältere je 1 Zeile.
> Kappe: 400 Zeilen. Schrittangaben vor Phase 4: n. a. (vor Budget-Regime).

## PW-4.10 — Phase-4-Gate-Vorbereitung (PR #41, gemergt 2026-07-21)
- Gebaut: volle Gate-Kette auf main grün (inkl. assembleRelease);
  APK-Größenbudget dokumentiert (Phase 3 ~10,4 MB → 11,17 MB Gate-
  Stand, OGG-Anteil 684 632 B nach Demo-Entfernung — Korridor
  +1–1,5 MB gehalten); music_demo_steigerung.ogg (652 KiB,
  unreferenziert, via synth.py reproduzierbar) entfernt; keep.xml
  komplett entfernt — Shrinker-Beweis: resources.txt zeigt 17/17
  raw-Assets reachable, Release-APK enthält 17/17 (vom Reviewer
  unabhängig nachgebaut und nachgezählt); versionName 0.4.0 /
  versionCode 2; docs/phase4-gate-checklist.md (Spieltest-Punkte +
  Abnahme-Deltas D1–D10 + Produktfrage Portrait-Lock).
- Gate-Artefakt: app-debug.apk 11 173 577 B, SHA-256 ac2384…5efb959,
  Tree-Hash-verifiziert (S6, byte-identisch reproduziert). Kein
  Signing, kein Tag (erst nach Freigabe).
- Review: MERGEABLE; Security-APPROVE (Hashes unabhängig
  nachgerechnet, kein Signing-Material, S8 intakt).
- Learning: Shrinker-Nachweis über mapping/release/resources.txt
  ist der belastbare Ersatz für pauschale keep.xml-Versicherungen.
- Schritte: ~22/70.

## PW-3.10 — :data:lintDebug in Gate-Kette + CI (PR #27, gemergt 2026-07-14)
- Gebaut: :data:lintDebug in die verbindliche Gate-Kette und CI
  aufgenommen — schließt den NewApi-Blindfleck, der den
  readNBytes-HIGH aus PW-3.2 erst spät sichtbar machte.
- Learning: Jedes Android-Modul braucht seinen eigenen Lint-Lauf im
  Gate; JVM-Tests ersetzen keinen API-Level-Check.
- Schritte: n. a.

## Phase-3-Gate-Artefakt (Zyklus 14, 2026-07-14)
- Debug-APK gebaut, Tree-identisch zu main@f8dd279 (verifiziert via
  `git rev-parse <sha>^{tree}`): app/build/outputs/apk/debug/
  app-debug.apk (~10,4 MB). Der QS-Merge (PR #29) ändert nur
  Test-Quellen+docs — kein Neubau nötig.
- Learning: Tree-Hash-Verifikation als Standard für Gate-Artefakte.
- Schritte: n. a.

## PW-2.6-impl — checkModuleGraph in check + CI (PR #9, 2026-07-11)
- Gebaut: checkModuleGraph gemäß ADR-004-Spezifikation als Gate;
  beide Negativproben doppelt reproduziert; ADR-004-Auflagenkette zu.
- (Zuordnung aus der Historie: Build-/Gate-Arbeit; im Statusprotokoll
  ohne expliziten Agenten vermerkt.)
- Schritte: n. a.

## PW-0.4-impl — Portal-Content-Filter (PR #3, 2026-07-09)
- Gebaut: Content-Filter in settings.gradle.kts gemäß ADR-002 —
  Security-Finding L2 endgültig geschlossen.
- Review: Reviewer- + Security-APPROVE, CI grün. Schritte: n. a.

## PW-0.3 / PW-0.5 — CI erstmals grün (PR #1, 2026-07-09)
- Gebaut: Kalt-Cache-Metadaten-Fix (PW-0.3) + Lint-Fix (PW-0.5,
  umgebungsabhängiger OldTargetApi begründet deaktiviert +
  Backlog-Prozess); Lint-Reports als CI-Failure-Artefakt.
- Kontext: zwei diagnostizierte CI-Fehlschläge vor dem Merge;
  Checksummen 3-fach verifiziert; Security-APPROVE für den Lint-Teil.
- Learning: CI-Diagnose braucht Artefakte — Reports immer hochladen.
- Schritte: n. a.

## Offen für diese Rolle
- Phase 4, Punkt 10 (PW-4.10): volle Gate-Kette auf main, APK-Größen-
  budget (Erwartung ≈ +1 MB OGG; > +3 MB ⇒ Befund statt Optimierung),
  R8/Shrinking-Check für raw-Assets, docs/phase4-gate-checklist.md,
  Gate-APK + versionName 0.4.0. Startet nach Merge der Punkte 1–9.
- Backlog (unverändert offen): gitleaks-CI, CVE-Scan, Renovate,
  PGP-Trigger.