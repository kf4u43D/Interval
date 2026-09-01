# Traçabilité vers le guide utilisateur de référence

## Source et méthode

Référence de travail : **Eventide Misha User Guide, version 1.1.6, P/N 141374,
© 2023 Eventide Inc.** Le lien officiel est conservé dans `docs/RESEARCH_SOURCES.md`.

Cette matrice sert à vérifier des **comportements observables**. Elle ne constitue pas une
spécification de firmware et ne doit pas conduire à copier du code, des textes, des
ressources, la disposition du panneau ou l’identité visuelle du produit. Les formulations
locales sont volontairement réécrites et adaptées à une tablette Android.

Les numéros ci-dessous sont les pages imprimées du guide, pas nécessairement les index
PDF.

## Matrice

| Fonction du prototype | Section du guide | Pages | Spécification locale | Étape | Adaptation locale |
|---|---|---:|---|---:|---|
| Navigation par intervalles/degrés | Quickstart ; Knobs, Buttons & Display | 5–11 | `BEHAVIOR_SPEC.md` | 1 | Neuf pads `-4…+4`, gammes 12-TET seulement |
| Répétition par `0` | Knobs, Buttons & Display | 9–11 | `BEHAVIOR_SPEC.md` | 1 | Réémet la hauteur courante selon la politique de notes actives |
| Undo | Knobs, Buttons & Display | 9–11 | `BEHAVIOR_SPEC.md` | 1 | Annule le dernier mouvement non nul, avec Note Off sûr |
| Home | Home | 40 | `BEHAVIOR_SPEC.md` | 1 | Retour à la fondamentale dans le registre central configuré |
| Limites de notes | Note Range Menu ; Setup/System | 14–15, 51 | `BEHAVIOR_SPEC.md` | 1 | Clamp/rejet déterministe documenté, sans CV |
| Choix de gamme et de clé | Scales | 16–17 | `PRODUCT_BRIEF.md`, `BEHAVIOR_SPEC.md` | 1 | Sous-ensemble original de gammes 12-TET ; aucune Scala/microtonalité |
| Same Interval, Same Pitch, Random et Chromatic Shift | User-Assignable Buttons | 18–19 | `BEHAVIOR_SPEC.md`, `MIDI_SPEC.md` | 4 | Same distingue mouvement diatonique et delta entendu ; Random est immédiat et Shift momentané |
| Fonctions assignables | User-Assignable Buttons | 18–19 | `MIDI_SPEC.md`, `UI_UX_SPEC.md` | 1/4 | Éditeur MIDI Learn original pour Note/CC exact ou Omni, seuil et commit transactionnel |
| Enregistrement Tone Row | Tone Row | 20–22 | `BEHAVIOR_SPEC.md` | 2/4 | Unicité des classes, fin automatique/anticipée ; second Record abandonne la prise en cours |
| Lecture manuelle Tone Row | Tone Row — Manual | 22–23 | `BEHAVIOR_SPEC.md` | 2/4 | Navigation circulaire, y compris en Pause ; vélocité MIDI live non persistée |
| Lecture automatique et séquence de pas | Tone Row — Automated | 23–26 | `BEHAVIOR_SPEC.md` | 2 | Horloge interne/MIDI, graine aléatoire injectable |
| Huit parcours de lecture | Play Options | 27–28 | `BEHAVIOR_SPEC.md` | 2/4 | Prime, Retro, Random signé, Pendulum et Auto-Transpose/Auto-Translate haut/bas |
| Transformations par boutons | Interval Button Option | 28–30 | `BEHAVIOR_SPEC.md` | 2/4 | Transposition/translation/inversion sans microtonalité ; accumulation automatique par cycle en V2 |
| Accords jusqu’à trois voix | Polyphony — Chords | 31–32 | `BEHAVIOR_SPEC.md`, `AUDIO_DSP_SPEC.md` | 1/3 | Neuf structures par degrés, noms/UI originaux autorisés |
| Articulation pads et strummer | Polyphony — Chords/MIDI Out | 31–33 | `BEHAVIOR_SPEC.md`, `UI_UX_SPEC.md` | 3 | Extension tactile originale : lead, accord plaqué ou pad muet puis égrenage ; aucune prétention de reproduire un geste ou panneau propriétaire |
| Polyphonie tactile simultanée | Polyphony — MIDI Out | 33 | `MIDI_SPEC.md` | 1 | Notes suivies par origine de pression |
| Mapping de notes et CC | MIDI In ; Setup/MIDI | 34, 46–47 | `MIDI_SPEC.md` | 1 | CC déclencheur au seuil documenté localement |
| All Notes Off | MIDI Out | 35 | `MIDI_SPEC.md` | 1 | Bouton Panic toujours visible + CC 123 par canal concerné |
| Modes Off/Active/Active Last Note/PassThru | MIDI PassThru | 35–37 | `MIDI_SPEC.md` | 1 | Machine d’état pure et tests de transitions |
| MIDI Clock, Start, Stop, Continue | MIDI Sync ; Setup/Clocking | 37, 48 | `MIDI_SPEC.md` | 2 | Transport à 24 PPQN, reprise sans double déclenchement |
| Synthèse interne | Audio Out | 41 | `AUDIO_DSP_SPEC.md` | 3 | Moteur original : soustractif + chorus/delay/réverb, pas une copie sonore |
| Mapping clavier par défaut | Appendix — Default MIDI Note Map | 58 | `MIDI_SPEC.md` | 1 | Preset de mapping propre au projet, modifiable |

## Fonctions volontairement différées

- Les éléments de séquence Rest, Random Step et Ratchet ne sont pas couverts par la ligne
  « lecture automatique ». Ratchet attend un scheduler de retrigger annulable.
- La réception MIDI Clock/Start/Stop/Continue est tracée ; leur émission et Song Position
  Pointer ne le sont pas encore.
- Les fonctions assignables V2 n'incluent pas la sélection de gamme, clé, accord ou
  preset, les CC relatifs/continus, profils ou import/export.
- Les catalogues matériels de gammes et de presets ne sont pas reproduits. La bibliothèque
  12-TET et la banque interne restent une adaptation originale et plus étroite.
- La fluidité soutenue à 90 Hz, USB MIDI/Learn, TalkBack, vrai multi-touch, loopback et
  soak sont des preuves tablette/matérielles ouvertes, pas des comportements déduits du
  guide.

## Règles de validation

1. Toute case marquée « fidèle » dans `MISHA_BEHAVIOR_MATRIX.md` doit pointer vers une
   ligne de cette matrice et vers au moins un test ou protocole.
2. En cas d’ambiguïté du guide, choisir l’interprétation la plus simple et déterministe,
   puis la consigner dans une ADR ou le plan d’étape.
3. Une fonction hors du lot courant reste documentée comme différée ; elle ne doit pas apparaître
   implicitement sous forme de dépendance ou d’API prématurée.
4. Une ressemblance visuelle n’est jamais un critère d’acceptation.
