# MovieFlix

Netflix-style Android streaming client — black + red.

**By FaresCodeX** · v1.2.0

## Features
- Splash screen
- Home: hero billboard · Continue Watching · horizontal rows
- Live search while typing
- My List (favorites)
- Offline downloads + Downloads shelf
- Player: quality · speed · **audio tracks** · **subtitles** (ar/en/fr when present) · PiP · lock · sleep · aspect
- Resume playback from last position
- Settings: account + **watch stats** (minutes · finished · sessions · downloads)
- minSdk 21 · targetSdk 34

## GitHub Actions
Push to `main` / `master` → workflow **Build MovieFlix APK** runs and uploads the APK artifact.

1. Create repo → upload this project (keep `app/`, `build.gradle`, `settings.gradle`, `.github/`)
2. Actions tab → run **Build MovieFlix APK** (or push a commit)
3. Download artifact **MovieFlix-APK**

## Local build
```bash
# need JDK 17 + Android SDK
echo "sdk.dir=/path/to/Sdk" > local.properties
gradle wrapper --gradle-version 8.2   # first time only
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```

Legacy non-Gradle script (custom env): `python3 build_apk.py`

## Package
`com.movieflix.app`

© 2026 MovieFlix — By FaresCodeX
