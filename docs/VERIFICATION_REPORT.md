# Rapport de vérification du workspace

Dates de référence : **20 août 2026** pour la clôture historique des portes 1–2,
**21 août 2026** pour le gate logiciel initial de l'étape 3 et sa campagne A/B, puis
**22 août 2026** pour l'extension articulation/strummer, anti-saturation et le lot final
de paramètres/panneau Synthé avec validation du stream réel, **31 août 2026** pour la
revalidation Wi-Fi et le correctif de packaging Oboe, puis **1er septembre 2026** pour le
suivi continu des sliders Synthé et la clôture du gate complet.

Ce rapport sépare les preuves logicielles réellement exécutées de la réception partielle
sur Samsung SM-X620 et des validations qui exigent encore un contrôleur MIDI USB, un vrai
geste multi-touch, TalkBack ou un soak prolongé. Il clôt la tranche logicielle des portes
1 à 3 ; il ne constitue pas une preuve matérielle complète ni une preuve de parité avec
le module de référence. L'état du MVP logiciel est `complete` ; la certification
matérielle reste explicitement partielle.

## Résultat synthétique du gate final — 1er septembre 2026

| Domaine | Résultat | Preuve ou commande |
|---|---|---|
| Domaine Kotlin pur | Réussi | `:domain:test` : 94/94 tests, 10 suites, 0 échec/erreur/ignoré |
| Tests JVM application | Réussi | `:app:testDebugUnitTest` : 140/140 tests, 18 suites, 0 échec/erreur/ignoré |
| Total déterministe Kotlin/JVM | Réussi | 234/234 tests |
| DSP et pont JNI/Oboe hôte | Réussi | `verify-native.ps1` : 2/2 suites |
| Diagnostic hôte | Réussi | `doctor.ps1` : 0 erreur ; seul `kotlinc` absent déclenche le repli Gradle prévu |
| Structure et gate agrégé | Réussi | `verify-structure.ps1` et `verify.ps1` verts avec contrôle du runtime APK |
| Lint Android final | Réussi | Debug, Release, Benchmark et Instrumented : `No issues found.` |
| Assemblages du lot | Réussi | Debug, Release non signé, Benchmark, Instrumented et APK de tests reconstruits ; `liboboe.so` vérifié pour chaque ABI de `libinterval_audio.so` |
| Tests instrumentés sur tablette | Réussi | SM-X620/API 36 : 6/6, 0 échec/ignoré, 13,612 s ; aperçu Synthé avant commit |
| Stream audio réel | Réussi sur le contrat testé | AUDIO-01 passe ses assertions sur 10 cycles ; dernière télémétrie détaillée archivée le 31 août sur le même appareil/binaire : 48 kHz, burst 96, buffer 192, file max 17, 0 drop/restart/erreur/xrun |
| Contrôle Synthé continu | Reçu | confirmation tactile et audible de l'utilisateur le 1er septembre ; aucune qualification anti-saturation générale n'en est déduite |
| Articulation/strummer sur tablette | Partiel réussi | trois modes et trois cordes accessibles ; `MUTED` déplace C4→D4 sans note active, strum sans déplacer D4 |
| Smoke benchmark minifié | Réussi sur le parcours | installation/mise à jour, cold start, trois modes + strummer visibles, 0 note active ; non AOT, aucune passe A/B |
| Qualité audio anti-saturation | Non exécutée | aucun test d'écoute comparative ni loopback sur le stream réel |
| MIDI USB, hotplug et Clock réels | Non exécutés | périphérie MIDI USB cible absente |

### Artefacts courants du lot

| Artefact | Taille | SHA-256 | Statut |
|---|---:|---|---|
| `app-debug.apk` | 40 140 987 octets | `8AD5EF7EFA34790C1358F3127AB4668AE4E09540DD375F660AA35A7CD41D459C` | assemblé, runtime natif vérifié |
| `app-release-unsigned.apk` | 9 025 500 octets | `06E642A65E31BBF332B3AC52AAE22EDEB90D11C92BD0BE0C1638A46BA53AD159` | assemblé, volontairement non signé |
| `app-benchmark.apk` | 9 033 732 octets | `06573B1D99573CF31DA9C5B24A9BD68B2511A68B15B5D5FD6117B9220EA6C5ED` | assemblé ; aucune nouvelle campagne A/B |
| `app-instrumented.apk` | 39 862 045 octets | `E8B5BE702CBE432C0E3C66B36DDA4120FFB979028D6BF8800D4EA73451C24B74` | installé pour les tests |
| `app-instrumented-androidTest.apk` | 2 508 467 octets | `21493BA4B430146B44AC36CA0F6E2DF1EA6DCEBE810E3B26EB8DFDF51E1BBE9F` | installé pour les tests |

La clôture est détaillée dans
[`implementation/HARDWARE_REPORT_2026-09-01.md`](implementation/HARDWARE_REPORT_2026-09-01.md) ;
la télémétrie détaillée précédente reste dans
[`implementation/HARDWARE_REPORT_2026-08-31.md`](implementation/HARDWARE_REPORT_2026-08-31.md).
Le smoke tablette du 22 août confirme les sémantiques radio, les cibles de mode/corde, une navigation
muette et le retour à zéro des notes actives après un balayage des trois cordes. Le cold
start observé une fois autour de 944 ms n'est pas un benchmark. La réception est détaillée
dans [`implementation/HARDWARE_REPORT_2026-08-22.md`](implementation/HARDWARE_REPORT_2026-08-22.md).

Un stress fonctionnel court ajoute douze taps alternés en `STACKED`, puis un strum aller
et retour : toutes les entrées sont acceptées, UI/processus restent vivants, le compteur
revient à zéro et le moniteur audio reste actif sans erreur visible. La campagne finale
ajoute le panneau Synthé et AUDIO-01 : chaque cycle envoie les 16 paramètres puis Panic,
et la file revient à zéro sans drop, reprise ou erreur. Un xrun au maximum est néanmoins
observé sous une autre charge AAudio concurrente ; cette mesure n'est donc pas une preuve
isolée de qualité, de latence ou d'absence de xrun.

Le gate du 22 août avait aussi reconstruit les variantes Release et Benchmark sans
rejouer la campagne A/B du 21 août. La revalidation du 31 août cible Debug et Instrumented
et ne produit donc aucune nouvelle preuve Release/AOT. Le cold start de 239 ms observé sur
le smoke antérieur reste illustratif. Les mesures de performance ci-dessous demeurent des
archives du gate précédent, pas des preuves de performance du code courant.

## Résultat synthétique du gate archivé du 21 août 2026

| Domaine | Résultat | Preuve ou commande |
|---|---|---|
| Domaine Kotlin pur | Réussi | `:domain:test` : 79/79 tests, 8 suites, 0 échec |
| Domaine sans SDK Android | Réussi | `verify-domain.ps1`, `kotlinc` absent et variables Android supprimées : build Gradle autonome de `domain/` vert |
| Tests JVM application | Réussi | `:app:testDebugUnitTest` : 109/109 tests, 16 suites, 0 échec |
| Total déterministe Kotlin/JVM | Réussi | 188/188 tests, 0 échec |
| DSP et pont JNI/Oboe hôte | Réussi | `scripts/verify-native.ps1` : 2/2 tests CTest |
| Lint Android | Réussi | debug, benchmark et instrumented : 0 issue ; `lintVitalRelease` : 0 issue |
| Assemblages Android | Réussi | debug, release unsigned, benchmark, instrumented et APK de test produits |
| Tests instrumentés sur tablette | Réussi | SM-X620 : 2/2, 0 échec |
| Porte logicielle de l'étape 3 | Réussi | gate Gradle final `BUILD SUCCESSFUL`, natif et instrumentation verts |
| Installation de mesure | Réussi | APK benchmark identifié, installé et compilé AOT `speed` sur SM-X620/API 36 |
| MIDI USB, hotplug et Clock réels | Non exécutés | Périphérie MIDI USB cible absente |
| UI tablette | Partiel | Paysage/portrait, police 1,3×, pad simple, Tone Row et presets observés ; vrai multi-touch et TalkBack non exécutés |
| Lifecycle et Audio Monitor | Partiel | Retour/relaunch et toggle audio sans crash, ANR ni erreur audio sur la session observée |
| Soak appareil | Partiel | 3 minutes, mémoire observée stable ; protocole de 60 minutes non exécuté |
| Fluidité UI à 90 Hz | Dette ouverte | A/B final : p50 18 ms, p90 22,5 ms, p95 23 ms, p99 26,5 ms dans les deux conditions ; jank strict >99 % |

Les deux tests instrumentés vérifient les neuf pads accessibles distincts d'au moins 72 dp
avec action sémantique et le maintien de la scène pondérée sous une timeline remplie. Ils
ne remplacent pas un vrai geste multi-touch ni un parcours TalkBack. Les suites historiques
des portes 1–2 restent incluses dans le gate de non-régression.

La réception du 21 août est détaillée dans
[`implementation/HARDWARE_REPORT_2026-08-21.md`](implementation/HARDWARE_REPORT_2026-08-21.md).

## Étape 3 intégrée et validée logiciellement

Les changements suivants sont présents dans le gate initial et son extension courante :

- acteur musical, horloge, gates et one-shots sur un dispatcher séquentiel hors Main ;
- effets temps réel émis avant capture durable, snapshots immuables confluentés et retry
  de persistance sur worker I/O séparé ;
- télémétrie MIDI de trafic échantillonnée et diagnostics audio hors de la mailbox,
  sans masquer topologie, erreurs ou overflow ;
- projections Compose étroites pour header, Tone Row, pads, ruban, statut, console et
  Synthé, avec patch/diagnostics audio et contenu/curseurs dissociés ;
- neuf pads dessinés avec cache mais conservés comme cibles focus/tactiles/sémantiques
  distinctes d'au moins 72 dp ;
- variante minifiée/profileable `benchmark` et variante non minifiée `instrumented`
  isolées entre elles et du package utilisateur ;
- cycle Oboe Exclusive→Shared, reprise hors callback, producteur SPSC sérialisé,
  générations, drain borné, Panic d'urgence sur overflow et ownership RAII des callbacks ;
- DSP C++20 avec oscillateurs PolyBLEP, huit voix, ADSR, filtre, chorus, delay,
  réverbération, limiteur, paramètres ciblés et assainissement des valeurs non finies.
- articulation des pads `ARPEGGIATED`/`STACKED`/`MUTED`, appliquée aussi aux gestes
  Record/Manual mais pas au Tone Row automatique historique ;
- strummer projeté depuis le voicing du domaine, avec sources one-shot, release ciblée,
  crossings intermédiaires, inversion, vélocité, multi-pointeur et hystérésis ;
- Settings v4, presets v3 et banque v2, avec migrations historiques préservant le rendu
  musical et initialisant un patch Synthé sûr ;
- contrat stable de 16 paramètres Synthé, patch persistant et panneau non modal avec
  timbre, filtre, ADSR, effets, master et diagnostics, fermé sous Performance Lock ;
- ADSR calibrée en temps-to-target à 44,1/48/96 kHz, mix d'oscillateurs normalisé,
  réverbération compensée et all-pass canoniques, limiteur identitaire sous `0.75`.

Les tests couvrent notamment Main/persistance/diagnostics bloqués, ordre d'un
one-shot, shutdown, reprise audio, retry du dernier snapshot, égalité des projections,
fallback Oboe, courses de lifecycle, callback tardif, générations, overflow, drain FIFO,
finitude DSP et reset Panic. Le gate du 22 août ajoute les matrices d'articulation,
l'ownership/invariance du strummer, ses gestes purs, les migrations jusqu'à Settings v4,
la sérialisation/persistance des 16 paramètres Synthé et les oracles de gain staging.
AUDIO-01 ouvre désormais le périphérique réel sur dix cycles et rejoue la sonde complète
si le compteur de reprise évolue entre deux échantillons, sans remplacer une écoute
qualifiée, une mesure de latence, un hotplug audio ou un soak.

## Artefacts Android archivés — gate du 21 août

| Artefact | Taille | SHA-256 | Statut |
|---|---:|---|---|
| `app-debug.apk` | 39 720 679 octets | `C46A4E11E3269DA968A5B6892155A13BD18577A61BAB5FDABBE964E5EE5956FD` | assemblé |
| `app-release-unsigned.apk` | 8 923 784 octets | `D67BCEC0B21ED3DE1D334E4AE2DB11123E0FD2952723F006D628AB34203132E0` | assemblé, non signé |
| `app-benchmark.apk` | 8 932 016 octets | `3F0E2B057159B6A26F7D1F2C434C6A3F9ACB4D0D6F82B8BF611ABF2C196E80C8` | installé et AOT `speed` |
| `app-instrumented.apk` | 39 458 061 octets | `041B1392337D28A1AEE84C8D51A51303F845439D13898134B59E68A326A6D7C7` | installé pour les tests |
| `app-instrumented-androidTest.apk` | 2 304 769 octets | `EED0885BD472F0FA3D5D0F5A77AF7B97F7015B5002AAA16E016F4512A717895F` | installé pour les tests |

## Environnement observé

- Windows PowerShell ;
- JDK 17 ;
- Android SDK Platform et Build Tools 36 ;
- NDK `28.2.13676358` ;
- CMake 3.22.1 et Ninja ;
- Gradle 8.13 et cache local au workspace.

Le diagnostic ne trouve pas de compilateur `kotlinc` autonome ; le domaine est donc
compilé par son build Gradle autonome, vérifié sans aucune variable SDK Android. Les
outils Android signalent aussi un décalage de version du format XML SDK
et l'impossibilité d'initialiser leurs métriques dans le répertoire global. Ces messages
sont environnementaux : le compilateur ne remonte aucun avertissement source dans le gate
final et Lint ne remonte aucune issue.

## Couverture logicielle

### Domaine intervallique et routage MIDI — porte 1

Les suites historiques restent dans le gate et couvrent notamment :

- mouvements `-14…+14`, zéro, Home, Undo, ancre externe, wrap/clamp et limites MIDI ;
- gammes, clés, plages, dix états d'accord, doublures, vélocités et omission hors plage ;
- polyphonie par origine, recomposition et Panic avec Note Off symétriques ;
- quatre modes de routage, mapping note/CC, seuils et gates ;
- leases conservant destination, canal, mode et compteur jusqu'au Note Off ;
- running status, fragmentation, temps réel imbriqué et SysEx 64 KiB borné ;
- générations de ports, pertes/reconnexions logiques, parser par connexion ;
- mailboxes MIDI In/Out bornées, latest-request-wins, overflow et drain FIFO à la fermeture ;
- purge ciblée et reset CC 123/120 sur les 16 canaux d'une destination réouverte.

### Tone Row et transport — porte 2

Les tests domaine et coordinateur couvrent :

- enregistrements complets de 5, 7 et 12 classes, fin précoce, plage étroite, vélocités
  et recherche directionnelle de la prochaine classe libre ;
- lecture manuelle par indices, Restart/Undo et feedback des sources tactiles/MIDI ;
- séquence de 1 à 64 mouvements, Prime, Retro, Random déterministe et Pendulum ;
- inversion, translation diatonique, transposition chromatique, octave et clamp ;
- Play Once exact, y compris Pause puis Restart ;
- ownership d'une voix automatique par source **et destination**, Note Off avant Note On,
  releases tardives inoffensives et historique borné sur lecture longue ;
- horloge interne sans rafale de rattrapage, MIDI Clock 24 PPQ, divisions, Start,
  Continue, Pause, Stop et sources exclusives ;
- gate MIDI calculé depuis la période positive observée des pulses, avec tempo local en
  repli avant la première mesure ;
- ordre byte-exact du temps réel avant ses effets musicaux et absence d'effet local en
  PassThru ;
- MIDI Start pendant Record, changements de grille pendant Record, Panic et changement
  de destination pendant une voix.

### Presets, acteur Android et UI

Les suites application couvrent :

- presets JSON v3, migrations v1/v2, corruption et bornes de payload ;
- Settings v4 et banque v2, avec inférence d'articulation et patch Synthé par défaut pour
  les formats historiques ;
- banque de 128 slots, mapping, contexte, routage, série, transformations et transport ;
- restauration systématique `Idle`/`Stopped` après Panic ;
- Program Change filtré par canal, Song Select global, slot absent non consommé et
  rappel désactivé en PassThru ;
- acteur `IntervalTabletViewModel` avec faux stockage, ports, audio et horloge :
  restauration, autosauvegarde d'une prise et du patch Synthé, rappel indépendant du
  patch, callback interne tardif et HostStop ;
- indépendance du chemin musical face à Main, stockage et diagnostics bloqués, one-shots
  ordonnés, shutdown gardé, reprise audio observée et retry de persistance confluentée ;
- registre multi-pointer, modèles Tone Row, curseurs, divisions, presets, adaptateur UI
  et égalité structurelle des projections Compose, dont patch et diagnostics audio.
- trois articulations, gestes Tone Row manuel/enregistrement muets, Auto inchangé,
  strummer à vélocité pleine sans navigation et release one-shot ciblée ;
- tracker pur couvrant crossings de bandes, changement de sens, vélocité secondaire,
  pointeurs indépendants et hystérésis de frontière.

Le build compile l'interface Compose complète : HUD de performance, grille multi-touch,
console MIDI, panneau Synthé et deck Tone Row avec timeline, transport, transformations,
séquence et presets. L'étape 3 remplace l'observation monolithique par des projections
dédiées et conserve chaque pad comme nœud accessible indépendant. Ces preuves logicielles
sont complétées par une réception visuelle et tactile partielle sur SM-X620 ; elles ne
prouvent toujours pas un vrai geste multi-touch simultané ni TalkBack.

### Réception de l'extension articulation et Synthé — 22 août

Le build `instrumented` courant et son APK de test sont réinstallés sans effacement, puis
les 6 tests instrumentés réussissent en 17,94 s. L'arbre d'accessibilité expose les trois
modes et, avec Octaves, trois cordes distinctes. Le panneau Synthé expose ses contrôles,
ne s'ouvre qu'après chargement des Settings, publie le patch en fin de geste et disparaît
sous Performance Lock. Sur le smoke physique, `MUTED` déplace C4 vers D4 sans dette de
note ; un balayage vertical des trois cordes laisse D4 inchangé et revient à zéro note
active. AUDIO-01 réalise dix cycles du stream avec 16 paramètres et Panic par cycle ; il
ne constitue ni une écoute, ni une mesure de latence, et sa valeur xrun est contaminée par
une autre charge AAudio concurrente. Voir le
[`rapport du 22 août`](implementation/HARDWARE_REPORT_2026-08-22.md).

### Réception Samsung SM-X620 — API 36

Les preuves sous
[`implementation/evidence/2026-08-21-sm-x620`](implementation/evidence/2026-08-21-sm-x620/)
et le rapport matériel associé établissent, sur les configurations capturées :

- installation, lancement, arrière-plan/retour et relance sans crash ni ANR ;
- activation/désactivation de l'Audio Monitor sans erreur audio signalée ;
- pression puis relâchement d'un pad avec retour du compteur actif à zéro ;
- enregistrement Tone Row, rangée affichée, Auto Play/Pause, Play Once et restauration ;
- sauvegarde, mutation puis rappel d'un preset local ;
- paysage, portrait et police à 1,3× ;
- correction du gonflement de la timeline et de son placement par rapport aux barres
  système, suivie d'un test instrumenté direct réussi ;
- soak court de trois minutes sans croissance mémoire observée sur cette fenêtre.

La baseline contrôlée antérieure au durcissement de l'étape 3 utilise un build Release AOT
à 120 BPM, division 6, rangée de 7 pas et lecture de 30 secondes. Avant isolation de la
timeline, elle donnait une durée de
frame p50 de 18 ms, p90 24 ms, 317/481 frames janky selon le seuil legacy (65,90 %) et
478/481 selon le seuil strict (99,38 %). Le dernier build de cette campagne historique
donne p50 16 ms, p90 24 ms,
p95 25 ms, p99 26 ms,
p50 GPU 6 ms et p90 GPU 7 ms ; le jank legacy descend à 304/481 (63,20 %), tandis que le
jank strict atteint 479/481 (99,58 %).

Le gain historique est donc de 2 ms sur la médiane de frame et de 2,70 points sur le taux legacy.
Il ne ferme pas la dette de performance à 90 Hz : le budget d'une frame est d'environ
11,11 ms, le p90 ne baisse pas et le taux strict ne s'améliore pas. Un démarrage froid
AOT à 216 ms a été observé une seule fois ; ce point isolé n'est pas interprété comme un
benchmark. Aucun crash, ANR ou message d'erreur audio n'a été observé pendant le soak de
trois minutes, mais cette durée reste insuffisante au regard du protocole de 60 minutes.

### Campagne A/B finale de l'étape 3

Le package isolé `dev.intervaltablet.benchmark`, SHA-256
`3F0E2B057159B6A26F7D1F2C434C6A3F9ACB4D0D6F82B8BF611ABF2C196E80C8`, est installé et
compilé AOT `speed`. Le scénario commun utilise la SM-X620 à 90 Hz, une rangée D4…C5 de
sept pas en Prime, 120 BPM, division 6 et gate 75 %. L'ordre contrebalancé est
OFF, ON, ON, OFF, ON, OFF, OFF, ON ; chaque passe comprend 10 s de warm-up puis 30 s de
mesure.

| Condition | Passes | Frames médiane (plage) | p50/p90/p95/p99 | GPU p50/p90 | Jank strict | Jank legacy |
|---|---:|---:|---:|---:|---:|---:|
| Audio OFF | 4 | 475 (474…476) | 18/22,5/23/26,5 ms | 6/7 ms | 99,475 % | 68,985 % |
| Audio ON | 4 | 476,5 (474…477) | 18/22,5/23/26,5 ms | 6/7 ms | 99,685 % | 75,155 % |

| Condition | UI | Traversal | Render | Issue | GPU | Frame completed |
|---|---:|---:|---:|---:|---:|---:|
| Audio OFF | 5,88 ms | 3,97 ms | 5,19 ms | 3,34 ms | 5,94 ms | 18,75 ms |
| Audio ON | 6,56 ms | 4,51 ms | 5,31 ms | 3,38 ms | 5,74 ms | 19,05 ms |

Les huit passes rapportent 0 `Missed Vsync`. Les distributions OFF/ON se recouvrent et
ne permettent pas d'attribuer un surcoût au DSP. La durée totale de frame reste toutefois
supérieur au budget 11,11 ms et le jank strict demeure ouvert. Le ring brut est limité aux
120 dernières frames, environ 7,5 s : les phases portent uniquement sur cette queue,
tandis que les métriques plateforme couvrent 30 s. `gfxinfo` ne mesure ni latence
tactile→audio, ni latence MIDI. Les dumps et résumés sont sous
[`implementation/evidence/2026-08-21-sm-x620/performance/stage3`](implementation/evidence/2026-08-21-sm-x620/performance/stage3/).
Stop puis Panic ont été exécutés après la campagne.

### DSP natif

La vérification hôte compile `AudioEngine.cpp` et `native_bridge.cpp` avec les primitives
DSP contre des stubs JNI/Oboe étroits. L'étape 3 exerce séparément le DSP et le cycle
AudioEngine/JNI : fallback open/start, reprise, ownership callback, intention de lifecycle,
générations, overflow, drain borné, finitude, ciblage de coefficients, reset O(1) et Panic.
Les 2/2 tests réussissent. Cette preuve détecte les régressions C++ principales sans
reproduire l'ABI, le scheduler ou le périphérique audio Android réel.

L'extension conserve les 2/2 suites et porte la compilation stricte à huit unités
vérifiées. Les oracles ajoutés contrôlent les durées ADSR à 44,1/48/96 kHz, l'identité et
la monotonie du limiteur, la normalisation des oscillateurs/comb filters, l'énergie et la
magnitude des all-pass, le niveau pré-limiteur en polyphonie nominale et l'extinction des
releases rapides. Ils prouvent le calcul C++ hôte, pas la perception du signal Android.

Sur SM-X620, la revalidation du 31 août complète cette preuve par dix
ouvertures/fermetures : 48 kHz, 96 frames/burst, buffer 192, file maximale 17 après
l'envoi des 16 paramètres et de Panic, 0 événement perdu, 0 reprise, 0 code d'erreur et
0 xrun. La campagne du 22 août avait observé burst 192, buffer 384 et au plus un xrun sous
une autre charge AAudio. Ces fenêtres nominales ne qualifient ni la latence, ni le hotplug,
ni la stabilité sur 60 minutes.

## Vérifications matérielles restantes

Suivre `docs/HARDWARE_TEST_PROTOCOL.md` et stocker les preuves sous
`docs/implementation/evidence/`. Restent indispensables :

1. découvrir, sélectionner et reconnecter des ports MIDI USB réels ;
2. confirmer l'absence de note bloquée sur changement de mode, port, hotplug et Panic ;
3. recevoir Clock/Start/Continue/Stop et Program/Song Select depuis un contrôleur réel ;
4. capturer un vrai geste multi-touch simultané et le retour de toutes les sources à zéro ;
5. exécuter TalkBack, ordre de focus et mesure de contraste ;
6. exécuter le soak audio complet de 60 minutes avec mémoire, xruns, erreurs et reprise.
7. écouter à niveau contrôlé les cas arpège/accord/strum et, si possible, capturer un
   loopback pour confirmer l'absence de saturation sur le stream réel.
8. mesurer la latence tactile/MIDI→audio et exercer le hotplug/changement de route audio
   sans charge AAudio concurrente.

La réception partielle ne ferme aucun indicateur global MIDI USB, écoute comparative ou
soak audio. `.codex/state.json` clôt néanmoins le MVP logiciel en `complete`, avec appareil
Android et contrôle Synthé continu reçus, mais USB MIDI, écoute audio comparative et soak
audio toujours faux.

## État du workspace

Le workspace est initialisé comme dépôt Git local sur `main`. Le Wrapper Gradle officiel
8.13 est présent et son JAR correspond au SHA-256 attendu. Aucun remote, push ou
publication n'a été configuré ; aucun état distant n'est revendiqué.

## Limite d'interprétation

Les résultats démontrent la cohérence déterministe des portes 1–3, la compilation Android,
les quatre Lint, le build natif hôte, les artefacts identifiés, six tests instrumentés courants,
dix cycles courts du stream réel et les huit passes A/B archivées sur SM-X620. Ils ne
démontrent pas le comportement MIDI USB, le vrai multi-touch, TalkBack, une fluidité
acceptable à 90 Hz, une latence audio/MIDI absolue, le hotplug audio, ni la qualité audio
subjective du correctif anti-saturation, ni la stabilité audio sur une session de
60 minutes. La charge AAudio concurrente interdit en particulier de surqualifier le xrun
maximal observé.
