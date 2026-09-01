# Étape 1 — Instrument intervallique MIDI complet

## Objectif utilisateur

Au terme de cette exécution, l’application doit être un instrument jouable : un musicien peut utiliser l’écran ou un contrôleur USB MIDI pour naviguer dans une gamme, produire des accords et envoyer un flux MIDI fiable vers un synthétiseur externe. L’audio interne peut rester secondaire.

## Lot à livrer

### Domaine musical

- Auditer et finaliser la grille de notes, Home, `-14…+14`, `0`, Undo, wrap/clamp, changement de clé/gamme et plage.
- Finaliser les neuf accords, leurs doublures, vélocités et notes hors plage.
- Garantir la polyphonie de plusieurs sources simultanées et le comptage d’instances.
- Unifier tactile et MIDI sur les mêmes `InstrumentAction`/reducers.
- Ajouter des tests de propriété ou tables exhaustives sur toutes les gammes fournies et limites MIDI.

### MIDI Android

- Implémenter découverte, ouverture et fermeture sûre des ports d’entrée/sortie.
- Parser Note On/Off, CC, Program Change, Pitch Bend, aftertouch et messages temps réel sans désynchronisation.
- Finaliser le mapping note/CC, y compris Move seulement pour CC ≥64.
- Implémenter Off, Active, Active Last Note et PassThru avec leases par Note On.
- Implémenter Panic, All Notes Off, changement de port et déconnexion sans notes bloquées.
- Exposer une couche testable avec faux périphériques/ports ; aucun test domaine ne doit dépendre d’Android.

### UI

- Écran paysage original avec grille multi-touch `-4…+4`, cibles de notes, note courante, gamme, clé, accord, routage, ports et Panic.
- Écran ou panneau MIDI pour choisir ports, canaux, mode et reset du mapping.
- État d’erreur lisible sans dialogue bloquant.
- Accessibilité de base et conservation d’état lors des rotations/recréations.

### Audio

- Conserver la façade existante opérationnelle, mais ne pas consacrer l’étape à enrichir le DSP.
- Les événements générés peuvent alimenter le synthé de départ lorsque l’option Audio Monitor est active.

## Tests et preuve

- Tous les tests domaine et parsing passent.
- Ajouter des scénarios de transition avec notes tenues pour chaque paire de modes.
- Si appareil disponible : clavier USB en entrée, synthé/interface en sortie, déconnexion pendant notes tenues.
- Documenter précisément tout test matériel impossible.

## Porte de sortie

Satisfaire entièrement la section « Porte 1 » de `docs/ACCEPTANCE_CRITERIA.md`, sauf éléments explicitement marqués comme bloqués par matériel. Ne pas commencer Tone Row/transport au-delà des interfaces nécessaires.
