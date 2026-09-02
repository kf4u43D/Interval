# Architecture

## Vue d’ensemble

```text
Compose UI ─────┐
Android MIDI ───┼─> actor ViewModel ─> capture Learn? ─> app coordinator ─> reducers purs ─┬─> MIDI output
Clock/arpège ───┘                         │                                              ├─> Native audio queue
                                          └─> brouillon/commit mapping                    └─> release planifiée

DataStore <──── session/preset bank serialization ────> actor ViewModel

C++/Oboe: event queue -> synth voices -> effects -> stereo stream
```

## Modules

### `domain`

Kotlin/JVM pur. Il contient :

- gammes, grille de notes et note range ;
- machine d’état d’instrument ;
- accords et instances actives ;
- articulation des pads, sessions maintenues par source, arpège déterministe configurable
  et projection trois octaves du voicing pour le strummer ;
- mapping/routage MIDI ;
- reducer transactionnel de l'éditeur MIDI Learn, avec baseline, brouillon, candidat,
  conflits et événement de commit ;
- Tone Row et transport ;
- contrat `SynthPatch` immutable et vingt-huit paramètres audio typés aux identifiants
  filaires explicites `0…27`, avec stabilité de `0…15` ;
- événements de sortie abstraits.

Il ne dépend pas d’Android, de Compose, de JNI, de DataStore ou d’une horloge réelle.

### `app`

Application Android :

- UI Jetpack Compose ;
- `ViewModel`/coordinateur ;
- découverte et ports Android MIDI ;
- parsing/encodage MIDI ;
- persistance DataStore ;
- adaptateur vers le moteur natif.

Le `ViewModel` sérialise les actions musicales sur une mailbox bornée de 256 commandes,
consommée hors du thread UI par un dispatcher mono-thread injectable. Les callbacks publics
déposent des intentions brutes ; vélocité, reducers, horloge, arpèges, gates et one-shots sont résolus
dans cet acteur. Le coordinateur compose ensuite `IntervalReducer`, `ToneRowReducer`,
`TransportReducer` et `MidiRouter` en préservant un ordre total des effets.

Lorsqu'une capture MIDI Learn est armée, l'acteur présente chaque message parsé au
`MidiMappingEditorReducer` avant `PresetMidiPolicy` et `MidiRouter`. Un message Note/CC
consommé par l'éditeur ne peut donc ni rappeler, ni jouer, ni traverser. Armer demande
Panic ; Add/Replace/Delete/Reset ne modifient qu'un brouillon. Le seul événement
`CommitRequested` installe ensuite le mapping par la commande applicative normale et
déclenche sa persistance. Cancel, arrêt d'hôte, Performance Lock, perte de source et
récupération d'overflow ferment la transaction sans sérialiser son état transitoire.

Compose ne calcule aucune harmonie : la projection du strummer appelle `strumNotes()` du
domaine. Les gestes ne déposent que des couples index/vélocité ; l'acteur les transforme
en `StrumTone` one-shot avec une origine unique et une release planifiée après acceptation.

Les jobs d'arpège sont indexés par source et génération. Le callback de gate dépose
`ReleaseArpeggioVoice`, qui éteint la voix sans retirer le geste ; le callback de pas
choisit ensuite ordre, octave et état du motif dans le reducer pur.

Le panneau Synthé conserve un brouillon Compose pendant le déplacement d'un slider. Un
ordonnanceur confluent limite ses aperçus à une position par frame d'affichage ; l'acteur
les sérialise avec les autres commandes et n'envoie au moteur que les paramètres filaires
modifiés depuis le dernier aperçu accepté. Ces positions ne rejoignent ni `AppUiState` ni
DataStore. La fin du geste annule la frame en attente, garantit sa dernière position, puis
applique le patch complet comme état autoritaire et capture ce seul commit sur le worker
de persistance. Le retrait du panneau avec un geste actif finalise aussi le brouillon.
Le panneau est désactivé avant le chargement de Settings et fermé/masqué sous Performance
Lock.

La politique de rappel Program Change/Song Select est appliquée dans l'acteur avant le
routage normal : elle dépend de la banque DataStore et n'est donc pas une règle du domaine
musical pur. Un rappel consommé exécute Panic avant d'installer le snapshot.

### `app/src/main/cpp`

Bibliothèque partagée `interval_audio` :

- façade JNI hors callback ;
- moteur Oboe ;
- file SPSC d’événements ;
- voix et DSP.

Le handle JNI possède le moteur par `shared_ptr`; les callbacks Oboe sont eux aussi possédés
jusqu'à leur retour effectif. Les événements portent la génération du stream, et le callback
en consomme au plus 128 par bloc. Le chemin audio temps réel est entièrement C++20 ; les
règles musicales restent des reducers Kotlin purs, testables et exécutés hors Main.

Le gain staging est interne au moteur natif : mix d'oscillateurs normalisé, ADSR calibrée
en temps-to-target, réverbération avec send/banque de combs normalisés et all-pass
canonique, puis limiteur identitaire sous son knee. Ces choix n'ajoutent aucune règle
musicale au C++.

La frontière JNI reçoit les paramètres compacts `0…15` et leurs valeurs déjà finies et
bornées. Le cutoff canonique Kotlin reste dans `20 Hz…20 kHz`; le DSP le borne encore sous
le Nyquist du sample rate négocié. Chaque démarrage accepté reçoit le patch complet. Après
une reprise native, une transition de diagnostics vers un stream actif ou une hausse de
`restartCount` déclenche le même replay. Le compteur couvre le cas où aucun état
`streamRunning=false` intermédiaire n'a été échantillonné, ainsi qu'un commit effectué
pendant la récupération.

### `native-tests`

Exécutable CMake hôte qui compile les primitives DSP sans Android ni Oboe. Il sert de garde rapide dans VS Code et Codex.

## Flux d’une action

1. Un bouton Compose, une intention Tone Row, une échéance interne ou un message MIDI
   dépose une commande horodatée dans la mailbox bornée.
2. Un message MIDI passe d'abord par une éventuelle capture Learn, puis par la politique
   de preset et le routeur seulement s'il n'a pas été consommé.
3. Le coordinateur appelle le reducer concerné. Une action Tone Row automatique devient
   `InstrumentAction.PressAbsolute` et conserve le voicing historique ; un geste de pad
   en Record/Manual devient `PressPadAbsolute` et respecte l'articulation. Le strummer
   utilise `StrumTone`. Un tick autonome devient `AdvanceArpeggio`. Voicing et ownership
   restent ainsi uniques dans le domaine.
4. Les reducers retournent de nouveaux états et des listes ordonnées d'événements.
5. Les événements sont dispatchés immédiatement vers MIDI Out et/ou audio interne selon la
   configuration ; la persistance et la présentation ne précèdent jamais ce dispatch.
6. L’état devient la source unique de l’UI, via des projections structurelles étroites.
7. Toute erreur d’adaptateur remonte comme état de capacité, jamais comme mutation implicite du domaine.

Une voix automatique Tone Row reçoit une origine système unique et mémorise aussi la
destination qui a accepté son Note On. Avant la note suivante, Pause, Stop, Panic ou
changement de destination, cette origine est libérée sur cette même destination. Le
coordinateur produit une demande `ReleaseAt` ciblée ; l'acteur possède un seul job de gate
actif et une libération tardive d'une ancienne origine ne peut pas couper la voix suivante.

## Concurrence

- UI et coordinateur : mailbox bornée du `ViewModel`, consommée séquentiellement sur
  `Dispatchers.Default.limitedParallelism(1)`, jamais sur Main en production.
- Callbacks Android MIDI : copie minimale et envoi vers une file bornée.
- Horloge interne : un seul job attend la prochaine deadline injectée par le domaine ;
  il renvoie une commande horodatée dans la même mailbox et n'exécute aucune règle musicale.
  Un callback tardif produit un seul tick puis le reducer rebase l'échéance suivante.
- Arpège autonome : un job borné par source attend la durée de pas du transport et remet
  uniquement `AdvanceArpeggio` dans la mailbox. Le domaine décide si la source et le
  voicing existent encore ; Release/Panic/reconfiguration annulent les jobs et rendent
  tout callback tardif idempotent.
- MIDI Clock : le reducer compte les pulses à 24 PPQ et mémorise la dernière période
  positive observée pour calculer le gate ; des timestamps identiques restent comptés
  mais ne remplacent pas cette estimation.
- Audio : thread temps réel Oboe indépendant ; ouverture/fermeture et reprise restent hors
  callback, avec génération et intention de cycle de vie revérifiées avant démarrage. Le
  dernier patch global est rejoué après chaque start accepté et chaque recovery observée.
- JNI : production d’événements vers une SPSC lock-free ; aucune remontée synchrone depuis
  le callback. Overflow ou course de génération déclenche un Panic conservateur.
- Persistance : dispatcher I/O séparé, snapshot versionné de la session et banque bornée de
  128 slots. L'acteur marque seulement les champs durables modifiés ; une capture immutable
  confluentée est matérialisée/écrite après les effets temps réel, sans double sérialisation
  à chaque Note On, Note Off, tick ou curseur transitoire.
- Diagnostics : un seul poll natif peut être en vol sur un dispatcher séparé. Son retour est
  réinjecté par la mailbox ; topologie, génération, erreurs et overflow MIDI restent immédiats,
  tandis que les compteurs de trafic sont échantillonnés à 1 Hz.

## Gestion du cycle de vie

- `onStart` : réconcilier périphériques et préférences.
- `onStop` : Panic, fermeture contrôlée des ports non persistants et arrêt/repli audio selon politique.
- Déconnexion MIDI : marquer le port indisponible, Panic vers toute destination encore accessible, vider leases.
- Changement de destination : Panic ancienne destination, fermer, ouvrir nouvelle, reprendre seulement les futures actions.
- Erreur audio : MIDI continue ; reprise native hors callback avec backoff, rejet des anciens
  callbacks et état réel du stream resynchronisé sans modifier l'intention utilisateur ;
  le retour à l'état actif rejoue le patch courant.

## Données persistées

- version de schéma ;
- clé, gamme, plage, accord, articulation des pads et Force to Scale ;
- mapping MIDI ;
- ports préférés par identité descriptive ;
- activation du moniteur audio et Performance Lock ;
- patch global du moniteur, vingt-huit floats bornés dont cutoff, deux enveloppes, drive,
  LFO, delay synchronisé et tempo ;
- Tone Row, séquence, mode, transformations et graine Random ;
- tempo, signature, durée de gate, division, source d'horloge et configuration d'arpège ;
- banque de 128 presets et slot sélectionné.

Une migration explicite accompagne tout changement de schéma. Le schéma Settings courant
est en version 6 ; les presets sont en version 5 et la banque en version 4. Les lecteurs
historiques installent les nouveaux champs et le patch synthé étendu à leurs défauts exacts ;
Settings v0 à v4 désactive Force
to Scale. Les lecteurs de presets v1/v2
infèrent `ARPEGGIATED` pour l'accord Off et `STACKED` pour un accord actif, puis réécrivent
le format courant. Le patch audio global est volontairement absent des presets : aucun
rappel UI, Program Change ou Song Select ne change le son du moniteur. Les identifiants
Android de session ne sont pas persistés seuls.

Les snapshots excluent intentionnellement les notes actives, les curseurs temporaires,
les compteurs de transport, les deadlines et toute transaction/capture MIDI Learn. Une
restauration conserve la destination physique actuellement ouverte, remet Tone Row à
`Idle` et le transport à `Stopped`; les
identités de ports restaurées servent de préférences de reconnexion.

Le mapping validé reste dans Settings et dans les snapshots musicaux. Modifier le mapping
de la session ne réécrit pas les 128 presets : un slot existant ne change qu'après sa
propre sauvegarde. Le format Mapping v1 reste suffisant pour les clés Note/CC, actions et
seuils actuels.

## Scène deux mains et chemin d’entrée faible latence

`PerformanceScreen` adapte uniquement la composition visuelle. La page Interval emploie
trois panneaux d'environ 37/15/48 % : harmonie, strummer vertical trois octaves et grille
d’intervalles. La barre supérieure contient utilitaires et navigation ; les pages MIDI,
Synthé et Arpégiateur remplacent les anciens panneaux superposés. Les treize gammes et dix
accords sont des cibles directes ; un composant Compose distinct sépare le touch-down de
l'action sémantique sans double déclenchement. Aucune règle musicale n’est dupliquée :
les deux panneaux consomment les mêmes projections et callbacks du ViewModel.

Le ViewModel possède par défaut un `ExecutorCoroutineDispatcher` mono-thread nommé
`IntervalMusicalActor`, fermé dans `onCleared()` et configuré avec
`THREAD_PRIORITY_AUDIO`. Les tests peuvent toujours injecter leur dispatcher. La mailbox
FIFO, les reducers, l’émission MIDI et la mise en file native restent sérialisés sur cet
acteur ; la persistance et les diagnostics gardent leurs workers séparés. Compose publie
les callbacks tactiles sans attendre l’état rendu suivant.

La variante `performance` hérite de Release, active R8 et les bibliothèques natives
optimisées, utilise une signature debug uniquement pour l’installation locale et ajoute
le suffixe de package `.performance`. Elle est l’application de jeu V2.4 ; les variantes
Benchmark et Instrumented conservent leurs rôles de mesure et de test.

## Observabilité

Les pages MIDI et Synthé observent des projections dédiées et exposent notamment :

- périphériques/ports ouverts ;
- compteur messages MIDI et pertes de file ;
- notes actives par route ;
- stream audio, sample rate, frames/burst, xruns ;
- taille maximale de file audio observée ;
- profondeur courante de file, reprises et reprise en attente ;
- dernière erreur non sensible.

Compose n'observe jamais l'état monolithique depuis la coque de scène. Des projections
immuables séparent header, contenu/cursor Tone Row, grille, chacun des neuf pads, surface
harmonique, ruban,
articulation/strummer, console, statut et synthé/diagnostics. Les pads conservent neuf nœuds tactiles/focus/sémantiques
distincts mais dessinent leur contenu via cache afin qu'un tick n'impose pas neuf sous-arbres
Material complets.

Le gate V2 couvre ce découpage, le contrat audio, ses migrations, les aperçus transitoires,
le reducer d'éditeur MIDI Learn et son interception avec 131 tests domaine et 160 tests
application, soit 291/291. Les 2/2 suites natives, les Lint Debug, Release, Benchmark et
Instrumented sans issue, les quatre variantes assemblées et les 7/7 tests directs sur
Samsung SM-X620 complètent la preuve. Cette réception Android ne remplace pas les
protocoles avec périphériques USB MIDI réels, qui restent ouverts.

Aucune télémétrie réseau dans le MVP.

## Extensions différées

Le modèle de séquence reste une liste de mouvements entiers. Rest, Random Step et Ratchet
attendent des actions typées ; Ratchet requiert plusieurs Note On futurs annulables par
génération, ce que le job unique de release ne doit pas simuler. La génération MIDI
Clock/transport et Song Position Pointer, le catalogue étendu d'actions mappables, les
CC relatifs/continus, gammes personnalisées/scopes de presets et la certification soutenue à
90 Hz restent hors de l'architecture V2.4. CV, réseau, Scala, microtonalité, MPE et MIDI 2.0 restent hors
des étapes engagées.
