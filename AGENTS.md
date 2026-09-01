# Instructions permanentes pour Codex

## Mission

Développer un instrument Android MIDI-first appelé provisoirement **Interval Tablet**. La priorité est la fidélité comportementale aux fonctions publiques documentées du module Misha, sans copier de code, firmware, ressources, textes, graphismes ou identité visuelle propriétaires.

## Ordre de lecture obligatoire

Avant toute modification, lire dans cet ordre :

1. `README.md`
2. `docs/PRODUCT_BRIEF.md`
3. `docs/BEHAVIOR_SPEC.md`
4. `docs/MIDI_SPEC.md`
5. `docs/ARCHITECTURE.md`
6. `docs/TEST_STRATEGY.md`
7. `docs/ACCEPTANCE_CRITERIA.md`
8. le prompt d’étape dans `codex/prompts/`
9. `docs/IMPLEMENTATION_STATUS.md`

## Règles de périmètre

- MIDI est la fonction principale. L’audio interne est un moniteur/instrument secondaire.
- Pas de microtonalité, Scala, MPE, MIDI 2.0, CV, réseau ou USB audio multicanal dans les trois étapes prévues.
- Ne pas introduire de bibliothèque DSP générale, de framework audio multiplateforme ou de moteur de jeu.
- Ne pas remplacer Kotlin/Compose/Oboe sans ADR approuvé dans `docs/adr/`.
- Ne pas reproduire l’apparence du panneau Eventide. Concevoir une UI originale pour tablette.
- Ne jamais utiliser les noms ou logos Eventide/Misha comme nom d’application, package, icône ou ressource commerciale.

## Architecture imposée

- `domain/` reste du Kotlin pur, sans dépendance Android, Compose, coroutine, horloge système ou I/O.
- Toute logique musicale doit être exprimée comme une transition déterministe `state + action -> state + événements` et couverte par tests.
- `app/` adapte Android MIDI, Compose, cycle de vie et stockage au domaine ; aucune règle musicale ne doit y être dupliquée.
- Le moteur audio C++ reçoit des événements compacts. Son callback ne fait ni allocation, ni verrou, ni I/O, ni journalisation, ni appel JNI.
- Les ressources sont possédées explicitement et libérées de manière déterministe.
- Les notes actives sont suivies par origine afin que changements de mode, hotplug et panique produisent toujours les Note Off nécessaires.

## Qualité et sécurité

- Kotlin : types explicites aux frontières, état immutable côté domaine, `when` exhaustifs, pas de `!!` hors test.
- C++ : C++20, RAII, `std::array`/buffers préalloués, aucune exception dans le chemin audio, compilation avec warnings stricts.
- Pas de secret, token, chemin utilisateur absolu ou adresse de dépôt dans les fichiers versionnés.
- Pas de commande destructive (`rm -rf` hors répertoires de build, reset hard, clean global, réécriture d’historique).
- Ne jamais pousser, publier, créer un remote ou modifier une configuration Git globale.
- L’accès réseau est réservé à la résolution de dépendances officielles déjà déclarées. Toute nouvelle dépendance exige une justification dans `docs/DEPENDENCIES.md` et une ADR si elle structure l’architecture.

## Méthode d’exécution autonome

Une exécution Codex traite exactement **une étape entière**. Ne pas la fragmenter en petits tickets artificiels.

1. Inspecter l’état Git, le workspace et les documents.
2. Lancer `./scripts/doctor.sh` puis les vérifications disponibles.
3. Écrire ou mettre à jour `docs/implementation/STAGE_<N>_PLAN.md` avec le plan, les risques et les tests.
4. Implémenter le lot complet en conservant des commits localement cohérents si Git est initialisé ; ne jamais pousser.
5. Exécuter tous les tests applicables et corriger les régressions.
6. Mettre à jour `docs/IMPLEMENTATION_STATUS.md`, `.codex/state.json`, `CHANGELOG.md` et la matrice de comportement.
7. Produire un compte rendu final comprenant fichiers majeurs, commandes exécutées, résultats, limites matérielles et dette restante.

Ne demander une clarification que si une décision irréversible, un secret ou un matériel absent empêche réellement l’acceptation. Sinon choisir l’option la plus conservative, la documenter et poursuivre.

## Commandes de validation

```bash
./scripts/verify-domain.sh
./scripts/verify-native.sh
./gradlew :domain:test
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
./scripts/verify.sh
```

Les deux premières commandes doivent fonctionner sans SDK Android. Les commandes Gradle Android peuvent être marquées « non exécutables faute de SDK/appareil », mais jamais déclarées réussies sans preuve.

## Définition de terminé

Une étape n’est terminée que lorsque :

- tous ses critères d’acceptation sont satisfaits ou explicitement bloqués par matériel ;
- le build concerné ne comporte aucun avertissement nouveau important ;
- les tests déterministes couvrent les cas nominaux, limites et transitions de notes actives ;
- aucune TODO vague n’est ajoutée ; chaque TODO indique l’étape ou l’issue cible ;
- les documents reflètent exactement le comportement livré.
