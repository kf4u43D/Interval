# Plan d'implémentation — Étape 6 / V2.2

Date : 1er septembre 2026

## Objectifs

1. Scinder la scène portrait en deux zones stables : harmonie/accords à gauche et
   intervalles à droite.
2. Maintenir les dix accords en accès direct, Force to Scale, les treize gammes et le
   strummer sans masquer les neuf pads.
3. Supprimer la variante de test du parcours musicien et livrer une variante performance
   release-like coinstallable avec la V1.
4. Réduire le délai entre callback tactile et mise en file audio grâce à un acteur musical
   dédié, sans modifier les reducers purs ni l'ownership des notes.
5. Comparer les métriques de rendu V2.1/V2.2 sur la même SM-X620 et conserver une preuve
   explicite des limites de mesure.

## Baseline observée

- V2.1 installée : variante non optimisée `dev.intervaltablet.instrumented`.
- `gfxinfo` depuis le lancement : 1 777 frames, p50 10 ms, p90 24 ms, 655 frames
  janky et 901 occurrences `High input latency`.
- Batterie 80 %, alimentation secteur, Battery Saver désactivé : aucun bridage énergie
  ne suffit à expliquer la régression.
- Cette baseline mesure le rendu et les symptômes d'entrée Android, pas la latence
  acoustique tactile→haut-parleur.

## Conception

- Portrait : panneau gauche à environ 42 % et panneau droit à environ 58 %.
- Gauche : gamme compacte, Force to Scale, dix accords en deux colonnes, articulation et
  strummer.
- Droite : repère « main droite », utilitaires et grille 3×3 des intervalles.
- Paysage : conserver la lecture deux mains en plaçant aussi l’harmonie à gauche et la
  scène intervallique à droite ; la surface harmonique ne doit plus comprimer le strummer.
- Variante `performance` : minification/optimisation Release, signature debug locale,
  package distinct et compilation AOT `speed` sur la tablette de réception.
- Thread acteur : exécuteur mono-thread fermé avec le ViewModel, priorité Android audio ;
  les dispatchers injectés des tests restent inchangés.

## Risques et parades

- Accord ou gamme hors écran : assertions instrumentées sur les deux panneaux et les dix
  accords, avec cibles de 48 dp minimum.
- Réordonnancement des notes : un seul acteur FIFO est conservé ; aucune seconde file
  prioritaire n'est introduite.
- Famine UI par priorité excessive : priorité `THREAD_PRIORITY_AUDIO`, sans priorité
  temps réel ni travail supplémentaire dans l'acteur.
- Comparaison trompeuse : séparer métriques `gfxinfo`, délai logiciel et perception
  acoustique ; ne pas revendiquer une mesure loopback absente.
- Perte de données : la V1 reste intacte ; l'ancienne V2 de test n'est retirée qu'après
  installation et validation de la V2.2 performance.

## Tests et acceptation

- Tests JVM existants, plus contrat de création/fermeture du dispatcher si applicable.
- Test instrumenté portrait : panneau harmonie entièrement à gauche du panneau
  intervalles, dix accords accessibles et neuf pads distincts d'au moins 48 dp.
- Cinq Lint et assemblages Debug, Release, Benchmark, Instrumented, AndroidTest et
  Performance.
- `scripts/verify.ps1` et contrôle des quatre ABI.
- Installation SM-X620, compilation AOT, suite instrumentée, smoke tactile, mesure
  `gfxinfo`, nettoyage des packages temporaires.

## Résultat

Terminé le 1er septembre 2026.

- Le portrait 900 × 1 440 dp est partagé à 43/57 entre harmonie/strummer et intervalles ;
  les dix accords, les neuf pads, les articulations et les cordes restent visibles et
  tactiles à 48 dp minimum. Le paysage adopte la même séparation latérale.
- Le strummer qui tombait à 22 dp dans l’ancienne contrainte paysage retrouve une cible
  d’au moins 48 dp et MIDI Learn reste accessible dans son panneau défilable.
- L’acteur musical possède un thread mono-thread nommé, fermé avec le ViewModel et placé
  à `THREAD_PRIORITY_AUDIO`. La variante `performance` est minifiée, utilise le moteur
  natif Release, porte le package `dev.intervaltablet.performance` et est compilée AOT
  `speed` sur la SM-X620.
- Le scénario contrôlé de 108 frappes donne p50 14 ms, p90 17 ms, p95 18 ms et p99 19 ms ;
  12 signaux `High input latency` sur 121 frames, contre 901/1 777 dans la baseline
  V2.1 cumulative. Ces fenêtres différentes prouvent la disparition de la régression
  grossière, pas une certification tactile→audio.
- Le moteur négocie 48 kHz, 96 trames/burst et un buffer de 192 trames ; après le stress,
  la file est à 0/16 avec 0 xrun, 0 événement perdu, 0 reprise et 0 erreur.
- Gate final : 136/136 domaine, 161/161 application, 8/8 instrumentés, CTest 2/2, cinq
  Lint sans issue, tous les assemblages et le contrôle runtime quatre ABI réussis.
- La tablette ne conserve que la V1 `dev.intervaltablet.debug` et la V2.2
  `dev.intervaltablet.performance`; les fichiers temporaires de diagnostic ont été retirés.

La mesure loopback tactile/MIDI→audio, le vrai multi-touch, TalkBack, le MIDI USB réel,
le rendu soutenu à 90 Hz et le soak audio de 60 minutes restent des validations matérielles
distinctes.
