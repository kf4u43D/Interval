# Rapport matériel — Samsung SM-X620 — 21 août 2026

## Portée

Cette session constitue une réception partielle de l'APK sur tablette, sans périphérique
MIDI externe. Elle prouve l'installation et plusieurs parcours tactiles/UI sur le modèle
testé. Elle ne ferme ni les protocoles MIDI USB, ni le vrai multi-touch, ni TalkBack, ni
le soak audio de 60 minutes.

Les chiffres de rendu ci-dessous sont la **baseline historique antérieure au durcissement
de l'étape 3**. Le mot « final » dans les preuves historiques désigne la dernière passe de
cette ancienne campagne, pas le résultat de l'étape 3. La campagne A/B `benchmark` audio
OFF/ON de l'étape 3 est présentée séparément avec ses dumps bruts.

Les captures d'écran et arbres UI sont conservés sous
[`evidence/2026-08-21-sm-x620`](evidence/2026-08-21-sm-x620/).

## Configuration observée — baseline historique

| Élément | Valeur |
|---|---|
| Tablette | Samsung `SM-X620` |
| API Android | 36 |
| Rafraîchissement | 90 Hz |
| APK | `app-debug.apk`, 39 265 007 octets |
| SHA-256 | `B9E6A8EC9A73315AB3D3E8888F3CF078B887CE345528A309421D4C690AA7FBD4` |
| Échelle de police testée | 1,3× |
| Périphérique MIDI externe | Aucun |
| Durée du soak court | 3 minutes |

## Résultats

Les termes employés ci-dessous sont stricts : « réussi » ne vaut que pour la portion
effectivement observée ; « partiel » signifie qu'une partie normative du cas n'a pas été
exécutée ; « bloqué » signifie que le matériel requis était absent.

| Cas | Résultat | Observation et preuve |
|---|---|---|
| Installation et lancement | Réussi | L'APK identifié ci-dessus s'installe et atteint l'écran Performance en paysage. Voir [`initial-landscape.png`](evidence/2026-08-21-sm-x620/initial-landscape.png) et son [arbre UI](evidence/2026-08-21-sm-x620/initial-landscape.xml). |
| Lifecycle sans MIDI | Réussi sur le parcours observé | Passage arrière-plan/retour et relance sans crash ni ANR ; la session Tone Row enregistrée est restaurée. Voir [`record-back.png`](evidence/2026-08-21-sm-x620/record-back.png) et [`after-record-relaunch.xml`](evidence/2026-08-21-sm-x620/after-record-relaunch.xml). |
| Rotation, timeline et barres système | Réussi après correctif | Portrait puis retour paysage sans crash. Le gonflement de la timeline et son placement par rapport aux barres système ont été corrigés. Voir [`portrait.png`](evidence/2026-08-21-sm-x620/portrait.png), [`row-fixed.png`](evidence/2026-08-21-sm-x620/row-fixed.png) et [`fixed-row-7.png`](evidence/2026-08-21-sm-x620/fixed-row-7.png). |
| Police 1,3× | Réussi sur cette configuration | Les contrôles principaux restent visibles et manipulables à l'échelle testée. Voir [`font-130.png`](evidence/2026-08-21-sm-x620/font-130.png) et son [arbre UI](evidence/2026-08-21-sm-x620/font-130.xml). |
| Pad tactile, une source | Réussi sur une pression | L'état pressé apparaît puis le compteur de notes actives revient à zéro après relâchement. Voir [`pad-held.png`](evidence/2026-08-21-sm-x620/pad-held.png) et [`pad-released.xml`](evidence/2026-08-21-sm-x620/pad-released.xml). |
| `MIDI-USB-02` — symétrie complète | Partiel | La portion tactile à un pointeur est observée, mais aucun flux MIDI sortant n'a été capturé et le vrai multi-touch n'a pas été exécuté. |
| Tone Row tactile | Partiel | Enregistrement, rangée complète, lecture automatique, pause, Play Once et restauration sont visibles. Voir [`fixed-recording-3.png`](evidence/2026-08-21-sm-x620/fixed-recording-3.png), [`fixed-row-7.png`](evidence/2026-08-21-sm-x620/fixed-row-7.png), [`auto-paused.png`](evidence/2026-08-21-sm-x620/auto-paused.png), [`play-once-finished.png`](evidence/2026-08-21-sm-x620/play-once-finished.png) et [`restored-row-7.png`](evidence/2026-08-21-sm-x620/restored-row-7.png). La comparaison Random à graines contrôlées du cas `TONEROW-01` n'est pas prouvée sur tablette. |
| Timeline et arrangement | Réussi sur les états capturés | Timeline, curseurs et arrangement restent visibles après le correctif de mise en page. Voir [`arrangement.png`](evidence/2026-08-21-sm-x620/arrangement.png) et [`arrangement-presets.png`](evidence/2026-08-21-sm-x620/arrangement-presets.png). |
| Presets locaux | Réussi pour sauvegarde/rappel UI | Un état modifié puis rappelé revient au snapshot attendu. Voir [`preset-mutated.png`](evidence/2026-08-21-sm-x620/preset-mutated.png) et [`preset-recalled.png`](evidence/2026-08-21-sm-x620/preset-recalled.png). Le rappel Program Change/Song Select de `PRESET-01` reste bloqué faute de MIDI externe. |
| Audio Monitor | Partiel | Activation/désactivation et lifecycle observés sans erreur audio signalée. Voir [`audio-toggle.xml`](evidence/2026-08-21-sm-x620/audio-toggle.xml). L'écoute qualifiée, le hotplug audio, les xruns prolongés et la qualité DSP ne sont pas validés. |
| `AUDIO-03` — soak | Partiel, durée insuffisante | La session courte de 3 minutes se termine sans crash, ANR ni erreur audio et sans croissance mémoire observée sur cette fenêtre. Voir [`soak-finished.png`](evidence/2026-08-21-sm-x620/soak-finished.png). Le protocole exige 60 minutes. |
| Tests instrumentés de régression UI | Réussi | L'exécution finale directe par `am instrument` réussit : 2/2 tests, 0 échec. Elle couvre la scène sous timeline et les neuf pads accessibles distincts. |
| Fluidité UI à 90 Hz | Baseline avant étape 3 | La dernière passe Release AOT historique améliore la médiane CPU et le taux de jank legacy, mais la majorité des frames dépasse encore le budget de 11,11 ms à 90 Hz et le taux strict reste à 99,58 %. |
| MIDI USB, Clock et hotplug | Bloqué | Aucun contrôleur, synthétiseur, interface ou loopback MIDI externe utilisé. `MIDI-USB-01/03/04/05/06`, la partie MIDI de `TRANSPORT-01` et le rappel MIDI de `PRESET-01` restent ouverts. |
| Vrai multi-touch | Bloqué | Une pression tactile a été observée, mais aucune preuve simultanée de plusieurs pointeurs physiques n'est conservée. |
| TalkBack et contraste mesuré | Bloqué | Aucun parcours TalkBack ni relevé de contraste n'est fourni dans ce lot de preuves. |

## Baseline historique de performance et stabilité

Le même scénario a été mesuré en build Release précompilé (AOT), à 120 BPM, division 6,
rangée de 7 pas et lecture de 30 secondes. La colonne « avant » correspond à la mesure
précédant l'isolation du coût de la timeline ; la colonne « baseline retenue » correspond
au build après ce correctif, mais avant les optimisations de l'étape 3.

| Mesure | Avant isolation | Baseline retenue | Évolution observée |
|---|---:|---:|---:|
| Temps de frame p50 | 18 ms | 16 ms | -2 ms (-11,1 %) |
| Temps de frame p90 | 24 ms | 24 ms | inchangé |
| Temps de frame p95 | non relevé | 25 ms | — |
| Temps de frame p99 | non relevé | 26 ms | — |
| Temps GPU p50 | non relevé | 6 ms | — |
| Temps GPU p90 | non relevé | 7 ms | — |
| Jank legacy | 317/481 (65,90 %) | 304/481 (63,20 %) | -13 frames, -2,70 points |
| Jank strict | 478/481 (99,38 %) | 479/481 (99,58 %) | +1 frame, +0,20 point |

Le gain de médiane et du classement legacy est réel sur ce scénario contrôlé, mais ne
ferme pas la dette de performance : à 90 Hz, le budget d'une frame est d'environ
11,11 ms, le p90 reste à 24 ms et le classement strict n'est pas amélioré. Les temps GPU
de la baseline retenue, plus faibles, ne suffisent pas à établir à eux seuls la cause des
dépassements.

Un démarrage froid AOT sur cette baseline a été observé à **216 ms**. Il s'agit d'une seule passe et
non d'un benchmark ; aucune tendance ne doit en être déduite.

Sur le soak court, la mémoire est restée stable pendant trois minutes et aucun crash,
ANR ou message d'erreur audio n'a été observé. La durée normative de 60 minutes n'a pas
été exécutée. Les captures et arbres UI de la session sont archivés dans le dossier de
preuves ; les mesures de performance restent des observations de session à reproduire
avec les données brutes lors du durcissement.

## Réception du durcissement de l'étape 3

Le binaire de mesure utilise la variante minifiée/profileable `benchmark` dans un package
isolé. Les tests UI utilisent une variante non minifiée `instrumented` distincte ; ils
n'injectent donc pas leur activité hôte dans l'APK mesuré.

| Élément | Valeur |
|---|---|
| Package mesuré | `dev.intervaltablet.benchmark` |
| APK benchmark | 8 932 016 octets ; SHA-256 `3F0E2B057159B6A26F7D1F2C434C6A3F9ACB4D0D6F82B8BF611ABF2C196E80C8` |
| Précompilation | AOT `speed` confirmée |
| APK instrumented | 39 458 061 octets ; SHA-256 `041B1392337D28A1AEE84C8D51A51303F845439D13898134B59E68A326A6D7C7` |
| APK de test | 2 304 769 octets ; SHA-256 `EED0885BD472F0FA3D5D0F5A77AF7B97F7015B5002AAA16E016F4512A717895F` |
| Instrumentation finale | 2/2 tests réussis |
| Scénario | D4…C5, Prime, 7 pas, 120 BPM, division 6, gate 75 %, 90 Hz |
| Ordre des passes | OFF, ON, ON, OFF, ON, OFF, OFF, ON |
| Fenêtre par passe | warm-up 10 s, mesure 30 s |

Les deux tests instrumentés prouvent que les neuf pads restent des nœuds accessibles
distincts d'au moins 72 dp avec action sémantique et qu'une timeline remplie conserve la
scène de performance pondérée. Ils ne constituent ni un vrai geste multi-touch, ni un
parcours TalkBack.

| Condition | Passes | Frames plateforme médiane (plage) | p50/p90/p95/p99 | GPU p50/p90 | Jank strict | Jank legacy |
|---|---:|---:|---:|---:|---:|---:|
| Audio OFF | 4 | 475 (474…476) | 18/22,5/23/26,5 ms | 6/7 ms | 99,475 % | 68,985 % |
| Audio ON | 4 | 476,5 (474…477) | 18/22,5/23/26,5 ms | 6/7 ms | 99,685 % | 75,155 % |

Les médianes de médianes des phases disponibles sont :

| Condition | UI | Traversal | Render | Issue | GPU | Frame completed |
|---|---:|---:|---:|---:|---:|---:|
| Audio OFF | 5,88 ms | 3,97 ms | 5,19 ms | 3,34 ms | 5,94 ms | 18,75 ms |
| Audio ON | 6,56 ms | 4,51 ms | 5,31 ms | 3,38 ms | 5,74 ms | 19,05 ms |

Les huit passes rapportent 0 `Missed Vsync`. Les distributions OFF/ON se recouvrent :
aucun surcoût DSP n'est attribuable à partir de cette campagne. La durée totale de frame
reste néanmoins supérieure au budget de 11,11 ms et le jank strict demeure
ouvert. Le ring `framestats` brut est limité aux 120 dernières frames, environ 7,5 s :
les phases et percentiles du tail ne décrivent que cette queue, tandis que les métriques
plateforme ci-dessus couvrent les 30 s.

Les preuves brutes sont sous
[`evidence/2026-08-21-sm-x620/performance/stage3`](evidence/2026-08-21-sm-x620/performance/stage3/).
`gfxinfo` ne mesure ni latence tactile→audio, ni latence MIDI, ni round-trip. Stop puis
Panic ont été exécutés après la campagne.

## Correctif vérifié pendant la session

Le gonflement de la timeline et son placement par rapport aux barres système ont été
corrigés, puis revérifiés sur les états de rangée et les deux orientations. Les deux tests
instrumentés finaux ont été lancés directement sur la tablette avec `am instrument` et
ont réussi sans échec. Le gate Gradle final est également vert. La réception reste
limitée à la SM-X620, à l'API 36 et aux configurations
capturées ; elle ne constitue pas une matrice multi-appareils.

## Cas restant à recevoir

1. Exécuter `MIDI-USB-01` à `MIDI-USB-06` avec contrôleur, destination et capture MIDI.
2. Exécuter Clock/Start/Continue/Stop réels et Program Change/Song Select.
3. Capturer un vrai geste multi-touch simultané et vérifier le retour de tous les compteurs à zéro.
4. Effectuer TalkBack, ordre de focus et mesure de contraste.
5. Exécuter le soak `AUDIO-03` complet de 60 minutes avec mémoire, xruns et erreurs relevés toutes les cinq minutes.

## Conclusion

L'APK est installable et les principaux parcours UI/Tone Row/presets fonctionnent sur la
SM-X620 dans les configurations observées. L'instrumentation finale est verte et la
campagne A/B ne met pas en évidence de surcoût DSP attribuable. Elle confirme cependant
que la durée totale des frames ne tient pas encore le budget 90 Hz. La réception reste donc
**partielle** : MIDI USB, vrai multi-touch, TalkBack, loopback audio, reprise/xruns réels
et soak de 60 minutes restent ouverts.

La réception de l'extension articulation/strummer du 22 août, basée sur un nouveau build
instrumented et 3/3 tests, est séparée dans
[`HARDWARE_REPORT_2026-08-22.md`](HARDWARE_REPORT_2026-08-22.md). Elle ne remplace pas la
campagne A/B ci-dessus et ne ferme toujours pas l'écoute/loopback anti-saturation.
