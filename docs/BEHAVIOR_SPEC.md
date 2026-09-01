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

## 2. Actions de performance

### Pression d’un intervalle

Au `press` :

1. Libérer les notes encore associées à la même source physique, le cas échéant.
2. Calculer la nouvelle hauteur.
3. Ajouter l’ancienne hauteur à l’historique uniquement si la nouvelle est différente.
4. Construire le voicing complet de l’accord courant.
5. Appliquer l'articulation de pad : lead seul en `ARPEGGIATED`, voicing complet en
   `STACKED`, aucune note en `MUTED`.
6. Émettre les Note On retenus et mémoriser exactement les notes/instances actives pour
   cette source. Une pression muette ne crée pas de propriétaire vide.

Au `release`, émettre un Note Off pour chaque instance créée par cette source. La position musicale ne revient pas en arrière.

Des sources différentes peuvent rester appuyées simultanément et produire une polyphonie indépendante.

Une articulation ne change ni le calcul de hauteur, ni l'historique, ni l'ancre. Son
changement ne coupe pas les notes déjà tenues : chaque source garde les instances émises
au moment de sa pression jusqu'à son `release`.

### Undo

- Revient à la précédente hauteur **différente**.
- Ignore donc les mouvements `0` et les répétitions de même hauteur.
- Déclenché comme une action jouable, il produit un nouveau Note On/Off selon la source.
- En mode Tone Row lecture, `Undo` est remplacé par `Restart`.

### Panic

`Panic` :

- émet un Note Off pour toutes les instances connues sur le canal de sortie ;
- envoie CC 123 (All Notes Off) et CC 120 (All Sound Off) sur les canaux concernés ;
- vide les registres de notes actives, sans modifier la gamme, la clé ou le contenu de Tone Row.

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

- `ARPEGGIATED` est le mode initial. Une pression de pad joue immédiatement le lead
  uniquement ; le terme décrit ici une articulation une note par geste, pas un scheduler
  temporel caché.
- `STACKED` joue simultanément toutes les instances du voicing dans leur ordre défini,
  avec la pondération lead/harmonies ci-dessus.
- `MUTED` conserve navigation, historique, ancre, feedback de pression et enregistrement
  Tone Row, mais n'émet aucun Note On depuis les pads.

Le voicing complet reste calculable dans les trois modes pour alimenter le strummer.

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
- Les notes d’un contrôleur MIDI mappées aux fonctions d’intervalle utilisent les mêmes règles.
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

### Modes de lecture

- `Prime` : ordre et sens normaux.
- `Retro` : ordre inversé.
- `Random` : choix par générateur pseudo-aléatoire à graine injectable ; les tests doivent être reproductibles.
- `Pendulum` : aller-retour sans répéter deux fois les extrémités.
- `Transpo Up/Down` : décalage chromatique en demi-tons appliqué après la projection
  diatonique, sans modifier la série enregistrée.
- `Translate Up/Down` : décalage en degrés appliqué au contour avant sa projection
  dans la gamme active, sans modifier la définition enregistrée.
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
  articulation comprise. Les schémas courants sont Settings v4, Preset v3 et banque v2.
  La migration d'un preset ancien infère `ARPEGGIATED` lorsque
  l'accord est Off et `STACKED` lorsqu'un accord est actif afin de préserver son rendu
  historique. Le schéma courant migre aussi le format plat de l'étape 1. Les imports/exports de fichiers
  restent hors périmètre.
- Le patch du synthétiseur interne est un réglage global de Settings v4. Il n'entre pas
  dans les snapshots Preset v3 ou la banque v2 ; sauvegarde, rappel UI, Program Change et
  Song Select conservent donc le patch courant. Le Panic préalable au rappel éteint les
  voix et les effets sans réinitialiser ses paramètres.
- L’autosauvegarde compare le snapshot durable après les commandes tactiles et MIDI :
  une nouvelle entrée enregistrée est persistée immédiatement, tandis que les simples
  Note On/Off, ticks, curseurs live et deadlines ne provoquent pas d’écriture.

## 10. Moniteur audio et patch synthé

- `SynthPatch` porte seize valeurs finies et bornées, associées à des identifiants filaires
  stables `0…15` : mix saw, pulse et triangle, pulse width, ADSR, cutoff, résonance,
  chorus mix, temps/feedback/mix du delay, reverb mix et master.
- Le cutoff canonique de la session est borné à `20 Hz…20 kHz`. Le moteur natif applique
  en plus le plafond sûr du sample rate réellement négocié.
- Les sessions Settings v0 à v3 migrent vers le patch par défaut exact. Settings v4
  persiste les seize champs globalement et borne chaque valeur lue avant la frontière JNI.
- L'acteur rejoue les seize paramètres dans l'ordre après chaque démarrage audio accepté.
  Il rejoue aussi le patch courant après toute récupération native, qu'elle soit observée
  par une transition d'arrêté à actif ou seulement par l'incrément du compteur de
  redémarrages, afin d'inclure les changements effectués pendant la reprise.
- Le panneau Synthé est non modal. Il expose Timbre, cutoff/résonance, ADSR,
  chorus/delay/reverb, master et une projection de diagnostics audio dédiée. Il est
  désactivé tant que les réglages ne sont pas chargés, puis fermé et masqué sous
  Performance Lock ; le toggle Audio Monitor reste disponible.
- Pendant un geste de slider, Compose conserve le brouillon local et publie au plus un
  aperçu par frame d'affichage. L'acteur n'envoie au moteur que les paramètres dont la
  valeur filaire a changé ; cet aperçu reste transitoire et ne déclenche ni publication
  d'état durable ni persistance. Au relâchement, la dernière position est garantie puis le
  patch complet devient autoritaire et est persisté. Une fermeture du panneau pendant un
  geste finalise également ce brouillon.

## 11. Déterminisme

Les tests fournissent explicitement :

- horloge monotone simulée ;
- graine aléatoire ;
- ordre des événements ;
- configuration de gamme, clé, plage et canal.

Aucune règle du domaine ne lit directement l’heure système, Android, un périphérique ou un générateur aléatoire global.
