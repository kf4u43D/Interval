# Rapport tablette — revalidation Wi-Fi, packaging natif et contrôles continus — 31 août 2026

## Portée

Cette session reprend la réception de l'étape 3 sur la Samsung SM-X620 après activation
du débogage Wi-Fi. Elle reconstruit les APK de la variante `instrumented`, réinstalle les
deux packages de test isolés et rejoue AUDIO-01 ainsi que la suite UI complète. Aucune
adresse réseau, clé ADB ou identité de session n'est archivée.

La première reconstruction a révélé un défaut reproductible : lorsque Gradle/Prefab
résolvait Oboe depuis un cache utilisateur Windows dont le chemin contenait un espace,
`libinterval_audio.so` restait lié dynamiquement à `liboboe.so`, mais ce dernier
n'entrait pas dans l'APK Debug/Instrumented. Android refusait alors le chargement natif
avant toute ouverture Oboe. Le lanceur utilise désormais par défaut les caches `.gradle`
et `.android` du workspace, et le gate inspecte le runtime de chaque ABI dans l'APK.

Après cette reprise, l'essai tactile a révélé que les sliders Synthé ne modifiaient le son
qu'au relâchement. Le correctif final ajoute un aperçu confluent par frame, transmet
seulement les paramètres modifiés et conserve le commit/persistance au relâchement. La
suite appareil vérifie l'ordre aperçu puis commit ; l'APK final a été réinstallé et laissé
ouvert pour la réception tactile et auditive, qui reste une observation utilisateur.

## Environnement et artefacts

| Élément | Valeur observée |
|---|---|
| Tablette | Samsung `SM-X620` |
| Système | Android 16, API 36 |
| ABI | `arm64-v8a` |
| Transport de test | ADB Wi-Fi appairé, valeurs éphémères non archivées |
| APK instrumented | 39 862 045 octets ; SHA-256 `E8B5BE702CBE432C0E3C66B36DDA4120FFB979028D6BF8800D4EA73451C24B74` |
| APK de tests | 2 508 467 octets ; SHA-256 `21493BA4B430146B44AC36CA0F6E2DF1EA6DCEBE810E3B26EB8DFDF51E1BBE9F` |
| MIDI USB physique | absent du catalogue Android MIDI pendant la session |

Les packages `dev.intervaltablet.instrumented` et
`dev.intervaltablet.instrumented.test` ont été remplacés après contrôle de leur cible.
Cette opération a effacé uniquement leur état de test ; le package utilisateur Debug
n'a pas été désinstallé ni vidé.

## Régression de packaging et correction

La première suite rend `5/6` : les cinq tests Compose passent, tandis qu'AUDIO-01 échoue
sur la disponibilité de la bibliothèque native. Le journal ciblé identifie
`liboboe.so not found`, et l'inspection ZIP confirme son absence pour les quatre ABI alors
que `libinterval_audio.so` est présent.

Le modèle CMake Debug montrait une liste `runtimeFiles` vide lorsque le Prefab Oboe venait
du cache utilisateur avec espace. Le même modèle, résolu depuis `.gradle` dans la racine
du workspace sans espace, répertorie `liboboe.so`. Après correction :

- `libinterval_audio.so`, `liboboe.so` et `libc++_shared.so` sont présents pour
  `arm64-v8a`, `armeabi-v7a`, `x86` et `x86_64` ;
- le nouvel oracle `verify_apk_native_runtime.py` échoue sur l'ancien APK incomplet et
  réussit sur les APK reconstruits ;
- `verify.ps1` et `verify.sh` exécutent cet oracle après `assembleDebug` ;
- les checksums de cinq métadonnées transitives déjà déclarées ont été comparés à Maven
  Central puis ajoutés au manifeste Gradle, sans nouvelle dépendance.

## Résultats appareil

| Cas | Résultat | Preuve ou limite |
|---|---|---|
| Chargement natif corrigé | Réussi | AUDIO-01 démarre après réinstallation du nouvel APK |
| AUDIO-01 isolé | Réussi | `OK (1 test)` en 6,219 s |
| Suite instrumentée complète | Réussi | `OK (6 tests)` en 13,723 s sur l'APK du correctif continu |
| Cycles Oboe | Réussi au nominal | 10/10 à 48 kHz, burst 96, buffer 192 |
| File audio | Réussi | profondeur maximale 17, profondeur finale 0, zéro drop |
| Santé du stream | Réussi sur la fenêtre | zéro xrun, reprise et code d'erreur |
| UI/accessibilité automatisée | Réussi | timeline, neuf pads, articulations, cordes et panneau Synthé ; aperçu observé avant commit |
| MIDI USB | Bloqué matériel | Android MIDI Manager n'expose que des services virtuels tiers, aucun périphérique USB MIDI |
| Écoute/loopback | Non exécuté | qualification subjective et mesure anti-saturation toujours ouvertes |
| AUDIO-03 60 minutes | Non exécuté | aucun scénario prolongé représentatif automatisé dans cette session |
| Multi-touch/TalkBack physiques | Non exécutés | tests sémantiques réussis, mais pas de geste physique ni parcours vocal |

La synthèse AUDIO-01 versionnée est conservée sous
[`evidence/2026-08-31-sm-x620/audio/audio-01-summary.txt`](evidence/2026-08-31-sm-x620/audio/audio-01-summary.txt).

## Gate logiciel rejoué

`verify.ps1` réussit après correction : structure sur 278 fichiers, 94/94 tests domaine,
140/140 tests application, 2/2 suites natives, Lint
Debug et assemblage Debug. Le contrôle du runtime APK confirme les quatre ABI. Lint
Instrumented réussit séparément. Les avertissements d'analytics vers `C:\.android` et de
version XML SDK restent environnementaux ; aucune issue Lint applicative n'est produite.

## Conclusion

La reprise Wi-Fi a fermé une régression réelle de distribution native, ajouté le suivi
continu des sliders et reconfirmé le contrat nominal AUDIO-01 avec de meilleures métriques
observées que le 22 août : burst 96, buffer 192 et zéro xrun sur cette fenêtre. Elle ne
ferme pas la porte matérielle globale :
MIDI USB, hotplug audio, écoute/loopback, latence, vrai multi-touch, TalkBack et soak de
60 minutes restent requis.
