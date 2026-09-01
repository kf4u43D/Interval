# Étape 3 — Audio natif, UX tablette et durcissement

## Précondition

Les portes 1 et 2 sont acceptées. Le MIDI et le Tone Row restent les fonctions principales.

## Objectif utilisateur

L’application devient jouable sans synthé externe et suffisamment robuste pour une répétition/performance prolongée sur la tablette cible.

## Lot à livrer

### Moteur Oboe

- Auditer et finaliser ouverture Low Latency/Exclusive avec repli Shared et sample rate réel.
- Finaliser file SPSC, timestamps, huit voix et politique de voice stealing.
- Saw/pulse anti-aliasés au minimum par PolyBLEP, triangle stable, mix lissé.
- ADSR, filtre résonant et contrôle de vélocité.
- Chorus stéréo, delay lissé, réverbération légère et limiteur doux.
- Paramètres bornés/lissés et métriques xruns/file/stream exposées hors callback.
- Reprise après erreur stream et cycle de vie, sans interrompre le MIDI.

### Intégration

- Chaque événement domaine peut aller vers MIDI, audio ou les deux selon le routing utilisateur.
- Panic vide immédiatement les voix et les effets de manière sûre.
- Aucun appel JNI dans le callback ; aucune allocation/verrou/I/O/log.
- Écran synthé simple : macro timbre, cutoff, resonance, ADSR, chorus, delay, reverb, master.

### UX et robustesse

- Finaliser paysage tablette, multi-touch, accessibilité, Performance Lock et diagnostics debug.
- Hotplug MIDI, changement d’activité, écran éteint selon politique, reprise après perte audio.
- Tests instrumentés des gestes, lifecycle et état.
- CI complète, lint, build debug et documentation de release.

### Mesures

- Tests hôte longs sans NaN/Inf.
- Sur appareil : sample rate, burst, xruns, CPU approximatif, latence subjective/mesurable et soak test documenté.
- Tester avec audio seul, MIDI seul et MIDI+audio.

## Porte de sortie

Satisfaire la « Porte 3 » de `docs/ACCEPTANCE_CRITERIA.md`, mettre la matrice à jour et produire un rapport de limitations. Ne pas ajouter CV, réseau ou microtonalité.
