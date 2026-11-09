# Rev01 validation

- Local Java core: 113 assertions passed (Tehran scheduling, complete clip coverage, URLs, rolling-date ranking).
- Real local FFmpeg: 123.4-second H264/AAC source exported as 60 + 60 + 3.4 seconds at 9:16; silent input also passed.
- XML manifest/resources: parsed successfully.
- Android assembleDebug and lintDebug: passed in GitHub Actions run 42575745369.
- Bundled arm64 Python: verified to be Python 3.12, compatible with pinned yt-dlp 2026.10.26.
- Final additions (process lock and diagnostic counts) are checked in the final build recorded in the delivery manifest.
- Physical S25 Ultra / YouTube network / Samsung background scheduling: not tested in this environment.
