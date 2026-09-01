# ADR 0003 — MVP MIDI-first

- Statut : accepté
- Date : 2026-08-19

## Décision

Le MVP privilégie MIDI USB et une sortie MIDI standard. Le moteur audio est stéréo, minimal et facultatif. CV, multicanal, réseau et microtonalité sont différés.

## Conséquences

- Le chemin MIDI ne dépend pas de l’état audio.
- Les critères de l’étape 1 doivent être validés avec un synthé externe.
- Les modèles restent en notes MIDI 12-TET entières pour ce cycle.
