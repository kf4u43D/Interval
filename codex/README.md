# Exécutions Codex

Les prompts sont volontairement peu nombreux et larges. Une exécution traite une tranche verticale complète et ne lance jamais automatiquement la suivante.

```bash
./scripts/codex-stage.sh 1
./scripts/codex-stage.sh 2
./scripts/codex-stage.sh 3
```

Le script assemble `CODEX_SUPERPROMPT.md` et le prompt d’étape, puis appelle :

```text
codex exec --sandbox workspace-write -
```

Codex lit aussi automatiquement `AGENTS.md` lorsque le projet est approuvé comme workspace de confiance. L’accès réseau du sandbox est activé pour la résolution des dépendances déclarées ; `AGENTS.md` en restreint l’usage.
