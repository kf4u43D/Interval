# Plan d’implémentation — Étape 7 / V2.3

Date : 1er septembre 2026

## Résultat utilisateur attendu

La main gauche dispose simultanément des treize gammes, de Force to Scale et des dix
accords. Une sélection harmonique répond dès l’appui. En mode Arpégé, maintenir un pad
de la main droite fait défiler les voix de l’accord sans lancer ni enregistrer de Tone
Row ; un changement d’accord revoicera immédiatement les pads maintenus.

## Périmètre inclus

- Grille directe de treize gammes et grille directe de dix accords en portrait/paysage.
- Sélections gamme, accord et articulation sur le front descendant tactile, avec
  sémantique clic conservée pour clavier, TalkBack et tests.
- Session d’arpège pure et déterministe par source de pad.
- Cadence autonome fondée sur le tempo et la division du transport, sans exiger que le
  transport soit démarré ni qu’une séquence existe.
- Revoicing immédiat et sûr des pads maintenus lors d’un changement d’accord.
- Version performance `0.2.3-dev-performance`, validation logicielle et installation
  SM-X620 en conservant la V1.

## Périmètre explicitement différé

- Éditeur de motifs d’arpège, directions alternatives, latch et randomisation.
- Réglages de gate ou de division propres à l’arpège : la V2.3 partage les réglages de
  tempo/division existants.
- Validation MIDI USB physique et mesure loopback tactile→audio.

## Invariants

- Chaque voix d’arpège reste possédée par sa source et est libérée avant la suivante.
- Release, reconfiguration, Panic et fermeture libèrent notes et sessions d’arpège.
- Le domaine reste pur : `state + action -> state + événements` ; l’app ne fait que
  planifier les actions `AdvanceArpeggio`.
- Aucun changement du callback audio natif.

## Changements prévus par module

### `domain/`

- Ajouter les sessions de pads maintenus et l’action d’avancement d’arpège.
- Projeter une seule voix à la fois et faire cycler les voix dans l’ordre du voicing.
- Revoicer les sessions maintenues lors de `SetChord` sans perdre l’ownership.

### `app/`

- Réconcilier un ordonnanceur borné par source avec les sessions du domaine.
- Remplacer le menu de gamme de la scène deux mains par treize boutons directs.
- Introduire un contrôle de sélection immédiat réutilisable pour gammes, accords et
  articulations, avec cibles de 48 dp.

### `app/src/main/cpp/`

- Aucun changement fonctionnel prévu.

### Tests et documentation

- Tests du cycle des voix, du revoicing, des doublons, des limites et des releases.
- Test ViewModel de cadence autonome et d’arrêt au relâchement.
- Tests instrumentés des treize gammes visibles et du déclenchement sur touch-down.
- Mise à jour des spécifications, critères, matrice, statut, changelog et état Codex.

## Risques

| Risque | Détection | Mitigation |
|---|---|---|
| Note bloquée pendant un tick concurrent au Release | tests FIFO domaine/ViewModel | action idempotente par source + Release propriétaire |
| Double sélection au down puis au click | test tactile instrumenté | contrôle custom : down tactile, action sémantique séparée |
| Grilles trop denses | assertions 900×1440 et paysage tablette | colonnes adaptatives, libellés compacts, minimum 48 dp |
| Arpège confondu avec Tone Row | test transport arrêté et séquence vide | ordonnanceur dédié utilisant seulement durée de pas |

## Commandes de baseline

```powershell
powershell -ExecutionPolicy Bypass -File scripts\doctor.ps1
powershell -ExecutionPolicy Bypass -File scripts\verify-domain.ps1
powershell -ExecutionPolicy Bypass -File scripts\verify-native.ps1
```

Baseline : diagnostic sans erreur, domaine réussi, CTest 2/2 réussi. `kotlinc` autonome
reste absent mais Gradle domaine est disponible.

## Résultats de validation

| Validation | Commande/protocole | Résultat | Preuve ou limite |
|---|---|---|---|
| Structure | `scripts\verify-structure.ps1` | À exécuter | |
| Domaine | `scripts\verify-domain.ps1` | À exécuter | |
| DSP/JNI hôte | `scripts\verify-native.ps1` | À exécuter | |
| Android | `scripts\verify.ps1` | À exécuter | SDK présent |
| Tablette | installation + tests instrumentés | À exécuter | SM-X620 Wi-Fi ADB |
| MIDI USB | `docs/HARDWARE_TEST_PROTOCOL.md` | Différé | matériel MIDI requis |

## Critères de sortie

- [ ] Treize gammes et dix accords directs, visibles et tactiles à 48 dp.
- [ ] Accord sélectionné dès touch-down et revoicing audible des pads maintenus.
- [ ] Arpège multi-voix autonome, arrêté proprement au Release/Panic.
- [ ] Porte logicielle, installation performance et instrumentation tablette réussies.
- [ ] Documentation, matrice, `.codex/state.json` et `CHANGELOG.md` à jour.
