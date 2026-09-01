# Rapport tablette — articulation, strummer et Synthé/audio — 22 août 2026

## Portée

Cette session reçoit sur Samsung SM-X620/API 36 l'extension d'articulation des pads, le
strummer et le lot final Synthé/audio. Elle utilise les APK `instrumented` reconstruits
après le correctif final et ne désinstalle ni n'efface l'application debug existante.

La session prouve installation, démarrage, sémantiques, tailles de cibles, navigation
muette, absence de dette de note après un strum, contrôle accessible du patch Synthé et
dix cycles courts du stream réel. Elle ne prouve ni sortie MIDI USB, ni qualité sonore
subjective, ni latence loopback, hotplug audio ou stabilité Oboe prolongée. Une autre
charge AAudio était active pendant AUDIO-01 : le compteur xrun est donc rapporté sans être
surqualifié.

Aucun nouveau dump brut ADB ou screenshot n'est versionné pour cette session ; le présent
rapport consigne les hashes du binaire, la sortie synthétique de l'instrumentation et les
observations reproductibles. Le niveau de preuve est donc inférieur à la campagne brute
du 21 août pour les aspects purement visuels.

## Binaire et environnement

| Élément | Valeur observée |
|---|---|
| Tablette | Samsung `SM-X620` |
| API Android | 36 |
| Variante | `instrumented`, APK application et APK de tests réinstallés avec mise à jour |
| APK instrumented | 41 050 917 octets ; SHA-256 `1AEAD32061DC3FF4EEC435287CF1954B6187DCE8CE2820E071969F23751C35D0` |
| APK de test | 2 441 967 octets ; SHA-256 `6E312722AD2B8A0A61EA6C413974B9B9489B3046D0327FCC6D63155E7175C2B7` |
| APK benchmark du smoke antérieur | 8 953 212 octets ; SHA-256 `A1E2F015A900ED8B62CC1CED1FABB313247DC74DA6A1DD484E983B7E8779584C` |
| Orientation du parcours | paysage |
| Périphérique MIDI externe | aucun |
| Écoute ou loopback audio | non exécuté |

Aucun identifiant de session ADB, numéro de série ou adresse réseau n'est archivé. Le
démarrage à froid a abouti à l'activité Performance en environ 944 ms sur une observation
unique ; cette valeur n'est pas un benchmark.

## Instrumentation finale

L'APK de test est exécuté directement avec `AndroidJUnitRunner` après réinstallation des
deux artefacts. Résultat : `OK (6 tests)`, 0 échec/ignoré, en 17,94 s.

Les cas couvrent :

- scène Tone Row conservée sous une timeline remplie ;
- neuf pads accessibles distincts d'au moins 72 dp ;
- trois sélecteurs d'articulation à sémantique radio ;
- une cible accessible distincte par corde du voicing et activation de callback ;
- panneau Synthé, sémantiques de ses contrôles, commit en fin de geste et fermeture sous
  Performance Lock ;
- bouton Synthé désactivé tant que Settings n'est pas chargé, puis panneau ouvrable ;
- AUDIO-01 sur dix cycles du stream réel avec les 16 paramètres et Panic à chaque cycle.

## AUDIO-01 — stream et paramètres

Chaque cycle ouvre le stream, attend des diagnostics négociés, envoie le patch complet de
16 paramètres puis Panic, attend le drain de la file et ferme le stream. Si `restartCount`
évolue entre les échantillons précédant et suivant la sonde, la sonde complète est rejouée
sur le stream repris avant validation.

Les dix cycles réussissent à 48 kHz, 192 frames/burst et buffer 384 frames. La profondeur
maximale de file atteint 17, cohérente avec 16 paramètres suivis de Panic, puis revient à
zéro. Les diagnostics finaux indiquent 0 événement perdu, 0 reprise et 0 code d'erreur ;
le maximum de xrun est 1. Une autre application maintenait simultanément une charge AAudio :
ce xrun est une observation sous concurrence, pas une mesure isolée de stabilité ou de
latence. Aucune écoute, capture loopback, commutation de route ni interruption physique
n'est réalisée.

## Smoke tactile et arbre d'accessibilité

Le parcours réel suivant a été observé :

1. sélection de `MUET`, confirmée par l'état `checked=true` et la description
   « Les pads déplacent la note sans produire de son » ;
2. pression de `+1`, faisant passer la navigation de C4 à D4 sans note active résiduelle ;
3. choix du voicing Octaves, exposant trois cordes distinctes dans l'arbre d'accessibilité ;
4. glissé vertical réel traversant les trois cordes ;
5. note de navigation toujours D4 et compteur revenu à `0 note active` ;
6. court stress en `PLAQUÉ` : douze taps alternés sur les pads, strum descendant puis
   remontant, toutes les entrées acceptées, UI/processus vivant et retour à zéro note active ;
7. moniteur audio toujours affiché actif, sans erreur visible, puis arrêt explicite de
   l'application après le contrôle.

L'arbre confirme également des sélecteurs d'au moins 48 dp et le rail vertical de
strummer prévu pour le paysage. Le geste est un balayage physique à un pointeur ; il ne
ferme pas le cas de vrai multi-touch simultané ni TalkBack.

## Smoke de la variante benchmark

La variante minifiée de la sous-campagne articulation est installée par mise à jour, sans
effacement. Elle atteint
la scène Performance, restaure l'état, expose `ARPÉGÉ`/`PLAQUÉ`/`MUET`, le strummer et
`0 note active` dans l'arbre d'accessibilité, puis est arrêtée explicitement. Un cold start
de 239 ms est observé une fois : il est illustratif et n'est ni une campagne de
performance, ni une preuve AOT. Cet APK n'est pas précompilé en mode `speed` et aucune
passe A/B n'est exécutée pour ce hash ; le gate final reconstruit ensuite Benchmark sans
répéter ce smoke.

## Résultats

| Cas | Résultat | Limite |
|---|---|---|
| Installation/mise à jour des APK isolés | Réussi | application debug utilisateur non remplacée |
| Démarrage de l'activité | Réussi | mesure unique, non statistique |
| Instrumentation Android | Réussi | 6/6, 0 échec/ignoré, 17,94 s |
| Trois articulations accessibles | Réussi | sémantiques/callbacks, pas de capture MIDI |
| Panneau Synthé accessible | Réussi | contrôles, commit en fin de geste et fermeture sous Performance Lock |
| Chargement Settings | Réussi | bouton Synthé désactivé avant chargement, activé ensuite |
| `MUTED` déplace sans dette de note | Réussi sur le parcours | absence de Note On externe non mesurée faute de MIDI USB |
| Trois cordes distinctes | Réussi | voicing Octaves observé |
| Strum sans déplacement de navigation | Réussi sur le parcours | balayage à un pointeur uniquement |
| Stress plaqué/strum court | Réussi fonctionnellement | 12 taps + aller/retour, 0 note active ; aucune mesure xrun/distorsion |
| AUDIO-01, 10 cycles | Réussi sur le contrat testé | 16 paramètres + Panic/cycle, file max 17, drop/restart/erreur 0, xrun max 1 sous charge AAudio concurrente |
| Smoke APK benchmark minifié | Réussi | installation, restauration, scène et sémantiques ; non AOT/non mesuré |
| Qualité anti-saturation | Non exécuté | aucune écoute comparative ni loopback |
| MIDI USB/Clock/hotplug | Bloqué | périphériques externes absents |
| Latence et hotplug audio | Non exécuté | aucune mesure loopback ni commutation physique de route |
| Soak audio 60 minutes | Non exécuté | dix cycles courts seulement |

## Conclusion

L'extension est reçue sur tablette pour sa structure UI, son geste principal, son
ownership visible et le contrat court de contrôle du stream : le mode muet navigue, le
strummer traverse les trois cordes, le panneau Synthé respecte chargement/verrouillage et
les 16 paramètres sont drainés sans perte sur dix cycles. Cette preuve ne permet pas
d'affirmer que la saturation audible est supprimée, que la latence est acceptable ou que
le moteur résiste au hotplug et au soak de 60 minutes. L'autre charge AAudio observée
interdit aussi d'interpréter le xrun maximal comme une mesure isolée. MIDI USB reste
bloqué faute de périphérique ; écoute contrôlée, loopback, latence, hotplug et soak restent
ouverts.
