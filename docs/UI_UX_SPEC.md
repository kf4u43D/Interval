# Spécification UI/UX tablette

## Portée et orientation

La porte 1 livre une surface de jeu intervallique MIDI originale. Le paysage est prioritaire ; le portrait et les petites fenêtres utilisent une disposition compacte calculée par contraintes, sans orientation forcée dans le manifeste. Leur réception visuelle reste à effectuer sur la tablette cible.

Tone Row et transport appartiennent à la porte 2. Ils ne doivent apparaître dans l’écran Performance qu’une fois leurs machines d’état intégrées ; la porte 1 n’affiche ni bouton ni aperçu factice pour ces fonctions.

## Écran Performance — porte 1

### Bandeau de scène

- Note courante et degré ; une ancre externe chromatique est signalée comme telle.
- Clé, gamme et, lorsque l’espace le permet, accord courant.
- Phases MIDI In et MIDI Out avec indicateur textuel et visuel.
- État du moniteur audio, sans jamais laisser entendre que MIDI dépend de lui.
- Accès permanent à la Console ; lorsque Performance Lock est actif, ce contrôle sert à déverrouiller.

### Surface de jeu

- Grille 3×3 des intervalles `-4…+4`, avec les valeurs positives en haut et les négatives en bas.
- Chaque pad montre direction, intervalle et note cible calculée depuis l’état musical réel, y compris l’ancre externe encore à consommer.
- Une cible bornée ou rebouclée porte explicitement le libellé Clamp ou Wrap : le sens n’est pas transmis par la couleur seule.
- Un pad actif affiche son état et son nombre de pointeurs/sources.
- Les neuf cibles ont une taille minimale de 72 dp.
- Un sélecteur permanent expose `ARPÉGÉ`, `PLAQUÉ` et `MUET`. Il reste disponible sous
  Performance Lock car il s'agit d'une articulation de jeu, pas d'un réglage dangereux.
- Le strummer affiche une corde distincte par instance du voicing courant, doublures
  comprises, avec nom de note et ordre accessibles.
- En paysage normal, le strummer forme un rail vertical de 112 dp à droite des pads ; il
  se réduit à 88 dp en paysage compact. En portrait/fenêtre étroite, il devient une bande
  horizontale de 160 dp de haut sous les pads.

### Contrôles permanents

- Rail gauche : Home, Undo et Panic ; Panic reste visible, plus grand et distinct, sans confirmation modale.
- En paysage large : panneau rapide Accord, mode de routage, Audio Monitor et accès Console.
- En mode compact : Accord et routage passent dans le ruban inférieur afin de conserver la grille jouable.
- Ruban inférieur : note range, politique Wrap/Clamp, nombre d’instances actives, état audio,
  accès Synthé et accès Console. L'accès Synthé est désactivé tant que les réglages ne sont
  pas chargés et masqué sous Performance Lock.

### Panneau synthé et diagnostics

Le panneau Synthé est une surface secondaire scrollable et non modale. Il conserve la
surface Performance visible, s'adapte en une ou deux colonnes et ne change jamais le
routage MIDI. Il expose :

- un macro Timbre qui pilote ensemble saw, pulse, triangle et pulse width ;
- cutoff logarithmique `20…20 000 Hz`, résonance, Attack, Decay, Sustain et Release ;
- mix Chorus, Delay et Reverb, puis gain Master.

Le patch persistant sous-jacent reste le contrat typé complet de 16 paramètres, même si
le panneau regroupe les oscillateurs dans Timbre et n'expose pas tous les réglages avancés
du delay. Pendant un glissement, le panneau garde un brouillon local et publie un aperçu
audio confluent au plus une fois par frame, limité aux paramètres réellement modifiés.
La dernière position puis le patch complet sont validés/persistés à la fin du geste. Un
redémarrage du stream rejoue ce patch complet, y
compris lorsque seul le compteur de reprises révèle le redémarrage entre deux diagnostics,
sans affecter MIDI.

La zone Diagnostics affiche l'état arrêté/actif/reprise, sample rate, frames par burst,
taille du buffer, profondeur courante/maximale de file, xruns, événements perdus, reprises
et dernier code d'erreur. Chaque slider expose libellé et valeur courante aux services
d'accessibilité ; chaque métrique est annoncée comme une paire libellé/valeur.

### Console MIDI non modale

La Console est un panneau superposé, défilable et refermable, pas un dialogue bloquant. Elle regroupe :

- clé, gamme, accord, note range et Wrap/Clamp ;
- ports MIDI In/Out et leur phase de connexion ;
- canal d’entrée Omni ou 1–16 et canal de sortie 1–16 ;
- mode Off, Active, Active Last Note ou PassThru ;
- état du mapping, accès à MIDI Learn et Reset mapping ;
- Performance Lock.

L’absence ou l’erreur d’un port reste visible sans masquer la surface de jeu. Les messages d’état apparaissent dans un bandeau non modal, annoncé comme région accessible polie et explicitement refermable.

### Panneau MIDI Learn V2

MIDI Learn utilise une surface secondaire dédiée, adaptative et scrollable ; la logique
de transaction n'est pas dupliquée dans l'écran Performance.

- L'en-tête distingue mapping actif, brouillon inchangé/modifié et capture inactive,
  armée ou candidate.
- Le musicien choisit une action, arme Learn, puis voit le type Note/CC, le numéro et le
  canal reçus. Une capture CC expose son seuil `1…127`, 64 par défaut.
- Un choix explicite bascule entre canal reçu et Omni. Un recouvrement exact/Omni est
  expliqué comme priorité, sans être présenté comme une erreur destructive.
- Une collision sur la même clé affiche l'affectation existante et sépare clairement
  `Ajouter` de `Remplacer`. L'éditeur expose aussi supprimer, annuler la capture et
  restaurer le mapping par défaut.
- Save est indisponible lorsqu'un candidat reste indécis et ne ferme qu'après commit
  accepté. Cancel ferme sans modifier le mapping actif. Le panneau rappelle qu'un preset
  existant ne reçoit le nouveau mapping qu'après resauvegarde de son slot.
- Performance Lock ferme et masque l'éditeur. Une perte du port source, un arrêt d'hôte
  ou une récupération d'overflow abandonne la capture et affiche ensuite un statut
  non modal sûr.

Chaque binding est présenté comme une cible focusable avec libellé complet type/numéro,
canal, seuil éventuel et action. Attente, candidat, conflit et avertissement Omni sont
annoncés en texte ; aucun état ne dépend de la couleur seule.

## Interaction

- Une action musicale commence au pointer-down et son Note Off est demandé au pointer-up ou à l’annulation.
- Chaque identifiant de pointeur possède une `TriggerSource.Touch` distincte ; plusieurs pointeurs, y compris sur le même pad, restent indépendants.
- Une source relâchée ou annulée ne peut produire qu’une seule libération.
- TalkBack et l’activation clavier utilisent une action one-shot passant par le même coordinateur musical ; Entrée et Espace sont acceptés.
- Un pointer-down sur une corde la déclenche immédiatement. Le déplacement énumère toutes
  les cordes intermédiaires, et une inversion peut les redéclencher dans l'autre sens.
  Chaque pointeur de strummer conserve son propre trajet.
- La distance sur l'axe secondaire produit une vélocité `1…127`. Une hystérésis de 8 dp
  autour des frontières évite les oscillations involontaires sans empêcher une inversion
  franche.
- Chaque corde reste activable séparément par TalkBack, Entrée ou Espace avec la vélocité
  tactile par défaut ; les sélecteurs de mode utilisent des sémantiques radio.
- Chaque hit est un one-shot de 220 ms. Sa release n'est planifiée qu'après acceptation
  du press par l'acteur, afin qu'une mailbox chargée ne puisse pas inverser leur ordre.
- Le retour haptique est optionnel et n’est pas une exigence de la porte 1.
- Aucun dialogue bloquant ne doit interrompre la performance.
- Lorsqu'un Learn est armé, la première Note On ou le premier CC est consommé avant toute
  action de performance, transmission ou rappel. Le feedback de candidat remplace donc le
  feedback de jeu pour ce message.

## États visuels

- Appuyé : surface, bordure et compteur actifs.
- Cible : nom de note affiché sous chaque intervalle.
- Hors plage : résultat Clamp ou Wrap affiché en texte.
- Port absent/en cours/en erreur/ouvert : phase explicite dans le bandeau et la Console.
- Audio arrêté ou indisponible : message explicite « MIDI opérationnel » ; une reprise en
  cours possède un état distinct dans le panneau Synthé.
- Performance Lock : Console fermée, panneau Synthé masqué et réglages dangereux masqués ;
  Home, Undo, Panic, accord, routage et Audio Monitor restent jouables.
- Articulation et strummer : toujours visibles et jouables ; `MUTED` n'est pas présenté
  comme un audio globalement coupé, puisque le strummer et le moniteur peuvent rester sonores.
- MIDI Learn : attente explicite, candidat reçu, collision à remplacer, recouvrement Omni,
  brouillon modifié et rejet de Save possèdent chacun un libellé distinct.

La porte 1 ne simulait aucun état d'enregistrement ou de transport. La porte 2 expose
désormais les états `Idle`, `Recording`, `ManualPlayback`, `AutoPlaying` et `Paused`, le
statut du transport ainsi que les curseurs de rangée et de séquence.

## Accessibilité et réception

L’implémentation fournit des libellés complets de type « monter de trois degrés, cible Sol cinq », un état pressé, l’annonce Clamp/Wrap, un ordre de focus Compose et des actions clavier. La mise en page suit les contraintes disponibles et les tailles de police système.

Les contrôles suivants restent matériels : TalkBack sur tablette, contraste mesuré,
grandes polices, portrait/paysage, multi-touch physique et validation MIDI-USB-02. Les
tests JVM du registre multi-pointeur et du tracker de strummer prouvent propriété logique,
crossings, inversion, vélocité et hystérésis, pas le comportement complet du numériseur
Android. Les cibles de mode et les cordes ont une hauteur/largeur interactive minimale de
48 dp ; les pads restent à 72 dp.

## Extension porte 2

Le deck Tone Row intégré affiche une timeline scrollable, les curseurs de rangée et de
séquence, Play/Pause, Play Once, Record, Stop et Restart. Il permet aussi de choisir le
parcours, l'inversion, la transposition chromatique, la translation diatonique, l'octave,
la séquence de mouvements, la source d'horloge, la division, le tempo, la durée de gate et
les presets. Les huit parcours sont Prime, Retro, Random, Pendulum, Auto-Transpose
haut/bas et Auto-Translate haut/bas. En Pause, les pads continuent de déplacer le curseur
sans afficher le transport comme relancé ; une vélocité MIDI live n'est jamais présentée
comme une modification de l'entrée enregistrée. Une seconde activation de Record indique
clairement l'abandon de la prise. Home et Panic restent accessibles ; Undo devient
Restart uniquement dans le contexte de lecture défini par la spécification
comportementale. Performance Lock masque
l'édition secondaire de l'arrangement sans retirer le transport ni les commandes de jeu.

## Extension porte 7 — surface harmonique V2.3

- Portrait et paysage conservent une séparation latérale à deux mains : harmonie à gauche,
  intervalles à droite.
- Les treize gammes standards et dix accords sont simultanément visibles en boutons
  dédiés d'au moins 48 dp ; aucun menu n'est requis pendant le jeu.
- Gamme, accord et articulation répondent au pointer-down. Clavier et services
  d'accessibilité utilisent une action sémantique distincte, sans double callback au up.
- `ARPÉGÉ` indique explicitement qu'un maintien parcourt les voix au tempo/division sans
  nécessiter de séquence. Le changement d'accord d'un pad maintenu est sonore immédiatement.

Rest, Random Step, Ratchet, Clock/transport MIDI sortants, Song Position Pointer, actions
mappées étendues et catalogues de gammes/presets ne doivent pas apparaître comme contrôles
inactifs ou factices en V2. L'optimisation soutenue à 90 Hz et la réception
USB/TalkBack/multi-touch restent des validations ouvertes.
