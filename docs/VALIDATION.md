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
- Full 123.4-second coverage and silent-source integration test rerun after the shared command-builder change: see current run result.
- Persian text is downloaded from the video's real YouTube subtitle/automatic-caption tracks. No independent ASR or translation engine is claimed. No Persian track/download/text means failure before exporting clips.
- The B1M is a persisted approved source, with an on-demand recent-video list. No automatic daily monitoring is claimed for this preview.
- Live ChartsList HTTP returned 200 in the previous CI run, but parser integration FAILED. The live test now requires explicit `CLIPYAR_LIVE_CHART_TEST=true`; CI labels it SKIPPED in this manual-only build. Discovery is disabled in the preview, not certified or silently replaced with another source. Parser fixture tests continue to run.
- Android build/lint: pending current CI build.
- Physical S25 / live YouTube subtitle retrieval / Android shaping / MediaStore Downloads and Instagram import: pending phone test.
