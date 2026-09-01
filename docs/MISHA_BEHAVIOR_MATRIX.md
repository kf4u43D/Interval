# Matrice de fidélité comportementale

Référence : guide utilisateur Misha v1.1.6, fonctions publiques uniquement. Cette matrice décrit l’état réellement prouvé du projet, sans assimiler un test JVM à une validation sur tablette.

- **Validé logiciel** : comportement déterministe couvert par les tests du workspace.
- **Reçu partiellement sur tablette** : un parcours réel est prouvé sur SM-X620, mais le
  cas normatif complet ou la matrice matérielle reste ouvert.
- **Validé logiciel; matériel requis** : comportement couvert hors appareil, mais écoute,
  périphérique ou protocole physique encore nécessaire.
- **Implémenté, matériel requis** : chemin logiciel présent, mais réception tablette/USB encore ouverte.
- **Validé logiciel; UI tablette reçue; USB requis** : reducer, intégration et panneau
  validés, parcours UI reçu sur tablette, mais contrôleur MIDI USB réel encore absent.
- **Non attribuable sur cette campagne** : les distributions mesurées se recouvrent et
  ne permettent pas d'isoler un effet causal.
- **Socle seulement** : structure existante, insuffisante pour accepter l’étape.
- **Non commencé** : comportement réservé à une étape ultérieure.

| Domaine | Comportement cible | Étape | Statut actuel | Preuve ou limite |
|---|---|---:|---|---|
| Jeu | intervalles tactiles `-4…+4` par degrés | 1 | Validé logiciel | `PitchGridTest` couvre `-14…+14`; `MidiMappingTest` vérifie les neuf bindings; le coordinateur partage le reducer tactile/MIDI |
| Jeu | `0` rejoue sans historique distinct | 1 | Validé logiciel | `zeroRetriggersWithoutGrowingHistory` et cas d’ancre externe |
| Jeu | Undo revient au dernier mouvement distinct | 1 | Validé logiciel | historique non tronqué, répétitions et zéro couverts par `IntervalReducerTest` |
| Jeu | Home choisit la fondamentale du registre central | 1 | Validé logiciel | toutes les gammes/racines et plages testées dans `PitchGridTest` |
| Jeu | Same Interval distinct de Same Pitch | 4 | Validé logiciel | oracles du pas diatonique répété, de l'ancre externe et du delta chromatique entendu, y compris composition d'un Shift stable/ajouté/retiré ; gate V2 291/291 |
| Jeu | Random Interval immédiat et déterministe | 4 | Validé logiciel | action instrument `-14…+14` distincte du mode Random Tone Row ; séries de tirages reproductibles à graine explicite et graine différente divergente |
| Jeu | Chromatic Shift momentané par source | 4 | Validé logiciel | modificateur silencieux additionnable, retiré par Release/purge/Panic ; Note et CC empilés survivent au remplacement du mapping jusqu'à leurs releases d'origine |
| Jeu | changement de clé/gamme 12-TET | 1–5 | Validé logiciel | oracle exhaustif de grille et reconfiguration avec libération des voix ; treize gammes standards disponibles en V2.1 |
| Jeu | Force to Scale sur les notes générées | 5 | Validé logiciel; tablette reçue | quantification chromatique/accord/Shift, égalité vers le bas, désactivation et releases couverts ; contrôle présent sur la scène SM-X620, PassThru inchangé |
| Range | wrap/clamp configurable | 1 | Validé logiciel | limites haut/bas et indicateurs `NONE`/`CLAMPED`/`WRAPPED` testés |
| Chords | neuf voicings et Off, trois notes maximum | 1 | Validé logiciel | les dix définitions, vélocités, doublures, ordre et omissions hors plage sont testés |
| Jeu | articulation pads lead/plaqué/muet | 3 | Validé logiciel; tablette reçue partiellement | `PadArticulationTest` couvre les dix accords, ranges, doublures, re-press, changement de mode et Panic ; les trois sélecteurs et une navigation muette ont été observés sur tablette, sans capture MIDI USB |
| Jeu | strummer égrenant le voicing sans navigation | 3 | Validé logiciel; tablette reçue partiellement | projection issue de `strumNotes()`, one-shots/release et invariance de navigation couverts ; crossings, inversion, vélocité, multi-pointeur et hystérésis testés, balayage à un pointeur observé sur tablette |
| Polyphonie | sources simultanées indépendantes | 1 | Validé logiciel; tablette requise | `activeBySource` et registre de pointeurs testés; geste multi-touch physique non reçu |
| MIDI | parsing Note/CC/Program/Pitch Bend/Aftertouch et temps réel | 1 | Validé logiciel | fragmentation octet par octet, running status, temps réel imbriqué et encodage couverts dans `MidiMessageParserTest` |
| MIDI | SysEx borné et brut uniquement en PassThru | 1 | Validé logiciel | fragmentation, dépassement 64 KiB, récupération et route PassThru testés |
| MIDI | mapping notes/CC et commandes de performance | 1–4 | Validé logiciel | priorité canal exact/Omni, sérialisation versionnée, seuils et leases historiques ; Random joue l'intervalle, Shift suit sa gate et Play/Stop/Record rejoignent Tone Row |
| MIDI | éditeur MIDI Learn transactionnel | 4 | Validé logiciel; UI tablette reçue; USB requis | reducer baseline/brouillon/candidat/conflit, capture avant rappel/routage, Save atomique et Cancel sans persistance ; conflit→Replace→Save/Cancel reçu dans la suite tablette 7/7, capture contrôleur USB ouverte |
| MIDI | CC mappé actif au seuil, 64 par défaut | 1 | Validé logiciel | seuils 63/64 et surcharges par binding couverts |
| MIDI | Off/Active/Active Last Note/PassThru | 1 | Validé logiciel | routes mappées, transmises ou supprimées et ancre externe couvertes ; l’exception normative Panic/Toggle en PassThru est testée |
| MIDI | messages non mappés transmis en Active | 1 | Validé logiciel | octets et ordre global des effets vérifiés par routeur/coordinateur |
| MIDI | Note Off suit la route du Note On malgré un changement de mode | 1 | Validé logiciel | toutes les transitions de modes avec notes tenues sont testées ; MIDI-USB-04 est aligné sur cette règle |
| MIDI | Panic/All Notes Off | 1 | Validé logiciel | leases et instances vidés; Note Off avant CC 123 puis CC 120 |
| MIDI | sélection, perte, reconnexion et générations de ports | 1 | Implémenté, matériel requis | contrat du faux repository et reset de parseur testés; MIDI-USB-01/04/05 restent à exécuter |
| Robustesse MIDI | purge source/destination, changement de port et saturation sans note bloquée | 1 | Validé logiciel; USB requis | purge ciblée, reset 16 canaux à l’ouverture et mailbox Out bornée couverts ; débranchement physique non reçu |
| UX | paysage prioritaire, disposition compacte adaptative | 1 | Reçu partiellement sur tablette | portrait/paysage et police 1,3× observés sur SM-X620/API 36 ; correction timeline/barres système validée visuellement et par 1 test instrumenté direct |
| UX | cibles compactes, multi-touch, clavier et sémantiques | 1–5 | Reçu partiellement sur tablette | pression simple reçue, cibles ≥48 dp et registre multi-pointeur testés ; dix accords affichés en deux rangées, vrai multi-touch, TalkBack et contraste restent à recevoir |
| UX | Console MIDI non modale et Performance Lock | 1–4 | Validé logiciel; UI tablette reçue; USB requis | ports, canaux, mode, mapping, plage, gamme, clé et accord câblés ; panneau Learn et flux Save/Cancel reçus, sélection de ports USB réels ouverte |
| Tone Row | record, fin précoce et classes uniques | 2 | Validé logiciel | `ToneRowReducerTest` couvre effacement de l'ancienne prise, fin vide/non vide, recherche directionnelle et vélocités |
| Tone Row | second Record annule la prise | 4 | Validé logiciel | `CancelRecording` revient à `Idle`, abandonne les entrées et couvre les commandes mappées/directes dans le gate combiné |
| Tone Row | fin lorsque la gamme est épuisée | 2 | Validé logiciel | fixtures complètes 5/7/12 et plage étroite ; la capacité suit les classes réellement accessibles, avec bascule automatique en lecture manuelle |
| Tone Row | boutons deviennent déplacements d’indice | 2 | Validé logiciel | wrap d'indices, `0`, Restart et Undo réinterprété couverts ; pads tactiles et mappings utilisent le même coordinateur |
| Tone Row | navigation en Pause et vélocité MIDI live | 4 | Validé logiciel | ManualMove accepte `Paused`; Continue reprend la position et l'override de vélocité est réservé à la Note MIDI sans altérer la rangée persistée |
| Auto | séquence initiale `{+1}` et édition live | 2 | Validé logiciel | curseur indépendant, ajout/suppression/sélection, borne 64 et dernier pas rétabli à `{+1}` |
| Auto | huit parcours Tone Row | 2–4 | Validé logiciel | Prime/Retro/Random/Pendulum plus Auto-Transpose haut/bas et Auto-Translate haut/bas ; cycles, Pause/Continue, Restart/Reset et Play Once couverts |
| Auto | Random signé | 4 | Validé logiciel | départ au premier élément ; chaque pas conserve son signe et tire une magnitude `0…2×|pas|` avec graine explicite reproductible |
| Auto | inversion, translate, transpose, octave | 2 | Validé logiciel | ordre inversion→translation diatonique→projection→transposition chromatique→octave→clamp et reset testé |
| Auto | Play Once sur une traversée | 2–4 | Validé logiciel | une taille de rangée exactement pour les huit modes ; le cycle Auto terminal accumule ±1 avant le retour manuel, compteur remis à zéro |
| Clock | horloge interne et MIDI Clock 24 PPQ | 2 | Validé logiciel; matériel requis | `TransportReducerTest` couvre sources exclusives, divisions, tempo, timestamps et ticks ; jitter USB/Android non mesuré |
| Clock | Start/Stop/Continue/Pause | 2 | Validé logiciel; matériel requis | Start remet au début ; MIDI Stop libère et pause en conservant la position ; Continue attend le prochain tick ; réception USB ouverte |
| Clock | Clock/transport MIDI sortants et Song Position Pointer | après V2 | Différé | seule la réception 24 PPQ/transport est couverte ; aucune émission ou SPP n'est revendiquée |
| Presets | persistance versionnée de session/Tone Row | 2–5 | Validé logiciel | Settings v5 porte patch global et Force to Scale ; presets v4 et banque v3 restent musicaux ; migrations historiques, limites/corruption et restauration sûre `Idle`/`Stopped` couvertes |
| Presets | Program Change et Song Select | 2 | Validé logiciel; matériel requis | slots `0…127`, PC filtré par canal, Song global, slot absent non consommé et aucun rappel en PassThru ; le patch synthé global est préservé ; contrôleur réel non reçu |
| UX | timeline, curseurs, transport et transformations Tone Row | 2–4 | Reçu partiellement sur tablette | états Tone Row, presets et timeline observés sur SM-X620 ; instrumentation finale 7/7 couvre aussi MIDI Learn ; vrai multi-touch et TalkBack restent ouverts |
| Performance | animation et rendu soutenus à 90 Hz | 2–3 | Dette ouverte | huit passes A/B : OFF et ON ont les mêmes médianes plateforme p50 18/p90 22,5/p95 23/p99 26,5 ms, GPU 6/7 ms ; jank strict 99,475/99,685 %, donc durée totale de frame >11,11 ms toujours ouverte |
| Performance | impact du moniteur audio sur le rendu UI | 3 | Non attribuable sur cette campagne | les distributions OFF/ON se recouvrent et `gfxinfo` ne permet pas d'attribuer un surcoût DSP ; phases limitées aux 120 dernières frames (~7,5 s), métriques plateforme sur 30 s, aucune latence audio/MIDI mesurée |
| Robustesse UI | acteur musical indépendant du rendu et de l'I/O | 3 | Validé logiciel | acteur, horloge, gates et one-shots hors Main ; blocage de Main, persistance ou diagnostics couvert sans arrêter le chemin musical, avec retry du dernier snapshot durable |
| Audio | synthèse huit voix | 3 | Validé logiciel; reçu nominalement sur tablette | allocation/stealing, oscillateurs PolyBLEP, table MIDI, mix/pulse width lissés et reset Panic couverts ; 10/10 cycles de stream réel passent après contrôle du runtime Oboe dans l'APK, mais écoute/loopback restent ouverts |
| Audio | contrôle synthé et diagnostics | 3–5 | Validé logiciel; reçu sur tablette | panneau non modal : timbre, cutoff 20 Hz–20 kHz, résonance, ADSR, chorus, temps/feedback/mix du delay, reverb/master et diagnostics ; aperçu continu et commit couverts ; patch rejoué au start/recovery |
| Audio | filtre/ADSR | 3 | Validé logiciel; matériel requis | coefficients précalculés, sustain/filtre lissés et durées time-to-target à 44,1/48/96 kHz, mises à jour ciblées, valeurs non finies et préparation multi-sample-rate couvertes ; qualification sonore requise |
| Audio | chorus/delay/reverb | 3 | Validé logiciel; matériel requis | chorus à récurrence trigonométrique, lignes circulaires, reset O(1), reverb à send/comb normalisés et all-pass canoniques testés en énergie/magnitude ; écoute/xruns/soak non reçus |
| Audio | gain staging et limiteur anti-saturation | 3 | Validé logiciel; écoute/loopback requis | mix d'oscillateurs normalisé, polyphonie nominale mesurée sous le knee et limiteur identitaire sous `0.75`, continu/monotone/borné au-dessus ; disparition subjective de la saturation non encore qualifiée |
| Audio | callback temps réel | 3 | Validé logiciel; reçu nominalement sur tablette | drain SPSC borné, Panic d'urgence, générations, ownership partagé et aucune allocation/verrou/I/O/log/JNI ; les 16 IDs + Panic sont drainés sur 10 cycles réels, queue max 17 et zéro drop/xrun dans la revalidation |
| Robustesse générale | lifecycle prolongé, reprise audio et soak | 3–5 | V2.1 logicielle terminée; certification partielle | gate final 297/297 JVM, 2/2 natifs, quatre Lint, tous les assemblages et 7/7 instrumentés en 17,279 s ; AUDIO-01 nominal passe ; hotplug physique et protocole 60 min non exécutés |
| Séquence | Rest, Random Step et Ratchet typés | après V2 | Différé | aucun encodage par valeur sentinelle ; Ratchet attend un scheduler de retrigger annulable par génération |
| Mapping | gamme/clé/accord/preset, CC relatifs/continus, profils/import-export | après V2 | Différé | le schéma V2 conserve le catalogue d'actions existant et Mapping v1 |

## Écarts assumés après la V2.1

- Aucun CV ou audio multicanal.
- Aucune microtonalité ni import Scala.
- Les 200 slots de gammes et les banques du matériel ne sont pas reproduits ; treize
  gammes standards 12-TET et presets utilisateur sont fournis.
- L’UI est originale et adaptée à la tablette.
- Les entrées physiques CV/gate, clavier USB d’ordinateur et fonctions de calibration matérielle ne sont pas reproduites.
- Rest, Random Step, Ratchet, Clock/transport MIDI sortants et SPP sont reportés après la V2.
- L'extension des actions mappées, les gammes personnalisées et les scopes de presets sont reportés ; les
  presets déjà sauvegardés ne reçoivent un nouveau mapping qu'après resauvegarde du slot.
- Le budget soutenu de 11,11 ms à 90 Hz, USB MIDI/Learn, TalkBack, vrai multi-touch,
  loopback et soak restent des validations de performance ou matérielles ouvertes.
