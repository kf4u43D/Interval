# Notices de composants tiers

Ce document recense les composants déclarés par le workspace. Il ne remplace pas les
textes de licence officiels. Avant une distribution publique, générer un inventaire depuis
le graphe Gradle réellement résolu et joindre les notices exigées par chaque licence.

| Composant | Usage | Licence déclarée en amont | Redistribution dans ce ZIP |
|---|---|---|---|
| Gradle | Build | Apache License 2.0 | Non ; téléchargé à la demande |
| Android Gradle Plugin / Android SDK / NDK | Build Android | Conditions Google applicables | Non |
| Kotlin et plugins Kotlin | Langage/build | Apache License 2.0 | Non |
| AndroidX / Jetpack Compose / DataStore / Lifecycle | UI et plateforme | Apache License 2.0 | Non |
| kotlinx-coroutines | Concurrence hors domaine | Apache License 2.0 | Non |
| kotlinx-serialization | Presets/configuration | Apache License 2.0 | Non |
| Google Oboe | Sortie audio basse latence | Apache License 2.0 | Non ; artefact Maven résolu au build |
| JUnit 4 | Tests | Eclipse Public License 1.0 | Non |
| AndroidX Test / Espresso | Tests Android | Apache License 2.0 | Non |

Le workspace ne contient aucun code, firmware, graphisme, audio, police ou texte long
propriétaire d’Eventide. Les marques « Eventide » et « Misha » apparaissent uniquement
pour identifier la source comportementale publique.

Le code propre au projet reste **non licencié** tant qu’une licence n’a pas été choisie ;
voir `LICENSE` et `NOTICE.md`.
