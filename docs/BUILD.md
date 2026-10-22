# Build preview

Status: APK assembled successfully in [GitHub Actions run39791474947](https://github.com/amirmetas-wq/Amirmetas/actions/runs/39791474947). Android lint, Java syntax and seven scheduling tests passed. The APK was retrieved and its archive integrity and SHA-256 checked. Local SDK downloads were unavailable; the GitHub build resolved that blocker. No device or live-account test has occurred.

## Local build

Use Android Studio or a JDK17+ development machine with:

- Android SDK Platform35 and Build Tools35.0.0
- Gradle8.11.1 (AGP8.9.2)
- Network access to Google Maven and Maven Central

Open `android/` as a project. Set `ANDROID_HOME` to the SDK installation, or create the untracked `android/local.properties` with `sdk.dir=/your/sdk/path`. Choose Gradle8.11.1 in Android Studio if no local Gradle installation is detected. This source bundle does not pretend to contain a downloaded Gradle wrapper JAR.

From the repository root:

```sh
python3 tests/check_java_syntax.py
python3 tests/check_schedule.py
gradle -p android --no-daemon assembleDebug lintDebug
gradle -p android signingReport
```

The test APK will be `android/app/build/outputs/apk/debug/app-debug.apk`. It is signed with the development machine's debug certificate. Register that certificate's SHA-1 and package `ir.hamrah.agent` for Gmail OAuth before connecting Gmail. Use the same certificate for later updates; an APK signed differently cannot update an existing installation. Keep signing keys private and backed up.

For production distribution use a separate private release key and an explicit release signing configuration; `assembleRelease` is not configured to create a signed production artifact. Do not use a public or ephemeral debug key for distribution.

## Optional GitHub build

`.github/workflows/android.yml` is a ready workflow file. After placing this source in a repository you control, run **Android preview → Run workflow**. It runs a full Android build, lint and scheduler tests, then provides an APK artifact. The source was uploaded, with the owner's approval, to public repository `amirmetas-wq/Amirmetas`, branch `hamrah/android-preview`, [PR1](https://github.com/amirmetas-wq/Amirmetas/pull/1). Pull requests targeting main also trigger this workflow.

The workflow's default debug certificate is ephemeral; rebuilds can have different fingerprints. For repeated Gmail use and upgrades, supply a fixed private signing key through your own protected build setup. Never paste the key or keystore password into chat or source files.

## Device acceptance checks still required

1. Install APK on the S25 Ultra. Grant notification permission on use; test a note notification with screen locked.
2. Share a text note, confirm preview, enable a near-future reminder; check completed lines are omitted. Test re-share updates and a linked text file edited to empty.
3. Register Gmail OAuth for the actual signed APK. Connect Gmail; verify inbox digests and that unread messages stay unread.
4. Generate Yahoo App password and test login, read-only behavior, network failure and disconnect.
5. Create a future meeting in the selected calendar; verify one event, Calendar reminder and app notification. Remove the meeting from the app and verify deletion.
6. Enable optional AI with a personal API key. Verify proposed dates before confirmation; test an unsupported action and preference deletion. Mail content should leave the device only after its specific opt-in.
7. Reboot and change time zone; verify future alarms return. Test Samsung battery settings, revoked calendar/file permissions, no network and Force stop recovery.

Passing syntax-only checks does not establish these behaviors.
