# Dépendances et chaîne d’outils

Jeu de versions figé le 20 août 2026. Il vise une combinaison conservative et cohérente, pas la course à la version la plus récente.

## Build

| Élément | Version | Rôle |
|---|---:|---|
| Gradle | 8.13 | moteur de build, compatible avec AGP choisi |
| Android Gradle Plugin | 8.13.2 | build Android |
| Kotlin / Compose compiler plugin | 2.3.21 | Kotlin et Compose |
| Java bytecode | 17 | cible stable Android/CI |
| compileSdk / targetSdk | 36 | API Android cible |
| minSdk | 29 | Android 10, baseline de cycle de vie et MIDI moderne |
| NDK | 28.2.13676358 | compilation C++ |
| CMake Android | 3.22.1 | build JNI/Oboe |
| C++ | 20 | moteur DSP |

## Bibliothèques

| Artefact | Version |
|---|---:|
| Compose BOM | 2026.04.01 |
| `androidx.core:core-ktx` | 1.17.0 |
| `androidx.activity:activity-compose` | 1.12.4 |
| AndroidX Lifecycle | 2.10.0 |
| DataStore Preferences | 1.2.1 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization-json | 1.11.0 |
| `com.google.oboe:oboe` | 1.10.0 |
| JUnit 4 | 4.13.2 |
| AndroidX Test Core | 1.7.0 |
| AndroidX Test Ext JUnit | 1.3.0 |
| Espresso | 3.7.0 |

## Politique

- Dépendances centralisées dans `gradle/libs.versions.toml`.
- Répertoires autorisés : Google Maven, Maven Central et Gradle Plugin Portal pour les plugins.
- Aucune version dynamique (`+`, `latest`, snapshot).
- Nouvelle dépendance : motivation, licence, taille, activité de maintenance, surface native et alternative standard.
- Oboe est la seule dépendance DSP/audio structurelle. Les effets sont internes et testables.
- Le MVP utilise `MidiManager`/`MidiReceiver` côté Android. AMidi/NDK reste une optimisation future, non une dépendance du socle.
- La ligne Compose 1.11/API 36 est conservée tant que le projet reste sur AGP 8.13. Compose 1.12 et Lifecycle 2.11 exigent API 37 et AGP 9.x ; les adopter nécessite une décision explicite sur la cible Android.
- Les dependency locks issus du gate sont versionnés dans `app/gradle.lockfile`, `domain/gradle.lockfile` et `settings-gradle.lockfile`.
- `gradle/verification-metadata.xml` enregistre les SHA-256 des artefacts effectivement résolus avec `verify-metadata=true`. Un gate ultérieur sans option d’écriture doit les vérifier. Ces empreintes figent le graphe approuvé, mais leur génération initiale depuis le cache ne remplace pas une validation indépendante de la chaîne de publication.
- Les lanceurs utilisent par défaut `.gradle` et `.android` dans le workspace. Outre la
  reproductibilité de la signature Debug, ce chemin sans espace évite l'omission du
  runtime Prefab `liboboe.so` observée avec AGP/CMake sous Windows ; le gate inspecte
  désormais explicitement le contenu natif de l'APK.

## Téléchargement

```bash
./scripts/fetch-dependencies.sh
```

Cette commande télécharge Gradle 8.13 via le lanceur vérifié, puis résout les artefacts Gradle/Maven et construit le debug lorsque le SDK Android est présent. Les caches Gradle/SDK ne sont pas embarqués dans le ZIP afin de préserver portabilité, licences et taille.

## Sources primaires

- Android Gradle Plugin 8.13 : <https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes>
- Kotlin/Compose : <https://developer.android.com/develop/ui/compose/compiler>
- Versions AndroidX : <https://developer.android.com/jetpack/androidx/versions>
- Oboe releases : <https://github.com/google/oboe/releases>
- Gradle checksums : <https://gradle.org/release-checksums/>
