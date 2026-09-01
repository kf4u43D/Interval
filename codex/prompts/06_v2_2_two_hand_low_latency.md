# Étape 6 — V2.2 ergonomie deux mains et faible latence

Livrer une scène portrait divisée : harmonie/accords pour la main gauche, navigation
intervallique pour la main droite. Conserver le paysage adaptatif et toutes les
sémantiques musicales existantes.

Traiter la régression de latence comme un défaut prioritaire :

- mesurer la V2.1 avant modification ;
- ne plus livrer la variante instrumentée comme application de jeu ;
- produire une variante performance coinstallable, optimisée et signée localement ;
- isoler l'acteur musical sur un thread explicitement possédé et prioritaire ;
- réduire le coût de la surface harmonique dans le chemin d'entrée tactile ;
- mesurer la build finale sur SM-X620, sans confondre frames UI et latence acoustique.

Ajouter les tests de disposition, d'accessibilité et de non-régression nécessaires,
mettre à jour les documents, installer uniquement la dernière V1 et la V2.2 de jeu, puis
clôturer Git localement sans push.
