# encri-images — Roadmap

What is next, and in what order. `AGENTS.md` is why; `CHANGELOG.md` is what happened. When this file and `AGENTS.md`
disagree, `AGENTS.md` wins.

Severities, as in encri: **S1** loses a user's data or lets the wrong app read it · **S2** a wrong answer a user cannot
tell from a right one · **S3** wrong under a narrow race, or a cost nobody chose · **S4** debt.

## Now

The first version shows the vault and does nothing else (I3, I4). What follows puts back what a gallery is expected
to do, each with a design of its own rather than Glimpse's.

1. **Measure a real library elsewhere.** The A20 and the S9 are measured at 5,000 photos, and the walk stays (I8,
   I9). Still to measure: the G6 (API 28), once its vault exists; API 27 and 33+, which need encri on an emulator and
   the S24; and a library of 20,000, where the A20's estimate is four to five seconds before Reels shows anything.
2. **Subsampling for large images.** ZoomImage does not subsample a vault photo, so zooming magnifies the screen's
   decode (I8). A `SubsamplingImageGenerator` of this app's own, reading through `openRead`, with its tiles in
   Glide's memory cache, which a lock clears.
3. **Delete**, which asks for the `DELETE` right, and what stands in for a trash when encri has none.
4. **Several at once.** Share is one photo at a time, from the viewer (I10). Glimpse's selection in the grid, which
   was deleted with its trash, would share several through one handoff.
5. **A date a photo was taken.** Reels sorts by last change (I4). EXIF for every photo is too slow to read in a walk,
   so this needs somewhere to keep the answer that is not outside the vault.
6. **Favourites**, which need somewhere to live. Encri has no such field; a file in the vault is one way.
7. **Settings of its own**: previews on or off (encri D112 gives each app its own), and screenshots (encri-files F3).

## Before a first release

- **The icon** is still Glimpse's (I2).
- **Stable dependencies**, for the alphas Glimpse was forked with (I1).
- **Third-party notices**, as encri-files shows them with a gate that checks the list (encri-files F10).
- **A signing key**, whether encri's registry lists this app, and whether it joins encri D136's joint release (I2).
- **Reproducible builds**, as encri-files has them (encri-files F9).
- **Device tests in a run of their own.** They exist (I6), and run by hand, one class on one phone at a time. A
  release wants every class on every phone, with the results kept.
- **Protect `master`** with a ruleset, at the user's word (I1).
- **Remove unused resources** left by what was deleted: strings, drawables and translations. The release build
  shrinks them out, so this is **S4**.

## Bugs

- **S3 — A video of no length spins.** ExoPlayer's repeat mode over a clip whose duration is 0 creates and destroys a
  decoder several times a second, seen on the A20 (I4). Inherited.
- **S2 — A gallery in front goes on showing a grant's folder after it is deleted**, until something walks again (I9).
  encri sends no notice the grant can hear, and the session does not end. A question for encri-api first.
- **S4 — ZoomImage asks Glide for every opened photo as a file**, from a cache that is always empty (I8). Nothing is
  read or written, and since I8 nothing is logged; item 2 replaces it.
- **S4 — Inherited compile warnings**: `bundleOf`, `BuilderInference` and two shadowed extensions.
- **S4 — Inherited lint errors**: 54 `ImpliedQuantity` in Glimpse's translations, and `UnsafeOptInUsageError` on Media3
  calls in `LocalPlayerViewModel` and `MediaViewerAdapter`. Lint is not one of the gates yet.
