# FitVita

*Sport and healthy living, made fun.* Move, drink, breathe and level up on Android.

A colourful, gamified healthy-living app for Android. No account, no ads, no trackers, no server.

- **Activity (home)** – big step counter with an animated walking buddy, walked distance, 7-day bars;
  four daily quests (walk, drink water, move, yoga & breath); water tracker; GPS route recording.
- **Workout** – ready and own routines, 22 illustrated exercises, one-tap sets, rest timer.
- **Yoga** – 10 illustrated poses, morning and evening flows, guided breathing (1/3/5 min).
- **Rewards** – XP, levels and ranks, day streaks, 20 badges, confetti when a quest is done.
- **Radio** – free internet radio from the Radio Browser directory (no key, HTTPS streams only),
  also reachable from every workout and route screen; a mini player stays at the bottom.
- **Weight & BMI** – daily weigh-ins (+10 XP), BMI with WHO bands, weight goal and trend chart.
- **Guided workouts** – preview a routine, then do it one exercise at a time with countdowns,
  beeps and rest timers.
- **Meditation music** – 11 tracks with a sleep timer; soft music behind the breathing exercise.
- **History** (in Settings) – workouts and routes; share a route as a picture, save it or export GPX.
- **Reminders** – friendly nudges for water (with a "+1 glass" button in the notification), moving,
  workouts and yoga; they stay quiet once the goal is reached.
- Four themes: Blue (man), Dark (man), Pink (woman), Light (woman) – the character changes too. UI in Türkçe, English, Deutsch, Español, Français, Русский, العربية.

Kotlin, Jetpack Compose, Material 3, Room, DataStore, osmdroid. minSdk 26, no Google Play Services.
Application ID `io.github.smyrna_pergamum.exercisetracker`. Licence GPL-3.0-or-later; illustrations
and exercise texts CC-BY-SA-4.0 (own work). See [BUILDING.md](BUILDING.md) and [PRIVACY.md](PRIVACY.md).

## Third-party content

- Meditation music: 11 tracks supplied by the app owner (AtlasAudio, Leberch, Nastelbom,
  Sound4Stock, Viacheslav Starostin, Verclub Music), compressed to Opus 48 kbps. They appear to come
  from Pixabay (Pixabay Content License): usable in apps, but **not a free licence**, so F-Droid would
  flag them or require removing them. Verify before publishing.
- Radio station list: [radio-browser.info](https://www.radio-browser.info/) (public domain data, free API).
- Map tiles and data: © OpenStreetMap contributors (ODbL).
- Libraries: AndroidX, Jetpack Compose, Room, DataStore, Media3/ExoPlayer, osmdroid, kotlinx – Apache-2.0.
