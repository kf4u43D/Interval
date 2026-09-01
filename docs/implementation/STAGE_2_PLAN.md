# Étape 2 — Tone Row, transformations, transport et presets

## Résultat utilisateur attendu

Livrer une seconde tranche verticale jouable sans matériel obligatoire : l'utilisateur
enregistre une série depuis les pads ou une entrée MIDI, la parcourt manuellement, la joue
automatiquement avec une séquence de mouvements éditable, la transforme en direct et la
synchronise soit à une horloge interne soit au MIDI Clock. La série, le contexte musical,
le routage et les options de transport sont restaurés après recréation de l'application.

L'interface conserve la surface intervallique et Panic de l'étape 1. Elle ajoute une
timeline originale, les curseurs de série et de séquence, un transport lisible et des
transformations accessibles, sans reproduire le panneau d'un produit tiers.

## Baseline initiale observée le 20 août 2026

- La porte 1 est terminée logiciellement ; seules ses validations tablette, USB MIDI et
  audio réel restent ouvertes et ne seront pas simulées comme preuves matérielles.
- Le workspace n'est pas un dépôt Git local. Aucun remote, commit, push ou publication ne
  sera créé pendant cette étape.
- `doctor.ps1` réussit avec le seul avertissement non bloquant `kotlinc` autonome absent.
- `verify-domain.ps1` réussit sur les 41 tests existants et `verify-native.ps1` réussit
  sur 1/1 test CTest avant modification.
- Le domaine contient des ébauches `ToneRowEngine` et `Transport`, non raccordées à
  l'application et insuffisantes pour la porte 2 ; elles seront durcies ou remplacées.
- Java 17, Android SDK 36, NDK 28.2 et CMake 3.22.1 sont disponibles localement. Aucun
  appareil ni périphérique MIDI réel n'est requis pour les preuves déterministes.

## Périmètre inclus

- Machine d'état pure `Idle / Recording / ManualPlayback / AutoPlaying / Paused`, avec
  transitions explicites Record, Play, Pause, Stop, Continue, Restart et Play Once.
- Enregistrement de degrés/contours et vélocités, unicité des classes de hauteur,
  recherche directionnelle de la prochaine classe libre, fin précoce et fin automatique.
- Lecture manuelle par les neuf intervalles : déplacement d'indice, `0` rejoué et Undo
  réinterprété en Restart lorsque le mode Tone Row le demande.
- Séquence automatique éditable et non vide, défaut/reset `{+1}`, curseur indépendant.
- Prime, Retro, Random déterministe à seed injecté et Pendulum sans double extrémité ;
  inversion, transposition, translation, octave bornée et remise à référence.
- Horloge interne pilotée par timestamps injectés et MIDI Clock à 24 PPQ ; Start revient
  au début, Continue reprend et Stop conserve la position.
- Tempo, durée de note et division d'horloge validés, sans double tick pour un même instant
  ni réordonnancement des événements.
- Presets versionnés et migrables contenant série, séquence, mapping, gamme, clé, accord,
  routage, transformations et paramètres de transport.
- Politique déterministe Program Change et Song Select, documentée et couverte par tests.
- UI Compose adaptative : timeline, curseurs, statut, transport et transformations, avec
  réglages secondaires inaccessibles sous Performance Lock.
- Réexécution intégrale des garanties de l'étape 1, notamment leases, Panic, hotplug et
  files bornées.

## Périmètre explicitement différé

- Import/export de fichiers de presets ; la persistance interne robuste suffit à la porte.
- Nouvelles familles d'effets audio, synthèse enrichie et optimisation DSP : étape 3.
- Mesure physique du jitter MIDI, connexion USB réelle, multi-touch/tablette et écoute
  audio : protocole matériel restant, jamais déclaré réussi sans appareil.
- Microtonalité, Scala, MPE, MIDI 2.0, CV, réseau et USB audio multicanal : hors cycle.

## Décisions de comportement livrées

- La banque comporte 128 slots internes `0…127`, affichés `1…128`. Program Change cible
  le slot zero-based et respecte le canal d'entrée ; Song Select est global.
- Un slot absent n'est pas consommé et poursuit le routage normal. En PassThru, aucun
  rappel de preset n'est effectué, même pour un slot existant.
- Panic et TogglePassThrough restent au contraire consommés en PassThru : cette exception
  de sécurité garantit un chemin vers le silence et la sortie du mode.
- Le Stop local revient à Tone Row `Idle` et transport `Stopped`. Un Stop MIDI libère la
  voix automatique, conserve les positions et place Tone Row en `Paused`; Continue reprend
  sans nouvelle note avant le tick qualifiant suivant.
- Tout rappel de preset exécute d'abord Panic, conserve la destination physique ouverte et
  restaure le snapshot en `Idle`/`Stopped`, sans voix, deadline, compteur ni curseur live.
- Les changements de gamme, clé, plage ou wrap pendant Record terminent d'abord la prise,
  afin de ne jamais mélanger deux référentiels dans une rangée.

## Invariants

- Toute règle Tone Row et transport est une transition Kotlin pure et déterministe ; le
  domaine ne lit ni horloge système, ni I/O, ni hasard global.
- Les actions tactiles et MIDI rejoignent le même reducer et gardent un ordre logique
  unique avec les changements de transport et de port.
- Un tick musical possède un timestamp unique ; les changements de tempo ne réordonnent
  aucun événement déjà accepté.
- Toute note émise est bornée à la plage et possède une libération ordonnée, y compris
  Pause, Stop, Restart, Panic, perte de port et surcharge de mailbox.
- L'horloge interne et le MIDI Clock sont mutuellement exclusifs.
- Une série enregistrée contient au plus une occurrence de chaque classe disponible dans
  la gamme de référence ; une fin complète bascule automatiquement en lecture manuelle.
- Play Once s'arrête après exactement la traversée définie par son parcours actif.
- Les files MIDI de l'étape 1 restent bornées et les générations de ports restent la
  source d'autorité pour ignorer les paquets obsolètes.
- MIDI reste prioritaire et indépendant de l'état du moniteur audio.
- Aucune nouvelle dépendance runtime n'est introduite.

## Changements prévus par module

### `domain/`

- Stabiliser les modèles immuables de série, transformations, séquence et curseurs.
- Remplacer les comportements incomplets par un reducer exhaustif et des événements de
  notes explicitement horodatables par l'adaptateur.
- Ajouter un reducer de transport couvrant source d'horloge, 24 PPQ, division, Start,
  Continue, Stop, pause, reprise et Play Once.
- Étendre le mapping typé afin que les commandes Tone Row passent par la même route que
  leurs équivalents tactiles.

### `app/`

- Raccorder Tone Row et transport au coordinateur sérialisé, sans seconde file d'ordre.
- Ajouter un ordonnanceur d'horloge interne à temps injecté ; les ticks produits restent
  des actions du domaine et sont stoppés au cycle de vie ou lors du passage au MIDI Clock.
- Interpréter Clock, Start, Continue, Stop, Program Change et Song Select en conservant le
  filtrage session/génération de l'étape 1.
- Versionner les presets et leurs migrations, sérialiser les écritures et restaurer avant
  d'accepter les commandes de performance.
- Étendre l'état UI et les callbacks sans dupliquer les règles musicales.

### `app/src/main/cpp/`

- Aucun enrichissement DSP. Vérifier seulement la compatibilité des événements de notes,
  Stop/Panic et la non-régression du test hôte.

### Tests et documentation

- Fixtures d'enregistrement à 5, 7 et 12 classes, fins précoces/complètes et recherche
  directionnelle aux limites.
- Tests de chaque mode, transformation et ordre de curseur, dont propriétés Pendulum et
  reproductibilité Random.
- Tests byte stream MIDI Clock/Start/Continue/Stop, horloge simulée, changement de source,
  absence de double tick et Play Once exact.
- Tests de round-trip/migration des presets et restauration après recréation.
- Tests d'intégration coordinateur, UI pure, accessibilité et Performance Lock.
- Mise à jour du statut, changelog, spécifications MIDI, matrice, rapport de vérification,
  état Codex et manifeste de structure avec résultats exacts.

## Risques

| Risque | Détection | Mitigation |
|---|---|---|
| Note bloquée à Pause/Stop/Restart | tests d'ordre NoteOn/NoteOff/Panic | ownership par source Tone Row et release avant transition |
| Double tick ou tick en retard | horloge simulée, timestamps dupliqués | déduplication dans le reducer et source d'horloge exclusive |
| Divergence UI/MIDI | tests mêmes actions/mêmes transitions | adaptateurs minces vers un seul reducer |
| Traversée Play Once ambiguë | fixtures Prime/Retro/Random/Pendulum | compteur explicite d'émissions défini dans l'état |
| Duplicat de classe en enregistrement | propriétés 5/7/12 et limites | recherche ordinale pure dans le sens demandé |
| Preset ancien illisible | fixtures schémas antérieurs/corrompus | migration versionnée, validation puis repli conservateur |
| Écriture DataStore perdue | tests d'écritures rapides/recréation | snapshot complet sérialisé par acteur |
| Jitter ou flood Clock | tests de rafale et mailbox bornée | coalescence interdite des ticks musicaux, récupération Panic |
| Régression porte 1 | gate complet et suites existantes | conservation des leases/générations/buffers et revue dédiée |
| Débordement UI compact/font scale | compilation, lint et inspections de contraintes | timeline scrollable, layout adaptatif, cibles >=56 dp |
| Budget de frame 90 Hz dépassé | mesures Release AOT contrôlées sur tablette | isoler les coûts UI, conserver un scénario fixe et suivre p50/p90/jank sans masquer le seuil strict |

## Stratégie de test

- Domaine : matrice FSM complète ; rangées de 5, 7 et 12 notes ; doublons, vélocités,
  bornes ; parcours manuels ; toutes transformations et combinaisons principales.
- Transport : 24 impulsions par noire, divisions supportées, Start/Continue/Stop,
  internal/MIDI exclusifs, timestamps identiques, tempo en vol et Play Once.
- MIDI : fragmentation/running status avec temps réel imbriqué, Program Change et Song
  Select, paquets obsolètes, changement de génération et rafales d'horloge.
- Persistance : schéma courant, migrations depuis réglages étape 1, valeurs invalides,
  checksum/version et restauration de toutes les propriétés utiles.
- Application : ordre entrée/transport/sortie, Panic aux arrêts, lifecycle, changement de
  port, saturation et reconstruction avec faux repository/horloge.
- UI : timeline/cursors dérivés du domaine, états et sémantiques, lock, layouts compacts ;
  les gestes réels restent documentés si aucun émulateur n'est présent.

## Commandes de baseline et de sortie

```powershell
powershell -ExecutionPolicy Bypass -File scripts/doctor.ps1
powershell -ExecutionPolicy Bypass -File scripts/verify-domain.ps1
powershell -ExecutionPolicy Bypass -File scripts/verify-native.ps1
.\gradlew.bat :domain:test
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
```

## Journal d'exécution

- 20 août 2026 : relecture complète de la documentation obligatoire et confirmation que
  la porte 1 n'a plus que des validations matérielles ouvertes.
- 20 août 2026 : baseline doctor/domaine/natif réussie ; `kotlinc` autonome reste le seul
  avertissement de l'environnement et le repli Gradle est opérationnel.
- 20 août 2026 : audits parallèles ouverts pour domaine, persistance/MIDI et UI ; le
  raccord central et l'ordre du coordinateur restent pilotés dans le lot principal.
- 20 août 2026 : reducer Tone Row complété avec invariants de taille/unicité, fixtures
  5/7/12, quatre parcours, transformations, Play Once et séquence bornée.
- 20 août 2026 : reducer de transport et raccord coordinateur livrés ; voix automatiques
  possédées par origine, gate planifiée, MIDI Stop/Continue et sources d'horloge exclusives.
- 20 août 2026 : snapshots/presets v2, migration v1, banque 128 slots et politique
  Program Change/Song Select raccordés à DataStore et à l'acteur applicatif.
- 20 août 2026 : deck Compose Tone Row raccordé avec timeline, curseurs, transport,
  transformations, séquence et presets, avec blocage des réglages secondaires sous Lock.
- 20 août 2026 : spécifications, matrice et protocole matériel alignés sur les décisions
  livrées ; aucune validation tablette, USB MIDI ou audio réelle n'est revendiquée.
- 20 août 2026 : acteur ViewModel testé avec ses adaptateurs injectés, autosauvegarde
  durable raccordée aux prises tactiles/MIDI et gate externe asservi aux pulses observés.
- 20 août 2026 : gate forcé, vérification globale, lint et APK réussis ; résultats exacts
  centralisés dans `docs/VERIFICATION_REPORT.md`.
- 21 août 2026 : réception partielle sur Samsung SM-X620/API 36/90 Hz : installation,
  lifecycle court, pad simple, Tone Row, presets, rotation, police 1,3× et Audio Monitor
  observés ; vrai multi-touch, TalkBack et MIDI USB non exécutés.
- 21 août 2026 : gonflement de la timeline et placement sous barres système corrigés ;
  test instrumenté final direct `am instrument` réussi (1 test, 1,593 s), après une passe
  antérieure de `connectedDebugAndroidTest` également verte.
- 21 août 2026 : mesure Release AOT fixe 120 BPM/division 6/7 pas/30 s. Après isolation,
  frame p50 16 ms contre 18 ms avant et jank legacy 63,20 % contre 65,90 %, mais p90 24 ms
  et jank strict 99,58 % ; la dette de performance 90 Hz reste ouverte.

## Résultats de validation

| Validation | Commande/protocole | Résultat courant | Preuve ou limite |
|---|---|---|---|
| Doctor | `scripts/doctor.ps1` | Réussi avec 1 avertissement | `kotlinc` autonome absent |
| Structure | `scripts/verify-structure.ps1` | Réussi lors du gate logiciel | aucun avertissement |
| Domaine baseline | `scripts/verify-domain.ps1` | Réussi | 41 tests avant étape 2 |
| DSP/JNI baseline | `scripts/verify-native.ps1` | Réussi | 1/1 test CTest |
| Domaine et application Stage 2 | `:domain:test :app:testDebugUnitTest` | Réussi | 79/79 domaine, 91/91 application |
| Lint | `:app:lintDebug` | Réussi | `No issues found.` |
| Assemblages Android | debug, release, test Android | Réussi | trois artefacts assemblés |
| Instrumentation UI finale | `am instrument` sur SM-X620 | Réussi | 1 test, 0 échec, 1,593 s ; passe `connectedDebugAndroidTest` antérieure verte |
| Gate global logiciel | scripts et tâches Gradle applicables | Réussi | domaine, CTest, JVM, lint et assemblages verts |
| MIDI USB / Clock réel | protocole matériel | Non exécuté | périphérique absent |
| UI sur tablette | protocole matériel | Partiel | SM-X620/API 36/90 Hz reçue ; vrai multi-touch et TalkBack non exécutés |
| Performance UI | Release AOT, 120 BPM/div. 6/7 pas/30 s | Dette ouverte | p50 16 ms, p90 24 ms ; legacy 63,20 %, strict 99,58 % |
| Soak audio | protocole matériel | Partiel | 3 minutes seulement, 60 minutes encore requises |

## Critères de sortie

- [x] Porte 2 spécifiée et implémentée pour tous les critères vérifiables sans matériel.
- [x] Les invariants de la porte 1 restent dans les mêmes suites de non-régression.
- [x] Tests dédiés ajoutés pour domaine, transport, parser, migrations, coordinateur et UI ;
  le résultat chiffré du gate complet est consigné une seule fois dans le rapport final.
- [x] Documentation, matrice et changelog reflètent le comportement livré. Le manifeste
  reste sous la responsabilité du lot principal ; l'état Codex distingue appareil reçu,
  MIDI USB absent et soak incomplet.
- [x] Limites matérielles explicitement marquées sans preuve simulée ; la réception
  tablette partielle ne ferme ni la performance 90 Hz ni la porte matérielle.
