# Spécification du moteur audio minimal

## Rôle

Le synthé interne sert de retour sonore autonome et de référence de test. Il ne doit ni
conditionner le MIDI, ni devenir le centre architectural du produit. Voix, oscillateurs
PolyBLEP, enveloppes, filtres, effets et limiteur sont traités en C++20. Kotlin/JNI ne font
qu'enfiler des événements compacts, hors du callback audio.

## Flux

```text
événements → allocateur 8 voix → oscillateurs → ADSR → filtre par voix
          → sommation stéréo → chorus → delay → réverbération → limiteur doux
```

## Voix

- Polyphonie maximale : 8 voix.
- Allocation : voix libre, sinon voix en release la plus ancienne, sinon voix active la plus ancienne.
- Retrigger configurable ; MVP : retrigger de l’enveloppe sur chaque Note On.
- Oscillateurs : saw, pulse et triangle, mixables.
- Pulse width bornée pour éviter les impulsions dégénérées.
- Enveloppe ADSR exponentielle ou quasi-exponentielle, temps bornés. Attack, Decay et
  Release sont des temps-to-target réels : chaque phase atteint son seuil numérique à la
  durée demandée, indépendamment de 44,1, 48 ou 96 kHz.
- Filtre passe-bas résonant stable, fréquence bornée sous Nyquist.
- Vélocité appliquée au gain de voix.

Saw et pulse emploient PolyBLEP afin de réduire l'aliasing ; le triangle est une onde
triangulaire directe, centrée et bornée, dont la phase partage celle des autres formes.

## Effets

### Chorus

- Ligne à retard modulée stéréo, LFO lent et phases différentes gauche/droite.
- Profondeur limitée pour éviter la lecture hors buffer.
- Interpolation linéaire minimale.
- Le LFO utilise une récurrence sinus/cosinus renormalisée périodiquement plutôt que deux
  appels trigonométriques par échantillon.

### Delay

- Retard stéréo avec feedback borné `<0.95`.
- Temps de delay modifiable avec lissage ou crossfade afin d’éviter les sauts violents.
- Dry/wet borné.
- Les index circulaires utilisent un wrap par branche, sans modulo entier par échantillon.

### Réverbération

- Réverbération algorithmique légère de type Schroeder/FDN réduite.
- Buffers alloués dans `prepare`, jamais dans `process`.
- Feedback et damping bornés pour garantir la stabilité.
- Entrée stéréo rabattue par moyenne, send compensé par `1-feedback`, puis moyenne des
  quatre combs parallèles avant deux all-pass canoniques par canal.
- Les all-pass utilisent la forme de Schroeder à magnitude unitaire ; ils changent la
  phase sans multiplier l'énergie de la tail.

### Limiteur

- Saturation douce sans lookahead au MVP, exactement identitaire jusqu'au knee `0.75`.
- Au-delà du knee, transition rationnelle continue, monotone et asymptotique vers
  `[-1, 1]`; le limiteur ne colore donc pas le signal nominal sous seuil.
- Sortie bornée et absence de NaN/Inf après chaque bloc.

## Gain staging anti-saturation

- Les trois mix d'oscillateurs sont divisés par `max(1, somme)` : les valeurs par défaut
  restent inchangées et une combinaison supérieure à l'unité est normalisée
  proportionnellement.
- La somme des huit voix conserve son trim interne existant ; le master par défaut n'est
  pas abaissé pour masquer un excès en amont.
- La réverbération normalise son réseau interne avant le mix dry/wet ; son feedback reste
  une durée de décroissance, pas un gain implicite de banque parallèle.
- Le limiteur est le dernier filet de sécurité. Une polyphonie nominale de deux accords
  à trois voix doit rester sous son knee dans le test de référence.

Ces garanties sont numériques. Elles ne qualifient pas à elles seules le haut-parleur,
le mixer Android, le stream Oboe réel ou la perception de saturation : une écoute et, si
possible, un loopback mesuré sur tablette restent obligatoires.

## Contraintes temps réel

Dans `onAudioReady` :

- aucune allocation/libération ;
- aucun mutex, attente, sleep ou appel système bloquant ;
- aucune journalisation ;
- aucun accès DataStore/Compose/Android MIDI ;
- aucun appel JNI ;
- complexité bornée par nombre de frames et nombre maximal de voix.

Les événements arrivent via une file SPSC préallouée de 1024 cases. Le callback en draine au
plus 128 par bloc afin de conserver un coût borné. Chaque événement porte la génération du
stream : un événement ancien est neutralisé, et une course de génération ou un overflow arme
un Panic d'urgence consommé au début du bloc suivant.

`Panic` est O(1) dans le callback. Les grandes lignes à retard conservent leur stockage
préalloué, mais remettent index et fenêtre de validité à zéro ; toute case non réécrite est
lue comme un zéro. Les anciennes tails ne peuvent donc ni coûter un grand `memset`, ni
réapparaître après un tour de buffer.

Les 128 fréquences MIDI sont précalculées dans `prepare`. `NoteOn` ne recalcule ni ADSR ni
filtre. Les paramètres identiques sont ignorés et chaque famille ne touche que le
sous-système concerné. Les mix saw/pulse/triangle, la pulse width, le sustain, les trois
coefficients du filtre, les paramètres d'effets et le master convergent par lissage par
échantillon. Le lisseur rejoint exactement sa cible sous un écart de `1e-4` afin d'éviter
une traîne subnormale ; un changement de contrôle ne crée donc ni saut de valeur durable,
ni calcul non borné dans le callback.

## Configuration de stream

- Oboe, sortie stéréo float.
- Demande Low Latency et tentative Exclusive complète (open + start), puis tentative Shared
  complète si l'ouverture ou le démarrage Exclusive échoue.
- Sample rate négocié avec le périphérique, sans forcer 48 kHz ; tout le DSP est préparé à
  la valeur réellement ouverte.
- Buffer cible de deux bursts ; sample rate, taille de burst, profondeur courante/maximale de
  la SPSC et nombre de xruns sont exposés au diagnostic.
- Reprise contrôlée après erreur de stream, hors callback, avec backoff et génération. Une
  intention Stop est revérifiée avant `requestStart`, et les callbacks d'un ancien stream sont
  rejetés.
- À chaque transition observée vers un stream de nouveau actif, l'adaptateur rejoue le
  `SynthPatch` complet dans l'ordre wire. Il le rejoue aussi si `restartCount` augmente entre
  deux diagnostics sans qu'un échantillon intermédiaire à l'arrêt ait été observé. Si un des
  28 événements est refusé, il envoie Panic, arrête le moniteur et signale l'échec plutôt que
  de conserver silencieusement un patch partiel.
- Le handle JNI possède un `shared_ptr<AudioEngine>` ; Oboe reçoit des callbacks possédés par
  `shared_ptr`. `shutdown()` est terminal et idempotent, joint le worker puis brise le cycle de
  possession seulement après le retour effectif des callbacks.

## Paramètres du patch courant

`SynthParameter` constitue le contrat typé et stable entre domaine, persistance, UI, JNI
et `ParameterId` natif. Chaque commande transporte un identifiant wire `0…27` et une valeur
finie dans la borne canonique ci-dessous ; `SynthPatch` émet toujours les 28 commandes par
identifiant croissant. Le cutoff durable/UI est borné à `20…20 000 Hz`, puis le rendu natif
applique en plus le plafond sûr dépendant du stream, `0,45 × sampleRate`.

| ID | Paramètre | Borne canonique | Valeur initiale |
|---:|---|---:|---:|
| 0 | Saw mix | 0…1 | 0.65 |
| 1 | Pulse mix | 0…1 | 0.20 |
| 2 | Triangle mix | 0…1 | 0.15 |
| 3 | Pulse width | 0.05…0.95 | 0.50 |
| 4 | Attack | 0.5 ms…10 s | 5 ms |
| 5 | Decay | 1 ms…20 s | 180 ms |
| 6 | Sustain | 0…1 | 0.70 |
| 7 | Release | 1 ms…30 s | 350 ms |
| 8 | Cutoff | 20…20 000 Hz | 3.5 kHz |
| 9 | Resonance | 0…1 | 0.15 |
| 10 | Chorus mix | 0…1 | 0.18 |
| 11 | Delay time | 10 ms…2 s | 320 ms |
| 12 | Delay feedback | 0…0.94 | 0.28 |
| 13 | Delay mix | 0…1 | 0.16 |
| 14 | Reverb mix | 0…1 | 0.20 |
| 15 | Master, gain linéaire | 0…1.5 | 0.35, environ -9 dB |
| 16 | Filter attack | 0.5 ms…10 s | 5 ms |
| 17 | Filter decay | 1 ms…20 s | 180 ms |
| 18 | Filter sustain | 0…1 | 0 |
| 19 | Filter release | 1 ms…30 s | 350 ms |
| 20 | Filter envelope amount | -4…4 octaves | 0 |
| 21 | Output drive | 0…1 | 0 |
| 22 | LFO rate | 0.05…20 Hz | 2 Hz |
| 23 | LFO depth | 0…1 | 0 |
| 24 | LFO destination | 0 Filter, 1 Pulse width, 2 Delay | 0 |
| 25 | LFO delay | 0…10 s | 0 |
| 26 | Delay sync beats | 0 libre, puis 0…4 noires | 0 |
| 27 | Tempo | 20…300 BPM | 120 |

Le drive est appliqué avant la limitation de sortie. Le LFO démarre avec un fondu
d'entrée et module une seule destination. Lorsque Delay sync beats est nul, Delay time
reste autoritaire ; sinon la durée provient du tempo et du nombre de noires.

## Tests

- silence avant événement et après extinction complète ;
- fréquence correcte à tolérance définie ;
- enveloppe finie et voix recyclable ;
- seuils temporels ADSR à 44,1/48/96 kHz ;
- pas de NaN/Inf sur plusieurs millions d’échantillons ;
- feedback stable aux valeurs maximales autorisées ;
- transparence exacte du limiteur sous `0.75`, continuité/monotonie au-dessus ;
- magnitude des all-pass, gain de réverbération normalisé et mix d'oscillateurs borné ;
- polyphonie nominale sous le knee et release d'arpège ne saturant pas les huit voix ;
- file pleine sans corruption ;
- ordre Note On/Off/Panic ;
- repli Shared après échec d'ouverture ou de démarrage Exclusive ;
- callback synchrone correctement armé et sample rate négocié ;
- overflow vers Panic/silence, profondeur de file et drain FIFO limité à 128 ;
- stop/restart sans événement résiduel, reprise après déconnexion et rejet d'ancienne génération ;
- destruction concurrente avec callback d'erreur encore en vol ;
- reset O(1), extinction exacte des tails et absence de résurgence après wrap ;
- contrat wire des 28 paramètres, bornes, ordre, défauts et rejet des non-finis ;
- ciblage et convergence avec snap des mix, pulse width, sustain, coefficients de filtre,
  effets et master, ainsi que budget répété de Panic ;
- rejeu du patch après reprise et arrêt sûr si le rejeu complet est refusé ;
- AUDIO-01 sur appareil réel : dix cycles start/stop avec diagnostics négociés ;
- test de durée prolongée sur appareil à l’étape 3.

La campagne AUDIO-01 sur SM-X620/API 36 a terminé ses dix cycles à 48 kHz, avec un burst
de 192 frames, un buffer de 384 frames et une profondeur maximale de file de 17. Aucun
drop, restart ou code d'erreur n'a été relevé ; le maximum observé est un xrun. Cette
preuve qualifie le cycle nominal et les diagnostics, pas l'écoute, la latence loopback,
le soak, ni le hotplug audio.
