# Mise en place du dépôt Git

Le ZIP est volontairement livré sans remote. L’initialisation ne publie rien.

## Initialisation

```bash
./scripts/init-git.sh https://example.invalid/proprietaire/depot.git
```

ou sous PowerShell :

```powershell
./scripts/init-git.ps1 https://example.invalid/proprietaire/depot.git
```

Remplacer l’URL d’exemple au moment de l’exécution. Le script refuse d’écraser un remote
`origin` existant.

## Avant le premier commit

1. Choisir le nom public, l’identifiant d’application et la licence.
2. Exécuter `PACKAGE_AUDIT=1 ./scripts/verify-structure.sh` sur un arbre propre.
3. Exécuter `./scripts/verify.sh` avec le SDK Android installé.
4. Générer et committer le Gradle Wrapper officiel avec `./scripts/install-standard-wrapper.sh`.
5. Vérifier le SHA-256 du JAR Wrapper et conserver `distributionSha256Sum`.
6. Examiner `THIRD_PARTY_NOTICES.md`, `SECURITY.md` et les permissions Android.
7. Créer le premier commit local, puis ajouter/pousser le remote explicitement.

## Branches et protections recommandées

- branche principale `main` ;
- pull request obligatoire avec CI réussie ;
- interdiction du force-push et de la suppression de `main` ;
- review obligatoire pour `.github/workflows/`, `gradle/wrapper/`, `SECURITY.md` et la
  configuration de signature ;
- Dependabot activé pour Gradle et GitHub Actions ;
- secrets de signature uniquement dans le coffre CI, jamais dans le dépôt.

## Publication assistée de la V2.4

Sous PowerShell, depuis la branche `v2.4` propre :

```powershell
.\scripts\publish-v2-4.ps1
```

Le script exécute le gate complet, actualise les références distantes, vérifie que
`origin/main` est bien un ancêtre et effectue un push normal sans option de force. Si
GitHub CLI est installé et authentifié, il crée ou retrouve la Pull Request. Sinon, il
copie sa description et ouvre la page Compare. Il n'installe aucun outil et ne stocke
aucun secret.

Prévisualisation entièrement locale :

```powershell
.\scripts\publish-v2-4.ps1 -DryRun -SkipVerify
```

L'option `-EnableAutoMerge` exige `gh auth login` et doit être demandée explicitement ;
elle conserve un merge commit et attend les protections de branche. Ne pas publier de
Release tant que le suffixe `-dev`, la signature et la licence ne sont pas finalisés.

## Gradle Wrapper

Le ZIP utilise d’abord un lanceur portable texte qui télécharge Gradle 8.13 et vérifie la
distribution. Après installation locale de Gradle, remplacer ce lanceur par le Wrapper
officiel généré :

```bash
./scripts/install-standard-wrapper.sh
```

Le JAR généré est un binaire attendu dans un dépôt Gradle. Le script vérifie son empreinte
connue avant qu’il soit ajouté au contrôle de version.
