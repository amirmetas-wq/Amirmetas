# Build and verification

Use JDK 17, Gradle 8.11.1, Android SDK platform 35 and build-tools 35.0.0.

```
python3 tests/check_java_syntax.py
python3 tests/check_schedule.py
python3 tests/check_video.py
gradle -p android --no-daemon assembleDebug lintDebug
```

CI reuses the existing Android preview workflow on the isolated `clipyar/rev02` branch. `main` and `hamrah/android-preview` are not changed. Artifact label is inherited from that workflow; APK package/label are ClipYar.

Maven Central dependencies: AndroidX WorkManager 2.10.1, youtubedl-android library and FFmpeg 0.18.1. yt-dlp 2026.10.26 is downloaded during the build from the official release and SHA-256 verified before packaging:

`e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`

No API tokens, account cookies, downloaded user media or signing secrets belong in source. Preview APK uses the CI debug signing key; do not promise cross-build signature continuity. A stable private signing key should be configured for maintained releases.

Tests compile and exercise the **actual Java app core**, covering fixed Tehran time independent of device timezone, date boundaries, strict rolling 24-hour filtering, missing/future timestamps, deduplication, view sorting, source limits and complete segment coverage. `check_video.py` invokes the actual Java FFmpeg command builder on a synthetic 123.4-second video and a silent input. Native Android build and lint are separate checks.

Device acceptance still required: Android native extraction / 16-KB page compatibility, current YouTube extraction and timestamp availability, downloads with QuickJS, foreground notification, MediaStore exports, cancel/resume, Samsung battery scheduling and Instagram import. No device pass should be inferred from CI success.

Rev02 adds jsoup 1.18.3 and JUnit 4.13.2. assembleDebug depends on testDebugUnitTest, so the inherited workflow runs parser tests, including a live public-page fetch from CI using the same parser and user-agent as the phone. The live probe validates table parsing and prints the snapshot date; it does not treat a pre-update chart as current. The app independently rejects stale data. Parser fixtures cover daily-versus-total column separation, inconsistent dates, malicious links, duplicate IDs and broken table order. No keyword fallback exists.
