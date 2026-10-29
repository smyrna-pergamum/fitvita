# Building

Requirements: OpenJDK 17+ (21 tested), Android SDK platform 36, build-tools 36.0.0.

```
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease     # unsigned unless signing/keystore.properties exists
```

Generated sources (edit these, not the output):
- `tools/draw_exercises.py` → `app/src/main/res/drawable/ex_*.xml` (exercise illustrations;
  `python tools/draw_exercises.py preview` also writes `tools/preview.png`).
- `tools/strings/<lang>.json` → `values*/strings.xml` via `python tools/strings.py` (checks that
  every language has the same keys and placeholders as `en.json`).
- The walking buddy frames (`walker_0..7`) and yoga poses come from the same drawing script.

Release signing: `signing/keystore.properties` (git-ignored) with `storeFile`, `storePassword`,
`keyAlias`, `keyPassword`.
