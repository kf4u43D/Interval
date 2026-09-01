# Protocole de validation matérielle

## Objectif

Valider le MVP sur la tablette cible avec un contrôleur MIDI USB, un synthétiseur MIDI
externe et la sortie audio Android. Ce protocole produit des preuves reproductibles ;
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

## Rapport

Créer `docs/implementation/HARDWARE_REPORT_<date>.md` avec :

- configuration exacte ;
- APK/commit testé ;
- tableau Pass/Fail/Blocked par identifiant ;
- captures MIDI et mesures brutes ;
- compteurs audio ;
- défauts reproductibles et étapes minimales.
