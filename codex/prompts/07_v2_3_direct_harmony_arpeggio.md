# Étape 7 — V2.3 harmonie directe et arpège autonome

Renforcer la scène deux mains de la V2.2 sans réintroduire sa latence :

- exposer les treize gammes sous forme de boutons dédiés, sans menu intermédiaire ;
- déclencher gammes, accords et articulations dès le contact tactile tout en conservant
  les actions clavier et d’accessibilité ;
- rendre les changements d’accord audibles immédiatement sur les pads maintenus ;
- transformer `ARPEGGIATED` en arpège autonome des voix de l’accord, cadencé par le
  tempo et la division existants mais indépendant du transport et de Tone Row ;
- préserver l’ownership par source, la symétrie Note On/Note Off, Panic et le chemin
  musical déterministe du domaine.

Ajouter les tests domaine, ViewModel et instrumentés nécessaires, mettre à jour les
documents, livrer une variante performance V2.3 coinstallable avec la dernière V1,
puis clôturer Git localement sans push.
