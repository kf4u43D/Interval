# Étape 2 — Tone Row, transformations, transport et presets

## Précondition

La porte 1 est acceptée ou les seuls éléments restants sont des validations matérielles documentées. Ne pas masquer une régression MIDI derrière les nouvelles fonctions.

## Objectif utilisateur

Enregistrer une série depuis les boutons ou le MIDI, la jouer manuellement ou automatiquement, la transformer en direct, la synchroniser à une horloge MIDI et retrouver le tout après relance.

## Lot à livrer

### Enregistrement et représentation

- Implémenter la machine d’état Idle/Recording/ManualPlayback/AutoPlaying/Paused.
- Garantir l’unicité des classes de hauteur et la recherche de la prochaine classe disponible dans le sens demandé.
- Fin automatique lorsque la gamme est épuisée et fin précoce par Play.
- Stocker les degrés/contours et vélocités de manière compatible avec changement de clé/gamme 12-TET.

### Lecture et transformations

- Lecture manuelle : les intervalles déplacent dans les indices de la série, `+1` suit l’ordre enregistré, `0` rejoue, Undo devient Restart.
- Séquence automatique éditable, défaut et reset `{+1}`.
- Prime, Retro, Random déterministe et Pendulum sans double extrémité.
- Inversion, transposition, translation, octave et reset aux valeurs de référence.
- Accords et multi-touch restent compatibles pendant les modes appropriés.

### Transport

- Horloge interne injectable et MIDI Clock 24 PPQ.
- Start revient au début, Continue reprend, Stop conserve la position.
- Tempo/note duration/clock division sans double tick ni blocage UI.
- Play Once s’arrête exactement après une traversée définie.

### Presets

- Schéma JSON/DataStore versionné avec migrations.
- Persister série, séquence, mapping, gamme, clé, accord, routage et paramètres utiles.
- Program Change et Song Select selon la politique documentée.
- Import/export local peut rester hors périmètre si la persistance interne est robuste.

### UI

- Timeline lisible de la série, curseurs de série/séquence, statut record/play/pause.
- Transformations accessibles sans remplacer la surface des intervalles par une copie du panneau d’origine.
- Réglages secondaires hors écran Performance Lock.

## Tests et preuve

- Fixtures 5, 7 et 12 notes ; enregistrements précoces et complets.
- Tests déterministes de chaque mode et transformation.
- Tests Clock/Start/Continue/Stop avec horloge simulée et byte stream MIDI.
- Tests de migration de preset et restauration après recréation.
- Réexécuter tous les tests de l’étape 1.

## Porte de sortie

Satisfaire la « Porte 2 » de `docs/ACCEPTANCE_CRITERIA.md`. Ne pas développer de nouvelles familles d’effets audio dans cette étape.
