# Preuve Stage 7 / V2.3 — Samsung SM-X620

Date : 1er septembre 2026

Appareil : Samsung SM-X620, API 36

Commit d’implémentation : `385c574ce9b1ec37a95f81fe4b50d511eb2bf646`

## Gate logiciel

- Domaine Kotlin pur : 138/138 tests sur 12 suites.
- Application JVM : 162/162 tests sur 19 suites.
- Total Kotlin/JVM : 300/300, zéro échec, erreur ou ignoré.
- CTest : 2/2.
- Lint Debug, Release, Benchmark, Instrumented et Performance : zéro issue.
- Assemblages Debug, Release, Benchmark, Instrumented, AndroidTest et Performance : réussis.
- Runtime natif : quatre ABI contrôlées.

## Réception tablette

- Suite instrumentée complète : 8/8 en 28,464 s.
- Fenêtre portrait forcée 900 × 1 440 dp : treize gammes, dix accords, neuf pads,
  articulations et cordes visibles avec cibles d’au moins 48 dp.
- Le test conserve le pointeur appuyé sur Triad et observe le callback d’accord avant
  pointer-up.
- Variante installée : `dev.intervaltablet.performance`, version
  `0.2.3-dev-performance`, compilation ART `speed`.
- Lancement au premier plan sans `FATAL EXCEPTION`.
- Packages Interval conservés : `dev.intervaltablet.debug` (V1) et
  `dev.intervaltablet.performance` (V2.3) uniquement.

## Artefact installé

- Chemin de build : `app/build/outputs/apk/performance/app-performance.apk`.
- Taille : 9 243 536 octets.
- SHA-256 : `E40AE016F1A8FCFE20A23629B5F4D17C2CB4BC1E32F3741380BBF5ED02C1603B`.
- Moteur natif Release, APK minifié, signature debug locale de la variante Performance.

## Limites

L’écoute subjective de l’arpège/revoicing, MIDI USB réel, TalkBack, vrai multi-touch,
loopback tactile/MIDI→audio, rendu soutenu à 90 Hz, hotplug et soak ne sont pas certifiés
par cette campagne.
