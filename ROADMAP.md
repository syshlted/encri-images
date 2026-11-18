# encri-images — roadmap

What is next, and in what order. Why is in `docs/decisions/`, and what happened is in `CHANGELOG.md`. When they
disagree, the decision records win.

## Severity

| | Meaning |
|---|---|
| **S1** | Data loss, a confidentiality breach, or a vault that will not open |
| **S2** | A user-visible wrong answer or failure, with no workaround |
| **S3** | Wrong under a narrow race, or an honest limit that needs disclosing |
| **S4** | Waste, cosmetics, or documentation that contradicts the code |

Severity is about the consequence if it happens, not the odds of it happening.

## Now

The first version shows the vault and does nothing else (I3, I4). What follows puts back what a gallery is expected
to do, each with a design of its own rather than Glimpse's.

1. **Measure a real library elsewhere.** The A20, the S9, the G6 and the S24 are measured at 5,000 photos, and the
   walk stays (I8, I9). Still to measure: a vault on API 27, where the app now starts (I18) but has no encri; and a
   library of 20,000, where the A20's estimate was four to five seconds before Reels shows anything, one folder at a
   time; I14 lists four at once, which took the G6's walk of 2,000 photos from a second or more to under a third of
   one, and the A20's from about two thirds of a second to under a third (**measured 2026-11-12**, I14, amended). Four
   at once is still to measure on the S9 and the S24.
2. **A bin**, if there is to be one. A delete is permanent (I11), and encri has no trash; a folder of this app's own
   in the vault would need `MOVE` and `CREATE`, and every app with a grant would see it.
3. **A date a photo was taken.** Reels sorts by last change (I4). EXIF for every photo is too slow to read in a walk,
   so this needs somewhere to keep the answer that is not outside the vault.
4. **Favourites**, which need somewhere to live. encri has no such field; a file in the vault is one
   way.
5. **The *Send its name* tick** (encri D148, encri-api L24 and L25). **Added 2026-11-11.** In the dialog I10 already
   shows before a share or an *open with*, a tick, unticked every time and never remembered, asking that the app
   chosen next read the document's real name. Ticked, the handoff is asked for with
   `EncriSession.handOff(documentIds, withNames = true)`, encri-api's `handOffNamed`, which comes with contract
   version 2. Unticked, an encri that speaks version 2 gives the app chosen a name made from the handoff's token.
   Against an older encri the client answers `STATUS_UNSUPPORTED` without asking it, and the app is to say so rather
   than send the photo under a name the user did not choose (encri-api L24). **Waits on** the encri-api pin moving to
   a commit with L24 (encri-api's `a414543`, ~~not pushed yet~~ **pushed 2026-11-11**; the pin is `c27f139`), and on an encri that has built
   encri D147 on the test phones, since encri 0.1.0 speaks version 1; **the A20 and Waydroid have one, a debug build,
   since 2026-11-11**. Its words and its screen go in a record of this repository's when it is built (encri-api L24).

6. **Crop and rotate on a phone** (I22). **Added 2026-11-15**, built and unit-tested, and ~~run on no phone~~ **run by
   the user on the S24 (API 37), the debug build at `d116f22`, by 2026-11-18**: *"It worked perfectly."* A photo was
   cropped and saved there, past encri's consent screen; a turn and a video were not said. ~~On the A20 or the G6~~
   Still to see, on the A20 or the G6, below the S24's API: a photo cropped, turned and saved after encri's consent
   screen for `CREATE` and `WRITE`, and a second save going straight to it; a turn alone of a clip and a crop of one,
   played back in the viewer; the app's data directory after both, which should hold no picture; memory over a long
   clip and a large photo. On a phone at API 31 or later, the S24 among them, `dumpsys media.metrics` showing that
   neither a playback nor an edit is reported. Then whether a copy should keep the original's dates, which waits on *A
   date a photo was taken* (3, above): not as EXIF, which `ExifInterface` writes only through a temporary copy of the
   picture (I23).

## Before the first release

- **The icon** is still Glimpse's (I2).
- **This app's upload key**, made on the key machine by `scripts/release/upload-key.sh <the keys' directory>`, as
  `encri-images-upload.p12`, alias `encri-images-upload`, and the `upload=` line it prints put in
  `config/release-certificates`; until then `kit.sh` makes no kit (I20). Then a kit is carried to the key machine,
  signed, verified here, and the bundle uploaded with the listing's automatic protection off (encri D144).
- **Whether encri's registry lists this app**, and whether it joins encri D136's joint release (I2): encri-api's, and
  the user's to say.
- **Protect `master`** with a ruleset, at the user's word (I1).

## Bugs

- **S2 — A gallery in front goes on showing a grant's folder after it is deleted**, until something walks again (I9).
  encri sends no notice the grant can hear, and the session does not end, though its `refreshGrant` answers
  `NOT_AUTHORIZED` (I12). encri-api L23 now promises the end, which the link already hears; this waits on an encri
  that sends it, and the newest build, 0.1.0 of 2026-10-29, does not. **encri's roadmap files the fix as encri B143
  (2026-11-11).**

## Later

- **Crop and rotate in Glimpse** (I23), photos first. The editor reads and keeps through `EditStore` since
  2026-11-18, so what goes to Glimpse can come back with the vault's store in place of Glimpse's. In order:
  1. **Ask Glimpse's maintainers**, the user's to do: whether they want an editor of Glimpse's own beside
     `ACTION_EDIT`, whether photos first will do, and whether a copy keeps the original's metadata.
  2. **The photo editor in Glimpse's shape**: in `org.lineageos.glimpse` under `src/main/java`, in Glimpse's
     formatting, with SPDX headers, asking which copyright line a contributor's new file carries. A store over
     MediaStore: a pending item in the original's folder, written, then `IS_PENDING` cleared, and deleted where it
     could not be filled; MediaStore numbers a name that is taken. How the built-in editor and `ACTION_EDIT` share
     *Edit*. Strings in `values/strings.xml` alone, without the vault's words. Whether Glimpse takes Robolectric
     tests, or they stay here. A Gerrit chain of small changes: the geometry and the frame, the view and its gestures,
     the photo's screen, the save. Each run on a phone at API 30 or later, Glimpse's floor, with `generateBp` run and
     REUSE lint passing before it is uploaded.
  3. **A video's crop and turn, later and on its own, looking and working almost exactly as the photo's do**: the same
     screen, frame, shapes, turn and *Save a copy*, with the video playing under the frame as `VideoStage` plays it.
     First, how much Media3 Transformer, Effect and Muxer add to Glimpse's APK, measured on this app's release builds
     before and after `d116f22`. Muxed into MediaStore's file rather than into memory, which drops I22's ceiling of
     an eighth of the phone's memory or 1 GiB. And whether `kotlinx-coroutines-guava` and `concurrent-futures` are
     among what Glimpse's `generateBp` takes the platform to have.
  4. **Then take it back**: Glimpse's editor cherry-picked with `git cherry-pick -x -S` (I1), with only the vault's
     store, `SaveFlow` and the lock kept in `src/main/kotlin`, and I23 and the editor's brief amended.
- **Changes waiting for Glimpse**, each a branch of one signed commit off `lineage-24.0` in the user's Glimpse checkout
  (`docs/dev-environment.md`): `platform-diagnostics-off`, the viewer's player reporting nothing to the platform
  (2026-11-18, I23), and `info-sheet-exif-io`, `media3-unstable-api-opt-in` and `zero-length-video-loop`
  (2026-11-04). All four are on GitHub, in the public fork `zish/android_packages_apps_Glimpse` (2026-11-18). None
  is uploaded to Gerrit: that waits on a Gerrit account at review.lineageos.org. `platform-diagnostics-off` is
  unmeasured on a phone at API 31 or later, where the reports begin; Waydroid and the S24 are the two.
