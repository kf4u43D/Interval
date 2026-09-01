# Scripts

- `bootstrap` : diagnostic puis résolution/build initial.
- `doctor` : vérifie outils, Java, SDK, NDK et Codex.
- `fetch-dependencies` : résout les dépendances Gradle et construit le debug.
- `verify-structure` : valide fichiers requis, JSON/TOML/XML, secrets et portabilité.
- `verify-domain` : compile/exécute le noyau Kotlin pur et le parseur MIDI sans SDK
  Android, avec `kotlinc` ou le build Gradle autonome de `domain/` en repli.
- `verify-native` : compile/exécute les tests DSP et moteur audio (cycle Oboe, reprise,
  overflow, générations et drain borné), puis compile le pont JNI/Oboe contre des stubs hôte.
- `verify` : validation hôte puis Android si SDK présent.
- `benchmark-gfxinfo.ps1` : vérifie l'identité SHA-256 de l'APK installé, l'activité au
  premier plan et les 90 Hz, chauffe la scène, archive `gfxinfo` brut avec batterie et
  thermique, puis signale explicitement toute troncature du ring buffer.
- `codex-stage` : lance l’un des sept prompts autonomes.
- `init-git` : initialise le dépôt et ajoute le remote lorsque son URL est connue.
- `install-standard-wrapper` : remplace le lanceur initial par le Wrapper Gradle officiel et vérifie son JAR.

Les mesures physiques utilisent `assembleBenchmark` (`dev.intervaltablet.benchmark`, minifié
et profileable). Les tests Compose physiques ciblent `assembleInstrumentedAndroidTest` et le
package non minifié isolé `dev.intervaltablet.instrumented`; ils ne remplacent donc jamais la
session `dev.intervaltablet.debug`. Ces deux gates supplémentaires ne sont pas lancés par
`verify`.
