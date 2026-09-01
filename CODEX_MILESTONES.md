# Trois étapes autonomes

## Étape 1 — Instrument intervallique MIDI

Livrer une application de performance utilisable avec écran tactile et contrôleur MIDI : navigation `-4…+4`, gammes 12-TET, note range, undo/home, accords, sélection MIDI In/Out, mapping notes/CC, quatre modes PassThru, suivi des notes actives, Panic et tests de domaine.

**Démonstration attendue** : un clavier USB pilote les intervalles, un synthétiseur externe reçoit les notes et aucun changement de mode/port ne laisse de note bloquée.

## Étape 2 — Tone Row et transport

Livrer le workflow Tone Row complet : enregistrement sans répétition de classe de hauteur, fin manuelle/automatique, lecture manuelle, séquence d’intervalles, auto-play, Prime/Retro/Random/Pendulum, inversion, transposition/translation, horloge interne et MIDI, Start/Stop/Continue, presets persistants.

**Démonstration attendue** : une série enregistrée est jouable manuellement et automatiquement, reste déterministe sous test et se resynchronise proprement.

## Étape 3 — Audio, UX tablette et durcissement

Livrer le synthé Oboe stéréo et son intégration complète : oscillateurs, ADSR, filtre, chorus, delay, réverbération, limiteur, paramètres temps réel, reprise audio, hotplug MIDI, lifecycle Android, interface paysage, accessibilité, instrumentation, CI et checklist de release debug.

**Démonstration attendue** : l’application fonctionne sans synthé externe, reste contrôlable en MIDI et tient une session prolongée sans xrun manifeste, crash ni note bloquée.
