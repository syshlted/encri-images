# encri-images — Changelog

What happened, newest first. `AGENTS.md` is why, and `ROADMAP.md` is what is next.

## 2026-11-04

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
