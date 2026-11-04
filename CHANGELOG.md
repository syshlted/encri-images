# encri-images — Changelog

What happened, newest first. `AGENTS.md` is why, and `ROADMAP.md` is what is next.

## 2026-11-04

**The device tests in one run** (I17): `scripts/device/suite <serial>...` runs the gate, a small seed, the walk
measurement, the seed's removal and the grant probe on each phone, and keeps what each logged under `build/`.

**Several at once** (I16). A long press in a grid starts choosing; the action bar shares what is chosen through one
handoff and deletes it after one question. Checked on the G6, where three of six seeded photos were shared as far as the
chooser and then deleted.

**Zoom reads the photo itself** (I15). A large photo zoomed now shows its own pixels in tiles read from the vault,
where it showed its screen-sized decode magnified; on the G6 an 8000x6000 photo's grain came back. ZoomImage no longer
asks Glide for the photo as a file.

**The walk lists four folders at once** (I14): on the G6, 2,000 photos in 50 folders walked in 274 to 440 ms,
where one folder at a time took 0.9 to 1.4 seconds.

**Settings of its own** (I13): *Allow screenshots*, off by default as in encri-files, and *Show previews*, on by
default. Checked on the A20, where the first try showed every preview still from Glide's memory after they were turned
off.

**Stable dependencies** (I1, amended). Glimpse's five alphas are the stable releases of their lines, and nothing the
app ships is a pre-release.

**Fixed: a video behind encri's consent screen lost what it was reading** (I12, amended). An allowed consent ends the
session, and a descriptor it opened fails after the 16 KiB it had buffered, seen on the S9 and the G6. The player's
source now opens the video again where it stopped, through the next session.

**Fixed: a video of no length spun the player** (I4, amended). Glimpse looped every clip, and one that lasts nothing
made and dropped a decoder about twenty times a second on the A20. A clip now loops only once it is ready and lasts.

**Fixed: a transfer to a new phone could carry what this app keeps** (I3, amended). From API 31 `allowBackup` false
stops a cloud backup and not a device transfer, which would have taken the preferences and the saved playback
positions, keyed by a hash of the document's id. Data extraction rules now exclude everything from both.

**Fixed: the inherited lint errors, and Android lint is a CI step.** The 54 `ImpliedQuantity` were all in strings for
what this app no longer has (trash, favourites, the picker and the wallpaper), which went with 11 drawables and the
rest of what nothing used. The Media3 calls lint flagged are opted in where they are made.

**Fixed: the inherited compile warnings**, and a new one now fails the build. `bundleOf` gave way to a typed
`Bundle`, `@BuilderInference` went, and the two extensions the compiler took for shadowed stay, since the members
they reach are package-private.

**Measured on the S24** (I9), at API 37, the first phone above API 30: a walk of 5,080 photos in an eighth of a
second, previews three times as fast as on any other phone, and Reels at 120 Hz missing about one frame in a thousand.
**Share, open with and delete checked on the A20 and the S24** (I10, I11), at API 30 and 37, and **both probes on
the S24 and `GrantGone` on the A20** (I12), which answered as on the G6.

## 2026-11-03

**What encri does when a grant changes** (I12), seen on the G6 by two new probes, `GrantChanges` and `GrantGone`.
Allowing a consent replaces the grant with exactly what was asked and ends the session; refusing changes nothing. A
grant whose folder another app deletes is gone, but its session is not told.

**Measured on the G6** (I9), once its vault was made. A walk there is bound by the CPU's clock: 0.95 to 1.36 seconds
at 2,080 photos, and a third of that with the clock held up. Reels drops most frames at its 1440x2880. Share, open
with and delete were checked on it too, on API 28 (I10, I11).

**Delete** (I11), from the viewer, for a photo or video and not an album. The first delete asks encri for the right,
and every delete says first that it cannot be undone. Checked on the S9, where a photo was deleted after encri's
consent screen allowed it.

**Share and open with** (I10), from the viewer, through encri's handoff and the platform's chooser, with encri left
out. A dialog says what it costs first, every time. Checked on the S9, where Samsung's gallery opened a photo it
read through the handoff.

**Fixed: the info sheet could be recorded** (I3, amended). A dialog's window does not take its activity's
`FLAG_SECURE`, so a photo's name, size, date and location were outside what screenshots are kept from.

**Device tests** (I6). `GateOnPhoneTest` and `WalkMeasurement` run in the app's own process, with its grant;
`SeedLibrary` and `RemoveLibrary` write a test library from a package of their own, `:testvault`, and remove it by the
id they wrote down. `scripts/device/instrument` runs one class on one phone, and CI now compiles their APKs.

**Measured on the S9 at 5,000 photos** (I9), at about half the A20's cost, and **a grant of one folder** checked on a
phone. **Fixed: a grant whose folder was deleted showed an empty gallery** where encri no longer lets the app in; the
app asks encri again now, and the gate says why. A gallery left in front still shows the folder until it walks again.

**Measured on the A20 at 5,000 photos** (I8): a walk of a second or so, previews at 55 a second, and 7–10% janky
frames over fast flings. The walk stays. ZoomImage turned out not to subsample a vault photo.

**Fixed: document ids in the log** (I8). Glide logged every failed load with its URI, and ZoomImage made one for every
photo opened.

**Fixed: a lock or an unlock that encri's events did not carry** (I7). The link now hears them in what encri answers,
and asks a session it believes locked whether it still is, so the gate no longer sticks on *locked* after an unlock it
missed.

**Fixed: the app crashed at launch below API 30.** Glimpse's theme asked for a cutout mode API 30 added. Found by the
gate's device test on the G6 (API 28) and the S9 (API 29), where it now passes, as it does on the A20 (API 30).

## 2026-11-02

**Nothing from encri's closed repository** (I5). The phones and the code-server container are now described here rather
than by reference to encri's notes.

## 2026-11-01

**Forked from LineageOS Glimpse** at `89b4735` as `syshlted/encri-images`, with `lineage-24.0` renamed to `master`.

**The first version** (I1 to I4). It shows the photos and videos in an encri vault, through encri-client and nothing
else: Reels, Albums and Library; a photo with its info; a video, with seeking. A gate says why when it cannot, and a
lock closes everything. It asks encri for `READ` alone.

**Taken out**, because each would have let a vault's pictures leave: every permission including `INTERNET`, the
exported picker, viewer and wallpaper activities, the network stack, the `Geocoder` lookup on a photo's location,
display over the keyguard, share, edit and *use as*. Favourites and trash went because encri has neither, and MediaStore
went altogether. Android.bp and the rest of the platform build went too.

**Added:** the `vault` package, with 61 tests covering 84.6% of its lines, and encri-files' toolchain, hooks, CI and
encri-api pin, with 510 components pinned by hash. Five guarantees were checked by reverting them and watching the test
fail: the disk cache, `DataSource.LOCAL`, `INTERNET`, an exported viewer, and the library forgetting at a lock.

**On the A20 by hand**, on the debug build and the minified release build, which found the viewer opening at the newest
item rather than the one tapped, now fixed (I4).
