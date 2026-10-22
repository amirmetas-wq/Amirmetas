# Validation and delivery status

- Delivery: installable Android preview APK, version0.1.0-preview, package ir.hamrah.agent, minSdk26 and targetSdk35.
- Built source commit: dfa6a7d69cdf5d65211ea5ead8e7c047b150ee04.
- CI: https://github.com/amirmetas-wq/Amirmetas/actions/runs/39791474947 — SUCCESS.
- Full Android assembly and lint: `assembleDebug lintDebug` passed using AGP8.9.2, Gradle8.11.1, JDK17 and SDK35.
- Java sources:12 files passed syntax parsing at language level17.
- Android manifest/resources: XML parsing passed; declared app component source files exist.
- Scheduling:7 executable tests passed against the actual scheduling method (Tehran before/after/equal time, UTC midnight, New York spring-forward and fall-back).
- Downloaded GitHub artifact ZIP integrity checked, matching artifact digest4b73b71a101b4c4531b48e33dbd95475d823339196466adb6ee6f7c6ef00fcf7.
- Extracted APK: app-debug.apk,3094464 bytes; SHA-2568bbd0139a6679f5505333be340253568be777b42862d4e3f223ded0928b6e02a.
- Signing certificate from successful Gradle signingReport: SHA-1`2A:B3:61:0B:93:30:3F:69:07:16:CC:56:C6:64:69:B2:8E:9A:C9:5F`.
- [Download APK artifact](https://github.com/amirmetas-wq/Amirmetas/actions/runs/39791474947/artifacts/12430279983).
- Independent source review corrected notification routing, linked-note empty-file refresh/cache, file-grant release, mail-disconnect races and queued daily work after scheduling is disabled.
- No emulator/device or real-account test performed. Gmail OAuth, Yahoo login and optional AI API key remain user setup requirements.
- No personal credentials, mailbox content or private notes were accessed or published. No recurring ChatGPT task or external message was created.

The APK is a development preview with a debug signing key. Persistent updates require retaining a fixed private signing key. Read the device acceptance checks in BUILD.md and Persian setup guide INSTALL.fa.md. Successful compilation does not establish on-device behavior or live-service authorization.
