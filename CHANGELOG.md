# Changelog

## Unreleased

### Added

- Étape 7/V2.3 : treize boutons de gammes permanents dans la zone main gauche, accords
  et articulations appliqués dès le touch-down avec sémantiques accessibles conservées.
- Arpège autonome par pad maintenu, cadencé par le tempo/division sans transport ni
  Tone Row, avec curseur/ownership par source et revoicing immédiat lors de SetChord.
- Variante Performance `0.2.3-dev-performance` validée 300/300 JVM, CTest 2/2, cinq
  Lint, 8/8 instrumentés sur SM-X620 et installée AOT `speed` aux côtés de la V1.

- Dépôt Git local initialisé sur `main`, remote `origin` configuré et Gradle Wrapper
  officiel 8.13 ajouté avec JAR et distribution protégés par leurs empreintes SHA-256.
- Workspace VS Code/Codex et documentation d’architecture.
- Socle Kotlin pur pour navigation intervallique, accords et routage MIDI.
- UI Compose de performance et adaptateurs Android de départ.
- Moteur audio Oboe/DSP minimal et tests natifs hôte.
- Cinq étapes Codex autonomes avec critères d’acceptation.
- Sixième étape Codex pour l’ergonomie deux mains et la réduction de la régression de
  latence, avec plan, critères et preuve SM-X620.
- Variante V2.2 `performance` minifiée, coinstallable avec la V1, signée localement et
  compilée avec le moteur natif Release.
- V2.1 coinstallable `0.2.1-dev-instrumented` nommée « Interval Tablet V2 », sans
  remplacement du package V1.
- Force to Scale déterministe, treize gammes standards 12-TET et persistance
  Settings v5/Preset v4/banque v3 avec migrations historiques.
- Surface harmonique permanente : dix variantes d’accords en deux rangées et gammes
  accessibles directement sur la page de performance.
- Parseur MIDI incrémental testé : running status, temps réel intercalé, SysEx et messages fragmentés.
- Audit de structure portable et compilation hôte du pont JNI/Oboe contre stubs.
- Traçabilité vers le guide utilisateur, protocole matériel et rapport de vérification.
- CI GitHub Actions, Dependabot et procédure de protection/configuration du dépôt.
- Reducer pur d'éditeur MIDI Learn avec baseline, brouillon, capture Note/CC, canal reçu
  ou Omni, seuil CC, collision exacte, recouvrement Omni et commit atomique.
- Panneau MIDI Learn dédié avec états attente/candidat/conflit, ajout/remplacement,
  suppression, reset, Save et Cancel.
- Quatre parcours Tone Row supplémentaires : Auto-Transpose haut/bas et Auto-Translate
  haut/bas, portant le total à huit.
- Prévisualisation pure des déplacements, degré courant et indications de limite
  clamp/wrap pour l'interface de performance.
- Repository Android MIDI injectable avec catalogue de ports, états de connexion,
  générations de session et événements de hotplug/erreur.
- Coordinateur applicatif sérialisé pour les actions UI/MIDI, le cycle de vie, les
  changements de port et les sorties MIDI/audio ordonnées.
- Réglages versionnés pour le contexte musical, les canaux, le routage, les ports
  descriptifs, le mapping et Performance Lock.
- UI Compose originale et adaptative avec HUD de scène, grille multi-touch, utilitaires
  Home/Undo/Panic et console MIDI non modale.
- Icône d'application originale fondée sur les directions intervalliques.
- Dependency locks Gradle et métadonnées de vérification SHA-256 du graphe résolu.
- Machine d'état Tone Row déterministe : enregistrement 5/7/12, lecture manuelle et
  automatique, séquence éditable, Play Once et parcours Prime/Retro/Random/Pendulum.
- Transformations Tone Row composables : inversion, translation diatonique,
  transposition chromatique, octave et reset sans effacement de la rangée.
- Transport pur avec horloge interne, MIDI Clock 24 PPQ, Start/Continue/Stop/Pause,
  division, tempo, durée de gate et sources exclusives.
- Première persistance Settings/Preset v2, migration du format plat v1, session de travail et
  banque interne bornée de 128 slots.
- Politique de rappel Program Change/Song Select et types MIDI Song Select parsés/encodés.
- Deck Compose Tone Row avec timeline, curseurs, transport, transformations, arrangement
  et gestion des presets sous Performance Lock.
- Variante `benchmark` minifiée/profileable dans un package isolé, harnais `gfxinfo`
  vérifiant APK/foreground/90 Hz et variante `instrumented` distincte pour la tablette.
- Tests déterministes du moteur audio : cycle Oboe, reprise, ownership callback, overflow,
  générations, drain borné, reset d'effets et ciblage des coefficients.
- Projections Compose étroites et tests des neuf pads dessinés/accessibles sur appareil.
- Trois articulations de pads persistées : lead seul (`ARPEGGIATED`), voicing plaqué
  (`STACKED`) et navigation/enregistrement sans Note On (`MUTED`).
- Strummer adaptatif et accessible, vertical ou horizontal selon la fenêtre, projeté
  depuis le voicing du domaine avec crossings, inversion, vélocité, multi-pointeur et
  hystérésis.
- Actions déterministes `PressPadAbsolute` et `StrumTone`, avec ownership/release par
  origine et invariance de la note courante pendant un strum.
- Settings/Preset v3 et banque v2 avec identifiants d'articulation stables et migrations
  depuis v1/v2.
- Contrat `SynthPatch` Kotlin pur de seize paramètres audio typés, panneau synthé non
  modal et diagnostics natifs dédiés dans l'UI tablette.
- Test appareil AUDIO-01 borné à dix cycles Oboe, avec sonde des seize identifiants de
  paramètres et journal de sample rate, burst, file, xruns et erreurs.
- Oracle de packaging natif qui vérifie dans l'APK `libinterval_audio.so`, `liboboe.so`
  et `libc++_shared.so` pour chaque ABI produite.

### Changed

- Scène de jeu réorganisée à deux mains en portrait et en paysage : harmonie, Force to
  Scale, dix accords et strummer à gauche ; utilitaires et intervalles à droite.
- Acteur musical placé sur un thread mono-thread possédé, nommé et prioritaire audio ;
  la variante instrumentée n’est plus utilisée comme application de jeu.
- Articulations compactées en grille 2×2 afin de préserver des cibles tactiles de 48 dp
  et la hauteur du strummer.
- Pads portrait compactés avec cible minimale de 48 dp et zone de grille plafonnée ; le
  Strummer horizontal utilise une hauteur réduite.
- Panneau Synthé complété avec trois contrôles distincts pour le temps, le feedback et le
  mix du delay, tous prévisualisés pendant le geste puis persistés au relâchement.

- Domaine musical finalisé pour la porte 1 : mouvements `-14…+14`, ancre hors gamme,
  wrap/clamp, Home, Undo, changements de contexte et invariants de note range.
- Accords rendus symétriques par instance, avec doublures conservées, vélocités bornées et
  harmonies hors plage omises.
- Routage MIDI consolidé autour de leases par origine, destination, canal et compteur ;
  les quatre modes conservent la route décidée au Note On.
- Parseur MIDI borné et réinitialisable par connexion, avec resynchronisation sûre du
  running status, du temps réel et des SysEx.
- Écran de performance rendu tolérant aux tailles compactes et aux recréations sans
  reproduire l'apparence du matériel de référence.
- Dépendances Android alignées sur la ligne compatible compile/target SDK 36.
- Random mappé joue désormais immédiatement un intervalle déterministe `-14…+14` dans le
  reducer d'instrument ; il ne sélectionne plus le mode Random Tone Row.
- Chromatic Shift mappé devient un modificateur silencieux et momentané, possédé par sa
  Note ou son gate CC jusqu'au relâchement, à la purge ou au Panic.
- Same Interval répète le dernier mouvement diatonique, tandis que Same Pitch répète le
  dernier écart chromatique réellement entendu.
- Play, Stop et Record restent reliés au reducer Tone Row ; un second Record annule la
  prise en cours.
- La lecture manuelle Tone Row reste disponible en Pause. Une Note MIDI fournit une
  vélocité live pour l'émission sans modifier l'entrée enregistrée.
- Random Tone Row part du premier élément et conserve le signe du pas demandé avec une
  magnitude `0…2×|pas|` ; les modes Auto accumulent leur demi-ton/degré par cycle.
- La capture Learn est interceptée avant politique Program/Song Select et routage ; seul
  Save remplace/persiste le mapping courant, tandis que Cancel jette le brouillon.
- Mapping v1 est conservé ; les presets existants ne changent qu'après resauvegarde
  explicite de leur slot.
- Les notes Tone Row passent par `PressAbsolute`, le voicing et le registre d'ownership
  existants ; l'horloge et les releases planifiées reviennent dans la mailbox applicative.
- Les snapshots restaurent contenu, contexte, mapping, routage et options, mais jamais
  notes actives, curseurs transitoires, deadlines ni lecture en cours.
- L'acteur musical, l'horloge, les gates et les one-shots quittent Main pour un dispatcher
  sérialisé injectable ; DataStore et diagnostics utilisent des dispatchers séparés.
- La coque Compose ne lit plus l'état monolithique : header, contenu/curseurs Tone Row,
  pads, ruban, console et statut observent leurs propres projections.
- Les neuf pads conservent leurs cibles tactiles/focus/sémantiques tout en dessinant texte,
  fond et badges via cache ; l'Arrangement ne réveille que les puces de curseur concernées.
- Le stream Oboe tente un cycle Exclusive complet puis Shared, négocie son sample rate et
  reprend hors callback avec génération et intention de cycle de vie.
- Chorus et buffers circulaires évitent trigonométrie/modulo répétés ; fréquences MIDI et
  coefficients sont précalculés ou mis à jour par famille de paramètres.
- Les pressions Tone Row Record/Manual respectent l'articulation de pad ; Auto Tone Row
  conserve volontairement le voicing complet historique.
- Les anciens presets infèrent `ARPEGGIATED` pour l'accord Off et `STACKED` pour un accord
  actif afin de conserver le rendu sonore antérieur.
- Le mix des oscillateurs est normalisé uniquement au-delà d'une somme de 1 ; le master
  et le trim voix restent inchangés après correction des gains internes.
- Settings passe à v4 pour persister le patch synthé global ; presets v3, banque v2,
  Program Change et Song Select restent strictement musicaux et ne rappellent pas ce patch.
- Les gestes des sliders synthé publient désormais un aperçu audio confluent, au plus une
  position par frame et limité aux paramètres filaires modifiés ; le patch complet reste
  publié et persisté seulement en fin de geste. Cutoff canonique et UI partagent la plage
  20 Hz–20 kHz.
- Mix d'oscillateurs, pulse width, sustain et coefficients du filtre sont lissés dans le
  chemin DSP borné, avec snap exact à la cible près de zéro.

### Fixed

- Régression de latence de la V2.1 de test : livraison R8/AOT dédiée et chemin musical
  découplé de la compétition du pool générique.
- Compression du strummer paysage à 22 dp et panneau MIDI Learn trop court ; la surface
  harmonique latérale libère désormais une vraie zone de jeu et de défilement.
- Same Pitch calcule désormais depuis l'ancre non shiftée puis compose exactement le
  Chromatic Shift courant : les cas stable, ajouté et relâché conservent la hauteur
  attendue ; une Note externe entendue devient aussi la référence du delta suivant.
- `UndoThenMove` devient le dernier déplacement répétable par Same Interval. Les leases
  Shift Note/CC survivent au remplacement du mapping jusqu'à leurs releases d'origine.
- Play Once comptabilise sa dernière émission comme fin du cycle logique avant le retour
  manuel ; Auto-Transpose/Auto-Translate accumulent donc leur ±1 terminal, tandis que
  Restart et Reset restaurent les accumulations documentées.
- Diagnostic Windows : Ninja fourni par CMake 3.22.1 dans le SDK Android est désormais
  accepté même s'il n'est pas inscrit dans le `PATH` global ; le diagnostic Unix applique
  la même recherche de repli.
- Contrôles Synthé auparavant audibles seulement après relâchement : le moteur suit
  maintenant le glissement, la dernière position est garantie au commit et une fermeture
  pendant le geste finalise proprement le brouillon.
- Packaging Debug/Instrumented d'Oboe sous Windows lorsque le cache Prefab réside dans
  un chemin utilisateur contenant un espace : les lanceurs isolent maintenant par défaut
  les caches Gradle/Android dans le workspace et le gate refuse un runtime APK incomplet.
- Métadonnées SHA-256 manquantes pour cinq BOM/POM transitifs déjà déclarés, vérifiées
  auprès de Maven Central avant ajout au manifeste Gradle.
- Libération garantie des notes lors d'un Panic, d'une déconnexion, d'un arrêt de cycle de
  vie ou d'un changement de destination.
- Ordre FIFO Panic/fermeture de port et rejet des paquets appartenant à une ancienne
  génération de connexion.
- Déclenchement CC sur front, Toggle et Panic restant accessibles en mode PassThru.
- Récupération conservatrice après saturation de la file MIDI et réouverture d'un port en
  erreur lorsqu'il réapparaît.
- Mailbox MIDI Out bornée et ordonnée : un seul drain Android, sélection latest-request-wins,
  reset CC 123/120 sur 16 canaux et Panic logique après saturation.
- Portabilité du lanceur Gradle PowerShell, cache local au workspace et compilation Kotlin
  sans daemon externe.
- Actions CC non tenues déclenchées une seule fois sur front montant puis réarmées sous
  le seuil, y compris les commandes Tone Row.
- Voix automatique libérée avant la suivante et lors de Pause, Stop, Panic ou changement
  de destination ; une release tardive d'une ancienne origine ne coupe pas la nouvelle.
- MIDI Stop conserve les positions et place Tone Row en pause, tandis que le Stop local
  revient explicitement à `Idle`.
- Changement de grille pendant Record terminant la prise avant d'appliquer le nouveau
  référentiel.
- Callback interne tardif rebasant l'échéance suivante après un seul tick, sans rafale de
  rattrapage dans l'acteur UI.
- Gate sous MIDI Clock calculé depuis la période positive réellement observée des pulses,
  avec tempo local comme repli avant la première mesure.
- Destination d'une voix Tone Row mémorisée jusqu'à son Note Off et portée par les
  releases planifiées, y compris pour une commande explicitement ciblée.
- MIDI Start reçu pendant Record terminant la prise avant Auto Play ; capacité de prise
  limitée aux classes réellement accessibles dans une plage étroite.
- Autosauvegarde des changements durables d'une prise tactile/MIDI sans écrire pour les
  Note Off, ticks, curseurs et deadlines transitoires.
- Gonflement de la timeline et placement sous les barres système corrigés sur tablette,
  avec test instrumenté de régression.
- Durée de vie des callbacks Oboe protégée par ownership partagé et shutdown JNI terminal,
  y compris lorsqu'un callback d'erreur survit au handle d'activité.
- Courses start/stop/recovery et événements d'ancienne génération neutralisés avant le
  rendu ; drain SPSC limité à 128 événements par bloc.
- Overflow audio rendu conservateur : Panic d'urgence et silence immédiat, avec reset O(1)
  des grandes lignes à retard sans résurgence de tail.
- One-shot ordonné après son Press accepté, fermeture finale sérialisée et télémétrie MIDI
  de trafic retirée du chemin musical haute fréquence.
- Autosauvegarde confluentée après les effets temps réel, sans deux snapshots complets sur
  chaque Note On/MIDI Clock, et diagnostics natifs hors acteur musical.
- Dernier snapshot durable conservé et retenté après un délai borné en cas d'erreur transitoire
  de stockage, sans bloquer l'acteur ni perdre les mutations plus récentes.
- Accès JNI audio protégé par ownership, producteur `send` strictement sérialisé pour la
  SPSC et verrou de lifecycle lecture/écriture : les diagnostics restent indépendants,
  tandis que start/stop/close gardent exclusivement la durée de vie native.
- Paramètres d'effets/master conservés lors d'un `prepare()` ou d'une reprise à un autre
  sample rate ; NaN/Inf, notes et vélocités invalides sont neutralisés aux frontières.
- Durées ADSR recalibrées comme temps-to-target réels à 44,1/48/96 kHz, avec fin de
  release bornée au lieu d'une accumulation excessive de tails.
- Réverbération normalisée par moyenne stéréo, send compensé et moyenne des quatre combs ;
  all-pass corrigés dans leur forme canonique à magnitude unitaire.
- Limiteur rendu exactement transparent sous son knee, continu et monotone au-dessus au
  lieu de saturer aussi le signal nominal.
- Mix d'oscillateurs supérieur à l'unité normalisé proportionnellement avant sommation.
- Jitter de frontière du strummer absorbé par une hystérésis de 8 dp sans perdre les
  cordes intermédiaires ni empêcher les inversions.
- Patch synthé rejoué intégralement après chaque démarrage et après une reprise de stream,
  y compris lorsqu'il change pendant la fenêtre de récupération native.
- Panneau synthé désactivé jusqu'au chargement DataStore afin qu'aucun draft affiché ne
  puisse diverger silencieusement de l'état appliqué et persistant.

### Deferred

- Rest, Random Step et Ratchet dans les séquences ; Ratchet attend un scheduler de
  retrigger annulable par génération.
- Émission MIDI Clock/Start/Stop/Continue et Song Position Pointer.
- Actions mappées de gamme/clé/accord/preset, CC relatifs/continus, profils et
  import/export.
- Gammes personnalisées/scopes de presets, optimisation soutenue à 90 Hz et certification
  USB MIDI/Learn, TalkBack, vrai multi-touch, loopback/hotplug et soak.

### Verified

- Gate Stage 6/V2.2 : 136/136 domaine, 161/161 application, 8/8 instrumentés sur
  SM-X620, CTest 2/2, cinq Lint sans issue, tous les assemblages et quatre ABI.
- Stress V2.2 Performance de 108 frappes : p50 14 ms, p90 17 ms, p95 18 ms, p99 19 ms,
  12/121 signaux de forte latence d’entrée ; audio 48 kHz, burst 96, buffer 192,
  queue 0/16, zéro xrun/drop/reprise/erreur. Ce test n’est pas un loopback acoustique.
- APK `app-performance.apk` final : 9 227 116 octets, SHA-256
  `CD708425DEC4671AD3DAC43F5B699A8C3FB9EE555DF994A79030231F33E7AE14`, installé et
  compilé ART `speed`; seules V1 et V2.2 restent sur la tablette.
- Gate Stage 5/V2.1 : 136/136 tests domaine sur 12 suites et 161/161 tests application
  sur 19 suites, soit 297/297 ; CTest 2/2, quatre Lint sans issue, tous les assemblages et
  runtimes natifs des quatre ABI validés.
- Réception directe SM-X620/API 36 : 7/7 en 17,279 s, incluant accords/Force to Scale,
  pads compacts et gestes du temps/feedback delay. Seuls les packages V1 et V2.1 restent
  installés après retrait de l’APK de test.

- Gate Stage 4/V2 final du 1er septembre sur le commit
  `13c2d7c4915e8da65c5e6898daf8ee9a5f253e75` : 131/131 tests domaine sur 11 suites et
  160/160 tests application sur 19 suites, soit 291/291 sans échec, erreur ni test ignoré ;
  CTest 2/2.
- `doctor.ps1` termine avec 0 erreur et un avertissement attendu pour `kotlinc` absent ;
  `verify-structure.ps1` et `verify.ps1` sont verts, avec quatre ABI contrôlées. Les Lint
  Debug, Release, Benchmark et Instrumented rendent tous `No issues found.` ; gate
  principal, variantes et AndroidTest réussissent.
- Suite directe finale SM-X620/API 36 : 7/7 en 15,603 s, couvrant MIDI Learn
  conflit→Replace→Save/Cancel, sliders Synthé, pads, Tone Row et dix cycles audio
  start/stop. Cette réception UI ne vaut pas capture Learn depuis un périphérique USB.
- Artefacts V2 finaux : Debug 40 596 619 octets
  (`744f5a08af37861eaecdb8c3b2a7a47b2dda68283929a50d08c3849ee9db5dc4`), Release non
  signé 9 199 584 (`c91874fb5628f9484673a089287515c27bc3074da74848dc3192ed3bcf971ed8`),
  Benchmark 9 207 816 (`87b2b48693d19c82bfaf6929695cc645a2a61bf2818780aa434eb603c364ffe2`),
  Instrumented 40 317 773 (`43adb0eb7a518367c0ce313f62f323217855b55d320609b7fd57c13f82aed029`)
  et AndroidTest 2 543 071 (`e78d057e92eeb42085643c098adf476147abf96d0f4e15e64afeee6fd47e711f`).

Les entrées suivantes conservent, comme archives datées, les preuves successives des
portes 1 à 3 et de la clôture MVP antérieure à la V2 ; leurs totaux ne remplacent pas le
gate final 291/291 ci-dessus.

- Gate Gradle de la porte 1 réussi : 41 tests domaine et 42 tests JVM application, sans
  échec, puis lint et assemblage debug.
- Lint Android sans problème (`No issues found.`) et APK debug produit.
- Vérification native hôte réussie (1/1 test CTest).
- Validations USB MIDI, vrai multi-touch, TalkBack et soak Oboe de 60 minutes laissées
  explicitement en attente ; la réception SM-X620 ne vaut pas validation matérielle complète.
- Couverture déterministe ajoutée pour Tone Row, transport, parser Clock/Song Select,
  presets/migrations, politique de rappel, coordinateur et modèle UI. Le rapport de
  vérification conserve les totaux du dernier gate complet.
- Gate de la porte 2 réussi : 79 tests domaine et 91 tests JVM application, aucun échec ;
  Lint `No issues found.` et assemblages debug, release et de test Android réussis.
- APK debug historique de clôture de l'étape 2 : 39 265 007 octets, SHA-256
  `B9E6A8EC9A73315AB3D3E8888F3CF078B887CE345528A309421D4C690AA7FBD4`.
- Réception partielle sur Samsung SM-X620/API 36/90 Hz : installation, lifecycle court,
  pad simple, Tone Row, presets, rotation et police 1,3× observés ; test final direct
  `am instrument` réussi (1 test, 1,593 s), après un `connectedDebugAndroidTest` vert.
- Sur le scénario Release AOT fixe, médiane de frame améliorée de 18 à 16 ms et jank legacy de
  65,90 % à 63,20 % ; p90 24 ms et jank strict final 99,58 %, donc dette 90 Hz ouverte.
- MIDI Clock/transport et rappels de presets sur périphérique réel restent explicitement
  non validés.
- Gate logiciel initial de l'étape 3 (21 août) réussi : domaine 79/79, application JVM 109/109, soit
  188/188 tests JVM ; vérification native 2/2.
- `verify-domain` utilise désormais un build Gradle autonome lorsque `kotlinc` est absent ;
  ce repli a été exécuté avec toutes les variables SDK Android supprimées.
- Lint debug, benchmark et instrumented : 0 issue ; `lintVitalRelease` : 0 issue ;
  assemblages debug, release unsigned, benchmark, instrumented et test APK réussis.
- Instrumentation finale sur SM-X620 : 2/2 tests, couvrant neuf pads accessibles
  distincts d'au moins 72 dp avec action sémantique et la conservation de la scène sous
  une timeline remplie.
- APK debug final : 39 720 679 octets, SHA-256
  `C46A4E11E3269DA968A5B6892155A13BD18577A61BAB5FDABBE964E5EE5956FD` ; APK benchmark
  installé et compilé AOT `speed` : 8 932 016 octets, SHA-256
  `3F0E2B057159B6A26F7D1F2C434C6A3F9ACB4D0D6F82B8BF611ABF2C196E80C8`.
- Huit passes `gfxinfo` 30 s + 10 s de warm-up, contrebalancées OFF/ON à 90 Hz : médianes
  plateforme identiques à p50 18 ms, p90 22,5 ms, p95 23 ms et p99 26,5 ms ; jank strict
  99,475 % OFF contre 99,685 % ON, legacy 68,985 % contre 75,155 %.
- Les distributions OFF/ON se recouvrent et ne permettent pas d'attribuer un surcoût au
  DSP ; la durée totale de frame reste au-dessus du budget 11,11 ms. `gfxinfo` ne mesure
  ni latence tactile/audio, ni latence MIDI. Stop puis Panic ont été exécutés après mesure.
- Gate courant articulation/anti-saturation : 88/88 tests domaine sur 9 suites et 128/128
  tests application sur 17 suites, soit 216/216 JVM ; `lintDebug` indique
  `No issues found.`.
- Variante benchmark courante reconstruite avec R8 ; `lintBenchmark` indique
  `No issues found.`. APK : 8 953 212 octets, SHA-256
  `A1E2F015A900ED8B62CC1CED1FABB313247DC74DA6A1DD484E983B7E8779584C`, installé pour un
  smoke minifié réussi ; non AOT et sans nouvelle passe A/B. Le cold start unique à
  239 ms n'est pas interprété comme un benchmark.
- Vérification native stricte courante : 8/8 unités compilées et 2/2 suites réussies.
- Instrumentation finale SM-X620/API 36 : 3/3 en 5,876 s ; trois modes et trois cordes
  accessibles, navigation `MUTED` puis strum sans dette de note ni déplacement pendant
  le strum.
- Smoke fonctionnel court en `STACKED` : douze taps alternés puis strum aller/retour,
  toutes les entrées acceptées, UI/processus vivant, moniteur audio actif sans erreur
  visible et retour à zéro note active ; aucune conclusion d'écoute/xrun n'en est tirée.
- La qualité sonore subjective du correctif anti-saturation, le loopback, MIDI USB et le
  soak audio de 60 minutes restent explicitement non validés.
- Gate synthé final : 93/93 tests domaine et 138/138 application (231/231 JVM), 2/2 suites
  natives, lint Debug/Benchmark/Instrumented/VitalRelease et tous les assemblages réussis.
- Instrumentation finale SM-X620/API 36 : 6/6 en 17,94 s. AUDIO-01 passe 10/10 cycles à
  48 kHz, burst 192, buffer 384, file max 17, zéro drop/reprise/erreur et `maxXruns=1` ;
  écoute, loopback, hotplug et soak restent ouverts.
- Revalidation SM-X620/API 36 du 31 août : gate 231/231 JVM et 2/2 natifs, Lint
  Debug/Instrumented vert, runtime APK vérifié sur quatre ABI et instrumentation 6/6 en
  16,222 s. AUDIO-01 passe 10/10 cycles à 48 kHz, burst 96, buffer 192, file max 17,
  zéro drop/reprise/erreur/xrun. Aucun périphérique USB MIDI physique n'était exposé.
- Correctif de suivi continu Synthé : 94/94 tests domaine et 140/140 application
  (234/234 JVM), 2/2 suites natives, Lint Debug/Instrumented sans issue et suite finale
  SM-X620/API 36 à 6/6 en 13,723 s. L'APK corrigé reste ouvert pour réception tactile et
  auditive ; cette écoute utilisateur n'est pas revendiquée par les tests automatisés.
- Clôture du MVP logiciel le 1er septembre : diagnostic à zéro erreur, 234/234 tests JVM,
  2/2 suites natives, Lint Debug/Release/Benchmark/Instrumented sans issue, cinq
  assemblages réussis et suite SM-X620/API 36 à 6/6 en 13,612 s. L'utilisateur confirme
  le suivi audible continu des sliders ; USB MIDI, loopback, hotplug, TalkBack et soak de
  60 minutes restent des limites de certification matérielle non exécutées.
