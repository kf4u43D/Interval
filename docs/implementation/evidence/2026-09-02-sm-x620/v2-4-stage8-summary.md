# Synthèse de preuve — V2.4 / Stage 8

Date : 2 septembre 2026

Appareil : Samsung SM-X620, Android API 36

Commit d’implémentation : `c762b9e632e2a141add757dced28a8995348db5d`

## Gates logiciels

| Gate | Résultat |
|---|---:|
| Domaine Kotlin | 143/143, 12 suites |
| Application JVM | 163/163, 19 suites |
| Total JVM | 306/306 |
| Native CTest | 2/2 |
| Instrumentation | 8/8 en 28,734 s |

Les tests couvrent notamment le revoicing de gamme au même timestamp, les releases par
source, le gate d’arpège sans perte du geste, les ordres/octaves/motifs, les migrations
Settings v6/Preset v5/banque v4 et les 28 identifiants synthé.

## Réception tablette

- Les onglets Interval, MIDI, Synthé et Arpégiateur sont accessibles depuis la barre
  supérieure avec Home, Undo, Panic, Mute, BPM et signature.
- La page Interval affiche l’harmonie à gauche, neuf cordes de strummer trois octaves au
  centre et les intervalles à droite. La suite instrumentée vérifie des cibles ≥48 dp.
- La page Synthé expose les deux enveloppes, drive, LFO, delay synchronisé, effets,
  master et six presets.
- La page Arpégiateur expose ordre, octaves, huit pas, division, gate, BPM et signature.
- La page MIDI réunit les informations In/Out, le routage et MIDI Learn.

Une première instrumentation a détecté des cordes de 38 dp. La disposition a été
corrigée avant la réception finale ; la seconde exécution passe 8/8.

## Artefact installé

- Package : `dev.intervaltablet.performance`
- Version : `0.2.4-dev-performance` (versionCode 6)
- Fichier : `app/build/outputs/apk/performance/app-performance.apk`
- Taille : 9 276 296 octets
- SHA-256 : `ECDDE9FC6E4FBD0811EDA0C24CAB847281599C5A7C94EC785DCBB6D539FC2A82`
- Compilation ART : `speed`
- Co-installation : dernière V1 `dev.intervaltablet.debug` conservée ; aucune variante
  instrumentée ou ancienne V2 installée.

## Stress et audio

Après 108 frappes :

- frames : 125 ;
- p50 16 ms, p90 20 ms, p95 20 ms, p99 21 ms ;
- maximum 22 ms, aucun frame au-dessus de 23 ms ;
- diagnostic audio : 48 kHz, 96 frames/burst, buffer 192, queue 0/28 ;
- xruns 0, événements perdus 0, reprises 0, dernière erreur aucune ;
- aucun fatal, ANR ou crash natif observé.

`gfxinfo` décrit le rendu Android. Cette campagne n’est ni un A/B strict avec V2.2,
ni une mesure loopback tactile→MIDI/audio. USB MIDI réel, TalkBack, vrai multi-touch,
hotplug et soak 60 minutes restent des validations matérielles ouvertes.
