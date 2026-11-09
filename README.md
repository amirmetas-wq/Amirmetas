# ClipYar — Rev02 subtitle test

Manual, on-device Android preview for Amir's Samsung S25 Ultra. Separate application `ir.amirsobhani.clipyar.test`, labelled «کلیپ‌یار تست», installs beside Rev01 without removing it or migrating/deleting its data. No API key, server or Instagram login is needed.

The approved engineering sample is The B1M's Changi Airport documentary:
https://www.youtube.com/watch?v=QfO4MMFXD-w

- One button starts the complete sample with Persian subtitles. The app downloads an actual Persian subtitle/automatic-translation track from YouTube. It fails before exporting if Persian text is unavailable or the download fails. There is no independent speech recognizer or translation service. Machine-translated engineering terms need review.
- Android Canvas/StaticLayout renders joined Persian and mixed-direction text to transparent images; FFmpeg burns these into each MP4 with the original timings. Captions crossing a minute boundary continue at the beginning of the next part, and all words in long captions are preserved across readable screens.
- Complete videos are split consecutively into 60-second parts; only the last may be shorter. Original frame is preserved over a blurred vertical background. Default 720×1280 H264/AAC, optional 1080×1920. Manual URLs have no duration cap.
- Output: `Download/clipYar/YYYY-MM-DD/0001.mp4`, `0002.mp4`, etc. Sources and subtitle provenance are retained in a text file and alongside the app's play/share actions. Completed output profiles resume without duplicating finished parts; a subtitled profile is separate from a plain one.
- The B1M is a persisted approved channel, with an on-demand latest-eight-video selector ordered by publication. This is not a 24-hour-views ranking or an active daily monitor.
- Local video selection and normal YouTube URL/text-share input remain available. Local files are split without automatic subtitles in this preview.
- Daily discovery is disabled in this manual test package. The previous live ChartsList parser probe failed after HTTP 200 and remains unresolved. The niche content filter is not implemented. A subtitle test build does not certify those features. Live source tests are explicitly opt-in with `CLIPYAR_LIVE_CHART_TEST=true`; parser fixture tests always run.

See [Persian test steps](docs/TEST_CHANGI.fa.md), [build](docs/BUILD.md), [validation](docs/VALIDATION.md) and [notices](THIRD_PARTY_NOTICES.md). Source is GPL-3.0. APK is arm64 Android 10+. Physical-device acceptance, live YouTube availability and subtitle quality remain to be tested on the phone. The sample is not presented as today's trend. The B1M requires separate permission for editing/reposting; attribution alone is insufficient.
