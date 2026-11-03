# encri-images — Changelog

What happened, newest first. `AGENTS.md` is why, and `ROADMAP.md` is what is next.

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
