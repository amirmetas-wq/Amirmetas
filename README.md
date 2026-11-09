# ClipYar — Rev01

Native, on-device Android video preparation for Amir's Samsung S25 Ultra. Persian interface; no server or paid API key. Separate package `ir.amirsobhani.clipyar`.

- Daily WorkManager trigger around 10:00 in `Asia/Tehran`, persisted across reboot. Approximate background execution, subject to Android battery/network limits.
- Public YouTube search samples, filtered to a rolling 24-hour publication window and ranked by current view count. This is **not** a complete global trending chart or a measurement of 24-hour view growth. Unknown publication times are excluded. Searches and failures are logged.
- Default five sources/day, each at most 30 minutes in automatic discovery. Both limits are editable; 0 minutes means unlimited. Long sources are skipped in discovery, never partially trimmed. Manual URLs and selected local videos are processed in full without this duration cap.
- Consecutive 60-second parts; last part may be shorter. Full frame fitted over a blurred background in 9:16 H264/AAC MP4. Default 720×1280; optional 1080×1920.
- Numbered outputs in `Movies/ClipYar/YYYY-MM-DD/0001.mp4`, etc. SQLite resume records prevent completed parts from being duplicated. Name, channel and source URL in the UI and `Downloads/ClipYar/sources-DATE.txt`.
- Manual YouTube links, Android text-share input, and local document picker. No broad storage permissions. No Instagram credentials or auto-posting.

See [Persian installation guide](docs/INSTALL.fa.md), [build and validation](docs/BUILD.md), and [component notices](THIRD_PARTY_NOTICES.md).

Source is GPL-3.0; see LICENSE. Preview APK is for arm64 Android 10+; S25 acceptance testing remains necessary. A successful CI build does not establish that current YouTube endpoints are reachable on a user's network.
