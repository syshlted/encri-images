---
paths:
  - "app/src/main/AndroidManifest.xml"
  - "app/src/main/res/xml/**"
  - "app/src/**/vault/SecureWindows*.kt"
  - "app/src/**/vault/PrivacySettings*.kt"
  - "app/src/**/vault/ShippedManifestTest.kt"
  - "app/src/main/java/**/SettingsActivity.kt"
---

# Privacy and settings — component brief

What this app lets out of its process and its screens, and the two settings that choose some of it: the manifest and
its gate `ShippedManifestTest`, `data_extraction_rules.xml`, `SecureWindows`, `PrivacySettings`, and Glimpse's
`SettingsActivity` with `root_preferences.xml`. Glimpse is written for pictures already public to anything holding a
media permission; every outlet it had was safe there and is a leak here. This brief is level 1; full records are in
`docs/decisions/`.

## Rules that hold here
- **No permission at all.** The manifest removes `INTERNET`, `ACCESS_NETWORK_STATE` and `WAKE_LOCK`, which libraries
  merge in, and the media permissions Glimpse asked for are gone. Without `INTERNET`, plaintext in this process cannot
  leave by a socket, whatever a library tries, because the kernel checks it. OkHttp, Glide's integration with it and
  Media3's streaming modules went with it. `ShippedManifestTest` reads the merged manifest, so a library that adds a
  permission fails the build. (I3)
- **One way in, the launcher.** Only `MainActivity` is exported. Glimpse's `PickerActivity` and `SetWallpaperActivity`
  are deleted, and `ViewActivity`, `SettingsActivity` and `NoticesActivity` are not exported. (I3, I19)
- **Nothing the app keeps leaves with a backup or a transfer.** `allowBackup` is false, and `data_extraction_rules.xml`
  excludes every domain from both a cloud backup and a transfer to a new phone, because from API 31 `allowBackup`
  false stops only the first. `ShippedManifestTest` reads both. (I3, amended 2026-11-04)
- **Media3 reports nothing to the platform.** Every `ExoPlayer` and `Transformer` this app builds has
  `setUsePlatformDiagnostics(false)`: by default each reports to `MediaMetricsManager` from API 31, which Google may
  collect. The viewer's player did until 2026-11-15. (I3, I22)
- **A video's sound is never another app's to record**: `allowAudioPlaybackCapture` is false, and it does not follow
  the screenshot setting, since a screenshot has no sound and a recording's would be the vault's. (I3, amended
  2026-11-04)
- **Every window carries `FLAG_SECURE` unless the user allows screenshots**, set in one place by `SecureWindows`,
  which the application registers for every activity: applied when a window is made and again whenever a screen
  comes to the front, so a change reaches the gallery behind the settings without a restart, and on the screen it was
  changed on at once (`SecureWindows.follow`). (I3, I13)
- **Every dialog goes through `SecureWindows.secure`**, because a dialog is a window of its own, which an activity's
  flag does not reach. (I3, amended 2026-11-03)
- **Two switches at the top of the settings screen, under *Privacy***, kept in the app's own preferences, which
  nothing backs up or transfers, and read by `PrivacySettings`. (I13)
- ***Allow screenshots* is off by default**, with encri-files' reasons and words (F3): off, every window this app
  draws carries `FLAG_SECURE`; on, none does. The switch says what a screenshot costs, and that encri's own screens
  are not changed by it. (I13)
- ***Show previews* is on by default** (encri D112, encri-files F4). Off, no grid cell, album cover or video's still
  asks encri for a preview; a photo opened is still read and shown, which is not a preview. How it reaches Glide is
  `vault-library`'s. (I13)
- **A setting takes effect at once**, never at the next start, which leaves a switch looking broken; and there is no
  setting per screen. (I13)
- **The third-party notices are a row of their own under *About*, below the switches**, because a licence's terms do
  not depend on there being a vault. (I19)

## Traps already paid for
- From API 31 `allowBackup` false stopped a cloud backup and not a transfer to a new phone, which would have carried
  the preferences, saved playback positions among them, each keyed by a hash of its document's id. Lint said so.
  (I3, amended)
- A video's sound was still another app's to record: from API 29 an app's audio may be captured by any app recording
  the screen unless it says not, and `FLAG_SECURE` keeps out the picture alone. Seen in the package's flags on
  Waydroid, API 33. (I3, amended)
- The info sheet, a dialog, had no `FLAG_SECURE`: Android leaves a secure window out of a screen recording and shows
  the rest, so its name, size, date and coordinates would have been in one. (I3, amended)
- The setting applied only when a window was made left the gallery behind the settings as it was; the re-apply on
  return is what its test fails without. (I13)
- Rejected: keeping Glimpse's outlets behind a confirmation, which is a design for later rather than a default to
  inherit. (I3)

## Measured, not assumed
- On the A20 (API 30): `screencap` refused; then allowed on the settings screen at once and on the gallery behind it;
  then refused again on both. (I13)
- On the A20, 2026-11-12: both switches' words of 2026-11-11 on the settings screen exactly as `strings.xml` has
  them, `allow_screenshots_summary` (464 characters) and `show_previews_summary` (427), compared character for
  character. Until then they had passed the unit suite and lint alone. (I13, `CHANGELOG.md`)
- On the S9, the info sheet's window flags were `0x81810102` beside the activity's `0x81812100`, and `0x81812102`
  after the fix; a share dialog's window is `0x1822002` on the A20 and the S24. (I3, I10)
- `allowAudioPlaybackCapture`'s flag is gone from the package's flags on Waydroid (API 33) and the S9. (I3)
- The minified release build's APK declares no permission but AndroidX's signature permission to itself. (I4)
- On the G6, release build: the settings screen with both switches and their words; screenshots allowed, then kept
  out again. (I18)
- Under Robolectric: screenshots kept out by default and allowed once chosen; applied on a screen's return and at
  once on the screen they were changed on; the settings screen's keys and defaults are `PrivacySettings`'. (I13)

## Decisions
| | Why it matters here |
|---|---|
| I3 | No permission, one way in, nothing on disk, nothing while locked, secure windows; three veto flags |
| I13 | The two switches; screenshots off by default (amends I3); one veto flag |
| I19 | The notices row under *About* |
| I10 | Dialogs before a handoff are secure windows too |
| I4 | The release APK's permissions |
| I18 | Both switches on the release build |
| I22 | Media3's platform reports off; the editor unexported, closing at a lock |

## Open here
- Screenshots are blocked by default, encri-files F3's flag again (I13). A location is shown as numbers, the only way
  to keep it from a service without a design for asking (I3). Both flagged for veto.
- Each switch's words follow encri-files' (I13); the suite's `docs/documentation.md` (*Text in the apps*) asks every
  companion to say the same things in the same words, which `strings.xml` (`glimpse-screens`) carries.

## Read next
- Related briefs: `.claude/rules/vault-library.md` (previews), `.claude/rules/glimpse-screens.md` (the settings
  screen's layout and strings), `.claude/rules/release-and-signing.md` (the notices)
- Full records: `docs/decisions/INDEX.md`
