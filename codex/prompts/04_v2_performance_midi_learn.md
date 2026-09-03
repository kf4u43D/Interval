# Étape 4 — V2 fidélité de performance et MIDI Learn

## Précondition

Les trois portes du MVP logiciel sont acceptées. Les validations USB MIDI, TalkBack,
multi-touch réel, loopback et soak restent des limites matérielles explicites ; elles ne
doivent pas être présentées comme acquises.

## Objectif utilisateur

Un musicien peut assigner directement une Note ou un CC à une fonction de performance,
retrouver ce mapping après relance et utiliser les fonctions Same, Same Pitch, Random et
Chromatic Shift avec leur sémantique musicale documentée. Tone Row conserve sa position
pendant Pause et expose les huit parcours publics sans compromettre les Note Off.

## Lot à livrer

### Fidélité du moteur musical

- Distinguer Same Interval, qui répète le dernier déplacement diatonique, de Same Pitch,
  qui répète le dernier écart chromatique réellement entendu.
- Rendre Random Interval immédiatement jouable et déterministe à graine explicite ; il ne
  doit plus sélectionner le mode Random de Tone Row.
- Rendre Chromatic Shift momentané, silencieux à la pression et actif seulement jusqu'au
  relâchement/passage du CC sous seuil/purge/Panic.
- Garantir l'ownership et la symétrie Note On/Note Off pour ces actions, y compris avec
  plusieurs modificateurs et sources simultanées.

### Tone Row

- Une seconde commande Record pendant l'enregistrement annule la prise en cours et sort
  vers Idle.
- Autoriser les déplacements manuels pendant Pause sans redémarrer le transport ; Continue
  reprend depuis la nouvelle position.
- Une Note MIDI utilisée en lecture manuelle remplace temporairement la vélocité enregistrée,
  sans modifier le contenu de la rangée.
- Corriger Random : le mouvement conserve le signe du pas demandé et varie dans
  `0…2×|pas|`, avec départ sur le premier élément et graine déterministe.
- Ajouter Auto-Transpose haut/bas et Auto-Translate haut/bas. Chaque cycle logique complet
  accumule respectivement un demi-ton ou un degré ; Pause/Continue conserve l'accumulation,
  Restart/Reset la remet à zéro.

### MIDI Learn et éditeur

- Ajouter un reducer pur transactionnel avec baseline, brouillon, capture, candidat et
  conflits explicites.
- Capturer la première Note On ou le premier CC avant rappel de preset et routage ; le
  message appris ne doit ni jouer, ni être transmis, ni rappeler un preset.
- Gérer canal reçu ou Omni, seuil CC `1…127`, ajout/remplacement explicite, suppression,
  reset du brouillon, Save atomique et Cancel sans persistance.
- Conserver le schéma Mapping v1 pour le catalogue d'actions existant et expliquer que les
  presets déjà sauvegardés ne changent qu'après une nouvelle sauvegarde du slot.
- Fournir un panneau Compose dédié, adaptatif et accessible, sans grossir davantage la
  logique musicale de `PerformanceScreen.kt`.

## Périmètre différé

- Événements de séquence Rest, Random Step et Ratchet ; Ratchet exige un scheduler de
  retrigger annulable par génération.
- MIDI Clock/Start/Stop/Continue sortants et Song Position Pointer.
- Actions mappées de sélection gamme/clé/accord/preset, contrôleurs CC relatifs ou continus,
  profils et import/export.
- Bibliothèque étendue de gammes, scopes de presets, optimisation 90 Hz et certification
  matérielle non exécutable avec le matériel disponible.
- CV, réseau, Scala, microtonalité, MPE et MIDI 2.0.

## Tests et preuve

- Oracles D→E→Same Pitch→F♯, `+3→Same`, Random déterministe et Shift Note/CC tenu.
- Purge, Panic, changement de mapping et relâchements après modificateurs empilés.
- Record→Record annule ; Pause→Move→Continue reprend ; vélocité MIDI live non persistée.
- Huit modes Tone Row, limites de cycle, Restart/Reset, Play Once et graine Random.
- Reducer d'éditeur : capture, exact/Omni, seuils, conflits, delete/reset/save/cancel.
- ViewModel : capture avant routage/rappel, Save persiste une fois, Cancel jamais, fermeture
  sûre au lifecycle/lock/perte de source.
- UI locale/instrumentée : panneau accessible, attente/candidat/conflit et actions Save/Cancel.
- Réexécuter le gate complet des étapes 1 à 3.

## Porte de sortie

Tous les critères logiciels ci-dessus sont couverts par des tests déterministes, Lint et
assemblage. Les limites USB MIDI, TalkBack, multi-touch, loopback, soak et 90 Hz restent
nommées comme validations matérielles ou de performance ouvertes.
