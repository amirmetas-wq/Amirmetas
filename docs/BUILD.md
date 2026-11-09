# Build and verification

Use JDK 17, Gradle 8.11.1, Android SDK platform 35 and build-tools 35.0.0.

```
python3 tests/check_java_syntax.py
python3 tests/check_schedule.py
python3 tests/check_video.py
python3 tests/check_subtitles.py
gradle -p android --no-daemon assembleDebug lintDebug
```

CI reuses the existing Android preview workflow on the isolated `clipyar/rev02` branch. `main` and `hamrah/android-preview` are not changed. Artifact label is inherited from that workflow; APK package/label are ClipYar.

Maven Central dependencies: AndroidX WorkManager 2.10.1, youtubedl-android library and FFmpeg 0.18.1. yt-dlp 2026.10.26 is downloaded during the build from the official release and SHA-256 verified before packaging:

`e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`

No API tokens, account cookies, downloaded user media or signing secrets belong in source. Preview APK uses the CI debug signing key; do not promise cross-build signature continuity. A stable private signing key should be configured for maintained releases.

Tests compile and exercise the **actual Java app core**, covering fixed Tehran time independent of device timezone, date boundaries, strict rolling 24-hour filtering, missing/future timestamps, deduplication, view sorting, source limits and complete segment coverage. `check_video.py` invokes the actual Java FFmpeg command builder on a synthetic 123.4-second video and a silent input. Native Android build and lint are separate checks.

Device acceptance still required: Android native extraction / 16-KB page compatibility, current YouTube extraction and timestamp availability, downloads with QuickJS, foreground notification, MediaStore exports, cancel/resume, Samsung battery scheduling and Instagram import. No device pass should be inferred from CI success.

Rev02 adds jsoup 1.18.3 and JUnit 4.13.2. assembleDebug depends on testDebugUnitTest. Parser fixtures cover daily-versus-total column separation, inconsistent dates, malicious links, duplicate IDs and broken table order. The previous live ChartsList probe failed after HTTP 200; this remains unresolved. The current subtitle preview is manual-only and has applicationId `ir.amirsobhani.clipyar.test`. The live probe is explicitly opt-in with `CLIPYAR_LIVE_CHART_TEST=true` and otherwise reported SKIPPED, not passed. Automatic scheduling/discovery is disabled in this preview. No keyword fallback exists.

Persian text is taken from an actual `fa` subtitle/automatic-caption track supplied by YouTube. Caption images are drawn with Android Canvas/StaticLayout so joining and bidi do not depend on libass support in the bundled FFmpeg. Transparent timed PNGs are composited by the real command builder. Both the caption parsing contract and host FFmpeg integration tests run from the inherited Java/core test steps. Actual YouTube retrieval and device rendering remain acceptance checks.
