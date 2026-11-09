# ClipYar — Rev02

Native, on-device Android video preparation for Amir's Samsung S25 Ultra. Persian interface; no server or paid API key. Separate package `ir.amirsobhani.clipyar`.

- Daily WorkManager trigger around **14:00 in Asia/Tehran**, persisted across reboot. Approximate background execution, subject to Android battery/network limits. Rev02 migrates the old schedule once and cancels old daily triggers.
- Automatic discovery uses **only https://chartslist.com/youtube/**, ordered by its **24h views** column. Keyword searches and the recent-upload filter have been removed; old uploads are eligible. Only the tracked dataset is covered. The chart snapshot date must match today in Tehran; yesterday or future data is rejected, with bounded retries when today is not ready.
- Rank, daily views, total views at snapshot, snapshot time and source page are stored and displayed with each clip and in the credits file. Up to 60 non-completed candidate pages from the first 100 chart positions are checked for availability, live status, duration and the optional CC setting. Completed sources are not repeated.
- Default five sources/day, each at most 30 minutes in automatic discovery. Both limits are editable; 0 minutes means unlimited. Long sources are skipped in discovery, never partially trimmed. Manual URLs and selected local videos are processed in full without this duration cap.
- Consecutive 60-second parts; last part may be shorter. Full frame fitted over a blurred background in 9:16 H264/AAC MP4. Default 720×1280; optional 1080×1920.
- Numbered outputs in `Movies/ClipYar/YYYY-MM-DD/0001.mp4`, etc. SQLite resume records prevent completed parts from being duplicated. Name, channel and source URL in the UI and `Downloads/ClipYar/sources-DATE.txt`.
- Manual YouTube links, Android text-share input, and local document picker. No broad storage permissions. No Instagram credentials or auto-posting.

See [Persian installation guide](docs/INSTALL.fa.md), [build and validation](docs/BUILD.md), and [component notices](THIRD_PARTY_NOTICES.md).

Source is GPL-3.0; see LICENSE. Preview APK is for arm64 Android 10+; S25 acceptance testing remains necessary. A successful CI build does not establish that current YouTube endpoints are reachable on a user's network.
