# Privacy

- No account, ads, analytics or telemetry. All data stays on the phone.
- Set values and finished routes are encrypted (AES-256-GCM) with a key protected by Android
  Keystore. While an activity is recording, its GPS points are kept unencrypted in the app's
  private database and are folded into the encrypted route when it ends.
- Location is used only while an outdoor activity is being recorded, through a foreground service
  with a visible notification. No background location permission is requested.
- Step counter (optional, off until you turn it on): reads the phone's hardware step counter
  every half hour, before midnight and after a restart, and stores only daily totals. Needs the
  "physical activity" permission. Steps taken between the last reading and a phone restart can be
  missed.
- Water, breathing sessions, points and badges are computed and stored only on the phone.
- Reminders are local alarms; nothing is sent anywhere.
- Radio: searches go to radio-browser.info and the audio comes from the station you pick; both see
  your IP address. Only HTTPS streams are offered.
- Body weight is stored encrypted on the phone; height and goal stay in local settings.
- Network: OpenStreetMap map tiles, loaded while a map is on screen. The tile server sees
  your IP address and which map area is shown; your route is never uploaded.
- Sharing a picture or exporting GPX happens only when you tap it; GPX contains your route.
- Android cloud/device backup is off. Use the password-protected backup in Settings
  (PBKDF2-HMAC-SHA256 600,000 iterations + AES-256-GCM).
- Permissions: location (fine/coarse), foreground service (location), physical activity (steps),
  start at boot (step reading), notifications, internet.
- The app gives general exercise information only, not medical advice.
