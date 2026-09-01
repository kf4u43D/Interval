# Spécification comportementale

Cette spécification décrit le comportement cible observable. Elle prime sur les choix d’interface et sert d’oracle aux tests du module `domain`.

## 1. Modèle de hauteur

Le MVP utilise exclusivement MIDI 1.0 en tempérament égal 12-TET.

- Une gamme est une liste ordonnée de classes de hauteur distinctes dans l’octave.
- La clé fixe la classe de hauteur de la fondamentale.
- La note courante est un numéro MIDI entier entre `range.min` et `range.max`.
- Le déplacement `+n` avance de `n` degrés dans la gamme ; `-n` recule de `n` degrés.
- Le déplacement `0` rejoue la hauteur courante sans créer d’entrée d’historique distincte.
- Avec `solfegeWrap=true`, dépasser la plage reboucle sur la première/dernière note valide de la plage. Avec `false`, le déplacement est borné.
- `Home` choisit la fondamentale la plus proche du centre de la plage.
- Une note d’ancrage externe hors gamme est admise ; le premier déplacement positif choisit la prochaine note de gamme dans le sens montant, et le premier déplacement négatif la précédente.
- La bibliothèque standard contient Major/Ionian, Natural Minor/Aeolian, Harmonic Minor,
  Melodic Minor ascendant, Dorian, Phrygian, Lydian, Mixolydian, Locrian, les pentatoniques
  majeure/mineure, Blues et Chromatic.

## 2. Actions de performance

### Pression d’un intervalle

Au `press` :

1. Libérer les notes encore associées à la même source physique, le cas échéant.
2. Calculer la nouvelle hauteur.
3. Si Force to Scale est actif, quantifier cette hauteur vers la note la plus proche de la
   gamme et de la plage actives ; une égalité choisit la note inférieure.
4. Ajouter l’ancienne hauteur à l’historique uniquement si la nouvelle est différente.
5. Construire le voicing complet de l’accord courant.
6. Appliquer l'articulation de pad : première voix en `ARPEGGIATED`, voicing complet en
   `STACKED`, aucune note en `MUTED`. Le contexte de hauteur/vélocité/articulation du pad
   reste possédé tant que la source physique est maintenue.
7. Émettre les Note On retenus et mémoriser exactement les notes/instances actives pour
   cette source. Une pression muette ne crée pas de propriétaire de note vide, mais son
   contexte de pad reste libérable.

Au `release`, émettre un Note Off pour chaque instance créée par cette source. La position musicale ne revient pas en arrière.

Des sources différentes peuvent rester appuyées simultanément et produire une polyphonie indépendante.
En `ARPEGGIATED`, chaque échéance libère la voix précédente avant d'émettre la suivante,
dans l'ordre du voicing. L'échéance utilise le tempo et la division configurés, mais ne
requiert ni transport démarré, ni Tone Row, ni séquence non vide.

Une articulation ne change ni le calcul de hauteur, ni l'historique, ni l'ancre. Son
changement ne coupe pas les notes déjà tenues : chaque source garde les instances émises
au moment de sa pression jusqu'à son `release`.

### Same Interval, Same Pitch et Random Interval

- `Same Interval` répète le dernier déplacement diatonique `-14…+14`. Après un mouvement
  `+3`, il applique donc de nouveau `+3` depuis la position courante. Avant tout mouvement,
  sa valeur neutre `0` rejoue la position.
- `Same Pitch` répète le dernier écart chromatique entre deux leads réellement émis. Dans
  l'oracle Ré majeur D→E, il mémorise `+2` demi-tons et produit ensuite F♯ depuis E, même
  si le prochain degré diatonique aurait une autre distance. Un geste muet ne remplace pas
  cet historique sonore.
- `Random Interval` choisit immédiatement un déplacement diatonique dans `-14…+14` avec
  le générateur et la graine de l'état, puis suit exactement le chemin d'une pression de
  pad. Il ne change jamais le parcours Random de Tone Row. Le mouvement tiré devient le
  dernier intervalle répétable par `Same Interval`.

Ces trois actions appliquent articulation, voicing, historique et ownership comme une
pression normale. Une même graine et une même suite d'actions produisent les mêmes tirages.

### Chromatic Shift momentané

- La pression installe silencieusement un décalage `-12…+12` demi-tons possédé par sa
  source. Elle ne déplace ni curseur, ni historique et n'émet aucune note.
- Les décalages de plusieurs sources tenues s'additionnent de façon bornée. Ils affectent
  uniquement les notes et strums démarrés pendant leur maintien ; une note déjà active
  conserve sa hauteur et sa future release exacte.
- Le relâchement de la Note, le passage d'un CC sous son seuil, une purge ou Panic retire
  le décalage de la source concernée. Les autres modificateurs tenus restent actifs.

### Force to Scale

- Le réglage est désactivé par défaut et agit sur les prochaines notes générées par les
  pressions chromatiques, Tone Row absolu, Same Pitch, accords, strummer et Chromatic Shift.
- Chaque hauteur résultante est ramenée à la note disponible la plus proche de la gamme
  active. À distance égale, la hauteur inférieure gagne de manière déterministe.
- Le MIDI entrant transmis en `PassThru` reste inchangé : Force to Scale n’est pas un
  quantificateur du flux brut et ne duplique aucune règle dans le routeur.
- Basculer le réglage ne coupe pas les notes déjà tenues. Leur future Note Off conserve la
  hauteur exacte mémorisée par leur source.

### Undo

- Revient à la précédente hauteur **différente**.
- Ignore donc les mouvements `0` et les répétitions de même hauteur.
- Déclenché comme une action jouable, il produit un nouveau Note On/Off selon la source.
- En mode Tone Row lecture, `Undo` est remplacé par `Restart`.

### Panic

`Panic` :

- émet un Note Off pour toutes les instances connues sur le canal de sortie ;
- envoie CC 123 (All Notes Off) et CC 120 (All Sound Off) sur les canaux concernés ;
- vide les registres de notes actives, de pads maintenus et de Chromatic Shift, sans
  modifier la gamme, la clé ou le contenu de Tone Row.

## 3. Accords

Un accord comporte jusqu’à trois mouvements relatifs au lead. Les valeurs sont des degrés de gamme, sauf les mentions explicites d’octave.

| Nom | Structure |
|---|---|
| Off | `{0}` |
| Octaves | `{0, -1 octave, -2 octaves}` |
| Third | `{0, -2, 0}` |
| Sixth | `{0, -5, 0}` |
| Triad | `{0, -2, -4}` |
| Triad 2 | `{0, -3, -5}` |
| Triad 3 | `{0, -2, -5}` |
| Jazz | `{0, -3, -9}` |
| Copland | `{0, -6, -12}` |
| Wide | `{0, -11, -22}` |

Le lead utilise la vélocité demandée. Les voix non-lead utilisent 50 % de cette vélocité, bornée à au moins 1. Une note située hors de la plage est omise. Les doublures exactes restent des instances distinctes afin de conserver la symétrie Note On/Off.

### Articulation des pads

- `ARPEGGIATED` est le mode initial. Une pression joue immédiatement le lead ; tant que
  le pad reste maintenu, les échéances suivantes parcourent cycliquement toutes les voix
  disponibles. Chaque source possède son curseur et sa note active. Avec `Off` ou un
  voicing réduit à une note par la plage, aucun tick inutile n'est planifié.
- `STACKED` joue simultanément toutes les instances du voicing dans leur ordre défini,
  avec la pondération lead/harmonies ci-dessus.
- `MUTED` conserve navigation, historique, ancre, feedback de pression et enregistrement
  Tone Row, mais n'émet aucun Note On depuis les pads.

Le voicing complet reste calculable dans les trois modes pour alimenter le strummer.
Changer de type d'accord revoicera immédiatement chaque pad maintenu : Note Off des
instances précédentes puis Note On du nouveau voicing au même timestamp. L'arpège repart
de la première voix ; un accord déjà sélectionné est un no-op exact.

### Strummer

- Les cordes correspondent, dans l'ordre, aux notes du voicing courant après omission
  des notes hors plage ; une doublure reste une corde distincte.
- Chaque corde déclenche une seule note à la vélocité demandée, sans atténuation des
  harmonies. Chaque hit possède une origine one-shot propre et une libération symétrique.
- Un balayage peut traverser plusieurs cordes et s'inverser ; toutes les cordes
  intermédiaires sont émises dans l'ordre du trajet. Une hystérésis limite les
  redéclenchements dus au tremblement sur une frontière.
- Le strummer ne modifie jamais la note courante, l'historique, l'ancre, les curseurs
  Tone Row ou le transport. Un index de corde invalide est un no-op exact.

## 4. Tone Row — enregistrement

État initial : `Idle`.

- `Record` depuis `Idle` vide la série et passe à `Recording`.
- `Record` reçu une seconde fois pendant `Recording` abandonne toute la prise en cours,
  vide la série créée par cette prise et revient à `Idle`.
- Chaque mouvement enregistre la hauteur résultante et sa vélocité.
- Un mouvement provenant d'un pad applique aussi son articulation sonore. En `MUTED`, la
  hauteur est donc enregistrée et devient courante sans Note On.
- Une classe de hauteur déjà présente ne peut pas être enregistrée une seconde fois. Lorsque la cible est déjà utilisée, le moteur continue à parcourir les degrés dans le sens demandé jusqu’à la prochaine classe disponible.
- Le nombre maximal d’éléments est le nombre de classes de hauteur distinctes de la
  gamme active réellement accessibles dans la plage au début de l’enregistrement.
- Lorsque toutes les classes sont utilisées, l’enregistrement se termine automatiquement et passe à `ManualPlayback`.
- `Play` termine l’enregistrement plus tôt et passe à `ManualPlayback` si au moins une note existe.
- `Stop` annule un enregistrement vide ou conserve une série non vide et revient à `Idle`.
- Un changement de gamme, clé, plage ou politique wrap termine d'abord l'enregistrement,
  puis applique la nouvelle grille. Une même prise ne mélange donc jamais deux référentiels.

L’enregistrement conserve une représentation en degrés relative à la gamme et la clé de référence, ainsi que la vélocité. Cela permet de préserver le contour lors d’un changement de gamme ultérieur.

## 5. Tone Row — lecture manuelle

- Une fin d'enregistrement précoce ou complète place le curseur sur le premier élément
  et passe à `ManualPlayback`. Elle ne rejoue pas automatiquement ce premier élément.
- Les neuf boutons ne déplacent plus dans la gamme, mais dans les indices de la série.
- `+1` parcourt donc l’ordre enregistré ; les autres valeurs sautent dans la série avec rebouclage.
- `0` rejoue l’élément courant.
- `Restart` revient au premier élément et le joue immédiatement.
- `Undo` est interprété comme `Restart` en lecture manuelle, automatique ou en pause.
- `Play/Pause` depuis `Idle` ou `ManualPlayback` lance Auto Play au début de la série.
- Les Notes d’un contrôleur MIDI mappées à Move utilisent les mêmes règles ; Same Interval
  répète ici le dernier déplacement d'indice de la rangée. Same Pitch, Random Interval et
  Chromatic Shift gardent leur sémantique instrument et ne sélectionnent aucun parcours
  Tone Row implicite.
- En `Paused`, les mêmes déplacements manuels restent actifs sans redémarrer le transport.
  La nouvelle position devient celle depuis laquelle Continue reprendra.
- Lorsqu'une Note MIDI mappée provoque une émission manuelle ou en pause, sa vélocité
  remplace celle de l'élément pour cette émission seulement. Le contenu enregistré et sa
  vélocité persistée ne changent pas ; le tactile continue d'utiliser la vélocité de la
  rangée.
- Les gestes de pads et mappings assimilés à une pression de pad utilisent l'articulation
  courante en lecture manuelle. La lecture automatique reste polyphonique comme avant et
  joue toujours le voicing complet, indépendamment de l'articulation des pads.

## 6. Tone Row — Auto Play

- La séquence de mouvements initiale est `{+1}`.
- La séquence contient de 1 à 64 mouvements, chacun borné à `-14…+14`, et possède
  un curseur indépendant du curseur de Tone Row.
- Le démarrage joue immédiatement l'élément de départ. À chaque tick musical suivant,
  le moteur applique le mouvement courant, joue le résultat, puis avance dans la séquence.
- Les actions `Add Step` et `Delete Step` éditent cette séquence sans interrompre le transport.
- Supprimer son dernier mouvement rétablit `{+1}`. Le curseur de séquence reste toujours valide.
- `Play Once` compte l'émission initiale et s'arrête après exactement autant de notes
  qu'il existe d'éléments, puis revient à `ManualPlayback`.
- `Play/Pause`, `Stop`, `Restart`, `Play Once` et `Record` suivent une machine d’état explicite.

### Huit modes de lecture

- `Prime` : mouvements dans leur sens normal.
- `Retro` : signe de parcours inversé.
- `Random` : départ au premier élément, puis variation pseudo-aléatoire de chaque
  mouvement demandé. Un pas positif tire dans `0…2×|pas|`, un pas négatif dans
  `-2×|pas|…0` et zéro reste zéro. La graine est injectable et reproductible.
- `Pendulum` : aller-retour sans répéter deux fois les extrémités.
- `Auto-Transpose Up/Down` : parcours Prime ; après chaque cycle logique de
  `row.size` émissions, ajoute ou retire un demi-ton à la transposition de sortie.
- `Auto-Translate Up/Down` : parcours Prime ; après chaque cycle logique de
  `row.size` émissions, ajoute ou retire un degré à la translation du contour.

Pause/Continue conserve la phase de cycle et l'accumulation automatique. Restart repart
du premier élément et remet cette accumulation à zéro ; Reset revient aussi au mode
`Prime` et aux transformations neutres. Changer de mode redémarre le compteur de cycle.

### Transformations indépendantes

- `Transposition` : décalage chromatique fixe en demi-tons appliqué après la projection
  diatonique, sans modifier la série enregistrée.
- `Translation` : décalage fixe en degrés appliqué au contour avant sa projection dans
  la gamme active, sans modifier la définition enregistrée.
- `Invert` : inverse le signe des mouvements relatifs autour du point de départ.
- `Octave` : offset d’octave de sortie, borné par la plage de notes.

L'ordre de transformation est : inversion autour du premier degré, translation en degrés,
projection sur la grille active, transposition chromatique, octave, puis clamp dans la plage.
Le reset restaure `Prime`, inversion normale, transposition 0, séquence `{+1}`,
translation 0 et octave 0, sans effacer la série.

## 7. Horloge et transport

- Horloge MIDI entrante : 24 pulses par noire.
- La division est exprimée en pulses MIDI par pas (`1…96`, 6 par défaut). Le tempo
  interne est borné à 20…300 BPM et la durée de gate à 1…100 % du pas.
- Sous MIDI Clock, la durée du pas et du gate suit la dernière période positive observée
  entre deux pulses. Avant cette première mesure, le tempo configuré sert de repli borné.
- `Start` MIDI : remet les compteurs et la série au début, démarre et joue la première
  note. S’il arrive pendant `Recording`, il termine d’abord la prise ; une prise vide
  laisse le transport arrêté. Le premier mouvement cadencé arrive après une division
  complète.
- `Continue` MIDI : reprend les compteurs et curseurs conservés. Après un Stop reçu
  pendant Auto Play, aucune note n'est rejouée avant le prochain tick qualifiant.
- `Stop` MIDI : libère la voix automatique, conserve la position de transport et place
  Tone Row en `Paused`; un `Continue` peut donc reprendre cette position.
- Le bouton `Stop` local est distinct : il revient à `Idle`, arrête le transport et
  conserve le contenu de la série.
- `Pause` libère la voix automatique courante et conserve la position.
- L’horloge interne et l’horloge MIDI sont mutuellement exclusives comme source active.
- Un changement de source arrête d'abord le transport courant. Le scheduler interne
  ignore un timestamp déjà traité et n'émet au plus qu'un tick par callback tardif.
- Les timestamps de ticks restent strictement croissants, y compris lorsque plusieurs
  pulses MIDI portent le même timestamp d'adaptateur.
- Les changements de tempo ne réordonnent pas l'échéance déjà planifiée ; la nouvelle
  durée s'applique à la suivante.

## 8. Pass-through

- `Off` : seuls les messages mappés déclenchent des fonctions ; les autres ne sont pas transmis.
- `Active` : les messages mappés déclenchent des fonctions, les non-mappés sont transmis inchangés.
- `Active Last Note` : comme `Active`, et le dernier Note On non mappé devient l’ancre de la prochaine navigation intervallique.
- `PassThru` : les messages sont transmis inchangés et les Note On reçus actualisent l’ancre de dernière note, à l’exception des bindings de sécurité `Panic` et `TogglePassThrough`. Ces deux commandes restent consommées, sans retransmission du message déclencheur, afin qu’il existe toujours un chemin déterministe vers le silence et vers la sortie du mode.

Clock, Start, Continue, Stop, Program Change et Song Select n'ont aucun effet local en
`PassThru`. Ils suivent alors uniquement la transmission inchangée ; Panic et
TogglePassThrough restent les deux seules exceptions consommées.

Une transition de mode ne coupe pas arbitrairement les notes déjà tenues. Chaque note conserve la route décidée à son Note On jusqu’à son Note Off. Un changement de port de sortie, une fermeture d’activité ou une déconnexion déclenche d’abord `Panic` sur l’ancien port lorsque cela reste possible.

## 9. Presets

- La banque interne contient 128 slots indexés `0…127` ; l'UI les affiche `1…128`.
- Program Change rappelle le slot correspondant uniquement s'il respecte le canal
  d'entrée configuré ; en Omni, tous les canaux sont acceptés.
- Song Select est global, sans filtrage de canal, et rappelle le slot correspondant.
- Seul un slot existant est consommé. Un slot absent poursuit le routage MIDI normal.
- Aucun rappel Program Change ou Song Select n'est effectué en PassThru.
- Avant d'installer un preset, le coordinateur exécute Panic. Le contenu, les
  transformations, le mapping, le contexte, le routage et les options de transport sont
  restaurés, mais les notes actives, curseurs transitoires, compteurs, deadlines et état
  de lecture ne le sont pas : Tone Row revient à `Idle` et le transport à `Stopped`.
- La session de travail, la banque et le slot sélectionné sont persistés dans DataStore,
  articulation et Force to Scale compris. Les schémas courants sont Settings v5,
  Preset v4 et banque v3.
  La migration d'un preset ancien infère `ARPEGGIATED` lorsque
  l'accord est Off et `STACKED` lorsqu'un accord est actif afin de préserver son rendu
  historique. Le schéma courant migre aussi le format plat de l'étape 1. Les imports/exports de fichiers
  restent hors périmètre.
- Le patch du synthétiseur interne est un réglage global de Settings v5. Il n'entre pas
  dans les snapshots Preset v4 ou la banque v3 ; sauvegarde, rappel UI, Program Change et
  Song Select conservent donc le patch courant. Le Panic préalable au rappel éteint les
  voix et les effets sans réinitialiser ses paramètres.
- L’autosauvegarde compare le snapshot durable après les commandes tactiles et MIDI :
  une nouvelle entrée enregistrée est persistée immédiatement, tandis que les simples
  Note On/Off, ticks, curseurs live et deadlines ne provoquent pas d’écriture.

## 10. Éditeur MIDI Learn

L'éditeur est une transaction éphémère autour du mapping courant.

- `Open` capture une baseline immutable et crée un brouillon identique. Ce brouillon ne
  rejoint ni la session, ni DataStore, ni les presets pendant l'édition.
- `Arm` choisit d'abord l'action cible et demande Panic afin de partir sans lease tenue.
  La première Note On ou le premier CC appris est capturé avant politique de rappel et
  routeur : il n'est ni joué, ni transmis, ni interprété comme une autre commande. Le
  trafic Note/CC reste consommé tant que le candidat n'est pas accepté ou annulé.
- Le candidat prend par défaut le canal reçu. Il peut devenir Omni ; un CC reçoit le seuil
  64, modifiable dans `1…127`.
- Une collision sur la même clé Note/CC et le même canal exige `Replace`. La coexistence
  d'un binding exact et d'un binding Omni est autorisée et signalée comme recouvrement ;
  à l'exécution, l'exact reste prioritaire.
- Add/Replace, suppression et Reset mapping ne modifient que le brouillon. Save est refusé
  tant qu'une capture n'est pas résolue ou si le mapping courant ne correspond plus à la
  baseline ; sinon il remplace le mapping une seule fois et ferme l'éditeur. Cancel ferme
  et jette tout le brouillon sans persistance.
- Performance Lock, arrêt d'hôte, perte de source ou récupération après débordement
  ferment prudemment la transaction et ses états de capture.

Le format persistant de mapping reste en version 1 parce que le catalogue d'actions et
les clés Note/CC existants suffisent. Le mapping validé devient celui de la session
courante. Un preset déjà sauvegardé conserve son ancien mapping tant que l'utilisateur ne
sauvegarde pas de nouveau ce slot.

## 11. Moniteur audio et patch synthé

- `SynthPatch` porte seize valeurs finies et bornées, associées à des identifiants filaires
  stables `0…15` : mix saw, pulse et triangle, pulse width, ADSR, cutoff, résonance,
  chorus mix, temps/feedback/mix du delay, reverb mix et master.
- Le cutoff canonique de la session est borné à `20 Hz…20 kHz`. Le moteur natif applique
  en plus le plafond sûr du sample rate réellement négocié.
- Les sessions Settings v0 à v3 migrent vers le patch par défaut exact. Settings v4/v5
  persiste les seize champs globalement et borne chaque valeur lue avant la frontière JNI.
- L'acteur rejoue les seize paramètres dans l'ordre après chaque démarrage audio accepté.
  Il rejoue aussi le patch courant après toute récupération native, qu'elle soit observée
  par une transition d'arrêté à actif ou seulement par l'incrément du compteur de
  redémarrages, afin d'inclure les changements effectués pendant la reprise.
- Le panneau Synthé est non modal. Il expose Timbre, cutoff/résonance, ADSR, chorus,
  temps/feedback/mix du delay, reverb, master et une projection de diagnostics audio dédiée. Il est
  désactivé tant que les réglages ne sont pas chargés, puis fermé et masqué sous
  Performance Lock ; le toggle Audio Monitor reste disponible.
- Pendant un geste de slider, Compose conserve le brouillon local et publie au plus un
  aperçu par frame d'affichage. L'acteur n'envoie au moteur que les paramètres dont la
  valeur filaire a changé ; cet aperçu reste transitoire et ne déclenche ni publication
  d'état durable ni persistance. Au relâchement, la dernière position est garantie puis le
  patch complet devient autoritaire et est persisté. Une fermeture du panneau pendant un
  geste finalise également ce brouillon.

## 12. Déterminisme

Les tests fournissent explicitement :

- horloge monotone simulée ;
- graine aléatoire ;
- ordre des événements ;
- configuration de gamme, clé, plage et canal.

Aucune règle du domaine ne lit directement l’heure système, Android, un périphérique ou un générateur aléatoire global.

## 13. Surface deux mains et chemin de jeu prioritaire

- En portrait, la scène réserve environ 43 % de sa largeur à la main gauche
  (treize gammes directes, Force to Scale, dix accords, articulation et strummer) et 57 % à la main droite
  (Home/Undo/Panic et grille 3×3 des intervalles).
- En paysage, l’harmonie reste également à gauche au lieu de consommer une bande au-dessus
  de la scène. Les contrôles de routage, audio, synthé et console restent disponibles dans
  le ruban système.
- Gammes, accords, pads, articulations et cordes exposent des cibles tactiles d’au moins
  48 dp dans les fenêtres tablette couvertes. Gammes, accords et articulations appliquent
  leur sélection dès le front descendant tactile, tout en conservant une action sémantique
  pour clavier et accessibilité. L’articulation utilise une grille 2×2 afin de ne pas
  réduire la surface du strummer.
- L’application de jeu V2.3 est une variante minifiée, compilée avec le moteur natif
  Release et isolée du package V1. La variante instrumentée reste réservée aux tests.
- Les actions tactiles rejoignent la mailbox FIFO d’un acteur mono-thread possédé par le
  ViewModel et exécuté à priorité Android audio. Leur traitement musical, MIDI et audio
  ne dépend pas d’une recomposition Compose.
- `gfxinfo` qualifie uniquement le rendu et les symptômes d’entrée Android. Une valeur de
  frame ou `High input latency` n’est jamais présentée comme une latence acoustique ou
  MIDI absolue sans mesure loopback.

## 14. Reports après la V2.3

- Les éléments de séquence typés Rest, Random Step et Ratchet ne sont pas assimilés à un
  mouvement entier. L'ordonnanceur d'arpège V2.3 ne remplace pas leur modèle : Ratchet
  attend encore ses propres actions et règles d'annulation liées au transport/destination.
- L'émission MIDI Clock/Start/Stop/Continue et Song Position Pointer est différée ; cette
  spécification ne couvre que la réception du transport MIDI.
- Les sélections de gamme, clé, accord et preset par mapping, les CC relatifs/continus,
  profils et import/export restent hors du catalogue V2.
- Les gammes personnalisées, les scopes de presets, l'optimisation soutenue à
  90 Hz et la certification USB MIDI/TalkBack/multi-touch/loopback/soak restent ouvertes.
