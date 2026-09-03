# Étape 5 — plan V2.1 surface de performance et Force to Scale

**Statut : complète le 1er septembre 2026.**

## Résultat attendu

La V2.1 doit se distinguer sans ambiguïté de la V1 installée et rendre ses ajouts directement
jouables : accords et gammes sur la surface principale, pads plus compacts, quantification
Force to Scale et delay complet.

## Contrat retenu

- La variante coinstallable `instrumented` garde son package séparé et prend le libellé
  « Interval Tablet V2 » ; la V1 `dev.intervaltablet.debug` n’est pas remplacée.
- Les pads gardent une cible minimale de 48 dp, mais ne réclament plus 72 dp et la scène est
  plafonnée afin de libérer une zone de contrôle harmonique permanente.
- Force to Scale s’applique uniquement aux notes créées par le moteur d’instrument. Le MIDI
  PassThru reste bit-identique. Une égalité de distance choisit la note inférieure.
- Le changement de Force to Scale affecte les prochaines notes et ne coupe pas les notes déjà
  tenues ; leur Note Off reste possédé par leur source.
- Les gammes ajoutées restent en tempérament égal 12-TET ; Scala et microtonalité demeurent
  hors périmètre.

## Risques et mitigations

| Risque | Mitigation |
|---|---|
| Notes bloquées au changement de quantification | état des notes actives inchangé ; tests Release/Panic |
| Presets historiques cassés | nouveau champ optionnel à défaut `false` et fixtures de migration |
| Surface trop dense | rangées horizontales compactes et tactiles, scène adaptative |
| Delay trompeur | trois paramètres DSP existants exposés et testés séparément |
| V1 écrasée | seul l’APK au suffixe `.instrumented` est installé |

## Plan et validations

- [x] Étendre le domaine, la bibliothèque de gammes et les oracles Force to Scale.
- [x] Câbler ViewModel, projections, réglages et presets.
- [x] Recomposer la surface principale et compléter le panneau du synthé.
- [x] Différencier le nom et la version de la V2.1.
- [x] Exécuter les validations domaine, natives, JVM, Lint et APK.
- [x] Installer et recevoir la V2.1 sur la tablette sans toucher à la V1.
- [x] Mettre à jour spécifications, matrice, statut, changelog et état Codex.

## Résultat de validation

- Diagnostic : 0 erreur, avertissement attendu pour `kotlinc` autonome absent.
- Domaine : 136/136 tests sur 12 suites ; application : 161/161 sur 19 suites.
- Natif : CTest 2/2 ; quatre rapports Lint sans issue.
- Debug, Release non signé, Benchmark, Instrumented et AndroidTest assemblés ; runtimes
  natifs contrôlés sur `arm64-v8a`, `armeabi-v7a`, `x86` et `x86_64`.
- SM-X620/API 36 : 7/7 tests en 17,279 s ; V1 `0.1.0` et V2.1 `0.2.1` seules conservées.
- Réception UI : dix accords visibles, Force to Scale présent, pads mesurés à 272 px de
  haut sur l’écran 1800×2880 et contrôle delay temps/feedback exercé par sémantique.
