# encri-images — Roadmap

What is next, and in what order. `AGENTS.md` is why; `CHANGELOG.md` is what happened. When this file and `AGENTS.md`
disagree, `AGENTS.md` wins.

Severities, as in encri: **S1** loses a user's data or lets the wrong app read it · **S2** a wrong answer a user cannot
tell from a right one · **S3** wrong under a narrow race, or a cost nobody chose · **S4** debt.

## Now

The first version shows the vault and does nothing else (I3, I4). What follows puts back what a gallery is expected
to do, each with a design of its own rather than Glimpse's.

1. **Measure a real library.** Walk time, memory and scrolling with thousands of photos, on the A20 and on an older
   phone, and API 27–29 and 33+. This decides whether encri-api needs a query across folders (I4).
2. **Pinch zoom and large images.** Whether ZoomImage's subsampling reaches our `Uri`, what it does if not, and that
   its tile cache stays in memory. Not checked (I4).
3. **Sharing**, through encri's handoffs (encri D117), with the confirmation encri-files makes (encri-files F7).
4. **Delete**, which asks for the `DELETE` right, and what stands in for a trash when encri has none.
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
- **Device tests** against a real encri, as encri-api's L13 runs them. Everything so far was by hand (I4).
- **Protect `master`** with a ruleset, at the user's word (I1).
- **Remove unused resources** left by what was deleted: strings, drawables and translations. The release build
  shrinks them out, so this is **S4**.

## Bugs

- **S3 — A video of no length spins.** ExoPlayer's repeat mode over a clip whose duration is 0 creates and destroys a
  decoder several times a second, seen on the A20 (I4). Inherited.
- **S4 — Inherited compile warnings**: `bundleOf`, `BuilderInference` and two shadowed extensions.
- **S4 — Inherited lint errors**: 54 `ImpliedQuantity` in Glimpse's translations, and `UnsafeOptInUsageError` on Media3
  calls in `LocalPlayerViewModel` and `MediaViewerAdapter`. Lint is not one of the gates yet.
