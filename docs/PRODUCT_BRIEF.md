# Product brief

## Proposition

Interval Tablet transforme une tablette Android en instrument de composition et performance fondé sur la navigation par intervalles diatoniques. L’utilisateur ne choisit pas d’abord une note absolue : il déplace un curseur dans une gamme avec neuf actions rapides, répète un déplacement ou un écart de hauteur, choisit si les pads jouent le lead, plaquent le voicing ou restent muets, puis peut égrener ce voicing avec un strummer. La scène se joue à deux mains, harmonie et accords à gauche, intervalles à droite. Il enregistre aussi une série, la transforme et l’envoie vers des instruments MIDI externes. Un éditeur MIDI Learn permet d’assigner Notes et CC sans quitter la tablette. Un synthé interne léger permet de jouer sans autre générateur sonore.

## Utilisateurs cibles

- Musiciens modulaires désirant garder l’approche intervallique sans dépendre d’un chemin CV.
- Claviéristes et compositeurs utilisant un contrôleur USB MIDI et des synthés matériels/logiciels.
- Performeurs recherchant un séquenceur non linéaire tactile et une sortie MIDI standard.

## Hiérarchie de valeur

1. **Fiabilité MIDI** : routage prévisible, notes actives maîtrisées, hotplug et Panic.
2. **Fidélité de jeu** : intervalles, Tone Row, transformations, accords et transport.
3. **Contrôle assignable** : apprentissage MIDI transactionnel, prévisible et annulable.
4. **Lisibilité scénique** : disposition deux mains portrait/paysage, contrôles tactiles
   accessibles et peu de menus pendant le jeu.
5. **Autonomie sonore** : synthèse soustractive efficace, sans devenir un workstation.

## Principes produit

- Une action principale doit être jouable en un geste.
- L’état musical courant doit toujours être visible : note, degré, gamme, clé, accord, mode et transport.
- Le tactile et le MIDI externe sont deux surfaces d’un même moteur, pas deux implémentations.
- L'articulation change le rendu d'un pad sans dupliquer sa navigation, son historique ou
  son ownership ; le strummer lit le voicing du domaine sans déplacer l'instrument.
- Le traitement MIDI reste fonctionnel lorsque l’audio interne est coupé.
- Le moteur doit réagir de façon déterministe à une séquence d’actions et à une graine aléatoire données.
- Toute sortie sonore/MIDI doit disposer d’un chemin explicite vers All Notes Off.
- Une capture MIDI Learn ne devient autoritaire qu'après Save ; Cancel ne laisse aucune
  modification durable.

## Portée de la V2

- `Same Interval` répète le dernier mouvement diatonique et `Same Pitch` le dernier écart
  chromatique effectivement entendu.
- Random Interval produit immédiatement un mouvement `-14…+14` à partir d'une graine
  explicite ; Chromatic Shift est un modificateur silencieux, momentané et possédé par sa
  source.
- Tone Row permet d'annuler Record par un second appui, de naviguer en Pause et d'utiliser
  la vélocité d'une Note MIDI sans réécrire la prise.
- Les quatre parcours historiques sont complétés par Auto-Transpose haut/bas et
  Auto-Translate haut/bas ; Random conserve le signe du mouvement demandé.
- MIDI Learn capture Note On ou CC avant le routage, gère canal reçu/Omni, seuil,
  collision exacte, recouvrement Omni et commit atomique.

Rest, Random Step et Ratchet, l'horloge/transport MIDI sortants et Song Position Pointer,
les actions mappées supplémentaires, les catalogues étendus de gammes/presets et la cible
de rendu soutenu à 90 Hz sont différés. Ratchet nécessite notamment un ordonnanceur de
retrigger annulable, distinct de la release unique actuelle. Les validations USB MIDI,
TalkBack, multi-touch réel, loopback et soak restent des réceptions matérielles.

## Indicateurs de réussite du prototype

- Temps de démarrage jusqu’à l’écran jouable inférieur à quelques secondes sur la tablette cible.
- Aucune note bloquée dans les scénarios de changement de port, mode, activité, déconnexion ou Panic.
- Parcours complet clavier USB → intervalle → MIDI Out sans dépendance au moteur audio.
- Session de performance prolongée sans crash, croissance mémoire continue ou perte durable de port.
- Tests déterministes couvrant chaque règle documentée dans la matrice comportementale.
- Capture Learn consommée sans note, transmission ou rappel de preset ; Save écrit une
  seule version cohérente et Cancel n'écrit rien.
- Le gain staging interne reste transparent sous le seuil du limiteur dans les cas
  nominaux ; la qualité sonore finale est reçue par écoute/loopback sur appareil, jamais
  déduite des seuls tests hôte.
