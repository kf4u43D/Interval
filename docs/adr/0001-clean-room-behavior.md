# ADR 0001 — Réimplémentation comportementale indépendante

- Statut : accepté
- Date : 2026-08-19

## Décision

Implémenter uniquement les comportements publics nécessaires à l’interopérabilité et à l’expérience musicale, à partir d’une spécification interne paraphrasée. Ne reprendre aucun code, firmware, ressource, mise en page ou texte propriétaire.

## Conséquences

- Nom et identité visuelle originaux obligatoires avant publication.
- Les tests se réfèrent à `docs/BEHAVIOR_SPEC.md`, pas directement au PDF.
- Toute ambiguïté est résolue par une décision produit documentée, sans rétro-ingénierie du firmware.
