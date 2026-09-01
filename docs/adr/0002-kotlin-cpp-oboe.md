# ADR 0002 — Kotlin/Compose + domaine pur + C++/Oboe

- Statut : accepté
- Date : 2026-08-19

## Décision

Utiliser Kotlin/Compose pour l’application, un module Kotlin/JVM pur pour le moteur musical et C++20/Oboe pour le son temps réel.

## Justification

- Le domaine pur rend les règles musicales déterministes et testables rapidement.
- Compose convient à une UI tablette dynamique.
- Oboe fournit une abstraction Android dédiée à la faible latence et permet un callback natif contrôlé.

## Conséquences

- Frontière JNI compacte et unidirectionnelle dans le chemin critique.
- Deux suites rapides : domaine Kotlin et DSP C++ hôte.
- Les décisions musicales ne vivent jamais dans le callback audio.
