# Preuve Stage 6 / V2.2 — Samsung SM-X620

Date : 1er septembre 2026

Appareil : Samsung SM-X620, Android 16/API 36, écran 2 880 × 1 800 en paysage lors de la
mesure, densité 320 dpi, rafraîchissement 90 Hz.

Commit d’implémentation : `9255a8309f674247565d270231a9abee445a09c8`.

## Installation reçue

- V1 conservée : `dev.intervaltablet.debug`, `0.1.0-dev-debug`.
- V2.2 installée : `dev.intervaltablet.performance`, `0.2.2-dev-performance`.
- APK V2.2 : 9 227 116 octets, SHA-256
  `CD708425DEC4671AD3DAC43F5B699A8C3FB9EE555DF994A79030231F33E7AE14`.
- Installation `adb install -r`, puis compilation ART `cmd package compile -m speed -f`
  réussies.
- Aucun package Instrumented/Benchmark/Test Interval Tablet conservé ; les trois dumps
  XML temporaires ont été supprimés.

## Ergonomie deux mains

Le test Compose instrumenté force une fenêtre 900 × 1 440 dp. Il vérifie :

- panneau harmonie entièrement à gauche du panneau intervalles ;
- panneau intervalles plus large ;
- dix accords visibles et cliquables ;
- neuf pads contenus dans le panneau droit ;
- pads, trois articulations et cordes du strummer à au moins 48 dp.

Le scénario paysage séparé a détecté avant correction une corde de seulement 44 px à
320 dpi, soit 22 dp. Après surface harmonique latérale et articulations en grille 2×2,
le même test respecte 48 dp. L’éditeur MIDI Learn reste accessible et son flux complet
conflit → Replace → Save/Cancel passe.

## Rendu et entrée Android

Baseline V2.1 instrumentée, cumulative depuis lancement :

- 1 777 frames ;
- p50 10 ms, p90 24 ms, p95 34 ms, p99 46 ms ;
- 655 frames janky ;
- 901 signaux `High input latency` et 632 `Slow UI thread`.

V2.2 Performance après compilation AOT, 108 frappes réparties sur les neuf pads :

- 121 frames ;
- p50 14 ms, p90 17 ms, p95 18 ms, p99 19 ms ;
- 12 signaux `High input latency` ;
- GPU p50/p90 6 ms.

Les fenêtres ne sont pas identiques : ces nombres qualifient la disparition de la
régression grossière de la variante de test, pas une campagne A/B soutenue à 90 Hz.

## Diagnostic audio après stress

- sample rate : 48 000 Hz ;
- frames/burst : 96 ;
- buffer : 192 frames, soit 4 ms de buffer nominal ;
- file d’événements : 0/16 ;
- xruns : 0 ;
- événements perdus : 0 ;
- redémarrages : 0 ;
- dernière erreur : aucune.

## Gates exécutés

- `doctor.ps1` : 0 erreur, avertissement attendu `kotlinc` absent ;
- domaine Kotlin : 136/136 sur 12 suites ;
- application JVM : 161/161 sur 19 suites ;
- instrumentation SM-X620 : 8/8 ;
- CTest natif : 2/2 ;
- Lint Debug, Release, Benchmark, Instrumented et Performance : 0 issue ;
- assemblages Debug, Release, Benchmark, Instrumented, Performance et AndroidTest :
  réussis ;
- `scripts/verify.ps1` : réussi, runtimes natifs validés sur quatre ABI.

## Limites explicites

`gfxinfo` ne mesure ni le temps toucher→haut-parleur, ni une latence MIDI, ni un
round-trip. Une mesure loopback, le vrai multi-touch, TalkBack, le MIDI USB physique,
le hotplug, le rendu soutenu à 90 Hz et le soak audio de 60 minutes restent ouverts.
