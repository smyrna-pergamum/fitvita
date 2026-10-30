# FitVita

*Sport and healthy living, made fun.* Move, drink, breathe and level up on Android.

A colourful, gamified healthy-living app for Android. No account, no ads, no trackers, no server.

- **Activity (home)** – big step counter with an animated walking buddy, walked distance, 7-day bars;
  four daily quests (walk, drink water, move, yoga & breath); water tracker; GPS route recording.
- **Workout** – your own workout or ready routines (full body, cardio, dumbbell basics, glutes, legs,
  waist & belly), yoga and pilates flows, 53 illustrated exercises, one-tap sets, rest timer,
  stopwatch and countdown timer.
- **Inner peace** – meditation routines (morning calm, stress relief, focus, sleep wind-down),
  calm, box and 4-7-8 breathing, body scan, 5-4-3-2-1 grounding, muscle relaxation, gratitude.
- **Rewards** – XP, levels and ranks, day streaks, 31 badges (tap one to see how to earn it), confetti when a quest is done.
- **Radio** – free internet radio from the Radio Browser directory (local, world, all or by genre; no key, HTTPS streams only),
  also reachable from every workout and route screen; a mini player stays at the bottom.
- **Weight & BMI** – daily weigh-ins (+10 XP), BMI with WHO bands, female/male reference weight
  (Devine formula), weight goal and trend chart.
- **Guided workouts** – preview a routine, then do it one exercise at a time with countdowns,
  beeps and rest timers.
- **Meditation music** – 11 tracks with a sleep timer and a next button; soft music behind the calm exercises.
- **Nature sounds** – 10 illustrated, endlessly looping sounds: birds, wind, water, forest,
  restaurant, city centre, rain, fire, frogs & crickets, aquarium.
- **History** (in Settings) – workouts and routes; share a route as a picture, save it or export GPX.
- **Reminders** – friendly nudges for water (with a "+1 glass" button in the notification), moving,
  workouts and yoga; they stay quiet once the goal is reached.
- Four themes: Blue and Dark with a male character, Pink and Brown with a female character. UI in Türkçe, English, Deutsch, Español, Français, Русский, العربية.

Kotlin, Jetpack Compose, Material 3, Room, DataStore, osmdroid. minSdk 26, no Google Play Services.
Application ID `io.github.smyrna_pergamum.exercisetracker`. Licence GPL-3.0-or-later; illustrations
and exercise texts CC-BY-SA-4.0 (own work). See [BUILDING.md](BUILDING.md) and [PRIVACY.md](PRIVACY.md).

## Third-party content

- Meditation music: 11 tracks supplied by the app owner (AtlasAudio, Leberch, Nastelbom,
  Sound4Stock, Viacheslav Starostin, Verclub Music), compressed to Opus 48 kbps. They appear to come
  from Pixabay (Pixabay Content License): usable in apps, but **not a free licence**, so F-Droid would
  flag them or require removing them. Verify before publishing.
- Nature sounds: 10 recordings supplied by the app owner (WR Sound Library, shut_up_ghost,
  SoundReality, Dragon Studio, Eryliaa, joelfazhari), compressed to Opus 32 kbps. They also appear to
  come from Pixabay (same licence note as above). Scene pictures are own work.
- Radio station list: [radio-browser.info](https://www.radio-browser.info/) (public domain data, free API).
- Map tiles and data: © OpenStreetMap contributors (ODbL).
- Libraries: AndroidX, Jetpack Compose, Room, DataStore, Media3/ExoPlayer, osmdroid, kotlinx – Apache-2.0.
