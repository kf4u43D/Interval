# Protocole de validation matérielle

## Objectif

Valider le MVP et le lot V2 sur la tablette cible avec un contrôleur MIDI USB, un
synthétiseur MIDI externe et la sortie audio Android. Ce protocole produit des preuves reproductibles ;
une impression subjective seule ne suffit pas pour fermer une porte d’acceptation.

## Matériel minimal

- tablette Android cible, alimentation suffisante et mode économie d’énergie désactivé ;
- hub USB-C OTG alimenté lorsque plusieurs périphériques sont utilisés ;
- contrôleur MIDI USB class-compliant ;
- synthétiseur ou interface MIDI de destination avec moniteur MIDI ;
- câble/loopback MIDI ou second ordinateur pour horodater les événements ;
- casque ou enceintes pour le test audio ;
- optionnel : interface audio et DAW pour mesurer les latences.

Consigner modèle exact, version Android/One UI, numéros de firmware, hub et câbles dans le
rapport de test.

## Préparation

1. Installer un APK debug produit par `:app:assembleDebug`.
2. Désactiver les optimisations batterie pour l’application pendant le test de durée.
3. Redémarrer la tablette, connecter le hub, puis le contrôleur et la destination MIDI.
4. Ouvrir l’application, sélectionner explicitement MIDI In et MIDI Out.
5. Activer le journal de diagnostic, sans journaliser depuis le callback audio.
6. Exécuter Panic avant chaque scénario afin de repartir d’un registre de notes vide.

## MIDI USB

### MIDI-USB-01 — découverte et sélection

- connecter chaque périphérique avant puis après le lancement de l’application ;
- vérifier que ports, directions et noms sont stables ;
- changer de port pendant qu’aucune note n’est active.

**Acceptation :** aucun crash, aucun doublon, état UI cohérent, fermeture de l’ancien port.

### MIDI-USB-02 — symétrie Note On/Off

- jouer chaque pad `-4…+4` séparément puis plusieurs pads simultanément ;
- répéter avec un contrôleur USB et plusieurs vélocités ;
- capturer les messages sortants.

**Acceptation :** chaque Note On possède exactement un Note Off correspondant ; aucun
compteur de note active ne reste non nul après relâchement.

### MIDI-USB-03 — mappings notes/CC

- tester mapping omni puis mapping limité à un canal ;
- vérifier la priorité du mapping de canal sur le mapping omni ;
- tester CC sous et au-dessus du seuil de déclenchement ;
- tester Note On à vélocité 0.

**Acceptation :** une seule action par message, aucune action sur le mauvais canal,
Note On vélocité 0 traité comme Note Off.

### MIDI-USB-03B — MIDI Learn et fonctions V2

- ouvrir l'éditeur, choisir Same Interval, armer puis jouer une Note ; répéter avec un CC,
  canal reçu puis Omni et plusieurs seuils ;
- capturer simultanément MIDI Out et l'état du synthé pour vérifier que le message appris
  ne joue pas et ne traverse pas ;
- créer un binding exact sur une clé Omni existante, puis provoquer une collision exacte ;
  vérifier avertissement de recouvrement et remplacement explicite ;
- modifier/supprimer/reset le brouillon puis Cancel ; rouvrir et comparer au mapping actif ;
- recommencer et Save, relancer l'application, puis rappeler un ancien preset avant et
  après resauvegarde explicite de son slot ;
- armer puis tester Performance Lock, arrière-plan, débranchement de la source et Panic.

**Acceptation :** capture avant routage/rappel, aucun Note On/CC appris transmis, exact
prioritaire sur Omni, seuil respecté, aucun remplacement silencieux, Save unique et
persistant, Cancel sans modification. Le preset antérieur garde son ancien mapping jusqu'à
sa resauvegarde ; toute fermeture forcée ne laisse ni capture ni lease active.

### MIDI-USB-03C — Same, Random et Shift

- dans une gamme dont les pas chromatiques varient, jouer D→E, puis Same Pitch et vérifier
  F♯ ; jouer `+3`, puis Same Interval et comparer les déplacements ;
- exécuter deux fois Random Interval depuis le même état/graine de session ;
- tenir Chromatic Shift par Note puis par CC, jouer plusieurs sources, empiler deux shifts
  et relâcher chaque modificateur séparément ;
- répéter avec changement de mode, purge de port et Panic.

**Acceptation :** Same Interval répète le pas diatonique, Same Pitch le delta réellement
entendu, Random joue immédiatement sans changer le mode Tone Row et la séquence est
reproductible à graine identique. Shift ne joue rien seul, ne modifie pas les notes déjà
tenues, et chaque source/release produit des Note Off exacts sans modificateur résiduel.

### MIDI-USB-03A — articulation des pads et strummer

- choisir un accord à trois voix puis jouer le même pad en `ARPEGGIATED`, `STACKED` et
  `MUTED`, en capturant la sortie ;
- vérifier que `MUTED` déplace la note et peut enregistrer une entrée Tone Row sans Note On ;
- en `MUTED`, balayer le strummer dans les deux sens, d'abord lentement puis assez vite
  pour sauter plusieurs frontières ; répéter avec deux pointeurs si le numériseur le permet ;
- maintenir une note plaquée, changer d'articulation, puis relâcher ;
- lancer Auto Tone Row dans chacun des trois modes.

**Acceptation :** lead seul en `ARPEGGIATED`, voicing complet en `STACKED`, aucun Note On
de pad en `MUTED`; chaque corde traversée produit une instance et un Note Off exacts, sans
changer la note courante. Le changement de mode ne coupe pas la note tenue et Auto conserve
le voicing complet. Aucun compteur actif ne subsiste après les releases/Panic.

### MIDI-USB-04 — quatre modes PassThru

Pour `Off`, `Active`, `Active Last Note` et `PassThru`, envoyer notes, CC, pitch bend,
clock et messages non mappés.

**Acceptation :** routage conforme à `MIDI_SPEC.md`; en `Active Last Note`, le prochain
intervalle part de la dernière note non mappée passée. Une transition de mode ne coupe
pas une note tenue : son Note Off suit la route choisie au Note On, puis aucun lease ni
aucune note logique ne subsiste après le relâchement. En PassThru, Panic et
TogglePassThrough restent consommés sans retransmission du déclencheur ; Clock, transport,
Program Change et Song Select sont transmis sans effet local ni rappel de preset.

### MIDI-USB-05 — hotplug et cycle de vie

- maintenir une note, débrancher MIDI In puis MIDI Out ;
- rebrancher dans un ordre différent ;
- passer l’application en arrière-plan, verrouiller/déverrouiller l’écran et changer
  d’application pendant une note tenue ;
- répéter après rotation/événement de configuration autorisé.

**Acceptation :** Panic/Note Off est émis lorsque possible, aucune note logique ne reste
active, la reconnexion ne duplique pas les callbacks.

### MIDI-USB-06 — charge et latence

- injecter au moins 10 Note On/Off par seconde pendant 10 minutes ;
- lancer simultanément l’UI et le synthé interne ;
- mesurer MIDI In → MIDI Out par loopback horodaté.

**Cible MVP :** zéro message perdu ou note bloquée ; latence p95 ≤ 20 ms et aucun événement
> 50 ms sous charge nominale. Toute dérogation exige mesures brutes et ADR.

## Transport/Tone Row

### TRANSPORT-01 — horloge MIDI

- envoyer Clock à 24 PPQN, puis Start, Stop et Continue ;
- tester plusieurs divisions, puis séparément l'horloge interne entre 40 et 240 BPM ;
- interrompre le câble pendant la lecture puis reconnecter.

**Acceptation :** pas doublé ou manquant dans le scénario nominal. Start revient au
début. Stop MIDI libère la voix automatique, conserve positions/phase et affiche Tone Row
en pause. Continue reprend cette position sans Note On immédiat ; la note suivante n'arrive
qu'au tick qualifiant. Le bouton Stop local est testé séparément et doit revenir à
`Idle`/`Stopped` tout en conservant le contenu de la rangée.

### PRESET-01 — rappel MIDI

- enregistrer des snapshots distincts dans les slots internes 0 et 127 (affichés 1 et 128) ;
- envoyer Program Change sur le canal configuré, puis sur un autre canal et en Omni ;
- envoyer Song Select depuis la même source, avec un slot existant puis absent ;
- répéter en Off, Active et PassThru, dont un rappel pendant une note tenue.

**Acceptation :** Program Change respecte le filtre de canal, Song Select est global et
les valeurs `0…127` ciblent les slots correspondants. Un slot existant consommé exécute
Panic avant restauration ; un slot absent poursuit le routage normal. En PassThru aucun
rappel n'est appliqué et le message traverse inchangé. Toute restauration commence avec
Tone Row `Idle`, transport `Stopped` et aucun registre de note active.

### TONEROW-01 — déterminisme

- enregistrer une série connue ;
- comparer lecture manuelle et auto ;
- répéter les modes aléatoires avec la même graine puis une graine différente.

**Acceptation :** même graine = même séquence, sauvegarde/rechargement conserve l’état
accepté, aucune répétition interdite pendant l’enregistrement.

### TONEROW-02 — fidélité V2

- démarrer Record, entrer plusieurs notes puis appuyer de nouveau sur Record ;
- enregistrer une rangée avec des vélocités distinctes, démarrer Auto, Pause, naviguer par
  pad puis par Note MIDI à vélocité extrême, et envoyer Continue ;
- parcourir Prime, Retro, Random, Pendulum, Auto-Transpose haut/bas et Auto-Translate
  haut/bas sur une rangée connue ; pour Random tester des pas positifs, négatifs et zéro ;
- laisser chaque mode Auto franchir au moins deux cycles, avec Pause/Continue puis
  Restart/Reset.

**Acceptation :** second Record revient à `Idle` sans conserver la prise annulée. Pause
reste affiché pendant la navigation ; Continue repart de la nouvelle position. La
vélocité MIDI ne change que l'émission. Random démarre au premier élément, conserve le
signe et reste reproductible. Les transformations Auto évoluent une fois par cycle,
survivent à Pause/Continue et reviennent à leur accumulation neutre sur Restart/Reset.

## Audio interne

### AUDIO-01 — démarrage/reprise

- activer/désactiver l’audio dix fois ;
- brancher/débrancher un périphérique audio compatible ;
- passer en arrière-plan puis revenir.

**Acceptation :** reprise contrôlée ou message d’erreur explicite, aucune fuite de stream,
aucun crash.

### AUDIO-02 — fonctions DSP

- vérifier oscillateur, ADSR, cutoff/résonance, chorus, delay et réverbération aux valeurs
  min/médiane/max ;
- envoyer polyphonie maximale et Panic ;
- surveiller `droppedEvents`, sample rate, frames/burst et xruns.
- comparer à niveau de sortie identique une note, deux accords plaqués, un arpège rapide et
  un strum du voicing, réverbération à 0 puis à sa valeur par défaut ;
- enregistrer si possible le flux par loopback pour relever peak, écrêtage consécutif et
  niveau avant/après correctif, sans normalisation automatique du DAW.

**Acceptation :** sortie finie (aucun NaN/Inf), pas de niveau continu dangereux, pas de
saturation audible ni d'écrêtage numérique nominal, Panic rend le signal silencieux après
la release prévue, paramètres bornés. Une écoute seule est consignée comme subjective ;
elle ne remplace pas le loopback lorsqu'une mesure chiffrée est revendiquée.

### AUDIO-03 — soak

- session continue de 60 minutes avec séquence, accords, changements de paramètres et UI ;
- écran allumé puis période écran éteint selon la politique produit ;
- relever diagnostics toutes les 5 minutes hors callback.

**Acceptation :** zéro crash/ANR, zéro note bloquée, aucune croissance continue de mémoire,
aucun événement perdu ; xruns nuls ou entièrement expliqués par une action de périphérique.

## Rendu 90 Hz

### UI-90HZ-01 — performance soutenue V2

- installer la variante benchmark identifiée, compiler AOT et vérifier que la dalle est
  réellement à 90 Hz ;
- exécuter des passes contrebalancées audio OFF/ON avec Auto Tone Row, puis avec le panneau
  MIDI Learn ouvert et un brouillon représentatif ;
- conserver warm-up, batterie, thermique, dump `gfxinfo` brut et fenêtre exacte.

**Acceptation :** budget de frame soutenu de 11,11 ms démontré sur une fenêtre complète,
sans présenter `gfxinfo` comme une mesure de latence MIDI ou audio. Ce protocole est ouvert
tant qu'aucune nouvelle campagne V2 n'est archivée.

## Reports non testables dans la V2

Ne pas ajouter de résultat Pass pour Rest, Random Step, Ratchet, émission MIDI
Clock/Start/Stop/Continue, Song Position Pointer, mappings de gamme/clé/accord/preset,
CC relatifs/continus, profils/import-export ou catalogues étendus de gammes/presets : ces
fonctions sont différées. USB MIDI/Learn, TalkBack, vrai multi-touch, loopback, hotplug
audio et soak nécessitent les scénarios matériels ci-dessus et restent ouverts jusqu'à
preuve archivée.

## Rapport

Créer `docs/implementation/HARDWARE_REPORT_<date>.md` avec :

- configuration exacte ;
- APK/commit testé ;
- tableau Pass/Fail/Blocked par identifiant ;
- captures MIDI et mesures brutes ;
- compteurs audio ;
- défauts reproductibles et étapes minimales.
