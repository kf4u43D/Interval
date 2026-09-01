# Étape 3 — audio natif, latence et robustesse

## Résultat utilisateur attendu

Interval Tablet reste immédiatement jouable lorsque Compose mesure, dessine ou reçoit une
rafale d'état. Les commandes musicales, le transport et les gates ne dépendent plus du
thread UI ; le moteur Oboe conserve un callback sans verrou, allocation, I/O, log ou JNI.
Les mesures distinguent explicitement fluidité graphique, latence toucher vers audio et
latence MIDI, afin de ne pas présenter `gfxinfo` comme une preuve audio.

## Périmètre inclus

- correction du gain staging natif : enveloppes exprimées en temps réels, réverbération
  normalisée et limiteur transparent sous son seuil ;
- articulation des pads en trois modes explicites `ARPEGGIATED`, `STACKED` et `MUTED`,
  sans modifier le fonctionnement de l'Auto Tone Row ;
- strummer tactile et accessible qui égrène le voicing préparé, avec ownership exact de
  chaque voix, geste multi-directionnel et vélocité issue de l'axe secondaire ;
- migration versionnée des réglages, presets et banques existants vers une articulation
  qui préserve leur flux MIDI historique ;
- acteur musical séquentiel exécuté hors de `Dispatchers.Main`, avec dispatcher injectable ;
- horloge interne, gates Tone Row et one-shots ordonnés sur ce même chemin ;
- publication Compose projetée et télémétrie MIDI découplée du trafic musical ;
- durcissement Oboe : ouverture/démarrage Exclusive puis Shared, overflow conservateur,
  arrêt/reprise sans événements résiduels, générations de stream et drain callback borné ;
- réduction des allocations/recompositions encore présentes sur les pads et la timeline ;
- tests de concurrence, ordre, shutdown, timing natif et non-régression musicale ;
- benchmarks Release AOT reproductibles sur la SM-X620, audio OFF/ON, avec sorties brutes.

## Périmètre explicitement différé

- USB audio, CV, réseau, MPE, MIDI 2.0, microtonalité et moteur de jeu ;
- migration de toute la logique musicale en C++ : les reducers restent Kotlin purs ;
- affirmation de latence audio absolue sans mesure loopback physique ;
- fermeture de la réception MIDI USB sans clavier et synthétiseur réels.

## Invariants

- symétrie Note On/Note Off par origine et destination ;
- Panic et changement de port libèrent toutes les notes connues avant fermeture ;
- ordre total unique des actions tactiles, MIDI et transport ;
- aucune règle musicale dupliquée dans Compose, Android MIDI ou C++ ;
- aucun verrou, allocation, I/O, log ou JNI dans le callback audio ;
- aucune commande audio/MIDI après le Panic final et la fermeture des adaptateurs ;
- aucune télémétrie best-effort ne peut retarder une commande de contrôle garantie.

## Changements intégrés par module

### `domain/`

- Les reducers et leurs horloges injectées restent l'autorité musicale ; aucune règle
  n'a été déplacée dans l'adaptateur Android ou dans le moteur natif.
- `PadArticulation`, `PressPadAbsolute` et `StrumTone` séparent articulation tactile,
  Tone Row automatique et cordes sans dupliquer le voicing. `strumNotes()` est l'unique
  projection du voicing complet.

### `app/`

- L'acteur utilise un dispatcher séquentiel injectable hors Main ; persistance et
  diagnostics utilisent des workers I/O indépendants.
- Les callbacks publics déposent des intents bruts, sans lire l'état mutable concurrent.
- Les releases sont programmées seulement après acceptation du Press associé.
- HostStop et la fermeture finale sont gardés et sérialisés ; la persistance finale est
  drainée séparément avec un nombre d'essais borné.
- La télémétrie MIDI haute fréquence est retirée de la mailbox musicale et échantillonnée,
  tandis que topologie, erreurs et overflow restent immédiats.
- Des snapshots immuables confluentés évitent deux reconstructions durables par événement
  et rejouent le dernier état après une erreur transitoire de stockage.
- Header, contenu/curseurs Tone Row, pads, ruban, console et statut observent des
  projections Compose étroites ; texte et décor des pads sont mis en cache.
- Le strummer adapte son orientation à la fenêtre, énumère toutes les bandes traversées,
  porte une hystérésis de 8 dp et ne transmet à l'acteur que les hits index/vélocité.
- Le schéma Settings est désormais v4 pour porter le patch synthé global ; les presets
  musicaux restent v3 et la banque v2, avec migrations explicites des générations publiées.

### `app/src/main/cpp/`

- Le cycle open/start tente Exclusive intégralement avant Shared, utilise le sample rate
  négocié et reprend hors callback avec backoff et intention de lifecycle vérifiée.
- Un overflow déclenche un Panic d'urgence et un reset conservateur au lieu de perdre
  silencieusement Panic/Note Off.
- Les événements sont tagués par génération, le drain est borné à 128 événements par
  callback et les callbacks conservent un ownership partagé jusqu'à leur retour. Le pont
  Kotlin sérialise tous les `send` en un producteur SPSC, indépendamment des diagnostics.
- La fréquence MIDI, les coefficients ADSR/filtre et les familles de paramètres évitent
  les recalculs par voix ; chorus et buffers circulaires évitent modulo/trigonométrie
  répétés, avec invalidation logique O(1) des grandes lignes à retard.
- Les entrées JNI/DSP sont bornées et assainies ; NaN/Inf ne peuvent pas empoisonner le
  rendu et Panic remet voix, filtres, phases et tails dans un état sûr.
- Les coefficients ADSR atteignent leurs seuils à la durée demandée ; les mix
  d'oscillateurs, le réseau Schroeder et ses all-pass sont normalisés, et le limiteur est
  une identité exacte sous `0.75` avant sa courbe rationnelle bornée.

### Tests et documentation

- `IntervalTabletViewModelTest` couvre désormais blocages indépendants, ordre des
  one-shots, fermeture, télémétrie, reprise audio et persistance retry/confluentée.
- Les tests natifs hôte couvrent lifecycle, fallback, ownership, génération, overflow,
  drain borné, finitude, reset et pont JNI.
- Les tests JVM de projections vérifient quelles sous-surfaces changent ; les tests
  instrumentés couvrent la timeline, les neuf pads, les trois modes et les cordes
  accessibles.
- La variante `instrumented` non minifiée est séparée de `benchmark`, minifiée et
  profileable, afin de ne pas modifier le binaire mesuré pour injecter les tests UI.
- Conserver les dumps `gfxinfo` bruts et, si disponible, une trace Perfetto séparée.
- Mettre à jour le rapport de vérification, la matrice, le statut, le changelog et l'état
  Codex uniquement avec les résultats réellement observés.

## Risques

| Risque | Détection | Mitigation |
|---|---|---|
| Course entre acteur et shutdown | test avec dispatchers séparés et commandes en attente | garde de fermeture + Panic final sérialisé |
| Note Off ou Panic perdu sur overflow natif | saturation déterministe de la SPSC | drapeau de Panic d'urgence consommé dans le callback |
| Réordonnancement d'un one-shot | mailbox saturée et temps virtuel | release planifiée seulement après traitement du press |
| UI plus fluide mais compteur faux | test de projections Note On/Off | état autoritatif conservé, projection/échantillonnage explicites |
| Callback Oboe ancien après reprise | test de génération et destruction concurrente | ownership partagé + rejet de génération |
| Benchmark non comparable | huit passes A/B contrebalancées | même APK AOT, scénario, cadence, thermique et dumps bruts |
| Saturation masquée par une baisse de volume | tests séparés enveloppe/réverbération/limiteur | corriger les gains internes avant de retoucher le master |
| Ancien preset rendu différemment | migrations v1/v2 et banque v1 | inférer le rendu historique : accord actif = `STACKED`, sinon `ARPEGGIATED` |
| Note bloquée pendant un strum | changements de direction, doublures et Panic | une source one-shot unique par voix et une Release ordonnée après acceptation |
| Mode `MUTED` contourné par Tone Row manuel | tests Recording/Manual/Auto | action pad absolue distincte ; Auto conserve l'action historique sonore |
| Geste rapide perdant des cordes | sauts de plusieurs bandes | énumérer tous les indices intermédiaires dans l'ordre du déplacement |

## Finalisation du contrôle synthé et des diagnostics — 2026-08-22

### Écart constaté

Le moteur natif accepte seize paramètres par JNI, mais aucun appel applicatif ne crée de
commande `AudioCommand.Parameter`. La scène ne présente donc que l'activation du moniteur
audio : le panneau synthé simple demandé par le prompt d'étape (timbre, filtre, ADSR,
chorus, delay, reverb et master) n'est pas livré. Les diagnostics atteignent
`AppUiState`, mais ne sont rendus par aucune projection Compose. Cet écart logiciel doit
être fermé avant de considérer le lot audio complet, indépendamment des réceptions
subjectives et loopback qui restent matérielles.

### Plan d'implémentation

1. Remplacer l'identifiant entier libre par un contrat de paramètres audio Kotlin typé,
   borné et aligné explicitement sur les identifiants C++.
2. Ajouter un patch synthé immutable avec valeurs par défaut sûres, projection de la macro
   Timbre vers le mix d'oscillateurs et liste ordonnée de commandes natives.
3. Versionner le patch global du moniteur dans la session de travail, avec migration des
   schémas Settings publiés et repli exact vers le patch par défaut. Les presets musicaux,
   Program Change et Song Select ne modifient pas ce patch global.
4. Sérialiser les changements dans l'acteur, persister seulement les valeurs durables et
   rejouer le patch après chaque démarrage audio accepté. Un rappel de preset conserve le
   patch courant ; Panic remet voix et tails au silence sans réinitialiser les paramètres.
5. Ajouter un panneau Synthé original, non modal, accessible depuis les surfaces compactes
   et larges. Il expose macro Timbre, cutoff/résonance, ADSR, chorus/delay/reverb et master,
   ainsi qu'une projection diagnostics dédiée. Performance Lock ferme et masque ce panneau
   sans retirer le toggle Audio Monitor de la scène.
6. Lisser dans le callback les trois gains normalisés d'oscillateurs, la pulse width, le
   sustain et les coefficients du filtre, en conservant un coût borné sans allocation,
   verrou, I/O, log ou JNI. Les valeurs lissées rejoignent exactement leur cible afin
   d'éviter les queues subnormales près de zéro.
7. Étendre les tests domaine/JVM/natifs/instrumentés, puis rejouer le gate complet et un
   smoke sur la SM-X620 si ADB reste disponible.

### Risques et parades propres au lot

| Risque | Détection | Mitigation |
|---|---|---|
| Dérive d'identifiants Kotlin/C++ | test exhaustif des seize identifiants et test JNI/natif | enum stable avec identifiants explicites, aucun ordinal implicite |
| Rafale de sliders saturant la SPSC | test acteur avec changements rapides | brouillon local, aperçu confluent limité à une frame, deltas filaires et commit complet en fin de geste |
| Patch perdu au premier démarrage/recovery | faux moteur et cycles start/stop | replay ordonné après chaque start accepté ; C++ conserve les cibles lors d'une reprise interne |
| Rappel de preset modifiant le son global | test preset avec patch non défaut | patch hors snapshot musical ; Panic conserve les paramètres et aucun état actif n'est restauré |
| Session historique illisible | fixtures Settings v0/v1/v2/v3 | seize champs à défaut explicite et migration vers le modèle courant |
| Recomposition de toute la scène à 1 Hz | test d'égalité des projections | projection synthé/diagnostics dédiée observée seulement par le panneau |
| Zip audible sur la macro Timbre | test natif de convergence et borne par échantillon | `SmoothedValue` préparées et avancées une fois par frame, valeurs partagées par les huit voix |

### Validation exécutée

- round-trip du patch et migrations Settings historiques ; valeurs non finies
  et hors plage rejetées ou bornées avant JNI ;
- acteur : mise à jour, persistance confluentée, replay au start et conservation du patch
  global lors d'un rappel ; le chemin MIDI reste utilisable audio coupé ;
- projections : seuls le patch/les diagnostics synthé changent lors d'une métrique audio ;
- Compose : ouverture/fermeture, Performance Lock, libellés/sémantiques et contrôles du
  panneau sur l'activité instrumentée isolée ;
- natif : lissage monotone/borné du timbre, du sustain et du filtre, finitude et maintien
  des gates DSP existants ;
- commandes finales : `verify-structure.ps1`, `verify-domain.ps1`, `verify-native.ps1`,
  tests Gradle, Lint debug/benchmark, assemblages debug/benchmark/instrumented et tests
  instrumentés disponibles sur la SM-X620.

Toutes ces validations sont vertes. Le smoke matériel nominal ne remplace toutefois ni
une écoute qualifiée, ni un loopback, ni un hotplug audio contrôlé, ni le soak de 60 minutes.

## Extension articulation et anti-saturation — 2026-08-22

### Contrat figé

- `ARPEGGIATED` joue seulement la voix principale à chaque geste de pad. Le voicing
  complet reste préparé pour le strummer ; aucun faux scheduler d'arpège n'est ajouté.
- `STACKED` joue simultanément toutes les voix du chord sélectionné, dans l'ordre du
  voicing et avec les doublures conservées.
- `MUTED` effectue la même navigation, le même historique et le même enregistrement,
  mais n'émet aucun nouveau Note On. Le strummer reste sonore.
- Les notes déjà tenues conservent le mode et la destination de leur Note On jusqu'à leur
  Release. Un changement de mode n'interrompt donc pas arbitrairement une voix.
- Le Tone Row automatique reste `STACKED` comme avant. Les gestes de pads capturés en
  Recording/Manual utilisent une action pad dédiée afin que `MUTED` ne soit pas contourné.
- Chaque hit du strummer reçoit une source unique, joue une seule voix à vélocité pleine
  et ne modifie jamais la note courante, l'historique ou le curseur Tone Row.

### Correctif DSP

- ADSR : Attack, Decay et Release atteignent leurs seuils documentés à la durée demandée,
  sur tous les sample rates pris en charge.
- Réverbération : sommation stéréo en moyenne, send compensé par `1-feedback`, puis
  moyenne des quatre combs avant les all-pass.
- Limiteur : identité exacte sous le knee, transition monotone et asymptotique vers
  `[-1, 1]` au-dessus ; NaN/Inf restent neutralisés.
- Le trim voix et le master par défaut ne sont pas abaissés sans mesure : ils disposent
  de la marge nécessaire une fois les trois erreurs précédentes corrigées.

### Validation ajoutée

- tests domaine exhaustifs des trois modes, doublures, ranges, re-press, changements de
  mode et Panic ; invariants Note On/Note Off par origine ;
- tests strummer : crossings intermédiaires, inversions, vélocité, sources indépendantes,
  index hors plage et invariance de navigation ;
- tests Tone Row Recording/Manual silencieux en `MUTED` et Auto inchangé ;
- tests de migration et round-trip des schémas preset/settings/banque ;
- tests natifs de durée ADSR à 44,1/48/96 kHz, transparence du limiteur et stabilité de la
  réverbération sous polyphonie ;
- gate logiciel complet et test instrumenté sur tablette ; la réception audio subjective
  n'est pas exécutée. Écoute/loopback et matériel MIDI externe restent des limites explicites.

## Commandes de baseline

```powershell
./scripts/doctor.ps1
./scripts/verify-structure.ps1
./scripts/verify-domain.ps1
./scripts/verify-native.ps1
./gradlew.bat :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Journal d’exécution

- 2026-08-21 : le workspace n'est pas un dépôt Git ; aucune comparaison Git ni commit
  local n'est possible.
- 2026-08-21 : `doctor.ps1` trouve CMake mais pas Java/Ninja/ADB dans `PATH`. La baseline
  utilise le JDK 17 temporaire déjà présent et le SDK `C:\Android\SDK` sans versionner
  ces chemins.
- 2026-08-21 : structure OK (225 fichiers), DSP/JNI hôte 1/1, puis gate Android
  `BUILD SUCCESSFUL`. L'avertissement de métriques vers `C:\.android` est environnemental.
- 2026-08-21 : les 481 frames en 30 s correspondent aux 8 Note On et 8 Note Off par
  seconde du scénario ; elles mesurent le rendu UI, pas un round-trip audio ou MIDI.
- 2026-08-21 : gate Gradle final `BUILD SUCCESSFUL`, 188/188 tests JVM, Lint sans issue
  sur debug/benchmark/instrumented et `lintVitalRelease` sans issue ; vérification native
  2/2.
- 2026-08-21 : les deux tests instrumentés passent sur SM-X620 ; le package benchmark
  isolé est installé puis compilé AOT `speed`.
- 2026-08-21 : huit passes contrebalancées `OFF,ON,ON,OFF,ON,OFF,OFF,ON` sont archivées,
  chacune après 10 s de warm-up puis 30 s de mesure. Stop puis Panic sont exécutés après
  la campagne.
- 2026-08-22 : extension articulation/anti-saturation intégrée. Le gate Gradle réussit
  avec 88/88 tests domaine sur 9 suites et 128/128 tests application sur 17 suites,
  soit 216/216 tests JVM, `lintDebug` sans issue et assemblages demandés produits.
- 2026-08-22 : vérification native stricte : 8/8 unités compilées, puis 2/2 suites
  réussies. La qualité audio subjective reste volontairement ouverte jusqu'à une écoute
  comparative ou un loopback sur le stream réel.
- 2026-08-22 : APK instrumented et APK de test reconstruits/réinstallés sans effacement ;
  instrumentation finale 3/3 en 5,876 s. Le smoke `MUTED` puis strum de trois cordes
  conserve la navigation et revient à zéro note active.
- 2026-08-22 : variante benchmark minifiée reconstruite, `lintBenchmark` sans issue,
  installée pour un smoke de restauration/sémantiques. Elle n'est pas compilée AOT ni
  remesurée dans la campagne A/B de ce lot ; le cold start unique à 239 ms est illustratif.
- 2026-08-22 : lot de contrôle synthé finalisé. Settings passe à v4 avec un patch global
  de seize paramètres, hors presets v3/banque v2 ; le patch est rejoué après start et
  après reprise native, et l'accès UI reste désactivé avant chargement des réglages.
- 2026-08-22 : `verify.ps1` réussit avec 93/93 tests domaine et 138/138 tests application,
  soit 231/231 JVM, puis 2/2 suites natives. Lint Debug/Benchmark/Instrumented/VitalRelease
  et tous les assemblages demandés réussissent.
- 2026-08-22 : instrumentation finale 6/6 en 17,94 s sur SM-X620/API 36. AUDIO-01 réalise
  10/10 cycles Oboe et enfile les 16 paramètres plus Panic à chaque cycle : 48 kHz,
  burst 192, buffer 384, file max 17, zéro drop/reprise/erreur et au plus un xrun.
- 2026-08-31 : reprise par ADB Wi-Fi et reconstruction complète des APK instrumentés.
  Une première passe 5/6 révèle que Prefab omet `liboboe.so` lorsque son cache Windows
  contient un espace. Les lanceurs ancrent désormais les caches Gradle/Android dans le
  workspace et le gate inspecte les runtimes de toutes les ABI.
- 2026-08-31 : après correction, AUDIO-01 isolé passe 1/1 en 6,219 s et la suite complète
  6/6 en 16,222 s : 48 kHz, burst 96, buffer 192, file max 17, zéro drop, reprise,
  erreur et xrun. Android MIDI Manager n'expose aucun périphérique USB MIDI physique.
- 2026-08-31 : le retour tactile révèle que les paramètres Synthé n'atteignent le moteur
  qu'au relâchement. Le brouillon local publie désormais un aperçu confluent au plus une
  fois par frame ; l'acteur calcule les seuls deltas filaires sans modifier l'état durable,
  puis le relâchement ou la fermeture applique et persiste le patch complet. Les tests
  couvrent aussi le retour à la valeur autoritaire après un aperçu.
- 2026-09-01 : l'utilisateur confirme le suivi audible continu. `doctor.ps1` termine à
  zéro erreur après ajout du repli Ninja fourni par le SDK. Le gate combiné réussit
  234/234 tests JVM, 2/2 suites natives, les quatre Lint et les assemblages Debug, Release,
  Benchmark, Instrumented et de tests. La suite tablette finale réussit 6/6 en 13,612 s.

## Résultats de validation

| Validation | Commande/protocole | Résultat | Preuve ou limite |
|---|---|---|---|
| Structure baseline | `verify-structure.ps1` | Réussi | 225 fichiers, empreinte `63017cec6e7a9cae` |
| Domaine final | `:domain:test` | Réussi | 79/79 tests, 8 suites |
| Domaine portable | `verify-domain.ps1` sans variables Android | Réussi | repli Gradle autonome, aucun SDK Android requis |
| Application JVM finale | `:app:testDebugUnitTest` | Réussi | 109/109 tests, 16 suites ; total JVM 188/188 |
| DSP/JNI hôte baseline | `verify-native.ps1` | Réussi | 1/1 CTest |
| Audio/DSP final | `verify-native.ps1` | Réussi | 2/2 CTest ; cycle Oboe/JNI/SPSC et DSP |
| Lint final | debug/benchmark/instrumented + release vital | Réussi | 0 issue sur les quatre analyses |
| Assemblages finaux | Gradle combiné | Réussi | debug, release unsigned, benchmark, instrumented et test APK |
| Acteur/persistance/projections | suites JVM finales | Réussi | concurrence, débit borné, shutdown et égalité des projections |
| Tests UI isolés | `am instrument` sur SM-X620 | Réussi | 2/2 ; pads accessibles ≥72 dp et scène sous timeline |
| Performance tablette finale | protocole A/B `benchmark` | Exécuté | 8 passes OFF/ON, dumps bruts et résumés archivés |
| MIDI USB | `HARDWARE_TEST_PROTOCOL.md` | Bloqué | périphériques USB réels requis |
| Latence audio absolue | loopback | Bloqué | interface/câble de mesure requis |
| Extension articulation — domaine | `:domain:test` | Réussi | 88/88 tests, 9 suites |
| Extension articulation — application | `:app:testDebugUnitTest` | Réussi | 128/128 tests, 17 suites ; total courant 216/216 JVM |
| Extension anti-saturation — natif | `verify-native.ps1` | Réussi | compilation stricte 8/8, 2/2 suites |
| Extension — Lint debug | `:app:lintDebug` | Réussi | `No issues found.` |
| Extension — benchmark | assemble/lint + smoke tablette | Réussi | R8, `No issues found.`, scène restaurée ; non AOT, aucune nouvelle passe A/B |
| Extension — tests UI isolés | `am instrument` sur SM-X620 | Réussi | 3/3 en 5,876 s ; modes, cordes, pads et scène |
| Extension — smoke tablette | `MUTED`, pad puis strum | Réussi sur le parcours | C4→D4 muet, trois cordes traversées, D4 inchangé, 0 note active |
| Contrat synthé/persistance | domaine + application JVM | Réussi | Settings v4, 16 IDs typés, cutoff 20 Hz–20 kHz, patch global hors presets |
| Replay start/recovery | `IntervalTabletViewModelTest` | Réussi | patch complet rejoué au start et après reprise, y compris une mutation pendant recovery |
| Lissage du contrôle synthé | `verify-native.ps1` | Réussi | mix, pulse width, sustain et coefficients filtre convergent sans saut de contrôle |
| Gate synthé final | `verify.ps1` + variantes Gradle | Réussi | 93 domaine + 138 application = 231/231 JVM ; 2/2 natifs ; lint et assemblages verts |
| UI/audio réel final | `am instrument` sur SM-X620 | Réussi au nominal | 6/6 ; AUDIO-01 10 cycles, 16 paramètres + Panic/cycle, 48 kHz, burst 192, queue max 17, 0 drop/restart/error, max xrun 1 |
| Revalidation Wi-Fi et runtime APK | `verify.ps1` + `am instrument` sur SM-X620 | Réussi au nominal | runtime Oboe/C++ présent pour 4 ABI ; 6/6 en 16,222 s ; AUDIO-01 48 kHz, burst 96, buffer 192, queue max 17, 0 drop/restart/error/xrun |
| Aperçu continu des sliders Synthé | domaine + ViewModel + Compose + SM-X620 | Réussi | deltas ordonnés, aucune persistance intermédiaire, aperçu avant commit ; 234/234 JVM et 6/6 appareil en 13,723 s |
| Clôture du MVP logiciel | gate complet + réception utilisateur | Réussi | 234/234 JVM, 2/2 natifs, 4 Lint, 5 assemblages, 6/6 appareil en 13,612 s et contrôle continu confirmé |
| Qualité audio subjective | écoute/loopback | Ouvert | aucune écoute comparative ni capture loopback qualifiée |

### Artefacts archivés du gate du 21 août

| Artefact | Taille | SHA-256 |
|---|---:|---|
| `app-debug.apk` | 39 720 679 octets | `C46A4E11E3269DA968A5B6892155A13BD18577A61BAB5FDABBE964E5EE5956FD` |
| `app-release-unsigned.apk` | 8 923 784 octets | `D67BCEC0B21ED3DE1D334E4AE2DB11123E0FD2952723F006D628AB34203132E0` |
| `app-benchmark.apk` | 8 932 016 octets | `3F0E2B057159B6A26F7D1F2C434C6A3F9ACB4D0D6F82B8BF611ABF2C196E80C8` |
| `app-instrumented.apk` | 39 458 061 octets | `041B1392337D28A1AEE84C8D51A51303F845439D13898134B59E68A326A6D7C7` |
| `app-instrumented-androidTest.apk` | 2 304 769 octets | `EED0885BD472F0FA3D5D0F5A77AF7B97F7015B5002AAA16E016F4512A717895F` |

### Artefacts courants de l'extension

| Artefact | Taille | SHA-256 |
|---|---:|---|
| `app-debug.apk` | 39 232 136 octets | `A8BDF41695225493066D872D6006D92094B3E56CBBE44AD7B68912D287D6CE4D` |
| `app-benchmark.apk` | 8 953 212 octets | `A1E2F015A900ED8B62CC1CED1FABB313247DC74DA6A1DD484E983B7E8779584C` |
| `app-instrumented.apk` | 38 969 398 octets | `E51AB1A9E2E886695E931803DD55F358DF396F3461336B5CACBBC81188194A62` |
| `app-instrumented-androidTest.apk` | 2 329 664 octets | `0E748A7A449ECAE7B6B5D359222E4DCA273DF2A7368552E8B2F8EAC8DDAC8657` |

### Artefacts finaux du contrôle synthé

| Artefact | Taille | SHA-256 |
|---|---:|---|
| `app-instrumented.apk` | 41 050 917 octets | `1AEAD32061DC3FF4EEC435287CF1954B6187DCE8CE2820E071969F23751C35D0` |
| `app-instrumented-androidTest.apk` | 2 441 967 octets | `6E312722AD2B8A0A61EA6C413974B9B9489B3046D0327FCC6D63155E7175C2B7` |

### Artefacts de revalidation du 31 août

| Artefact | Taille | SHA-256 |
|---|---:|---|
| `app-debug.apk` | 39 361 808 octets | `D7663411361EA8117EF37DEA170EAA4659FDDEC0524528C4E5B54E2FD82012FE` |
| `app-instrumented.apk` | 39 082 686 octets | `A40E68E944DA60C2BB4C102C3AAF489D65A97DE209256F8DB2C2C892259021FC` |
| `app-instrumented-androidTest.apk` | 2 394 966 octets | `4FA079B363AA212E307C857D054B4F322D2079F41881C78227943399D0A511A2` |

### Artefacts de clôture du 1er septembre

| Artefact | Taille | SHA-256 |
|---|---:|---|
| `app-debug.apk` | 40 140 987 octets | `8AD5EF7EFA34790C1358F3127AB4668AE4E09540DD375F660AA35A7CD41D459C` |
| `app-release-unsigned.apk` | 9 025 500 octets | `06E642A65E31BBF332B3AC52AAE22EDEB90D11C92BD0BE0C1638A46BA53AD159` |
| `app-benchmark.apk` | 9 033 732 octets | `06573B1D99573CF31DA9C5B24A9BD68B2511A68B15B5D5FD6117B9220EA6C5ED` |
| `app-instrumented.apk` | 39 862 045 octets | `E8B5BE702CBE432C0E3C66B36DDA4120FFB979028D6BF8800D4EA73451C24B74` |
| `app-instrumented-androidTest.apk` | 2 508 467 octets | `21493BA4B430146B44AC36CA0F6E2DF1EA6DCEBE810E3B26EB8DFDF51E1BBE9F` |

### Campagne A/B SM-X620

Scénario commun : 90 Hz, rangée D4…C5 de sept pas, Prime, 120 BPM, division 6,
gate 75 %, 10 s de warm-up puis 30 s de mesure, package `dev.intervaltablet.benchmark`
précompilé AOT `speed`.

| Condition | Passes | Frames plateforme médiane (plage) | p50/p90/p95/p99 | GPU p50/p90 | Jank strict | Jank legacy |
|---|---:|---:|---:|---:|---:|---:|
| Audio OFF | 4 | 475 (474…476) | 18/22,5/23/26,5 ms | 6/7 ms | 99,475 % | 68,985 % |
| Audio ON | 4 | 476,5 (474…477) | 18/22,5/23/26,5 ms | 6/7 ms | 99,685 % | 75,155 % |

| Condition | UI | Traversal | Render | Issue | GPU | Frame completed |
|---|---:|---:|---:|---:|---:|---:|
| Audio OFF | 5,88 ms | 3,97 ms | 5,19 ms | 3,34 ms | 5,94 ms | 18,75 ms |
| Audio ON | 6,56 ms | 4,51 ms | 5,31 ms | 3,38 ms | 5,74 ms | 19,05 ms |

Les compteurs indiquent 0 `Missed Vsync` sur les huit passes. Les phases sont des médianes
de médianes calculées sur le tail disponible : le ring `framestats` ne conserve que les
120 dernières frames, environ 7,5 s. Les percentiles et janks plateforme couvrent les
30 s. Les distributions OFF/ON se recouvrent, donc aucun surcoût DSP n'est attribuable à
partir de cette campagne. La durée totale de frame reste supérieure au budget 11,11 ms et
le jank strict demeure ouvert. `gfxinfo` n'est pas une mesure de latence tactile/audio ou
MIDI.

## Critères de sortie

- [x] Porte 3 logicielle satisfaite et limites matérielles précisément ouvertes.
- [x] Acteur, transport et gates hors Main, couverts par tests de concurrence.
- [x] Callback natif et cycle de stream couverts par tests hôte.
- [x] Gate logiciel complet sans avertissement nouveau important.
- [x] Mesures A/B du 21 août archivées sous forme brute et interprétées correctement.
- [x] Réception UI/ownership du 22 août documentée sans identifiant sensible.
- [x] Panneau synthé, contrat typé, Settings v4 et replay après recovery couverts.
- [x] AUDIO-01 nominal réel exécuté sur dix cycles avec les seize paramètres.
- [ ] Écoute comparative ou loopback anti-saturation et soak audio de 60 minutes.
- [x] Documentation, matrice, `.codex/state.json` et `CHANGELOG.md` à jour.
- [x] MVP logiciel clôturé après gate toutes variantes et confirmation du contrôle continu.

## Clôture complète du MVP — 1er septembre 2026

### Décision de clôture

La confirmation utilisateur valide le suivi audible des sliders pendant le geste. Le
MVP est considéré terminé lorsque le gate logiciel final, tous les assemblages et la
suite tablette restent verts. Les protocoles nécessitant un contrôleur MIDI USB, une
interface de loopback, un parcours TalkBack physique ou une heure de réception restent
des limites de certification matérielle : ils ne maintiennent plus le lot logiciel en
état `in-progress` et ne sont pas présentés comme réussis.

### Plan final

1. Corriger le faux négatif du diagnostic Windows lorsque Ninja est fourni par CMake
   dans le SDK Android plutôt que par le `PATH` global, et aligner le diagnostic Unix.
2. Rejouer structure, domaine portable, natif, tests JVM, lint et assemblages Debug,
   Release, Benchmark, Instrumented et APK de tests.
3. Rejouer la suite instrumentée sur la SM-X620 si la liaison ADB reste disponible et
   conserver uniquement des métriques non sensibles.
4. Mettre à jour README, rapport de vérification, statut, matrice, changelog et état Codex
   avec la distinction « MVP logiciel terminé / certification matérielle limitée ».

### Risques et tests

| Risque | Parade et preuve attendue |
|---|---|
| Déclarer une validation physique non exécutée | conserver chaque protocole concerné en `Non exécuté` ou `Bloqué matériel` |
| Diagnostic dépendant du `PATH` local | accepter Ninja du SDK versionné et exécuter `doctor.ps1` jusqu'à zéro erreur |
| Variante finale divergente | assembler toutes les variantes dans un même gate et relever leurs empreintes |
| Régression du contrôle continu | conserver les tests deltas/aperçu/commit et la confirmation tactile utilisateur |
