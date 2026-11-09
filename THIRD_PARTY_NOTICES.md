# Components

ClipYar source: GPL-3.0.

- [youtubedl-android](https://github.com/yausername/youtubedl-android), version 0.18.1: GPL-3.0. Includes Python and QuickJS runtime components and FFmpeg binaries. Source/build instructions are available in that repository, including BUILD_PYTHON.md and BUILD_FFMPEG.md.
- [yt-dlp](https://github.com/yt-dlp/yt-dlp), release 2026.10.26: Unlicense for yt-dlp itself; additional bundled dependency licenses accompany its official distribution. Official release URL and verified hash are recorded in app/build.gradle.
- [FFmpeg](https://ffmpeg.org/), bundled by youtubedl-android: licenses depend on enabled components. The app invokes the packaged executable rather than bundling a separate proprietary SDK. FFmpeg upstream source and build instructions: https://github.com/FFmpeg/FFmpeg and youtubedl-android/BUILD_FFMPEG.md.
- [AndroidX WorkManager](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/work/), 2.10.1: Apache-2.0. Transitive AndroidX, Kotlin, Jackson and commons-io notices remain with their distributions.

User-selected or downloaded videos are not included in the app or this source tree. The app records each selected video's original title, channel and public URL rather than presenting it as original material created by the app.
