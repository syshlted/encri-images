# I3 — Nothing leaves: no permission, one way in, no disk, and nothing shown while locked
*2026-11-01 · accepted; **flagged for veto on three points** (below)*

Glimpse is written for pictures that are already public to anything holding a media permission. Every outlet it has
was safe there and is a leak here, so the first version closes all of them, and each comes back only with a design of
its own.

**No permission at all.** The manifest removes `INTERNET`, `ACCESS_NETWORK_STATE` and `WAKE_LOCK`, which libraries
merge in, and the media permissions Glimpse asked for are gone. Without `INTERNET`, plaintext in this process cannot
leave by a socket, whatever a library tries, because the kernel checks it. OkHttp, Glide's integration with it, and
Media3's DASH, HLS, RTSP and SmoothStreaming modules are gone with it. `ShippedManifestTest` reads the merged manifest,
so a library that adds one fails the build.

**One way in, the launcher.** Glimpse exported `PickerActivity` for `GET_CONTENT` and `PICK`, `ViewActivity` for `VIEW`
and `REVIEW`, and `SetWallpaperActivity`. Here the first would hand vault plaintext to whichever app asked, the second
would be a way to probe it, and a wallpaper is a picture from the vault on the lock screen. Picker and wallpaper were
deleted, and the viewer is unexported and takes its request from this app's own extras.

**Nowhere to write a picture.** Glide keeps a disk cache by default, which encri D112 forbids because it would hold
plaintext outside the vault. `VaultGlide` installs its no-op `DiskCacheAdapter`, and answers `DataSource.LOCAL`, since
Glide writes what is `REMOTE` to its data cache. ExoPlayer has no cache configured. After browsing, playing and
looking at the info sheet on the A20, the app's data directory held five empty directories and no file at all.

**Nothing sent about a picture.** Glimpse's info sheet gave a photo's coordinates to `Geocoder`, which on most phones
is a network service, without asking, and offered them to a maps app. Here it shows the numbers and nothing else.
Share, edit and *use as* are gone, because they need encri's handoffs (encri D117), and the favourite and trash
buttons are gone because encri has neither. *Amended by I10: share is back, and open with, through a handoff.*
*Amended by I22: crop and rotate are back, as a design of this app's own: a copy kept beside the original, in the
vault.*

**Nothing shown while locked.** `VaultLibrary` forgets everything when the vault locks or the session goes, and Glide's
memory is cleared. `MainActivity` hides the whole gallery behind a gate with one message and one button. **The viewer
closes itself**, because an open descriptor goes on serving across a lock (encri D103), so a video would otherwise
play on. Glimpse's `ACTION_REVIEW_SECURE`, which showed media over the keyguard, is gone.

**Asks for `READ` alone**, because this version does nothing else, and encri's screen says so to the user.
*Amended by I11: and for `DELETE` as well, the first time the user deletes.*

**`allowBackup` is false**, and **every window has `FLAG_SECURE`**, set in one place by `SecureWindows`. *Amended by
I13: unless the user allows screenshots.*
*Amended 2026-11-04: from API 31 `allowBackup` false stops a cloud backup and not a transfer to a new phone, which
would have carried the preferences, saved playback positions among them, each keyed by a hash of its document's id.
Lint said so.
`data_extraction_rules.xml` excludes every domain from both, and `ShippedManifestTest` reads it and the merged
manifest.* *Amended 2026-11-04 again: a video's sound was still another app's to record. From API 29 an app's audio may
be captured by any app recording the screen, unless it says not, and `FLAG_SECURE` keeps out the picture alone; seen in
the package's flags on Waydroid, API 33. `allowAudioPlaybackCapture` is false, the flag is gone there and on the S9, and
`ShippedManifestTest` reads it. It does not follow I13's screenshot setting: a screenshot has no sound, and a
recording's would be the vault's.*
*Amended 2026-11-03: a dialog is a window of its own, which an activity's flag does not reach, and the info sheet's
had none (its flags on the S9 were `0x81810102`, the activity's behind it `0x81812100`). Android leaves a secure
window out of a screen recording and shows the rest, so the sheet's name, size, date and coordinates would have been
in one (not tried here: `screencap` on API 29 refuses the whole screen). Every dialog now goes through
`SecureWindows.secure`, and the sheet's flags on the S9 are `0x81812102`.*

*Rejected:* keeping these features and asking for confirmation, which is a design for later rather than a default to
inherit. *Rejected:* disabling Glide's disk cache per request, where one forgotten call is a leak; with the adapter
there is nothing to forget.

**Flagged for veto:**

1. **Screenshots are always blocked**, with no setting. encri-files has one, off by default (encri-files F3).
   *Amended by I13: a setting, off by default.*
2. **The first version has no share, favourites, trash or delete**, all of which a gallery is expected to have.
3. **A location is shown as numbers**, which is the only way to keep it from a service without a design for asking.

<!-- amendments: generated by scripts/docs/decision-index.py; do not edit -->

## Amended since, as recorded in `amendments.md`

- **I3's edit, gone with Glimpse's outlets, is back by I22 (2026-11-15)**, as a design of this app's own: a photo or
  video cropped and turned here, decoded and muxed in memory, and kept as a new document beside the original.
- **I3's screenshots, blocked always and with no setting, are amended by I13 (2026-11-04).** *Allow screenshots* is a
  setting, off by default, and every window and dialog follows it.
- **I3's secure windows kept a video's picture out of a screen recording and not its sound, amended 2026-11-04.** From
  API 29 any app recording the screen may capture another's audio unless it says not. `allowAudioPlaybackCapture` is
  false, `ShippedManifestTest` reads it, and it does not follow I13's screenshot setting.
- **I3's `allowBackup` false stopped a cloud backup and not a transfer to a new phone, amended 2026-11-04.** From API
  31 a transfer would have carried the preferences, saved playback positions among them. `data_extraction_rules.xml`
  excludes every domain from both, and `ShippedManifestTest` reads it.
- **I3's first version, with no share and `READ` alone, is amended by I10 and I11 (2026-11-03).** Share is back, and
  *open with*, through encri's handoff (I10); and the app asks for `DELETE` as well, the first time the user deletes
  (I11).
- **I3's `FLAG_SECURE` on every window did not reach a dialog, amended 2026-11-03.** A dialog is a window of its own,
  and the info sheet's had no flag. Every dialog now goes through `SecureWindows.secure`.

<!-- /amendments -->
