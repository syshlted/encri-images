# encri-images — changelog

What happened, newest first. Why is in `docs/decisions/`, and what is next is in `ROADMAP.md`.

## Unreleased

### Crop and rotate, into a copy beside the original (2026-11-15)

**Completed.** The viewer's *Edit* opens *Crop and rotate* for a photo or a video, not a GIF (I22). A dotted frame
over the picture is dragged by a corner or a side and moved from inside, and two fingers zoom the picture under it, up
to eight times, so the frame can be put on a detail. It can be held to *Free*, *Original*, *Square*, *4:3*, *3:4*,
*16:9* or *9:16*, and *Rotate* turns the picture a quarter clockwise, the frame and its shape with it. *Save a copy*
keeps a new document beside the original, `IMG_1 (edited).jpg`, and never changes the original; the first time, it
asks encri for `CREATE` and `WRITE`, with every right the grant holds, and a delete's consent now asks the same way
(amends I11). A photo is decoded only where it is kept, a band at a time, and encoded straight into encri's pipe. A
video is made by Media3's Transformer and muxed into memory, never a file, and a turn alone is written as its
orientation without decoding it. Neither copy carries the original's metadata. Media3 Transformer, Effect and Muxer
1.10.0 and kotlinx-coroutines-guava now ship, with Guava's annotations left out as Media3 leaves them, and the notices
name them. The unit suite, `:app:lintDebug` and the notices gate pass, 233 tests, 88 of them new, at 89.5% of lines.

**Known issues.** Nothing of it has run on a phone (I22, *Not measured*). A copy keeps no EXIF, so no location and no
time taken, and reads as today's in Reels. A video is held whole in memory while it is made, up to an eighth of the
phone's memory or 1 GiB. A copy encri made and this app could not fill stays, empty, where the grant cannot delete.

### The viewer's player reports nothing to the platform (2026-11-15)

**Completed.** ExoPlayer reports each playback to Android's `MediaMetricsManager` by default, from API 31, and
Media3's own documentation says Google may collect what it reports where the user shares usage and diagnostics. The
viewer's player never turned it off, so a video played from the vault was reported: its format, its length and how it
played, though nothing of its picture. It is off (`setUsePlatformDiagnostics(false)`), as I3's *nothing sent about a
picture* asks. Found while building crop and rotate, whose player and Transformer have it off too.

**Known issues.** Not seen on a phone: `dumpsys media.metrics` on one at API 31 or later would show that a playback
is no longer reported.

### On the A20 and Waydroid: the suite, four at once, and the new words (2026-11-12)

**Completed.** Debug builds of this app at `d64a4c3`, with encri-api at its pin, ran on the A20 (API 30) and under
Waydroid (API 33) against an encri that has built encri D147. Waydroid has encri and a test vault for the first time,
and the A20's vault was made again (`docs/dev-environment.md`). **The suite** (I17, amended) passed all five classes on
both, none skipped, once the app had been let in on encri's consent screen. **Four folders at once** (I14, amended) on
the A20: 2,000 photos in 50 folders walked in 259–294 ms again, against 624–685 ms one at a time, and eight did no
better than four, the G6's shape. **The words of 2026-11-11 on a device**, where they had passed the unit suite and
lint alone: the gate's *The vault is locked.* under Waydroid and on the A20, and its *not let in yet*, with *Allow in
encri*, on the A20; and the summaries of *Allow screenshots* and *Show previews* on the A20's settings screen exactly
as `strings.xml` has them, compared character for character.

**Known issues.** The suite does not let the app in itself, so on a vault that has not, `WalkMeasurement` skips
(I17, amended). The gate's *access turned off*, *not installed*, *not trusted* and *too old* have not been seen on a
device. Four at once is unmeasured on the S9 and the S24, and so is a library of 20,000 (I14).

### The app on GitHub, and its first CI runs (2026-11-11)

**Completed.** The fork's `master` held only Glimpse's history. It was fast-forwarded to this app's work and pushed,
39 signed commits up to `e5ff5d8`, and the workflows ran for the first time: `ci` and `commit-policy` passed.
`reproducible.yml` waits for a `v*` tag or its Monday run.

### The gate and the privacy switches say what Encri Files says (2026-11-11)

**Completed.** The screen in front of the gallery words each of its states as Encri Files does: not let in yet,
access turned off in encri, encri not installed, not trusted and too old. *Allow screenshots* and *Show previews* now
say what each does on and off in Encri Files' words, which I13 meant them to have: what a screenshot exposes, that
previews are kept in memory alone and dropped at a lock, and that the switch changes this app alone. Encri Images
still names no certificate or protocol version, which its gate has no argument for, and with previews off its cells
stay empty rather than giving way to names. The notices' line ends with a full stop. `scripts/ci/diff-coverage.py`,
copied from Encri Files, cited its F1 as though it were this repository's, and now cites encri-files F1. The unit
suite and `:app:lintDebug` pass, 145 tests.

### The decision log leaves AGENTS.md, as encri's did (2026-11-11)

**Completed (I21).** Each of I1 to I20 is a record of its own in `docs/decisions/`, its text unchanged, with an index
and footers that `scripts/docs/decision-index.py` writes. `AGENTS.md` is the level-0 guide in the suite's order, 198
lines where it was 940; the status, the conventions and the development environment moved to `docs/` unchanged, and a
rebuild of the old file from what was written matched it byte for byte. Nine component briefs in `.claude/rules/`
load with the files they cover, and `scripts/docs/brief-coverage.py` finds every file covered. `README.md`,
`ROADMAP.md` and this file follow the suite's `docs/documentation.md`, this file's entries each under a heading of its
own. `.gitignore` keeps out `signed/` and `.vscode/`, and lets `.claude/rules/` in.

**Roadmap.** The deleted-grant bug cites encri B143, encri's fix; the *Send its name* tick is queued (encri D148,
encri-api L24 and L25).

**Known issues.** Code, scripts and workflows still cite `AGENTS.md I4` and the like, which `AGENTS.md` says how to
resolve (I21).

### Releases are built and signed as Encri Files' are (2026-11-05)

**Releases are built and signed as Encri Files' are** (I20): twice from a tag, the same both times, here and by a
workflow on GitHub, and signed from a kit on the key machine with encri's app signing and transparency keys and an upload
key of this app's own, which is owed. `1d957f4` built the same twice.

### Third-party notices (2026-11-05)

**Third-party notices** (I19). The licences and notices of the 117 modules the app ships, written from each library's own
files, shown from the settings, and held to the build, which fails when a module ships that the notices do not name.

### The release build walked through again, and API 27 started (2026-11-04)

**The release build walked through again, and API 27 started** (I18). The minified build zooms, chooses, shares,
deletes and plays as the debug build does, on the G6 and the A20; on an API 27 emulator the app starts and shows its
gate, the first run below API 28.

### Fixed: a screen recording could carry a vault video's sound (2026-11-04)

**Fixed: a screen recording could carry a vault video's sound** (I3, amended). `FLAG_SECURE` keeps out the picture,
and from API 29 any app recording the screen may capture another's audio unless it says not; this app now says not.
Seen in the package's flags on Waydroid, where the gate test passes at API 33.

### The device tests in one run (2026-11-04)

**The device tests in one run** (I17): `scripts/device/suite <serial>...` runs the gate, a small seed, the walk
measurement, the seed's removal and the grant probe on each phone, and keeps what each logged under `build/`.

### Several at once (2026-11-04)

**Several at once** (I16). A long press in a grid starts choosing; the action bar shares what is chosen through one
handoff and deletes it after one question. Checked on the G6, where three of six seeded photos were shared as far as the
chooser and then deleted.

### Zoom reads the photo itself (2026-11-04)

**Zoom reads the photo itself** (I15). A large photo zoomed now shows its own pixels in tiles read from the vault,
where it showed its screen-sized decode magnified; on the G6 an 8000x6000 photo's grain came back. ZoomImage no longer
asks Glide for the photo as a file.

### The walk lists four folders at once (2026-11-04)

**The walk lists four folders at once** (I14): on the G6, 2,000 photos in 50 folders walked in 274 to 440 ms,
where one folder at a time took 0.9 to 1.4 seconds.

### Settings of its own (2026-11-04)

**Settings of its own** (I13): *Allow screenshots*, off by default as in encri-files, and *Show previews*, on by
default. Checked on the A20, where the first try showed every preview still from Glide's memory after they were turned
off.

### Stable dependencies (2026-11-04)

**Stable dependencies** (I1, amended). Glimpse's five alphas are the stable releases of their lines, and nothing the
app ships is a pre-release.

### Fixed: a video behind encri's consent screen lost what it was reading (2026-11-04)

**Fixed: a video behind encri's consent screen lost what it was reading** (I12, amended). An allowed consent ends the
session, and a descriptor it opened fails after the 16 KiB it had buffered, seen on the S9 and the G6. The player's
source now opens the video again where it stopped, through the next session.

### Fixed: a video of no length spun the player (2026-11-04)

**Fixed: a video of no length spun the player** (I4, amended). Glimpse looped every clip, and one that lasts nothing
made and dropped a decoder about twenty times a second on the A20. A clip now loops only once it is ready and lasts.

### Fixed: a transfer to a new phone could carry what this app keeps (2026-11-04)

**Fixed: a transfer to a new phone could carry what this app keeps** (I3, amended). From API 31 `allowBackup` false
stops a cloud backup and not a device transfer, which would have taken the preferences and the saved playback
positions, keyed by a hash of the document's id. Data extraction rules now exclude everything from both.

### Fixed: the inherited lint errors, and Android lint is a CI step (2026-11-04)

**Fixed: the inherited lint errors, and Android lint is a CI step.** The 54 `ImpliedQuantity` were all in strings for
what this app no longer has (trash, favourites, the picker and the wallpaper), which went with 11 drawables and the
rest of what nothing used. The Media3 calls lint flagged are opted in where they are made.

### Fixed: the inherited compile warnings (2026-11-04)

**Fixed: the inherited compile warnings**, and a new one now fails the build. `bundleOf` gave way to a typed
`Bundle`, `@BuilderInference` went, and the two extensions the compiler took for shadowed stay, since the members
they reach are package-private.

### Measured on the S24 (2026-11-04)

**Measured on the S24** (I9), at API 37, the first phone above API 30: a walk of 5,080 photos in an eighth of a
second, previews three times as fast as on any other phone, and Reels at 120 Hz missing about one frame in a thousand.
**Share, open with and delete checked on the A20 and the S24** (I10, I11), at API 30 and 37, and **both probes on
the S24 and `GrantGone` on the A20** (I12), which answered as on the G6.

### What encri does when a grant changes (2026-11-03)

**What encri does when a grant changes** (I12), seen on the G6 by two new probes, `GrantChanges` and `GrantGone`.
Allowing a consent replaces the grant with exactly what was asked and ends the session; refusing changes nothing. A
grant whose folder another app deletes is gone, but its session is not told.

### Measured on the G6 (2026-11-03)

**Measured on the G6** (I9), once its vault was made. A walk there is bound by the CPU's clock: 0.95 to 1.36 seconds
at 2,080 photos, and a third of that with the clock held up. Reels drops most frames at its 1440x2880. Share, open
with and delete were checked on it too, on API 28 (I10, I11).

### Delete (2026-11-03)

**Delete** (I11), from the viewer, for a photo or video and not an album. The first delete asks encri for the right,
and every delete says first that it cannot be undone. Checked on the S9, where a photo was deleted after encri's
consent screen allowed it.

### Share and open with (2026-11-03)

**Share and open with** (I10), from the viewer, through encri's handoff and the platform's chooser, with encri left
out. A dialog says what it costs first, every time. Checked on the S9, where Samsung's gallery opened a photo it
read through the handoff.

### Fixed: the info sheet could be recorded (2026-11-03)

**Fixed: the info sheet could be recorded** (I3, amended). A dialog's window does not take its activity's
`FLAG_SECURE`, so a photo's name, size, date and location were outside what screenshots are kept from.

### Device tests (2026-11-03)

**Device tests** (I6). `GateOnPhoneTest` and `WalkMeasurement` run in the app's own process, with its grant;
`SeedLibrary` and `RemoveLibrary` write a test library from a package of their own, `:testvault`, and remove it by the
id they wrote down. `scripts/device/instrument` runs one class on one phone, and CI now compiles their APKs.

### Measured on the S9 at 5,000 photos (2026-11-03)

**Measured on the S9 at 5,000 photos** (I9), at about half the A20's cost, and **a grant of one folder** checked on a
phone. **Fixed: a grant whose folder was deleted showed an empty gallery** where encri no longer lets the app in; the
app asks encri again now, and the gate says why. A gallery left in front still shows the folder until it walks again.

### Measured on the A20 at 5,000 photos (2026-11-03)

**Measured on the A20 at 5,000 photos** (I8): a walk of a second or so, previews at 55 a second, and 7–10% janky
frames over fast flings. The walk stays. ZoomImage turned out not to subsample a vault photo.

### Fixed: document ids in the log (2026-11-03)

**Fixed: document ids in the log** (I8). Glide logged every failed load with its URI, and ZoomImage made one for every
photo opened.

### Fixed: a lock or an unlock that encri's events did not carry (2026-11-03)

**Fixed: a lock or an unlock that encri's events did not carry** (I7). The link now hears them in what encri answers,
and asks a session it believes locked whether it still is, so the gate no longer sticks on *locked* after an unlock it
missed.

### Fixed: the app crashed at launch below API 30 (2026-11-03)

**Fixed: the app crashed at launch below API 30.** Glimpse's theme asked for a cutout mode API 30 added. Found by the
gate's device test on the G6 (API 28) and the S9 (API 29), where it now passes, as it does on the A20 (API 30).

### Nothing from encri's closed repository (2026-11-02)

**Nothing from encri's closed repository** (I5). The phones and the code-server container are now described here rather
than by reference to encri's notes.

### Forked from LineageOS Glimpse (2026-11-01)

**Forked from LineageOS Glimpse** at `89b4735` as `syshlted/encri-images`, with `lineage-24.0` renamed to `master`.

### The first version (2026-11-01)

**The first version** (I1 to I4). It shows the photos and videos in an encri vault, through encri-client and nothing
else: Reels, Albums and Library; a photo with its info; a video, with seeking. A gate says why when it cannot, and a
lock closes everything. It asks encri for `READ` alone.

### Taken out (2026-11-01)

**Taken out**, because each would have let a vault's pictures leave: every permission including `INTERNET`, the
exported picker, viewer and wallpaper activities, the network stack, the `Geocoder` lookup on a photo's location,
display over the keyguard, share, edit and *use as*. Favourites and trash went because encri has neither, and MediaStore
went altogether. Android.bp and the rest of the platform build went too.

### Added (2026-11-01)

**Added:** the `vault` package, with 61 tests covering 84.6% of its lines, and encri-files' toolchain, hooks, CI and
encri-api pin, with 510 components pinned by hash. Five guarantees were checked by reverting them and watching the test
fail: the disk cache, `DataSource.LOCAL`, `INTERNET`, an exported viewer, and the library forgetting at a lock.

### On the A20 by hand (2026-11-01)

**On the A20 by hand**, on the debug build and the minified release build, which found the viewer opening at the newest
item rather than the one tapped, now fixed (I4).
