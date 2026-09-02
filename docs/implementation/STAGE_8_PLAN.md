# Plan d’implémentation — Étape 8 / V2.4

Date : 2 septembre 2026

## Résultat utilisateur attendu

La scène principale reste immédiatement jouable à deux mains, avec un strummer vertical
pleine hauteur couvrant trois octaves. La barre supérieure donne accès à Interval, MIDI,
Synthé et Arpégiateur, ainsi qu'à Home, Undo, Panic, Mute, BPM et signature. Un changement
de gamme ou d'accord revoicera les pads maintenus au même timestamp, sans exiger de
relâcher le doigt. Les contrôles du synthé ont tous un effet DSP réel et continu.

## Périmètre inclus

- Changement de gamme en direct, avec ancien Note Off puis nouveau Note On au même
  timestamp et conservation des sessions physiques.
- Strummer du voicing courant répliqué sur trois octaves, borné à la plage MIDI.
- Barre supérieure unique et pages plein écran Interval, MIDI, Synthé et Arpégiateur.
- Page MIDI contenant les informations MIDI In/Out et l'éditeur Learn existant.
- Bouton Mute explicite pour le moniteur audio.
- BPM `20…300`, signature `1…12` sur `2/4/8/16`, division et gate visibles.
- Arpégiateur autonome configurable : ordre, une à trois octaves et motif rythmique de
  huit pas, tout en restant indépendant de Tone Row et du transport démarré.
- Synthé étendu avec ADSR filtre, montant d'enveloppe, ADSR ampli, delay libre/synchronisé,
  feedback/mix, drive, LFO assignable et délai d'entrée, chorus/réverbération/master.
- Banque embarquée de presets synthé originaux et rappel en un geste ; le patch rappelé
  devient le patch global persistant.
- Force to Scale conservé et décrit comme quantification optionnelle des notes générées.
- Version performance `0.2.4-dev-performance` installable avec la V1.

## Périmètre explicitement différé

- Copie d'interface, de ressources, de presets ou de textes d'un produit tiers.
- Latch d'arpège, export MIDI, lanes polymétriques indépendantes, ties et éditeur piano-roll.
- Émission MIDI Clock/transport, MPE, MIDI 2.0, Scala et microtonalité.
- Certification USB MIDI, loopback tactile→audio, TalkBack complet et soak 60 minutes.

## Invariants

- Chaque note reste possédée par sa source ; Release et Panic restent symétriques.
- Le revoicing gamme/accord n'efface pas le geste maintenu et n'insère aucun timestamp
  intermédiaire.
- Le gate d'arpège libère seulement la voix courante, jamais la session de pad.
- Le domaine reste Kotlin pur et déterministe ; l'app ne calcule aucune harmonie.
- Les identifiants synthé `0…15` restent inchangés et les nouveaux identifiants sont ajoutés.
- Le callback audio ne fait ni allocation, ni verrou, ni I/O, ni JNI.
- MIDI reste opérationnel lorsque le moniteur audio est muet ou indisponible.

## Risques et preuves

| Risque | Preuve prévue | Mitigation |
|---|---|---|
| Note bloquée au changement de gamme | tests reducer multi-source | revoicing atomique par ownership |
| Gate d'arpège supprimant le geste | tests callback tardif et ReleaseVoice | action dédiée idempotente |
| Migration cassant la V2.3 | fixtures Settings/Preset/banque historiques | schémas versionnés avec défauts exacts |
| Nouveau contrôle sans effet sonore | contrat Kotlin/C++ et tests natifs | identifiants filaires et bornes partagés |
| Écran trop dense | instrumentation 900×1440 + inspection tablette | pages dédiées et barre unique |
| Régression de latence | acteur inchangé + variante minifiée | actions directes au touch-down, projections étroites |

## Baseline

- `scripts\\doctor.ps1` : 0 erreur, avertissement attendu pour `kotlinc` autonome absent.
- `scripts\\verify-domain.ps1` : réussi, 138/138.
- `scripts\\verify-native.ps1` : réussi, CTest 2/2.
- Git propre sur `main`, huit commits locaux devant `origin/main`.

## Critères de sortie

- [x] Gamme et accord revoicent les pads maintenus sans perte de session.
- [x] Quatre pages et contrôles système sont regroupés dans la barre supérieure.
- [x] Strummer vertical trois octaves et cibles accessibles.
- [x] Arpégiateur configurable audible sans Tone Row.
- [x] Tous les paramètres synthé et presets sont fonctionnels et persistants.
- [x] Gates domaine, natif, Android, Lint, assemblages et tablette exécutés.
- [x] Documentation, matrice, état Codex, changelog et commits locaux finalisés.

## Résultat de clôture

- Commit d'implémentation : `c762b9e632e2a141add757dced28a8995348db5d`.
- Tests : domaine 143/143, application 163/163, CTest 2/2, instrumentés 8/8.
- Appareil : Samsung SM-X620/API 36, V1 et V2.4 Performance coinstallées.
- APK : 9 276 296 octets, SHA-256
  `ECDDE9FC6E4FBD0811EDA0C24CAB847281599C5A7C94EC785DCBB6D539FC2A82`.
- Audio après stress : 48 kHz, burst 96, buffer 192, queue 0/28, zéro xrun/drop.
- Limites : le stress `gfxinfo` n'est pas un loopback ; USB MIDI, TalkBack,
  multi-touch réel et soak 60 minutes restent des réceptions distinctes.
