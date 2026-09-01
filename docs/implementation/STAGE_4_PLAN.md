# Étape 4 — plan et journal d’implémentation V2

## Résultat utilisateur attendu

Interval Tablet fournit une tranche V2 jouable : les fonctions assignables respectent
leur sens musical, les huit parcours Tone Row fonctionnent de façon déterministe et la
Console MIDI permet de construire puis sauvegarder un mapping Note/CC sans éditer de
fichier ni provoquer de note parasite pendant l'apprentissage.

## Périmètre inclus

- Same Interval, Same Pitch, Random Interval et Chromatic Shift momentané.
- Annulation Record par seconde pression, navigation Tone Row pendant Pause et vélocité
  MIDI live en lecture manuelle.
- Random Tone Row fidèle et quatre modes Auto-Transpose/Auto-Translate cumulés par cycle.
- Éditeur/MIDI Learn transactionnel pour le catalogue `MidiAction` actuel : Note/CC,
  canal capturé ou Omni, seuil CC, conflits, suppression, reset, Save et Cancel.
- Persistance par les schémas existants, projection Compose dédiée et accessibilité.
- Mise à jour des spécifications, de la matrice, du rapport, du changelog et de l'état Codex.

## Périmètre explicitement différé

- Rest, Random Step et Ratchet dans la séquence ; Ratchet nécessite des Note On futurs
  annulables par génération et une étape dédiée.
- Horloge/transport MIDI sortants, SPP, Active Sense musical et port MIDI virtuel Android.
- Sélection gamme/clé/accord/preset par mapping, import/export et profils par périphérique.
- Gammes étendues, mode Preserve Root/Key, scopes de presets et patch audio par preset.
- Optimisation 90 Hz et protocoles matériels sans périphériques USB MIDI/loopback disponibles.
- Fonctions CV, réseau, Scala, microtonalité, MPE et MIDI 2.0.

## Invariants

- Une action tenue est libérée par sa source d'origine, y compris après changement de
  mapping, mode, destination, déconnexion ou Panic.
- Chromatic Shift ne crée aucune note, reste éphémère et disparaît à Release/Purge/Panic.
- Le message capturé par MIDI Learn n'est jamais routé, transmis ou interprété comme rappel.
- Save remplace le mapping une seule fois ; Cancel et le brouillon ne sont jamais persistés.
- Le domaine reste pur et déterministe ; graines et ordre des événements sont explicites.
- Pause n'avance pas l'horloge et un déplacement manuel ne redémarre pas le transport.
- Aucun changement n'est requis dans le callback audio C++.

## Changements prévus par module

### `domain/`

- Étendre l'état et les actions de l'instrument pour les répétitions, Random et les
  modificateurs chromatiques tenus.
- Ajouter un reducer transactionnel d'édition de mapping.
- Étendre Tone Row aux huit modes, au compteur de cycle, à Pause navigable, à la vélocité
  live et à l'annulation de prise.
- Adapter le routeur afin que toute action nécessitant une libération reçoive Note Off,
  front CC descendant et purge.

### `app/`

- Retirer les anciens détournements Random/Chromatic Shift du coordinateur.
- Intercepter la capture MIDI Learn dans l'acteur avant la politique de preset et le routeur.
- Ajouter intents, projection étroite et panneau Compose d'édition accessible.
- Étendre les adaptateurs UI Tone Row aux quatre nouveaux modes.

### `app/src/main/cpp/`

- Aucun changement prévu.

### Tests et documentation

- Étendre les tests domaine, routeur, coordinateur, ViewModel, projections, sérialisation
  et accessibilité Compose.
- Mettre la documentation en accord avec le comportement réellement livré et les limites.

## Risques

| Risque | Détection | Mitigation |
|---|---|---|
| Note ou modificateur bloqué | tests Release/Purge/Panic et transitions de mapping | ownership par source et action `requiresRelease` explicite |
| Capture MIDI qui joue ou rappelle un preset | faux repository et tests acteur | interception avant `PresetMidiPolicy`/routeur, consommation du relâchement |
| Brouillon perdu ou écrasant un mapping concurrent | tests baseline obsolète | Save compare la baseline et exige confirmation/rechargement |
| Cycle Auto décalé d'une note | fixtures 1/5/7/12 et Play Once | compteur d'émissions logique explicite, reset testé |
| Régression de persistance | round-trip et fixtures historiques | aucun changement de schéma pour ce lot |
| Régression UI/performance | tests projections/Lint et mesure appareil si disponible | panneau séparé, projection triée seulement à changement du mapping |

## Commandes de baseline

- `scripts/doctor.ps1` : 0 erreur, avertissement attendu `kotlinc` autonome absent.
- `scripts/verify-domain.ps1` : réussi.
- `scripts/verify-native.ps1` : 2/2 réussi.
- `gradlew.bat :domain:test :app:lintDebug :app:testDebugUnitTest :app:assembleDebug` : réussi.
- `scripts/verify.ps1` : réussi, runtime APK complet pour quatre ABI.

## Journal d’exécution

- 2026-09-01 : audit de la notice et du code ; séparation entre corrections de fidélité,
  extensions V2 et fonctions matérielles volontairement hors périmètre.
- 2026-09-01 : Ratchet différé car le scheduler courant ne possède qu'une release future ;
  l'ajouter sans génération annulable créerait un risque de Note On tardif.
- 2026-09-01 : Mapping v1 conservé, car les quinze variantes existantes couvrent ce lot.

## Résultats de validation

| Validation | Commande/protocole | Résultat | Preuve ou limite |
|---|---|---|---|
| Structure | `scripts/verify-structure.ps1` | À exécuter | |
| Domaine | `scripts/verify-domain.ps1` | À exécuter | |
| DSP/JNI hôte | `scripts/verify-native.ps1` | À exécuter | aucun changement natif prévu |
| Android | `scripts/verify.ps1` | À exécuter | SDK/NDK disponibles |
| Instrumentation | `connectedInstrumentedAndroidTest` ciblé | Selon appareil | tablette requise |
| MIDI USB | `docs/HARDWARE_TEST_PROTOCOL.md` | Bloqué matériel | clavier et synthé/interface requis |

## Critères de sortie

- [ ] Correctifs de fidélité et huit modes couverts par oracles déterministes.
- [ ] MIDI Learn/éditeur complet du catalogue actuel, persistant et accessible.
- [ ] Aucun schéma historique cassé et aucune note/modificateur bloqué.
- [ ] Gate logiciel complet sans avertissement nouveau important.
- [ ] Documentation, matrice, `.codex/state.json` et `CHANGELOG.md` à jour.
- [ ] Limites matérielles et dettes V2 suivantes explicitement marquées.
