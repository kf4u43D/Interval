# Rapport de clôture tablette — 1er septembre 2026

## Portée

Ce rapport clôt le MVP logiciel Interval Tablet sur Samsung SM-X620/API 36. Il ne contient
aucun identifiant réseau. Il distingue les preuves reproductibles, la réception utilisateur
du contrôle Synthé continu et les protocoles matériels qui n'ont pas pu être exécutés.

## Résultat final

| Contrôle | Résultat | Preuve ou limite |
|---|---|---|
| Diagnostic Windows | Réussi | `doctor.ps1` : zéro erreur ; absence de `kotlinc` couverte par le repli Gradle prévu |
| Kotlin/JVM | Réussi | 94/94 domaine + 140/140 application = 234/234 |
| Natif hôte | Réussi | 2/2 suites CTest |
| Lint Android | Réussi | Debug, Release, Benchmark et Instrumented : `No issues found.` |
| Assemblages | Réussi | Debug, Release non signé, Benchmark, Instrumented et APK de tests |
| Suite appareil | Réussi | 6/6, 0 échec/ignoré, 13,612 s |
| AUDIO-01 | Réussi au nominal | dix cycles Oboe, seize paramètres et Panic par cycle ; toutes les assertions finales passent |
| Sliders Synthé | Reçu par l'utilisateur | le son suit le doigt en continu ; le patch durable reste validé à la fin du geste |

La dernière télémétrie AUDIO-01 détaillée, archivée le 31 août sur le même appareil et le
même binaire, indique 48 kHz, burst 96, buffer 192, file maximale 17 et zéro événement
perdu, reprise, erreur ou xrun. La passe du 1er septembre confirme le contrat par ses
assertions, sans revendiquer une nouvelle capture détaillée absente du journal final.

## Artefacts

| Artefact | Taille | SHA-256 |
|---|---:|---|
| `app-debug.apk` | 40 140 987 octets | `8AD5EF7EFA34790C1358F3127AB4668AE4E09540DD375F660AA35A7CD41D459C` |
| `app-release-unsigned.apk` | 9 025 500 octets | `06E642A65E31BBF332B3AC52AAE22EDEB90D11C92BD0BE0C1638A46BA53AD159` |
| `app-benchmark.apk` | 9 033 732 octets | `06573B1D99573CF31DA9C5B24A9BD68B2511A68B15B5D5FD6117B9220EA6C5ED` |
| `app-instrumented.apk` | 39 862 045 octets | `E8B5BE702CBE432C0E3C66B36DDA4120FFB979028D6BF8800D4EA73451C24B74` |
| `app-instrumented-androidTest.apk` | 2 508 467 octets | `21493BA4B430146B44AC36CA0F6E2DF1EA6DCEBE810E3B26EB8DFDF51E1BBE9F` |

La variante Release est volontairement non signée. Licence, identité commerciale,
signature et publication ne font pas partie de cette clôture et aucune publication n'a
été effectuée.

## Certification matérielle restante

| Protocole | Statut | Motif |
|---|---|---|
| Ports, trafic et hotplug MIDI USB | Bloqué matériel | aucun clavier/synthétiseur USB MIDI cible disponible |
| MIDI Clock, Program Change et Song Select réels | Bloqué matériel | contrôleur MIDI externe absent |
| Notes tenues pendant hotplug/changement de port | Bloqué matériel | périphérie MIDI USB absente |
| Vrai multi-touch simultané et TalkBack | Non exécuté | parcours physique dédié non réalisé |
| Hotplug/changement de route audio | Non exécuté | scénario de déconnexion contrôlé non réalisé |
| Latence tactile/MIDI vers audio et loopback | Bloqué matériel | interface et câblage de mesure absents |
| Écoute comparative anti-saturation | Non exécuté | la confirmation des sliders ne qualifie pas globalement le gain staging |
| AUDIO-03, soak de 60 minutes | Non exécuté | session prolongée instrumentée non réalisée |

## Conclusion

Le MVP logiciel des étapes 1 à 3 est terminé : son code, ses variantes, ses tests et son
parcours appareil final sont verts. La certification matérielle reste partielle pour les
protocoles listés ci-dessus ; aucun d'eux n'est présenté comme réussi.
