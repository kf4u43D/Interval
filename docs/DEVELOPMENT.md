# Développement

## Installation SDK

Installer Android Studio ou les command-line tools, puis :

```bash
sdkmanager "platform-tools" "platforms;android-36" \
  "build-tools;36.0.0" "ndk;28.2.13676358" "cmake;3.22.1"
```

Accepter les licences avec `sdkmanager --licenses`, puis définir `ANDROID_HOME` ou créer `local.properties` :

```properties
sdk.dir=/chemin/vers/Android/Sdk
```

## Bootstrap

```bash
./scripts/doctor.sh
./scripts/fetch-dependencies.sh
```

Le lanceur `gradlew` télécharge `gradle-8.13-bin.zip` et vérifie :

```text
20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
```

## Vérifications rapides sans Android SDK

```bash
./scripts/verify-structure.sh
./scripts/verify-domain.sh
./scripts/verify-native.sh
```

La première commande valide la portabilité du workspace et les formats structurés. La
deuxième compile le Kotlin pur ainsi que le parseur MIDI avec `kotlinc`, ou utilise le
build Gradle autonome de `domain/` en repli, sans SDK Android. La troisième configure
CMake/Ninja, teste le DSP et compile le pont JNI/Oboe contre des stubs étroits.

## Vérification complète

```bash
./scripts/verify.sh
```

Cette commande exécute les vérifications hôte, puis les tâches Gradle lorsque le SDK est présent.


## Wrapper Gradle officiel

Le ZIP initial évite d’embarquer un JAR binaire et fournit un lanceur texte vérifié. Avant
le premier commit du dépôt, générer puis contrôler le Wrapper officiel :

```bash
./scripts/install-standard-wrapper.sh
```

Le script vérifie à la fois la distribution Gradle 8.13 et le JAR Wrapper attendu.

## Verrouillage des dépendances

Après une première résolution approuvée :

```bash
./gradlew dependencies --write-locks
./gradlew --write-verification-metadata sha256 help
```

Examiner manuellement `gradle/verification-metadata.xml` avant commit. Ne pas accepter aveuglément un nouveau checksum.

## Appareil

Le protocole complet est dans `docs/HARDWARE_TEST_PROTOCOL.md`.

- Activer le débogage USB.
- Vérifier `adb devices`.
- Installer avec `./gradlew :app:installDebug`.
- Lancer la variante de test isolée avec
  `./gradlew :app:connectedInstrumentedAndroidTest` lorsque le transport ADB piloté par
  Gradle est disponible.
- Après installation des APK `instrumented` et `instrumented-androidTest`, la commande
  directe utilisée pour la réception finale est :

  ```bash
  adb -s <device> shell am instrument -w -r dev.intervaltablet.instrumented.test/androidx.test.runner.AndroidJUnitRunner
  ```

- Pour MIDI USB, utiliser un hub USB-C alimenté si la tablette doit être chargée simultanément.

## Git

Le workspace possède un remote `origin` configuré. Cette configuration ne prouve aucun
état distant : aucun push ni publication n’est effectué ou autorisé par les procédures
Codex du projet. Les scripts d’initialisation ne doivent pas être relancés sur ce dépôt,
car ils refusent d’écraser un `origin` existant. Voir `docs/REPOSITORY_SETUP.md` pour les
protections et contrôles préalables à toute publication explicitement autorisée.
