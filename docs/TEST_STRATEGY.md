# Stratégie de test

## Pyramide

Cette pyramide couvre la cible des trois portes. La présence d’une rubrique Tone Row, transport ou audio ne signifie pas que sa porte est déjà acceptée.

### Tests domaine — majorité

Rapides, déterministes, sans Android :

- grilles de gamme, wrap/clamp et ancrage hors gamme ;
- mouvements `-14…+14`, zéro, Home et Undo ;
- accords, doublures et notes hors plage ;
- articulations `ARPEGGIATED`/`STACKED`/`MUTED`, projection du voicing et hits de
  strummer sans mutation de navigation ;
- symétrie des instances actives ;
- routage Off/Active/Active Last Note/PassThru ;
- mapping note/CC et seuil 64 ;
- Same Interval/Same Pitch, Random Interval à graine explicite et Chromatic Shift
  multi-source libéré par Note, CC, purge et Panic ;
- reducer MIDI Learn : baseline/brouillon, capture Note/CC, exact/Omni, seuil, collision,
  recouvrement, add/replace/delete/reset/save/cancel et baseline devenue obsolète ;
- Tone Row, unicité, fin automatique et transformations ;
- Record annulé par un second appui, navigation en Pause, vélocité MIDI transitoire,
  huit modes, Random signé et cycles Auto-Transpose/Auto-Translate ;
- transport et Clock 24 PPQ ;
- sérialisation/migrations via fixtures dans l’app.

### Tests natifs hôte

- oscillateurs et enveloppes ;
- durées ADSR à 44,1/48/96 kHz, normalisation des mix et transparence du limiteur ;
- stabilité filtre/effets ;
- magnitude des all-pass et gain de réverbération normalisé ;
- absence de NaN/Inf ;
- file SPSC ;
- allocation de voix et Panic ;
- contrat `ParameterId` des 16 paramètres et rejet des identifiants/valeurs invalides ;
- lissage avec snap des mix d'oscillateurs, pulse width, sustain, coefficients du filtre,
  effets et master ;
- cycle open/start/stop/reprise, fallback Exclusive→Shared et ownership des callbacks ;
- overflow conservateur, générations de stream, drain borné et reset O(1) des effets ;
- compilation stricte d’`AudioEngine.cpp` et `native_bridge.cpp` contre des stubs hôte étroits.

### Tests Android locaux

- coordinateur pur et contrat de repository MIDI avec faux ports ;
- interception Learn avant rappel/routage, message capturé consommé, commit unique,
  Cancel sans stockage et fermeture sûre au lifecycle/lock/perte de source ;
- parser MIDI fragmenté et messages temps réel imbriqués ;
- sérialisation versionnée du mapping, de la session et de la banque de presets,
  migrations, schémas futurs et entrées invalides ;
- politique Program Change/Song Select, canal, slots absents et PassThru ;
- calcul d’état UI Tone Row et registre multi-pointeur indépendants de l’appareil.
- tracker de strummer : crossings intermédiaires, inversion, vélocité, multi-pointeur et
  hystérésis ;
- acteur musical hors Main, horloges/gates, fermeture concurrente, persistance confluentée
  et diagnostics asynchrones avec dispatchers de test séparés ;
- projections Compose : Note Off, curseurs, console, pads et grille ne changent que leurs
  modèles de présentation concernés.
- migrations Settings/Preset v1/v2 vers articulation explicite et banque v1 vers v2 ;
- coordinateur : pads Tone Row muets en Record/Manual, Auto inchangé et gate strummer
  ciblée sans dette de release.
- contrat Kotlin typé `SynthParameter`/`SynthPatch`, bornes `20…20 000 Hz` du cutoff,
  persistance du patch et ordre wire complet ;
- rejeu des 16 paramètres lors du retour du stream ou de l'augmentation de `restartCount`
  sans état arrêté observé, avec Panic/stop si le rejeu est refusé ;
- projections et contrôles du panneau Synthé, brouillon local, aperçu audio confluent par
  frame, deltas filaires sans persistance et commit complet en fin de geste.
- projection/panneau MIDI Learn : attente, candidat, canal exact/Omni, seuil, conflit,
  recouvrement, brouillon sale et commandes Save/Cancel.

### Tests instrumentés/appareil

- découverte et hotplug ;
- multi-touch et lifecycle ;
- port MIDI réel ;
- audio Oboe, reprise et session prolongée ;
- accessibilité et paysage.
- conservation de la scène pondérée avec une timeline de 1 à 12 éléments ;
- neuf pads sémantiques distincts, cliquables et mesurés à au moins 48 dp ;
- dix variantes d’accords simultanément visibles et Force to Scale accessible sur la scène.
- trois sélecteurs d'articulation et chaque corde du voicing comme cibles accessibles
  distinctes d'au moins 48 dp.
- panneau Synthé scrollable, sliders décrits, ordre aperçu puis commit et diagnostics
  libellé/valeur ;
- AUDIO-01 : dix cycles start/stop, envoi des 16 paramètres et d'un Panic silencieux,
  vidage borné de la file et collecte des diagnostics négociés.
- panneau MIDI Learn adaptatif : cibles accessibles, capture depuis un port réel, aucune
  note/transmission/rappel du message appris et fermeture propre sous Performance Lock.

Ces tests instrumentés sont requis pour la réception matérielle. Ils ne sont pas remplacés par les tests JVM des mêmes invariants.

## État de preuve — tranche logicielle des portes 2 et 3

- `ToneRowReducerTest` couvre les prises complètes 5/7/12, la fin précoce, la recherche
  de classe libre, la lecture manuelle, les parcours historiques, les transformations, la
  séquence éditable et Play Once.
- `TransportReducerTest` couvre timing interne, 24 PPQ, divisions, Start/Continue/Stop,
  Pause, changement de source, timestamps et changement de tempo en vol.
- Les tests du coordinateur couvrent l'ordre des effets et l'ownership commun aux pads,
  mappings, Tone Row et transport, y compris la libération des voix aux transitions.
- `MidiMessageParserTest` couvre le byte stream Clock/transport, Program Change et Song
  Select typé sans casser running status ni les messages fragmentés.
- `PerformancePresetSerializerTest`, `PerformancePresetAdaptersTest`,
  `PresetMidiPolicyTest` et `SettingsRepositoryMigrationTest` couvrent round-trip,
  migration v1→v2, limites de banque, restauration arrêtée et politique de rappel.
- `ToneRowUiModelTest` couvre les curseurs bornés, statuts, disponibilités et slots de
  presets sans instrumentation Android.
- `IntervalTabletViewModelTest` pilote l’acteur avec horloge, stockage, audio et ports
  injectés : restauration arrêtée, autosauvegarde/rappel, callback interne tardif sans
  rafale, Program/Song Select avant routage, isolation PassThru et HostStop idempotent.
- `PadArticulationTest`, `PerformanceCoordinatorTest` et
  `IntervalTabletViewModelTest` couvrent le cycle autonome de toutes les voix, le voicing
  plaqué, le pad muet, les doublures, le revoicing immédiat, les callbacks tardifs, le
  transport arrêté, le strummer one-shot à vélocité pleine et les releases ciblées.
- `StrummerGestureTrackerTest` couvre les bandes sautées dans les deux sens, les sources
  indépendantes, la vélocité secondaire et l'hystérésis de frontière.
- `AudioParametersTest` fixe les 16 identifiants wire, leurs bornes/défauts, l'ordre du
  patch et la sélection ordonnée des seuls paramètres modifiés ; les tests ViewModel
  couvrent les aperçus transitoires, le commit, la persistance et le rejeu après recovery.
- Les tests DSP couvrent la convergence puis le snap exact des paramètres lissés ;
  `SynthPanelAccessibilityTest` couvre les contrôles, l'ordre aperçu/commit, le temps et
  le feedback du delay, ainsi que les diagnostics exposés par Compose.

## État de preuve du lot V2.3

- `PadArticulationTest` prouve le cycle complet, le wrap de voix, les vélocités,
  doublures, revoicing d’accord, ownership, Release et callback tardif no-op.
- `IntervalTabletViewModelTest` utilise une horloge et un dispatcher virtuels : à
  120 BPM/division 6, la seconde voix arrive à 125 ms alors que transport et Tone Row
  restent arrêtés/vides ; Release annule les échéances futures.
- `IntervalPadAccessibilityTest` force 900 × 1 440 dp, trouve les treize gammes, dix
  accords, neuf pads et trois articulations à au moins 48 dp, puis laisse un pointeur
  abaissé pour prouver que le callback d’accord précède `up()`.
- Gate final : 138/138 tests domaine, 162/162 application, 8/8 instrumentés sur
  SM-X620/API 36, CTest 2/2, cinq Lint sans issue et tous les assemblages.
- L’APK Performance est contrôlé sur quatre ABI, installé en `0.2.3-dev-performance`,
  compilé ART `speed`, lancé sans crash et coinstallé uniquement avec la V1.

## État de preuve du lot V2.2

- `IntervalPadAccessibilityTest` force une fenêtre portrait 900 × 1 440 dp, prouve que
  l’harmonie est entièrement à gauche des intervalles, que le panneau droit est plus large
  et que les dix accords, neuf pads, articulations et cordes sont affichés avec des cibles
  d’au moins 48 dp.
- Le scénario paysage vérifie séparément les cibles du strummer. La correction fait passer
  la hauteur fautive observée de 22 dp à au moins 48 dp et laisse MIDI Learn accessible.
- La suite instrumentée complète réussit 8/8 sur SM-X620/API 36. Les gates conservent
  136/136 tests domaine, 161/161 application, CTest 2/2 et ajoutent le cinquième Lint de
  la variante Performance, tous sans issue.
- La variante `performance` est assemblée/minifiée, installée, compilée AOT `speed` et
  vérifiée en présence de la seule V1. Un stress contrôlé de 108 frappes collecte
  `gfxinfo`, puis le panneau Synthé confirme les diagnostics audio négociés.
- La baseline V2.1 cumulative et la fenêtre V2.2 contrôlée ne sont pas assimilées à une
  expérience A/B stricte. Elles suffisent à vérifier la disparition de la régression
  grossière d’entrée, pas à certifier la latence acoustique.

## État de preuve du lot V2.1

- `ForceToScaleTest` couvre le catalogue de treize gammes, la quantification chromatique,
  l’égalité vers le bas, le voicing après Shift et l’ownership exact des releases.
- Les migrations Settings v5, Preset v4 et banque v3 gardent les fixtures historiques et
  installent Force to Scale à `false` pour les formats antérieurs.
- Le test ViewModel vérifie le changement d’état et l’autosauvegarde ; le test Compose
  mesure les pads compacts et trouve les dix accords sur la page principale.
- La réception directe SM-X620 vérifie le package V2.1 coinstallable, son libellé distinct
  et la présence réelle des contrôles ; les tests instrumentés exercent le delay continu.
- Le gate V2.1 réussit 136/136 tests domaine sur 12 suites et 161/161 tests application
  sur 19 suites, soit 297/297, plus CTest 2/2, quatre Lint sans issue et 7/7 tests
  instrumentés directs en 17,279 s.

Le rapport `docs/VERIFICATION_REPORT.md` reste la source du résultat chiffré du dernier
gate complet. Ces tests JVM exercent directement l’acteur et son ordonnanceur coroutine,
mais ne remplacent pas une instrumentation de `HandlerThread`, `StateFlow` et lifecycle
sur Android réel.

## État de preuve du lot V2

La V2 ajoute des oracles dans
`IntervalReducerTest`, `ToneRowReducerTest`, `MidiMappingEditorTest`,
`PerformanceCoordinatorTest`, `IntervalTabletViewModelTest` et les tests de présentation.
La revalidation finale prouve notamment D→E→Same Pitch→F♯, `+3→Same`, les tirages
reproductibles, les shifts Note/CC empilés, Record→Record, Pause→Move→Continue, la
vélocité live non persistée, les huit modes et l'atomicité Save/Cancel, tout en rejouant
les suites des portes 1 à 3.

Le gate V2 réussit 291/291 tests Kotlin/JVM, soit 131 domaine et 160 application, ainsi
que 2/2 suites natives. Les Lint Debug, Release, Benchmark et Instrumented indiquent tous
`No issues found.` et les quatre variantes correspondantes sont assemblées. La suite
directe sur Samsung SM-X620/API 36 réussit 7/7 en 15,603 s ; son septième scénario exerce
le panneau MIDI Learn accessible, le conflit, Replace, Save et Cancel. AUDIO-01 conserve
ses dix cycles start/stop et le gate vérifie toujours que chaque ABI contenant
`libinterval_audio.so` embarque `liboboe.so` et `libc++_shared.so`.

Cette campagne ne valide pas USB MIDI, Clock physique, Program/Song Select depuis un
contrôleur, TalkBack, vrai multi-touch, qualité audio subjective, latence loopback, xruns
sous charge, hotplug audio, rendu soutenu dans le budget 90 Hz ni soak audio de
60 minutes. La porte matérielle globale reste donc ouverte.

## Mesure de rendu reproductible

La variante minifiée `benchmark` utilise un package isolé et `profileable by shell`. Avant
chaque passe, le harnais vérifie le SHA-256 de l'APK installé, l'activité au premier plan et
le taux de rendu actif de 90 Hz. Une passe conserve warm-up, batterie, thermique, dump
`gfxinfo` brut et résumé versionné ; les conditions audio OFF/ON sont contrebalancées.

`gfxinfo` mesure des délais de frame UI. Il ne mesure jamais une latence toucher→audio,
une latence MIDI, ni un round-trip matériel. Si le ring `framestats` est tronqué, seuls les
agrégats plateforme couvrent la fenêtre entière ; les percentiles recalculés sont étiquetés
comme portant sur la queue disponible.

Le protocole MIDI-USB-04 est aligné sur la règle normative : un changement de mode conserve la route décidée au Note On jusqu’au Note Off et ne coupe pas arbitrairement une note tenue.

## Propriétés/invariants

- Aucun Note Off ne vise une instance inconnue sauf Panic global.
- Après Panic, le registre d’instances est vide.
- Chaque lease entrant survit au changement de mode jusqu’à son Note Off.
- Toute note générée appartient à `[min,max]`.
- Une Tone Row enregistrée ne contient pas deux fois la même classe de hauteur.
- Pendulum ne répète pas les extrémités.
- Une graine identique produit la même séquence Random.
- Random Tone Row part du premier élément et ne renverse jamais le signe du pas demandé.
- Same Interval répète le dernier pas diatonique ; Same Pitch répète le dernier delta de
  lead réellement émis.
- Chaque Chromatic Shift est retiré par le release de sa propre source ; Panic et purge
  vident tous les modificateurs sans réinterpréter les notes déjà actives.
- Un déplacement Tone Row en Pause conserve le transport en Pause, et une vélocité MIDI
  de lecture manuelle ne modifie aucune entrée persistable.
- Save MIDI Learn ne peut committer ni capture indécise ni baseline obsolète ; Cancel ne
  modifie jamais le mapping autoritaire.
- Play Once produit exactement la taille de la rangée, émission initiale comprise.
- MIDI Stop libère la voix, conserve les curseurs et met Tone Row en pause ; Continue ne
  rejoue rien avant le tick qualifiant suivant.
- Program Change est filtré par canal, Song Select est global, un slot absent n'est pas
  consommé et PassThru n'effectue aucun rappel.
- La restauration d'un preset ne restaure aucune note active ni deadline et commence
  toujours avec Tone Row `Idle` et transport `Stopped`.
- `MUTED` peut déplacer/enregistrer mais ne crée aucun Note On ni propriétaire vide ;
  Auto Tone Row reste `STACKED`.
- Un strum ne change ni note courante, ni historique, ni curseur, et chaque hit possède
  exactement sa release.
- Les sorties audio restent finies pour toute combinaison de paramètres bornés.
- Le signal reste identique sous le knee du limiteur et le réseau de réverbération ne
  multiplie pas l'énergie à chaque all-pass.

## Matériel et captures de preuve

Suivre `docs/HARDWARE_TEST_PROTOCOL.md`, puis conserver sous `docs/implementation/evidence/` :

- modèle/tablette, version Android et build ;
- périphériques MIDI et topologie USB ;
- commandes de build/test ;
- logs synthétiques sans données sensibles ;
- durée du soak test, xruns, crash et anomalies ;
- vidéos/captures seulement si elles ne contiennent pas de ressources propriétaires.

Les validations de rendu soutenu à 90 Hz, USB MIDI/Learn, vrai multi-touch, TalkBack,
latence loopback et soak audio restent matérielles. Rest, Random Step, Ratchet,
l'émission Clock/transport/SPP, les mappings étendus et les gammes/presets étendus ne font
pas partie des oracles V2 et ne doivent pas être marqués comme implicitement couverts.

## Politique de régression

Un bug de note bloquée, crash audio ou corruption de preset exige :

1. un test reproducteur échouant ;
2. la correction minimale structurée ;
3. le test passant ;
4. une entrée Changelog et, si nécessaire, une nouvelle règle d’acceptation.
