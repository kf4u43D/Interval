# Critères d’acceptation

## Convention de preuve

Une case cochée signifie que le comportement est couvert par une preuve reproductible dans le workspace (test déterministe, analyse Lint ou artefact de build). Une validation qui dépend d’une tablette, d’un périphérique USB MIDI ou d’un flux audio réel reste décochée, même lorsque son chemin logiciel est implémenté et couvert par des doubles de test.

## Porte 1 — Instrument MIDI

### Validations logicielles acquises

- [x] L’APK debug est assemblé avec `minSdk=29` et constitue l’artefact candidat à l’installation.
- [x] Les neuf actions tactiles et mappées `-4…+4` convergent vers les mêmes `InstrumentAction` et le même reducer.
- [x] `0`, Undo, Home, changement de gamme/clé et note range passent leurs tests déterministes.
- [x] Les neuf accords et Off respectent vélocités, doublures, ordre des voix et limites de plage.
- [x] Les pads ont trois articulations déterministes : lead seul, voicing plaqué ou
  navigation muette ; un changement de mode ne coupe pas les sources déjà tenues.
- [x] Le strummer égrène le voicing courant à vélocité pleine par corde sans déplacer la
  note, avec origine/release distinctes, doublures et indices invalides couverts.
- [x] La sélection MIDI In/Out, les générations de connexion, la perte et la reconnexion sont couvertes par le contrat du faux repository et les tests du coordinateur.
- [x] Les routes Off, Active, Active Last Note et PassThru sont couvertes par les tests du routeur, y compris l’exception de sécurité Panic/Toggle consommable en PassThru.
- [x] Les leases survivent aux transitions de mode et un changement/purge de destination libère d’abord l’ancienne route dans les tests déterministes.
- [x] Panic vide leases et instances musicales, émet les Note Off explicites, puis CC 123 et CC 120 sur les routes concernées.
- [x] La surface Compose adaptative expose neuf cibles d’au moins 72 dp, des sémantiques accessibles et une propriété de pointeur indépendante ; le registre multi-touch est testé pour pressions simultanées et annulation.
- [x] Les trois modes et chaque corde du strummer restent accessibles sous Performance
  Lock ; crossings, inversion, multi-pointeur, vélocité et hystérésis sont testés hors appareil.

Ces preuves restent dans le gate combiné de l'étape 3. Le total exact du dernier gate
achevé est centralisé dans `docs/VERIFICATION_REPORT.md`; l'acceptation ci-dessus est
logicielle et ne vaut pas compte rendu matériel.

### Validations matérielles encore bloquées

- [x] Installer et démarrer l’APK sur une tablette API 29+ cible : réception partielle
  prouvée sur Samsung SM-X620/API 36.
- [ ] Valider la découverte/sélection MIDI USB et la reconnexion avec MIDI-USB-01 et MIDI-USB-05.
- [ ] Vérifier l’absence de note bloquée sur matériel lors des changements de mode/port et déconnexions avec MIDI-USB-04/05.
- [x] Observer paysage/portrait, police 1,3×, pad simple et parcours Tone Row/presets sur
  la tablette cible.
- [x] Observer les trois articulations, une navigation muette et un balayage de plusieurs
  cordes sans déplacement de la note courante sur la tablette cible.
- [ ] Valider le vrai multi-touch et la chaîne tactile → MIDI avec MIDI-USB-02, puis
  effectuer le contrôle TalkBack/ordre de focus/contraste sur la tablette cible.

Le lot de porte 1 est donc implémenté et prouvé du point de vue logiciel, mais sa réception matérielle reste ouverte. Le moniteur audio réel relève de la porte 3 et n’est pas une condition de la réception logicielle MIDI de la porte 1.

## Porte 2 — Tone Row/transport

### Validations logicielles acquises

- [x] Les fixtures 5, 7 et 12 notes terminent sans classe répétée ; fin précoce,
  recherche directionnelle et fin automatique sont couvertes.
- [x] La lecture manuelle reboucle les indices, `0` rejoue et Undo/Restart revient au
  premier élément avec une libération symétrique.
- [x] La séquence automatique est éditable en direct, bornée à 64 pas et ne devient
  jamais vide ; son défaut/reset est `{+1}`.
- [x] Prime, Retro, Random à graine explicite et Pendulum sans double extrémité sont
  couverts par des fixtures déterministes.
- [x] Inversion, transposition chromatique, translation diatonique, octave, ordre de
  composition et clamp de plage suivent les règles documentées.
- [x] Play Once compte l'émission initiale, produit exactement une longueur de rangée,
  programme la libération selon le gate et arrête le transport.
- [x] Horloge interne, Clock MIDI 24 PPQ, division, tempo, gate, Start, Continue, Pause et
  Stop sont couverts avec timestamps simulés et sources exclusives.
- [x] MIDI Stop libère la voix et met Tone Row en pause sans perdre sa position ; Continue
  reprend sans redéclenchement avant le tick suivant. Le Stop local revient à `Idle`.
- [x] Les snapshots et la banque de 128 presets sont bornés, versionnés, migrables depuis
  v1/v2, y compris l'articulation, et restaurés dans un état sûr `Idle`/`Stopped`.
- [x] Program Change cible `0…127` avec filtrage du canal d'entrée, Song Select cible
  globalement `0…127`, un slot absent n'est pas consommé et PassThru n'effectue aucun rappel.
- [x] L’acteur ViewModel est testé avec horloge et adaptateurs injectés pour la
  restauration, l’autosauvegarde, les rappels MIDI, le callback tardif et HostStop.
- [x] La surface Compose expose timeline, curseurs, transport, transformations et presets ;
  Performance Lock masque/bloque l'arrangement secondaire.
- [x] Les tests de la porte 1 restent inclus dans le gate combiné, sans remplacement de
  leurs invariants de leases, Panic, hotplug logique et files bornées.

### Validations matérielles encore ouvertes

- [ ] Recevoir MIDI Clock/Start/Continue/Stop réels et mesurer les rafales/jitter avec
  `TRANSPORT-01`.
- [ ] Recevoir Program Change/Song Select et les rappels de slots sur un contrôleur USB.
- [x] Observer l'affichage timeline/cibles et les parcours Tone Row/presets exécutés sur
  la SM-X620.
- [ ] Recevoir Performance Lock et l'ergonomie Tone Row pendant une session prolongée.
- [ ] Confirmer l'ordonnanceur interne, le lifecycle et l'absence de note bloquée lors
  d'une session physique prolongée.

La porte 2 est donc acceptée du point de vue des transitions et adaptateurs testables sans
matériel. Elle ne constitue pas une validation USB/tablette et ne ferme aucun protocole
matériel ci-dessus.

## Porte 3 — Audio/robustesse

### Validations logicielles intégrées

- [x] L'acteur musical, l'horloge, les gates et one-shots sont sérialisés hors Main ; les
  tests bloquent séparément Main, persistance et diagnostics sans arrêter le chemin musical.
- [x] Les snapshots durables sont immuables et confluentés sur un worker I/O avec retry ;
  la télémétrie MIDI haute fréquence et les diagnostics natifs sont hors de la mailbox.
- [x] Le cycle Oboe Exclusive→Shared, le sample rate négocié, stop/restart, reprise hors
  callback et rejet des générations anciennes sont couverts par les tests natifs hôte.
- [x] Huit voix, ADSR, filtre, oscillateurs PolyBLEP, chorus, delay, réverbération et
  limiteur sont compilés et exercés par les tests DSP hôte.
- [x] Les durées ADSR sont vérifiées à 44,1/48/96 kHz ; le mix d'oscillateurs, le gain de
  réverbération et les all-pass sont normalisés, et le limiteur reste exactement
  transparent sous son knee.
- [x] Le callback ne fait ni allocation, verrou, I/O, log ou JNI ; son drain SPSC est
  borné et un overflow déclenche un Panic d'urgence conservateur.
- [x] Paramètres bornés/ciblés, coefficients précalculés et sorties sans NaN/Inf sont
  couverts, y compris valeurs non finies, reset Panic et préparation à plusieurs
  fréquences d'échantillonnage.
- [x] Le contrat typé `SynthParameter`/`SynthPatch` fixe 16 identifiants wire, bornes et
  défauts, dont un cutoff canonique `20…20 000 Hz`, et rejette les valeurs non finies.
- [x] Les mix saw/pulse/triangle, la pulse width, le sustain, les coefficients du filtre,
  les effets et le master sont lissés par échantillon puis rejoignent exactement leur
  cible par snap.
- [x] Le patch complet est rejoué après une reprise du stream, y compris lorsque
  `restartCount` augmente sans état arrêté observé ; un rejeu partiel refusé entraîne
  Panic et arrêt du moniteur plutôt qu'une divergence silencieuse.
- [x] L'ownership RAII partagé et le shutdown JNI idempotent couvrent les callbacks
  tardifs et les courses start/stop/recovery sans libération prématurée.
- [x] Compose observe des projections étroites ; les neuf pads restent des cibles
  tactiles/focus/sémantiques distinctes d'au moins 72 dp.
- [x] Le panneau Synthé expose les contrôles de timbre, filtre, enveloppe, effets/master
  et les diagnostics stream, sample rate, burst, buffer, file, xruns, drops, reprises et
  dernier code d'erreur avec sémantiques accessibles.
- [x] Le son suit les sliders Synthé pendant le glissement avec au plus un aperçu par
  frame et seulement les paramètres modifiés ; seul le patch final est publié et persisté
  au relâchement ou à la fermeture du panneau.
- [x] Les variantes `benchmark` minifiée/profileable et `instrumented` non minifiée sont
  séparées du package utilisateur et l'une de l'autre.

### Dernier gate logiciel MVP acquis — archive antérieure à la V2

- [x] Le gate final du 1er septembre réussit 234/234 tests JVM (94 domaine et 140
  application), 2/2 tests natifs, les quatre analyses Lint et tous les assemblages ;
  l'APK exige aussi les runtimes Oboe/C++ pour chaque ABI native produite.
- [x] La suite instrumentée finale réussit 6/6 sur Samsung SM-X620/API 36 et
  l'utilisateur confirme que les contrôles Synthé suivent désormais le doigt en continu.
- [x] La campagne A/B du gate précédent reste archivée avec huit passes contrebalancées,
  APK benchmark identifié et AOT, métadonnées appareil, dumps bruts et résumés versionnés.
- [x] La matrice comportementale expose les preuves logicielles et les écarts matériels
  restants sans présenter `gfxinfo` comme une latence audio/MIDI.

### Réception matérielle encore ouverte

- [x] AUDIO-01 exécute dix cycles start/stop Oboe réels sur SM-X620/API 36 : la
  revalidation du 31 août observe 48 kHz, burst 96, buffer 192, profondeur maximale de
  file 17 et aucun drop/restart/code d'erreur/xrun.
- [ ] Valider le hotplug audio et la reprise après une déconnexion réelle sans affecter MIDI.
- [ ] Mesurer la latence toucher/MIDI→audio et le loopback sur la tablette cible.
- [ ] Confirmer par écoute comparative ou loopback que la saturation perçue a disparu ;
  la finitude et les niveaux DSP hôte ne constituent pas une qualification subjective.
- [ ] Session matérielle AUDIO-03 prolongée documentée.

AUDIO-01 prouve l'ouverture Oboe réelle et le cycle nominal. L'écoute comparative
anti-saturation, la latence loopback, le soak de 60 minutes et le hotplug audio restent à
recevoir ; les essais USB MIDI des portes 1 et 2 restent eux aussi ouverts. Ces protocoles
exigent du matériel absent et restent décochés : le MVP logiciel est terminé, tandis que
sa certification matérielle globale demeure partielle.

## Porte 4 — V2 performance et MIDI Learn

**Porte logicielle acceptée le 1er septembre 2026.** Le gate combiné vérifie le commit
`13c2d7c4915e8da65c5e6898daf8ee9a5f253e75`. Les résultats MVP de la porte 3 ci-dessus
restent une archive antérieure et ne sont pas additionnés aux chiffres V2.

### Critères logiciels validés

- [x] `Same Interval` répète le dernier déplacement diatonique et `Same Pitch` le dernier
  écart chromatique réellement émis, avec les oracles `+3→Same` et D→E→F♯.
- [x] Random Interval joue immédiatement un mouvement déterministe `-14…+14` sans changer
  le mode Tone Row ; même graine et mêmes actions donnent la même sortie.
- [x] Chromatic Shift est silencieux et momentané, s'additionne par source et disparaît
  correctement sur Note Off, passage CC sous seuil, purge et Panic, sans Note Off perdu.
- [x] Un second Record pendant l'enregistrement abandonne la prise et revient à `Idle`.
- [x] La navigation manuelle reste possible en `Paused`, Continue repart de cette position
  et la vélocité d'une Note MIDI n'affecte que l'émission courante.
- [x] Tone Row expose Prime, Retro, Random, Pendulum, Auto-Transpose haut/bas et
  Auto-Translate haut/bas ; Random conserve le signe dans `0…2×|pas|` et démarre au
  premier élément.
- [x] Les modes automatiques accumulent un demi-ton/degré par cycle logique ; Pause et
  Continue conservent la phase, tandis que Restart/Reset restaurent l'accumulation neutre.
- [x] Le reducer MIDI Learn pur couvre baseline, brouillon, armement, candidat, canal
  reçu/Omni, seuil CC `1…127`, collision exacte, recouvrement Omni, suppression, reset,
  Save, Cancel et baseline obsolète.
- [x] Une capture Note On/CC est consommée avant politique de preset et routage : elle ne
  joue pas, ne traverse pas et ne rappelle aucun preset.
- [x] Armer provoque Panic ; Save installe/persiste une seule fois le mapping complet et
  Cancel n'écrit rien. Lock, lifecycle, perte de source et overflow ferment sans conserver
  de capture transitoire.
- [x] Le panneau Compose dédié expose état d'attente, candidat, conflit/recouvrement,
  actions Add/Replace/Delete/Reset/Save/Cancel et sémantiques accessibles.
- [x] Mapping v1 reste lisible et les presets existants conservent leur mapping jusqu'à
  une nouvelle sauvegarde explicite du slot.
- [x] Toutes les suites des portes 1 à 3, Lint et les assemblages concernés repassent sans
  régression : 131/131 domaine sur 11 suites et 160/160 application sur 19 suites, soit
  291/291, plus CTest 2/2, quatre Lint sans issue et toutes les variantes demandées vertes.
- [x] Le gate principal et `scripts/verify.ps1` réussissent avec contrôle des quatre ABI ;
  les APK Debug, Release non signé, Benchmark, Instrumented et AndroidTest sont produits.

### Réception et performance encore ouvertes

- [x] Recevoir le panneau MIDI Learn sur SM-X620/API 36 sans périphérique USB : le test
  direct final couvre conflit→Replace→Save puis Cancel dans une suite 7/7 en 15,603 s.
- [ ] Exécuter MIDI Learn avec un contrôleur USB réel, canal exact puis Omni, Note et CC,
  et vérifier par moniteur externe que le message capturé ne sort jamais.
- [ ] Recevoir TalkBack, vrai multi-touch et les fermetures lifecycle/Performance Lock sur
  la tablette cible.
- [ ] Atteindre puis mesurer un rendu soutenu dans le budget de 11,11 ms à 90 Hz.
- [ ] Terminer les protocoles USB MIDI, loopback/hotplug audio et soak de 60 minutes déjà
  ouverts dans les portes précédentes.

### Reports explicites

Rest, Random Step et Ratchet, l'émission MIDI Clock/Start/Stop/Continue et Song Position
Pointer, les actions mappées de gamme/clé/accord/preset, les CC relatifs/continus,
profils/import-export et les bibliothèques/scopes étendus de gammes/presets ne sont pas
des critères de la V2. Ratchet attend un scheduler de retrigger annulable par génération.
CV, réseau, Scala, microtonalité, MPE et MIDI 2.0 restent hors périmètre.

## Porte 5 — V2.1 surface de performance et Force to Scale

### Critères logiciels

- [x] La variante coinstallable porte le libellé « Interval Tablet V2 », le package
  `dev.intervaltablet.instrumented` et la version `0.2.1-dev-instrumented` ; la V1
  `dev.intervaltablet.debug` reste installée en `0.1.0-dev-debug`.
- [x] Les neuf pads gardent des cibles tactiles d’au moins 48 dp et la scène portrait
  plafonne leur hauteur pour rendre la réduction immédiatement visible.
- [x] Les dix variantes d’accords sont affichées simultanément en deux rangées sur la
  page de performance, sans ouvrir la Console.
- [x] Treize gammes standards sont disponibles sur la scène et Force to Scale quantifie
  les notes générées vers la note la plus proche, égalité vers le bas.
- [x] PassThru reste inchangé, les notes tenues conservent leur release exacte et le
  réglage est restauré par Settings v5, Preset v4 et banque v3.
- [x] Le panneau Synthé expose séparément temps, feedback et mix du delay ; l’aperçu
  continu et le commit de fin de geste utilisent le patch existant.
- [x] Le gate complet domaine, natif, application, Lint, assemblages et structure passe
  sans nouvel avertissement important.
- [x] La suite instrumentée V2.1 finale passe sur SM-X620 et la tablette est nettoyée de
  son package de test temporaire.

Les périphériques USB MIDI réels, TalkBack, vrai multi-touch, 90 Hz soutenu, loopback,
hotplug et soak audio restent des validations matérielles distinctes.

## Porte 6 — V2.2 ergonomie deux mains et faible latence

### Critères logiciels et tablette

- [x] En portrait 900 × 1 440 dp, l’harmonie/les accords occupent le panneau gauche et
  les neuf intervalles le panneau droit, plus large ; la disposition reste latérale en
  paysage.
- [x] Les dix accords, les neuf pads, toutes les articulations et les cordes visibles
  conservent des cibles tactiles d’au moins 48 dp ; le strummer n’est plus comprimé à
  22 dp et MIDI Learn reste accessible.
- [x] La V2.2 de jeu utilise le package `dev.intervaltablet.performance`, la version
  `0.2.2-dev-performance`, R8, le moteur natif Release et une compilation AOT `speed` ;
  la V1 `dev.intervaltablet.debug` reste installée.
- [x] L’acteur musical possède un thread mono-thread fermé avec le ViewModel et placé à
  priorité Android audio, sans changer la machine d’état FIFO ni l’ownership des notes.
- [x] Le gate réussit 136/136 tests domaine, 161/161 application, 8/8 instrumentés,
  CTest 2/2, cinq Lint sans issue, tous les assemblages et le contrôle des quatre ABI.
- [x] Sur le stress de 108 frappes, p90 est à 17 ms et les signaux `High input latency`
  sont 12/121 ; le diagnostic audio reste à 48 kHz, burst 96, buffer 192, avec zéro xrun
  et zéro événement perdu.
- [x] Après réception, seules la dernière V1 et la V2.2 Performance restent installées ;
  les APK/packages de test et fichiers XML temporaires sont retirés.

Le scénario `gfxinfo` ne clôt pas la mesure loopback tactile/MIDI→audio. MIDI USB réel,
TalkBack, vrai multi-touch, 90 Hz soutenu, hotplug et soak audio restent ouverts.

## Porte 7 — V2.3 harmonie directe et arpège autonome

### Critères logiciels et tablette

- [x] Les treize gammes standards et les dix accords sont simultanément visibles dans le
  panneau main gauche, en portrait et en paysage, sans menu intermédiaire.
- [x] Gammes, accords et articulations répondent au front descendant tactile ; leurs
  actions sémantiques clavier/accessibilité restent distinctes et ne doublent pas le geste.
- [x] Changer d’accord revoicera immédiatement les pads maintenus, avec Note Off des
  anciennes instances avant les nouveaux Note On au même timestamp.
- [x] En mode Arpégé, maintenir un pad parcourt cycliquement toutes les voix disponibles
  au tempo/division configurés, transport arrêté et Tone Row vide compris.
- [x] Chaque arpège reste possédé par sa source ; Release, reconfiguration, Panic,
  HostStop et `onCleared()` annulent sessions, notes et échéances tardives.
- [x] Le gate réussit 138/138 tests domaine, 162/162 tests application, soit 300/300 JVM,
  CTest 2/2, cinq Lint sans issue, tous les assemblages et le runtime natif quatre ABI.
- [x] La suite SM-X620/API 36 réussit 8/8 en 28,464 s ; elle mesure les treize gammes,
  dix accords, articulations, cordes et pads à au moins 48 dp et vérifie l’accord au down.
- [x] La variante `dev.intervaltablet.performance` est installée en version
  `0.2.3-dev-performance`, compilée ART `speed` et coinstallée avec la seule V1.

Le MIDI USB réel, le vrai multi-touch, TalkBack, le loopback tactile/MIDI→audio, le rendu
soutenu à 90 Hz, le hotplug et le soak audio restent des validations matérielles ouvertes.
