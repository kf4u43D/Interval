# État d'implémentation

## Étape 1 — Instrument intervallique MIDI

**Porte logicielle terminée le 20 août 2026 ; réception tablette partielle le 21 août
2026, sans fermeture de la validation matérielle.**

Le lot constitue désormais une tranche verticale jouable dans l'application : les gestes
tactiles et messages MIDI mappés rejoignent la même machine d'état déterministe, puis les
événements ordonnés alimentent la sortie MIDI et, lorsque demandé, le moniteur audio.

### Livré

- [x] Navigation diatonique `-14…+14`, neuf pads `-4…+4`, zéro, Home, Undo,
  wrap/clamp, ancre externe, clé, gamme et plage MIDI.
- [x] Off et neuf voicings d'accord, avec doublures, vélocités, limites et polyphonie par
  origine.
- [x] Mapping note/CC, seuil et gate CC, quatre modes Off/Active/Active Last Note/PassThru
  et leases de routage conservés jusqu'au Note Off.
- [x] Parseur MIDI incrémental borné, running status, temps réel imbriqué, messages de
  canal, SysEx borné et réinitialisation par génération de connexion.
- [x] Repository Android MIDI testable : découverte, ouverture/fermeture, sélection,
  générations de ports, hotplug logique, mailbox Out bornée et remontée d'erreurs.
- [x] Coordinateur applicatif sérialisé, récupération conservatrice sur saturation,
  cycle de vie, changements de port et Panic ordonné avant fermeture.
- [x] Réglages versionnés pour contexte musical, ports descriptifs, canaux, routage,
  mapping et Performance Lock.
- [x] UI Compose originale et adaptative : HUD scénique, grille multi-touch, cibles,
  utilitaires permanents et console MIDI non modale accessible.
- [x] Façade audio existante conservée comme moniteur secondaire ; MIDI ne dépend pas de
  son état.
- [x] Lanceurs et configuration Windows rendus utilisables avec un cache Gradle situé
  dans le workspace.

### Preuves historiques de clôture de la porte 1

- Gate `:domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` réussi.
- Domaine : 41 tests réussis sur 41.
- Application : 42 tests JVM réussis sur 42.
- Lint : 0 erreur et 0 avertissement (`No issues found.`).
- APK debug produit (38 768 047 octets) et reconstruit après finalisation de l'icône.
- DSP/JNI hôte : 1 test CTest réussi sur 1 lors de la vérification native.

Les détails, commandes et limites sont consignés dans `docs/VERIFICATION_REPORT.md` et
`docs/implementation/STAGE_1_PLAN.md`.

## Étape 2 — Tone Row, transport et presets

**Porte logicielle terminée le 20 août 2026 ; réception tablette partielle le 21 août
2026, sans fermeture de la validation matérielle.**

### Livré

- [x] Reducer Tone Row pur avec états `Idle`, `Recording`, `ManualPlayback`,
  `AutoPlaying` et `Paused`; prises 5/7/12 sans classe répétée et fin précoce/complète.
- [x] Lecture manuelle par indices, `0`, Restart/Undo et lecture automatique avec
  séquence éditable non vide, bornée à 64 mouvements.
- [x] Prime, Retro, Random déterministe, Pendulum sans double extrémité, inversion,
  translation diatonique, transposition chromatique, octave et reset.
- [x] Play Once d'une longueur exacte, ownership par origine système, gate planifiée et
  libération ordonnée avant note suivante, Pause, Stop ou Panic.
- [x] Reducer de transport avec horloge interne, MIDI Clock 24 PPQ, divisions, tempo,
  durée de note, sources exclusives et timestamps injectés.
- [x] Start revient au début. MIDI Stop libère puis place Tone Row en pause sans perdre
  la position ; Continue reprend sans note fantôme avant le tick suivant. Le Stop local
  revient à `Idle` et arrête le transport.
- [x] Session de travail et banque interne de 128 presets, schémas Settings/Preset v2,
  migration v1, validation bornée et restauration sûre `Idle`/`Stopped` après Panic.
- [x] Politique MIDI documentée : Program Change `0…127` filtré par canal d'entrée,
  Song Select `0…127` global, slot absent non consommé et aucun rappel en PassThru.
- [x] Actions mappées Random/ChromaticShift/Play/Stop/Record reliées à Tone Row sur
  front actif, tout en conservant Panic/Toggle comme exceptions consommées en PassThru.
- [x] UI Compose avec timeline, curseurs de rangée/séquence, statut, transport,
  transformations et gestion des 128 slots; arrangement secondaire bloqué sous
  Performance Lock.

### Preuves logicielles

Les preuves reproductibles sont portées par `ToneRowReducerTest`,
`TransportReducerTest`, `PerformanceCoordinatorTest`, `MidiMessageParserTest`,
`PerformancePresetSerializerTest`, `PerformancePresetAdaptersTest`,
`PresetMidiPolicyTest`, `SettingsRepositoryMigrationTest`, `ToneRowUiModelTest` et
`IntervalTabletViewModelTest`, plus les suites de non-régression de l'étape 1. Les totaux
et le résultat du dernier gate complet sont centralisés dans
`docs/VERIFICATION_REPORT.md` afin de ne pas dupliquer une valeur susceptible de devenir
obsolète.

Les fixtures de persistance prouvent la reconstruction du snapshot et son redémarrage
arrêté. Le ViewModel est aussi piloté sur JVM avec ses adaptateurs injectés ; cette preuve
ne vaut toutefois pas test instrumenté des interclassements Android réels.

Le gate historique de clôture de l'étape 2 compte 79/79 tests domaine et 91/91 tests JVM
application. Lint rend
`No issues found.` et les assemblages debug, release et de test Android réussissent. Le
test instrumenté de régression UI lancé directement sur la tablette avec `am instrument`
réussit également : 1 test, 0 échec, 1,593 s. Une passe antérieure de
`connectedDebugAndroidTest` était verte.

### Réception tablette partielle du 21 août 2026

- Samsung SM-X620, API 36, écran 90 Hz ; APK debug de 39 265 007 octets, SHA-256
  `B9E6A8EC9A73315AB3D3E8888F3CF078B887CE345528A309421D4C690AA7FBD4`.
- Installation, lancement, lifecycle court, Audio Monitor, pad simple, Tone Row, presets,
  rotation et police 1,3× observés sans crash ni ANR sur les parcours exécutés.
- Gonflement de timeline et placement par rapport aux barres système corrigés, puis
  vérifiés visuellement et par le test instrumenté final.
- Sur le scénario Release AOT 120 BPM/division 6/rangée de 7 pas/30 s, la médiane de frame
  passe de 18 à 16 ms et le jank legacy de 65,90 % à 63,20 %. Le p90 reste à 24 ms et le
  jank strict final à 99,58 % : la dette de performance 90 Hz reste ouverte.
- Démarrage froid AOT observé une fois à 216 ms, sans portée statistique. Soak court de
  trois minutes seulement, mémoire stable sur cette fenêtre, sans crash, ANR ni erreur
  audio observés.

Le détail et les preuves visuelles sont consignés dans
`docs/implementation/HARDWARE_REPORT_2026-08-21.md` et son dossier `evidence/`.

## Étape 3 — Audio natif, latence et robustesse

**MVP logiciel terminé le 1er septembre 2026 ; certification matérielle toujours partielle.**

### Livré dans le code

- [x] Acteur musical sur un dispatcher séquentiel hors Main ; horloge interne, gates et
  one-shots suivent la même mailbox et une release n'est programmée qu'après le Press
  accepté.
- [x] Effets MIDI/audio émis avant la capture durable ; snapshots immuables confluentés,
  persistance sur worker I/O séparé et seconde tentative bornée après erreur transitoire.
- [x] Diagnostics audio hors de l'acteur, au plus une lecture I/O en vol, et télémétrie
  MIDI de trafic échantillonnée sans retarder les erreurs ou overflows de contrôle.
- [x] Projections Compose séparées pour header, contenu et curseurs Tone Row, pads,
  ruban, statut, console et Synthé ; patch et diagnostics audio peuvent évoluer sans
  reconstruire tout l'écran.
- [x] Trois articulations de pads déterministes et persistées : `ARPEGGIATED` joue le
  lead, `STACKED` le voicing complet et `MUTED` conserve navigation/enregistrement sans
  Note On ; Auto Tone Row reste polyphonique.
- [x] Strummer adaptatif issu du voicing du domaine : crossings intermédiaires dans les
  deux sens, vélocité sur l'axe secondaire, multi-pointeur, hystérésis, clavier/TalkBack
  et une origine/release one-shot par corde.
- [x] Neuf pads conservés comme nœuds tactiles/focus/sémantiques distincts d'au moins
  72 dp, avec dessin mis en cache ; timeline et sélection de séquence isolées.
- [x] Ouverture Oboe Exclusive avec repli Shared, sample rate négocié, reprise hors
  callback, générations de stream et intention start/stop sérialisée.
- [x] File SPSC et drain callback borné, Panic d'urgence sur overflow, rejet des événements
  d'ancienne génération et purge au stop/restart ; les appels JNI `send` sont sérialisés
  en un producteur sans bloquer les diagnostics.
- [x] Ownership C++ partagé des callbacks et shutdown RAII/JNI idempotent afin qu'un
  callback tardif ne survive pas à ses ressources.
- [x] DSP C++20 durci : huit voix, oscillateurs PolyBLEP, ADSR, filtre, chorus, delay,
  réverbération et limiteur ; fréquences/coefficients précalculés, paramètres ciblés,
  entrées non finies neutralisées et reset O(1) des lignes à retard.
- [x] Contrat stable de 16 paramètres Synthé, patch persistant et panneau non modal avec
  timbre, cutoff/résonance, ADSR, chorus, delay, réverbération, master et diagnostics ;
  aperçu sonore confluent pendant le geste, deltas filaires, publication/persistance au
  relâchement, attente du chargement Settings et fermeture sous Performance Lock.
- [x] Correctif anti-saturation natif : temps-to-target ADSR à plusieurs sample rates,
  mix d'oscillateurs normalisé, reverb compensée avec all-pass canoniques et limiteur
  exactement transparent sous son knee, sans abaisser arbitrairement le master.
- [x] Settings v4, presets v3 et banque v2 avec articulation et patch Synthé stables ;
  les migrations historiques préservent le rendu (`Off` → `ARPEGGIATED`, accord actif
  → `STACKED`) et initialisent les paramètres audio absents à leurs valeurs sûres.
- [x] Variantes Android séparées : `benchmark` minifiée/profileable pour les mesures AOT
  et `instrumented` non minifiée pour les tests UI, chacune dans un package isolé.

### Preuves de clôture logicielle

Le gate final du 1er septembre réussit avec 94/94 tests domaine sur 10 suites et 140/140
tests JVM application sur 18 suites, soit 234/234 sans échec, erreur ni test ignoré.
`doctor.ps1` termine avec zéro erreur, `verify-structure.ps1` est OK,
`verify-native.ps1` réussit 2/2 et `verify.ps1` est vert. Lint Debug, Release, Benchmark et
Instrumented ne remontent aucune issue. Les assemblages Debug, Release non signé,
Benchmark, Instrumented et de tests réussissent dans le même gate.

Cette revalidation a corrigé une omission de `liboboe.so` propre aux builds Debug sous
Windows lorsque Prefab venait d'un cache utilisateur dont le chemin contenait un espace.
Les lanceurs ancrent désormais par défaut `GRADLE_USER_HOME` et `ANDROID_USER_HOME` dans
le workspace. Le gate inspecte l'APK et exige `liboboe.so` et `libc++_shared.so` pour
chaque ABI contenant `libinterval_audio.so`; les métadonnées de vérification manquantes
pour cinq artefacts transitifs déjà déclarés ont été complétées avec leurs SHA-256
officiels.

L'instrumentation finale du 1er septembre sur SM-X620/API 36 réussit 6/6 en 13,612 s. Elle conserve les
preuves UI articulation/strummer et vérifie le panneau Synthé, son ordre aperçu/commit,
son verrou de chargement et AUDIO-01. Ce dernier ouvre/ferme dix fois le stream, envoie les 16 paramètres et Panic à
chaque cycle, et rejoue la sonde si `restartCount` évolue entre deux échantillons. Le test
final satisfait toutes ses assertions ; la dernière télémétrie détaillée archivée le
31 août sur le même appareil et le même binaire indique 48 kHz, 96 frames/burst, buffer
192, file maximale 17, 0 événement perdu, 0 reprise, 0 erreur et 0 xrun. L'utilisateur
confirme en outre le suivi audible continu des sliders. L'écoute comparative
anti-saturation, la latence, le hotplug audio et le soak ne sont pas validés.
Le catalogue Android MIDI n'exposait aucun périphérique USB MIDI physique pendant cette
session ; ces protocoles restent donc bloqués matériellement.

Les cinq artefacts finaux et leurs empreintes sont consignés dans
`docs/implementation/HARDWARE_REPORT_2026-09-01.md`. La variante Benchmark a été
reconstruite, mais aucune nouvelle campagne A/B ni mesure AOT n'est revendiquée.

Les valeurs suivantes restent l'archive du gate du 21 août :

- Gradle `BUILD SUCCESSFUL` : domaine 79/79 sur 8 suites, application JVM 109/109 sur
  16 suites, soit 188/188 tests JVM.
- Vérification native hôte : 2/2 ; Lint debug/benchmark/instrumented et
  `lintVitalRelease` : 0 issue.
- Instrumentation finale SM-X620 : 2/2, pour les neuf pads accessibles distincts d'au
  moins 72 dp avec action sémantique et la conservation de la scène sous une timeline
  remplie.
- APK debug : 39 720 679 octets, SHA-256
  `C46A4E11E3269DA968A5B6892155A13BD18577A61BAB5FDABBE964E5EE5956FD`.
- APK benchmark : 8 932 016 octets, SHA-256
  `3F0E2B057159B6A26F7D1F2C434C6A3F9ACB4D0D6F82B8BF611ABF2C196E80C8`, installé puis
  compilé AOT en mode `speed` sur la SM-X620.
- Huit passes contrebalancées OFF/ON, 30 s après 10 s de warm-up : p50 plateforme 18 ms,
  p90 22,5 ms, p95 23 ms et p99 26,5 ms dans les deux conditions ; jank strict médian
  99,475 % OFF/99,685 % ON et legacy 68,985 % OFF/75,155 % ON.

Les distributions OFF/ON se recouvrent : cette campagne ne permet pas d'attribuer un
surcoût au DSP. Elle confirme toutefois que la durée totale de frame dépasse encore le
budget de 11,11 ms à 90 Hz. Le ring brut ne conserve que les 120 dernières frames
(environ 7,5 s), alors que les métriques plateforme couvrent les 30 s ; `gfxinfo` ne
mesure aucune latence tactile→audio ou MIDI.

## Validation matérielle encore requise

La tablette Android est désormais identifiée comme reçue partiellement. Les éléments
suivants ne sont toutefois pas déclarés validés matériellement :

- sélection, trafic et hotplug avec clavier et synthétiseur USB MIDI réels, aucun
  périphérique de ce type n'étant présent pendant le gate final ;
- MIDI Clock/Start/Continue/Stop et Program Change/Song Select depuis un contrôleur réel ;
- changement de mode ou de port avec notes physiquement tenues ;
- vrai multi-touch simultané, TalkBack, ordre de focus et contraste ;
- fluidité soutenue dans le budget 90 Hz ;
- latence tactile/MIDI→audio, hotplug/changement de route et stabilité prolongée du stream
  Oboe, dont le soak de 60 minutes, dans un environnement sans charge AAudio concurrente ;
- qualification subjective du correctif de saturation par écoute comparative ou loopback
  à niveau contrôlé ; les tests DSP hôte ne qualifient pas le haut-parleur ni le mixer Android.

Le protocole restant est `docs/HARDWARE_TEST_PROTOCOL.md`. Dans `.codex/state.json`,
`androidDevice` et la confirmation utilisateur du contrôle Synthé continu sont vrais ; les
indicateurs USB MIDI, écoute audio comparative et soak audio restent faux. Cela ne signifie
pas que la certification matérielle globale est complète.

## État de clôture de l'étape 3

La clôture logicielle et l'archivage des mesures tablette sont obtenus. L'étape 3 et le MVP
passent à `complete` dans l'état Codex. MIDI USB, vrai multi-touch, TalkBack, loopback
audio et soak de 60 minutes restent des limites explicites de certification matérielle,
pas des résultats réussis. Ces limites n'impliquent pas un déplacement des reducers vers
C++ : le moteur natif reste le synthétiseur temps réel, tandis que Kotlin demeure
l'autorité musicale déterministe.

## Dépôt

Le workspace est un dépôt Git local sur `main` avec le Wrapper Gradle officiel 8.13 et son
JAR vérifié. Aucun remote, push ou publication n'a été configuré. Aucun état distant n'est
revendiqué ; l'ajout d'une destination doit suivre séparément
`docs/REPOSITORY_SETUP.md` lorsqu'une URL de dépôt sera explicitement fournie.
