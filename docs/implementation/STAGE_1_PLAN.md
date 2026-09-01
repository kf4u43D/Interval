# Étape 1 — instrument intervallique MIDI complet

## Résultat utilisateur attendu

Livrer une tranche verticale jouable sans matériel obligatoire : les neuf pads tactiles et
les messages MIDI mappés traversent le même moteur déterministe, produisent des accords
corrects, conservent une polyphonie par origine et libèrent toujours leurs notes. L'écran
paysage présente clairement la note, le degré, les cibles, la plage, le routage et l'état
des ports, avec un panneau MIDI non modal. L'audio existant reste un moniteur secondaire.

## Baseline initiale observée le 20 août 2026

- Le workspace fourni n'est pas encore un dépôt Git local.
- Le dépôt distant désigné était vide lors de l'audit initial. Aucun remote et aucune
  publication ne seront créés pendant cette étape.
- `doctor.ps1` passe après mise à disposition locale de Java 17 et installation des
  composants Android officiels épinglés ; seul `kotlinc` autonome reste absent.
- La structure et les tests natifs hôte passent.
- Le lancement Gradle Windows a révélé deux défauts d'environnement/portabilité : cache
  utilisateur non accessible et appel Windows PowerShell ne trouvant pas `Get-FileHash`.
  Ils ont été corrigés pendant l'étape ; la distribution Gradle téléchargée reste
  contrôlée par son SHA-256 officiel.
- Les validations USB MIDI, multi-touch sur tablette et audio Oboe restent volontairement
  hors de cette session faute de matériel.

## Périmètre inclus

- Navigation diatonique `-14…+14`, Home, zéro, Undo, ancre externe, wrap/clamp, clé,
  gamme, plage et canal de sortie.
- Prévisualisation pure des mouvements avec indication `NONE`, `CLAMPED` ou `WRAPPED`, et
  calcul du degré courant pour l'UI.
- Dix définitions d'accord (Off et neuf voicings), doublures conservées, harmonies hors
  plage omises et vélocités conformes.
- Sources tactiles identifiées par vrai pointer, sources MIDI identifiées par session,
  polyphonie et comptage exact des instances.
- Parseur MIDI 1.0 incrémental borné et resynchronisable : Channel Voice, running status,
  temps réel imbriqué, System Common, SysEx et Note On vélocité zéro.
- Mapping notes/CC, filtrage Omni/canal, seuil et relâchement CC, quatre modes de routage,
  ancrage Last Note et leases conservant la route décidée au Note On.
- Coordinateur applicatif à file bornée et ordre unique pour UI, MIDI reçu, routage,
  changements de port, erreurs et cycle de vie.
- Panic local et routé : Note Off explicites, CC 123/120 par canal concerné, purge des
  instances et leases avant fermeture ou changement de destination.
- Découverte Android MIDI, états de connexion explicites, fermeture déterministe,
  notifications de déconnexion/erreur et identité descriptive persistable.
- Réglages versionnés pour contexte musical, plage/wrap, canaux, mode, ports préférés,
  mapping par défaut réinitialisable et Performance Lock.
- UI Compose paysage originale, adaptative et accessible, avec grille multi-touch,
  utilitaires, HUD de scène et panneau MIDI non modal.
- Réparation des scripts Windows nécessaire pour exécuter les validations déclarées.

## Périmètre explicitement différé

- Tone Row, Auto Play, Clock musical et transport, au-delà du parsing sûr des messages
  temps réel requis par MIDI 1.0 : étape 2.
- Enrichissement du DSP, effets, optimisation audio et validation Oboe : étape 3.
- Microtonalité, Scala, MPE, MIDI 2.0, CV, réseau et USB audio multicanal : hors cycle.
- Reconnexion et validation de jitter sur périphériques USB réels : implémentées de façon
  testable, mais leur preuve matérielle est différée.
- Optimisation dédiée du portrait : le layout livré est adaptatif et tolère la rotation,
  sans verrou d'orientation, mais la validation scénique cible reste le paysage tablette.

## Invariants

- Toute règle musicale reste dans `domain/` et suit `state + action -> state + événements`.
- Une source physique possède ses propres instances ; deux doigts sur le même pad restent
  indépendants.
- La route, la destination logique et le canal choisis au Note On survivent aux changements
  de mode jusqu'au Note Off ou à une purge explicite.
- Après Panic, déconnexion, changement de destination ou arrêt de l'activité, les registres
  d'instances et de leases sont vides.
- Les événements sortants d'une transition gardent des timestamps non décroissants.
- Toute note générée appartient à la plage ; une harmonie hors plage est omise, jamais
  rabattue ni rebouclée.
- Les files d'entrée et de sortie sont bornées ; une surcharge visible déclenche une
  récupération conservatrice plutôt qu'une perte silencieuse de Note Off.
- MIDI reste utilisable lorsque l'audio est arrêté ou indisponible.
- Aucun ajout de dépendance runtime n'est nécessaire.

## Changements réalisés par module

### `domain/`

- Étendre `PitchGrid` avec un résultat de mouvement explicite et le degré courant.
- Rendre les actions de reconfiguration horodatables et valider les bornes publiques.
- Corriger les voicings en calculant les degrés non normalisés pour les harmonies.
- Définir des actions atomiques pour les mappings composés et une sémantique gate pour CC.
- Reconcevoir les leases de routage avec compteurs d'instances et opérations de purge.
- Isoler le mapping par défaut et exposer les primitives nécessaires au reset/preset.

### `app/`

- Introduire une abstraction de port injectable et des événements de repository explicites.
- Sérialiser tous les stimuli dans un coordinateur unique à capacité bornée.
- Associer un parseur à la connexion source et le réinitialiser à chaque génération.
- Propager lifecycle, changement de port et erreurs d'envoi vers la procédure de Panic.
- Versionner et sérialiser les réglages sans écritures concurrentes susceptibles de perdre
  une modification.
- Refondre l'écran Performance et ajouter le panneau MIDI, les canaux, plage/wrap,
  sélection explicite, reset mapping, lock et diagnostics utiles.

### `app/src/main/cpp/`

- Aucun enrichissement DSP prévu. Vérifier seulement que les événements et Panic existants
  restent compatibles et que les tests natifs ne régressent pas.

### Scripts, tests et documentation

- Rendre les lanceurs PowerShell indépendants de l'encodage Windows PowerShell et capables
  d'utiliser un cache Gradle explicitement configurable.
- Ajouter des tests exhaustifs de grille/accords/reducer/routage et du parser.
- Ajouter des faux ports et tests du coordinateur sans matériel.
- Ajouter des tests d'état UI purs ; réserver les gestes Compose et le hotplug réel aux
  tests instrumentés lorsque l'émulateur ou la tablette est disponible.
- Mettre à jour le statut, le changelog, la matrice et ce journal avec les preuves exactes.

## Risques

| Risque | Détection | Mitigation |
|---|---|---|
| Note bloquée locale ou PassThru | matrice des 16 transitions, répétitions et Panic | leases comptés, purge ciblée, coordinateur unique |
| Réordonnancement mapped/forwarded | test d'un paquet mixte et timestamps | une seule mailbox et une seule phase de dispatch |
| Perte de Note Off sous charge | test de saturation du faux ingress | file bornée, compteur et Panic de récupération |
| Saturation MIDI Out / file Handler | tests de mailbox Send/Select et overflow | 512 opérations, drain unique par lots, reset 16 canaux et Panic logique |
| Régression multi-touch | tests du registre de pointers et test Compose futur | `PointerId` réel, libération au cancel/dispose |
| Débordement UI compact/font scale | previews/tests de contraintes 960×600 et 1280×800 | layout adaptatif, panneau scrollable, cibles garanties |
| Hotplug spécifique constructeur | faux repository + protocole USB documenté | générations de connexion et fermeture idempotente |
| Build non reproductible sous Windows | exécution depuis PowerShell 7 et Windows PowerShell | lanceur sans dépendance fragile, cache configurable |
| Dérive vers l'étape 2 | revue de matrice et prompt | aucun contrôle Tone Row/transport livré dans l'UI |

## Stratégie de test

- Grille : toutes les gammes fournies × 12 clés × wrap/clamp × mouvements `-14…+14`,
  ancres dans/hors gamme, limites MIDI et Home.
- Accords : chaque définition, limites, doublures, ordre lead/harmonies et vélocités
  `1`, `2`, `64`, `127`.
- Reducer : zéro, Undo, Home, repress même source, plusieurs sources, reconfiguration,
  canal et Panic avec ordre exact.
- Routeur : quatre modes et leurs 16 transitions, notes répétées, canaux, CC 63/64/bas,
  ancrage, System Reset et purge.
- Codec : fragmentation à chaque octet, tous messages Channel Voice, temps réel imbriqué,
  System Common, SysEx incomplet/trop long et resynchronisation.
- Application : ordre mixed mapped/forwarded, changement source/destination, déconnexion,
  erreur d'envoi, cycle de vie, saturation et restauration de réglages avec faux ports.
- UI : état dérivé pur, deux pointers indépendants, sémantique actionnable, contrôles
  minimaux et configurations compactes ; les gestes instrumentés sont documentés si aucun
  émulateur n'est disponible.

## Commandes de baseline et de sortie

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/doctor.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/verify-domain.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/verify-native.ps1
./gradlew.bat :domain:test
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1
```

## Journal d'exécution

- 20 août 2026 : dépôt GitHub cible inspecté en lecture seule ; dépôt vide, aucun remote
  local créé.
- 20 août 2026 : audits séparés du domaine, de l'adaptateur MIDI et de l'UI. Décision de
  traiter d'abord les garanties de notes actives et l'ordre des événements, puis l'UI.
- 20 août 2026 : SDK déclaré complété avec Platform 36, NDK 28.2 et CMake 3.22.1 ; Java 17
  fourni temporairement. Doctor passe avec un avertissement `kotlinc`.
- 20 août 2026 : structure et C++/DSP hôte passent. Les problèmes de cache et de
  compatibilité du lanceur Windows sont corrigés avec un cache de build situé dans le
  workspace.
- 20 août 2026 : domaine, routeur, parseur, repository Android MIDI, coordinateur,
  persistance et UI adaptative sont finalisés pour la porte 1 logicielle.
- 20 août 2026 : le gate Gradle complet réussit ; l'APK debug est produit. Aucun essai
  matériel n'est revendiqué.
- 20 août 2026 : dependency locks et 863 empreintes Gradle sont figés, puis le gate est
  rejoué en lecture seule (`BUILD SUCCESSFUL`, 66 tâches en 1 min 29 s).
- 20 août 2026 : `scripts/verify.ps1` repasse structure, domaine, natif et Android de bout
  en bout ; dernière passe Gradle réussie en 13 s (9 tâches exécutées, 57 à jour).

## Résultats de validation

| Validation | Commande/protocole | Résultat courant | Preuve ou limite |
|---|---|---|---|
| Doctor | `scripts/doctor.ps1` | Réussi avec 1 avertissement | `kotlinc` autonome absent, repli Gradle prévu |
| Structure | `scripts/verify-structure.ps1` | Réussi | 153 fichiers, aucun avertissement après régénération du manifeste |
| Domaine | `./gradlew.bat :domain:test` | Réussi | 41 tests, 0 échec |
| DSP/JNI hôte | `scripts/verify-native.ps1` | Réussi | 1/1 test CTest |
| Application | `:app:testDebugUnitTest` | Réussi | 42 tests, 0 échec |
| Lint et APK | `:app:lintDebug :app:assembleDebug` | Réussi | lint : 0 erreur, 0 avertissement ; APK debug produit |
| MIDI USB | `docs/HARDWARE_TEST_PROTOCOL.md` | Non exécuté | matériel volontairement absent |
| UI et audio sur tablette | protocole matériel | Non exécuté | aucun appareil, aucune preuve multi-touch/Oboe |

## Critères de sortie

- [x] Porte 1 satisfaite pour tous les critères vérifiables sans matériel.
- [x] Tests domaine, parser, coordinateur, lint et build debug réussis.
- [x] Tests natifs sans régression importante observée.
- [x] Statut, état Codex et changelog reflètent le livré ; la matrice est réconciliée lors
  de la clôture d'intégration.
- [x] Critères matériels marqués non exécutés avec protocole restant, sans les déclarer
  réussis.
