# Étape 5 — V2.1 surface de performance et Force to Scale

## Précondition

L’étape 4 est acceptée. La V1 installée doit rester disponible sur la tablette et la
V2.1 doit utiliser un package coinstallable et un nom visible distinct.

## Objectif utilisateur

La différence avec la V1 est immédiatement visible et audible : les pads laissent de la
place aux variantes d’accords et aux gammes sur l’écran principal, Force to Scale contraint
les notes générées, et le synthé expose le temps, le feedback et le mix du delay.

## Lot à livrer

- Nommer explicitement la variante coinstallable « Interval Tablet V2 » sans écraser la V1.
- Compacter les neuf pads tout en conservant des cibles tactiles accessibles d’au moins 48 dp.
- Exposer toutes les variantes d’accords sur la page de performance, sans ouvrir la Console.
- Ajouter Force to Scale au domaine pur, à la persistance et aux presets ; il quantifie les
  notes générées par l’instrument vers la note la plus proche de la gamme active, avec égalité
  résolue vers le bas, sans modifier le MIDI PassThru.
- Compléter la bibliothèque avec les modes diatoniques et mineurs standards manquants et les
  rendre sélectionnables sur la page principale.
- Exposer séparément le temps, le feedback et le mix du delay avec prévisualisation continue.

## Tests et preuve

- Oracles domaine pour quantification chromatique, égalité, accords, shift et désactivation.
- Round-trip et migration des réglages/presets historiques avec Force to Scale désactivé.
- Tests Compose des cibles de 48 dp, accords/gammes visibles et trois contrôles du delay.
- Gate domaine, natif, JVM, Lint et assemblage, puis installation/réception sur la SM-X620.

## Porte de sortie

La V1 demeure installée, la V2.1 porte un nom distinct, les nouveaux contrôles sont visibles
sur la tablette et le gate logiciel complet ne présente aucune nouvelle régression importante.
