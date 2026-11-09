# Rev01 validation

- Local Java core: 113 assertions passed (Tehran scheduling, complete clip coverage, URLs, rolling-date ranking).
- Real local FFmpeg: 123.4-second H264/AAC source exported as 60 + 60 + 3.4 seconds at 9:16; silent input also passed.
- XML manifest/resources: parsed successfully.
- Android assembleDebug and lintDebug: passed in GitHub Actions run 42575745369.
- Bundled arm64 Python: verified to be Python 3.12, compatible with pinned yt-dlp 2026.10.26.
- Final additions (process lock and diagnostic counts) are checked in the final build recorded in the delivery manifest.
- Physical S25 Ultra / YouTube network / Samsung background scheduling: not tested in this environment.

## Rev02

- Manual-only subtitle preview, separate package `ir.amirsobhani.clipyar.test` / label کلیپ‌یار تست. Rev01 is retained; its data is not migrated or deleted.
- 137 Java core assertions passed locally, including actual VTT parsing, Persian-script checking, cue overlap cleanup, long-caption splitting without dropped words, and preserving captions across the 60-second boundary.
- Actual FFmpeg subtitle compositor passed with synthetic transparent PNG fixtures: correct seeking, visibility timing, continuation at t=0 of the next part, and clearing after a cue ends. Persian text rendering is performed by Android StaticLayout and still requires device visual acceptance.
- Full 123.4-second coverage and silent-source integration test PASSED after the shared command-builder change: 60 + 60 + 3.4 seconds, H264/AAC, vertical frame, and silent input.
- Persian text is downloaded from the video's real YouTube subtitle/automatic-caption tracks. No independent ASR or translation engine is claimed. No Persian track/download/text means failure before exporting clips.
- The B1M is a persisted approved source, with an on-demand recent-video list. No automatic daily monitoring is claimed for this preview.
- Live ChartsList HTTP returned 200 in the previous CI run, but parser integration FAILED. The live test now requires explicit `CLIPYAR_LIVE_CHART_TEST=true`; CI labels it SKIPPED in this manual-only build. Discovery is disabled in the preview, not certified or silently replaced with another source. Parser fixture tests continue to run.
- Android assembleDebug and lintDebug PASSED in GitHub Actions run 42596282626 for commit 37af920476748e46302ad4bd8ac186c4e09f91e5. Parser fixture tests passed; live ChartsList check explicitly skipped. Bundled Python 3.12 verified.
- Physical S25 / live YouTube subtitle retrieval / Android shaping / MediaStore Downloads and Instagram import: pending phone test.

- Remote browser subtitle probe: YouTube displayed “Sign in to confirm you are not a bot”; subtitle controls could not be inspected. This does not establish that Persian is unavailable to the user. Retrieval of this exact sample must be checked on the phone.


## Rev03 — observed 429 and local translation

- Actual Rev02 phone screenshot confirms `Unable to download video subtitles for fa: HTTP Error 429: Too Many Requests` twice (13:25 and 13:39 Tehran); title extraction succeeded and zero clips were saved. It does not prove that the video bytes or renderer worked on-device.
- Added original-English caption selection and Google ML Kit 17.0.3 English/Persian translation. Official API and Persian language support were checked against Google's documentation. Actual model download/translation requires the phone and has NOT been certified by CI.
- 157 Java core assertions passed, including the exact observed error, original-vs-translated track choice, persistent cooldown policy, human-verification classification, SRT import, timing retention, translation memoization, cancellation/failure with no partial result, and engineering comparison symbols surviving the VTT cache.
- Real FFmpeg timed-caption/alpha compositor test PASSED again using the current command builder. Full-video 123.4-second coverage and silent input had already passed; the FFmpeg command builder is unchanged in this revision.
- No automatic retry or alternate-track request after a detected 429. One-hour app cooldown is conservative, not an assertion about YouTube's Retry-After header (not exposed by the wrapper).
- Separate package ir.amirsobhani.clipyar.test.rev03 avoids uninstalling prior apps or deleting their data. Daily discovery remains disabled; existing live ChartsList parser integration is unresolved and explicitly opt-in/skipped in this manual preview.
- Android assemble/lint: pending current CI build.
- Device acceptance still required: model download and ML Kit translation; original-English YouTube access; real Persian rendering; complete video download/splitting; MediaStore Downloads; Instagram import.
