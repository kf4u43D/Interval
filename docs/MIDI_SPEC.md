# Spécification MIDI

## Priorité et portée

Le chemin MIDI doit rester disponible lorsque le moteur audio est arrêté ou indisponible. Les ports et le routage sont des adaptateurs autour du module `domain`.

La porte 1 couvre l’instrument intervallique, les quatre modes de routage, les ports Android
et la sûreté des notes actives. La porte 2 ajoute l'interprétation musicale de
Clock/transport, Program Change/Song Select et des actions Tone Row sans créer un second
chemin de routage. La V2 ajoute les actions de performance fidèles et un éditeur MIDI
Learn transactionnel sans modifier le format filaire MIDI 1.0.

## Compatibilité MVP

- MIDI 1.0 byte stream.
- Android MIDI API pour découverte/ouverture des périphériques et ports.
- Canal d’entrée : Omni ou 1–16.
- Canal de sortie : 1–16, valeur par défaut 1.
- Vélocité par défaut pour le tactile : 64.
- Note On avec vélocité 0 normalisé en Note Off.
- Messages temps réel acceptés sans perturber un message fragmenté, le running status ou un SysEx en cours.
- SysEx complet borné à 65 536 octets ; un paquet trop long est abandonné jusqu’à EOX, puis le parseur récupère.

## Messages entrants

| Message | Traitement après la porte 2 |
|---|---|
| Note On/Off | mapping de fonction ou pass-through selon le mode, avec lease par instance |
| CC | mapping par seuil configurable, 64 par défaut ; sinon pass-through selon le mode |
| Program Change | rappel de preset existant `0…127` sur le canal d'entrée accepté ; sinon routage normal |
| Song Select `F3` | rappel global de preset existant `0…127` ; sinon routage normal |
| Pitch Bend, Channel/Poly Aftertouch | parsés, encodables et transmis lorsque le mode le permet |
| Clock `F8` | routé puis accumulé à 24 PPQ par le transport lorsque la source MIDI est active |
| Start `FA`, Continue `FB`, Stop `FC` | routés puis appliqués au transport/Tone Row si la source MIDI est sélectionnée, hors PassThru |
| Active Sense `FE` | transmissible, sans effet musical direct |
| System Reset `FF` | Panic conservateur, puis transmission si le mode autorise les messages non mappés |

SysEx n’a aucun effet musical. Un paquet complet est émis comme `MidiMessage.Raw` et n’est transmis qu’en PassThru. Les fragments incomplets ne traversent ni changement de source ni changement de génération de connexion.

## Mapping

Le mapping est une table `source -> MidiAction` :

- source note : numéro 0–127 et canal optionnel ;
- source CC : numéro 0–127, canal optionnel et seuil 1–127 ;
- un binding de canal exact prend priorité sur le binding Omni ;
- le seuil CC vaut 64 lorsqu’il n’est pas surchargé ; une action ne se déclenche que sur le front montant et une action tenue est libérée au passage sous le seuil.

Les actions reliées à l’instrument sont Move `-14…+14`, Chromatic,
UndoThenMove atomique, Undo, Same Interval, Same Pitch, Random Interval, Home, Octave,
Chromatic Shift, Panic et TogglePassThrough. Same Interval répète le dernier déplacement
diatonique ; Same Pitch répète le dernier delta chromatique réellement émis. Random tire
et joue immédiatement un déplacement `-14…+14` avec l'état pseudo-aléatoire déterministe :
il ne sélectionne pas le mode Random de Tone Row. Chromatic Shift est silencieux au front
actif et reste possédé par sa Note ou son gate CC jusqu'au relâchement, à la purge ou au
Panic. En lecture Tone Row manuelle ou en pause, Same Interval répète plutôt le dernier
déplacement d'indice de la rangée ; Same Pitch, Random et Shift conservent leur portée
instrument.

Play, Stop et Record rejoignent le reducer Tone Row. Play termine Record ou bascule Auto
Play/Pause selon l'état, Stop effectue le Stop local, et un second front Record pendant
l'enregistrement annule la prise au lieu d'en commencer une nouvelle. Les sélections de
gamme, clé, accord et preset ne sont pas des actions de mapping dans le schéma courant ;
les presets utilisent la politique Program Change/Song Select ci-dessous.

Une action mappée à un mouvement est une pression de pad du point de vue musical : elle
respecte donc `ARPEGGIATED`, `STACKED` ou `MUTED`, y compris pendant l'enregistrement et la
lecture manuelle Tone Row. Le transport automatique ne dépend pas de cette articulation et
conserve le voicing complet historique.

Le mapping par défaut, isolé dans `DefaultMidiMap.kt`, contient notamment les neuf mouvements directs `-4…+4`, les commandes de sécurité et le toggle de mode. Il est sérialisé avec version de schéma, validé à la lecture et restaurable par `Reset mapping` sans réutiliser une instance mutable.

### Éditeur MIDI Learn V2

L'éditeur travaille sur une baseline et un brouillon complets du mapping. Son état de
capture est transitoire et ne fait pas partie du schéma persistant.

1. Ouvrir l'éditeur ne modifie pas le mapping actif. Armer une action demande Panic avant
   d'accepter un nouvel input.
2. Pendant l'armement, le premier Note On ou CC devient candidat. Cette interception a
   lieu avant Program Change/Song Select et avant le routeur ; le message consommé ne joue
   pas, ne traverse pas et ne déclenche aucune autre fonction. Les Note Off vus en attente
   sont aussi absorbés, et le trafic Note/CC reste protégé lorsqu'un candidat attend une
   décision.
3. Le canal exact reçu est le défaut ; Omni est un choix explicite. Pour un CC, le seuil
   par défaut vaut 64 et reste réglable dans `1…127`.
4. Une clé identique — type, numéro et canal — constitue un conflit destructif et exige
   un remplacement explicite. Exact et Omni peuvent coexister : l'éditeur signale le
   recouvrement, puis la priorité exacte existante s'applique à l'exécution.
5. Ajouter/remplacer, supprimer ou restaurer le mapping par défaut ne change que le
   brouillon. Save est atomique et refusé si une capture reste indécise ou si la baseline
   est devenue obsolète. Cancel abandonne le brouillon sans écriture.

Le format Mapping v1 est conservé : les clés Note/CC, actions et seuils existants expriment
déjà le résultat. Un Save met à jour la session courante. Les presets sauvegardés
auparavant gardent leur mapping jusqu'à une nouvelle sauvegarde explicite du slot.

## Routage et propriété des notes actives

Deux registres complémentaires évitent de dupliquer ou de perdre la vérité sur les notes :

1. Le routeur MIDI possède les `RoutingLease`. Un lease mémorise la route `MAPPED`, `FORWARDED` ou `DROPPED`, la source physique `device/port/channel/note`, le mode au Note On, la destination, le canal de sortie et le compteur d’instances. Il ne stocke pas le voicing généré.
2. Le reducer de l’instrument possède `activeBySource`. Pour une source mappée, il mémorise exactement chaque `ActiveNoteInstance` produite par l’accord — note, vélocité et canal — y compris les doublures.

Au Note Off d’une source mappée, le routeur consulte le lease d’origine et émet un `InstrumentAction.Release` vers sa destination initiale. Le reducer consulte alors `activeBySource[source]` et produit un Note Off pour chaque instance réellement générée. Pour une note transmise, le lease contient directement les informations nécessaires au Note Off brut et son compteur. Une note supprimée ne produit rien.

Les CC tenus utilisent un registre `CcGateLease` distinct contenant source logique, action, mode de pression, destination, canal et seuil. Les purges de source/destination libèrent d’abord toutes les actions tenues, puis émettent les contrôleurs de sécurité par couple destination/canal.

Cette séparation garantit qu’un changement de mode après le Note On ne réinterprète jamais son Note Off et que les accords restent symétriques sans imposer au routeur de connaître la logique musicale.

Force to Scale appartient exclusivement au reducer de l’instrument. Il quantifie les
notes générées par ses actions, mais un message Note On/Off transmis en `PassThru` conserve
strictement sa hauteur et sa route d’origine.

## Modes

- Off : les messages mappés déclenchent leurs fonctions ; les autres sont supprimés.
- Active : les messages mappés déclenchent leurs fonctions ; les autres sont transmis.
- Active Last Note : comportement Active, plus ancrage de navigation par le dernier Note On non mappé accepté.
- PassThru : messages transmis et Note On utilisé comme ancre, sans appliquer les mappings musicaux ordinaires.

Exception de sécurité normative : Panic et TogglePassThrough restent consommables en PassThru afin de conserver un chemin de silence et de sortie du mode. Le message qui déclenche l’une de ces deux commandes n’est pas retransmis. Tous les autres messages suivent la règle PassThru inchangée.

Lorsque la source d'horloge MIDI est sélectionnée, Clock, Start, Continue et Stop sont
interprétés localement après l'effet de routage dans Off, Active et Active Last Note. Ils
ne pilotent jamais le transport en PassThru ni lorsque l'horloge interne est propriétaire.
Dans les modes qui transmettent, l'octet temps réel sortant précède donc les notes
éventuellement générées par ce même message.

### Horloge MIDI et articulation

- Le transport compte les `F8` à 24 PPQ. La division sélectionnée (`1…96` pulses par
  pas, 6 par défaut) détermine le prochain mouvement Tone Row.
- La durée d'un pas et de son gate est calculée depuis la dernière période strictement
  positive observée entre deux `F8`. Tant qu'aucune période n'a pu être mesurée, le tempo
  local configuré sert de repli. Des timestamps identiques ou décroissants restent
  comptés comme pulses, sans remplacer l'estimation ni rendre les ticks non monotones.
- `FA` remet les compteurs et la série au début, puis joue la première note. Pendant un
  enregistrement, il termine d'abord la prise ; une prise vide ne démarre pas le transport.
- `FC` libère la voix automatique et place la série en pause en conservant sa position.
  `FB` reprend cette position sans rejouer de note avant le prochain `F8` qualifiant.
- L'horloge interne n'émet au plus qu'un tick par callback, même tardif, puis rebase
  l'échéance suivante afin de ne jamais produire une rafale de rattrapage.

La sortie MIDI Clock/Start/Stop/Continue et Song Position Pointer n'est pas implémentée
dans ce lot ; « Clock MIDI » désigne ici uniquement une source entrante.

## Program Change, Song Select et presets

- La banque comporte 128 slots internes, indexés `0…127`. Program Change `n` et Song
  Select `n` ciblent tous deux le slot `n`; l'UI affiche ces slots `1…128`.
- Program Change respecte le filtre de canal d'entrée. Un canal précis n'accepte que ce
  canal ; la valeur Omni accepte les seize canaux.
- Song Select est un message System Common global et ignore donc le filtre de canal.
- Seul un slot existant est consommé. Si le slot est absent, le message repasse dans le
  routeur ordinaire : supprimé en Off, transmis en Active/Active Last Note/PassThru.
- En PassThru, le rappel est désactivé même si le slot existe ; le message est transmis
  inchangé.
- Un rappel consommé déclenche d'abord Panic, puis installe le snapshot sans reprendre
  les notes, les curseurs transitoires ni la lecture. La destination physique actuellement
  ouverte reste la destination active ; les identités de ports du preset deviennent des
  préférences de reconnexion, pas un hot-switch implicite.
- Le patch du moniteur audio est global dans Settings v4 et absent des snapshots Preset v3
  et de la banque v2. Program Change, Song Select et le Panic préalable au rappel ne le
  modifient donc jamais ; le chemin MIDI reste identique lorsque le moniteur est arrêté,
  indisponible ou en récupération.

## Sortie et ordre

Les effets du routeur forment une liste globale ordonnée et ciblée par destination. Pour une action jouable :

1. Note Off des anciennes instances de la même source ;
2. messages de contrôle requis ;
3. Note On lead, puis harmonies dans l’ordre du voicing.

En `ARPEGGIATED`, l'étape 3 ne contient que le lead ; en `MUTED`, elle ne contient aucun
Note On mais les mutations musicales de la pression sont conservées. En `STACKED`, elle
contient le voicing complet. Un hit de strummer suit le même ciblage de destination et le
même registre d'instances, mais ne contient qu'une corde à vélocité pleine et ne modifie
pas la position musicale. Les doublures sont des instances distinctes et reçoivent leurs
Note Off respectifs.

Pour Panic et purge : Note Off explicites, puis CC 123 (All Notes Off) et CC 120 (All Sound Off). Un changement de destination purge l’ancienne route avant d’ouvrir/utiliser la nouvelle ; dès que la nouvelle session est réellement `OPEN`, ses 16 canaux reçoivent chacun CC 123 puis CC 120 avant le premier jeu, afin d’effacer d’éventuelles voix transmises sur un canal qui n’est plus connu après la déconnexion. Les timestamps sortants doivent être non décroissants.

## Ports Android

L’adaptateur doit et, en porte 1, sait :

- exposer une liste dédupliquée de périphériques/ports avec identité persistable descriptive et identité de session ;
- ouvrir/fermer sur un thread I/O dédié, sans traiter de règle musicale dans les callbacks Android ;
- représenter les phases fermée, ouverture, ouverte et erreur avec une génération de connexion ;
- tolérer perte et retour de catalogue, invalider immédiatement une source fermée et réinitialiser son parseur ;
- copier les octets reçus vers une file bornée et déclencher une récupération garantie en cas de débordement ;
- cibler chaque envoi avec destination, session et génération afin de rejeter les événements périmés ;
- vider en ordre FIFO les envois déjà acceptés avant la fermeture effective ;
- ne jamais conserver de référence Android de port après fermeture.

Les contrats de catalogue/génération, les envois ciblés, le reset du parseur et l’ordre du
coordinateur sont couverts par les tests locaux. La mailbox destination pure est bornée à
512 opérations et testée pour l’ordre Send/Select, le latest-request-wins, le reset
multicanal après saturation et le drain de fermeture. L'acteur du `ViewModel` et
l'ordonnanceur interne sont pilotés sur JVM avec horloge, stockage, audio et ports injectés.

Le gate V2 réussit 131 tests domaine et 160 tests application, soit 291/291, plus 2/2
suites natives. Les Lint Debug, Release, Benchmark et Instrumented indiquent
`No issues found.`, les quatre variantes sont assemblées et la suite directe sur Samsung
SM-X620 réussit 7/7. Elle reçoit notamment l'éditeur Learn Compose et ses transactions
conflit/Replace/Save/Cancel. Ces preuves synthétiques et instrumentées ne valident pas la
découverte, le trafic, le hotplug, l'horloge ou les notes tenues avec des périphériques
USB MIDI physiques ; ces protocoles restent ouverts.

## Tests matériels minimaux — non exécutés pendant la porte 2

- Clavier USB class-compliant en entrée.
- Interface/synthé USB MIDI class-compliant en sortie.
- Un périphérique possédant plusieurs ports virtuels.
- Déconnexion pendant notes tenues.
- Changement Active/PassThru pendant notes tenues.
- Rafale de Clock MIDI, Start/Continue/Stop et rappel de presets Program/Song Select.
- Capture MIDI Learn Note/CC exact/Omni, seuil et conflit, avec contrôle qu'aucun message
  appris ne joue ni ne traverse.

Rest, Random Step et Ratchet, les mappings de gamme/clé/accord/preset, les contrôleurs CC
relatifs ou continus, les profils et l'import/export sont différés. Ratchet exige un
scheduler de retriggers annulable qui n'est pas confondu avec la gate unique actuelle.
