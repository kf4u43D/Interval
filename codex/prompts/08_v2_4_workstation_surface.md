# Étape 8 — V2.4 surface instrument, arpégiateur et synthé étendu

Livrer une évolution cohérente de la scène de performance :

- revoicer immédiatement les pads maintenus lors d'un changement de gamme, sans phase
  silencieuse entre les anciens Note Off et les nouveaux Note On ;
- réunir navigation, Home, Undo, Panic, BPM, signature et Mute dans une barre supérieure ;
- proposer quatre pages plein écran originales : Interval, MIDI, Synthé et Arpégiateur ;
- étendre le strummer vertical à trois octaves du voicing courant ;
- ajouter un arpégiateur configurable (ordre, octaves, division, gate et motif rythmique)
  inspiré de principes fonctionnels publics, sans copier interface, textes ni ressources ;
- exposer un synthé réellement pilotable : enveloppes filtre et ampli, delay synchronisable,
  drive, LFO assignable avec délai d'entrée et presets d'exemple embarqués ;
- préserver Force to Scale comme garde-fou optionnel des notes générées, sans modifier
  le MIDI PassThru.

Ajouter migrations, tests domaine/application/natifs/instrumentés, mettre à jour les
documents, livrer la variante performance V2.4 coinstallable avec la V1, puis clôturer
Git localement sans push.
