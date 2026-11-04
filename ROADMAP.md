# encri-images — Roadmap

What is next, and in what order. `AGENTS.md` is why; `CHANGELOG.md` is what happened. When this file and `AGENTS.md`
disagree, `AGENTS.md` wins.

Severities, as in encri: **S1** loses a user's data or lets the wrong app read it · **S2** a wrong answer a user cannot
tell from a right one · **S3** wrong under a narrow race, or a cost nobody chose · **S4** debt.

## Now

The first version shows the vault and does nothing else (I3, I4). What follows puts back what a gallery is expected
to do, each with a design of its own rather than Glimpse's.

1. **Measure a real library elsewhere.** The A20, the S9, the G6 and the S24 are measured at 5,000 photos, and the
   walk stays (I8, I9). Still to measure: API 27, which needs encri on an emulator; and a library of 20,000,
   where the A20's estimate was four to five seconds before Reels shows anything, one folder at a time; I14 lists four
   at once, which took the G6's walk of 2,000 photos from a second or more to under a third of one.
2. **A bin**, if there is to be one. A delete is permanent (I11), and encri has no trash; a folder of this app's own
   in the vault would need `MOVE` and `CREATE`, and every app with a grant would see it.
3. **A date a photo was taken.** Reels sorts by last change (I4). EXIF for every photo is too slow to read in a walk,
   so this needs somewhere to keep the answer that is not outside the vault.
4. **Favourites**, which need somewhere to live. Encri has no such field; a file in the vault is one way.

## Before a first release

- **The icon** is still Glimpse's (I2).
- **Third-party notices**, as encri-files shows them with a gate that checks the list (encri-files F10).
- **A signing key**, whether encri's registry lists this app, and whether it joins encri D136's joint release (I2).
- **Reproducible builds**, as encri-files has them (encri-files F9).
- **Protect `master`** with a ruleset, at the user's word (I1).

## Bugs

- **S2 — A gallery in front goes on showing a grant's folder after it is deleted**, until something walks again (I9).
  encri sends no notice the grant can hear, and the session does not end, though its `refreshGrant` answers
  `NOT_AUTHORIZED` (I12). encri-api L23 now promises the end, which the link already hears; this waits on an encri
  that sends it, and the newest build, 0.1.0 of 2026-10-29, does not.
