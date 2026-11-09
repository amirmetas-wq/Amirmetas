# ClipYar — Rev03 local Persian translation test

Native Android preview for Amir's Samsung S25 Ultra. Label **کلیپ‌یار Rev03**, package `ir.amirsobhani.clipyar.test.rev03`. Installs beside Rev01/Rev02 without removing their data. This preview is manual-only; daily discovery and niche classification remain unfinished and disabled.

## Confirmed problem

The actual phone reported `Unable to download video subtitles for fa: HTTP Error 429: Too Many Requests` twice, at 13:25 and 13:39 Iran time on 2026-11-09. The title was resolved, but no clips were exported. This is a failed subtitle download; successful full-video retrieval or FFmpeg rendering on the phone was not established.

## Change

- Prefer native publisher Persian captions if available; otherwise prefer original English captions over YouTube's translated Persian track. English text is translated on-device by Google ML Kit 17.0.3, after an initial language-model download. No API key or server is required. An existing automatic Persian track can still be used when no English track exists; its availability is not guaranteed.
- A separate **۱. آماده‌سازی و تست ترجمهٔ فارسی** button downloads/prepares the model and translates a short engineering sentence without contacting YouTube. The actual result is stored and shown in the UI.
- Caption timing is retained. Full sentences are translated before being divided into readable screens. Identical sentences reuse a translation. Completed and partial translation caches are stored privately, keyed by the original subtitle content. No partial or non-Persian output is presented as successful.
- A detected YouTube 429 stops the current request, disables automatic retries, and records a persistent one-hour application cooldown. No alternate track, account or address is tried in response to a 429. That cooldown is not a claim about the server's actual reset time. An actual human-verification prompt is reported separately, for the user to handle; Chrome cookies are not assumed shared with the downloader.
- **ویدئو و زیرنویس از گوشی** accepts a local video plus English/Persian SRT or WebVTT. English is translated on-device; Persian is checked and burned in directly. This path can test rendering without depending on YouTube subtitle availability. There is no speech-recognition engine.
- Complete video -> consecutive 60-second MP4s; only the last may be shorter. Original image fits over a blurred 9:16 background. Arabic/Persian joining and bidi use Android Canvas/StaticLayout, composited by FFmpeg.
- Export: `Download/clipYar/YYYY-MM-DD/0001.mp4`, `0002.mp4`, etc. Errors include their actual stage in the main screen and copyable logs. Source title/channel/URL and translation attribution accompany online-derived clips.
- The B1M remains an approved channel with an on-demand recent-video list. The approved Changi sample is https://www.youtube.com/watch?v=QfO4MMFXD-w . It is a content/processing test, not a verified top-24-hour trend. The publisher requires separate permission for edited reposts; the app does not post automatically.

See [Persian test steps](docs/TEST_CHANGI.fa.md), [build](docs/BUILD.md), [validation](docs/VALIDATION.md), [checklist](docs/REVIEW_CHECKLIST.fa.md) and [component terms](THIRD_PARTY_NOTICES.md). The phone must still test live caption access, model installation, translation quality, Android rendering and Downloads export. CI is not a physical-device pass.
