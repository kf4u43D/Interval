# Amorçage puis Wrapper Gradle officiel

Le ZIP initial contient des lanceurs `gradlew`/`gradlew.bat` en texte, sans JAR binaire.
Ils téléchargent la distribution Gradle 8.13 dans le cache utilisateur, vérifient son
SHA-256 officiel, puis exécutent Gradle.

Avant le premier commit du futur dépôt, générer le Wrapper officiel :

```bash
./scripts/install-standard-wrapper.sh
```

Sous PowerShell :

```powershell
./scripts/install-standard-wrapper.ps1
```

Le script lance la tâche `wrapper`, conserve `distributionSha256Sum`, puis exige cette
empreinte pour `gradle-wrapper.jar` :

```text
81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f
```

Empreinte de la distribution `gradle-8.13-bin.zip` :

```text
20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
```

Le JAR officiel généré doit ensuite être versionné et contrôlé par la CI.

Les deux scripts générés conservent une adaptation locale minimale : lorsque l'appelant
ne fournit pas `GRADLE_USER_HOME` ou `ANDROID_USER_HOME`, ils utilisent respectivement
`.gradle/` et `.android/` dans le workspace. Cette adaptation évite la perte du runtime
Oboe par Prefab sous Windows lorsque le chemin du cache utilisateur contient un espace.
