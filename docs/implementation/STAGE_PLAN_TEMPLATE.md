# Étape N — plan et journal d’implémentation

## Résultat utilisateur attendu

Décrire une tranche verticale démontrable, de l’entrée utilisateur/MIDI jusqu’aux
sorties MIDI/audio et à l’état UI.

## Périmètre inclus

- Fonctionnalités livrées.
- Comportements publics de référence couverts.
- Cas limites et transitions de cycle de vie inclus.

## Périmètre explicitement différé

- Fonctions hors étape.
- Décisions qui exigent du matériel ou une validation propriétaire.

## Invariants

- Symétrie Note On/Note Off par origine.
- Panic et changement de port/mode libèrent toutes les notes concernées.
- Domaine musical pur et déterministe.
- Aucun verrou, allocation, I/O ou JNI dans le callback audio.

## Changements prévus par module

### `domain/`

### `app/`

### `app/src/main/cpp/`

### Tests et documentation

## Risques

| Risque | Détection | Mitigation |
|---|---|---|
| Note bloquée | test de transition et test matériel | registre de notes par origine + Panic |
| Jitter/latence | mesure MIDI horodatée | file bornée, traitement hors UI |
| Régression temps réel | audit + compteur d’événements perdus | buffers préalloués |

## Commandes de baseline

```bash
./scripts/doctor.sh
./scripts/verify.sh
```

## Journal d’exécution

Consigner les décisions importantes et les écarts au plan, sans recopier un journal de
commandes exhaustif.

## Résultats de validation

| Validation | Commande/protocole | Résultat | Preuve ou limite |
|---|---|---|---|
| Structure | `./scripts/verify-structure.sh` | À exécuter | |
| Domaine | `./scripts/verify-domain.sh` | À exécuter | |
| DSP/JNI hôte | `./scripts/verify-native.sh` | À exécuter | |
| Android | `./scripts/verify.sh` | À exécuter | SDK requis |
| MIDI USB | `docs/HARDWARE_TEST_PROTOCOL.md` | À exécuter | appareil requis |

## Critères de sortie

- [ ] Porte d’acceptation de l’étape satisfaite.
- [ ] Documentation et matrice de traçabilité à jour.
- [ ] `.codex/state.json` et `CHANGELOG.md` à jour.
- [ ] Limites matérielles explicitement marquées.
