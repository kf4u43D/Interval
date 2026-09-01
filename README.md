# Interval Tablet — workspace Android MIDI-first

Prototype d’instrument intervallique pour tablette Android, conçu d’abord comme **processeur et générateur MIDI**, puis comme instrument autonome grâce à un moteur audio stéréo minimal.

Le comportement cible est documenté à partir des fonctions observables décrites dans le guide utilisateur Misha d’Eventide. Le projet est une réimplémentation indépendante : il ne contient ni firmware, ni code, ni graphismes, ni ressources propriétaires d’Eventide.

## Périmètre actuel

- Navigation par degrés de gamme avec les neuf actions `-4 … 0 … +4`.
- Treize gammes standards 12-TET et Force to Scale ; aucune microtonalité dans ce cycle.
- MIDI USB entrant et sortant, sélection des ports, mapping notes/CC, panique MIDI.
- Modes de routage `Off`, `Active`, `Active Last Note` et `PassThru`.
- Tone Row : enregistrement, lecture manuelle, séquence automatique, transformations principales
  et huit parcours (`Prime`, `Retro`, `Random`, `Pendulum`, Auto-Transpose haut/bas et
  Auto-Translate haut/bas).
- Fonctions de jeu V2 : `Same Interval` et `Same Pitch` distincts, intervalle aléatoire
  immédiat et Chromatic Shift momentané.
- Éditeur MIDI Learn transactionnel pour Notes/CC, canal reçu ou Omni, seuil de CC,
  résolution explicite des conflits et validation/annulation atomique.
- Accords jusqu’à trois notes, trois articulations de pads (`ARPEGGIATED`, `STACKED`,
  `MUTED`) et strummer tactile pour égrener le voicing courant.
- Horloge MIDI **entrante**, Start, Stop et Continue.
- Synthèse interne optionnelle : patch typé de 16 paramètres, oscillateurs soustractifs,
  filtre, ADSR, chorus, temps/feedback/mix du delay, réverbération et panneau non modal.
- Interface portrait/paysage adaptée à une tablette 10–11 pouces, avec pads compacts et
  les dix variantes d’accords accessibles sur la page principale.

Rest, Random Step et Ratchet dans la séquence, l'horloge/transport MIDI sortants, Song
Position Pointer, le catalogue étendu d'actions mappables, les gammes personnalisées,
les scopes de presets étendus et l'optimisation soutenue à 90 Hz restent différés après
cette V2.1. CV, USB audio
multicanal, Wi‑Fi/RTP-MIDI, Ableton Link, Bluetooth MIDI, Scala/MPE/MIDI 2.0 et
microtonalité restent hors périmètre.

## Démarrage

### Prérequis recommandés

- Android Studio récent ou VS Code avec les extensions proposées.
- JDK 17 ou 21.
- Android SDK Platform 36, Build Tools 36.x, NDK `28.2.13676358` et CMake 3.22.1.
- Un appareil Android 10/API 29 minimum pour la baseline produit.
- Codex CLI ou l’extension officielle Codex pour VS Code, si l’on utilise les étapes autonomes.

### Commandes

```bash
./scripts/doctor.sh
./scripts/verify-structure.sh
./gradlew :domain:test
./gradlew :app:assembleDebug
./scripts/verify.sh
```

Sous Windows PowerShell :

```powershell
./scripts/doctor.ps1
./scripts/verify-structure.ps1
./gradlew.bat :domain:test
./gradlew.bat :app:assembleDebug
./scripts/verify.ps1
```

`gradlew` est un lanceur portable texte fourni avec le workspace. Au premier lancement, il télécharge Gradle 8.13, vérifie son SHA-256 officiel, puis exécute la commande. Les dépendances Android/Oboe sont ensuite résolues depuis `google()` et `mavenCentral()`. Avant le premier commit, `scripts/install-standard-wrapper.*` génère le Wrapper Gradle officiel et contrôle l’empreinte de son JAR.

## Parcours Codex

1. Lire `AGENTS.md`, puis `CODEX_SUPERPROMPT.md`.
2. Exécuter **une seule grande étape à la fois** :
   - `codex/prompts/01_midi_core.md`
   - `codex/prompts/02_tone_row_transport.md`
   - `codex/prompts/03_audio_ui_hardening.md`
   - `codex/prompts/04_v2_performance_midi_learn.md`
   - `codex/prompts/05_v2_1_performance_surface.md`
3. Utiliser le lanceur d'étape correspondant pour exécuter un seul lot en mode écriture
   contrôlé.

L’état du programme de développement est conservé dans `.codex/state.json` et `docs/IMPLEMENTATION_STATUS.md`.

## Structure

- `domain/` : machine d’état musicale Kotlin pure, déterministe et testable hors Android.
- `app/` : UI Compose, MIDI Android, préférences et pont JNI.
- `app/src/main/cpp/` : moteur temps réel Oboe et DSP sans allocation dans le callback.
- `native-tests/` : tests DSP compilables sur l’hôte sans SDK Android.
- `docs/` : spécifications, traçabilité du guide, protocole matériel, architecture et validation.
- `codex/prompts/` : cinq lots autonomes et significatifs.

## État d'implémentation

Les trois étapes du **MVP logiciel**, l'étape 4 de la **V2** et l'étape 5 de la **V2.1**
sont terminées depuis le 1er septembre 2026. La certification matérielle étendue reste
partielle et séparée de
cette clôture. Les neuf actions tactiles
et mappées, les accords, Tone Row et le
transport rejoignent des reducers déterministes. Tone Row couvre l'enregistrement de 5,
7 ou 12 classes, la lecture manuelle et automatique, Prime/Retro/Random/Pendulum, les
transformations, Play Once et une séquence de mouvements éditable. L'horloge interne et
le MIDI Clock 24 PPQ sont exclusifs et les notes restent possédées par leur origine jusqu'à
leur libération.

La V2 ajoute une sémantique de performance plus fidèle. `Same Interval` répète le dernier
déplacement diatonique, tandis que `Same Pitch` répète le dernier écart chromatique
réellement entendu. `Random` joue immédiatement un déplacement pseudo-aléatoire
déterministe et Chromatic Shift agit silencieusement tant que sa Note ou son CC reste
tenu. Une seconde pression sur Record abandonne la prise en cours ; une rangée en pause
reste navigable et une Note MIDI peut remplacer la vélocité enregistrée pour la seule
émission courante.

Tone Row expose huit parcours. Son Random démarre au premier élément puis conserve le
signe de chaque pas demandé en faisant varier sa magnitude entre zéro et le double. Les
quatre parcours Auto-Transpose/Auto-Translate accumulent leur transformation par cycle
logique. L'éditeur MIDI Learn maintient une baseline et un brouillon : la première Note
On ou le premier CC capturé est consommé avant rappel/routage, les conflits exacts exigent
un remplacement explicite, et seul Save installe le mapping. Cancel ne modifie ni la
session ni les presets existants.

La V2.1 rend ses fonctions immédiatement visibles : l’application coinstallable porte le
nom « Interval Tablet V2 », les pads portrait sont plus compacts, les dix accords restent
affichés en deux rangées et les gammes standards se sélectionnent sur la scène. Force to
Scale quantifie les notes générées vers la gamme active, sans altérer le MIDI PassThru.
Le panneau Synthé expose séparément temps, feedback et mix du delay.

La session de travail et une banque interne de 128 presets sont persistées avec migration
de schéma. Program Change rappelle le slot zéro-based correspondant sur le canal d'entrée
configuré ; Song Select effectue le même rappel global. Un slot absent n'est pas consommé
et aucun rappel n'est appliqué en PassThru. L'UI Compose originale ajoute timeline,
curseurs, transport, transformations et gestion de presets tout en conservant la surface
intervallique.

Les pads proposent trois articulations persistées : la voix principale seule, le voicing
plaqué, ou une navigation muette qui laisse le strummer égrener les notes du voicing. La
lecture automatique Tone Row conserve son rendu polyphonique historique. Le strummer
accepte balayage dans les deux sens, sauts de plusieurs cordes, vélocité sur l'axe
secondaire, clavier et services d'accessibilité sans déplacer la note courante.

Le chemin musical applicatif, l'horloge, les gates et les one-shots sont désormais
sérialisés hors du thread UI. La persistance et les diagnostics audio disposent de
workers séparés ; Compose observe des projections étroites afin qu'un tick, une release
ou une métrique ne réveille que les surfaces concernées. Le moteur C++20 porte le rendu
audio temps réel : huit voix, enveloppes, filtre, effets, limiteur, file SPSC, reprise Oboe
et ownership RAII des callbacks. Les reducers et toutes les règles musicales restent en
Kotlin pur ; le C++ ne les duplique pas.

Le moniteur interne est piloté par un `SynthPatch` Kotlin immutable dont les seize
paramètres possèdent des identifiants filaires explicites `0…15`. Le cutoff canonique est
borné à `20 Hz…20 kHz`, puis le DSP respecte également le plafond sûr du sample rate
négocié. Ce patch global relève de Settings v5 : il est distinct des presets musicaux v4
et de leur banque v3, est rejoué après chaque démarrage audio accepté ainsi qu'après une
récupération du stream — y compris détectée par le compteur de redémarrages sans état
arrêté intermédiaire observé — et n'est pas modifié par Program Change ou Song Select.

Le panneau Synthé non modal expose Timbre, filtre, ADSR, effets, master et diagnostics
audio dédiés. Les sliders publient un aperçu audio confluent au plus une fois par frame,
limité aux paramètres modifiés, puis déposent le patch complet et persistant à la fin du
geste. Le panneau reste désactivé avant le chargement de la session et disparaît sous
Performance Lock, sans retirer le contrôle Audio Monitor.

Le gain staging natif a été corrigé à sa source : durées ADSR exprimées comme de vrais
temps musicaux, mix d'oscillateurs normalisé, réverbération Schroeder à gain compensé et
all-pass canonique, puis limiteur exactement transparent sous son seuil. Les tests DSP
hôte prouvent bornes, finitude et stabilité à plusieurs fréquences d'échantillonnage ;
la confirmation utilisateur du 1er septembre établit le suivi audible des contrôles. Une
comparaison anti-saturation à niveau contrôlé ou un loopback chiffré reste une validation
de certification distincte.

Cette progression ne constitue pas une affirmation de parité matérielle achevée avec le
module de référence. Les écarts et preuves sont suivis dans
`docs/MISHA_BEHAVIOR_MATRIX.md`.

## Vérification livrée

Les validations reproductibles sont décrites dans `docs/VERIFICATION_REPORT.md`. Le gate
V2.1 du 1er septembre 2026 réussit **136 tests domaine sur 12 suites et 161 tests
application sur 19 suites, soit 297/297**, CTest 2/2, quatre Lint sans issue, tous les
assemblages et le contrôle des runtimes natifs sur quatre ABI. La réception directe
SM-X620/API 36 réussit 7/7 en 17,279 s et vérifie notamment les dix accords visibles,
Force to Scale, les pads compacts et les gestes temps/feedback du delay. La tablette ne
conserve que la V1 `0.1.0-dev-debug` et la V2.1 `0.2.1-dev-instrumented`.

Le gate
V2 final du 1er septembre 2026, associé au commit vérifié
`13c2d7c4915e8da65c5e6898daf8ee9a5f253e75`, réussit **131 tests domaine sur 11 suites
et 160 tests application sur 19 suites, soit 291/291**, sans échec, erreur ni test
ignoré. Les suites
déterministes couvrent Tone Row/transport, le coordinateur et son acteur hors Main, les
articulations/strums, le byte stream MIDI, le contrat `SynthPatch`, la persistance et ses
migrations, les projections UI et le moteur C++/JNI. Une
variante minifiée `benchmark` et une variante non minifiée `instrumented`, toutes deux
isolées du package utilisateur, séparent mesure de rendu et tests UI. Les quatre rapports
Lint indiquent `No issues found.`, CTest réussit 2/2 et le gate principal comme les
variantes Debug, Release non signée, Benchmark, Instrumented et AndroidTest sont verts.
`scripts/verify.ps1` contrôle aussi les runtimes Oboe/C++ sur les quatre ABI. Les totaux
94/140 du MVP et les campagnes antérieures restent archivés, clairement datés, dans le
rapport de vérification.

Une réception partielle a été effectuée sur Samsung SM-X620/API 36. La suite directe
finale réussit 7/7 en 15,603 s : conflit MIDI Learn puis Replace, Save et Cancel, sliders
Synthé continus, pads, Tone Row et dix cycles audio start/stop. Elle ne valide cependant
pas le MIDI Learn avec un périphérique USB réel, le hotplug, le vrai multi-touch,
TalkBack, un rendu soutenu à 90 Hz, l'écoute comparative, la latence loopback ni le soak
audio de 60 minutes ; ces validations restent régies par le protocole matériel et ne
bloquent pas la clôture de la V2 logicielle. Le package Release produit reste
volontairement non signé ; choix de
licence, identité commerciale, signature et publication sont hors de cette clôture.

## Dépôt et CI

Le workspace est initialisé comme dépôt Git local sur la branche `main`, avec le Wrapper
Gradle officiel 8.13 vérifié, et un remote `origin` est configuré. Le dépôt contient des
commits locaux non poussés ; aucun push ni publication n'a été effectué et aucun état
distant n'est déduit de la seule présence du remote. `docs/REPOSITORY_SETUP.md`
décrit les protections de branche. Une CI GitHub Actions et une configuration Dependabot
sont fournies sans secret ni action de publication.

## Nom, marques et publication

« Misha » et « Eventide » sont utilisés uniquement pour identifier la référence comportementale. Choisir un nom, une identité visuelle et une licence définitifs avant toute publication. Le fichier `LICENSE` maintient par défaut le projet non licencié.
