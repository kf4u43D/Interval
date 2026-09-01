# Product brief

## Proposition

Interval Tablet transforme une tablette Android en instrument de composition et performance fondé sur la navigation par intervalles diatoniques. L’utilisateur ne choisit pas d’abord une note absolue : il déplace un curseur dans une gamme avec neuf actions rapides, choisit si les pads jouent le lead, plaquent le voicing ou restent muets, puis peut égrener ce voicing avec un strummer. Il enregistre aussi une série, la transforme et l’envoie vers des instruments MIDI externes. Un synthé interne léger permet de jouer sans autre générateur sonore.

## Utilisateurs cibles

- Musiciens modulaires désirant garder l’approche intervallique sans dépendre d’un chemin CV.
- Claviéristes et compositeurs utilisant un contrôleur USB MIDI et des synthés matériels/logiciels.
- Performeurs recherchant un séquenceur non linéaire tactile et une sortie MIDI standard.

## Hiérarchie de valeur

1. **Fiabilité MIDI** : routage prévisible, notes actives maîtrisées, hotplug et Panic.
2. **Fidélité de jeu** : intervalles, Tone Row, transformations, accords et transport.
3. **Lisibilité scénique** : interaction paysage, gros contrôles, peu de menus pendant le jeu.
4. **Autonomie sonore** : synthèse soustractive efficace, sans devenir un workstation.

## Principes produit

- Une action principale doit être jouable en un geste.
- L’état musical courant doit toujours être visible : note, degré, gamme, clé, accord, mode et transport.
- Le tactile et le MIDI externe sont deux surfaces d’un même moteur, pas deux implémentations.
- L'articulation change le rendu d'un pad sans dupliquer sa navigation, son historique ou
  son ownership ; le strummer lit le voicing du domaine sans déplacer l'instrument.
- Le traitement MIDI reste fonctionnel lorsque l’audio interne est coupé.
- Le moteur doit réagir de façon déterministe à une séquence d’actions et à une graine aléatoire données.
- Toute sortie sonore/MIDI doit disposer d’un chemin explicite vers All Notes Off.

## Indicateurs de réussite du prototype

- Temps de démarrage jusqu’à l’écran jouable inférieur à quelques secondes sur la tablette cible.
- Aucune note bloquée dans les scénarios de changement de port, mode, activité, déconnexion ou Panic.
- Parcours complet clavier USB → intervalle → MIDI Out sans dépendance au moteur audio.
- Session de performance prolongée sans crash, croissance mémoire continue ou perte durable de port.
- Tests déterministes couvrant chaque règle documentée dans la matrice comportementale.
- Le gain staging interne reste transparent sous le seuil du limiteur dans les cas
  nominaux ; la qualité sonore finale est reçue par écoute/loopback sur appareil, jamais
  déduite des seuls tests hôte.
